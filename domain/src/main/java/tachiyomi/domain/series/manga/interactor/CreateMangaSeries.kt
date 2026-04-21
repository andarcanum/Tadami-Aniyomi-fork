package tachiyomi.domain.series.manga.interactor

import tachiyomi.domain.series.manga.model.MangaSeries
import tachiyomi.domain.series.manga.model.MangaSeriesEntry
import tachiyomi.domain.series.manga.repository.MangaSeriesRepository
import java.util.Date

class CreateMangaSeries(
    private val repository: MangaSeriesRepository,
) {
    suspend fun await(title: String, categoryId: Long, mangaIds: List<Long>) {
        val seriesId = repository.insertSeries(
            MangaSeries(
                id = 0,
                title = title,
                description = null,
                categoryId = categoryId,
                sortOrder = 0,
                dateAdded = Date().time,
                coverLastModified = 0,
            ),
        )

        mangaIds.forEachIndexed { index, mangaId ->
            repository.insertEntry(
                MangaSeriesEntry(
                    id = 0,
                    seriesId = seriesId,
                    mangaId = mangaId,
                    position = index,
                ),
            )
        }
    }
}
