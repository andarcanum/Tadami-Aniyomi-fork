package tachiyomi.data.anilist

import dataanime.Anilist_metadata_cache
import logcat.LogPriority
import logcat.logcat
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.anilist.model.AnilistMetadata

class AnilistMetadataCache(
    private val handler: AnimeDatabaseHandler,
) {

    suspend fun get(animeId: Long): AnilistMetadata? {
        return try {
            handler.awaitOneOrNull {
                anilist_metadata_cacheQueries.getByAnimeId(animeId)
            }?.toAnilistMetadata()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to get Anilist cache for anime $animeId: ${e.message}" }
            null
        }
    }

    suspend fun upsert(metadata: AnilistMetadata) {
        try {
            handler.await {
                anilist_metadata_cacheQueries.upsert(
                    anime_id = metadata.animeId,
                    anilist_id = metadata.anilistId,
                    score = metadata.score,
                    format = metadata.format,
                    status = metadata.status,
                    cover_url = metadata.coverUrl,
                    search_query = metadata.searchQuery,
                    updated_at = metadata.updatedAt,
                    is_manual_match = metadata.isManualMatch,
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to upsert Anilist cache: ${e.message}" }
        }
    }

    suspend fun delete(animeId: Long) {
        try {
            handler.await {
                anilist_metadata_cacheQueries.deleteByAnimeId(animeId)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to delete Anilist cache: ${e.message}" }
        }
    }

    suspend fun clearAll() {
        try {
            handler.await {
                anilist_metadata_cacheQueries.clearAll()
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to clear Anilist cache: ${e.message}" }
        }
    }

    suspend fun deleteStaleEntries() {
        try {
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            handler.await {
                anilist_metadata_cacheQueries.deleteStaleEntries(thirtyDaysAgo)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to delete stale entries: ${e.message}" }
        }
    }
}

// Extension function to convert SQLDelight model to domain model
private fun Anilist_metadata_cache.toAnilistMetadata(): AnilistMetadata {
    return AnilistMetadata(
        animeId = anime_id,
        anilistId = anilist_id,
        score = score,
        format = format,
        status = status,
        coverUrl = cover_url,
        coverUrlFallback = cover_url?.replace("/large/", "/medium/"),
        searchQuery = search_query,
        updatedAt = updated_at,
        isManualMatch = is_manual_match ?: false,
    )
}
