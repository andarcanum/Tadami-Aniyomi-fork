package eu.kanade.tachiyomi.data.library.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NovelChapter

/**
 * The library update job filters freshly synced chapters before queueing downloads.
 * Downloaded-state must come from a single precomputed id set (one directory walk) instead
 * of per-chapter file checks, and category gating must keep its existing semantics.
 */
class NovelChapterDownloadFilterTest {

    @Test
    fun `chapters already downloaded are filtered out by precomputed ids`() {
        val chapters = listOf(chapter(id = 1), chapter(id = 2), chapter(id = 3))

        filterNovelChaptersForDownload(
            newChapters = chapters,
            unreadOnly = false,
            includedCategories = emptySet(),
            excludedCategories = emptySet(),
            categoryIds = setOf(0L),
            downloadedChapterIds = setOf(2L),
        ) shouldBe listOf(chapter(id = 1), chapter(id = 3))
    }

    @Test
    fun `unread-only mode drops already read chapters`() {
        val chapters = listOf(
            chapter(id = 1, read = true),
            chapter(id = 2, read = false),
        )

        filterNovelChaptersForDownload(
            newChapters = chapters,
            unreadOnly = true,
            includedCategories = emptySet(),
            excludedCategories = emptySet(),
            categoryIds = setOf(0L),
            downloadedChapterIds = emptySet(),
        ) shouldBe listOf(chapter(id = 2, read = false))
    }

    @Test
    fun `include list keeps nothing when novel categories do not intersect`() {
        val chapters = listOf(chapter(id = 1))

        filterNovelChaptersForDownload(
            newChapters = chapters,
            unreadOnly = false,
            includedCategories = setOf(5L),
            excludedCategories = emptySet(),
            categoryIds = setOf(0L),
            downloadedChapterIds = emptySet(),
        ) shouldBe emptyList()
    }

    @Test
    fun `include list keeps chapters when novel categories intersect`() {
        val chapters = listOf(chapter(id = 1), chapter(id = 2))

        filterNovelChaptersForDownload(
            newChapters = chapters,
            unreadOnly = false,
            includedCategories = setOf(5L),
            excludedCategories = emptySet(),
            categoryIds = setOf(0L, 5L),
            downloadedChapterIds = emptySet(),
        ) shouldBe chapters
    }

    @Test
    fun `exclude list drops everything when any novel category is excluded`() {
        val chapters = listOf(chapter(id = 1))

        filterNovelChaptersForDownload(
            newChapters = chapters,
            unreadOnly = false,
            includedCategories = emptySet(),
            excludedCategories = setOf(7L),
            categoryIds = setOf(0L, 7L),
            downloadedChapterIds = emptySet(),
        ) shouldBe emptyList()
    }

    @Test
    fun `input order is preserved for surviving chapters`() {
        val chapters = listOf(
            chapter(id = 3, sourceOrder = 3),
            chapter(id = 1, sourceOrder = 1),
            chapter(id = 2, sourceOrder = 2),
        )

        filterNovelChaptersForDownload(
            newChapters = chapters,
            unreadOnly = false,
            includedCategories = emptySet(),
            excludedCategories = emptySet(),
            categoryIds = emptySet(),
            downloadedChapterIds = setOf(1L),
        ) shouldBe listOf(chapter(id = 3, sourceOrder = 3), chapter(id = 2, sourceOrder = 2))
    }

    private fun chapter(
        id: Long,
        read: Boolean = false,
        sourceOrder: Long = id,
    ): NovelChapter {
        return NovelChapter.create().copy(
            id = id,
            read = read,
            sourceOrder = sourceOrder,
        )
    }
}
