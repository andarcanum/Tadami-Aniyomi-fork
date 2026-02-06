package tachiyomi.domain.items.novelchapter.service

import org.junit.Assert.assertEquals
import org.junit.Test
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelChapterSorterTest {

    @Test
    fun `source sorting default sorts by sourceOrder (same as manga)`() {
        val novel = Novel.create()

        val chapters = listOf(
            chapter(id = 1, sourceOrder = 20),
            chapter(id = 2, sourceOrder = 10),
            chapter(id = 3, sourceOrder = 30),
        )

        val sorted = chapters.sortedWith(Comparator(getNovelChapterSort(novel)))

        assertEquals(listOf(2L, 1L, 3L), sorted.map { it.id })
    }

    @Test
    fun `number sorting asc sorts by chapterNumber ascending`() {
        val novel = Novel.create().copy(
            chapterFlags = Novel.CHAPTER_SORT_ASC or Novel.CHAPTER_SORTING_NUMBER,
        )

        val chapters = listOf(
            chapter(id = 1, number = 20.0),
            chapter(id = 2, number = 10.0),
            chapter(id = 3, number = 30.0),
        )

        val sorted = chapters.sortedWith(Comparator(getNovelChapterSort(novel)))

        assertEquals(listOf(2L, 1L, 3L), sorted.map { it.id })
    }

    @Test
    fun `alphabet sorting desc sorts by name descending`() {
        val novel = Novel.create().copy(
            chapterFlags = Novel.CHAPTER_SORT_DESC or Novel.CHAPTER_SORTING_ALPHABET,
        )

        val chapters = listOf(
            chapter(id = 1, name = "A"),
            chapter(id = 2, name = "C"),
            chapter(id = 3, name = "B"),
        )

        val sorted = chapters.sortedWith(Comparator(getNovelChapterSort(novel)))

        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.id })
    }

    private fun chapter(
        id: Long,
        sourceOrder: Long = 0,
        name: String = "Chapter $id",
        number: Double = id.toDouble(),
    ) = NovelChapter(
        id = id,
        novelId = 1,
        read = false,
        bookmark = false,
        lastPageRead = 0,
        dateFetch = 0,
        sourceOrder = sourceOrder,
        url = "https://example.org/$id",
        name = name,
        dateUpload = 0,
        chapterNumber = number,
        scanlator = null,
        lastModifiedAt = 0,
        version = 1,
    )
}

