package tachiyomi.core.common.util.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.ColorSpace
import android.graphics.Rect
import android.os.Build
import ca.mpreg.imagedecoder.ImageDecoder
import java.io.Closeable
import java.io.InputStream

/**
 * Shared still-image decode boundary. Owns its decoder; returned bitmaps belong to the caller.
 * Native embedded profiles become sRGB (extended sRGB for half-float HDR). Gainmaps use the SDR base.
 * Large PNGs use Android regions: imagedecoder18 intentionally rejects either axis above 32767px.
 */
class NativeImageDecoder private constructor(
    private val decoder: RegionDecoder,
    private val content: Rect,
) : Closeable {
    val width: Int get() = content.width()
    val height: Int get() = content.height()

    @Synchronized
    fun decode(region: Rect? = null, sampleSize: Int = 1): Bitmap {
        require(sampleSize >= 1)
        val source = if (region == null) {
            Rect(content)
        } else {
            require(
                !region.isEmpty && region.left >= 0 && region.top >= 0 && region.right <= width &&
                    region.bottom <= height,
            )
            Rect(region).apply { offset(content.left, content.top) }
        }
        return decoder.decode(source, sampleSize)
    }

    @Synchronized
    override fun close() = decoder.close()

    private interface RegionDecoder : Closeable {
        val width: Int
        val height: Int
        val isHdr: Boolean
        fun decode(region: Rect, sampleSize: Int): Bitmap
    }

    private class NativeRegionDecoder(private val native: ImageDecoder) : RegionDecoder {
        override val width get() = native.width
        override val height get() = native.height
        override val isHdr get() = native.isHdr
        override fun decode(region: Rect, sampleSize: Int): Bitmap = native.decodeToBitmap(region, sampleSize)
        override fun close() = native.close()
    }

    private class LargePngRegionDecoder(private val platform: BitmapRegionDecoder) : RegionDecoder {
        override val width get() = platform.width
        override val height get() = platform.height
        override val isHdr = false
        override fun decode(region: Rect, sampleSize: Int): Bitmap {
            check(!platform.isRecycled) { "Image decoder has been closed" }
            val options = BitmapFactory.Options().apply {
                inSampleSize = Integer.highestOneBit(sampleSize)
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
            }
            val bitmap = checkNotNull(platform.decodeRegion(region, options)) { "Failed to decode PNG region" }
            val width = ((region.width().toLong() + sampleSize - 1) / sampleSize).toInt()
            val height = ((region.height().toLong() + sampleSize - 1) / sampleSize).toInt()
            if (bitmap.width == width && bitmap.height == height) return bitmap
            return try {
                Bitmap.createScaledBitmap(bitmap, width, height, true)
            } finally {
                bitmap.recycle()
            }
        }
        override fun close() = platform.recycle()
    }

    companion object {
        private const val BORDER_ANALYSIS_PIXELS = 1_048_576L
        private const val NATIVE_PNG_AXIS_LIMIT = 32_767
        private const val MAX_IMAGE_PIXELS = 268_435_456L // Same pixel ceiling as decoder18.

        /** Consumes and closes the input, including setup/stream-close failures. */
        fun open(input: InputStream, cropBorders: Boolean = false): NativeImageDecoder {
            var opened: RegionDecoder? = null
            try {
                input.buffered().use { source ->
                    val header = ImageHeader.read(source)
                    val largePng = header?.format == ImageHeader.Format.PNG &&
                        ((header.width ?: 0) > NATIVE_PNG_AXIS_LIMIT || (header.height ?: 0) > NATIVE_PNG_AXIS_LIMIT)
                    opened = if (largePng) {
                        val png = checkNotNull(header)
                        require(checkNotNull(png.width).toLong() * checkNotNull(png.height) <= MAX_IMAGE_PIXELS) {
                            "Image is too large"
                        }
                        val platform = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            BitmapRegionDecoder.newInstance(source)
                        } else {
                            @Suppress("DEPRECATION")
                            BitmapRegionDecoder.newInstance(source, false)
                        }
                        LargePngRegionDecoder(checkNotNull(platform) { "Failed to open large PNG" })
                    } else {
                        NativeRegionDecoder(ImageDecoder.open(source))
                    }
                }
                val decoder = checkNotNull(opened)
                val bounds = Rect(0, 0, decoder.width, decoder.height)
                if (cropBorders && !decoder.isHdr &&
                    decoder.width.toLong() * decoder.height <= BORDER_ANALYSIS_PIXELS
                ) {
                    // Never infer destructive crop bounds from nearest-sampled pixels: a thin
                    // line in an omitted row can be arbitrarily far from the sampled content.
                    val preview = decoder.decode(bounds, 1)
                    try {
                        val pixels = IntArray(preview.width * preview.height)
                        preview.getPixels(pixels, 0, preview.width, 0, 0, preview.width, preview.height)
                        bounds.set(ImageBorders.find(pixels, preview.width, preview.height))
                    } finally {
                        preview.recycle()
                    }
                }
                return NativeImageDecoder(decoder, bounds)
            } catch (error: Throwable) {
                opened?.close()
                throw error
            }
        }
    }
}
