package tachiyomi.domain.track.novel.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.track.novel.model.NovelTrack

interface NovelTrackRepository {

    suspend fun getTrackByNovelId(id: Long): NovelTrack?

    suspend fun getTracksByNovelId(novelId: Long): List<NovelTrack>

    fun getNovelTracksAsFlow(): Flow<List<NovelTrack>>

    fun getTracksByNovelIdAsFlow(novelId: Long): Flow<List<NovelTrack>>

    suspend fun delete(novelId: Long, trackerId: Long)

    suspend fun insertNovel(track: NovelTrack)

    suspend fun insertAllNovel(tracks: List<NovelTrack>)
}
