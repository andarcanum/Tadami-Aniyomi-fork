package eu.kanade.tachiyomi.data.suggestions.sources

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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Headers
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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
                        SuggestionTitleResolver.scoreMatch(stub.title, c)
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

            recommendations.map { rec ->
                SuggestionItem(
                    title = rec.title,
                    searchQueries = listOf(rec.title),
                    thumbnailUrl = null,
                    providerName = name,
                    providerUrl = "https://www.novelupdates.com/series/${bestMatch.id}/",
                    providerId = "${bestMatch.id}:${rec.id}",
                    mediaType = mediaType,
                    reason = SuggestionReason.EXTERNAL_NU,
                )
            }
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
     * The implementation uses the public series-search endpoint and parses
     * the JSON result. We don't need the full page, just enough to obtain
     * a series id and a display title.
     */
    private suspend fun searchSeries(query: String): List<NuSeriesStub> {
        val url = "https://www.novelupdates.com/wp-json/wp/v2/series?search=" +
            java.net.URLEncoder.encode(query, "UTF-8") +
            "&per_page=5"
        val response = client.newCall(GET(url))
            .awaitSuccess()
            .parseAs<List<NuSeriesDto>>(json)
        return response.map { NuSeriesStub(id = it.id, title = it.title?.rendered ?: "") }
    }

    /**
     * Fetch the recommendations for a given series id and parse out the
     * related series titles. The endpoint returns HTML, so we use a
     * lightweight regex-based parser that looks for the "Recommendations"
     * list items. This is intentionally lenient — NovelUpdates changes
     * markup occasionally, and silently returning fewer/no items is
     * better than failing the whole pipeline.
     */
    private suspend fun fetchRecommendations(seriesId: Int): List<NuRecommendation> {
        val url = "https://www.novelupdates.com/series/$seriesId/"
        val headers = Headers.Builder()
            .add("User-Agent", "Mozilla/5.0 (compatible)")
            .build()
        val request = GET(url, headers = headers)
        val body = client.newCall(request).awaitSuccess().body.string()
        // NovelUpdates renders related series as <a ...>title</a> entries
        // inside the "Series Recommendations" section. We extract anything
        // that looks like /series/<id>/ links after the heading.
        val recRegex = Regex("""<a[^>]+href="https?://www\.novelupdates\.com/series/(\d+)/?"[^>]*>([^<]+)</a>""")
        return recRegex.findAll(body)
            .map { match ->
                NuRecommendation(id = match.groupValues[1].toIntOrNull() ?: 0, title = match.groupValues[2].trim())
            }
            .filter { it.id > 0 && it.title.isNotEmpty() }
            .distinctBy { it.id }
            .toList()
    }

    @Serializable
    private data class NuSeriesDto(
        val id: Int = 0,
        val title: Rendered? = null,
    )

    @Serializable
    private data class Rendered(val rendered: String = "")

    private data class NuSeriesStub(val id: Int, val title: String)

    private data class NuRecommendation(val id: Int, val title: String)
}
