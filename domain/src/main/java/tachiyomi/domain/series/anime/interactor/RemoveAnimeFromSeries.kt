package tachiyomi.domain.series.anime.interactor

import kotlinx.coroutines.flow.first
import tachiyomi.domain.series.anime.repository.AnimeSeriesRepository

class RemoveAnimeFromSeries(
    private val repository: AnimeSeriesRepository,
) {
    suspend fun await(animeId: Long) {
        val series = repository.getSeriesForAnime(animeId) ?: return
        repository.deleteEntry(animeId)

        // Re-index remaining entries
        val remaining = repository.getEntriesForSeries(series.id).first().sortedBy { it.position }
        val updated = remaining.mapIndexed { index, entry ->
            entry.copy(position = index)
        }
        repository.updateEntryPositions(updated)
    }
}
