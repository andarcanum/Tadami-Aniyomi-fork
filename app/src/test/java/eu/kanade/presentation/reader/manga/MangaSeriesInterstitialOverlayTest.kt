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
            ),
            chaptersByManga = listOf(
                first to listOf(
                    chapter(11L, first.id, "Chapter 1", read = false, chapterNumber = 1.0),
                    chapter(12L, first.id, "Chapter 2", read = false, chapterNumber = 2.0),
                ),
                second to listOf(
                    chapter(21L, second.id, "Chapter 1", read = false, chapterNumber = 1.0),
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
            ),
            chaptersByManga = listOf(
                first to listOf(
                    chapter(11L, first.id, "Chapter 1", read = true, chapterNumber = 1.0),
                    chapter(12L, first.id, "Chapter 2", read = true, chapterNumber = 2.0),
                ),
                second to listOf(
                    chapter(21L, second.id, "Chapter 1", read = false, chapterNumber = 1.0),
                    chapter(22L, second.id, "Chapter 2", read = false, chapterNumber = 2.0),
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
            ),
            chaptersByManga = listOf(
                first to listOf(
                    chapter(11L, first.id, "Chapter 1", read = true, chapterNumber = 1.0),
                    chapter(12L, first.id, "Chapter 2", read = true, chapterNumber = 2.0),
                ),
            ),
        )

        assertNull(result?.nextManga)
        assertNull(result?.nextChapterId)
        assertNull(result?.nextChapterName)
    }

    @Test
    fun `last chapter resolves when chapter number order differs from source order`() {
        val first = libraryManga(id = 1L, title = "One", readCount = 2L, totalChapters = 2L)

        val result = resolveMangaSeriesInterstitialState(
            series = series(first),
            currentManga = first.manga,
            currentChapter = chapter(
                id = 21L,
                mangaId = first.id,
                name = "Chapter 2",
                read = true,
                chapterNumber = 2.0,
                sourceOrder = 0L,
            ),
            chaptersByManga = listOf(
                first to listOf(
                    chapter(11L, first.id, "Chapter 1", read = true, chapterNumber = 1.0, sourceOrder = 1L),
                    chapter(21L, first.id, "Chapter 2", read = true, chapterNumber = 2.0, sourceOrder = 0L),
                ),
            ),
        )

        assertNotNull(result)
        assertNull(result?.nextManga)
        assertNull(result?.nextChapterId)
        assertNull(result?.nextChapterName)
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

    private fun chapter(
        id: Long,
        mangaId: Long,
        name: String,
        read: Boolean,
        chapterNumber: Double,
        sourceOrder: Long = chapterNumber.toLong(),
    ): Chapter {
        return Chapter.create().copy(
            id = id,
            mangaId = mangaId,
            name = name,
            read = read,
            lastPageRead = if (read) 1L else 0L,
            sourceOrder = sourceOrder,
            chapterNumber = chapterNumber,
        )
    }
}
