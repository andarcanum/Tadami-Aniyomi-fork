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

    @Test
    fun `pending tts chapter restore expires after its ttl`() {
        // A handoff whose chapter switch never completed (reader closed mid-handoff, load failed)
        // must not wait forever for a later session that happens to open the same chapter and
        // spontaneously start TTS there.
        val requestedAt = 1_000_000L
        NovelReaderTtsChapterHandoffPolicy.markPendingRestore(42L, requestedAtMs = requestedAt)

        NovelReaderTtsChapterHandoffPolicy.hasPendingRestore(42L, nowMs = requestedAt + 1_000L)
            .shouldBeTrue()
        NovelReaderTtsChapterHandoffPolicy.consumePendingRestore(42L, nowMs = requestedAt + 301_000L)
            .shouldBeFalse()
        // The expired mark is dropped instead of lingering for the next lookups.
        NovelReaderTtsChapterHandoffPolicy.hasPendingRestore(42L, nowMs = requestedAt + 1_000L)
            .shouldBeFalse()
    }
}
