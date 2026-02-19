package tachiyomi.domain.track.novel.interactor

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.novel.model.NovelTrack
import tachiyomi.domain.track.novel.repository.NovelTrackRepository

class GetNovelTracks(
    private val trackRepository: NovelTrackRepository,
) {

    suspend fun awaitOne(id: Long): NovelTrack? {
        return try {
            trackRepository.getTrackByNovelId(id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    suspend fun await(novelId: Long): List<NovelTrack> {
        return try {
            trackRepository.getTracksByNovelId(novelId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    fun subscribe(novelId: Long): Flow<List<NovelTrack>> {
        return trackRepository.getTracksByNovelIdAsFlow(novelId)
    }
}
