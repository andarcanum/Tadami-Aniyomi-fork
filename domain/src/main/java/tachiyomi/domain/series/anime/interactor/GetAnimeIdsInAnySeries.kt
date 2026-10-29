package tachiyomi.domain.series.anime.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.series.anime.repository.AnimeSeriesRepository

class GetAnimeIdsInAnySeries(
    private val repository: AnimeSeriesRepository,
) {
    fun subscribe(): Flow<Set<Long>> {
        return repository.getAllAnimeIdsInAnySeries()
    }
}
