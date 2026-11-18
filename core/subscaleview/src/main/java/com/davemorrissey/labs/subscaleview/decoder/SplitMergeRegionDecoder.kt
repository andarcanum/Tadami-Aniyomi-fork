package com.davemorrissey.labs.subscaleview.decoder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.Rect
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.provider.InputProvider
import tachiyomi.core.common.util.image.NativeImageDecoder
import tachiyomi.core.common.util.system.ImageUtil

/**
 * Virtual [ImageRegionDecoder] that presents a wide double-page spread `(W x H)` as a stacked
 * single-column strip `((W / 2) x (H * 2))` without materializing an untiled `2x` bitmap in RAM.
 */
class SplitMergeRegionDecoder(
    private val upperSide: ImageUtil.Side,
    hardwareTilesEnabled: Boolean = false,
    private val smartGutter: Boolean = false,
) : HdrAwareRegionDecoder, HardwareTileConfigurableDecoder {

    private val lock = Any()

    @Volatile
    private var decoder: NativeImageDecoder? = null

    @Volatile
    private var recycled = false

    @Volatile
    private var hdr = false

    @Volatile
    private var sourceWidth = 0

    @Volatile
    private var sourceHeight = 0

    @Volatile
    private var splitX = 0

    @Volatile
    private var halfWidth = 0

    @Volatile
    override var hardwareTilesEnabled: Boolean = hardwareTilesEnabled

    override val isHdr: Boolean
        get() = hdr

    override fun init(context: Context, provider: InputProvider): Point = synchronized(lock) {
        recycled = false
        decoder?.close()
        decoder = null
        hdr = false
        val stream = checkNotNull(provider.openStream()) { "Image stream could not be opened" }
        // Split before any border crop so asymmetric outer margins never shift the center seam.
        val opened = NativeImageDecoder.open(stream, cropBorders = false)
        decoder = opened
        hdr = opened.isHdr
        sourceWidth = opened.width
        sourceHeight = opened.height
        splitX = if (smartGutter) {
            ImageUtil.findSmartSplitX(opened)
        } else {
            (sourceWidth / 2).coerceAtLeast(1)
        }
        halfWidth = maxOf(splitX, sourceWidth - splitX).coerceAtLeast(1)
        Point(halfWidth, sourceHeight * 2)
    }

    override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
        val bitmap = synchronized(lock) {
            check(!recycled) { "Decoder not initialized or recycled" }
            val active = checkNotNull(decoder) { "Decoder not initialized or recycled" }
            val h = sourceHeight
            val upperLeftX = when (upperSide) {
                ImageUtil.Side.RIGHT -> splitX
                ImageUtil.Side.LEFT -> 0
            }
            val upperWidth = when (upperSide) {
                ImageUtil.Side.RIGHT -> sourceWidth - splitX
                ImageUtil.Side.LEFT -> splitX
            }
            val lowerLeftX = when (upperSide) {
                ImageUtil.Side.LEFT -> splitX
                ImageUtil.Side.RIGHT -> 0
            }
            val lowerWidth = when (upperSide) {
                ImageUtil.Side.LEFT -> sourceWidth - splitX
                ImageUtil.Side.RIGHT -> splitX
            }

            when {
                sRect.bottom <= h -> decodeHalfSlice(
                    active = active,
                    srcBaseX = upperLeftX,
                    halfSpanWidth = upperWidth,
                    sLeft = sRect.left,
                    sTop = sRect.top,
                    sRight = sRect.right,
                    sBottom = sRect.bottom,
                    sampleSize = sampleSize,
                )
                sRect.top >= h -> decodeHalfSlice(
                    active = active,
                    srcBaseX = lowerLeftX,
                    halfSpanWidth = lowerWidth,
                    sLeft = sRect.left,
                    sTop = sRect.top - h,
                    sRight = sRect.right,
                    sBottom = sRect.bottom - h,
                    sampleSize = sampleSize,
                )
                else -> decodeCrossingSeam(
                    active = active,
                    sRect = sRect,
                    sampleSize = sampleSize,
                    sourceHeight = h,
                    upperLeftX = upperLeftX,
                    upperWidth = upperWidth,
                    lowerLeftX = lowerLeftX,
                    lowerWidth = lowerWidth,
                )
            }
        }
        return maybeConvertToHardwareBitmap(bitmap)
    }

    private fun decodeHalfSlice(
        active: NativeImageDecoder,
        srcBaseX: Int,
        halfSpanWidth: Int,
        sLeft: Int,
        sTop: Int,
        sRight: Int,
        sBottom: Int,
        sampleSize: Int,
    ): Bitmap {
        val clampedLeft = sLeft.coerceIn(0, halfSpanWidth)
        val clampedRight = sRight.coerceIn(0, halfSpanWidth)
        if (clampedLeft == sLeft && clampedRight == sRight && clampedRight > clampedLeft) {
            return active.decode(
                Rect(srcBaseX + clampedLeft, sTop, srcBaseX + clampedRight, sBottom),
                sampleSize,
            )
        }
        val outWidth = (((sRight - sLeft).toLong() + sampleSize - 1) / sampleSize).toInt().coerceAtLeast(1)
        val outHeight = (((sBottom - sTop).toLong() + sampleSize - 1) / sampleSize).toInt().coerceAtLeast(1)
        val padded = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        if (clampedRight <= clampedLeft) {
            return padded
        }
        val decoded = active.decode(
            Rect(srcBaseX + clampedLeft, sTop, srcBaseX + clampedRight, sBottom),
            sampleSize,
        )
        try {
            val dstRight = (((clampedRight - sLeft).toLong() * outWidth) / (sRight - sLeft).coerceAtLeast(1))
                .toInt()
                .coerceIn(1, outWidth)
            val dstLeft = (((clampedLeft - sLeft).toLong() * outWidth) / (sRight - sLeft).coerceAtLeast(1))
                .toInt()
                .coerceIn(0, dstRight - 1)
            Canvas(padded).drawBitmap(decoded, null, Rect(dstLeft, 0, dstRight, outHeight), null)
        } finally {
            decoded.recycle()
        }
        return padded
    }

    private fun decodeCrossingSeam(
        active: NativeImageDecoder,
        sRect: Rect,
        sampleSize: Int,
        sourceHeight: Int,
        upperLeftX: Int,
        upperWidth: Int,
        lowerLeftX: Int,
        lowerWidth: Int,
    ): Bitmap {
        val outWidth = ((sRect.width().toLong() + sampleSize - 1) / sampleSize).toInt().coerceAtLeast(1)
        val outHeight = ((sRect.height().toLong() + sampleSize - 1) / sampleSize).toInt().coerceAtLeast(1)
        val combined = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combined)

        val upperHeight = sourceHeight - sRect.top
        val upperDstHeight = ((upperHeight.toLong() * outHeight) / sRect.height().coerceAtLeast(1))
            .toInt()
            .coerceIn(1, outHeight)

        val upperBitmap = decodeHalfSlice(
            active = active,
            srcBaseX = upperLeftX,
            halfSpanWidth = upperWidth,
            sLeft = sRect.left,
            sTop = sRect.top,
            sRight = sRect.right,
            sBottom = sourceHeight,
            sampleSize = sampleSize,
        )
        try {
            canvas.drawBitmap(upperBitmap, null, Rect(0, 0, outWidth, upperDstHeight), null)

            val lowerBitmap = decodeHalfSlice(
                active = active,
                srcBaseX = lowerLeftX,
                halfSpanWidth = lowerWidth,
                sLeft = sRect.left,
                sTop = 0,
                sRight = sRect.right,
                sBottom = sRect.bottom - sourceHeight,
                sampleSize = sampleSize,
            )
            try {
                canvas.drawBitmap(lowerBitmap, null, Rect(0, upperDstHeight, outWidth, outHeight), null)
            } finally {
                lowerBitmap.recycle()
            }
        } finally {
            upperBitmap.recycle()
        }
        return combined
    }

    override fun isReady(): Boolean = !recycled && decoder != null

    override fun recycle() {
        recycled = true
        synchronized(lock) {
            val current = decoder
            decoder = null
            hdr = false
            current?.close()
        }
    }

    private fun maybeConvertToHardwareBitmap(bitmap: Bitmap): Bitmap {
        var current = bitmap
        if (
            !hdr &&
            SubsamplingScaleImageView.getPreferredBitmapConfig() == Bitmap.Config.RGB_565 &&
            current.config != Bitmap.Config.RGB_565 &&
            current.config != Bitmap.Config.HARDWARE
        ) {
            val rgb565 = try {
                current.copy(Bitmap.Config.RGB_565, false)
            } catch (_: Throwable) {
                null
            }
            if (rgb565 != null) {
                current.recycle()
                current = rgb565
            }
        }
        if (
            !hardwareTilesEnabled ||
            hdr ||
            current.config == Bitmap.Config.HARDWARE ||
            !ImageUtil.canUseHardwareBitmap(current)
        ) {
            return current
        }
        val hardware = try {
            current.copy(Bitmap.Config.HARDWARE, false)
        } catch (_: Throwable) {
            null
        }
        return if (hardware != null) {
            current.recycle()
            hardware
        } else {
            current
        }
    }
}
