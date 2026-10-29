package tachiyomi.domain.series.anime.interactor

import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.series.anime.model.AnimeSeries
import tachiyomi.domain.series.anime.model.AnimeSeriesEntry
import tachiyomi.domain.series.anime.repository.AnimeSeriesRepository

class RemoveAnimeFromSeriesTest {

    private val repository: AnimeSeriesRepository = mockk()
    private val interactor = RemoveAnimeFromSeries(repository)

    @Test
    fun `keeps empty series row when last anime is removed`() = runTest {
        val series = animeSeries(id = 42L)

        coEvery { repository.getSeriesForAnime(7L) } returns series
        coJustRun { repository.deleteEntry(7L) }
        coEvery { repository.getEntriesForSeries(series.id) } returns flowOf(emptyList())

        interactor.await(7L)

        coVerify(exactly = 1) { repository.deleteEntry(7L) }
        coVerify(exactly = 0) { repository.deleteSeries(series.id) }
        coVerify(exactly = 0) { repository.updateEntryPositions(match<List<AnimeSeriesEntry>> { true }) }
    }

    @Test
    fun `reorders remaining entries when series still has anime`() = runTest {
        val series = animeSeries(id = 42L)
        val first = entry(animeId = 1L, seriesId = series.id, position = 4)
        val second = entry(animeId = 2L, seriesId = series.id, position = 1)

        coEvery { repository.getSeriesForAnime(7L) } returns series
        coJustRun { repository.deleteEntry(7L) }
        coEvery { repository.getEntriesForSeries(series.id) } returns flowOf(listOf(first, second))
        coJustRun { repository.updateEntryPositions(any()) }

        interactor.await(7L)

        coVerify(exactly = 1) { repository.deleteEntry(7L) }
        coVerify(exactly = 1) {
            repository.updateEntryPositions(
                listOf(
                    second.copy(position = 0),
                    first.copy(position = 1),
                ),
            )
        }
        coVerify(exactly = 0) { repository.deleteSeries(series.id) }
    }

    private fun animeSeries(id: Long): AnimeSeries {
        return AnimeSeries(
            id = id,
            title = "Series",
            description = null,
            categoryId = 0L,
            sortOrder = 0L,
            dateAdded = 0L,
            coverLastModified = 0L,
        )
    }

    private fun entry(
        animeId: Long,
        seriesId: Long,
        position: Int,
    ): AnimeSeriesEntry {
        return AnimeSeriesEntry(
            id = animeId + 1000L,
            animeId = animeId,
            seriesId = seriesId,
            position = position,
        )
    }
}
