package eu.kanade.tachiyomi.ui.entries.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelTranslationBatchResolverTest {

    @Test
    fun `range scope resolves inclusive chapter interval in visible order`() {
        val chapters = chapters(count = 8)

        val resolved = resolveTranslationBatchChapterIds(
            scope = TranslationBatchScope.RANGE,
            limit = 0,
            chapters = chapters,
            selectedChapterIds = emptySet(),
            downloadedChapterIds = emptySet(),
            rangeStart = 3,
            rangeEnd = 6,
        )

        resolved shouldBe listOf(3L, 4L, 5L, 6L)
    }

    @Test
    fun `range scope normalizes reversed and out of bounds inputs`() {
        val chapters = chapters(count = 5)

        val resolved = resolveTranslationBatchChapterIds(
            scope = TranslationBatchScope.RANGE,
            limit = 0,
            chapters = chapters,
            selectedChapterIds = emptySet(),
            downloadedChapterIds = emptySet(),
            rangeStart = 12,
            rangeEnd = 2,
        )

        resolved shouldBe listOf(2L, 3L, 4L, 5L)
    }

    @Test
    fun `range scope returns empty list for empty chapter list`() {
        val resolved = resolveTranslationBatchChapterIds(
            scope = TranslationBatchScope.RANGE,
            limit = 0,
            chapters = emptyList(),
            selectedChapterIds = emptySet(),
            downloadedChapterIds = emptySet(),
            rangeStart = 1,
            rangeEnd = 10,
        )

        resolved shouldBe emptyList()
    }

    @Test
    fun `range scope returns empty list when range lies fully outside the chapter list`() {
        val resolved = resolveTranslationBatchChapterIds(
            scope = TranslationBatchScope.RANGE,
            limit = 0,
            chapters = chapters(count = 5),
            selectedChapterIds = emptySet(),
            downloadedChapterIds = emptySet(),
            rangeStart = 12,
            rangeEnd = 14,
        )

        resolved shouldBe emptyList()
    }

    @Test
    fun `unread scope applies global limit`() {
        val chapters = chapters(count = 8)

        val resolved = resolveTranslationBatchChapterIds(
            scope = TranslationBatchScope.UNREAD,
            limit = 3,
            chapters = chapters,
            selectedChapterIds = emptySet(),
            downloadedChapterIds = emptySet(),
        )

        resolved shouldBe listOf(1L, 2L, 3L)
    }

    @Test
    fun `downloaded scope applies global limit`() {
        val chapters = chapters(count = 8).map { chapter -> chapter.copy(id = chapter.id + 100) }

        val resolved = resolveTranslationBatchChapterIds(
            scope = TranslationBatchScope.DOWNLOADED,
            limit = 2,
            chapters = chapters,
            selectedChapterIds = emptySet(),
            downloadedChapterIds = setOf(101L, 102L, 103L),
        )

        resolved shouldBe listOf(101L, 102L)
    }

    @Test
    fun `selected scope applies global limit`() {
        val resolved = resolveTranslationBatchChapterIds(
            scope = TranslationBatchScope.SELECTED,
            limit = 2,
            chapters = chapters(count = 8),
            selectedChapterIds = setOf(7L, 8L, 1L, 2L),
            downloadedChapterIds = emptySet(),
        )

        resolved shouldBe listOf(1L, 2L)
    }

    @Test
    fun `range scope applies global limit after range slicing`() {
        val resolved = resolveTranslationBatchChapterIds(
            scope = TranslationBatchScope.RANGE,
            limit = 2,
            chapters = chapters(count = 8),
            selectedChapterIds = emptySet(),
            downloadedChapterIds = emptySet(),
            rangeStart = 3,
            rangeEnd = 6,
        )

        resolved shouldBe listOf(3L, 4L)
    }

    @Test
    fun `limit zero means unlimited for all scopes`() {
        val chapters = chapters(count = 4)

        val unreadUnlimited = resolveTranslationBatchChapterIds(
            scope = TranslationBatchScope.UNREAD,
            limit = 0,
            chapters = chapters,
            selectedChapterIds = emptySet(),
            downloadedChapterIds = emptySet(),
        )

        unreadUnlimited shouldBe listOf(1L, 2L, 3L, 4L)
    }

    private fun chapters(count: Int): List<NovelChapter> {
        return (1..count).map { index ->
            NovelChapter.create().copy(
                id = index.toLong(),
                novelId = 1L,
                chapterNumber = index.toDouble(),
                sourceOrder = index.toLong(),
                read = false,
                url = "https://example.org/ch$index",
                name = "Chapter $index",
            )
        }
    }
}
