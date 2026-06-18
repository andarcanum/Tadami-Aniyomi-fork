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

    @Test
    fun `volume display data groups lnori book anchors by volume`() {
        val displayData = resolveNovelVolumeChapterDisplayData(
            chapters = listOf(
                chapter(
                    id = 1L,
                    chapterNumber = 1.0,
                    sourceOrder = 10,
                    name = "Re Zero Starting Life In Another World Vol 1 - Cover",
                    url = "book/12040/re-zero-starting-life-in-another-world-vol-1#page01",
                ),
                chapter(
                    id = 2L,
                    chapterNumber = 2.0,
                    sourceOrder = 20,
                    name = "Re Zero Starting Life In Another World Vol 1 - Chapter 1: The End of the Beginning",
                    url = "book/12040/re-zero-starting-life-in-another-world-vol-1#page11",
                ),
                chapter(
                    id = 3L,
                    chapterNumber = 3.0,
                    sourceOrder = 30,
                    name = "Volume 2 - Cover",
                    url = "book/12156/re-zero-starting-life-in-another-world-vol-2#page01",
                ),
                chapter(
                    id = 4L,
                    chapterNumber = 4.0,
                    sourceOrder = 40,
                    name = "Volume 2 - Prologue: The Road to Redemption Begins",
                    url = "book/12156/re-zero-starting-life-in-another-world-vol-2#page08",
                ),
            ),
            expandedVolumeKeys = emptySet(),
        )

        displayData.volumeGroups.map { it.title } shouldBe listOf("Volume 1", "Volume 2")
        displayData.displayRows.filterIsInstance<NovelChapterDisplayRow.VolumeGroup>().map { it.chapters.size } shouldBe
            listOf(2, 2)
    }

    @Test
    fun `expanded volume display rows strip volume prefix from child chapter titles`() {
        val chapters = listOf(
            chapter(
                id = 1L,
                chapterNumber = 1.0,
                sourceOrder = 10,
                name = "Re Zero Starting Life In Another World Vol 1 - Cover",
                url = "book/12040/re-zero-starting-life-in-another-world-vol-1#page01",
            ),
            chapter(
                id = 2L,
                chapterNumber = 2.0,
                sourceOrder = 20,
                name = "Re Zero Starting Life In Another World Vol 1 - Chapter 1: The End of the Beginning",
                url = "book/12040/re-zero-starting-life-in-another-world-vol-1#page11",
            ),
        )
        val collapsed = resolveNovelVolumeChapterDisplayData(chapters, expandedVolumeKeys = emptySet())
        val group = collapsed.volumeGroups.single()

        val expanded = resolveNovelVolumeChapterDisplayData(chapters, expandedVolumeKeys = setOf(group.groupKey))

        expanded.displayRows.filterIsInstance<NovelChapterDisplayRow.VolumeChapter>().map { it.title } shouldBe listOf(
            "Cover",
            "Chapter 1: The End of the Beginning",
        )
        resolveNovelChapterRowIndex(expanded.displayRows, targetChapterId = 2L) shouldBe 2
    }

    @Test
    fun `volume grouping stays disabled for plain flat chapter lists`() {
        shouldGroupNovelChaptersByVolume(
            listOf(
                chapter(id = 1L, chapterNumber = 1.0, sourceOrder = 10, name = "Chapter 1", url = "/chapter-1"),
                chapter(id = 2L, chapterNumber = 2.0, sourceOrder = 20, name = "Chapter 2", url = "/chapter-2"),
            ),
        ) shouldBe false
    }

    private fun chapter(
        id: Long,
        chapterNumber: Double,
        sourceOrder: Long,
        scanlator: String? = null,
        name: String = "Chapter $id",
        url: String = "/chapter-$id",
    ): NovelChapter {
        return NovelChapter.create().copy(
            id = id,
            novelId = 1L,
            chapterNumber = chapterNumber,
            sourceOrder = sourceOrder,
            scanlator = scanlator,
            url = url,
            name = name,
        )
    }
}
