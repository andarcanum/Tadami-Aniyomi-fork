package eu.kanade.tachiyomi.data.suggestions.sources

import eu.kanade.tachiyomi.data.discovery.CompositeTrendingSource
import eu.kanade.tachiyomi.data.discovery.DiscoveryMeta
import eu.kanade.tachiyomi.data.discovery.runCatchingUnlessCancelled
import eu.kanade.tachiyomi.data.suggestions.SuggestionCache
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionReason
import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed
import eu.kanade.tachiyomi.data.suggestions.SuggestionTitleResolver
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Headers
import org.jsoup.Jsoup
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private val defaultNuMetaSource: CompositeTrendingSource by lazy { CompositeTrendingSource() }

/**
 * Source backed by NovelUpdates.com series metadata.
 *
 * NovelUpdates has one of the broadest catalogues of Asian web-novels and
 * light-novels in English (KR/CN/JP). Each series has a "Recommendations"
 * page that lists related series, which we mirror here.
 *
 * The public site does not expose a stable recommendation API, so we use
 * the series page HTML endpoint. The endpoint is read-only and the same
 * approach is used by the official NovelUpdates clients.
 *
 * If the network is unreachable or the HTML parsing returns nothing, this
 * source degrades to a no-op — the rest of the suggestion pipeline keeps
 * working.
 */
