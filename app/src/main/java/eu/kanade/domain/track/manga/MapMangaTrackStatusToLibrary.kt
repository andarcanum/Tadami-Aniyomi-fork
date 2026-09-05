package eu.kanade.domain.track.manga

import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.Anilist
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
            else -> when (mangaTracker) {
                // E-M1: the index formula below assumes the canonical list order (ON_HOLD at 2,
                // DROPPED at 3, PLAN at 4). Anilist's manga list is [READING, PLAN_TO_READ,
                // COMPLETED, REREADING, ON_HOLD, DROPPED], so its ON_HOLD landed in "Plan to
                // read" and DROPPED/PLAN_TO_READ in OTHER - breaking the BY_TRACK_STATUS library
                // grouping and the library-update track-status filter. Resolve by constants.
                is Anilist -> when (status) {
                    Anilist.ON_HOLD -> LibraryTrackStatus.ON_HOLD
                    Anilist.DROPPED -> LibraryTrackStatus.DROPPED
                    Anilist.PLAN_TO_READ -> LibraryTrackStatus.PLAN_TO_READ
                    else -> LibraryTrackStatus.OTHER
                }
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
}
