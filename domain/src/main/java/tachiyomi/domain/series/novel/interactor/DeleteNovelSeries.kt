package tachiyomi.domain.series.novel.interactor

import tachiyomi.domain.series.novel.repository.NovelSeriesRepository

class DeleteNovelSeries(
    private val repository: NovelSeriesRepository,
) {
    suspend fun await(seriesId: Long) {
        repository.deleteSeries(seriesId)
    }
}
