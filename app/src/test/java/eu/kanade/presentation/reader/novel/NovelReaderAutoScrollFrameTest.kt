package eu.kanade.presentation.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

private const val FRAME_16_MS_NANOS = 16_000_000L

class NovelReaderAutoScrollFrameTest {

    @Test
    fun `sub pixel frames are carried over instead of being lost`() {
        val first = resolveAutoScrollFrameAdvance(
            speed = 1,
            speedFactor = 1f,
            frameDeltaNanos = FRAME_16_MS_NANOS,
            previousRemainderPx = 0f,
        )
        val second = resolveAutoScrollFrameAdvance(
            speed = 1,
            speedFactor = 1f,
            frameDeltaNanos = FRAME_16_MS_NANOS,
            previousRemainderPx = first.remainderPx,
        )

        first.stepPx shouldBe 0
        first.shouldSkipFrame shouldBe true
        second.stepPx shouldBe 1
        second.shouldSkipFrame shouldBe false
    }

    @Test
    fun `a fully suppressed cooldown produces no movement`() {
        val advance = resolveAutoScrollFrameAdvance(
            speed = 100,
            speedFactor = 0f,
            frameDeltaNanos = FRAME_16_MS_NANOS,
            previousRemainderPx = 0f,
        )

        advance.stepPx shouldBe 0
        advance.remainderPx shouldBe 0f
        advance.shouldSkipFrame shouldBe true
    }

    @Test
    fun `a longer frame scrolls further so speed does not depend on refresh rate`() {
        val singleFrame = resolveAutoScrollFrameAdvance(
            speed = 50,
            speedFactor = 1f,
            frameDeltaNanos = FRAME_16_MS_NANOS,
            previousRemainderPx = 0f,
        )
        val doubleFrame = resolveAutoScrollFrameAdvance(
            speed = 50,
            speedFactor = 1f,
            frameDeltaNanos = FRAME_16_MS_NANOS * 2,
            previousRemainderPx = 0f,
        )

        (doubleFrame.stepPx > singleFrame.stepPx) shouldBe true
    }

    @Test
    fun `an out of range speed factor is clamped instead of inverting the scroll`() {
        val advance = resolveAutoScrollFrameAdvance(
            speed = 100,
            speedFactor = -1f,
            frameDeltaNanos = FRAME_16_MS_NANOS,
            previousRemainderPx = 0f,
        )

        advance.stepPx shouldBe 0
        advance.shouldSkipFrame shouldBe true
    }

    @Test
    fun `auto scroll pixels per second matches exact native 16ms scale rates`() {
        autoScrollPixelsPerSecond(1) shouldBe 31.25f
        val speed37 = autoScrollPixelsPerSecond(37)
        (speed37 > 133.52f && speed37 < 133.53f) shouldBe true
        autoScrollPixelsPerSecond(100) shouldBe 312.5f
    }
}
