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
    fun `internal chapter handoff exposes page reader target once`() {
        NovelReaderChapterHandoffPolicy.markInternalChapterHandoff(NovelReaderPageReaderHandoffTarget.END)

        shouldRestoreSavedPageReaderProgress(NovelReaderPageReaderHandoffTarget.END) shouldBe false
        NovelReaderChapterHandoffPolicy.consumeInternalChapterHandoff() shouldBe NovelReaderPageReaderHandoffTarget.END
        NovelReaderChapterHandoffPolicy.consumeInternalChapterHandoff() shouldBe NovelReaderPageReaderHandoffTarget.SAVED
        shouldRestoreSavedPageReaderProgress(NovelReaderPageReaderHandoffTarget.SAVED) shouldBe true
    }
}
