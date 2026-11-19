package eu.kanade.tachiyomi.ui.reader.viewer

import kotlin.math.min

/**
 * Pure geometry helper that filters accidental bezel/palm touches near the left and right edges
 * of modern borderless or curved-edge displays.
 */
object ReaderEdgeGuard {

    /**
     * Maximum fraction of the viewport width that a single side guard may consume.
     * Prevents narrow split-screen or picture-in-picture windows from becoming untappable.
     */
    const val MAX_SIDE_FRACTION = 0.15f

    /**
     * Converts a user-configured [guardDp] into pixels for the given display [density].
     */
    fun computeGuardPx(guardDp: Int, density: Float): Float {
        if (guardDp <= 0 || !density.isFinite() || density <= 0f) return 0f
        return guardDp * density
    }

    /**
     * Returns true if a tap at screen coordinates ([rawX], [rawY]) falls inside the left or right
     * edge guard dead-zone of a viewer whose on-screen bounds start at ([viewLeft], [viewTop])
     * with dimensions ([viewWidth] x [viewHeight]).
     */
    fun isInDeadZone(
        rawX: Float,
        rawY: Float,
        viewLeft: Int,
        viewTop: Int,
        viewWidth: Int,
        viewHeight: Int,
        guardPx: Float,
    ): Boolean {
        if (guardPx <= 0f || viewWidth <= 0 || viewHeight <= 0) return false
        if (!rawX.isFinite() || !rawY.isFinite()) return false

        val localX = rawX - viewLeft
        val localY = rawY - viewTop
        if (localY < 0f || localY > viewHeight) return false

        val effectiveGuard = min(guardPx, viewWidth * MAX_SIDE_FRACTION)
        return localX < effectiveGuard || localX > (viewWidth - effectiveGuard)
    }
}
