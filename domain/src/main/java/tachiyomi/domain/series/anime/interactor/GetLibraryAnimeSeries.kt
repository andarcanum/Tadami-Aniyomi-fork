package tachiyomi.domain.series.anime.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.series.anime.model.LibraryAnimeSeries
import tachiyomi.domain.series.anime.repository.AnimeSeriesRepository

class GetLibraryAnimeSeries(
    private val repository: AnimeSeriesRepository,
) {
    fun subscribe(): Flow<List<LibraryAnimeSeries>> {
        return repository.getLibrarySeriesWithEntries()
    }
}
