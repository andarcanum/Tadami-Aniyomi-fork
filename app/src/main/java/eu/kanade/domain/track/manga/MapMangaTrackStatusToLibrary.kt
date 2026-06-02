package eu.kanade.domain.track.manga

import eu.kanade.tachiyomi.data.track.TrackerManager
import tachiyomi.domain.library.model.LibraryTrackStatus

class MapMangaTrackStatusToLibrary(
    private val trackerManager: TrackerManager,
) {
    fun map(trackerId: Long, status: Long): LibraryTrackStatus {
        val tracker = trackerManager.get(trackerId) ?: return LibraryTrackStatus.OTHER
        val mangaTracker = tracker.mangaService
        val statusList = mangaTracker.getStatusListManga()
        return when (status) {
            mangaTracker.getReadingStatus() -> LibraryTrackStatus.READING
            mangaTracker.getRereadingStatus() -> LibraryTrackStatus.REPEATING
            mangaTracker.getCompletionStatus() -> LibraryTrackStatus.COMPLETED
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
