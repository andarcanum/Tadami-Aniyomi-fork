package eu.kanade.presentation.reader.novel

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class NovelReaderTtsChapterHandoffPolicyTest {

    @AfterEach
    fun tearDown() {
        NovelReaderTtsChapterHandoffPolicy.clear()
    }

    @Test
    fun `pending tts chapter restore is consumed only by matching chapter once`() {
        NovelReaderTtsChapterHandoffPolicy.markPendingRestore(42L)

        NovelReaderTtsChapterHandoffPolicy.consumePendingRestore(41L).shouldBeFalse()
        NovelReaderTtsChapterHandoffPolicy.consumePendingRestore(42L).shouldBeTrue()
        NovelReaderTtsChapterHandoffPolicy.consumePendingRestore(42L).shouldBeFalse()
    }
}
