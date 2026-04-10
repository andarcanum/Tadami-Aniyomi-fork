package eu.kanade.domain.metadata.interactor

import logcat.LogPriority
import logcat.logcat
import tachiyomi.domain.metadata.cache.ExternalMetadataCache
import tachiyomi.domain.metadata.model.ExternalMetadata
import tachiyomi.domain.metadata.model.MetadataContentType
import tachiyomi.domain.metadata.model.MetadataSource
import kotlin.math.roundToInt

data class MetadataTarget(
    val mediaId: Long,
    val title: String,
    val description: String?,
)

interface MetadataAdapter<Track : Any, Remote : Any> {
    val contentType: MetadataContentType
    val source: MetadataSource
    val trackerId: Long

    suspend fun getTracks(mediaId: Long): List<Track>

    fun trackTrackerId(track: Track): Long

    fun trackRemoteId(track: Track): Long

    suspend fun fetchById(remoteId: Long): Remote?

    suspend fun search(query: String): List<Remote>

    fun remoteId(remote: Remote): Long

    fun candidateTitles(remote: Remote): List<String>

    suspend fun map(
        target: MetadataTarget,
        remote: Remote,
        searchQuery: String,
        isManualMatch: Boolean,
    ): ExternalMetadata

    fun isNotAuthenticated(error: Throwable): Boolean
}