class NovelUpdatesSimilarSource(
    override val mediaType: SuggestionMediaType,
    private val metaResolver: suspend (String) -> DiscoveryMeta? = { title ->
        runCatchingUnlessCancelled { defaultNuMetaSource.fetchMeta(title, DiscoveryMediaType.NOVEL) }.getOrNull()
    },
) : RecommendationPagingSource() {

    override val name: String = "NovelUpdates"

    private val client by lazy { Injekt.get<NetworkHelper>().client }
    private val json by lazy { Injekt.get<Json>() }

    override suspend fun fetchSuggestions(seed: SuggestionSeed): List<SuggestionItem> = coroutineScope {
        if (mediaType != SuggestionMediaType.NOVEL) return@coroutineScope emptyList()

        val cacheKey = SuggestionCache.makeKey(
            name,
            seed.primaryTitle,
            mediaType.name,
            seed.candidateTitles,
            seed.description,
            seed.author,
        )
        SuggestionCache.get(cacheKey)?.let {
            logcat { "[NovelUpdates] CACHE HIT for '${seed.primaryTitle}' (${seed.candidateTitles.size} candidates)" }
            return@coroutineScope it
        }
        logcat { "[NovelUpdates] START fetching for '${seed.primaryTitle}', candidates=${seed.candidateTitles}" }

        val suggestions = try {
            // Step 1: search for the seed to find the canonical series id.
            val searchResults = coroutineScope {
                seed.candidateTitles
                    .take(3)
                    .map { candidate ->
                        async {
                            try {
                                searchSeries(candidate)
                            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                logcat { "[NovelUpdates] search failed for '$candidate': ${e.message}" }
                                emptyList<NuSeriesStub>()
                            }
                        }
                    }.awaitAll()
            }.flatten().distinctBy { it.id }

            // Step 2: pick the best matching series by title overlap.
            val bestMatch = searchResults
                .mapNotNull { stub ->
                    val score = seed.candidateTitles.maxOfOrNull { c ->
                        SuggestionTitleResolver.scoreBaseTitleMatch(stub.title, c)
                    } ?: 0
                    if (score > 0) stub to score else null
                }
                .maxByOrNull { it.second }
                ?.first

            if (bestMatch == null) {
                logcat { "[NovelUpdates] No base series matched for '${seed.primaryTitle}'" }
                return@coroutineScope emptyList()
            }
            logcat {
                "[NovelUpdates] Base series selected: '${bestMatch.title}' (id=${bestMatch.id}) for '${seed.primaryTitle}'"
            }

            // Step 3: fetch the recommendations page and extract related series.
            val recommendations = fetchRecommendations(bestMatch.id)
                .take(8)

            val metaPermits = Semaphore(3)
            recommendations.map { rec ->
                async {
                    val meta = metaPermits.withPermit {
                        withTimeoutOrNull(3_500L) {
                            runCatchingUnlessCancelled { metaResolver(rec.title) }.getOrNull()
                        }
                    }
                    val queries = listOfNotNull(rec.title, meta?.altTitle)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                    SuggestionItem(
                        title = rec.title,
                        searchQueries = queries.ifEmpty { listOf(rec.title) },
                        thumbnailUrl = meta?.coverUrl,
                        providerName = name,
                        providerUrl = "https://www.novelupdates.com/series/${rec.id}/",
                        providerId = "${bestMatch.id}:${rec.id}",
                        mediaType = mediaType,
                        reason = SuggestionReason.EXTERNAL_NU,
                        genres = meta?.genres.orEmpty(),
                    )
                }
            }.awaitAll()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[NovelUpdates] ERROR for '${seed.primaryTitle}': ${e.message}" }
            emptyList()
        }

        logcat { "[NovelUpdates] END for '${seed.primaryTitle}': ${suggestions.size} suggestions" }
        if (suggestions.isNotEmpty()) {
            SuggestionCache.put(cacheKey, suggestions)
        }
        suggestions
    }

    /**
     * Search the NovelUpdates index. Returns a list of [NuSeriesStub]s.
     *
     * Primary: the wp-json REST endpoint. On failure (endpoint был удалён/404 —
     * видно в логах устройства) fallback: HTML-поиск `/?s=<q>&post_type=seriesplan`,
     * парсинг `div.search_title > a` (slug-URL серии).
     */
    private suspend fun searchSeries(query: String): List<NuSeriesStub> = try {
        val url = "https://www.novelupdates.com/wp-json/wp/v2/series?search=" +
            java.net.URLEncoder.encode(query, "UTF-8") +
            "&per_page=5"
        val response = client.newCall(GET(url, headers = browserHeaders()))
            .awaitSuccess()
            .parseAs<List<NuSeriesDto>>(json)
        response.map { NuSeriesStub(id = it.id.toString(), title = it.title?.rendered ?: "") }
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (e: Exception) {
        logcat { "[NovelUpdates] wp-json search failed for '$query' (${e.message}), trying HTML search" }
        htmlSearch(query)
    }

    private suspend fun htmlSearch(query: String): List<NuSeriesStub> {
        val url = "https://www.novelupdates.com/?s=" +
            java.net.URLEncoder.encode(query, "UTF-8") + "&post_type=seriesplan"
        val body = client.newCall(GET(url, headers = browserHeaders())).awaitSuccess().body.string()
        val regex = Regex(
            """<div class="search_title">\s*<a[^>]+href="https?://www\.novelupdates\.com/series/([^/"]+)/?"[^>]*>([^<]+)</a>""",
        )
        return regex.findAll(body)
            .map { match -> NuSeriesStub(id = match.groupValues[1], title = match.groupValues[2].trim()) }
            .filter { it.id.isNotEmpty() && it.title.isNotEmpty() }
            .toList()
    }

    private fun browserHeaders(): Headers = Headers.Builder()
        .add(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36",
        )
        .add("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
        .add("Accept-Language", "en-US,en;q=0.8")
        .build()

    /**
     * Fetch the recommendations for a given series id and parse out the
     * related series titles. The endpoint returns HTML, so we use a
     * lightweight regex-based parser that looks for the "Recommendations"
     * list items. This is intentionally lenient — NovelUpdates changes
     * markup occasionally, and silently returning fewer/no items is
     * better than failing the whole pipeline.
     */
    private suspend fun fetchRecommendations(seriesSlug: String): List<NuRecommendation> {
        val url = "https://www.novelupdates.com/series/$seriesSlug/"
        val headers = Headers.Builder()
            .add("User-Agent", "Mozilla/5.0 (compatible)")
            .build()
        val request = GET(url, headers = headers)
        val body = client.newCall(request).awaitSuccess().body.string()
        return parseRecommendationsHtml(body, seriesSlug)
    }

    internal fun parseRecommendationsHtml(body: String, seriesSlug: String): List<NuRecommendation> {
        if (body.isBlank()) return emptyList()
        val doc = Jsoup.parse(body)
        val recHeading = doc.select("h1, h2, h3, h4, h5, h6")
            .firstOrNull { el ->
                val text = el.text().trim()
                text.equals("Recommendations", ignoreCase = true) ||
                    text.equals("Series Recommendations", ignoreCase = true)
            }
        val links = if (recHeading != null) {
            buildList {
                var sibling = recHeading.nextElementSibling()
                while (sibling != null) {
                    val tag = sibling.tagName().lowercase()
                    if (tag in HEADING_TAGS) break
                    if (tag == "a") {
                        add(sibling)
                    } else {
                        addAll(sibling.select("a[href]"))
                    }
                    sibling = sibling.nextElementSibling()
                }
            }
        } else {
            emptyList()
        }
        val slugRegex = Regex("""https?://(?:www\.)?novelupdates\.com/series/([^/?#"]+)/?""")
        val extracted = links.mapNotNull { link ->
            val href = link.attr("href")
            val slug = slugRegex.find(href)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val title = link.text().trim()
            if (slug.isEmpty() || title.isEmpty() || slug == seriesSlug) {
                null
            } else {
                NuRecommendation(id = slug, title = title)
            }
        }
        return extracted.distinctBy { it.id }
    }

    @Serializable
    private data class NuSeriesDto(
        val id: Int = 0,
        val title: Rendered? = null,
    )

    @Serializable
    private data class Rendered(val rendered: String = "")

    private data class NuSeriesStub(val id: String, val title: String)

    internal data class NuRecommendation(val id: String, val title: String)

    private companion object {
        val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    }
}
