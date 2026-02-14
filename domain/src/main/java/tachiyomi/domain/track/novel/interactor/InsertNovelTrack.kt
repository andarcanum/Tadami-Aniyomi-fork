package tachiyomi.domain.track.novel.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.novel.model.NovelTrack
import tachiyomi.domain.track.novel.repository.NovelTrackRepository

class InsertNovelTrack(
    private val trackRepository: NovelTrackRepository,
) {

    suspend fun await(track: NovelTrack) {
        try {
            trackRepository.insertNovel(track)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    suspend fun awaitAll(tracks: List<NovelTrack>) {
        try {
            trackRepository.insertAllNovel(tracks)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
