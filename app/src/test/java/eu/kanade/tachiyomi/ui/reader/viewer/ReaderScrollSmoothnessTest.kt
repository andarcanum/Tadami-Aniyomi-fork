package eu.kanade.tachiyomi.ui.reader.viewer

import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReaderScrollSmoothnessTest {

    @Test
    fun `selectFastestDisplayMode returns fastest matching mode even when already active on LTPO`() {
        val current120 = DisplayModeCandidate(modeId = 1, physicalWidth = 1260, physicalHeight = 2800, refreshRate = 120f)
        val mode60 = DisplayModeCandidate(modeId = 2, physicalWidth = 1260, physicalHeight = 2800, refreshRate = 60f)
        val otherRes144 = DisplayModeCandidate(modeId = 3, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 144f)

        val picked = selectFastestDisplayMode(
            current = current120,
            supported = listOf(mode60, current120, otherRes144),
        )

        picked shouldBe current120
    }

    @Test
    fun `selectFastestDisplayMode upgrades from capped 60Hz mode to 120Hz mode of same resolution`() {
        val current60 = DisplayModeCandidate(modeId = 2, physicalWidth = 1260, physicalHeight = 2800, refreshRate = 60f)
        val mode90 = DisplayModeCandidate(modeId = 3, physicalWidth = 1260, physicalHeight = 2800, refreshRate = 90f)
        val mode120 = DisplayModeCandidate(modeId = 1, physicalWidth = 1260, physicalHeight = 2800, refreshRate = 120f)

        val picked = selectFastestDisplayMode(
            current = current60,
            supported = listOf(current60, mode90, mode120),
        )

        picked shouldBe mode120
    }

    @Test
    fun `WebtoonViewportPushCoalescer coalesces slow 120Hz scroll frames and flushes at threshold`() {
        val coalescer = WebtoonViewportPushCoalescer()
        coalescer.markForcedPush(scale = 1f)

        val viewportHeight = 2400f
        val density = 3f
        // Threshold = max(48*3=144, 2400*0.08=192) = 192px
        coalescer.computePushThresholdPx(viewportHeight, density) shouldBe 192f

        var flushes = 0
        repeat(100) {
            if (coalescer.onScrollDelta(dy = 10, viewportHeightPx = viewportHeight, density = density, scale = 1f)) {
                flushes++
            }
        }
        // 1000px total / 192px threshold -> 5 flushes (200px, 400px, 600px, 800px, 1000px) instead of 100
        flushes shouldBe 5
    }

    @Test
    fun `WebtoonViewportPushCoalescer flushes immediately on direction reversal and scale change`() {
        val coalescer = WebtoonViewportPushCoalescer()
        coalescer.markForcedPush(scale = 1f)

        coalescer.onScrollDelta(dy = 20, viewportHeightPx = 2400f, density = 3f, scale = 1f) shouldBe false
        // Direction reverses -> immediate flush so prefetch shifts to the new direction
        coalescer.onScrollDelta(dy = -5, viewportHeightPx = 2400f, density = 3f, scale = 1f) shouldBe true
        // Small move in same direction -> coalesced
        coalescer.onScrollDelta(dy = -10, viewportHeightPx = 2400f, density = 3f, scale = 1f) shouldBe false
        // Zoom scale change -> immediate flush
        coalescer.onScrollDelta(dy = 0, viewportHeightPx = 2400f, density = 3f, scale = 1.5f) shouldBe true
    }

    @Test
    fun `WebtoonViewportPushCoalescer smooths lookahead factor without jitter`() {
        val coalescer = WebtoonViewportPushCoalescer()
        coalescer.markForcedPush(scale = 1f)

        coalescer.onScrollDelta(dy = 2, viewportHeightPx = 2400f, density = 3f, scale = 1f)
        val slowLookahead = coalescer.computeSmoothedLookaheadFactor(
            isSettling = false,
            flingVelocityY = 0,
            viewportHeightPx = 2400f,
            minFactor = 0.5f,
            maxFactor = 1.0f,
        )
        slowLookahead shouldBe 0.5f

        val flingLookahead = coalescer.computeSmoothedLookaheadFactor(
            isSettling = true,
            flingVelocityY = 9000,
            viewportHeightPx = 2400f,
            minFactor = 0.5f,
            maxFactor = 1.0f,
        )
        flingLookahead shouldBeGreaterThanOrEqual 0.5f
        flingLookahead shouldBeLessThanOrEqual 1.0f
    }
}
