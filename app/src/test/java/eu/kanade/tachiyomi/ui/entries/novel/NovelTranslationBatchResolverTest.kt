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
    fun `unread scope ignores first n visible count and excludes read chapters`() {
        val chapters = chapters(count = 8).map { it.copy(read = it.id in setOf(4L, 7L)) }

        val resolved = resolveTranslationBatchChapterIds(
            scope = TranslationBatchScope.UNREAD,
            limit = 3,
            chapters = chapters,
            selectedChapterIds = emptySet(),
            downloadedChapterIds = emptySet(),
        )

        resolved shouldBe listOf(1L, 2L, 3L, 5L, 6L, 8L)
    }

    @Test
    fun `downloaded scope ignores first n visible count`() {
        val chapters = chapters(count = 8).map { chapter -> chapter.copy(id = chapter.id + 100) }

        val resolved = resolveTranslationBatchChapterIds(
            scope = TranslationBatchScope.DOWNLOADED,
            limit = 2,
            chapters = chapters,
            selectedChapterIds = emptySet(),
            downloadedChapterIds = setOf(101L, 102L, 103L),
        )

        resolved shouldBe listOf(101L, 102L, 103L)
    }

    @Test
    fun `selected scope ignores first n visible count and keeps visible order`() {
        val resolved = resolveTranslationBatchChapterIds(
            scope = TranslationBatchScope.SELECTED,
            limit = 2,
            chapters = chapters(count = 8),
            selectedChapterIds = setOf(7L, 8L, 1L, 2L),
            downloadedChapterIds = emptySet(),
        )

        resolved shouldBe listOf(1L, 2L, 7L, 8L)
    }

    @Test
    fun `range scope ignores first n visible count after range slicing`() {
        val resolved = resolveTranslationBatchChapterIds(
            scope = TranslationBatchScope.RANGE,
            limit = 2,
            chapters = chapters(count = 8),
            selectedChapterIds = emptySet(),
            downloadedChapterIds = emptySet(),
            rangeStart = 3,
            rangeEnd = 6,
        )

        resolved shouldBe listOf(3L, 4L, 5L, 6L)
    }

    @Test
    fun `selected scope retains all 31 selected chapters in reverse visible order`() {
        val resolved = resolveTranslationBatchChapterIds(
            scope = TranslationBatchScope.SELECTED,
            limit = 10,
            chapters = chapters(count = 35).reversed(),
            selectedChapterIds = (1L..31L).toSet(),
            downloadedChapterIds = emptySet(),
        )

        resolved shouldBe (31L downTo 1L).toList()
    }

    @Test
    fun `downloaded scope retains all 31 downloaded chapters`() {
        val resolved = resolveTranslationBatchChapterIds(
            scope = TranslationBatchScope.DOWNLOADED,
            limit = 10,
            chapters = chapters(count = 35),
            selectedChapterIds = emptySet(),
            downloadedChapterIds = (1L..31L).toSet(),
        )

        resolved shouldBe (1L..31L).toList()
    }

    @Test
    fun `unread scope retains all 31 unread chapters`() {
        val resolved = resolveTranslationBatchChapterIds(
            scope = TranslationBatchScope.UNREAD,
            limit = 10,
            chapters = chapters(count = 35).map { it.copy(read = it.id > 31L) },
            selectedChapterIds = emptySet(),
            downloadedChapterIds = emptySet(),
        )

        resolved shouldBe (1L..31L).toList()
    }

    @Test
    fun `range 1 through 31 is not truncated by first n visible count`() {
        val resolved = resolveTranslationBatchChapterIds(
            scope = TranslationBatchScope.RANGE,
            limit = 10,
            chapters = chapters(count = 35),
            selectedChapterIds = emptySet(),
            downloadedChapterIds = emptySet(),
            rangeStart = 1,
            rangeEnd = 31,
        )

        resolved shouldBe (1L..31L).toList()
    }

    @Test
    fun `first n visible alone limits the batch count in visible order`() {
        val resolved = resolveTranslationBatchChapterIds(
            scope = TranslationBatchScope.FIRST_N_VISIBLE,
            limit = 10,
            chapters = chapters(count = 31).reversed(),
            selectedChapterIds = emptySet(),
            downloadedChapterIds = emptySet(),
        )

        resolved shouldBe listOf(31L, 30L, 29L, 28L, 27L, 26L, 25L, 24L, 23L, 22L)
    }

    @Test
    fun `range stays positional with reversed visible order and unnumbered chapters`() {
        val resolved = resolveTranslationBatchChapterIds(
            scope = TranslationBatchScope.RANGE,
            limit = 10,
            chapters = chapters(count = 8).reversed().map { it.copy(chapterNumber = -1.0) },
            selectedChapterIds = emptySet(),
            downloadedChapterIds = emptySet(),
            rangeStart = 6,
            rangeEnd = 3,
        )

        resolved shouldBe listOf(6L, 5L, 4L, 3L)
    }

    @Test
    fun `nonpositive limits retain all matching chapters for every scope`() {
        for (limit in listOf(0, -1)) {
            for (scope in TranslationBatchScope.entries) {
                val resolved = resolveTranslationBatchChapterIds(
                    scope = scope,
                    limit = limit,
                    chapters = chapters(count = 4),
                    selectedChapterIds = setOf(1L, 2L, 3L, 4L),
                    downloadedChapterIds = setOf(1L, 2L, 3L, 4L),
                    rangeStart = 1,
                    rangeEnd = 4,
                )

                resolved shouldBe listOf(1L, 2L, 3L, 4L)
            }
        }
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
