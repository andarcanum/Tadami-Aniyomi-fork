package eu.kanade.domain.track.novel.interactor

import eu.kanade.domain.track.novel.model.toDbTrack
import eu.kanade.domain.track.novel.model.toNovelTrack
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import tachiyomi.domain.track.novel.interactor.GetNovelTracks
import tachiyomi.domain.track.novel.interactor.InsertNovelTrack

class RefreshNovelTracks(
    private val getTracks: GetNovelTracks,
    private val trackerManager: TrackerManager,
    private val insertTrack: InsertNovelTrack,
    private val syncNovelChapterProgressWithTrack: SyncNovelChapterProgressWithTrack,
) {

    suspend fun await(novelId: Long): List<Pair<Tracker?, Throwable>> {
        return supervisorScope {
            return@supervisorScope getTracks.await(novelId)
                .map { it to trackerManager.get(it.trackerId) }
                .filter { (_, service) -> service?.isLoggedIn == true }
                .map { (track, service) ->
                    async {
                        return@async try {
                            val updatedTrack = service!!.mangaService.refresh(track.toDbTrack()).toNovelTrack()!!
                            insertTrack.await(updatedTrack)
                            syncNovelChapterProgressWithTrack.await(novelId, updatedTrack, service.mangaService)
                            null
                        } catch (e: Throwable) {
                            service to e
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }
    }
}
