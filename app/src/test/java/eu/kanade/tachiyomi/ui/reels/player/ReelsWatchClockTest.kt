package eu.kanade.tachiyomi.ui.reels.player

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReelsWatchClockTest {

    @Test
    fun `only running intervals accrue`() {
        val clock = ReelsWatchClock()
        clock.start(1_000)
        clock.elapsedMs(1_800) shouldBe 800
        clock.stop(1_800)
        // Ten seconds paused: the dwell/playback total must stay at 800 ms.
        clock.elapsedMs(11_800) shouldBe 800
        clock.start(11_800)
        clock.elapsedMs(14_000) shouldBe 3_000
    }

    @Test
    fun `start and stop are idempotent`() {
        val clock = ReelsWatchClock()
        clock.start(0)
        clock.start(1_000)
        clock.elapsedMs(2_000) shouldBe 2_000
        clock.stop(2_000)
        clock.stop(5_000)
        clock.elapsedMs(5_000) shouldBe 2_000
    }

    @Test
    fun `consumption keeps fractional and partial intervals`() {
        val clock = ReelsWatchClock()
        clock.start(0)
        clock.consumeSeconds(800) shouldBe 0.8f
        // A second report without new time must not repeat the first interval.
        clock.consumeSeconds(800) shouldBe 0f
        clock.stop(800)
        clock.start(3_000)
        clock.consumeSeconds(5_200) shouldBe 2.2f
    }

    @Test
    fun `consumption while running advances the baseline`() {
        val clock = ReelsWatchClock()
        clock.start(1_000)
        clock.consumeSeconds(2_500) shouldBe 1.5f
        // Still running: the live interval continues from the previous consumption.
        clock.consumeSeconds(3_000) shouldBe 0.5f
        clock.stop(3_000)
        clock.consumeSeconds(9_000) shouldBe 0f
    }

    @Test
    fun `reset starts a fresh appearance`() {
        val clock = ReelsWatchClock()
        clock.start(0)
        clock.consumeSeconds(800) shouldBe 0.8f
        clock.reset()
        clock.elapsedMs(800) shouldBe 0
        clock.consumeSeconds(800) shouldBe 0f

        clock.start(10_000)
        clock.consumeSeconds(10_500) shouldBe 0.5f
    }

    @Test
    fun `a backwards clock never produces negative time`() {
        val clock = ReelsWatchClock()
        clock.start(5_000)
        clock.stop(4_000)
        clock.elapsedMs(4_000) shouldBe 0
        clock.consumeSeconds(4_000) shouldBe 0f
    }
}
