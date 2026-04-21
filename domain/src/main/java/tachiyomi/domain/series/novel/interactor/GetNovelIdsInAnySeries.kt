package tachiyomi.domain.series.novel.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.series.novel.repository.NovelSeriesRepository

class GetNovelIdsInAnySeries(
    private val repository: NovelSeriesRepository,
) {
    fun subscribe(): Flow<Set<Long>> {
        return repository.getAllNovelIdsInAnySeries()
    }
}
