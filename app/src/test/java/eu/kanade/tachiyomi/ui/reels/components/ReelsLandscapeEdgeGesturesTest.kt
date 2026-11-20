package eu.kanade.tachiyomi.ui.reels.components

import io.kotest.matchers.floats.shouldBeWithinPercentageOf
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReelsLandscapeEdgeGesturesTest {

    @Test
    fun `resolveLandscapeEdgeZone maps left edge to brightness, right edge to volume, center to null`() {
        val width = 1000f
        val height = 500f

        resolveLandscapeEdgeZone(
            xPx = 120f,
            yPx = 250f,
            widthPx = width,
            heightPx = height,
            topExclusionPx = 48f,
            bottomExclusionPx = 72f,
            brightnessEnabled = true,
            volumeEnabled = true,
        ) shouldBe ReelsLandscapeEdgeZone.Brightness

        resolveLandscapeEdgeZone(
            xPx = 880f,
            yPx = 250f,
            widthPx = width,
            heightPx = height,
            topExclusionPx = 48f,
            bottomExclusionPx = 72f,
            brightnessEnabled = true,
            volumeEnabled = true,
        ) shouldBe ReelsLandscapeEdgeZone.Volume

        resolveLandscapeEdgeZone(
            xPx = 500f,
            yPx = 250f,
            widthPx = width,
            heightPx = height,
            topExclusionPx = 48f,
            bottomExclusionPx = 72f,
            brightnessEnabled = true,
            volumeEnabled = true,
        ) shouldBe null
    }

    @Test
    fun `resolveLandscapeEdgeZone excludes top bar and bottom scrubber bands`() {
        val width = 1000f
        val height = 500f

        resolveLandscapeEdgeZone(
            xPx = 80f,
            yPx = 30f,
            widthPx = width,
            heightPx = height,
            topExclusionPx = 48f,
            bottomExclusionPx = 72f,
            brightnessEnabled = true,
            volumeEnabled = true,
        ) shouldBe null

        resolveLandscapeEdgeZone(
            xPx = 920f,
            yPx = 450f,
            widthPx = width,
            heightPx = height,
            topExclusionPx = 48f,
            bottomExclusionPx = 72f,
            brightnessEnabled = true,
            volumeEnabled = true,
        ) shouldBe null
    }

    @Test
    fun `resolveLandscapeEdgeZone respects per-gesture settings toggles`() {
        val width = 1000f
        val height = 500f

        resolveLandscapeEdgeZone(
            xPx = 100f,
            yPx = 250f,
            widthPx = width,
            heightPx = height,
            topExclusionPx = 48f,
            bottomExclusionPx = 72f,
            brightnessEnabled = false,
            volumeEnabled = true,
        ) shouldBe null

        resolveLandscapeEdgeZone(
            xPx = 900f,
            yPx = 250f,
            widthPx = width,
            heightPx = height,
            topExclusionPx = 48f,
            bottomExclusionPx = 72f,
            brightnessEnabled = true,
            volumeEnabled = false,
        ) shouldBe null
    }

    @Test
    fun `computeUpdatedEdgeGestureLevel moves up on negative dy, down on positive dy, and clamps`() {
        val up = computeUpdatedEdgeGestureLevel(
            startLevel = 0.5f,
            totalDyPx = -100f,
            gestureSpanPx = 400f,
        )
        up.shouldBeWithinPercentageOf(0.75f, 0.1)

        val down = computeUpdatedEdgeGestureLevel(
            startLevel = 0.5f,
            totalDyPx = 100f,
            gestureSpanPx = 400f,
        )
        down.shouldBeWithinPercentageOf(0.25f, 0.1)

        computeUpdatedEdgeGestureLevel(
            startLevel = 0.9f,
            totalDyPx = -500f,
            gestureSpanPx = 300f,
            minLevel = LANDSCAPE_MIN_BRIGHTNESS,
            maxLevel = 1f,
        ) shouldBe 1f

        computeUpdatedEdgeGestureLevel(
            startLevel = 0.1f,
            totalDyPx = 500f,
            gestureSpanPx = 300f,
            minLevel = LANDSCAPE_MIN_BRIGHTNESS,
            maxLevel = 1f,
        ) shouldBe LANDSCAPE_MIN_BRIGHTNESS
    }

    @Test
    fun `mapNormalizedVolumeToStreamIndex maps endpoints and rounds cleanly`() {
        mapNormalizedVolumeToStreamIndex(0f, 15) shouldBe 0
        mapNormalizedVolumeToStreamIndex(1f, 15) shouldBe 15
        mapNormalizedVolumeToStreamIndex(0.5f, 15) shouldBe 8
        mapNormalizedVolumeToStreamIndex(1.5f, 15) shouldBe 15
        mapNormalizedVolumeToStreamIndex(-0.2f, 15) shouldBe 0
        mapNormalizedVolumeToStreamIndex(0.5f, 0) shouldBe 0
    }

    @Test
    fun `formatGesturePercent clamps and rounds cleanly`() {
        formatGesturePercent(0f) shouldBe "0%"
        formatGesturePercent(0.646f) shouldBe "65%"
        formatGesturePercent(1f) shouldBe "100%"
        formatGesturePercent(1.4f) shouldBe "100%"
        formatGesturePercent(-0.1f) shouldBe "0%"
    }
}
