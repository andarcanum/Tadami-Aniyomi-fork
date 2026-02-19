package tachiyomi.domain.track.novel.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.novel.repository.NovelTrackRepository

class DeleteNovelTrack(
    private val trackRepository: NovelTrackRepository,
) {

    suspend fun await(novelId: Long, trackerId: Long) {
        try {
            trackRepository.delete(novelId, trackerId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
