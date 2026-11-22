package eu.kanade.tachiyomi.ui.reader.viewer

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

/**
 * Floors positive content scroll velocity for Android 15/16 Adaptive Refresh Rate (ARR) so that
 * slow manual reading and the deceleration tail of a fling do not cause `ViewRootImpl` to step
 * LTPO panels down mid-gesture (e.g. 120 -> 90 -> 60 -> 30 Hz).
 *
 * Non-positive or non-finite velocities pass through unchanged so the display returns to low-power
 * LTPO refresh rates as soon as scrolling actually stops, without forcing display-mode locks or
 * View invalidations.
 */
internal fun floorFrameContentVelocityForArr(
    pixelsPerSecond: Float,
    density: Float,
): Float {
    if (!pixelsPerSecond.isFinite() || pixelsPerSecond <= 0f) return pixelsPerSecond
    val safeDensity = if (density.isFinite() && density > 0f) density else 1f
    val minHighVelocityPxPerSec = MIN_HIGH_REFRESH_VELOCITY_DP_PER_SEC * safeDensity
    return max(pixelsPerSecond, minHighVelocityPxPerSec)
}

/**
 * Smooths webtoon scroll speed with an exponential moving average and quantizes the resulting
 * lookahead factor so `prefetchRect` does not jitter across tile borders on per-frame `dy` noise.
 *
 * Unlike delaying `setExternalViewport`, this keeps `externalVisibleViewport` pixel-accurate on
 * every scroll frame while preventing boundary tiles from oscillating between prefetch and retain.
 */
internal class WebtoonLookaheadSmoother {
    private var smoothedSpeedPxPerFrame = 0f

    fun onScrollDelta(dy: Int, frameDeltaMs: Float = DEFAULT_FRAME_DELTA_MS) {
        val absDy = abs(dy).toFloat()
        if (absDy == 0f) return
        val safeFrameMs = if (frameDeltaMs.isFinite() && frameDeltaMs > 0f) {
            frameDeltaMs.coerceIn(MIN_FRAME_DELTA_MS, MAX_FRAME_DELTA_MS)
        } else {
            DEFAULT_FRAME_DELTA_MS
        }
        // Normalize to 60Hz-equivalent px/frame so 90/120/144Hz displays get the same lookahead
        // for the same physical finger velocity.
        val normalizedDy = absDy * (DEFAULT_FRAME_DELTA_MS / safeFrameMs)
        smoothedSpeedPxPerFrame = if (smoothedSpeedPxPerFrame == 0f) {
            normalizedDy
        } else {
            smoothedSpeedPxPerFrame * SPEED_EMA_KEEP + normalizedDy * SPEED_EMA_NEW
        }
    }

    fun reset() {
        smoothedSpeedPxPerFrame = 0f
    }

    fun computeLookaheadFactor(
        isSettling: Boolean,
        flingVelocityY: Int,
        viewportHeightPx: Float,
        minFactor: Float,
        maxFactor: Float,
    ): Float {
        if (!viewportHeightPx.isFinite() || viewportHeightPx <= 0f) return minFactor
        val raw = if (isSettling && flingVelocityY != 0) {
            abs(flingVelocityY) / (viewportHeightPx * 2.5f)
        } else {
            smoothedSpeedPxPerFrame / (viewportHeightPx * 0.06f).coerceAtLeast(1f)
        }
        val clamped = raw.coerceIn(minFactor, maxFactor)
        return (round(clamped / LOOKAHEAD_QUANTUM) * LOOKAHEAD_QUANTUM).coerceIn(minFactor, maxFactor)
    }

    companion object {
        // Above AOSP Android 15/16 ViewRootImpl's HIGH frame-rate category velocity threshold.
        internal const val MIN_HIGH_REFRESH_VELOCITY_DP_PER_SEC = 300f
        internal const val DEFAULT_FRAME_DELTA_MS = 16.6667f
        private const val MIN_FRAME_DELTA_MS = 4f
        private const val MAX_FRAME_DELTA_MS = 50f
        private const val SPEED_EMA_KEEP = 0.8f
        private const val SPEED_EMA_NEW = 0.2f
        private const val LOOKAHEAD_QUANTUM = 0.05f
    }
}

private const val MIN_HIGH_REFRESH_VELOCITY_DP_PER_SEC =
    WebtoonLookaheadSmoother.MIN_HIGH_REFRESH_VELOCITY_DP_PER_SEC
