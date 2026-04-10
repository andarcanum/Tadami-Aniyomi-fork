package eu.kanade.presentation.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.Test

class NovelReaderTtsNavigationPolicyTest {

    @Test
    fun `page mode does not pause when current page still belongs to active utterance`() {
        resolveShouldPauseTtsForManualNavigation(
            isPlaying = true,
            pauseOnManualNavigation = true,
            nowMs = 2_000L,
            suppressUntilMs = 1_000L,
            usePageReader = true,
            currentBlockIndex = 9,
            activeSourceBlockIndex = 3,
            currentPageIndex = 5,
            activePageCandidates = setOf(4, 5, 6),
        ) shouldBe false
    }

    @Test
    fun `page mode pauses when current page is outside active utterance pages`() {
        resolveShouldPauseTtsForManualNavigation(
            isPlaying = true,
            pauseOnManualNavigation = true,
            nowMs = 2_000L,
            suppressUntilMs = 1_000L,
            usePageReader = true,
            currentBlockIndex = 3,
            activeSourceBlockIndex = 3,
            currentPageIndex = 8,
            activePageCandidates = setOf(4, 5, 6),
        ) shouldBe true
    }

    @Test
    fun `manual navigation pause remains block based outside page mode`() {
        resolveShouldPauseTtsForManualNavigation(
            isPlaying = true,
            pauseOnManualNavigation = true,
            nowMs = 2_000L,
            suppressUntilMs = 1_000L,
            usePageReader = false,
            currentBlockIndex = 8,
            activeSourceBlockIndex = 3,
            currentPageIndex = null,
            activePageCandidates = null,
        ) shouldBe true
    }

    @Test
    fun `suppression window disables manual navigation pause`() {
        resolveShouldPauseTtsForManualNavigation(
            isPlaying = true,
            pauseOnManualNavigation = true,
            nowMs = 500L,
            suppressUntilMs = 1_000L,
            usePageReader = true,
            currentBlockIndex = 8,
            activeSourceBlockIndex = 3,
            currentPageIndex = 9,
            activePageCandidates = setOf(4, 5, 6),
        ) shouldBe false
    }
}
