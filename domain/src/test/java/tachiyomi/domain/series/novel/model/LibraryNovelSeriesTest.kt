package tachiyomi.domain.series.novel.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.library.novel.LibraryNovel

@Execution(ExecutionMode.CONCURRENT)
class LibraryNovelSeriesTest {

    @Test
    fun `active novel follows the committed reading frontier`() {
        val first = libraryNovel(
            id = 1L,
            title = "One",
            readCount = 0L,
            totalChapters = 10L,
        )
        val second = libraryNovel(
            id = 2L,
            title = "Two",
            readCount = 3L,
            totalChapters = 10L,
        )
        val third = libraryNovel(
            id = 3L,
            title = "Three",
            readCount = 0L,
            totalChapters = 10L,
        )

        LibraryNovelSeries(
            series = NovelSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = listOf(first, second, third),
        ).activeNovel shouldBe second.novel
    }

    @Test
    fun `active novel advances to next title after completed first`() {
        val first = libraryNovel(
            id = 1L,
            title = "One",
            readCount = 10L,
            totalChapters = 10L,
        )
        val second = libraryNovel(
            id = 2L,
            title = "Two",
            readCount = 0L,
            totalChapters = 10L,
        )

        LibraryNovelSeries(
            series = NovelSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = listOf(first, second),
        ).activeNovel shouldBe second.novel
    }

    @Test
    fun `active novel falls back to first title when nothing is read`() {
        val first = libraryNovel(
            id = 1L,
            title = "One",
            readCount = 0L,
            totalChapters = 10L,
        )
        val second = libraryNovel(
            id = 2L,
            title = "Two",
            readCount = 0L,
            totalChapters = 10L,
        )

        LibraryNovelSeries(
            series = NovelSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = listOf(first, second),
        ).activeNovel shouldBe first.novel
    }

    @Test
    fun `active novel falls back to last title when all titles are complete`() {
        val first = libraryNovel(
            id = 1L,
            title = "One",
            readCount = 10L,
            totalChapters = 10L,
        )
        val second = libraryNovel(
            id = 2L,
            title = "Two",
            readCount = 10L,
            totalChapters = 10L,
        )

        LibraryNovelSeries(
            series = NovelSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = listOf(first, second),
        ).activeNovel shouldBe second.novel
    }

    @Test
    fun `active novel returns null for empty series`() {
        LibraryNovelSeries(
            series = NovelSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = emptyList(),
        ).activeNovel shouldBe null
    }

    private fun libraryNovel(
        id: Long,
        title: String,
        readCount: Long,
        totalChapters: Long,
    ): LibraryNovel {
        val novel = Novel.create().copy(
            id = id,
            title = title,
            source = 1L,
        )

        return LibraryNovel(
            novel = novel,
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
