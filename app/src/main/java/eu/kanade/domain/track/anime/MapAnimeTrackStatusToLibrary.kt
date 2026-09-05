package eu.kanade.domain.track.anime

import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.kitsu.Kitsu
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
            else -> when (animeTracker) {
                // E-M1: the index formula assumes the canonical order; Anilist's anime list is
                // [WATCHING, PLAN_TO_WATCH, COMPLETED, REWATCHING, ON_HOLD, DROPPED] and Kitsu's
                // is [WATCHING, PLAN_TO_WATCH, COMPLETED, ON_HOLD, DROPPED] - the formula sent
                // Anilist ON_HOLD to "Plan" and Kitsu ON_HOLD to DROPPED / DROPPED to "Plan".
                is Anilist -> when (status) {
                    Anilist.ON_HOLD -> LibraryTrackStatus.ON_HOLD
                    Anilist.DROPPED -> LibraryTrackStatus.DROPPED
                    Anilist.PLAN_TO_WATCH -> LibraryTrackStatus.PLAN_TO_READ
                    else -> LibraryTrackStatus.OTHER
                }
                is Kitsu -> when (status) {
                    Kitsu.ON_HOLD -> LibraryTrackStatus.ON_HOLD
                    Kitsu.DROPPED -> LibraryTrackStatus.DROPPED
                    Kitsu.PLAN_TO_WATCH -> LibraryTrackStatus.PLAN_TO_READ
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
