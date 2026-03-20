package eu.kanade.presentation.entries.novel

import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelScreenAuroraLayoutTest {

    @Test
    fun `two pane aurora layout is enabled only for tablet expanded`() {
        shouldUseNovelAuroraTwoPane(AuroraDeviceClass.Phone) shouldBe false
        shouldUseNovelAuroraTwoPane(AuroraDeviceClass.TabletCompact) shouldBe false
        shouldUseNovelAuroraTwoPane(AuroraDeviceClass.TabletExpanded) shouldBe true
    }

    @Test
    fun `fast scroller uses pane scoped placement only in two pane novel layout`() {
        shouldUseNovelAuroraPaneScopedFastScroller(useTwoPaneLayout = false) shouldBe false
        shouldUseNovelAuroraPaneScopedFastScroller(useTwoPaneLayout = true) shouldBe true
    }

    @Test
    fun `collapsed novel aurora list shows only preview chapters until expanded`() {
        resolveNovelAuroraVisibleChapterCount(
            chaptersExpanded = false,
            totalChapters = 20,
        ) shouldBe 5

        resolveNovelAuroraVisibleChapterCount(
            chaptersExpanded = true,
            totalChapters = 20,
        ) shouldBe 20

        resolveNovelAuroraVisibleChapterCount(
            chaptersExpanded = false,
            totalChapters = 3,
        ) shouldBe 3
    }

    @Test
    fun `novel aurora auto jump waits until chapters list is expanded`() {
        shouldAutoJumpToNovelAuroraTarget(
            hasScrolledToTarget = false,
            chaptersExpanded = false,
            totalChapters = 20,
        ) shouldBe false

        shouldAutoJumpToNovelAuroraTarget(
            hasScrolledToTarget = false,
            chaptersExpanded = true,
            totalChapters = 20,
        ) shouldBe true

        shouldAutoJumpToNovelAuroraTarget(
            hasScrolledToTarget = true,
            chaptersExpanded = true,
            totalChapters = 20,
        ) shouldBe false
    }

    @Test
    fun `novel aurora auto jump is allowed immediately when all chapters already fit in preview`() {
        shouldAutoJumpToNovelAuroraTarget(
            hasScrolledToTarget = false,
            chaptersExpanded = false,
            totalChapters = 3,
        ) shouldBe true
    }

    @Test
    fun `novel aurora target scroll index uses grouped row index instead of raw chapter position`() {
        resolveNovelAuroraTargetScrollIndex(
            chapters = listOf(
                chapter(id = 10L, chapterNumber = 1.0, sourceOrder = 10, scanlator = "Team A"),
                chapter(id = 11L, chapterNumber = 1.0, sourceOrder = 20, scanlator = "Team B"),
                chapter(id = 12L, chapterNumber = 2.0, sourceOrder = 30, scanlator = "Team C"),
            ),
            targetChapterIndex = 2,
            expandedGroupKeys = emptySet(),
            groupedByChapter = true,
            isAutoJumpToNextEnabled = true,
            restoredScrollIndex = 0,
        ) shouldBe 1
    }

    @Test
    fun `novel aurora target scroll index returns null when auto jump is disabled`() {
        resolveNovelAuroraTargetScrollIndex(
            chapters = listOf(
                chapter(id = 10L, chapterNumber = 1.0, sourceOrder = 10),
                chapter(id = 11L, chapterNumber = 3.0, sourceOrder = 20),
            ),
            targetChapterIndex = 1,
            expandedGroupKeys = emptySet(),
            groupedByChapter = false,
            isAutoJumpToNextEnabled = false,
            restoredScrollIndex = 0,
        ) shouldBe null
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
