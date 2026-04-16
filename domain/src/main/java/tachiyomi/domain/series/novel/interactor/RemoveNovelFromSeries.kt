package tachiyomi.domain.series.novel.interactor

import kotlinx.coroutines.flow.first
import tachiyomi.domain.series.novel.repository.NovelSeriesRepository

class RemoveNovelFromSeries(
    private val repository: NovelSeriesRepository,
) {
    suspend fun await(novelId: Long) {
        val series = repository.getSeriesForNovel(novelId) ?: return
        repository.deleteEntry(novelId)
        
        // Re-index remaining entries
        val remaining = repository.getEntriesForSeries(series.id).first().sortedBy { it.position }
        val updated = remaining.mapIndexed { index, entry ->
            entry.copy(position = index)
        }
        repository.updateEntryPositions(updated)
    }
}
