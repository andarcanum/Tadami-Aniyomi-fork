package tachiyomi.domain.series.anime.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.series.anime.model.LibraryAnimeSeries
import tachiyomi.domain.series.anime.model.LibraryAnimeSeriesWithEntryIds
import tachiyomi.domain.series.anime.repository.AnimeSeriesRepository

class GetAnimeSeriesWithEntries(
    private val repository: AnimeSeriesRepository,
    private val animeRepository: AnimeRepository,
) {
    fun subscribe(seriesId: Long): Flow<LibraryAnimeSeriesWithEntryIds?> {
        return combine(
            repository.getSeriesById(seriesId),
            repository.getEntriesForSeries(seriesId),
            animeRepository.getLibraryAnimeAsFlow(),
        ) { series, entries, libraryAnimes ->
            if (series == null) return@combine null
            val animesById = libraryAnimes.associateBy { it.anime.id }
            val seriesLibraryAnimes = entries
                .sortedBy { it.position }
                .mapNotNull { animesById[it.animeId] }

            val entryIds = entries.associate { it.animeId to it.id }
            LibraryAnimeSeriesWithEntryIds(
                series = LibraryAnimeSeries(series, seriesLibraryAnimes),
                entryIds = entryIds,
            )
        }
    }
}
