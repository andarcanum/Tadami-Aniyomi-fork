package tachiyomi.domain.series.manga.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.series.manga.model.LibraryMangaSeries
import tachiyomi.domain.series.manga.repository.MangaSeriesRepository

class GetLibraryMangaSeries(
    private val repository: MangaSeriesRepository,
) {
    fun subscribe(): Flow<List<LibraryMangaSeries>> {
        return repository.getLibrarySeriesWithEntries()
    }
}
