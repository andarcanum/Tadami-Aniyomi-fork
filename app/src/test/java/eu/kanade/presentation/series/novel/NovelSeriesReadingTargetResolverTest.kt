package eu.kanade.presentation.series.novel

import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.series.novel.model.LibraryNovelSeries
import tachiyomi.domain.series.novel.model.NovelSeries
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NovelSeriesReadingTargetResolverTest {

    @Test
    fun `empty series returns null`() {
        val result = resolveNovelSeriesReadingTarget(
            series = series(entries = emptyList()),
            chapters = emptyList(),
        )

        assertNull(result)
    }

    @Test
    fun `no progress uses first title and first chapter`() {
        val one = libraryNovel(id = 10L, title = "One", readCount = 0, totalChapters = 3)
        val two = libraryNovel(id = 20L, title = "Two", readCount = 0, totalChapters = 2)
        val result = resolveNovelSeriesReadingTarget(
            series = series(entries = listOf(one, two)),
            chapters = listOf(
                one to listOf(
                    chapter(id = 100L, novelId = one.novel.id, read = false, lastPageRead = 0L, chapterNumber = 1.0, name = "Chapter 1"),
                    chapter(id = 101L, novelId = one.novel.id, read = false, lastPageRead = 0L, chapterNumber = 2.0, name = "Chapter 2"),
                ),
                two to listOf(
                    chapter(id = 200L, novelId = two.novel.id, read = false, lastPageRead = 0L, chapterNumber = 1.0, name = "Chapter 1"),
                ),
            ),
        )

        assertEquals(one.novel.id, result?.novel?.novel?.id)
        assertEquals(100L, result?.chapter?.id)
    }

    @Test
    fun `partial progress resumes the active title`() {
        val one = libraryNovel(id = 10L, title = "One", readCount = 1, totalChapters = 3)
        val two = libraryNovel(id = 20L, title = "Two", readCount = 0, totalChapters = 2)
        val result = resolveNovelSeriesReadingTarget(
            series = series(entries = listOf(one, two)),
            chapters = listOf(
                one to listOf(
                    chapter(id = 100L, novelId = one.novel.id, read = false, lastPageRead = 12L, chapterNumber = 1.0, name = "Chapter 1"),
                    chapter(id = 101L, novelId = one.novel.id, read = false, lastPageRead = 0L, chapterNumber = 2.0, name = "Chapter 2"),
                ),
                two to listOf(
                    chapter(id = 200L, novelId = two.novel.id, read = false, lastPageRead = 0L, chapterNumber = 1.0, name = "Chapter 1"),
                ),
            ),
        )

        assertEquals(one.novel.id, result?.novel?.novel?.id)
        assertEquals(100L, result?.chapter?.id)
    }

    @Test
    fun `completed title advances to the next committed title`() {
        val one = libraryNovel(id = 10L, title = "One", readCount = 3, totalChapters = 3)
        val two = libraryNovel(id = 20L, title = "Two", readCount = 0, totalChapters = 2)
        val result = resolveNovelSeriesReadingTarget(
            series = series(entries = listOf(one, two)),
            chapters = listOf(
                one to listOf(
                    chapter(id = 100L, novelId = one.novel.id, read = true, lastPageRead = 0L, chapterNumber = 1.0, name = "Chapter 1"),
                ),
                two to listOf(
                    chapter(id = 200L, novelId = two.novel.id, read = false, lastPageRead = 0L, chapterNumber = 1.0, name = "Chapter 1"),
                ),
            ),
        )

        assertEquals(two.novel.id, result?.novel?.novel?.id)
        assertEquals(200L, result?.chapter?.id)
    }

    @Test
    fun `reordered committed entries change the target`() {
        val one = libraryNovel(id = 10L, title = "One", readCount = 3, totalChapters = 3)
        val two = libraryNovel(id = 20L, title = "Two", readCount = 0, totalChapters = 2)
        val original = resolveNovelSeriesReadingTarget(
            series = series(entries = listOf(one, two)),
            chapters = listOf(
                one to listOf(
                    chapter(id = 100L, novelId = one.novel.id, read = true, lastPageRead = 0L, chapterNumber = 1.0, name = "Chapter 1"),
                ),
                two to listOf(
                    chapter(id = 200L, novelId = two.novel.id, read = false, lastPageRead = 0L, chapterNumber = 1.0, name = "Chapter 1"),
                ),
            ),
        )
        val reordered = resolveNovelSeriesReadingTarget(
            series = series(entries = listOf(two, one)),
            chapters = listOf(
                one to listOf(
                    chapter(id = 100L, novelId = one.novel.id, read = true, lastPageRead = 0L, chapterNumber = 1.0, name = "Chapter 1"),
                ),
                two to listOf(
                    chapter(id = 200L, novelId = two.novel.id, read = false, lastPageRead = 0L, chapterNumber = 1.0, name = "Chapter 1"),
                ),
            ),
        )

        assertEquals(two.novel.id, original?.novel?.novel?.id)
        assertEquals(one.novel.id, reordered?.novel?.novel?.id)
    }

    private fun series(entries: List<LibraryNovel>): LibraryNovelSeries {
        return LibraryNovelSeries(
            series = NovelSeries(
                id = 42L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = entries,
        )
    }

    private fun libraryNovel(
        id: Long,
        title: String,
        readCount: Long,
        totalChapters: Long,
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

    private fun chapter(
        id: Long,
        novelId: Long,
        read: Boolean,
        lastPageRead: Long,
        chapterNumber: Double,
        name: String,
    ): NovelChapter {
        return NovelChapter.create().copy(
            id = id,
            novelId = novelId,
            read = read,
            lastPageRead = lastPageRead,
            sourceOrder = chapterNumber.toLong(),
            chapterNumber = chapterNumber,
            name = name,
            url = "https://example.com/chapter/$id",
        )
    }
}
