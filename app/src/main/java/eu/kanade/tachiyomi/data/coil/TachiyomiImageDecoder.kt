package eu.kanade.tachiyomi.data.coil

import android.graphics.Bitmap
import android.graphics.Gainmap
import android.os.Build
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.allowRgb565
import coil3.request.bitmapConfig
import coil3.request.maxBitmapSize
import coil3.size.Dimension
import coil3.size.Precision
import coil3.size.Scale
import kotlinx.coroutines.runInterruptible
import tachiyomi.core.common.util.image.ImageHeader
import tachiyomi.core.common.util.image.NativeImageDecoder
import tachiyomi.core.common.util.system.ImageUtil
import kotlin.math.roundToInt

internal fun Options.computeDstDimensions(srcWidth: Int, srcHeight: Int): Pair<Int, Int> {
    var dstWidth = size.widthPx(scale) { srcWidth }
    var dstHeight = size.heightPx(scale) { srcHeight }
    val maxWidth = maxBitmapSize.width
    if (maxWidth is Dimension.Pixels && dstWidth != Int.MIN_VALUE && dstWidth != Int.MAX_VALUE) {
        dstWidth = dstWidth.coerceAtMost(maxWidth.px)
    }
    val maxHeight = maxBitmapSize.height
    if (maxHeight is Dimension.Pixels && dstHeight != Int.MIN_VALUE && dstHeight != Int.MAX_VALUE) {
        dstHeight = dstHeight.coerceAtMost(maxHeight.px)
    }
    return dstWidth to dstHeight
}

/**
 * A [Decoder] using [NativeImageDecoder] for still images outside platform support (AVIF, JXL,
 * HEIF, TIFF, JPEG 2000) or when border cropping / custom decoding is requested.
 */
class TachiyomiImageDecoder(private val resources: ImageSource, private val options: Options) : Decoder {

