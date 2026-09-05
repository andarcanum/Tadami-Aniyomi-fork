package eu.kanade.domain.track.manga.interactor

import android.content.Context
import eu.kanade.domain.track.manga.model.toDbTrack
import eu.kanade.domain.track.manga.model.toDomainTrack
import eu.kanade.domain.track.manga.service.DelayedMangaTrackingUpdateJob
import eu.kanade.domain.track.manga.store.DelayedMangaTrackingStore
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack

class TrackChapter(
    private val getTracks: GetMangaTracks,
    private val trackerManager: TrackerManager,
    private val insertTrack: InsertMangaTrack,
    private val delayedTrackingStore: DelayedMangaTrackingStore,
) {

    suspend fun await(context: Context, mangaId: Long, chapterNumber: Double, setupJobOnFailure: Boolean = true) {
        withNonCancellableContext {
            val tracks = getTracks.await(mangaId)
            if (tracks.isEmpty()) return@withNonCancellableContext

            tracks.mapNotNull { track ->
                val service = trackerManager.get(track.trackerId)
                // E-L: the identical condition used to be checked twice, nested (a merge artifact).
                if (service == null || !service.isLoggedIn || chapterNumber <= track.lastChapterRead) {
                    return@mapNotNull null
                }

                async {
                    runCatching {
                        try {
                            val refreshedTrack = service.mangaService.refresh(track.toDbTrack())
                                .toDomainTrack(idRequired = true)!!
                            // DECISION-8: the guard above compared against the LOCAL
                            // lastChapterRead; when the remote was ahead (another device),
                            // copying chapterNumber over the refreshed value rolled the remote
                            // progress back. Push the max instead.
                            val mergedTrack = refreshedTrack.copy(
                                lastChapterRead = maxOf(refreshedTrack.lastChapterRead, chapterNumber),
                            )
                            val pushedTrack = service.mangaService.update(mergedTrack.toDbTrack(), true)
                            // E-L: persist the track RETURNED by update() - services apply the
                            // didReadChapter side effects (COMPLETED status, start/finish dates)
                            // on it, while the old code persisted the pre-update copy, leaving
                            // the local row behind the remote until the next manual refresh.
                            insertTrack.await(pushedTrack.toDomainTrack(idRequired = true) ?: mergedTrack)
                            delayedTrackingStore.removeMangaItem(track.mangaId, track.trackerId)
                        } catch (e: Exception) {
                            delayedTrackingStore.addManga(track.mangaId, track.trackerId, chapterNumber)
                            if (setupJobOnFailure) {
                                DelayedMangaTrackingUpdateJob.setupTask(context)
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
