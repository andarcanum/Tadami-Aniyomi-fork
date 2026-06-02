package eu.kanade.domain.track.novel

import eu.kanade.domain.track.manga.MapMangaTrackStatusToLibrary
import eu.kanade.tachiyomi.data.track.TrackerManager
import tachiyomi.domain.library.model.LibraryTrackStatus

class MapNovelTrackStatusToLibrary(
    private val trackerManager: TrackerManager,
) {
    private val mangaMapper = MapMangaTrackStatusToLibrary(trackerManager)

    fun map(trackerId: Long, status: Long): LibraryTrackStatus {
        return mangaMapper.map(trackerId, status)
    }
}
