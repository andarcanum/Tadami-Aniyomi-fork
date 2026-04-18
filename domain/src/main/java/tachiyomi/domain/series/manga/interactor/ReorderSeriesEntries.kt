package tachiyomi.domain.series.manga.interactor

import tachiyomi.domain.series.manga.model.MangaSeriesEntry
import tachiyomi.domain.series.manga.repository.MangaSeriesRepository

class ReorderSeriesEntries(
    private val repository: MangaSeriesRepository,
) {
    suspend fun await(entries: List<MangaSeriesEntry>) {
        repository.updateEntryPositions(entries)
    }
}
