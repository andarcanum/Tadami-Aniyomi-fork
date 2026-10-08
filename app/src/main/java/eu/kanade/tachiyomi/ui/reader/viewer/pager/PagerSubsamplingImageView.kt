package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.content.Context
import android.util.AttributeSet
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderScaleUtil

/**
 * Custom SubsamplingScaleImageView for the Pager viewer that provides adaptive True Smart Fit.
 *
 * When smart fit is enabled, it dynamically recalculates whether the page should fit width
 * or fit screen/height based on the device's current viewport orientation and the image's aspect ratio.
 * When the screen rotates between portrait and landscape, it automatically updates the scale type
 * and resets scale to comfortably fit the new orientation.
 */
class PagerSubsamplingImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SubsamplingScaleImageView(context, attrs) {

    var isSmartFitEnabled = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (isSmartFitEnabled && isReady && (w != oldw || h != oldh) && w > 0 && h > 0) {
            val smartType = ReaderScaleUtil.resolveSmartScaleType(
                viewWidth = w,
                viewHeight = h,
                imageWidth = sWidth,
                imageHeight = sHeight,
            )
            setMinimumScaleType(smartType)
            resetScaleAndCenter()
        }
        super.onSizeChanged(w, h, oldw, oldh)
    }

    fun applySmartScaleIfNeeded(): Boolean {
        if (!isSmartFitEnabled || !isReady || width <= 0 || height <= 0) return false
        val smartType = ReaderScaleUtil.resolveSmartScaleType(
            viewWidth = width,
            viewHeight = height,
            imageWidth = sWidth,
            imageHeight = sHeight,
        )
        setMinimumScaleType(smartType)
        resetScaleAndCenter()
        return true
    }
}
