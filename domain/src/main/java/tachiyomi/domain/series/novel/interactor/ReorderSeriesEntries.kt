package tachiyomi.domain.series.novel.interactor

import tachiyomi.domain.series.novel.model.NovelSeriesEntry
import tachiyomi.domain.series.novel.repository.NovelSeriesRepository

class ReorderSeriesEntries(
    private val repository: NovelSeriesRepository,
) {
    suspend fun await(entries: List<NovelSeriesEntry>) {
        repository.updateEntryPositions(entries)
    }
}
