package tachiyomi.domain.series.anime.interactor

import kotlinx.coroutines.flow.first
import tachiyomi.domain.series.anime.model.AnimeSeriesEntry
import tachiyomi.domain.series.anime.repository.AnimeSeriesRepository

class AddAnimesToSeries(
    private val repository: AnimeSeriesRepository,
) {
    suspend fun await(seriesId: Long, animeIds: List<Long>) {
        val currentEntries = repository.getEntriesForSeries(seriesId).first()
        val nextPosition = (currentEntries.maxOfOrNull { it.position } ?: -1) + 1

        animeIds.forEachIndexed { index, animeId ->
            repository.insertEntry(
                AnimeSeriesEntry(
                    id = 0,
                    seriesId = seriesId,
                    animeId = animeId,
                    position = nextPosition + index,
                ),
            )
        }
    }
}
