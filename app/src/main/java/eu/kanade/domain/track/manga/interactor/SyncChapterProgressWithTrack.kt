package eu.kanade.domain.track.manga.interactor

import eu.kanade.domain.track.manga.model.toDbTrack
import eu.kanade.domain.track.service.ResolveTrackProgressSync
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.MangaTracker
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.items.chapter.model.toChapterUpdate
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack
import tachiyomi.domain.track.manga.model.MangaTrack

class SyncChapterProgressWithTrack(
    private val updateChapter: UpdateChapter,
    private val insertTrack: InsertMangaTrack,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val trackPreferences: TrackPreferences,
    private val resolveTrackProgressSync: ResolveTrackProgressSync,
) {

    suspend fun await(
        mangaId: Long,
        remoteTrack: MangaTrack,
        tracker: MangaTracker,
    ) {
        val sortedChapters = getChaptersByMangaId.await(mangaId)
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
                    .map { it.copy(read = true).toChapterUpdate() }
                updateChapter.awaitAll(chapterUpdates)
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
