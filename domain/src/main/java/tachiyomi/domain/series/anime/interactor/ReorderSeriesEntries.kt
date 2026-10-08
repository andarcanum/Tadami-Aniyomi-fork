package tachiyomi.domain.series.anime.interactor

import tachiyomi.domain.series.anime.model.AnimeSeriesEntry
import tachiyomi.domain.series.anime.repository.AnimeSeriesRepository

class ReorderSeriesEntries(
    private val repository: AnimeSeriesRepository,
) {
    suspend fun await(entries: List<AnimeSeriesEntry>) {
        repository.updateEntryPositions(entries)
    }
}
