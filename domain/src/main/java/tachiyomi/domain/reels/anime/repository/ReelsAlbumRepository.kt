package tachiyomi.domain.reels.anime.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import tachiyomi.domain.reels.anime.model.ReelsAlbum

/**
 * Albums collection storage (device feature): saved albums per source and the manual
 * "watched" marks for album items. Progress bars come from the watch history, not from here.
 */
interface ReelsAlbumRepository {

    fun subscribeAll(): Flow<List<ReelsAlbum>> = emptyFlow()

    suspend fun getAll(): List<ReelsAlbum> = emptyList()

    fun subscribeBySource(sourceId: Long): Flow<List<ReelsAlbum>>

    suspend fun getBySource(sourceId: Long): List<ReelsAlbum>

    suspend fun insert(album: ReelsAlbum)

    suspend fun delete(sourceId: Long, albumId: String)

    /** Cleanup cascade for uninstalled sources (same rule as favorites/follows/hidden). */
    suspend fun deleteBySource(sourceId: Long)

    /** Manual watched marks of one source (video ids). */
    fun subscribeWatched(sourceId: Long): Flow<Set<String>>

    suspend fun markWatched(sourceId: Long, videoId: String)

    suspend fun unmarkWatched(sourceId: Long, videoId: String)

    suspend fun deleteWatchedBySource(sourceId: Long)
}
