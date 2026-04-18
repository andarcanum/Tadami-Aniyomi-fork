package tachiyomi.domain.series.manga.interactor

import tachiyomi.domain.series.manga.repository.MangaSeriesRepository

class DeleteMangaSeries(
    private val repository: MangaSeriesRepository,
) {
    suspend fun await(seriesId: Long) {
        repository.deleteSeries(seriesId)
    }
}
