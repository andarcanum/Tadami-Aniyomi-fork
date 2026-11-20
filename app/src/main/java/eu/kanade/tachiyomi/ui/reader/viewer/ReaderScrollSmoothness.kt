package eu.kanade.tachiyomi.ui.reader.viewer

import android.os.Build
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

/**
 * Pure descriptor for an Android [android.view.Display.Mode] used to select the highest refresh
 * rate mode matching the active physical display resolution.
 */
internal data class DisplayModeCandidate(
    val modeId: Int,
    val physicalWidth: Int,
    val physicalHeight: Int,
    val refreshRate: Float,
)

/**
 * Selects the fastest display mode matching [current]'s physical resolution.
 *
 * Unlike checking `fastest.modeId != current.modeId`, this always returns the fastest candidate
 * (even when an LTPO panel is already in its 120 Hz VRR mode) so the Activity can explicitly set
 * `preferredDisplayModeId` and `preferredRefreshRate` on the Window — which OEM refresh-rate
 * policies (e.g. OriginOS / FuntouchOS / MIUI) require to keep the reader out of 60/90 Hz caps.
 */
internal fun selectFastestDisplayMode(
    current: DisplayModeCandidate,
    supported: List<DisplayModeCandidate>,
): DisplayModeCandidate? {
    return supported
        .asSequence()
        .filter {
            it.physicalWidth == current.physicalWidth &&
                it.physicalHeight == current.physicalHeight &&
                it.refreshRate.isFinite() &&
                it.refreshRate > 0f
        }
        .maxByOrNull { it.refreshRate }
}

/**
 * Holds [View.REQUESTED_FRAME_RATE_CATEGORY_HIGH] on API 35+ while the reader is scrolling,
 * flinging, zooming, or auto-scrolling, with a short post-idle grace window before returning to
 * [View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT].
 *
 * On Android 15/16 LTPO displays, RecyclerView 1.4+ reports low `setFrameContentVelocity` during
 * slow manual reading and during the deceleration tail of a fling, causing ViewRootImpl ARR to
 * step the panel down (120 -> 90 -> 60 -> 48 -> 30 Hz) mid-gesture, which reads as visible
 * stair-stepping. Voting HIGH during active motion keeps frame pacing locked to the panel's top
 * refresh rate all the way through the stop, while releasing back to DEFAULT on idle lets LTPO
 * drop to low power when reading a stationary page.
 */
internal class ViewMotionFrameRateBooster(
    private val view: View,
    private val settleGraceMs: Long = DEFAULT_SETTLE_GRACE_MS,
) {
    private var highVoteActive = false
    private val releaseRunnable = Runnable {
        applyFrameRateCategory(high = false)
    }

    fun onMotionStart() {
        view.removeCallbacks(releaseRunnable)
        applyFrameRateCategory(high = true)
    }

    fun onMotionIdle() {
        if (!highVoteActive) return
        view.removeCallbacks(releaseRunnable)
        if (settleGraceMs <= 0L) {
            applyFrameRateCategory(high = false)
        } else {
            view.postDelayed(releaseRunnable, settleGraceMs)
        }
    }

    fun releaseImmediately() {
        view.removeCallbacks(releaseRunnable)
        applyFrameRateCategory(high = false)
    }

    private fun applyFrameRateCategory(high: Boolean) {
        if (highVoteActive == high) return
        highVoteActive = high
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            view.requestedFrameRate = if (high) {
                View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
            } else {
                View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
            }
        }
    }

    companion object {
        const val DEFAULT_SETTLE_GRACE_MS = 300L
    }
}

/**
 * Coalesces per-frame webtoon viewport pushes and smooths lookahead speed estimates.
 *
 * Pushing `setExternalViewport` to every attached `WebtoonSubsamplingImageView` on every 1px
 * scroll frame (120x/sec) repeatedly runs `refreshRequiredTiles` across all attached strips and
 * oscillates the `prefetchRect` border when per-frame `dy` fluctuates. Coalescing pushes until
 * cumulative displacement crosses a modest threshold (~8% of viewport, min 48dp, max 140dp) or
 * scroll direction / scale changes cuts main-thread tile-bookkeeping churn by ~90-95% while
 * preserving the full 0.5x-1.0x viewport prefetch cushion ahead of visible tiles.
 */
internal class WebtoonViewportPushCoalescer {
    private var accumulatedDy = 0f
    private var lastDirection = 0
    private var smoothedSpeedPxPerFrame = 0f
    private var lastScale = 1f
    private var hasBaseline = false

    val currentSmoothedSpeedPxPerFrame: Float
        get() = smoothedSpeedPxPerFrame

    fun computePushThresholdPx(viewportHeightPx: Float, density: Float): Float {
        val safeDensity = if (density.isFinite() && density > 0f) density else 1f
        val minPx = MIN_PUSH_THRESHOLD_DP * safeDensity
        val maxPx = MAX_PUSH_THRESHOLD_DP * safeDensity
        val fractionalPx = if (viewportHeightPx.isFinite() && viewportHeightPx > 0f) {
            viewportHeightPx * VIEWPORT_PUSH_FRACTION
        } else {
            minPx
        }
        return max(minPx, fractionalPx).coerceAtMost(maxPx)
    }

    /**
     * Records a single scroll delta and returns `true` when child viewports should be pushed now.
     */
    fun onScrollDelta(
        dy: Int,
        viewportHeightPx: Float,
        density: Float,
        scale: Float,
    ): Boolean {
        val absDy = abs(dy).toFloat()
        smoothedSpeedPxPerFrame = if (smoothedSpeedPxPerFrame == 0f) {
            absDy
        } else {
            smoothedSpeedPxPerFrame * SPEED_EMA_KEEP + absDy * SPEED_EMA_NEW
        }

        if (!hasBaseline || scale != lastScale) {
            markForcedPush(scale = scale, direction = dy.sign)
            return true
        }

        val dir = dy.sign
        if (dir != 0 && lastDirection != 0 && dir != lastDirection) {
            markForcedPush(scale = scale, direction = dir)
            return true
        }

        accumulatedDy += dy
        val threshold = computePushThresholdPx(viewportHeightPx, density)
        if (abs(accumulatedDy) >= threshold) {
            accumulatedDy = 0f
            if (dir != 0) lastDirection = dir
            lastScale = scale
            return true
        }
        if (dir != 0 && lastDirection == 0) {
            lastDirection = dir
        }
        return false
    }

    fun markForcedPush(scale: Float, direction: Int = lastDirection) {
        accumulatedDy = 0f
        lastScale = scale
        if (direction != 0) {
            lastDirection = direction
        }
        hasBaseline = true
    }

    fun resetVelocity() {
        smoothedSpeedPxPerFrame = 0f
        lastDirection = 0
        accumulatedDy = 0f
    }

    fun computeSmoothedLookaheadFactor(
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
        return raw.coerceIn(minFactor, maxFactor)
    }

    companion object {
        private const val MIN_PUSH_THRESHOLD_DP = 48f
        private const val MAX_PUSH_THRESHOLD_DP = 140f
        private const val VIEWPORT_PUSH_FRACTION = 0.08f
        private const val SPEED_EMA_KEEP = 0.8f
        private const val SPEED_EMA_NEW = 0.2f
    }
}
