package tachiyomi.domain.series.novel.interactor

import kotlinx.coroutines.flow.first
import tachiyomi.domain.series.novel.model.NovelSeriesEntry
import tachiyomi.domain.series.novel.repository.NovelSeriesRepository

class AddNovelsToSeries(
    private val repository: NovelSeriesRepository,
) {
    suspend fun await(seriesId: Long, novelIds: List<Long>) {
        val currentEntries = repository.getEntriesForSeries(seriesId).first()
        val nextPosition = (currentEntries.maxOfOrNull { it.position } ?: -1) + 1

        novelIds.forEachIndexed { index, novelId ->
            repository.insertEntry(
                NovelSeriesEntry(
                    id = 0,
                    seriesId = seriesId,
                    novelId = novelId,
                    position = nextPosition + index,
                ),
            )
        }
    }
}
