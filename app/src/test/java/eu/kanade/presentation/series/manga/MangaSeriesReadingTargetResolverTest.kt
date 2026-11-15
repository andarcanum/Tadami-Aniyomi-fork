package eu.kanade.presentation.series.manga

import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.series.manga.model.LibraryMangaSeries
import tachiyomi.domain.series.manga.model.MangaSeries
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
                sourceOrders = listOf(1L, 0L),
                sorting = Manga.CHAPTER_SORTING_NUMBER,
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
            sourceOrder = 2L,
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
            sourceOrder = 0L,
            name = "Chapter 3",
            url = "https://example.com/manga/10/3",
        )

        val target = resolveMangaResumeChapter(
            manga = Manga.create(),
            chapters = listOf(chapter1, chapter2, chapter3),
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

    @Test
    fun `source resume starts at the first unknown chapter with or without missing history`() {
        for (number in listOf(-1.0, -2.0)) {
            val (manga, chapters) = libraryMangaChapters(
                mangaId = 10L,
                chapterIds = listOf(30L, 10L, 20L),
                read = false,
                chapterNumbers = List(3) { number },
                sourceOrders = listOf(0L, 2L, 1L),
            )

            assertEquals(10L, resolveMangaResumeChapter(manga.manga, chapters)?.id)
            assertEquals(10L, resolveMangaResumeChapter(manga.manga, chapters, fromChapterId = 999L)?.id)
        }
    }

    @Test
    fun `source resume follows source order even when parsed numbers disagree`() {
        val (manga, chapters) = libraryMangaChapters(
            mangaId = 10L,
            chapterIds = listOf(40L, 30L, 20L, 10L),
            read = false,
            chapterNumbers = listOf(-2.0, 1.0, -1.0, 100.0),
            sourceOrders = listOf(0L, 1L, 2L, 3L),
        )

        assertEquals(10L, resolveMangaResumeChapter(manga.manga, chapters)?.id)
    }

    @Test
    fun `resume follows each configured sorting mode but never display direction`() {
        val (manga, sourceChapters) = libraryMangaChapters(
            mangaId = 10L,
            chapterIds = listOf(4L, 2L, 1L, 3L),
            read = false,
            chapterNumbers = listOf(20.0, 10.0, 30.0, 40.0),
            sourceOrders = listOf(0L, 2L, 3L, 1L),
        )
        val chapters = sourceChapters.map { chapter ->
            when (chapter.id) {
                1L -> chapter.copy(dateUpload = 200L, name = "Delta")
                2L -> chapter.copy(dateUpload = 400L, name = "Charlie")
                3L -> chapter.copy(dateUpload = 100L, name = "Bravo")
                else -> chapter.copy(dateUpload = 300L, name = "Alpha")
            }
        }
        val cases = listOf(
            Manga.CHAPTER_SORTING_SOURCE to 1L,
            Manga.CHAPTER_SORTING_NUMBER to 2L,
            Manga.CHAPTER_SORTING_UPLOAD_DATE to 3L,
            Manga.CHAPTER_SORTING_ALPHABET to 4L,
        )

        for ((sorting, expectedId) in cases) {
            for (direction in listOf(Manga.CHAPTER_SORT_ASC, Manga.CHAPTER_SORT_DESC)) {
                val configuredManga = manga.manga.copy(chapterFlags = sorting or direction)

                assertEquals(expectedId, resolveMangaResumeChapter(configuredManga, chapters)?.id)
                assertEquals(1L, resolveMangaResumeChapter(configuredManga, chapters, fromChapterId = 1L)?.id)
            }
        }
    }

    @Test
    fun `equal primary keys resume by source descending then exact id ascending`() {
        val (manga, sourceChapters) = libraryMangaChapters(
            mangaId = 10L,
            chapterIds = listOf(20L, 30L, 10L),
            read = false,
            chapterNumbers = List(3) { 5.0 },
            sourceOrders = listOf(0L, 2L, 2L),
        )
        val chapters = sourceChapters.map { it.copy(dateUpload = 100L, name = "Same") }

        for (sorting in listOf(
            Manga.CHAPTER_SORTING_SOURCE,
            Manga.CHAPTER_SORTING_NUMBER,
            Manga.CHAPTER_SORTING_UPLOAD_DATE,
            Manga.CHAPTER_SORTING_ALPHABET,
        )) {
            assertEquals(10L, resolveMangaResumeChapter(manga.manga.copy(chapterFlags = sorting), chapters)?.id)
        }
    }

    @Test
    fun `missing history resumes the earliest partial chapter in reading order`() {
        val (manga, sourceChapters) = libraryMangaChapters(
            mangaId = 10L,
            chapterIds = listOf(1L, 2L, 3L),
            read = false,
            chapterNumbers = List(3) { -1.0 },
        )
        val chapters = sourceChapters.map { it.copy(lastPageRead = if (it.id == 3L) 0L else 5L) }

        assertEquals(1L, resolveMangaResumeChapter(manga.manga, chapters, fromChapterId = 999L)?.id)
    }

    @Test
    fun `missing history advances past the last read chapter rather than earlier unread gaps`() {
        val (manga, sourceChapters) = libraryMangaChapters(
            mangaId = 10L,
            chapterIds = listOf(1L, 2L, 3L, 4L),
            read = false,
            chapterNumbers = List(4) { -2.0 },
        )
        val chapters = sourceChapters.map { it.copy(read = it.id == 2L) }

        assertEquals(3L, resolveMangaResumeChapter(manga.manga, chapters, fromChapterId = 999L)?.id)
    }

    @Test
    fun `fully read manga resumes its last chapter in reading order`() {
        val (manga, chapters) = libraryMangaChapters(
            mangaId = 10L,
            chapterIds = listOf(1L, 2L, 3L),
            read = true,
            chapterNumbers = List(3) { -1.0 },
        )

        assertEquals(3L, resolveMangaResumeChapter(manga.manga, chapters)?.id)
    }

    @Test
    fun `empty chapters have no resume target`() {
        assertNull(resolveMangaResumeChapter(Manga.create(), emptyList(), fromChapterId = 999L))
    }

    @Test
    fun `series starts the next unknown-numbered volume at its first source chapter`() {
        val first = libraryManga(id = 10L, title = "One", totalChapters = 2L, readCount = 2L)
        val second = libraryManga(id = 20L, title = "Two", totalChapters = 3L, readCount = 0L)
        val chapters = listOf(
            first to libraryMangaChapters(mangaId = first.id, chapterIds = listOf(1L, 2L), read = true).second,
            second to libraryMangaChapters(
                mangaId = second.id,
                chapterIds = listOf(11L, 12L, 13L),
                read = false,
                chapterNumbers = listOf(-1.0, -2.0, -1.0),
            ).second,
        )

        val target = resolveMangaSeriesReadingTarget(series(listOf(first, second)), chapters)

        assertEquals(second.id, target?.manga?.id)
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
        sorting: Long = Manga.CHAPTER_SORTING_SOURCE,
    ): LibraryManga {
        return LibraryManga(
            manga = Manga.create().copy(
                id = id,
                title = title,
                source = 1L,
                url = "https://example.com/$id",
                chapterFlags = sorting,
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
        sourceOrders: List<Long> = chapterIds.mapIndexed { index, _ -> (chapterIds.lastIndex - index).toLong() },
        sorting: Long = Manga.CHAPTER_SORTING_SOURCE,
    ): Pair<LibraryManga, List<Chapter>> {
        // Chapter IDs are in reading order; newer source chapters have smaller source orders.
        val manga = libraryManga(
            id = mangaId,
            title = "Manga $mangaId",
            totalChapters = 1,
            readCount = 0,
            sorting = sorting,
        )
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
