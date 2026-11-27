package eu.kanade.tachiyomi.ui.reels.player

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pro-player zoom presets & math for Reels playback.
 *
 * Manual pinch-to-zoom persists at the user's chosen scale (up to [MAX_ZOOM_SCALE]) instead of
 * snapping back to 1x, while Fit/Fill presets reset manual zoom cleanly and toggle aspect mode.
 */
internal object ReelsZoom {

    const val MIN_ZOOM_SCALE = 1f
    const val MAX_ZOOM_SCALE = 4f
    const val SNAP_TO_BASE_THRESHOLD = 1.05f

    /** Quick zoom multiplier presets displayed in the pro-player zoom HUD. */
    val SCALE_PRESETS: List<Float> = listOf(1.5f, 2f, 3f)

    /** Normalizes a raw pinch scale: clamps to [MIN_ZOOM_SCALE]..[MAX_ZOOM_SCALE] and snaps near 1x. */
    fun normalizeScale(rawScale: Float, snapToBase: Boolean = false): Float {
        if (!rawScale.isFinite()) return MIN_ZOOM_SCALE
        val clamped = rawScale.coerceIn(MIN_ZOOM_SCALE, MAX_ZOOM_SCALE)
        return if (snapToBase && clamped <= SNAP_TO_BASE_THRESHOLD) MIN_ZOOM_SCALE else clamped
    }

    /** True when the user has an active manual zoom beyond the base Fit/Fill scale. */
    fun isZoomed(scale: Float): Boolean = scale > 1.01f

    /** Clamps pan offset so the scaled frame never drifts off-screen into empty space. */
    fun clampOffset(
        offsetX: Float,
        offsetY: Float,
        scale: Float,
        viewWidth: Float,
        viewHeight: Float,
    ): Pair<Float, Float> {
        if (!isZoomed(scale) || viewWidth <= 0f || viewHeight <= 0f) return 0f to 0f
        val maxX = viewWidth * (scale - 1f) / 2f
        val maxY = viewHeight * (scale - 1f) / 2f
        return offsetX.coerceIn(-maxX, maxX) to offsetY.coerceIn(-maxY, maxY)
    }

    /** Locale-independent compact label: "1.5×", "2×", "1.65×". */
    fun formatScaleLabel(scale: Float): String {
        val hundredths = (scale * 100f).roundToInt()
        val rounded = hundredths / 100f
        return when {
            hundredths % 100 == 0 -> "${hundredths / 100}×"
            hundredths % 10 == 0 -> String.format(Locale.US, "%.1f×", rounded)
            else -> String.format(Locale.US, "%.2f×", rounded)
        }
    }
}
