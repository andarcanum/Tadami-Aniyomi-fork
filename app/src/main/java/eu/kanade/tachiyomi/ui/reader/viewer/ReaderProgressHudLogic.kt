package eu.kanade.tachiyomi.ui.reader.viewer

import kotlin.math.roundToInt

/**
 * Pure math helpers for the ambient reading-progress hairline bar and live thumbnail scrubber.
 */
object ReaderProgressHudLogic {

    /**
     * Normalized chapter reading progress in `[0f..1f]`.
     * Uses 1-based [currentPage] out of [totalPages]; returns `0f` if empty and `1f` on the final
     * page of a 1-page chapter.
     */
    fun computeProgressFraction(currentPage: Int, totalPages: Int): Float {
        if (totalPages <= 0 || currentPage <= 0) return 0f
        if (totalPages == 1) return 1f
        return (currentPage.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Rounded integer percentage `[0..100]` for scrubber and HUD badges.
     */
    fun computePercent(currentPage: Int, totalPages: Int): Int {
        return (computeProgressFraction(currentPage, totalPages) * 100f).roundToInt().coerceIn(0, 100)
    }

    /**
     * Visual horizontal offset fraction `[0f..1f]` of the slider thumb along the scrubber track,
     * mirrored automatically when reading right-to-left (Japanese manga).
     */
    fun computeScrubPreviewOffsetFraction(
        currentPage: Int,
        totalPages: Int,
        isRtl: Boolean,
    ): Float {
        if (totalPages <= 1) return if (isRtl) 1f else 0f
        val normalized = ((currentPage - 1).toFloat() / (totalPages - 1).toFloat()).coerceIn(0f, 1f)
        return if (isRtl) 1f - normalized else normalized
    }

    /**
     * Computes power-of-two sample size targeting a compact scrubber thumbnail (~180px wide).
     */
    fun computeScrubSampleSize(sourceWidth: Int, sourceHeight: Int, targetMaxDim: Int = 240): Int {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetMaxDim <= 0) return 1
        val maxDim = maxOf(sourceWidth, sourceHeight)
        var sample = 1
        while ((maxDim / (sample * 2)) >= targetMaxDim) {
            sample *= 2
        }
        return sample
    }
}
