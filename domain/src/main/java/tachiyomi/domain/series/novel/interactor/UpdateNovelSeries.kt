package tachiyomi.domain.series.novel.interactor

import tachiyomi.domain.series.novel.model.NovelSeries
import tachiyomi.domain.series.novel.repository.NovelSeriesRepository

class UpdateNovelSeries(
    private val repository: NovelSeriesRepository,
) {
    suspend fun await(series: NovelSeries) {
        repository.updateSeries(series)
    }
}
