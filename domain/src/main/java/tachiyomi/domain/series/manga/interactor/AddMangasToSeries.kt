package tachiyomi.domain.series.manga.interactor

import kotlinx.coroutines.flow.first
import tachiyomi.domain.series.manga.model.MangaSeriesEntry
import tachiyomi.domain.series.manga.repository.MangaSeriesRepository

class AddMangasToSeries(
    private val repository: MangaSeriesRepository,
) {
    suspend fun await(seriesId: Long, mangaIds: List<Long>) {
        val currentEntries = repository.getEntriesForSeries(seriesId).first()
        val nextPosition = (currentEntries.maxOfOrNull { it.position } ?: -1) + 1

        mangaIds.forEachIndexed { index, mangaId ->
            repository.insertEntry(
                MangaSeriesEntry(
                    id = 0,
                    seriesId = seriesId,
                    mangaId = mangaId,
                    position = nextPosition + index,
                ),
            )
        }
    }
}
