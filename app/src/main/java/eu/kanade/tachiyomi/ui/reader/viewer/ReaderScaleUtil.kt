package eu.kanade.tachiyomi.ui.reader.viewer

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView

object ReaderScaleUtil {

    /**
     * Resolves the effective scale type for Pager viewer when the configured scale type is
     * [SubsamplingScaleImageView.SCALE_TYPE_SMART_FIT].
     *
     * In legacy Tachiyomi, the library's internal smart-fit only compares image dimensions
     * (sHeight > sWidth ? FIT_WIDTH : FIT_HEIGHT), which completely ignores the screen orientation.
     * When rotated to landscape, a portrait manga page got blown up 2.4x+ (FIT_WIDTH), cutting off
     * 70% of the image vertically.
     *
     * True Adaptive Smart Fit resolves:
     * - Landscape screen (viewWidth > viewHeight):
     *   Scale to fit the screen height completely without vertical cutoff (SCALE_TYPE_CENTER_INSIDE).
     *   Both portrait pages and landscape double-spreads fit comfortably within the landscape viewport.
     * - Portrait screen (viewWidth <= viewHeight):
     *   Portrait page fills width (SCALE_TYPE_FIT_WIDTH).
     *   Landscape double-spread fits within screen bounds (SCALE_TYPE_CENTER_INSIDE).
     */
    fun resolveSmartScaleType(
        viewWidth: Int,
        viewHeight: Int,
        imageWidth: Int,
        imageHeight: Int,
    ): Int {
        if (viewWidth <= 0 || viewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) {
            return SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
        }

        val isScreenLandscape = viewWidth > viewHeight
        val isImageLandscape = imageWidth > imageHeight

        return if (isScreenLandscape) {
            // When user rotates the device to landscape:
            // Single portrait pages must scale to fit the screen height (not blown up across 2400px width),
            // and wide double spreads also fit the screen.
            SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
        } else {
            // When user holds the device in portrait:
            // Single portrait pages fit the screen width for natural reading.
            // Wide double spreads fit inside the screen without clipping.
            if (isImageLandscape) {
                SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
            } else {
                SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH
            }
        }
    }

    /**
     * Calculates the adaptive horizontal side margin for a Webtoon page in landscape orientation
     * when [webtoonSmartFit] is enabled.
     *
     * In landscape orientation, standard webtoon strips would otherwise stretch to the full screen
     * width (e.g. 2400px), magnifying panels 3x and breaking the vertical reading flow.
     * When smart fit is active, vertical webtoon panels are constrained to a comfortable pillarbox
     * reading width (matching the natural portrait screen width), while wide splash panels
     * (imageWidth > imageHeight) can expand to the full landscape width.
     */
    fun computeWebtoonAdaptiveSideMargin(
        viewportWidth: Int,
        viewportHeight: Int,
        imageWidth: Int?,
        imageHeight: Int?,
        basePaddingPercent: Int,
        webtoonSmartFit: Boolean,
    ): Int {
        val baseMargin = (viewportWidth * (basePaddingPercent.coerceIn(0, 100) / 100f)).toInt()
        if (!webtoonSmartFit || viewportWidth <= 0 || viewportHeight <= 0) {
            return baseMargin
        }

        val isLandscape = viewportWidth > viewportHeight
        if (!isLandscape) {
            return baseMargin
        }

        val isWideImage = imageWidth != null && imageHeight != null && imageWidth > imageHeight
        if (isWideImage) {
            // Wide artwork/splash panels use full landscape width
            return baseMargin
        }

        // Standard vertical strip: cap width to the natural portrait width (viewportHeight)
        val maxContentWidth = minOf(viewportWidth, viewportHeight)
        val smartSideMargin = maxOf(0, (viewportWidth - maxContentWidth) / 2)
        return maxOf(baseMargin, smartSideMargin)
    }
}
