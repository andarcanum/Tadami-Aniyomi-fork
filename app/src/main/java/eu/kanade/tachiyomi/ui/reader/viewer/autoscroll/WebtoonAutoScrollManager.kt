package eu.kanade.tachiyomi.ui.reader.viewer.autoscroll

import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.animation.LinearInterpolator
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonRecyclerView
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.system.logcat

/**
 * Duration of the speed ramp (in milliseconds) used to smoothly decelerate into
 * and accelerate out of a touch cooldown.
 */
private const val SPEED_RAMP_DURATION_MS = 300f

/**
 * Maximum frame delta used for scroll calculations. Prevents huge jumps after
 * dropped frames or when the app returns from the background.
 */
private const val MAX_FRAME_DELTA_MS = 64L

/**
 * Auto-scroll manager for [WebtoonViewer] that scrolls the webtoon content at a
 * continuous, frame rate independent speed.
 *
 * Scrolling is computed from real frame delta times (px/second), so the perceived
 * speed is identical on 60Hz, 90Hz and 120Hz+ displays. Fractional pixels are
 * accumulated between frames to avoid the "stair-step" effect caused by
 * truncating the scroll amount on every frame.
 *
 * @property viewer The [WebtoonViewer] instance to control.
 */
class WebtoonAutoScrollManager(
    private val viewer: WebtoonViewer,
) : AutoScrollManager {

    private val _state = MutableStateFlow(AutoScrollState())
    override val state: StateFlow<AutoScrollState> = _state.asStateFlow()

    private var valueAnimator: ValueAnimator? = null
    private var cooldownUntilMs: Long = 0L
    private var currentSpeedFactor: Float = 1f

    /**
     * Timestamp of the previous animation frame, used to compute frame deltas.
     */
    private var lastFrameTimeMs: Long = 0L

    /**
     * Accumulates fractional pixels that cannot be scrolled within a single frame.
     * Carrying the remainder over keeps slow speeds perfectly smooth instead of
     * alternating between e.g. 1px and 2px steps.
     */
    private var scrollRemainder: Float = 0f

    /**
     * The recycler view from the webtoon viewer that will be scrolled.
     */
    private val recyclerView: WebtoonRecyclerView
        get() = viewer.recycler

    /**
     * Calculates the scroll speed in pixels per second based on the speed setting.
     * Higher speed values result in faster scrolling.
     *
     * @param speed The speed value (1-100).
     * @return The scroll speed in pixels per second.
     */
    private fun calculateScrollSpeedPxPerSecond(speed: Int): Float {
        // Speed 1 = 90 px/s, Speed 100 = 600 px/s
        // (Matches the previous frame-based values calibrated at 60fps.)
        val clampedSpeed = speed.coerceIn(1, 100)
        return 90f + (clampedSpeed - 1) * (510f / 99f)
    }

    override fun setCooldown(delayMs: Long) {
        cooldownUntilMs = SystemClock.elapsedRealtime() + delayMs
        _state.update { it.copy(cooldownUntilMs = cooldownUntilMs) }
    }

    override fun start(speed: Int?) {
        if (speed != null) {
            _state.update { it.copy(speed = speed.coerceIn(1, 100)) }
        }

        if (_state.value.isActive) {
            // Already active, stop current animator to restart with new speed
            stopAnimator()
        }

        cooldownUntilMs = 0L
        currentSpeedFactor = 1f
        _state.update { it.copy(isActive = true, isPaused = false, cooldownUntilMs = 0L) }
        startAnimator()
        logcat { "WebtoonAutoScrollManager started with speed ${_state.value.speed}" }
    }

    override fun stop() {
        stopAnimator()
        cooldownUntilMs = 0L
        currentSpeedFactor = 1f
        _state.update { it.copy(isActive = false, isPaused = false, cooldownUntilMs = 0L) }
        logcat { "WebtoonAutoScrollManager stopped" }
    }

    override fun pause() {
        if (!_state.value.isActive || _state.value.isPaused) return

        stopAnimator()
        cooldownUntilMs = 0L
        _state.update { it.copy(isPaused = true, cooldownUntilMs = 0L) }
        logcat { "WebtoonAutoScrollManager paused" }
    }

    override fun resume() {
        if (!_state.value.isActive || !_state.value.isPaused) return

        _state.update { it.copy(isPaused = false) }
        startAnimator()
        logcat { "WebtoonAutoScrollManager resumed" }
    }

    override fun togglePause() {
        when {
            !_state.value.isActive -> start()
            _state.value.isPaused -> resume()
            else -> pause()
        }
    }

    override fun setSpeed(speed: Int) {
        val clampedSpeed = speed.coerceIn(1, 100)
        _state.update { it.copy(speed = clampedSpeed) }

        // Restart animator if currently running to apply new speed
        if (isRunning) {
            stopAnimator()
            startAnimator()
        }
        logcat { "WebtoonAutoScrollManager speed set to $clampedSpeed" }
    }

    override fun destroy() {
        stop()
    }

    /**
     * Starts the value animator with the current speed setting.
     * The animator is only used as a per-frame callback source; the actual scroll
     * amount is derived from real elapsed time between frames, which makes the
     * scroll speed independent of the display refresh rate.
     */
    private fun startAnimator() {
        val pxPerSecond = calculateScrollSpeedPxPerSecond(_state.value.speed)

        lastFrameTimeMs = 0L
        scrollRemainder = 0f

        valueAnimator = ValueAnimator.ofInt(0, Int.MAX_VALUE).apply {
            duration = Long.MAX_VALUE
            interpolator = LinearInterpolator()

            addUpdateListener {
                val now = SystemClock.elapsedRealtime()
                val deltaMs = if (lastFrameTimeMs == 0L) {
                    0L
                } else {
                    (now - lastFrameTimeMs).coerceAtMost(MAX_FRAME_DELTA_MS)
                }
                lastFrameTimeMs = now
                if (deltaMs <= 0L) return@addUpdateListener

                val inCooldown = now < cooldownUntilMs

                // Smooth, time-based deceleration into / acceleration out of cooldown
                val rampDelta = deltaMs / SPEED_RAMP_DURATION_MS
                currentSpeedFactor = when {
                    inCooldown -> (currentSpeedFactor - rampDelta).coerceAtLeast(0f)
                    currentSpeedFactor < 1f -> (currentSpeedFactor + rampDelta).coerceAtMost(1f)
                    else -> 1f
                }

                if (currentSpeedFactor <= 0f) {
                    scrollRemainder = 0f
                    return@addUpdateListener
                }

                // Frame rate independent scroll amount with sub-pixel accumulation
                val exactDelta = pxPerSecond * currentSpeedFactor * (deltaMs / 1000f) + scrollRemainder
                val deltaY = exactDelta.toInt()
                scrollRemainder = exactDelta - deltaY

                if (deltaY > 0) {
                    recyclerView.scrollBy(0, deltaY)
                }

                // Check if we've reached the end
                if (!recyclerView.canScrollVertically(1)) {
                    // Reached the end, stop auto-scroll
                    stop()
                }
            }

            start()
        }
    }

    /**
     * Stops and cleans up the current value animator.
     */
    private fun stopAnimator() {
        valueAnimator?.cancel()
        valueAnimator = null
    }
}