    override suspend fun decode(): DecodeResult = runInterruptible {
        var sampleSize = 1
        var isSampled = false
        var isHdr = false
        var format: ImageHeader.Format? = null
        var gainmap: Gainmap? = null

        var bitmap = resources.use { imageSource ->
            val buffered = imageSource.source()
            format = ImageHeader.read(buffered.peek().inputStream())?.format
            NativeImageDecoder.open(buffered.inputStream(), options.cropBorders).use { decoder ->
                isHdr = decoder.isHdr
                val srcWidth = decoder.width
                val srcHeight = decoder.height
                val (dstWidth, dstHeight) = options.computeDstDimensions(srcWidth, srcHeight)
                sampleSize = DecodeUtils.calculateInSampleSize(
                    srcWidth = srcWidth,
                    srcHeight = srcHeight,
                    dstWidth = dstWidth,
                    dstHeight = dstHeight,
                    scale = options.scale,
                )
                isSampled = sampleSize > 1

                var decoded = decoder.decode(sampleSize = sampleSize)
                val multiplier = computeTargetScale(
                    decodedWidth = decoded.width,
                    decodedHeight = decoded.height,
                    dstWidth = dstWidth,
                    dstHeight = dstHeight,
                )
                if (multiplier != 1.0) {
                    val targetWidth = (decoded.width * multiplier).roundToInt().coerceAtLeast(1)
                    val targetHeight = (decoded.height * multiplier).roundToInt().coerceAtLeast(1)
                    if (targetWidth != decoded.width || targetHeight != decoded.height) {
                        val scaled = try {
                            Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
                        } catch (error: Throwable) {
                            decoded.recycle()
                            throw error
                        }
                        if (scaled !== decoded) {
                            decoded.recycle()
                        }
                        decoded = scaled
                        if (multiplier < 1.0) {
                            isSampled = true
                        }
                    }
                }

                if (!isHdr && decoder.hasGainmap) {
                    gainmap = try {
                        decoder.createPlatformGainmap()
                    } catch (error: Throwable) {
                        decoded.recycle()
                        throw error
                    }
                }
                decoded
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && gainmap != null) {
                bitmap.setGainmap(gainmap)
            }

            if (options.bitmapConfig == Bitmap.Config.HARDWARE && ImageUtil.canUseHardwareBitmap(bitmap)) {
                val hwBitmap = bitmap.copy(Bitmap.Config.HARDWARE, false)
                if (hwBitmap != null) {
                    val preservesGainmap = gainmap == null ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hwBitmap.hasGainmap())
                    if (preservesGainmap) {
                        bitmap.recycle()
                        bitmap = hwBitmap
                    } else {
                        hwBitmap.recycle()
                    }
                }
            }

            if (bitmap.config != Bitmap.Config.HARDWARE && shouldConvertToRgb565(bitmap, isHdr, gainmap, format)) {
                val rgb565 = bitmap.copy(Bitmap.Config.RGB_565, false)
                if (rgb565 != null) {
                    bitmap.recycle()
                    bitmap = rgb565
                }
            }

            DecodeResult(
                image = bitmap.asImage(),
                isSampled = isSampled,
            )
        } catch (error: Throwable) {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                gainmap?.gainmapContents?.let { gmBitmap ->
                    if (!gmBitmap.isRecycled) gmBitmap.recycle()
                }
            }
            throw error
        }
    }

    private fun computeTargetScale(
        decodedWidth: Int,
        decodedHeight: Int,
        dstWidth: Int,
        dstHeight: Int,
    ): Double {
        if (options.precision == Precision.EXACT) {
            return DecodeUtils.computeSizeMultiplier(
                srcWidth = decodedWidth.toDouble(),
                srcHeight = decodedHeight.toDouble(),
                dstWidth = dstWidth.toDouble(),
                dstHeight = dstHeight.toDouble(),
                scale = options.scale,
                maxSize = options.maxBitmapSize,
            )
        }
        return DecodeUtils.computeSizeMultiplier(
            srcWidth = decodedWidth.toDouble(),
            srcHeight = decodedHeight.toDouble(),
            dstWidth = decodedWidth.toDouble(),
            dstHeight = decodedHeight.toDouble(),
            scale = Scale.FIT,
            maxSize = options.maxBitmapSize,
        ).coerceAtMost(1.0)
    }

    private fun shouldConvertToRgb565(
        bitmap: Bitmap,
        isHdr: Boolean,
        gainmap: Gainmap?,
        format: ImageHeader.Format?,
    ): Boolean {
        if (isHdr || gainmap != null || options.bitmapConfig == Bitmap.Config.RGBA_F16) return false
        if (options.bitmapConfig == Bitmap.Config.RGB_565) return true
        if (!options.allowRgb565) return false
        return format == ImageHeader.Format.JPEG || !bitmap.hasAlpha()
    }

    class Factory : Decoder.Factory {

        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            val source = result.source.source()
            val info = try {
                ImageHeader.read(source.peek().inputStream())
            } catch (_: Exception) {
                null
            }
            val animated = when (info?.format) {
                ImageHeader.Format.GIF -> true
                ImageHeader.Format.WEBP,
                ImageHeader.Format.AVIF,
                ImageHeader.Format.HEIF,
                -> info.animated
                ImageHeader.Format.JXL -> ImageUtil.isAnimatedAndSupported(source)
                else -> false
            }
            if (animated) return null
            val supportedFormat = info?.format in SUPPORTED_FORMATS
            return if (options.customDecoder || options.cropBorders || supportedFormat) {
                TachiyomiImageDecoder(result.source, options)
            } else {
                null
            }
        }

        override fun equals(other: Any?) = other is Factory

        override fun hashCode() = javaClass.hashCode()

        private companion object {
            val SUPPORTED_FORMATS = setOf(
                ImageHeader.Format.AVIF,
                ImageHeader.Format.HEIF,
                ImageHeader.Format.JP2,
                ImageHeader.Format.JXL,
                ImageHeader.Format.TIFF,
            )
        }
    }
}
