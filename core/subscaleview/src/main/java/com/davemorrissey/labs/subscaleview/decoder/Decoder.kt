package com.davemorrissey.labs.subscaleview.decoder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.provider.InputProvider
import tachiyomi.core.common.util.image.NativeImageDecoder
import tachiyomi.core.common.util.system.ImageUtil

class Decoder(
    private val cropBorders: Boolean,
    hardwareTilesEnabled: Boolean = false,
) : HdrAwareRegionDecoder, HardwareTileConfigurableDecoder {
    private val lock = Any()

    @Volatile
    private var decoder: NativeImageDecoder? = null

    @Volatile
    private var recycled = false

    @Volatile
    private var hdr = false

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
        val opened = NativeImageDecoder.open(stream, cropBorders)
        decoder = opened
        hdr = opened.isHdr
        Point(opened.width, opened.height)
    }

    override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
        val bitmap = synchronized(lock) {
            check(!recycled) { "Decoder not initialized or recycled" }
            val active = checkNotNull(decoder) { "Decoder not initialized or recycled" }
            active.decode(sRect, sampleSize)
        }
        return maybeConvertToHardwareBitmap(bitmap)
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
            !current.hasAlpha() &&
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
        if (current.config != Bitmap.Config.HARDWARE) {
            current.setHasMipMap(true)
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
            runCatching { hardware.setHasMipMap(true) }
            current.recycle()
            hardware
        } else {
            current
        }
    }
}
