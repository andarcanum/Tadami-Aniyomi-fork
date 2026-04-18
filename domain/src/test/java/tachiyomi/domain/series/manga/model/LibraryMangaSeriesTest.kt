package tachiyomi.domain.series.manga.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.library.manga.LibraryManga

@Execution(ExecutionMode.CONCURRENT)
class LibraryMangaSeriesTest {

    @Test
    fun `active manga follows the committed reading frontier`() {
        val first = libraryManga(
            id = 1L,
            title = "One",
            readCount = 0L,
            totalChapters = 10L,
        )
        val second = libraryManga(
            id = 2L,
            title = "Two",
            readCount = 3L,
            totalChapters = 10L,
        )
        val third = libraryManga(
            id = 3L,
            title = "Three",
            readCount = 0L,
            totalChapters = 10L,
        )

        LibraryMangaSeries(
            series = MangaSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = listOf(first, second, third),
        ).activeManga shouldBe second.manga
    }

    @Test
    fun `active manga advances to next title after completed first`() {
        val first = libraryManga(
            id = 1L,
            title = "One",
            readCount = 10L,
            totalChapters = 10L,
        )
        val second = libraryManga(
            id = 2L,
            title = "Two",
            readCount = 0L,
            totalChapters = 10L,
        )

        LibraryMangaSeries(
            series = MangaSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = listOf(first, second),
        ).activeManga shouldBe second.manga
    }

    @Test
    fun `active manga falls back to first title when nothing is read`() {
        val first = libraryManga(
            id = 1L,
            title = "One",
            readCount = 0L,
            totalChapters = 10L,
        )
        val second = libraryManga(
            id = 2L,
            title = "Two",
            readCount = 0L,
            totalChapters = 10L,
        )

        LibraryMangaSeries(
            series = MangaSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = listOf(first, second),
        ).activeManga shouldBe first.manga
    }

    @Test
    fun `active manga falls back to last title when all titles are complete`() {
        val first = libraryManga(
            id = 1L,
            title = "One",
            readCount = 10L,
            totalChapters = 10L,
        )
        val second = libraryManga(
            id = 2L,
            title = "Two",
            readCount = 10L,
            totalChapters = 10L,
        )

        LibraryMangaSeries(
            series = MangaSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = listOf(first, second),
        ).activeManga shouldBe second.manga
    }

    @Test
    fun `active manga returns null for empty series`() {
        LibraryMangaSeries(
            series = MangaSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = emptyList(),
        ).activeManga shouldBe null
    }

    private fun libraryManga(
        id: Long,
        title: String,
        readCount: Long,
        totalChapters: Long,
    ): LibraryManga {
        val manga = Manga.create().copy(
            id = id,
            title = title,
            source = 1L,
        )

        return LibraryManga(
            manga = manga,
            category = 0L,
            totalChapters = totalChapters,
            readCount = readCount,
            bookmarkCount = 0L,
            latestUpload = 0L,
            chapterFetchedAt = 0L,
            lastRead = 0L,
        )
    }
}
