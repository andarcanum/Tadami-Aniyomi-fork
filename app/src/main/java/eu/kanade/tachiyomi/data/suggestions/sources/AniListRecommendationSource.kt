package eu.kanade.tachiyomi.data.suggestions.sources

import eu.kanade.tachiyomi.data.suggestions.SuggestionCache
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionReason
import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed
import eu.kanade.tachiyomi.data.suggestions.SuggestionTitleResolver
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AniListRecommendationSource(
    override val mediaType: SuggestionMediaType,
) : RecommendationPagingSource() {

    override val name: String = "AniList"

    private val client by lazy { Injekt.get<NetworkHelper>().client }
    private val json by lazy { Injekt.get<Json>() }
    private val aniListType = mediaType.toAniListType()

    private fun isValidMediaType(type: String?, format: String?): Boolean {
        val upperType = type?.uppercase()
        val upperFormat = format?.uppercase()
        return when (mediaType) {
            SuggestionMediaType.ANIME -> upperType == "ANIME"
            SuggestionMediaType.MANGA -> upperType == "MANGA" && upperFormat != "NOVEL"
            SuggestionMediaType.NOVEL -> upperType == "MANGA" || upperType == "ANIME"
        }
    }

    private fun isValidRecommendationType(type: String?, format: String?): Boolean {
        val upperType = type?.uppercase()
        val upperFormat = format?.uppercase()
        return when (mediaType) {
            SuggestionMediaType.ANIME -> upperType == "ANIME"
            SuggestionMediaType.MANGA -> upperType == "MANGA" && upperFormat != "NOVEL"
            SuggestionMediaType.NOVEL -> upperType == "MANGA"
        }
    }

    override suspend fun fetchSuggestions(seed: SuggestionSeed): List<SuggestionItem> = coroutineScope {
        val cacheKey = SuggestionCache.makeKey(
            name,
            seed.primaryTitle,
            mediaType.name,
            seed.candidateTitles,
            seed.description,
            seed.author,
        )
        SuggestionCache.get(cacheKey)?.let {
            logcat {
                "[AniList] CACHE HIT for '${seed.primaryTitle}' (${seed.candidateTitles.size} candidates, type=$mediaType)"
            }
            matchedBase = true
            return@coroutineScope it
        }
        logcat { "[AniList] START fetching for '${seed.primaryTitle}', candidates=${seed.candidateTitles}" }

        val candidatesToFetch = if (mediaType == SuggestionMediaType.NOVEL) {
            // For novels we keep the original Cyrillic title as the FIRST
            // candidate: it often matches the catalogue on AniList exactly,
            // and only fall back to Latin variants if the base media can't
            // be found.
            val primaryFirst = listOf(seed.primaryTitle) + seed.candidateTitles.filter { it != seed.primaryTitle }
            primaryFirst
        } else {
            seed.candidateTitles
        }

        val query = """
            query Recommendations(${'$'}search: String!, ${'$'}type: MediaType!) {
                Page {
                    media(search: ${'$'}search, type: ${'$'}type) {
                        id
                        type
                        format
                        title { romaji english native }
                        synonyms
                        recommendations {
                            edges {
                                node {
                                    mediaRecommendation {
                                        id
                                        type
                                        format
                                        siteUrl
                                        title { romaji english native }
                                        coverImage { large }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        suspend fun fetchForType(type: String): List<JsonObject> {
            // Reduced from 5 -> 3 candidates per type. Each request hits the
            // public AniList API, which is rate-limited; 3 is enough to cover
            // the Cyrillic primary + the most relevant Latin variants while
            // leaving headroom for the ANIME fallback below.
            val jobs = candidatesToFetch.take(3).map { candidate ->
                async {
                    try {
                        val payload = buildJsonObject {
                            put("query", query)
                            put(
                                "variables",
                                buildJsonObject {
                                    put("search", candidate)
                                    put("type", type)
                                },
                            )
                        }
                        val body = payload.toString().toRequestBody(jsonMime)
                        val data = client.newCall(POST("https://graphql.anilist.co/", body = body))
                            .awaitSuccess()
                            .parseAs<JsonObject>(json)

                        data["data"]?.jsonObject
                            ?.get("Page")?.jsonObject
                            ?.get("media")?.jsonArray
                            ?.mapNotNull { it.jsonObject }
                            ?: emptyList()
                    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logcat { "AniList query failed for candidate '$candidate' type=$type: ${e.message}" }
                        emptyList()
                    }
                }
            }
            return jobs.awaitAll().flatten()
        }

        // Primary search with the correct media type
        var allResults = fetchForType(aniListType).distinctBy { it["id"]?.jsonPrimitive?.contentOrNull }

        // For NOVEL: also search ANIME type as fallback — many novels have anime adaptations
        // whose recommendation edges may contain other novels
        if (mediaType == SuggestionMediaType.NOVEL && allResults.isEmpty()) {
            logcat { "[AniList] No MANGA results for '${seed.primaryTitle}', trying ANIME type fallback" }
            val animeResults = fetchForType("ANIME").distinctBy { it["id"]?.jsonPrimitive?.contentOrNull }
            if (animeResults.isNotEmpty()) {
                logcat { "[AniList] ANIME fallback found ${animeResults.size} base media for '${seed.primaryTitle}'" }
                allResults = animeResults
            }
        }

        // F3.1: Genre-based fallback for NOVEL. If we still have no base
        // media after the title + ANIME fallbacks, search AniList by the
        // novel's primary genre and take the top-N by averageScore. This
        // gives us *something* to show even when the title is unknown
        // (e.g. KR/CN web-novels that never made it onto AniList).
        if (mediaType == SuggestionMediaType.NOVEL && allResults.isEmpty() && !seed.genres.isNullOrEmpty()) {
            logcat { "[AniList] Genre fallback for '${seed.primaryTitle}': genres=${seed.genres}" }
            val genreResults = fetchByGenre(seed.genres)
            if (genreResults.isNotEmpty()) {
                logcat { "[AniList] Genre fallback found ${genreResults.size} media for '${seed.primaryTitle}'" }
                allResults = genreResults
            }
        }

        logcat { "[AniList] Fetched ${allResults.size} base media before filtering for '${seed.primaryTitle}'" }

        if (mediaType == SuggestionMediaType.NOVEL) {
            val discardedCount = allResults.count { media ->
                val type = media["type"]?.jsonPrimitive?.contentOrNull
                val upperType = type?.uppercase()
                upperType != "MANGA" && upperType != "ANIME"
            }
            logcat { "[AniList] Discarded $discardedCount base media entries because they were not MANGA/ANIME type" }
        }

        val mediaWithScore = allResults.mapNotNull { media ->
            val id = media["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val type = media["type"]?.jsonPrimitive?.contentOrNull
            val format = media["format"]?.jsonPrimitive?.contentOrNull
            if (!isValidMediaType(type, format)) return@mapNotNull null

            // Extract all title variations + synonyms
            val titleObj = media["title"]?.jsonObject
            val romaji = titleObj?.get("romaji")?.jsonPrimitive?.contentOrNull
            val english = titleObj?.get("english")?.jsonPrimitive?.contentOrNull
            val native = titleObj?.get("native")?.jsonPrimitive?.contentOrNull
            val synonyms = media["synonyms"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

            val mediaTitles = listOfNotNull(romaji, english, native) + synonyms
            val maxScore = mediaTitles.maxOfOrNull { mediaTitle ->
                seed.candidateTitles.maxOfOrNull { candidate ->
                    SuggestionTitleResolver.scoreMatch(mediaTitle, candidate)
                } ?: 0
            } ?: 0

            Pair(media, maxScore)
        }

        val bestBaseMedia = mediaWithScore
            .filter { it.second > 0 }
            .maxByOrNull { it.second }

        if (bestBaseMedia != null) {
            matchedBase = true
            val media = bestBaseMedia.first
            val titleObj = media["title"]?.jsonObject
            val romaji = titleObj?.get("romaji")?.jsonPrimitive?.contentOrNull
            val english = titleObj?.get("english")?.jsonPrimitive?.contentOrNull
            val native = titleObj?.get("native")?.jsonPrimitive?.contentOrNull
            val synonyms = media["synonyms"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            val mediaTitles = listOfNotNull(romaji, english, native) + synonyms

            var winningCandidate: String? = null
            var bestScore = 0
            for (candidate in seed.candidateTitles) {
                for (mediaTitle in mediaTitles) {
                    val score = SuggestionTitleResolver.scoreMatch(mediaTitle, candidate)
                    if (score > bestScore) {
                        bestScore = score
                        winningCandidate = candidate
                    }
                }
            }

            val winnerTitle = english ?: romaji ?: "unknown"
            logcat {
                "[AniList] Base media selected: '$winnerTitle' (score=${bestBaseMedia.second}) for '${seed.primaryTitle}' | Winning candidate: '$winningCandidate'"
            }
        } else {
            logcat { "[AniList] No base media matched for '${seed.primaryTitle}' (all candidates scored 0)" }
            logcat { "[AniList] Candidates: ${seed.candidateTitles}" }
        }

        val suggestions = if (bestBaseMedia != null) {
            val filteredTypeCount = intArrayOf(0)
            val edges = bestBaseMedia.first["recommendations"]?.jsonObject
                ?.get("edges")?.jsonArray ?: emptyList()

            edges.mapNotNull { edge ->
                val rec = edge.jsonObject["node"]?.jsonObject
                    ?.get("mediaRecommendation")?.takeIf { it is JsonObject }?.jsonObject
                    ?: return@mapNotNull null

                val recId = rec["id"]?.jsonPrimitive?.contentOrNull
                val recType = rec["type"]?.jsonPrimitive?.contentOrNull
                val recFormat = rec["format"]?.jsonPrimitive?.contentOrNull
                if (!isValidRecommendationType(recType, recFormat)) {
                    filteredTypeCount[0]++
                    return@mapNotNull null
                }

                val siteUrl = rec["siteUrl"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val recTitle = rec["title"]?.jsonObject?.let { t ->
                    t["english"]?.jsonPrimitive?.contentOrNull
                        ?: t["romaji"]?.jsonPrimitive?.contentOrNull
                        ?: t["native"]?.jsonPrimitive?.contentOrNull
                } ?: return@mapNotNull null

                val thumbnailUrl = rec["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.contentOrNull

                SuggestionItem(
                    title = recTitle,
                    searchQueries = listOf(recTitle),
                    thumbnailUrl = thumbnailUrl,
                    providerName = name,
                    providerUrl = siteUrl,
                    providerId = recId,
                    mediaType = mediaType,
                    reason = SuggestionReason.EXTERNAL_ANILIST,
                )
            }
        } else {
            emptyList()
        }

        logcat { "[AniList] END for '${seed.primaryTitle}': ${suggestions.size} suggestions, type=$mediaType" }
        if (suggestions.isNotEmpty()) {
            SuggestionCache.put(cacheKey, suggestions)
        }
        suggestions
    }

    /**
     * F3.1 — Genre-based AniList search.
     *
     * Returns the top [limit] novels sorted by `averageScore` that match
     * the given list of genres. The result is a list of JSON "media"
     * entries shaped the same way as the title-search result, so the
     * downstream code can fall through to the recommendation-edge
     * extraction path without changes.
     */
    private suspend fun fetchByGenre(genres: List<String>, limit: Int = 10): List<JsonObject> = coroutineScope {
        val anilistGenres = genres
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.replace(Regex("\\s+"), "_").uppercase() }
            .distinct()
            .take(3)

        if (anilistGenres.isEmpty()) return@coroutineScope emptyList()

        val query = """
            query GenreTop(${'$'}genres: [String], ${'$'}perPage: Int) {
                Page(perPage: ${'$'}perPage) {
                    media(genre_in: ${'$'}genres, type: MANGA, sort: SCORE_DESC) {
                        id
                        type
                        format
                        title { romaji english native }
                        synonyms
                        averageScore
                        recommendations {
                            edges {
                                node {
                                    mediaRecommendation {
                                        id
                                        type
                                        format
                                        siteUrl
                                        title { romaji english native }
                                        coverImage { large }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        try {
            val payload = buildJsonObject {
                put("query", query)
                put(
                    "variables",
                    buildJsonObject {
                        put(
                            "genres",
                            buildJsonArray {
                                anilistGenres.forEach { add(it) }
                            },
                        )
                        put("perPage", limit)
                    },
                )
            }
            val body = payload.toString().toRequestBody(jsonMime)
            val data = client.newCall(POST("https://graphql.anilist.co/", body = body))
                .awaitSuccess()
                .parseAs<JsonObject>(json)
            data["data"]?.jsonObject
                ?.get("Page")?.jsonObject
                ?.get("media")?.jsonArray
                ?.mapNotNull { it.jsonObject }
                ?: emptyList()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[AniList] genre search failed for $anilistGenres: ${e.message}" }
            emptyList()
        }
    }
}
