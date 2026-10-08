package tachiyomi.domain.series.anime.interactor

import tachiyomi.domain.series.anime.model.AnimeSeries
import tachiyomi.domain.series.anime.repository.AnimeSeriesRepository

class UpdateAnimeSeries(
    private val repository: AnimeSeriesRepository,
) {
    suspend fun await(series: AnimeSeries) {
        repository.updateSeries(series)
    }
}
