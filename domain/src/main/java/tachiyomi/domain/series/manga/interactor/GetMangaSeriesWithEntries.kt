package tachiyomi.domain.series.manga.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.series.manga.model.LibraryMangaSeries
import tachiyomi.domain.series.manga.model.LibraryMangaSeriesWithEntryIds
import tachiyomi.domain.series.manga.repository.MangaSeriesRepository

class GetMangaSeriesWithEntries(
    private val repository: MangaSeriesRepository,
    private val mangaRepository: MangaRepository,
) {
    fun subscribe(seriesId: Long): Flow<LibraryMangaSeriesWithEntryIds?> {
        return combine(
            repository.getSeriesById(seriesId),
            repository.getEntriesForSeries(seriesId),
            mangaRepository.getLibraryMangaAsFlow(),
        ) { series, entries, libraryMangas ->
            if (series == null) return@combine null

            val mangasById = libraryMangas.associateBy { it.manga.id }
            val seriesLibraryMangas = entries
                .sortedBy { it.position }
                .mapNotNull { mangasById[it.mangaId] }

            val entryIds = entries.associate { it.mangaId to it.id }
            LibraryMangaSeriesWithEntryIds(
                series = LibraryMangaSeries(series, seriesLibraryMangas),
                entryIds = entryIds,
            )
        }
    }
}
