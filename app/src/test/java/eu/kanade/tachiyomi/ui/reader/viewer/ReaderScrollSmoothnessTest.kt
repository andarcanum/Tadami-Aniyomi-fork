package eu.kanade.tachiyomi.ui.reader.viewer

import android.os.Build
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReaderScrollSmoothnessTest {

    @Test
    fun `selectScrollBoostModeId returns null on Android 15 and above`() {
        val modes = listOf(
            ReaderDisplayModeCandidate(1, 1080, 2400, 60f, floatArrayOf(90f, 120f)),
            ReaderDisplayModeCandidate(2, 1080, 2400, 90f, floatArrayOf(60f, 120f)),
            ReaderDisplayModeCandidate(3, 1080, 2400, 120f, floatArrayOf(60f, 90f)),
        )

        selectScrollBoostModeId(
            sdkInt = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            currentModeId = 1,
            currentWidth = 1080,
            currentHeight = 2400,
            currentRefreshRate = 60f,
            alternativeRefreshRates = floatArrayOf(90f, 120f),
            supportedModes = modes,
        ).shouldBeNull()

        selectScrollBoostModeId(
            sdkInt = Build.VERSION_CODES.VANILLA_ICE_CREAM + 1,
            currentModeId = 1,
            currentWidth = 1080,
            currentHeight = 2400,
            currentRefreshRate = 60f,
            alternativeRefreshRates = floatArrayOf(90f, 120f),
            supportedModes = modes,
        ).shouldBeNull()
    }

    @Test
    fun `selectScrollBoostModeId picks fastest seamless same-resolution mode on Android 12 to 14`() {
        val modes = listOf(
            ReaderDisplayModeCandidate(1, 1080, 2400, 60f, floatArrayOf(90f, 120.05f)),
            ReaderDisplayModeCandidate(2, 1080, 2400, 90f, floatArrayOf(60f, 120.05f)),
            ReaderDisplayModeCandidate(3, 1080, 2400, 120f, floatArrayOf(60f, 90f)),
            // 144 Hz is faster but NOT listed in current mode's seamless alternatives.
            ReaderDisplayModeCandidate(4, 1080, 2400, 144f, floatArrayOf()),
        )

        selectScrollBoostModeId(
            sdkInt = Build.VERSION_CODES.S,
            currentModeId = 1,
            currentWidth = 1080,
            currentHeight = 2400,
            currentRefreshRate = 60f,
            alternativeRefreshRates = floatArrayOf(90f, 120.05f),
            supportedModes = modes,
        ) shouldBe 3

        selectScrollBoostModeId(
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            currentModeId = 1,
            currentWidth = 1080,
            currentHeight = 2400,
            currentRefreshRate = 60f,
            alternativeRefreshRates = floatArrayOf(90f, 120.05f),
            supportedModes = modes,
        ) shouldBe 3
    }

    @Test
    fun `selectScrollBoostModeId returns null when faster modes are not seamless`() {
        val modes = listOf(
            ReaderDisplayModeCandidate(1, 1080, 2400, 60f, floatArrayOf()),
            ReaderDisplayModeCandidate(2, 1080, 2400, 120f, floatArrayOf()),
            ReaderDisplayModeCandidate(3, 1080, 2400, 144f, floatArrayOf()),
        )

        selectScrollBoostModeId(
            sdkInt = Build.VERSION_CODES.TIRAMISU,
            currentModeId = 1,
            currentWidth = 1080,
            currentHeight = 2400,
            currentRefreshRate = 60f,
            alternativeRefreshRates = floatArrayOf(),
            supportedModes = modes,
        ).shouldBeNull()
    }

    @Test
    fun `selectScrollBoostModeId returns null on Android 11 and below`() {
        val modes = listOf(
            ReaderDisplayModeCandidate(1, 1080, 2400, 60f, floatArrayOf(120f)),
            ReaderDisplayModeCandidate(2, 1080, 2400, 120f, floatArrayOf(60f)),
        )

        selectScrollBoostModeId(
            sdkInt = Build.VERSION_CODES.R,
            currentModeId = 1,
            currentWidth = 1080,
            currentHeight = 2400,
            currentRefreshRate = 60f,
            alternativeRefreshRates = floatArrayOf(120f),
            supportedModes = modes,
        ).shouldBeNull()
    }

    @Test
    fun `selectScrollBoostModeId ignores modes with different physical resolution`() {
        val modes = listOf(
            ReaderDisplayModeCandidate(1, 1080, 2400, 60f, floatArrayOf(120f)),
            ReaderDisplayModeCandidate(2, 1440, 3200, 120f, floatArrayOf(60f)),
        )

        selectScrollBoostModeId(
            sdkInt = Build.VERSION_CODES.S,
            currentModeId = 1,
            currentWidth = 1080,
            currentHeight = 2400,
            currentRefreshRate = 60f,
            alternativeRefreshRates = floatArrayOf(120f),
            supportedModes = modes,
        ).shouldBeNull()
    }

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
