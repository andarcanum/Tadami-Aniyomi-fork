package tachiyomi.domain.series.manga.interactor

import tachiyomi.domain.series.manga.model.MangaSeries
import tachiyomi.domain.series.manga.repository.MangaSeriesRepository

class UpdateMangaSeries(
    private val repository: MangaSeriesRepository,
) {
    suspend fun await(series: MangaSeries) {
        repository.updateSeries(series)
    }
}
