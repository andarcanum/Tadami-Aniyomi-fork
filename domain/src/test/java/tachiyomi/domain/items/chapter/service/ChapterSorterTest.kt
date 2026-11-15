package tachiyomi.domain.items.chapter.service

import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import kotlin.test.assertEquals

class ChapterSorterTest {

    @Test
    fun `reading order follows each sorting mode independently of display direction`() {
        val chapters = listOf(
            chapter(4L, sourceOrder = 0L, chapterNumber = 20.0, dateUpload = 300L, name = "Alpha"),
            chapter(2L, sourceOrder = 2L, chapterNumber = 10.0, dateUpload = 400L, name = "Charlie"),
            chapter(1L, sourceOrder = 3L, chapterNumber = 30.0, dateUpload = 200L, name = "Delta"),
            chapter(3L, sourceOrder = 1L, chapterNumber = 40.0, dateUpload = 100L, name = "Bravo"),
        )
        val cases = listOf(
            Manga.CHAPTER_SORTING_SOURCE to listOf(1L, 2L, 3L, 4L),
            Manga.CHAPTER_SORTING_NUMBER to listOf(2L, 4L, 1L, 3L),
            Manga.CHAPTER_SORTING_UPLOAD_DATE to listOf(3L, 1L, 4L, 2L),
            Manga.CHAPTER_SORTING_ALPHABET to listOf(4L, 3L, 2L, 1L),
        )

        for ((sorting, expected) in cases) {
            val descendingManga = manga(sorting, descending = true)
            assertEquals(
                expected,
                chapters.sortedWith(getChapterSort(descendingManga, sortDescending = false)).map { it.id },
                "Reading order for sorting=$sorting",
            )
            assertEquals(
                expected.reversed(),
                chapters.sortedWith(getChapterSort(descendingManga, sortDescending = true)).map { it.id },
                "Descending order for sorting=$sorting",
            )
            assertEquals(expected.reversed(), chapters.sortedWith(getChapterSort(descendingManga)).map { it.id })
            assertEquals(expected, chapters.sortedWith(getChapterSort(manga(sorting))).map { it.id })
        }
    }

    @Test
    fun `source reading order retains all unknown chapters in latest-first source semantics`() {
        for (number in listOf(-1.0, -2.0)) {
            val chapters = listOf(
                chapter(30L, sourceOrder = 0L, chapterNumber = number),
                chapter(10L, sourceOrder = 2L, chapterNumber = number),
                chapter(20L, sourceOrder = 1L, chapterNumber = number),
            )

            assertEquals(
                listOf(10L, 20L, 30L),
                chapters.sortedWith(getChapterSort(manga(Manga.CHAPTER_SORTING_SOURCE), false)).map { it.id },
            )
        }
    }

    @Test
    fun `source reading order does not prioritize recognized or negative chapter numbers`() {
        val chapters = listOf(
            chapter(40L, sourceOrder = 0L, chapterNumber = -2.0),
            chapter(30L, sourceOrder = 1L, chapterNumber = 1.0),
            chapter(20L, sourceOrder = 2L, chapterNumber = -1.0),
            chapter(10L, sourceOrder = 3L, chapterNumber = 100.0),
        )

        assertEquals(
            listOf(10L, 20L, 30L, 40L),
            chapters.sortedWith(getChapterSort(manga(Manga.CHAPTER_SORTING_SOURCE), false)).map { it.id },
        )
    }

    @Test
    fun `equal keys use source descending then id ascending and descending reverses all ties`() {
        val chapters = listOf(
            chapter(20L, sourceOrder = 0L),
            chapter(30L, sourceOrder = 2L),
            chapter(10L, sourceOrder = 2L),
        )
        val inputs = listOf(chapters, chapters.reversed(), listOf(chapters[1], chapters[2], chapters[0]))

        for (sorting in sortingModes) {
            for (input in inputs) {
                assertEquals(
                    listOf(10L, 30L, 20L),
                    input.sortedWith(getChapterSort(manga(sorting), false)).map { it.id },
                    "Equal-key reading order for sorting=$sorting",
                )
                assertEquals(
                    listOf(20L, 30L, 10L),
                    input.sortedWith(getChapterSort(manga(sorting), true)).map { it.id },
                    "Equal-key descending order for sorting=$sorting",
                )
            }
        }
    }

    @Test
    fun `alphabet sorting uses collation rather than raw character order`() {
        val chapters = listOf(
            chapter(2L, sourceOrder = 1L, name = "Zebra"),
            chapter(1L, sourceOrder = 0L, name = "apple"),
        )

        assertEquals(
            listOf(1L, 2L),
            chapters.sortedWith(getChapterSort(manga(Manga.CHAPTER_SORTING_ALPHABET), false)).map { it.id },
        )
    }

    @Test
    fun `source date and identity comparisons handle long extremes without overflow`() {
        val chapters = listOf(
            chapter(Long.MAX_VALUE, sourceOrder = Long.MAX_VALUE, dateUpload = Long.MIN_VALUE),
            chapter(Long.MIN_VALUE, sourceOrder = Long.MAX_VALUE, dateUpload = Long.MIN_VALUE),
            chapter(0L, sourceOrder = Long.MIN_VALUE, dateUpload = Long.MAX_VALUE),
        )

        for (sorting in sortingModes) {
            assertEquals(
                listOf(Long.MIN_VALUE, Long.MAX_VALUE, 0L),
                chapters.sortedWith(getChapterSort(manga(sorting), false)).map { it.id },
            )
            assertEquals(
                listOf(0L, Long.MAX_VALUE, Long.MIN_VALUE),
                chapters.sortedWith(getChapterSort(manga(sorting), true)).map { it.id },
            )
        }
    }

    private val sortingModes = listOf(
        Manga.CHAPTER_SORTING_SOURCE,
        Manga.CHAPTER_SORTING_NUMBER,
        Manga.CHAPTER_SORTING_UPLOAD_DATE,
        Manga.CHAPTER_SORTING_ALPHABET,
    )

    private fun manga(sorting: Long, descending: Boolean = false) = Manga.create().copy(
        chapterFlags = sorting or if (descending) Manga.CHAPTER_SORT_DESC else Manga.CHAPTER_SORT_ASC,
    )

    private fun chapter(
        id: Long,
        sourceOrder: Long,
        chapterNumber: Double = 5.0,
        dateUpload: Long = 100L,
        name: String = "Same",
    ) = Chapter.create().copy(
        id = id,
        sourceOrder = sourceOrder,
        chapterNumber = chapterNumber,
        dateUpload = dateUpload,
        name = name,
    )
}
