package eu.kanade.presentation.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.Test

class NovelReaderTtsChapterSyncPolicyTest {

    @Test
    fun `returns next chapter id when tts session has auto advanced`() {
        resolveTtsAutoAdvancedChapterNavigationTarget(
            currentChapterId = 10L,
            activeTtsChapterId = 11L,
            nextChapterId = 11L,
        ) shouldBe 11L
    }

    @Test
    fun `does not navigate when tts chapter still matches current chapter`() {
        resolveTtsAutoAdvancedChapterNavigationTarget(
            currentChapterId = 10L,
            activeTtsChapterId = 10L,
            nextChapterId = 11L,
        ) shouldBe null
    }

    @Test
    fun `returns active tts chapter when reader has not resolved next chapter yet`() {
        resolveTtsAutoAdvancedChapterNavigationTarget(
            currentChapterId = 10L,
            activeTtsChapterId = 11L,
            nextChapterId = null,
        ) shouldBe 11L
    }

    @Test
    fun `does not navigate when active tts chapter is unrelated to resolved next chapter`() {
        resolveTtsAutoAdvancedChapterNavigationTarget(
            currentChapterId = 10L,
            activeTtsChapterId = 99L,
            nextChapterId = 11L,
        ) shouldBe null
    }

    @Test
    fun `pending handoff naming the current chapter is not a navigation target`() {
        // After a seamless in-place switch the pending handoff still names the chapter that is
        // already on screen; navigating "to" it replaces the whole reader onto the same chapter
        // (full reload, engine restart, re-speak).
        resolveTtsChapterNavigationTarget(
            pendingChapterHandoffId = 5L,
            currentChapterId = 5L,
            activeTtsChapterId = 5L,
            nextChapterId = 6L,
        ) shouldBe null
    }

    @Test
    fun `pending handoff for the next chapter still navigates`() {
        resolveTtsChapterNavigationTarget(
            pendingChapterHandoffId = 6L,
            currentChapterId = 5L,
            activeTtsChapterId = 6L,
            nextChapterId = 6L,
        ) shouldBe 6L
    }

    @Test
    fun `session derived target is used when no handoff is pending`() {
        resolveTtsChapterNavigationTarget(
            pendingChapterHandoffId = null,
            currentChapterId = 10L,
            activeTtsChapterId = 11L,
            nextChapterId = 11L,
        ) shouldBe 11L
    }
}
