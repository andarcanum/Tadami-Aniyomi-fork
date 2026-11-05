package eu.kanade.presentation.series.manga

import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.series.manga.model.LibraryMangaSeries
import tachiyomi.domain.series.manga.model.MangaSeries
import kotlin.test.assertEquals

class MangaSeriesReadingTargetResolverTest {

    @Test
    fun `returns next manga when active manga is fully read`() {
        val series = LibraryMangaSeries(
            series = MangaSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = listOf(
                libraryManga(id = 10L, title = "One", totalChapters = 100, readCount = 90),
                libraryManga(id = 20L, title = "Two", totalChapters = 20, readCount = 0),
            ),
        )

        val chapters = listOf(
            libraryMangaChapters(
                mangaId = 10L,
                chapterIds = listOf(1L, 2L, 3L),
                read = true,
            ),
            libraryMangaChapters(
                mangaId = 20L,
                chapterIds = listOf(11L, 12L),
                read = false,
            ),
        )

        val target = resolveMangaSeriesReadingTarget(series, chapters)

        assertEquals(20L, target?.manga?.manga?.id)
        assertEquals(11L, target?.chapter?.id)
    }

    @Test
    fun `returns first unread chapter by chapter number order`() {
        val series = LibraryMangaSeries(
            series = MangaSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = listOf(
                libraryManga(id = 10L, title = "One", totalChapters = 100, readCount = 0),
            ),
        )

        val chapters = listOf(
            libraryMangaChapters(
                mangaId = 10L,
                chapterIds = listOf(21L, 11L),
                read = false,
                chapterNumbers = listOf(2.0, 1.0),
                sourceOrders = listOf(0L, 1L),
            ),
        )

        val target = resolveMangaSeriesReadingTarget(series, chapters)

        assertEquals(11L, target?.chapter?.id)
    }

    @Test
    fun `history chapter id keeps the exact chapter even when later unread exists`() {
        val chapter1 = Chapter.create().copy(
            id = 1L,
            mangaId = 10L,
            read = false,
            chapterNumber = 1.0,
            sourceOrder = 0L,
            name = "Chapter 1",
            url = "https://example.com/manga/10/1",
        )
        val chapter2 = Chapter.create().copy(
            id = 2L,
            mangaId = 10L,
            read = true,
            chapterNumber = 2.0,
            sourceOrder = 1L,
            name = "Chapter 2",
            url = "https://example.com/manga/10/2",
        )
        val chapter3 = Chapter.create().copy(
            id = 3L,
            mangaId = 10L,
            read = false,
            chapterNumber = 3.0,
            sourceOrder = 2L,
            name = "Chapter 3",
            url = "https://example.com/manga/10/3",
        )

        val target = resolveMangaResumeChapter(
            listOf(chapter1, chapter2, chapter3),
            fromChapterId = chapter2.id,
        )

        assertEquals(chapter2.id, target?.id)
    }

    @Test
    fun `resumes history chapter in later volume without completed chapters`() {
        val series = series(
            listOf(
                libraryManga(id = 10L, title = "One", totalChapters = 2, readCount = 1),
                libraryManga(id = 20L, title = "Two", totalChapters = 2, readCount = 0),
            ),
        )
        val first = libraryMangaChapters(mangaId = 10L, chapterIds = listOf(1L, 2L), read = false)
        val second = libraryMangaChapters(mangaId = 20L, chapterIds = listOf(11L, 12L), read = false)
        val chapters = listOf(
            first.first to first.second.map { it.copy(read = it.id == 1L) },
            second.first to second.second.map { it.copy(lastPageRead = if (it.id == 11L) 5L else 0L) },
        )

        val target = resolveMangaSeriesReadingTarget(series, chapters, resumeChapterId = 11L)

        assertEquals(20L, target?.manga?.manga?.id)
        assertEquals(11L, target?.chapter?.id)
    }

    @Test
    fun `history chapter wins over later volumes when rereading`() {
        val series = series(
            listOf(
                libraryManga(id = 10L, title = "One", totalChapters = 2, readCount = 2),
                libraryManga(id = 20L, title = "Two", totalChapters = 2, readCount = 1),
            ),
        )
        val first = libraryMangaChapters(mangaId = 10L, chapterIds = listOf(1L, 2L), read = true)
        val second = libraryMangaChapters(mangaId = 20L, chapterIds = listOf(11L, 12L), read = false)
        val chapters = listOf(first, second.first to second.second.map { it.copy(read = it.id == 11L) })

        val target = resolveMangaSeriesReadingTarget(series, chapters, resumeChapterId = 1L)

        assertEquals(10L, target?.manga?.manga?.id)
        assertEquals(1L, target?.chapter?.id)
    }

    @Test
    fun `missing history chapter falls back to reading frontier`() {
        val series = series(
            listOf(
                libraryManga(id = 10L, title = "One", totalChapters = 2, readCount = 2),
                libraryManga(id = 20L, title = "Two", totalChapters = 2, readCount = 0),
            ),
        )
        val chapters = listOf(
            libraryMangaChapters(mangaId = 10L, chapterIds = listOf(1L, 2L), read = true),
            libraryMangaChapters(mangaId = 20L, chapterIds = listOf(11L, 12L), read = false),
        )

        val target = resolveMangaSeriesReadingTarget(series, chapters, resumeChapterId = 999L)

        assertEquals(20L, target?.manga?.manga?.id)
        assertEquals(11L, target?.chapter?.id)
    }

    private fun series(entries: List<LibraryManga>) = LibraryMangaSeries(
        series = MangaSeries(
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

    private fun libraryManga(
        id: Long,
        title: String,
        totalChapters: Long,
        readCount: Long,
    ): LibraryManga {
        return LibraryManga(
            manga = Manga.create().copy(
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

    private fun libraryMangaChapters(
        mangaId: Long,
        chapterIds: List<Long>,
        read: Boolean,
        chapterNumbers: List<Double> = chapterIds.mapIndexed { index, _ -> (index + 1).toDouble() },
        sourceOrders: List<Long> = chapterIds.mapIndexed { index, _ -> (index + 1).toLong() },
    ): Pair<LibraryManga, List<Chapter>> {
        val manga = libraryManga(id = mangaId, title = "Manga $mangaId", totalChapters = 1, readCount = 0)
        return manga to chapterIds.mapIndexed { index, chapterId ->
            Chapter.create().copy(
                id = chapterId,
                mangaId = mangaId,
                read = read,
                chapterNumber = chapterNumbers[index],
                sourceOrder = sourceOrders[index],
                name = "Chapter $chapterId",
                url = "https://example.com/manga/$mangaId/$chapterId",
            )
        }
    }
}
