package tachiyomi.domain.series.manga.interactor

import kotlinx.coroutines.flow.first
import tachiyomi.domain.series.manga.repository.MangaSeriesRepository

class RemoveMangaFromSeries(
    private val repository: MangaSeriesRepository,
) {
    suspend fun await(mangaId: Long) {
        val series = repository.getSeriesForManga(mangaId) ?: return
        repository.deleteEntry(mangaId)

        val remaining = repository.getEntriesForSeries(series.id).first().sortedBy { it.position }
        if (remaining.isEmpty()) return

        val updated = remaining.mapIndexed { index, entry ->
            entry.copy(position = index)
        }
        repository.updateEntryPositions(updated)
    }
}
