package tachiyomi.domain.track.anime.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.track.anime.model.AnimeTrack

interface AnimeTrackRepository {

    /**
     * Resolves a track by its `anime_sync._id` row id (NOT by anime id - that was the former
     * `getTrackByAnimeId` misnomer which masked the delayed-queue cross-medium collisions; use
     * [getTracksByAnimeId] for anime-id lookups).
     */
    suspend fun getTrackById(trackId: Long): AnimeTrack?

    suspend fun getTracksByAnimeId(animeId: Long): List<AnimeTrack>

    fun getAnimeTracksAsFlow(): Flow<List<AnimeTrack>>

    fun getTracksByAnimeIdAsFlow(animeId: Long): Flow<List<AnimeTrack>>

    suspend fun delete(animeId: Long, trackerId: Long)

    suspend fun insertAnime(track: AnimeTrack)

    suspend fun insertAllAnime(tracks: List<AnimeTrack>)
}
