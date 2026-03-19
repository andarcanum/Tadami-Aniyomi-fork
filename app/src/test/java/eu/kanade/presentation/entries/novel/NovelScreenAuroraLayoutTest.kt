package eu.kanade.presentation.entries.novel

import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

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
}
