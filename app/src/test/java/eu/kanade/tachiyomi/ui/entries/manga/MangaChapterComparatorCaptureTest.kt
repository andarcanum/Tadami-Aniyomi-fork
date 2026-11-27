package eu.kanade.tachiyomi.ui.entries.manga

import eu.kanade.domain.items.chapter.model.applyFilters
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.source.MangaSource
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.service.getChapterSort
import kotlin.test.assertSame

class MangaChapterComparatorCaptureTest {

    @Test
    fun `item filter constructs one canonical comparator per materialized sort`() {
        for (case in cases) {
            val manga = manga(case.flags)
            withFactorySpy(manga) {
                items.applyFilters(manga, downloadedOnly = false).toList().map { it.chapter.id } shouldBe case.ids
                verify(exactly = 1) { getChapterSort(manga, any()) }
            }
        }
    }

    @Test
    fun `processed state constructs one canonical comparator and reuses its lazy result`() {
        for (case in cases) {
            val manga = manga(case.flags)
            val state = state(manga)
            withFactorySpy(manga) {
                val sorted = state.processedChapters
                sorted.map { it.chapter.id } shouldBe case.ids
                assertSame(sorted, state.processedChapters)
                verify(exactly = 1) { getChapterSort(manga, any()) }
            }
        }
    }

    @Test
    fun `both actual item consumers preserve all canonical modes directions unknowns and ties`() {
        for (case in cases) {
            val manga = manga(case.flags)
            items.applyFilters(manga, downloadedOnly = false).toList().map { it.chapter.id } shouldBe case.ids
            state(manga).processedChapters.map { it.chapter.id } shouldBe case.ids
        }
    }

    private fun withFactorySpy(manga: Manga, block: () -> Unit) {
        mockkStatic("tachiyomi.domain.items.chapter.service.ChapterSorterKt")
        try {
            every { getChapterSort(manga, any()) } answers { callOriginal() }
            block()
        } finally {
            unmockkStatic("tachiyomi.domain.items.chapter.service.ChapterSorterKt")
        }
    }

    private fun manga(flags: Long): Manga = Manga.create().copy(id = 10L, source = 1L, chapterFlags = flags)

    private fun state(manga: Manga): MangaScreenModel.State.Success = MangaScreenModel.State.Success(
        manga = manga,
        source = SortingSource,
        isFromSource = false,
        chapters = items,
        availableScanlators = emptySet(),
        scanlatorChapterCounts = emptyMap(),
        excludedScanlators = emptySet(),
    )

    private val items = listOf(
        item(4L, 0L, -2.0, 300L, "Alpha"),
        item(2L, 2L, 1.0, 400L, "Charlie"),
        item(1L, 3L, 2.0, 200L, "Delta"),
        item(3L, 1L, 1.0, 100L, "Bravo"),
        item(5L, 2L, -1.0, 400L, "Charlie"),
        item(6L, 2L, 1.0, 400L, "Charlie"),
    )

    private fun item(id: Long, order: Long, number: Double, date: Long, name: String): ChapterList.Item {
        return ChapterList.Item(
            chapter = Chapter.create().copy(
                id = id,
                mangaId = 10L,
                sourceOrder = order,
                chapterNumber = number,
                dateUpload = date,
                name = name,
                url = "/test/chapter-$id",
            ),
            downloadState = MangaDownload.State.NOT_DOWNLOADED,
            downloadProgress = 0,
            selected = false,
        )
    }

    private data class SortCase(val flags: Long, val ids: List<Long>)

    private val cases = listOf(
        SortCase(Manga.CHAPTER_SORTING_SOURCE or Manga.CHAPTER_SORT_ASC, listOf(1L, 2L, 5L, 6L, 3L, 4L)),
        SortCase(Manga.CHAPTER_SORTING_SOURCE or Manga.CHAPTER_SORT_DESC, listOf(4L, 3L, 6L, 5L, 2L, 1L)),
        SortCase(Manga.CHAPTER_SORTING_NUMBER or Manga.CHAPTER_SORT_ASC, listOf(4L, 5L, 2L, 6L, 3L, 1L)),
        SortCase(Manga.CHAPTER_SORTING_NUMBER or Manga.CHAPTER_SORT_DESC, listOf(1L, 3L, 6L, 2L, 5L, 4L)),
        SortCase(Manga.CHAPTER_SORTING_UPLOAD_DATE or Manga.CHAPTER_SORT_ASC, listOf(3L, 1L, 4L, 2L, 5L, 6L)),
        SortCase(Manga.CHAPTER_SORTING_UPLOAD_DATE or Manga.CHAPTER_SORT_DESC, listOf(6L, 5L, 2L, 4L, 1L, 3L)),
        SortCase(Manga.CHAPTER_SORTING_ALPHABET or Manga.CHAPTER_SORT_ASC, listOf(4L, 3L, 2L, 5L, 6L, 1L)),
        SortCase(Manga.CHAPTER_SORTING_ALPHABET or Manga.CHAPTER_SORT_DESC, listOf(1L, 6L, 5L, 2L, 3L, 4L)),
    )

    private object SortingSource : MangaSource {
        override val id: Long = 1L
        override val name: String = "SortingSource"
    }
}
