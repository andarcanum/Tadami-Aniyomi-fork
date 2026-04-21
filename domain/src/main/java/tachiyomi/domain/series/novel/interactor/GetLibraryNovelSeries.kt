package tachiyomi.domain.series.novel.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.series.novel.model.LibraryNovelSeries
import tachiyomi.domain.series.novel.repository.NovelSeriesRepository

class GetLibraryNovelSeries(
    private val repository: NovelSeriesRepository,
) {
    fun subscribe(): Flow<List<LibraryNovelSeries>> {
        return repository.getLibrarySeriesWithEntries()
    }
}
