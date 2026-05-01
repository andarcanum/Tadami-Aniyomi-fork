package eu.kanade.domain.track.novel.interactor

import eu.kanade.domain.track.novel.model.toDbTrack
import eu.kanade.domain.track.service.ResolveTrackProgressSync
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.MangaTracker
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.items.novelchapter.interactor.GetNovelChapters
import tachiyomi.domain.items.novelchapter.model.toNovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.track.novel.interactor.InsertNovelTrack
import tachiyomi.domain.track.novel.model.NovelTrack

class SyncNovelChapterProgressWithTrack(
    private val novelChapterRepository: NovelChapterRepository,
    private val insertTrack: InsertNovelTrack,
    private val getNovelChapters: GetNovelChapters,
    private val trackPreferences: TrackPreferences,
    private val resolveTrackProgressSync: ResolveTrackProgressSync,
) {

    suspend fun await(
        novelId: Long,
        remoteTrack: NovelTrack,
        tracker: MangaTracker,
    ) {
        val sortedChapters = getNovelChapters.await(novelId)
            .sortedBy { it.chapterNumber }
            .filter { it.isRecognizedNumber }

        val localLastRead = sortedChapters.takeWhile { it.read }
            .lastOrNull()
            ?.chapterNumber
            ?: 0.0
        val action = resolveTrackProgressSync.resolve(
            local = localLastRead,
            remote = remoteTrack.lastChapterRead,
            pullEnabled = trackPreferences.autoSyncProgressFromTracker().get(),
            trigger = ResolveTrackProgressSync.Trigger.OPEN_REFRESH,
        )

        when (action) {
            ResolveTrackProgressSync.SyncAction.NoOp -> Unit
            is ResolveTrackProgressSync.SyncAction.MarkLocalUntil -> {
                val chapterUpdates = sortedChapters
                    .filter { chapter -> chapter.chapterNumber <= action.value && !chapter.read }
                    .map { it.copy(read = true).toNovelChapterUpdate() }
                try {
                    novelChapterRepository.updateAllChapters(chapterUpdates)
                } catch (e: Throwable) {
                    logcat(LogPriority.WARN, e)
                }
            }
            is ResolveTrackProgressSync.SyncAction.PushRemoteTo -> {
                val updatedTrack = remoteTrack.copy(lastChapterRead = action.value)
                try {
                    tracker.update(updatedTrack.toDbTrack())
                    insertTrack.await(updatedTrack)
                } catch (e: Throwable) {
                    logcat(LogPriority.WARN, e)
                }
            }
        }
    }
}
