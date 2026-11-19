package eu.kanade.tachiyomi.ui.reader.viewer.autoscroll

import java.util.Locale

/**
 * Pure formatting and step calculation helpers for auto-scroll HUD controls and zoom presets.
 */
object AutoScrollControlLogic {

    const val MIN_SPEED = 1
    const val MAX_SPEED = 100
    const val DEFAULT_STEP = 10

    const val MIN_DOUBLE_TAP_ZOOM_PERCENT = 125
    const val MAX_DOUBLE_TAP_ZOOM_PERCENT = 350

    fun clampSpeed(speed: Int): Int = speed.coerceIn(MIN_SPEED, MAX_SPEED)

    /**
     * Adjusts [current] auto-scroll speed by [delta] while staying strictly inside [1..100].
     */
    fun stepSpeed(current: Int, delta: Int): Int {
        return clampSpeed(clampSpeed(current) + delta)
    }

    /**
     * Computes the page-turn delay in milliseconds for pager auto-scroll mode.
     * Matches the reader's 10s (speed 1) down to 2s (speed 100) curve.
     */
    fun pagerDelayMs(speed: Int): Long {
        val clamped = clampSpeed(speed)
        return (10_000L - (clamped - 1) * 80L).coerceIn(2_000L, 10_000L)
    }

    /**
     * Compact, locale-stable label displayed inside the floating auto-scroll speed pill.
     * Pager mode shows seconds per page (e.g. "6.1s"); webtoon mode shows percentage speed ("50%").
     */
    fun formatCompactBadge(speed: Int, isPagerMode: Boolean): String {
        val clamped = clampSpeed(speed)
        return if (isPagerMode) {
            val seconds = pagerDelayMs(clamped) / 1000.0
            String.format(Locale.US, "%.1fs", seconds)
        } else {
            "$clamped%"
        }
    }

    /**
     * Converts a stored double-tap zoom percentage (e.g. 200 -> 2.0f) to a safe multiplier.
     */
    fun doubleTapZoomMultiplier(percent: Int): Float {
        return percent.coerceIn(MIN_DOUBLE_TAP_ZOOM_PERCENT, MAX_DOUBLE_TAP_ZOOM_PERCENT) / 100f
    }
}
