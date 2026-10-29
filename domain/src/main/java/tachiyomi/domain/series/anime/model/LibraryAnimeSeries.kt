package tachiyomi.domain.series.anime.model

import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.series.model.SeriesCoverMode

data class LibraryAnimeSeries(
    val series: AnimeSeries,
    val entries: List<LibraryAnime>,
) {
    val id: Long = series.id
    val title: String = series.title
    val categoryId: Long = series.categoryId
    val pinned: Boolean = series.pinned

    val totalEpisodes: Long
        get() = entries.sumOf { it.totalCount }

    val seenCount: Long
        get() = entries.sumOf { it.seenCount }

    val unseenCount: Long
        get() = totalEpisodes - seenCount

    val lastSeen: Long
        get() = entries.maxOfOrNull { it.lastSeen } ?: 0L

    val latestUpload: Long
        get() = entries.maxOfOrNull { it.latestUpload } ?: 0L

    val hasStarted: Boolean
        get() = seenCount > 0

    val activeAnime: Anime?
        get() {
            if (entries.isEmpty()) return null

            val lastProgressedIndex = entries.indexOfLast { it.hasStarted }
            if (lastProgressedIndex == -1) return entries.first().anime

            val activeEntry = entries[lastProgressedIndex]
            if (activeEntry.seenCount < activeEntry.totalCount) {
                return activeEntry.anime
            }

            return entries.getOrNull(lastProgressedIndex + 1)?.anime ?: activeEntry.anime
        }

    val selectedCoverAnime: Anime?
        get() {
            if (series.coverMode != SeriesCoverMode.ENTRY) return null
            return entries.firstOrNull { it.id == series.coverEntryId }?.anime
        }

    val coverAnimes: List<Anime>
        get() {
            val base = entries.take(5).map { it.anime }
            val selected = selectedCoverAnime ?: return base
            return listOf(selected) + base.filterNot { it.id == selected.id }
        }
}

data class LibraryAnimeSeriesWithEntryIds(
    val series: LibraryAnimeSeries,
    val entryIds: Map<Long, Long>, // animeId -> entryId
)
