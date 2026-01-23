package tachiyomi.data.shikimori

import dataanime.Shikimori_metadata_cache
import logcat.LogPriority
import logcat.logcat
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.shikimori.model.ShikimoriMetadata

class ShikimoriMetadataCache(
    private val handler: AnimeDatabaseHandler,
) {

    suspend fun get(animeId: Long): ShikimoriMetadata? {
        return try {
            handler.awaitOneOrNull {
                shikimori_metadata_cacheQueries.getByAnimeId(animeId)
            }?.toShikimoriMetadata()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to get Shikimori cache for anime $animeId: ${e.message}" }
            null
        }
    }

    suspend fun upsert(metadata: ShikimoriMetadata) {
        try {
            handler.await {
                shikimori_metadata_cacheQueries.upsert(
                    anime_id = metadata.animeId,
                    shikimori_id = metadata.shikimoriId,
                    score = metadata.score,
                    kind = metadata.kind,
                    cover_url = metadata.coverUrl,
                    search_query = metadata.searchQuery,
                    updated_at = metadata.updatedAt,
                    is_manual_match = metadata.isManualMatch,
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to upsert Shikimori cache: ${e.message}" }
        }
    }

    suspend fun delete(animeId: Long) {
        try {
            handler.await {
                shikimori_metadata_cacheQueries.deleteByAnimeId(animeId)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to delete Shikimori cache: ${e.message}" }
        }
    }

    suspend fun clearAll() {
        try {
            handler.await {
                shikimori_metadata_cacheQueries.clearAll()
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to clear Shikimori cache: ${e.message}" }
        }
    }

    suspend fun deleteStaleEntries() {
        try {
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            handler.await {
                shikimori_metadata_cacheQueries.deleteStaleEntries(thirtyDaysAgo)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to delete stale entries: ${e.message}" }
        }
    }
}

// Extension function to convert SQLDelight model to domain model
private fun Shikimori_metadata_cache.toShikimoriMetadata(): ShikimoriMetadata {
    return ShikimoriMetadata(
        animeId = anime_id,
        shikimoriId = shikimori_id,
        score = score,
        kind = kind,
        coverUrl = cover_url,
        searchQuery = search_query,
        updatedAt = updated_at,
        isManualMatch = is_manual_match ?: false,
    )
}
