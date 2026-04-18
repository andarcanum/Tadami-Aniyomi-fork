package tachiyomi.domain.series.manga.interactor

import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.series.manga.model.MangaSeries
import tachiyomi.domain.series.manga.model.MangaSeriesEntry
import tachiyomi.domain.series.manga.repository.MangaSeriesRepository

class RemoveMangaFromSeriesTest {

    private val repository: MangaSeriesRepository = mockk()
    private val interactor = RemoveMangaFromSeries(repository)

    @Test
    fun `keeps empty series row when last manga is removed`() = runTest {
        val series = mangaSeries(id = 42L)

        coEvery { repository.getSeriesForManga(7L) } returns series
        coJustRun { repository.deleteEntry(7L) }
        coEvery { repository.getEntriesForSeries(series.id) } returns flowOf(emptyList())

        interactor.await(7L)

        coVerify(exactly = 1) { repository.deleteEntry(7L) }
        coVerify(exactly = 0) { repository.deleteSeries(series.id) }
        coVerify(exactly = 0) { repository.updateEntryPositions(match<List<MangaSeriesEntry>> { true }) }
    }

    @Test
    fun `reorders remaining entries when series still has manga`() = runTest {
        val series = mangaSeries(id = 42L)
        val first = entry(mangaId = 1L, seriesId = series.id, position = 4)
        val second = entry(mangaId = 2L, seriesId = series.id, position = 1)

        coEvery { repository.getSeriesForManga(7L) } returns series
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

    private fun mangaSeries(id: Long): MangaSeries {
        return MangaSeries(
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
        mangaId: Long,
        seriesId: Long,
        position: Int,
    ): MangaSeriesEntry {
        return MangaSeriesEntry(
            id = mangaId + 1000L,
            mangaId = mangaId,
            seriesId = seriesId,
            position = position,
        )
    }
}
