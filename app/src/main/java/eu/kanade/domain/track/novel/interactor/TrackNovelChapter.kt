package eu.kanade.domain.track.novel.interactor

import android.content.Context
import eu.kanade.domain.track.novel.model.toDbTrack
import eu.kanade.domain.track.novel.model.toNovelTrack
import eu.kanade.domain.track.novel.service.DelayedNovelTrackingUpdateJob
import eu.kanade.domain.track.novel.store.DelayedNovelTrackingStore
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.novel.interactor.GetNovelTracks
import tachiyomi.domain.track.novel.interactor.InsertNovelTrack

class TrackNovelChapter(
    private val getTracks: GetNovelTracks,
    private val trackerManager: TrackerManager,
    private val insertTrack: InsertNovelTrack,
    private val delayedTrackingStore: DelayedNovelTrackingStore,
) {

    suspend fun await(context: Context, novelId: Long, chapterNumber: Double, setupJobOnFailure: Boolean = true) {
        withNonCancellableContext {
            val tracks = getTracks.await(novelId)
            if (tracks.isEmpty()) return@withNonCancellableContext

            tracks.mapNotNull { track ->
                val service = trackerManager.get(track.trackerId)
                if (service == null || !service.isLoggedIn || chapterNumber <= track.lastChapterRead) {
                    return@mapNotNull null
                }

                async {
                    runCatching {
                        try {
                            val updatedTrack = service.mangaService.refresh(track.toDbTrack())
                                .toNovelTrack(idRequired = true)!!
                                .copy(lastChapterRead = chapterNumber)
                            service.mangaService.update(updatedTrack.toDbTrack(), true)
                            insertTrack.await(updatedTrack)
                            delayedTrackingStore.removeNovelItem(track.id)
                        } catch (e: Exception) {
                            delayedTrackingStore.addNovel(track.id, chapterNumber)
                            if (setupJobOnFailure) {
                                DelayedNovelTrackingUpdateJob.setupTask(context)
                            }
                            throw e
                        }
                    }
                }
            }
                .awaitAll()
                .mapNotNull { it.exceptionOrNull() }
                .forEach { logcat(LogPriority.INFO, it) }
        }
    }
}
