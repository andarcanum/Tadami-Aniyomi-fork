package tachiyomi.domain.series.manga.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.series.manga.repository.MangaSeriesRepository

class GetMangaIdsInAnySeries(
    private val repository: MangaSeriesRepository,
) {
    fun subscribe(): Flow<Set<Long>> {
        return repository.getAllMangaIdsInAnySeries()
    }
}
