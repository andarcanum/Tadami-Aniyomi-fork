package eu.kanade.presentation.series.novel

import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.series.novel.model.LibraryNovelSeries
import tachiyomi.domain.series.novel.model.NovelSeries
import kotlin.test.assertEquals

class NovelSeriesReadingTargetResolverTest {

    @Test
    fun `returns next novel when active novel is fully read`() {
        val series = LibraryNovelSeries(
            series = NovelSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = listOf(
                libraryNovel(id = 10L, title = "One", totalChapters = 100, readCount = 90),
                libraryNovel(id = 20L, title = "Two", totalChapters = 20, readCount = 0),
            ),
        )

        val chapters = listOf(
            libraryNovelChapters(
                novelId = 10L,
                chapterIds = listOf(1L, 2L, 3L),
                read = true,
            ),
            libraryNovelChapters(
                novelId = 20L,
                chapterIds = listOf(11L, 12L),
                read = false,
            ),
        )

        val target = resolveNovelSeriesReadingTarget(series, chapters)

        assertEquals(20L, target?.novel?.novel?.id)
        assertEquals(11L, target?.chapter?.id)
    }

    @Test
    fun `book state redirects the target chapter of a compiled book entry`() {
        val series = LibraryNovelSeries(
            series = NovelSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = listOf(
                libraryNovel(id = 10L, title = "One", totalChapters = 100, readCount = 90),
                libraryNovel(id = 20L, title = "Two", totalChapters = 20, readCount = 0),
            ),
        )
        val chapters = listOf(
            libraryNovelChapters(novelId = 10L, chapterIds = listOf(1L, 2L, 3L), read = true),
            libraryNovelChapters(novelId = 20L, chapterIds = listOf(11L, 12L), read = false),
        )
        val bookState = tachiyomi.domain.book.novel.model.NovelBookState
            .create(novelId = 20L, sourceId = 1L)
            .copy(enabled = true, lastChapterId = 12L)

        // Without the book state the heuristic targets the first unread chapter...
        assertEquals(11L, resolveNovelSeriesReadingTarget(series, chapters)?.chapter?.id)
        // ...a compiled book targets its stored reading position.
        assertEquals(
            12L,
            resolveNovelSeriesReadingTarget(series, chapters) { novelId ->
                bookState.takeIf { it.novelId == novelId }
            }?.chapter?.id,
        )
    }

    @Test
    fun `book position takes precedence within the last read volume`() {
        val series = series(
            listOf(
                libraryNovel(id = 10L, title = "One", totalChapters = 2, readCount = 1),
                libraryNovel(id = 20L, title = "Two", totalChapters = 2, readCount = 0),
            ),
        )
        val chapters = listOf(
            libraryNovelChapters(novelId = 10L, chapterIds = listOf(1L, 2L), read = false),
            libraryNovelChapters(novelId = 20L, chapterIds = listOf(11L, 12L), read = false),
        )
        val bookState = tachiyomi.domain.book.novel.model.NovelBookState
            .create(novelId = 20L, sourceId = 1L)
            .copy(enabled = true, lastChapterId = 12L)

        val target = resolveNovelSeriesReadingTarget(series, chapters, resumeChapterId = 11L) { novelId ->
            bookState.takeIf { it.novelId == novelId }
        }

        assertEquals(20L, target?.novel?.novel?.id)
        assertEquals(12L, target?.chapter?.id)
    }

    @Test
    fun `resumes history chapter in later volume without completed chapters`() {
        val series = series(
            listOf(
                libraryNovel(id = 10L, title = "One", totalChapters = 2, readCount = 1),
                libraryNovel(id = 20L, title = "Two", totalChapters = 2, readCount = 0),
            ),
        )
        val first = libraryNovelChapters(novelId = 10L, chapterIds = listOf(1L, 2L), read = false)
        val second = libraryNovelChapters(novelId = 20L, chapterIds = listOf(11L, 12L), read = false)
        val chapters = listOf(
            first.first to first.second.map { it.copy(read = it.id == 1L) },
            second.first to second.second.map { it.copy(lastPageRead = if (it.id == 11L) 5L else 0L) },
        )

        val target = resolveNovelSeriesReadingTarget(series, chapters, resumeChapterId = 11L)

        assertEquals(20L, target?.novel?.novel?.id)
        assertEquals(11L, target?.chapter?.id)
    }

    @Test
    fun `history chapter wins over later volumes when rereading`() {
        val series = series(
            listOf(
                libraryNovel(id = 10L, title = "One", totalChapters = 2, readCount = 2),
                libraryNovel(id = 20L, title = "Two", totalChapters = 2, readCount = 1),
            ),
        )
        val first = libraryNovelChapters(novelId = 10L, chapterIds = listOf(1L, 2L), read = true)
        val second = libraryNovelChapters(novelId = 20L, chapterIds = listOf(11L, 12L), read = false)
        val chapters = listOf(first, second.first to second.second.map { it.copy(read = it.id == 11L) })

        val target = resolveNovelSeriesReadingTarget(series, chapters, resumeChapterId = 1L)

        assertEquals(10L, target?.novel?.novel?.id)
        assertEquals(1L, target?.chapter?.id)
    }

    @Test
    fun `missing history chapter falls back to reading frontier`() {
        val series = series(
            listOf(
                libraryNovel(id = 10L, title = "One", totalChapters = 2, readCount = 2),
                libraryNovel(id = 20L, title = "Two", totalChapters = 2, readCount = 0),
            ),
        )
        val chapters = listOf(
            libraryNovelChapters(novelId = 10L, chapterIds = listOf(1L, 2L), read = true),
            libraryNovelChapters(novelId = 20L, chapterIds = listOf(11L, 12L), read = false),
        )

        val target = resolveNovelSeriesReadingTarget(series, chapters, resumeChapterId = 999L)

        assertEquals(20L, target?.novel?.novel?.id)
        assertEquals(11L, target?.chapter?.id)
    }

    private fun series(entries: List<LibraryNovel>) = LibraryNovelSeries(
        series = NovelSeries(
            id = 1L,
            title = "Series",
            description = null,
            categoryId = 0L,
            sortOrder = 0L,
            dateAdded = 0L,
            coverLastModified = 0L,
        ),
        entries = entries,
    )

    private fun libraryNovel(
        id: Long,
        title: String,
        totalChapters: Long,
        readCount: Long,
    ): LibraryNovel {
        return LibraryNovel(
            novel = Novel.create().copy(
                id = id,
                title = title,
                source = 1L,
                url = "https://example.com/$id",
            ),
            category = 0L,
            totalChapters = totalChapters,
            readCount = readCount,
            bookmarkCount = 0L,
            latestUpload = 0L,
            chapterFetchedAt = 0L,
            lastRead = 0L,
        )
    }

    private fun libraryNovelChapters(
        novelId: Long,
        chapterIds: List<Long>,
        read: Boolean,
    ): Pair<LibraryNovel, List<NovelChapter>> {
        val novel = libraryNovel(id = novelId, title = "Novel $novelId", totalChapters = 1, readCount = 0)
        return novel to chapterIds.mapIndexed { index, chapterId ->
            NovelChapter.create().copy(
                id = chapterId,
                novelId = novelId,
                read = read,
                chapterNumber = (index + 1).toDouble(),
                sourceOrder = (index + 1).toLong(),
                name = "Chapter $chapterId",
                url = "https://example.com/novel/$novelId/$chapterId",
            )
        }
    }
}
