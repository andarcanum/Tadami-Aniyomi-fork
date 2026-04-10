package tachiyomi.data.metadata

import data.External_metadata_cache
import logcat.LogPriority
import logcat.logcat
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.metadata.cache.ExternalMetadataCache
import tachiyomi.domain.metadata.model.ExternalMetadata
import tachiyomi.domain.metadata.model.MetadataContentType
import tachiyomi.domain.metadata.model.MetadataSource

class MangaExternalMetadataCache(
    private val handler: MangaDatabaseHandler,
) : ExternalMetadataCache {

    override suspend fun get(
        contentType: MetadataContentType,
        mediaId: Long,
        source: MetadataSource,
    ): ExternalMetadata? {
        return try {
            handler.awaitOneOrNull { db ->
                db.external_metadata_cacheQueries.getByMediaIdAndSource(
                    contentType = contentType.name,
                    mediaId = mediaId,
                    source = source.name,
                )
            }?.toExternalMetadata()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "Failed to get metadata cache for $contentType $mediaId $source: ${e.message}"
            }
            null
        }
    }

    override suspend fun upsert(metadata: ExternalMetadata) {
        try {
            handler.await { db ->
                db.external_metadata_cacheQueries.upsert(
                    metadata.contentType.name,
                    metadata.mediaId,
                    metadata.source.name,
                    metadata.remoteId,
                    metadata.score,
                    metadata.format,
                    metadata.status,
                    metadata.coverUrl,
                    metadata.coverUrlFallback,
                    metadata.searchQuery,
                    metadata.updatedAt,
                    metadata.isManualMatch,
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "Failed to upsert metadata cache for ${metadata.contentType} ${metadata.mediaId}: ${e.message}"
            }
        }
    }

    override suspend fun delete(
        contentType: MetadataContentType,
        mediaId: Long,
        source: MetadataSource,
    ) {
        try {
            handler.await { db ->
                db.external_metadata_cacheQueries.deleteByMediaIdAndSource(
                    contentType = contentType.name,
                    mediaId = mediaId,
                    source = source.name,
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "Failed to delete metadata cache for $contentType $mediaId $source: ${e.message}"
            }
        }
    }

    override suspend fun clearAll() {
        try {
            handler.await { db ->
                db.external_metadata_cacheQueries.clearAll()
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to clear metadata cache: ${e.message}" }
        }
    }

    override suspend fun deleteStaleEntries() {
        try {
            val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
            handler.await { db ->
                db.external_metadata_cacheQueries.deleteStaleEntries(sevenDaysAgo)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to delete stale metadata cache: ${e.message}" }
        }
    }
}

private fun External_metadata_cache.toExternalMetadata(): ExternalMetadata {
    return ExternalMetadata(
        contentType = MetadataContentType.valueOf(content_type),
        source = MetadataSource.valueOf(source),
        mediaId = media_id,
        remoteId = remote_id,
        score = score,
        format = format,
        status = status,
        coverUrl = cover_url,
        coverUrlFallback = cover_url_fallback,
        searchQuery = search_query,
        updatedAt = updated_at,
        isManualMatch = is_manual_match ?: false,
    )
}
