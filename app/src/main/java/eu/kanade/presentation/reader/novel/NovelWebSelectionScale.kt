package eu.kanade.presentation.reader.novel

/** Convert visual-viewport CSS pixels to the Android view's physical pixels. */
internal fun resolveNovelWebSelectionScale(
    viewWidthPx: Int,
    viewportWidthCss: Double,
    fallbackDensity: Float,
): Double {
    if (viewWidthPx > 0 && viewportWidthCss.isFinite() && viewportWidthCss > 0.0) {
        val scale = viewWidthPx.toDouble() / viewportWidthCss
        if (scale.isFinite() && scale > 0.0) return scale
    }
    return fallbackDensity.toDouble().takeIf { it.isFinite() && it > 0.0 } ?: 1.0
}
