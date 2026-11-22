package eu.kanade.tachiyomi.ui.reader.viewer

import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReaderScrollSmoothnessTest {

    @Test
    fun `floorFrameContentVelocityForArr lifts slow fling-tail velocity above high refresh threshold`() {
        val density = 3.5f
        val expectedFloor = 300f * density

        floorFrameContentVelocityForArr(pixelsPerSecond = 25f, density = density) shouldBe expectedFloor
        floorFrameContentVelocityForArr(pixelsPerSecond = 2500f, density = density) shouldBe 2500f
    }

    @Test
    fun `floorFrameContentVelocityForArr passes zero and non-finite velocity through so LTPO idles down`() {
        floorFrameContentVelocityForArr(pixelsPerSecond = 0f, density = 3.5f) shouldBe 0f
        floorFrameContentVelocityForArr(pixelsPerSecond = -10f, density = 3.5f) shouldBe -10f
        floorFrameContentVelocityForArr(pixelsPerSecond = Float.NaN, density = 3.5f).isNaN() shouldBe true
    }

    @Test
    fun `WebtoonLookaheadSmoother smooths and quantizes lookahead factor without jitter`() {
        val smoother = WebtoonLookaheadSmoother()

        smoother.onScrollDelta(dy = 2)
        val slowLookahead = smoother.computeLookaheadFactor(
            isSettling = false,
            flingVelocityY = 0,
            viewportHeightPx = 2400f,
            minFactor = 0.5f,
            maxFactor = 1.0f,
        )
        slowLookahead shouldBe 0.5f

        val flingLookahead = smoother.computeLookaheadFactor(
            isSettling = true,
            flingVelocityY = 9000,
            viewportHeightPx = 2400f,
            minFactor = 0.5f,
            maxFactor = 1.0f,
        )
        flingLookahead shouldBeGreaterThanOrEqual 0.5f
        flingLookahead shouldBeLessThanOrEqual 1.0f

        // Small single-frame spike does not jump the EMA lookahead abruptly
        smoother.reset()
        repeat(10) { smoother.onScrollDelta(dy = 10) }
        val beforeSpike = smoother.computeLookaheadFactor(
            isSettling = false,
            flingVelocityY = 0,
            viewportHeightPx = 2400f,
            minFactor = 0.5f,
            maxFactor = 1.0f,
        )
        smoother.onScrollDelta(dy = 12)
        val afterTinyDelta = smoother.computeLookaheadFactor(
            isSettling = false,
            flingVelocityY = 0,
            viewportHeightPx = 2400f,
            minFactor = 0.5f,
            maxFactor = 1.0f,
        )
        afterTinyDelta shouldBe beforeSpike
    }

    @Test
    fun `WebtoonLookaheadSmoother produces identical lookahead on 60Hz and 120Hz for equal scroll velocity`() {
        val smoother60 = WebtoonLookaheadSmoother()
        val smoother120 = WebtoonLookaheadSmoother()

        repeat(8) { smoother60.onScrollDelta(dy = 120, frameDeltaMs = 16.6667f) }
        repeat(16) { smoother120.onScrollDelta(dy = 60, frameDeltaMs = 8.3333f) }

        val factor60 = smoother60.computeLookaheadFactor(
            isSettling = false,
            flingVelocityY = 0,
            viewportHeightPx = 2400f,
            minFactor = 0.5f,
            maxFactor = 1.0f,
        )
        val factor120 = smoother120.computeLookaheadFactor(
            isSettling = false,
            flingVelocityY = 0,
            viewportHeightPx = 2400f,
            minFactor = 0.5f,
            maxFactor = 1.0f,
        )

        factor120 shouldBe factor60
        factor120 shouldBeGreaterThanOrEqual 0.8f
    }
}
