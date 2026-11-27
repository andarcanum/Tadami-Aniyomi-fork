package eu.kanade.presentation.reader.manga

import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.series.manga.model.LibraryMangaSeries
import tachiyomi.domain.series.manga.model.MangaSeries
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MangaSeriesInterstitialOverlayTest {

    @Test
    fun `non-terminal chapter returns null`() {
        val first = libraryManga(id = 1L, title = "One", readCount = 0L, totalChapters = 2L)
        val second = libraryManga(id = 2L, title = "Two", readCount = 0L, totalChapters = 2L)

        val result = resolveMangaSeriesInterstitialState(
            series = series(first, second),
            currentManga = first.manga,
            currentChapter = chapter(
                id = 11L,
                mangaId = first.id,
                name = "Chapter 1",
                read = false,
                chapterNumber = 1.0,
                sourceOrder = 1L,
            ),
            chaptersByManga = listOf(
                first to listOf(
                    chapter(11L, first.id, "Chapter 1", read = false, chapterNumber = 1.0, sourceOrder = 1L),
                    chapter(12L, first.id, "Chapter 2", read = false, chapterNumber = 2.0, sourceOrder = 0L),
                ),
                second to listOf(
                    chapter(21L, second.id, "Chapter 1", read = false, chapterNumber = 1.0, sourceOrder = 1L),
                ),
            ),
        )

        assertNull(result)
    }

    @Test
    fun `last chapter resolves next title and chapter`() {
        val first = libraryManga(id = 1L, title = "One", readCount = 2L, totalChapters = 2L)
        val second = libraryManga(id = 2L, title = "Two", readCount = 0L, totalChapters = 2L)

        val result = resolveMangaSeriesInterstitialState(
            series = series(first, second),
            currentManga = first.manga,
            currentChapter = chapter(
                id = 12L,
                mangaId = first.id,
                name = "Chapter 2",
                read = true,
                chapterNumber = 2.0,
                sourceOrder = 0L,
            ),
            chaptersByManga = listOf(
                first to listOf(
                    chapter(11L, first.id, "Chapter 1", read = true, chapterNumber = 1.0, sourceOrder = 1L),
                    chapter(12L, first.id, "Chapter 2", read = true, chapterNumber = 2.0, sourceOrder = 0L),
                ),
                second to listOf(
                    chapter(21L, second.id, "Chapter 1", read = false, chapterNumber = 1.0, sourceOrder = 1L),
                    chapter(22L, second.id, "Chapter 2", read = false, chapterNumber = 2.0, sourceOrder = 0L),
                ),
            ),
        )

        assertEquals("Series", result?.seriesTitle)
        assertEquals("One", result?.currentMangaTitle)
        assertEquals(second.id, result?.nextManga?.id)
        assertEquals(21L, result?.nextChapterId)
        assertEquals("Chapter 1", result?.nextChapterName)
    }

    @Test
    fun `final title still resolves completed state`() {
        val first = libraryManga(id = 1L, title = "One", readCount = 2L, totalChapters = 2L)

        val result = resolveMangaSeriesInterstitialState(
            series = series(first),
            currentManga = first.manga,
            currentChapter = chapter(
                id = 12L,
                mangaId = first.id,
                name = "Chapter 2",
                read = true,
                chapterNumber = 2.0,
                sourceOrder = 0L,
            ),
            chaptersByManga = listOf(
                first to listOf(
                    chapter(11L, first.id, "Chapter 1", read = true, chapterNumber = 1.0, sourceOrder = 1L),
                    chapter(12L, first.id, "Chapter 2", read = true, chapterNumber = 2.0, sourceOrder = 0L),
                ),
            ),
        )

        assertNotNull(result)
        assertNull(result?.nextManga)
        assertNull(result?.nextChapterId)
        assertNull(result?.nextChapterName)
    }

    @Test
    fun `number-sorted endpoint differs from the source reading endpoint`() {
        val first = libraryManga(
            id = 1L,
            title = "One",
            readCount = 2L,
            totalChapters = 2L,
            sorting = Manga.CHAPTER_SORTING_NUMBER,
        )

        val result = resolveMangaSeriesInterstitialState(
            series = series(first),
            currentManga = first.manga,
            currentChapter = chapter(
                id = 21L,
                mangaId = first.id,
                name = "Chapter 2",
                read = true,
                chapterNumber = 2.0,
                sourceOrder = 1L,
            ),
            chaptersByManga = listOf(
                first to listOf(
                    chapter(11L, first.id, "Chapter 1", read = true, chapterNumber = 1.0, sourceOrder = 0L),
                    chapter(21L, first.id, "Chapter 2", read = true, chapterNumber = 2.0, sourceOrder = 1L),
                ),
            ),
        )

        assertNotNull(result)
        assertNull(result?.nextManga)
        assertNull(result?.nextChapterId)
        assertNull(result?.nextChapterName)
    }

    @Test
    fun `multiple unknown source chapters reach the interstitial only at the reading endpoint`() {
        val first = libraryManga(id = 1L, title = "One", readCount = 3L, totalChapters = 3L)
        for (number in listOf(-1.0, -2.0)) {
            val chapters = listOf(
                chapter(13L, first.id, "Latest", read = true, chapterNumber = number, sourceOrder = 0L),
                chapter(11L, first.id, "First", read = true, chapterNumber = number, sourceOrder = 2L),
                chapter(12L, first.id, "Middle", read = true, chapterNumber = number, sourceOrder = 1L),
            )
            val cases = listOf(11L to false, 12L to false, 13L to true)

            for ((chapterId, isTerminal) in cases) {
                val result = resolveMangaSeriesInterstitialState(
                    series = series(first),
                    currentManga = first.manga,
                    currentChapter = chapters.first { it.id == chapterId },
                    chaptersByManga = listOf(first to chapters),
                )

                assertEquals(isTerminal, result != null, "Unknown number=$number, chapter=$chapterId")
            }
        }
    }

    @Test
    fun `endpoint follows current manga sorting rather than a stale chapter wrapper`() {
        val first = libraryManga(id = 1L, title = "One", readCount = 4L, totalChapters = 4L)
        val chapters = listOf(
            chapter(14L, first.id, "Alpha", true, 20.0, sourceOrder = 0L, dateUpload = 300L),
            chapter(12L, first.id, "Charlie", true, 10.0, sourceOrder = 2L, dateUpload = 400L),
            chapter(11L, first.id, "Delta", true, 30.0, sourceOrder = 3L, dateUpload = 200L),
            chapter(13L, first.id, "Bravo", true, 40.0, sourceOrder = 1L, dateUpload = 100L),
        )
        val cases = listOf(
            Manga.CHAPTER_SORTING_SOURCE to 14L,
            Manga.CHAPTER_SORTING_NUMBER to 13L,
            Manga.CHAPTER_SORTING_UPLOAD_DATE to 12L,
            Manga.CHAPTER_SORTING_ALPHABET to 11L,
        )

        for ((sorting, terminalId) in cases) {
            for (chapter in chapters) {
                val result = resolveMangaSeriesInterstitialState(
                    series = series(first),
                    currentManga = first.manga.copy(chapterFlags = sorting),
                    currentChapter = chapter,
                    chaptersByManga = listOf(first to chapters),
                )

                assertEquals(chapter.id == terminalId, result != null, "Sorting=$sorting, chapter=" + chapter.id)
            }
        }
    }

    @Test
    fun `next volume resume uses the next series entry sorting configuration`() {
        val first = libraryManga(id = 1L, title = "One", readCount = 1L, totalChapters = 1L)
        val second = libraryManga(id = 2L, title = "Two", readCount = 0L, totalChapters = 4L)
        val terminal = chapter(10L, first.id, "End", true, -1.0, sourceOrder = 0L)
        val nextChapters = listOf(
            chapter(24L, second.id, "Alpha", false, 20.0, sourceOrder = 0L, dateUpload = 300L),
            chapter(22L, second.id, "Charlie", false, 10.0, sourceOrder = 2L, dateUpload = 400L),
            chapter(21L, second.id, "Delta", false, 30.0, sourceOrder = 3L, dateUpload = 200L),
            chapter(23L, second.id, "Bravo", false, 40.0, sourceOrder = 1L, dateUpload = 100L),
        )
        val cases = listOf(
            Manga.CHAPTER_SORTING_SOURCE to 21L,
            Manga.CHAPTER_SORTING_NUMBER to 22L,
            Manga.CHAPTER_SORTING_UPLOAD_DATE to 23L,
            Manga.CHAPTER_SORTING_ALPHABET to 24L,
        )

        for ((sorting, expectedId) in cases) {
            val nextEntry = second.copy(manga = second.manga.copy(chapterFlags = sorting))
            val result = resolveMangaSeriesInterstitialState(
                series = series(first, nextEntry),
                currentManga = first.manga,
                currentChapter = terminal,
                chaptersByManga = listOf(first to listOf(terminal), second to nextChapters),
            )

            assertEquals(second.id, result?.nextManga?.id)
            assertEquals(expectedId, result?.nextChapterId)
        }
    }

    @Test
    fun `next unknown volume starts in source order and still resumes partial progress`() {
        val first = libraryManga(id = 1L, title = "One", readCount = 1L, totalChapters = 1L)
        val second = libraryManga(id = 2L, title = "Two", readCount = 0L, totalChapters = 3L)
        val terminal = chapter(10L, first.id, "End", true, -1.0, sourceOrder = 0L)
        val start = chapter(21L, second.id, "First", false, -1.0, sourceOrder = 2L)
        val middle = chapter(22L, second.id, "Middle", false, -2.0, sourceOrder = 1L)
        val last = chapter(23L, second.id, "Latest", false, -1.0, sourceOrder = 0L)

        for ((lastPageRead, expectedId) in listOf(0L to 21L, 5L to 22L)) {
            val result = resolveMangaSeriesInterstitialState(
                series = series(first, second),
                currentManga = first.manga,
                currentChapter = terminal,
                chaptersByManga = listOf(
                    first to listOf(terminal),
                    second to listOf(last, middle.copy(lastPageRead = lastPageRead), start),
                ),
            )

            assertEquals(expectedId, result?.nextChapterId)
        }
    }

    private fun series(vararg mangas: LibraryManga): LibraryMangaSeries {
        return LibraryMangaSeries(
            series = MangaSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = mangas.toList(),
        )
    }

    private fun libraryManga(
        id: Long,
        title: String,
        readCount: Long,
        totalChapters: Long,
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

    private fun chapter(
        id: Long,
        mangaId: Long,
        name: String,
        read: Boolean,
        chapterNumber: Double,
        sourceOrder: Long,
        dateUpload: Long = 0L,
    ): Chapter {
        return Chapter.create().copy(
            id = id,
            mangaId = mangaId,
            name = name,
            read = read,
            lastPageRead = if (read) 1L else 0L,
            sourceOrder = sourceOrder,
            chapterNumber = chapterNumber,
            dateUpload = dateUpload,
        )
    }
}
