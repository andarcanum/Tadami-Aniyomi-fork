package eu.kanade.tachiyomi.ui.entries.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelChapterDisplayRowTest {

    @Test
    fun `branch rows renumber chapters sequentially`() {
        val rows = resolveNovelBranchChapterRows(
            listOf(
                chapter(id = 1L, chapterNumber = 1.0, sourceOrder = 10),
                chapter(id = 2L, chapterNumber = 3.0, sourceOrder = 20),
                chapter(id = 3L, chapterNumber = 5.0, sourceOrder = 30),
            ),
        )

        rows.map { it.displayNumber } shouldBe listOf(1, 2, 3)
        rows.map { it.chapter.chapterNumber } shouldBe listOf(1.0, 3.0, 5.0)
    }

    @Test
    fun `grouped rows bundle duplicate chapter numbers and keep them ordered`() {
        val rows = resolveNovelGroupedChapterRows(
            listOf(
                chapter(id = 1L, chapterNumber = 1.0, sourceOrder = 20, scanlator = "Team A"),
                chapter(id = 2L, chapterNumber = 1.0, sourceOrder = 10, scanlator = "Team B"),
                chapter(id = 3L, chapterNumber = 2.0, sourceOrder = 30, scanlator = "Team C"),
            ),
            expandedGroupKeys = emptySet(),
        )

        val groups = rows.filterIsInstance<NovelChapterDisplayRow.ChapterGroup>()
        groups.map { it.displayNumber } shouldBe listOf(1, 2)
        groups.map { it.chapters.map { chapter -> chapter.id } } shouldBe listOf(
            listOf(2L, 1L),
            listOf(3L),
        )
    }

    @Test
    fun `expanded grouped rows insert variants after the matching group`() {
        val targetGroupKey = resolveNovelChapterGroupKey(1.0)
        val rows = resolveNovelGroupedChapterRows(
            listOf(
                chapter(id = 1L, chapterNumber = 1.0, sourceOrder = 10, scanlator = "Team A"),
                chapter(id = 2L, chapterNumber = 1.0, sourceOrder = 20, scanlator = "Team B"),
                chapter(id = 3L, chapterNumber = 2.0, sourceOrder = 30, scanlator = "Team C"),
            ),
            expandedGroupKeys = setOf(targetGroupKey),
        )

        rows.filterIsInstance<NovelChapterDisplayRow.ChapterGroup>().size shouldBe 2
        rows.filterIsInstance<NovelChapterDisplayRow.ChapterVariant>().map { it.chapter.id } shouldBe listOf(
            1L,
            2L,
        )
        rows.filterIsInstance<NovelChapterDisplayRow.ChapterVariant>().map { it.displayNumber } shouldBe listOf(
            1,
            1,
        )
        resolveNovelChapterRowIndex(
            chapters = listOf(
                chapter(id = 1L, chapterNumber = 1.0, sourceOrder = 10, scanlator = "Team A"),
                chapter(id = 2L, chapterNumber = 1.0, sourceOrder = 20, scanlator = "Team B"),
                chapter(id = 3L, chapterNumber = 2.0, sourceOrder = 30, scanlator = "Team C"),
            ),
            expandedGroupKeys = setOf(targetGroupKey),
            groupedByChapter = true,
            targetChapterId = 2L,
        ) shouldBe 0
    }

    @Test
    fun `display data exposes grouped rows and target row lookup`() {
        val targetGroupKey = resolveNovelChapterGroupKey(1.0)
        val displayData = resolveNovelChapterDisplayData(
            chapters = listOf(
                chapter(id = 1L, chapterNumber = 1.0, sourceOrder = 10, scanlator = "Team A"),
                chapter(id = 2L, chapterNumber = 1.0, sourceOrder = 20, scanlator = "Team B"),
                chapter(id = 3L, chapterNumber = 2.0, sourceOrder = 30, scanlator = "Team C"),
            ),
            groupedByChapter = true,
            expandedGroupKeys = setOf(targetGroupKey),
        )

        displayData.chapterGroups.map { it.groupKey } shouldBe listOf(
            targetGroupKey,
            resolveNovelChapterGroupKey(2.0),
        )
        displayData.displayRows.filterIsInstance<NovelChapterDisplayRow.ChapterVariant>().map { it.chapter.id } shouldBe
            listOf(1L, 2L)
        resolveNovelChapterRowIndex(
            rows = displayData.displayRows,
            targetChapterId = 2L,
        ) shouldBe 0
    }

    private fun chapter(
        id: Long,
        chapterNumber: Double,
        sourceOrder: Long,
        scanlator: String? = null,
    ): NovelChapter {
        return NovelChapter.create().copy(
            id = id,
            novelId = 1L,
            chapterNumber = chapterNumber,
            sourceOrder = sourceOrder,
            scanlator = scanlator,
            url = "/chapter-$id",
            name = "Chapter $id",
        )
    }
}
