package eu.kanade.tachiyomi.ui.reader.novel.tts

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

class NovelTtsHighlightEstimatorTest {

    private val estimator = NovelTtsHighlightEstimator()

    @Test
    fun `estimateWordRange advances through short utterance`() {
        val utterance = testUtterance("Short simple line.")

        estimator.estimateWordRange(
            utterance = utterance,
            elapsedMs = 0L,
            durationMs = 900L,
            mode = NovelTtsHighlightMode.ESTIMATED,
        )?.wordRange?.text shouldBe "Short"

        estimator.estimateWordRange(
            utterance = utterance,
            elapsedMs = 400L,
            durationMs = 900L,
            mode = NovelTtsHighlightMode.ESTIMATED,
        )?.wordRange?.text shouldBe "simple"

        estimator.estimateWordRange(
            utterance = utterance,
            elapsedMs = 899L,
            durationMs = 900L,
            mode = NovelTtsHighlightMode.ESTIMATED,
        )?.wordRange?.text shouldBe "line"
    }

    @Test
    fun `estimateWordRange gives punctuation extra dwell time`() {
        val utterance = testUtterance("Wait, now go.")

        estimator.estimateWordRange(
            utterance = utterance,
            elapsedMs = 280L,
            durationMs = 1000L,
            mode = NovelTtsHighlightMode.ESTIMATED,
        )?.wordRange?.text shouldBe "Wait"
    }

    @Test
    fun `estimateWordRange gives long words more time`() {
        val utterance = testUtterance("Tiny extraordinary end")

        estimator.estimateWordRange(
            utterance = utterance,
            elapsedMs = 500L,
            durationMs = 1000L,
            mode = NovelTtsHighlightMode.ESTIMATED,
        )?.wordRange?.text shouldBe "extraordinary"
    }

    @Test
    fun `estimateWordRange clamps to final word when elapsed exceeds duration`() {
        val utterance = testUtterance("One more step")

        estimator.estimateWordRange(
            utterance = utterance,
            elapsedMs = 3000L,
            durationMs = 1000L,
            mode = NovelTtsHighlightMode.ESTIMATED,
        )?.wordRange?.text shouldBe "step"
    }

    @Test
    fun `estimateWordRange returns null when highlight mode is off`() {
        estimator.estimateWordRange(
            utterance = testUtterance("No highlight please"),
            elapsedMs = 500L,
            durationMs = 1000L,
            mode = NovelTtsHighlightMode.OFF,
        ).shouldBeNull()
    }

    private fun testUtterance(text: String): NovelTtsUtterance {
        return NovelTtsUtterance(
            id = "utterance",
            segmentId = "segment",
            text = text,
            sourceBlockIndex = 0,
            wordRanges = NovelTtsWordTokenizer.tokenize(text),
        )
    }
}
