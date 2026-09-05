package tachiyomi.domain.track.manga.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.track.manga.model.MangaTrack

interface MangaTrackRepository {

    /**
     * Resolves a track by its `manga_sync._id` row id (NOT by manga id - that was the former
     * `getTrackByMangaId` misnomer which masked the delayed-queue cross-medium collisions; use
     * [getTracksByMangaId] for manga-id lookups).
     */
    suspend fun getTrackById(trackId: Long): MangaTrack?

    suspend fun getTracksByMangaId(mangaId: Long): List<MangaTrack>

    fun getMangaTracksAsFlow(): Flow<List<MangaTrack>>

    fun getTracksByMangaIdAsFlow(mangaId: Long): Flow<List<MangaTrack>>

    suspend fun delete(mangaId: Long, trackerId: Long)

    suspend fun insertManga(track: MangaTrack)

    suspend fun insertAllManga(tracks: List<MangaTrack>)
}
