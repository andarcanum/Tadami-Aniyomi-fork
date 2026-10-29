package tachiyomi.domain.series.anime.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.series.model.SeriesCoverMode

@Execution(ExecutionMode.CONCURRENT)
class LibraryAnimeSeriesTest {

    @Test
    fun `active anime follows the committed watching frontier`() {
        val first = libraryAnime(
            id = 1L,
            title = "One",
            seenCount = 0L,
            totalCount = 10L,
        )
        val second = libraryAnime(
            id = 2L,
            title = "Two",
            seenCount = 3L,
            totalCount = 10L,
        )
        val third = libraryAnime(
            id = 3L,
            title = "Three",
            seenCount = 0L,
            totalCount = 10L,
        )

        LibraryAnimeSeries(
            series = AnimeSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = listOf(first, second, third),
        ).activeAnime shouldBe second.anime
    }

    @Test
    fun `active anime advances to next title after completed first`() {
        val first = libraryAnime(
            id = 1L,
            title = "One",
            seenCount = 10L,
            totalCount = 10L,
        )
        val second = libraryAnime(
            id = 2L,
            title = "Two",
            seenCount = 0L,
            totalCount = 10L,
        )

        LibraryAnimeSeries(
            series = AnimeSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = listOf(first, second),
        ).activeAnime shouldBe second.anime
    }

    @Test
    fun `active anime falls back to first title when nothing is watched`() {
        val first = libraryAnime(
            id = 1L,
            title = "One",
            seenCount = 0L,
            totalCount = 10L,
        )
        val second = libraryAnime(
            id = 2L,
            title = "Two",
            seenCount = 0L,
            totalCount = 10L,
        )

        LibraryAnimeSeries(
            series = AnimeSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = listOf(first, second),
        ).activeAnime shouldBe first.anime
    }

    @Test
    fun `active anime falls back to last title when all titles are complete`() {
        val first = libraryAnime(
            id = 1L,
            title = "One",
            seenCount = 10L,
            totalCount = 10L,
        )
        val second = libraryAnime(
            id = 2L,
            title = "Two",
            seenCount = 10L,
            totalCount = 10L,
        )

        LibraryAnimeSeries(
            series = AnimeSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = listOf(first, second),
        ).activeAnime shouldBe second.anime
    }

    @Test
    fun `active anime returns null for empty series`() {
        LibraryAnimeSeries(
            series = AnimeSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = emptyList(),
        ).activeAnime shouldBe null
    }

    @Test
    fun `series exposes aggregates correctly`() {
        val first = libraryAnime(
            id = 1L,
            title = "One",
            seenCount = 4L,
            totalCount = 10L,
        )
        val second = libraryAnime(
            id = 2L,
            title = "Two",
            seenCount = 6L,
            totalCount = 12L,
        )

        val series = LibraryAnimeSeries(
            series = AnimeSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = listOf(first, second),
        )

        series.totalEpisodes shouldBe 22L
        series.seenCount shouldBe 10L
        series.unseenCount shouldBe 12L
        series.hasStarted shouldBe true
    }

    @Test
    fun `series with empty entries exposes zero aggregates`() {
        val series = LibraryAnimeSeries(
            series = AnimeSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = emptyList(),
        )

        series.totalEpisodes shouldBe 0L
        series.seenCount shouldBe 0L
        series.unseenCount shouldBe 0L
        series.hasStarted shouldBe false
        series.lastSeen shouldBe 0L
        series.latestUpload shouldBe 0L
    }

    @Test
    fun `series exposes pinned state`() {
        val series = LibraryAnimeSeries(
            series = AnimeSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
                pinned = true,
            ),
            entries = emptyList(),
        )

        series.pinned shouldBe true
    }

    @Test
    fun `series exposes cover selection state`() {
        val series = LibraryAnimeSeries(
            series = AnimeSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
                coverMode = SeriesCoverMode.AUTO,
                coverEntryId = null,
            ),
            entries = emptyList(),
        )

        series.series.coverMode shouldBe SeriesCoverMode.AUTO
        series.series.coverEntryId shouldBe null
    }

    private fun libraryAnime(
        id: Long,
        title: String,
        seenCount: Long,
        totalCount: Long,
    ): LibraryAnime {
        val anime = Anime.create().copy(
            id = id,
            title = title,
            source = 1L,
        )

        return LibraryAnime(
            anime = anime,
            category = 0L,
            totalCount = totalCount,
            seenCount = seenCount,
            bookmarkCount = 0L,
            fillermarkCount = 0L,
            latestUpload = 0L,
            episodeFetchedAt = 0L,
            lastSeen = 0L,
        )
    }
}
