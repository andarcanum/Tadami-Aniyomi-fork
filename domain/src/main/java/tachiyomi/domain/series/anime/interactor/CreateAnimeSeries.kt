package tachiyomi.domain.series.anime.interactor

import tachiyomi.domain.series.anime.model.AnimeSeries
import tachiyomi.domain.series.anime.model.AnimeSeriesEntry
import tachiyomi.domain.series.anime.repository.AnimeSeriesRepository
import java.util.Date

class CreateAnimeSeries(
    private val repository: AnimeSeriesRepository,
) {
    suspend fun await(title: String, categoryId: Long, animeIds: List<Long>) {
        val seriesId = repository.insertSeries(
            AnimeSeries(
                id = 0,
                title = title,
                description = null,
                categoryId = categoryId,
                sortOrder = 0,
                dateAdded = Date().time,
                coverLastModified = 0,
            ),
        )

        animeIds.forEachIndexed { index, animeId ->
            repository.insertEntry(
                AnimeSeriesEntry(
                    id = 0,
                    seriesId = seriesId,
                    animeId = animeId,
                    position = index,
                ),
            )
        }
    }
}
