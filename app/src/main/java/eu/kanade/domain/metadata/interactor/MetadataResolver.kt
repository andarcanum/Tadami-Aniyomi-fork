package eu.kanade.domain.metadata.interactor

import logcat.LogPriority
import logcat.logcat
import tachiyomi.domain.metadata.cache.ExternalMetadataCache
import tachiyomi.domain.metadata.model.ExternalMetadata
import tachiyomi.domain.metadata.model.MetadataContentType
import tachiyomi.domain.metadata.model.MetadataSource

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

            var firstResult: Remote? = null
            var usedQuery: String? = null

            for (query in searchQueries) {
                val results = adapter.search(query)
                if (results.isNotEmpty()) {
                    firstResult = results.first()
                    usedQuery = query
                    logcat(LogPriority.DEBUG) {
                        "Metadata search hit for ${adapter.contentType} ${target.mediaId} ${adapter.source}: query='$query', results=${results.size}"
                    }
                    break
                }
            }

            val remote = firstResult ?: return null
            val metadata = adapter.map(
                target = target,
                remote = remote,
                searchQuery = usedQuery ?: target.title,
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
}
