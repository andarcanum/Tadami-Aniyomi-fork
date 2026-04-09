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
    fun `does not navigate when active tts chapter is unrelated`() {
        resolveTtsAutoAdvancedChapterNavigationTarget(
            currentChapterId = 10L,
            activeTtsChapterId = 99L,
            nextChapterId = 11L,
        ) shouldBe null
    }
}