class MetadataResolver<Track : Any, Remote : Any>(
    private val cache: ExternalMetadataCache,
    private val adapter: MetadataAdapter<Track, Remote>,
) {
    suspend fun await(target: MetadataTarget): ExternalMetadata? {
        val searchQueries = buildSearchQueries(target)
        val cached = cache.get(adapter.contentType, target.mediaId, adapter.source)
        if (cached != null && !cached.isStale() && shouldUseCachedResult(cached, searchQueries)) {
            logcat(LogPriority.DEBUG) {
                "Metadata cache hit for ${adapter.contentType} ${target.mediaId} ${adapter.source}: query='${cached.searchQuery}'"
            }
            return cached
        }

        if (cached != null && !cached.isStale()) {
            logcat(LogPriority.DEBUG) {
                "Metadata cache bypass for ${adapter.contentType} ${target.mediaId} ${adapter.source}: cachedQuery='${cached.searchQuery}', currentQueries=$searchQueries"
            }
        }

        val fromTracking = getFromTracking(target)
        if (fromTracking != null) {
            cache.upsert(fromTracking)
            return fromTracking
        }

        val fromSearch = searchAndCache(target)
        if (fromSearch != null) {
            return fromSearch
        }

        cacheNotFound(target)
        return null
    }

    private suspend fun getFromTracking(target: MetadataTarget): ExternalMetadata? {
        return try {
            val track = adapter.getTracks(target.mediaId)
                .firstOrNull { adapter.trackTrackerId(it) == adapter.trackerId && adapter.trackRemoteId(it) > 0 }
                ?: return null

            val remoteId = adapter.trackRemoteId(track)
            val remote = adapter.fetchById(remoteId)
                ?: return null

            adapter.map(
                target = target,
                remote = remote,
                searchQuery = "tracking:$remoteId",
                isManualMatch = true,
            )
        } catch (e: Exception) {
            if (adapter.isNotAuthenticated(e)) {
                throw e
            }
            null
        }
    }

    private suspend fun searchAndCache(target: MetadataTarget): ExternalMetadata? {
        return try {
            val searchQueries = buildSearchQueries(target)
            val candidates = linkedMapOf<Long, ScoredRemote<Remote>>()

            for (query in searchQueries) {
                val results = adapter.search(query)
                if (results.isNotEmpty()) {
                    logcat(LogPriority.DEBUG) {
                        "Metadata search hit for ${adapter.contentType} ${target.mediaId} ${adapter.source}: query='$query', results=${results.size}"
                    }
                }

                results.forEachIndexed { resultIndex, remote ->
                    val remoteId = adapter.remoteId(remote)
                    val scored = scoreRemote(searchQueries, query, resultIndex, remote)
                    val current = candidates[remoteId]
                    if (current == null || scored.score > current.score) {
                        candidates[remoteId] = scored
                    }
                }
            }

            val rankedCandidates = candidates.values
                .sortedWith(
                    compareByDescending<ScoredRemote<Remote>> { it.selectionScore }
                        .thenBy { it.remoteOrder },
                )

            val best = rankedCandidates.firstOrNull() ?: return null
            val second = rankedCandidates.getOrNull(1)

            if (second != null && best.selectionScore == second.selectionScore) {
                logcat(LogPriority.DEBUG) {
                    "Metadata search ambiguous for ${adapter.contentType} ${target.mediaId} ${adapter.source}: bestScore=${best.score}, secondScore=${second.score}"
                }
                return null
            }

            logcat(LogPriority.DEBUG) {
                buildString {
                    append("Metadata search selected for ")
                    append(adapter.contentType)
                    append(" ")
                    append(target.mediaId)
                    append(" ")
                    append(adapter.source)
                    append(": remoteId=")
                    append(adapter.remoteId(best.remote))
                    append(", query='")
                    append(best.searchQuery)
                    append("', score=")
                    append(best.score)
                }
            }

            val metadata = adapter.map(
                target = target,
                remote = best.remote,
                searchQuery = best.searchQuery,
                isManualMatch = false,
            )

            cache.upsert(metadata)
            metadata
        } catch (e: Exception) {
            if (adapter.isNotAuthenticated(e)) {
                throw e
            }
            null
        }
    }

    private fun buildSearchQueries(target: MetadataTarget): List<String> {
        val originalTitle = parseOriginalTitle(target.description)
        return buildList {
            originalTitle?.let { add(normalizeMetadataSearchQuery(it)) }
            add(normalizeMetadataSearchQuery(target.title))
        }.distinct()
    }

    private fun scoreRemote(
        searchQueries: List<String>,
        query: String,
        resultIndex: Int,
        remote: Remote,
    ): ScoredRemote<Remote> {
        val titles = adapter.candidateTitles(remote)
        val scoredByTitles = searchQueries.mapIndexed { queryIndex, candidateQuery ->
            scoreTextMatch(candidateQuery, titles).let { match ->
                ScoreMatch(
                    score = match.score,
                    query = candidateQuery,
                    queryIndex = queryIndex,
                    titleIndex = match.titleIndex,
                    selectionScore = match.score * 1000 - match.titleIndex * 100 - queryIndex,
                )
            }
        }

        val bestMatch = scoredByTitles.maxWithOrNull(
            compareByDescending<ScoreMatch> { it.selectionScore },
        ) ?: ScoreMatch(
            score = 0,
            query = query,
            queryIndex = searchQueries.indexOf(query).coerceAtLeast(0),
            titleIndex = Int.MAX_VALUE,
            selectionScore = 0,
        )

        return ScoredRemote(
            remote = remote,
            searchQuery = bestMatch.query,
            score = bestMatch.score,
            queryIndex = bestMatch.queryIndex,
            titleIndex = bestMatch.titleIndex,
            selectionScore = bestMatch.selectionScore,
            remoteOrder = resultIndex,
        )
    }

    private fun scoreTextMatch(query: String, titles: List<String>): TitleMatchScore {
        val normalizedQuery = normalizeSearchKey(query)
        if (normalizedQuery.isBlank()) return TitleMatchScore(score = 0, titleIndex = Int.MAX_VALUE)

        return titles.mapIndexed { titleIndex, title ->
            TitleMatchScore(
                score = scoreTextPair(normalizedQuery, normalizeSearchKey(title)),
                titleIndex = titleIndex,
            )
        }.maxWithOrNull(
            compareBy<TitleMatchScore> { it.score }
                .thenBy { -it.titleIndex },
        ) ?: TitleMatchScore(score = 0, titleIndex = Int.MAX_VALUE)
    }

    private fun scoreTextPair(query: String, candidate: String): Int {
        if (query.isBlank() || candidate.isBlank()) return 0

        return when {
            query == candidate -> 100
            query.startsWith(candidate) || candidate.startsWith(query) -> 85
            query.contains(candidate) || candidate.contains(query) -> 70
            else -> tokenOverlapScore(query, candidate)
        }
    }

    private fun tokenOverlapScore(query: String, candidate: String): Int {
        val queryTokens = tokenize(query)
        val candidateTokens = tokenize(candidate)
        if (queryTokens.isEmpty() || candidateTokens.isEmpty()) return 0

        val overlap = queryTokens.intersect(candidateTokens).size.toDouble()
        val denominator = maxOf(queryTokens.size, candidateTokens.size).toDouble()
        return (overlap / denominator * 60.0).roundToInt()
    }

    private fun tokenize(value: String): Set<String> {
        return value.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length > 2 }
            .toSet()
    }

    private fun normalizeSearchKey(value: String): String {
        return normalizeMetadataSearchQuery(value)
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun shouldUseCachedResult(
        cached: ExternalMetadata,
        searchQueries: List<String>,
    ): Boolean {
        if (cached.isManualMatch || cached.searchQuery.startsWith("tracking:", ignoreCase = true)) {
            return true
        }

        return cached.searchQuery in searchQueries
    }

    private suspend fun cacheNotFound(target: MetadataTarget) {
        cache.upsert(
            ExternalMetadata(
                contentType = adapter.contentType,
                source = adapter.source,
                mediaId = target.mediaId,
                remoteId = null,
                score = null,
                format = null,
                status = null,
                coverUrl = null,
                coverUrlFallback = null,
                searchQuery = target.title,
                updatedAt = System.currentTimeMillis(),
                isManualMatch = false,
            ),
        )
    }

    private data class ScoreMatch(
        val score: Int,
        val query: String,
        val queryIndex: Int,
        val titleIndex: Int,
        val selectionScore: Int,
    )

    private data class TitleMatchScore(
        val score: Int,
        val titleIndex: Int,
    )

    private data class ScoredRemote<Remote>(
        val remote: Remote,
        val searchQuery: String,
        val score: Int,
        val queryIndex: Int,
        val titleIndex: Int,
        val selectionScore: Int,
        val remoteOrder: Int,
    )
}
