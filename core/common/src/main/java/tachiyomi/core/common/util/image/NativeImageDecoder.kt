package tachiyomi.core.common.util.image

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.ColorSpace
import android.graphics.Gainmap
import android.graphics.Rect
import android.os.Build
import ca.mpreg.imagedecoder.ImageDecoder
import java.io.Closeable
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/**
 * Shared still-image decode boundary. Owns its decoder; returned bitmaps belong to the caller.
 * Native embedded profiles become sRGB (extended sRGB for half-float HDR). Gainmap HDR images decode
 * to an SDR base bitmap and expose optional [createPlatformGainmap] metadata on Android 14+.
 * Large PNGs use Android regions: imagedecoder18 intentionally rejects either axis above 32767px.
 */
class NativeImageDecoder private constructor(
    private val decoder: RegionDecoder,
    private val content: Rect,
) : Closeable {
    private var closed = false

    val width: Int get() = content.width()
    val height: Int get() = content.height()
    val isHdr: Boolean get() = decoder.isHdr
    val hasGainmap: Boolean get() = decoder.hasGainmap

    @Synchronized
    fun decode(region: Rect? = null, sampleSize: Int = 1): Bitmap {
        check(!closed) { "Image decoder has been closed" }
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
        val outWidth = ((source.width().toLong() + sampleSize - 1) / sampleSize)
        val outHeight = ((source.height().toLong() + sampleSize - 1) / sampleSize)
        val bytesPerPixel = if (decoder.isHdr) 8L else 4L
        require(outWidth * outHeight * bytesPerPixel <= MAX_BITMAP_BYTES) {
            "Decoded bitmap exceeds byte limit: ${outWidth}x$outHeight"
        }
        return decoder.decode(source, sampleSize)
    }

    /**
     * Builds a platform [Gainmap] aligned with this decoder's [content] crop on Android 14+, or
     * returns `null` when unsupported or absent. Caller owns the gainmap contents bitmap.
     */
    @Synchronized
    fun createPlatformGainmap(): Gainmap? {
        check(!closed) { "Image decoder has been closed" }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        if (!decoder.hasGainmap) return null
        val nativeGainmap = decoder.getGainmap() ?: return null
        return Api34Gainmap.build(nativeGainmap, content, decoder.width, decoder.height)
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        decoder.close()
    }

    private interface RegionDecoder : Closeable {
        val width: Int
        val height: Int
        val isHdr: Boolean
        val hasGainmap: Boolean
        fun decode(region: Rect, sampleSize: Int): Bitmap
        fun getGainmap(): ImageDecoder.Gainmap? = null
    }

    private class NativeRegionDecoder(private val native: ImageDecoder) : RegionDecoder {
        override val width get() = native.width
        override val height get() = native.height
        override val isHdr get() = native.isHdr
        override val hasGainmap get() = native.hdrKind == ImageDecoder.HdrKind.GAINMAP
        override fun decode(region: Rect, sampleSize: Int): Bitmap = native.decodeToBitmap(region, sampleSize)
        override fun getGainmap(): ImageDecoder.Gainmap? = native.getGainmap()
        override fun close() = native.close()
    }

    private class PlatformRegionDecoder(private val platform: BitmapRegionDecoder) : RegionDecoder {
        override val width get() = platform.width
        override val height get() = platform.height
        override val isHdr = false
        override val hasGainmap = false
        override fun decode(region: Rect, sampleSize: Int): Bitmap {
            check(!platform.isRecycled) { "Image decoder has been closed" }
            val maxSample = minOf(sampleSize, region.width(), region.height()).coerceAtLeast(1)
            val options = BitmapFactory.Options().apply {
                inSampleSize = Integer.highestOneBit(maxSample)
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
            }
            val bitmap = checkNotNull(platform.decodeRegion(region, options)) { "Failed to decode large image region" }
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

    @TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private object Api34Gainmap {
        fun build(
            nativeGainmap: ImageDecoder.Gainmap,
            content: Rect,
            fullWidth: Int,
            fullHeight: Int,
        ): Gainmap? {
            val gw = nativeGainmap.width
            val gh = nativeGainmap.height
            if (gw <= 0 || gh <= 0 || fullWidth <= 0 || fullHeight <= 0) return null
            val rawBitmap = decodeGainmapBitmap(nativeGainmap, gw, gh) ?: return null
            val bitmap = cropIfNeeded(rawBitmap, gw, gh, content, fullWidth, fullHeight)
            return try {
                Gainmap(bitmap).apply {
                    val minBoost = triple(nativeGainmap.minContentBoost, 1f)
                    val maxBoost = triple(nativeGainmap.maxContentBoost, 1f)
                    val gamma = triple(nativeGainmap.gamma, 1f)
                    val epsSdr = triple(nativeGainmap.offsetSdr, 0f)
                    val epsHdr = triple(nativeGainmap.offsetHdr, 0f)
                    if (minBoost.all { it.isFinite() && it > 0f }) {
                        setRatioMin(minBoost[0], minBoost[1], minBoost[2])
                    }
                    if (maxBoost.all { it.isFinite() && it > 0f }) {
                        setRatioMax(maxBoost[0], maxBoost[1], maxBoost[2])
                    }
                    if (gamma.all { it.isFinite() && it > 0f }) {
                        setGamma(gamma[0], gamma[1], gamma[2])
                    }
                    if (epsSdr.all { it.isFinite() && it >= 0f }) {
                        setEpsilonSdr(epsSdr[0], epsSdr[1], epsSdr[2])
                    }
                    if (epsHdr.all { it.isFinite() && it >= 0f }) {
                        setEpsilonHdr(epsHdr[0], epsHdr[1], epsHdr[2])
                    }
                    // ImageDecoder exposes headroom in stops (log2); Android Gainmap expects linear display ratios.
                    val minLinear = 2f.pow(nativeGainmap.baseHdrHeadroom).coerceAtLeast(1f)
                    val fullLinear = 2f.pow(nativeGainmap.alternateHdrHeadroom)
                    if (minLinear.isFinite()) {
                        setMinDisplayRatioForHdrTransition(minLinear)
                    }
                    if (fullLinear.isFinite() && fullLinear > 1f) {
                        setDisplayRatioForFullHdr(maxOf(fullLinear, minLinear))
                    }
                }
            } catch (error: Throwable) {
                bitmap.recycle()
                throw error
            }
        }

        private fun decodeGainmapBitmap(nativeGainmap: ImageDecoder.Gainmap, width: Int, height: Int): Bitmap? {
            val src = nativeGainmap.pixels.duplicate().apply { rewind() }
            val pixelCount = width.toLong() * height
            return when (nativeGainmap.channels) {
                1 -> {
                    if (src.remaining() < pixelCount) return null
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
                    try {
                        val rowBytes = bitmap.rowBytes
                        if (rowBytes == width && src.remaining().toLong() == pixelCount) {
                            bitmap.copyPixelsFromBuffer(src)
                        } else {
                            val padded = ByteArray(rowBytes * height)
                            for (row in 0 until height) {
                                src.get(padded, row * rowBytes, width)
                            }
                            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(padded))
                        }
                        bitmap
                    } catch (error: Throwable) {
                        bitmap.recycle()
                        throw error
                    }
                }
                3 -> {
                    if (src.remaining() < pixelCount * 3L) return null
                    val argb = IntArray(pixelCount.toInt())
                    for (i in argb.indices) {
                        val r = src.get().toInt() and 0xFF
                        val g = src.get().toInt() and 0xFF
                        val b = src.get().toInt() and 0xFF
                        argb[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    }
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                        setPixels(argb, 0, width, 0, 0, width, height)
                    }
                }
                4 -> {
                    if (src.remaining() < pixelCount * 4L) return null
                    val argb = IntArray(pixelCount.toInt())
                    for (i in argb.indices) {
                        val r = src.get().toInt() and 0xFF
                        val g = src.get().toInt() and 0xFF
                        val b = src.get().toInt() and 0xFF
                        val a = src.get().toInt() and 0xFF
                        argb[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                    }
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                        setPixels(argb, 0, width, 0, 0, width, height)
                    }
                }
                else -> null
            }
        }

        private fun cropIfNeeded(
            rawBitmap: Bitmap,
            gw: Int,
            gh: Int,
            content: Rect,
            fullWidth: Int,
            fullHeight: Int,
        ): Bitmap {
            if (content.left == 0 && content.top == 0 && content.right == fullWidth && content.bottom == fullHeight) {
                return rawBitmap
            }
            val gLeft = floor(content.left.toDouble() * gw / fullWidth).toInt().coerceIn(0, gw - 1)
            val gTop = floor(content.top.toDouble() * gh / fullHeight).toInt().coerceIn(0, gh - 1)
            val gRight = ceil(content.right.toDouble() * gw / fullWidth).toInt().coerceIn(gLeft + 1, gw)
            val gBottom = ceil(content.bottom.toDouble() * gh / fullHeight).toInt().coerceIn(gTop + 1, gh)
            if (gLeft == 0 && gTop == 0 && gRight == gw && gBottom == gh) return rawBitmap
            return try {
                Bitmap.createBitmap(rawBitmap, gLeft, gTop, gRight - gLeft, gBottom - gTop)
            } finally {
                rawBitmap.recycle()
            }
        }

        private fun triple(values: FloatArray, fallback: Float): FloatArray {
            val c0 = values.getOrElse(0) { fallback }
            val c1 = values.getOrElse(1) { c0 }
            val c2 = values.getOrElse(2) { c0 }
            return floatArrayOf(c0, c1, c2)
        }
    }

    companion object {
        private const val NATIVE_AXIS_LIMIT = 32_767
        private val PLATFORM_LARGE_AXIS_FORMATS = setOf(
            ImageHeader.Format.PNG,
            ImageHeader.Format.JPEG,
            ImageHeader.Format.WEBP,
        )
        private const val MAX_IMAGE_PIXELS = 268_435_456L // Same pixel ceiling as decoder18.
        private const val MAX_BITMAP_BYTES = Int.MAX_VALUE.toLong()

        /** Consumes and closes the input, including setup/stream-close failures. */
        fun open(input: InputStream, cropBorders: Boolean = false): NativeImageDecoder {
            var opened: RegionDecoder? = null
            try {
                input.buffered().use { source ->
                    val header = ImageHeader.read(source)
                    val maxAxis = maxOf(header?.width ?: 0, header?.height ?: 0)
                    val usePlatformLargeDecoder = header != null &&
                        header.format in PLATFORM_LARGE_AXIS_FORMATS &&
                        maxAxis > NATIVE_AXIS_LIMIT
                    opened = if (usePlatformLargeDecoder) {
                        val large = checkNotNull(header)
                        require(checkNotNull(large.width).toLong() * checkNotNull(large.height) <= MAX_IMAGE_PIXELS) {
                            "Image is too large"
                        }
                        val platform = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            BitmapRegionDecoder.newInstance(source)
                        } else {
                            @Suppress("DEPRECATION")
                            BitmapRegionDecoder.newInstance(source, false)
                        }
                        PlatformRegionDecoder(checkNotNull(platform) { "Failed to open large ${large.format} image" })
                    } else {
                        NativeRegionDecoder(ImageDecoder.open(source))
                    }
                }
                val decoder = checkNotNull(opened)
                require(decoder.width > 0 && decoder.height > 0) {
                    "Invalid image dimensions: ${decoder.width}x${decoder.height}"
                }
                val bounds = Rect(0, 0, decoder.width, decoder.height)
                if (cropBorders && !decoder.isHdr) {
                    bounds.set(
                        ImageBorders.find(decoder.width, decoder.height) { rect, buffer ->
                            val strip = decoder.decode(rect, 1)
                            try {
                                check(strip.width == rect.width() && strip.height == rect.height()) {
                                    "Unexpected border strip size ${strip.width}x${strip.height} for $rect"
                                }
                                strip.getPixels(buffer, 0, rect.width(), 0, 0, rect.width(), rect.height())
                            } finally {
                                strip.recycle()
                            }
                        },
                    )
                }
                return NativeImageDecoder(decoder, bounds)
            } catch (error: Throwable) {
                opened?.close()
                throw error
            }
        }
    }
}
