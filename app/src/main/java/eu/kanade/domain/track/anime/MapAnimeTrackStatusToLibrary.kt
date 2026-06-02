package eu.kanade.domain.track.anime

import eu.kanade.tachiyomi.data.track.TrackerManager
import tachiyomi.domain.library.model.LibraryTrackStatus

class MapAnimeTrackStatusToLibrary(
    private val trackerManager: TrackerManager,
) {
    fun map(trackerId: Long, status: Long): LibraryTrackStatus {
        val tracker = trackerManager.get(trackerId) ?: return LibraryTrackStatus.OTHER
        val animeTracker = tracker.animeService
        val statusList = animeTracker.getStatusListAnime()
        return when (status) {
            animeTracker.getWatchingStatus() -> LibraryTrackStatus.READING
            animeTracker.getRewatchingStatus() -> LibraryTrackStatus.REPEATING
            animeTracker.getCompletionStatus() -> LibraryTrackStatus.COMPLETED
            else -> {
                val statusIndex = statusList.indexOf(status)
                when (statusIndex) {
                    2 -> LibraryTrackStatus.ON_HOLD
                    3 -> LibraryTrackStatus.DROPPED
                    4 -> LibraryTrackStatus.PLAN_TO_READ
                    else -> LibraryTrackStatus.OTHER
                }
            }
        }
    }
}
