package eu.kanade.domain.track.anime.interactor

import android.content.Context
import eu.kanade.domain.track.anime.model.toDbTrack
import eu.kanade.domain.track.anime.model.toDomainTrack
import eu.kanade.domain.track.anime.service.DelayedAnimeTrackingUpdateJob
import eu.kanade.domain.track.anime.store.DelayedAnimeTrackingStore
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.util.system.isOnline
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack

class TrackEpisode(
    private val getTracks: GetAnimeTracks,
    private val trackerManager: TrackerManager,
    private val insertTrack: InsertAnimeTrack,
    private val delayedTrackingStore: DelayedAnimeTrackingStore,
) {

    suspend fun await(context: Context, animeId: Long, episodeNumber: Double, setupJobOnFailure: Boolean = true) {
        withNonCancellableContext {
            val tracks = getTracks.await(animeId)
            if (tracks.isEmpty()) return@withNonCancellableContext

            tracks.mapNotNull { track ->
                val service = trackerManager.get(track.trackerId)
                if (service == null || !service.isLoggedIn || episodeNumber <= track.lastEpisodeSeen) {
                    return@mapNotNull null
                }

                async {
                    runCatching {
                        if (context.isOnline()) {
                            val refreshedTrack = service.animeService.refresh(track.toDbTrack())
                                .toDomainTrack(idRequired = true)!!
                            // DECISION-8 (anime mirror): never roll the remote progress back when
                            // it is ahead of the local copy (another device).
                            val mergedTrack = refreshedTrack.copy(
                                lastEpisodeSeen = maxOf(refreshedTrack.lastEpisodeSeen, episodeNumber),
                            )
                            val pushedTrack = service.animeService.update(mergedTrack.toDbTrack(), true)
                            // E-L (anime mirror): persist the track returned by update() so the
                            // didWatchEpisode side effects land in the local row too.
                            insertTrack.await(pushedTrack.toDomainTrack(idRequired = true) ?: mergedTrack)
                            delayedTrackingStore.removeAnimeItem(track.animeId, track.trackerId)
                        } else {
                            delayedTrackingStore.addAnime(track.animeId, track.trackerId, episodeNumber)
                            if (setupJobOnFailure) {
                                DelayedAnimeTrackingUpdateJob.setupTask(context)
                            }
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
