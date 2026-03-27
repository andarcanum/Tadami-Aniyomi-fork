package eu.kanade.presentation.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class NovelReaderChapterHandoffPolicyTest {

    @AfterEach
    fun tearDown() {
        NovelReaderChapterHandoffPolicy.clear()
    }

    @Test
    fun `internal chapter handoff suppresses saved page restore once`() {
        NovelReaderChapterHandoffPolicy.markInternalChapterHandoff()

        shouldRestoreSavedPageReaderProgress(isInternalChapterHandoff = true) shouldBe false
        NovelReaderChapterHandoffPolicy.consumeInternalChapterHandoff() shouldBe true
        NovelReaderChapterHandoffPolicy.consumeInternalChapterHandoff() shouldBe false
        shouldRestoreSavedPageReaderProgress(isInternalChapterHandoff = false) shouldBe true
    }
}
