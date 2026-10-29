package tachiyomi.domain.series.anime.interactor

import tachiyomi.domain.series.anime.repository.AnimeSeriesRepository

class DeleteAnimeSeries(
    private val repository: AnimeSeriesRepository,
) {
    suspend fun await(seriesId: Long) {
        repository.deleteSeries(seriesId)
    }
}
