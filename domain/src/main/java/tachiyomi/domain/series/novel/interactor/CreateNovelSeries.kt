package tachiyomi.domain.series.novel.interactor

import tachiyomi.domain.series.novel.model.NovelSeries
import tachiyomi.domain.series.novel.model.NovelSeriesEntry
import tachiyomi.domain.series.novel.repository.NovelSeriesRepository
import java.util.Date

class CreateNovelSeries(
    private val repository: NovelSeriesRepository,
) {
    suspend fun await(title: String, categoryId: Long, novelIds: List<Long>) {
        val seriesId = repository.insertSeries(
            NovelSeries(
                id = 0,
                title = title,
                description = null,
                categoryId = categoryId,
                sortOrder = 0,
                dateAdded = Date().time,
                coverLastModified = 0
            )
        )

        novelIds.forEachIndexed { index, novelId ->
            repository.insertEntry(
                NovelSeriesEntry(
                    id = 0,
                    seriesId = seriesId,
                    novelId = novelId,
                    position = index
                )
            )
        }
    }
}
