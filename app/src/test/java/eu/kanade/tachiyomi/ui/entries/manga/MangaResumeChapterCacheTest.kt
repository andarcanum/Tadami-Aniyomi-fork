package eu.kanade.tachiyomi.ui.entries.manga

import eu.kanade.presentation.series.manga.resolveMangaResumeChapterFromSorted
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class MangaResumeChapterCacheTest {

    @Test
    fun `same chapter list and reading sort reuse the cached order`() {
        val cache = MangaResumeChapterCache()
        val items = chapterItems()
        val manga = manga(Manga.CHAPTER_SORTING_SOURCE)

        val first = cache.get(items, manga)
        val second = cache.get(items, manga.copy(title = "Renamed"))

        assertEquals(listOf(1L, 2L, 3L), first.map { it.id })
        assertSame(first, second)
    }

    @Test
    fun `changing reading sort invalidates the cache even with the same chapter list`() {
        val cache = MangaResumeChapterCache()
        val items = chapterItems()

        val sourceOrder = cache.get(items, manga(Manga.CHAPTER_SORTING_SOURCE))
        val numberOrder = cache.get(items, manga(Manga.CHAPTER_SORTING_NUMBER))

        assertEquals(listOf(1L, 2L, 3L), sourceOrder.map { it.id })
        assertEquals(listOf(2L, 3L, 1L), numberOrder.map { it.id })
        assertNotSame(sourceOrder, numberOrder)
        assertEquals(listOf(1L, 2L, 3L), sourceOrder.map { it.id })
        assertSame(numberOrder, cache.get(items, manga(Manga.CHAPTER_SORTING_NUMBER)))
    }

    @Test
    fun `upload date and alphabet use the manga reading sort`() {
        val cache = MangaResumeChapterCache()
        val items = chapterItems()

        val dateOrder = cache.get(items, manga(Manga.CHAPTER_SORTING_UPLOAD_DATE))
        val nameOrder = cache.get(items, manga(Manga.CHAPTER_SORTING_ALPHABET))

        assertEquals(listOf(3L, 1L, 2L), dateOrder.map { it.id })
        assertEquals(listOf(2L, 3L, 1L), nameOrder.map { it.id })
        assertNotSame(dateOrder, nameOrder)
    }

    @Test
    fun `display direction does not reverse the cached reading order`() {
        val cache = MangaResumeChapterCache()
        val items = chapterItems()

        val descendingDisplay = cache.get(items, manga(Manga.CHAPTER_SORTING_SOURCE))
        val ascendingDisplay = cache.get(
            items,
            manga(Manga.CHAPTER_SORTING_SOURCE or Manga.CHAPTER_SORT_ASC),
        )

        assertEquals(listOf(1L, 2L, 3L), ascendingDisplay.map { it.id })
        assertSame(descendingDisplay, ascendingDisplay)
    }

    @Test
    fun `a new chapter list does not reuse stale read progress`() {
        val cache = MangaResumeChapterCache()
        val items = chapterItems()
        val manga = manga(Manga.CHAPTER_SORTING_SOURCE)
        val previous = cache.get(items, manga)
        val updatedItems = items.map { item ->
            if (item.chapter.id == 1L) {
                item.copy(chapter = item.chapter.copy(read = true, lastPageRead = 8L))
            } else {
                item
            }
        }

        val updated = cache.get(updatedItems, manga)

        assertNotSame(previous, updated)
        assertEquals(false, previous.first().read)
        assertEquals(true, updated.first().read)
        assertEquals(8L, updated.first().lastPageRead)
        assertEquals(2L, resolveMangaResumeChapterFromSorted(updated)?.id)
    }

    @Test
    fun `start from twelve unnumbered chapters keeps a following chapter in reader order`() {
        val cache = MangaResumeChapterCache()
        val items = (0L..11L).map { order ->
            item(Chapter.create().copy(id = order + 1L, sourceOrder = order, chapterNumber = -1.0))
        }
        val sorted = cache.get(items, manga(Manga.CHAPTER_SORTING_SOURCE))

        val start = resolveMangaResumeChapterFromSorted(sorted)
        val startIndex = sorted.indexOfFirst { it.id == start?.id }
        val next = sorted.getOrNull(startIndex + 1)

        assertEquals(12, sorted.size)
        assertEquals(11L, start?.sourceOrder)
        assertEquals(10L, next?.sourceOrder)
    }

    private fun manga(chapterFlags: Long): Manga = Manga.create().copy(
        id = 10L,
        chapterFlags = chapterFlags,
    )

    private fun chapterItems(): List<ChapterList.Item> = listOf(
        item(
            Chapter.create().copy(
                id = 3L,
                sourceOrder = 0L,
                chapterNumber = 2.0,
                dateUpload = 10L,
                name = "Beta",
            ),
        ),
        item(
            Chapter.create().copy(
                id = 1L,
                sourceOrder = 2L,
                chapterNumber = 3.0,
                dateUpload = 20L,
                name = "Gamma",
            ),
        ),
        item(
            Chapter.create().copy(
                id = 2L,
                sourceOrder = 1L,
                chapterNumber = 1.0,
                dateUpload = 30L,
                name = "Alpha",
            ),
        ),
    )

    private fun item(chapter: Chapter): ChapterList.Item = ChapterList.Item(
        chapter = chapter,
        downloadState = MangaDownload.State.NOT_DOWNLOADED,
        downloadProgress = 0,
    )
}
