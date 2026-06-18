package eu.kanade.tachiyomi.data.suggestions.sources

import eu.kanade.tachiyomi.data.suggestions.SuggestionCache
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionReason
import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed
import eu.kanade.tachiyomi.data.suggestions.SuggestionTitleResolver
import eu.kanade.tachiyomi.data.suggestions.sources.dto.MuSearchResponse
import eu.kanade.tachiyomi.data.suggestions.sources.dto.MuSearchResult
import eu.kanade.tachiyomi.data.suggestions.sources.dto.MuSeriesDetail
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaUpdatesSimilarSource(
    override val mediaType: SuggestionMediaType,
) : RecommendationPagingSource() {

    override val name: String = "MangaUpdates"

    private val client by lazy { Injekt.get<NetworkHelper>().client }
    private val json by lazy { Injekt.get<Json>() }

    private val allowedTypes = setOf("Manga", "Manhwa", "Manhua", "Comic", "Webtoon", "Novel")

    override suspend fun fetchSuggestions(seed: SuggestionSeed): List<SuggestionItem> = coroutineScope {
        // Enabled for both MANGA and NOVEL: MangaUpdates has a large light-novel
        // catalogue (type "Novel") that the old code was throwing away.
        if (mediaType != SuggestionMediaType.MANGA && mediaType != SuggestionMediaType.NOVEL) {
            return@coroutineScope emptyList()
        }

        val cacheKey = SuggestionCache.makeKey(
            name,
            seed.primaryTitle,
            mediaType.name,
            seed.candidateTitles,
            seed.description,
            seed.author,
        )
        SuggestionCache.get(cacheKey)?.let {
            logcat { "[MangaUpdates] CACHE HIT for '${seed.primaryTitle}' (${seed.candidateTitles.size} candidates)" }
            return@coroutineScope it
        }
        logcat { "[MangaUpdates] START fetching for '${seed.primaryTitle}', candidates=${seed.candidateTitles}" }

        val suggestions = try {
            val allSearchResults = mutableListOf<MuSearchResult>()

            for (candidate in seed.candidateTitles.take(3)) {
                try {
                    logcat { "MangaUpdates suggestions: searching for '$candidate'" }
                    val payload = buildJsonObject {
                        put("search", candidate)
                        put("page", 1)
                        put("perpage", 5)
                    }
                    val body = payload.toString().toRequestBody(jsonMime)

                    val searchResponse = client.newCall(
                        POST("https://api.mangaupdates.com/v1/series/search", body = body),
                    )
                        .awaitSuccess()
                        .parseAs<MuSearchResponse>(json)

                    allSearchResults.addAll(searchResponse.results)
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat { "MangaUpdates search failed for candidate '$candidate': ${e.message}" }
                }
            }

            val entryWithScore = allSearchResults.distinctBy { it.record.seriesId }.mapNotNull { result ->
                val record = result.record
                val type = record.type
                if (type != null && type !in allowedTypes) return@mapNotNull null

                val titles = listOfNotNull(record.title, result.hitTitle)
                val maxScore = titles.maxOfOrNull { t ->
                    seed.candidateTitles.maxOfOrNull { candidate ->
                        SuggestionTitleResolver.scoreMatch(t, candidate)
                    } ?: 0
                } ?: 0

                Pair(result, maxScore)
            }

            val bestMatch = entryWithScore
                .filter { it.second > 0 }
                .maxByOrNull { it.second }
                ?.first
                ?: return@coroutineScope emptyList()

            logcat {
                "[MangaUpdates] Base manga selected: '${bestMatch.record.title}' (ID=${bestMatch.record.seriesId}) for '${seed.primaryTitle}'"
            }

            val recUrl = "https://api.mangaupdates.com/v1/series/${bestMatch.record.seriesId}"
            val detailResponse = client.newCall(GET(recUrl))
                .awaitSuccess()
                .parseAs<MuSeriesDetail>(json)

            val mainRecs = detailResponse.recommendations.sortedByDescending { it.weight }
            val catRecs = detailResponse.categoryRecommendations.sortedByDescending { it.weight }
            val mergedRecs = (mainRecs + catRecs).distinctBy { it.seriesId }.take(8)

            // Validate type for each recommended series in parallel with capped parallelism
            mergedRecs.map { rec ->
                async {
                    try {
                        val detailUrl = "https://api.mangaupdates.com/v1/series/${rec.seriesId}"
                        val recDetail = client.newCall(GET(detailUrl))
                            .awaitSuccess()
                            .parseAs<MuSeriesDetail>(json)

                        if (recDetail.type in allowedTypes) {
                            SuggestionItem(
                                title = rec.seriesName,
                                searchQueries = listOf(rec.seriesName),
                                thumbnailUrl = rec.seriesImage?.url?.thumb ?: rec.seriesImage?.url?.original,
                                providerName = name,
                                providerUrl = rec.seriesUrl,
                                providerId = rec.seriesId.toString(),
                                mediaType = mediaType,
                                reason = SuggestionReason.EXTERNAL_MU,
                            )
                        } else {
                            null
                        }
                    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logcat { "[MangaUpdates] rec detail fetch failed for series=${rec.seriesId}: ${e.message}" }
                        null
                    }
                }
            }.mapNotNull { it.await() }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[MangaUpdates] ERROR for '${seed.primaryTitle}': ${e.message}" }
            emptyList()
        }

        logcat { "[MangaUpdates] END for '${seed.primaryTitle}': ${suggestions.size} suggestions" }

        if (suggestions.isNotEmpty()) {
            SuggestionCache.put(cacheKey, suggestions)
        }
        suggestions
    }
}
