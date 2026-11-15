package tachiyomi.core.common.util.system

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import androidx.annotation.ColorInt
import androidx.core.graphics.alpha
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.blue
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.green
import androidx.core.graphics.red
import ca.mpreg.imagedecoder.ImageDecoder
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.util.system.GLUtil
import logcat.LogPriority
import okio.Buffer
import okio.BufferedSource
import tachiyomi.core.common.util.image.ImageHeader
import tachiyomi.core.common.util.image.NativeImageDecoder
import java.io.InputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object ImageUtil {

    fun isImage(name: String?, openStream: (() -> InputStream)? = null): Boolean {
        if (name == null) return false

        val extension = name.substringAfterLast('.')
        return ImageType.entries.any { it.extension == extension } || openStream?.let { findImageType(it) } != null
    }

    fun findImageType(openStream: () -> InputStream): ImageType? {
        return openStream().use { findImageType(it) }
    }

    fun findImageType(stream: InputStream): ImageType? {
        return try {
            when (ImageHeader.read(stream)?.format) {
                ImageHeader.Format.AVIF -> ImageType.AVIF
                ImageHeader.Format.GIF -> ImageType.GIF
                ImageHeader.Format.HEIF -> ImageType.HEIF
                ImageHeader.Format.JPEG -> ImageType.JPEG
                ImageHeader.Format.JXL -> ImageType.JXL
                ImageHeader.Format.PNG -> ImageType.PNG
                ImageHeader.Format.WEBP -> ImageType.WEBP
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getExtensionFromMimeType(mime: String?, openStream: () -> InputStream): String {
        val type = mime?.let { ImageType.entries.find { it.mime == mime } } ?: findImageType(openStream)
        return type?.extension ?: "jpg"
    }

    fun isAnimatedAndSupported(source: BufferedSource): Boolean {
        return try {
            val type = ImageHeader.read(source.peek().inputStream()) ?: return false
            when (type.format) {
                ImageHeader.Format.GIF -> true
                ImageHeader.Format.WEBP -> type.animated
                ImageHeader.Format.HEIF -> type.animated
                ImageHeader.Format.AVIF -> type.animated
                ImageHeader.Format.JXL -> {
                    try {
                        source.peek().inputStream().use { stream ->
                            ImageDecoder.open(stream).use { it.isAnimated }
                        }
                    } catch (_: LinkageError) {
                        false
                    }
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    enum class ImageType(val mime: String, val extension: String) {
        AVIF("image/avif", "avif"),
        GIF("image/gif", "gif"),
        HEIF("image/heif", "heif"),
        JPEG("image/jpeg", "jpg"),
        JXL("image/jxl", "jxl"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp"),
    }

    /**
     * Check whether the image is wide (which we consider a double-page spread).
     *
     * @return true if the width is greater than the height
     */
    fun isWideImage(imageSource: BufferedSource): Boolean {
        val options = extractImageOptions(imageSource)
        return options.outWidth > options.outHeight
    }

    fun getImageDimensions(imageSource: BufferedSource): ImageDimensions? {
        val options = extractImageOptions(imageSource)
        return if (options.outWidth > 0 && options.outHeight > 0) {
            ImageDimensions(width = options.outWidth, height = options.outHeight)
        } else {
            null
        }
    }

    /**
     * Extract the 'side' part from [BufferedSource] and return it as [BufferedSource].
     */
    fun splitInHalf(imageSource: BufferedSource, side: Side): BufferedSource {
        val imageBitmap = decodeBitmap(imageSource)
        return try {
            val height = imageBitmap.height
            val width = imageBitmap.width

            val singlePage = Rect(0, 0, width / 2, height)

            val half = createBitmap(width / 2, height)
            try {
                val part = when (side) {
                    Side.RIGHT -> Rect(width - width / 2, 0, width, height)
                    Side.LEFT -> Rect(0, 0, width / 2, height)
                }
                half.applyCanvas {
                    drawBitmap(imageBitmap, part, singlePage, null)
                }
                val output = Buffer()
                half.compress(Bitmap.CompressFormat.JPEG, 100, output.outputStream())
                output
            } finally {
                half.recycle()
            }
        } finally {
            imageBitmap.recycle()
        }
    }

    fun rotateImage(imageSource: BufferedSource, degrees: Float): BufferedSource {
        val imageBitmap = decodeBitmap(imageSource)
        val rotated = try {
            rotateBitMap(imageBitmap, degrees)
        } catch (e: Throwable) {
            imageBitmap.recycle()
            throw e
        }
        if (rotated !== imageBitmap) {
            imageBitmap.recycle()
        }

        return try {
            val output = Buffer()
            rotated.compress(Bitmap.CompressFormat.JPEG, 100, output.outputStream())
            output
        } finally {
            rotated.recycle()
        }
    }

    private fun rotateBitMap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Split the image into left and right parts, then merge them into a new image.
     */
    fun splitAndMerge(imageSource: BufferedSource, upperSide: Side): BufferedSource {
        val imageBitmap = decodeBitmap(imageSource)
        return try {
            val height = imageBitmap.height
            val width = imageBitmap.width

            val result = createBitmap(width / 2, height * 2)
            try {
                result.applyCanvas {
                    // right -> upper
                    val rightPart = when (upperSide) {
                        Side.RIGHT -> Rect(width - width / 2, 0, width, height)
                        Side.LEFT -> Rect(0, 0, width / 2, height)
                    }
                    val upperPart = Rect(0, 0, width / 2, height)
                    drawBitmap(imageBitmap, rightPart, upperPart, null)
                    // left -> bottom
                    val leftPart = when (upperSide) {
                        Side.LEFT -> Rect(width - width / 2, 0, width, height)
                        Side.RIGHT -> Rect(0, 0, width / 2, height)
                    }
                    val bottomPart = Rect(0, height, width / 2, height * 2)
                    drawBitmap(imageBitmap, leftPart, bottomPart, null)
                }

                val output = Buffer()
                result.compress(Bitmap.CompressFormat.JPEG, 100, output.outputStream())
                output
            } finally {
                result.recycle()
            }
        } finally {
            imageBitmap.recycle()
        }
    }

    /**
     * Bitmap-returning variants of the dual-page operations. They skip the lossy JPEG q=100
     * re-encode round trip (full bitmap -> crop -> compress -> buffer -> SSIV re-decode) that
     * the [BufferedSource] variants perform; holders hand the bitmap straight to the image view.
     * Return null when decoding fails so callers can fall back to the stream variant.
     */
    fun splitInHalfBitmap(imageSource: BufferedSource, side: Side): Bitmap? {
        return try {
            // Region-decode only the kept half: halves both decode cost and peak memory
            // (no full-size bitmap is ever materialized).
            val decoder = NativeImageDecoder.open(imageSource.inputStream())
            try {
                val width = decoder.width
                val height = decoder.height
                if (width <= 0 || height <= 0) return null
                val halfWidth = width / 2
                if (halfWidth == 0) return null
                val part = when (side) {
                    Side.RIGHT -> Rect(width - halfWidth, 0, width, height)
                    Side.LEFT -> Rect(0, 0, halfWidth, height)
                }
                decoder.decode(part)
            } finally {
                decoder.close()
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "splitInHalfBitmap failed, falling back" }
            null
        }
    }

    fun rotateImageBitmap(imageSource: BufferedSource, degrees: Float): Bitmap? {
        var imageBitmap: Bitmap? = null
        return try {
            imageBitmap = decodeBitmap(imageSource)
            val rotated = rotateBitMap(imageBitmap, degrees)
            if (rotated !== imageBitmap) {
                imageBitmap.recycle()
            }
            rotated
        } catch (e: Exception) {
            if (imageBitmap != null && !imageBitmap.isRecycled) {
                imageBitmap.recycle()
            }
            logcat(LogPriority.WARN, e) { "rotateImageBitmap failed, falling back" }
            null
        }
    }

    fun splitAndMergeBitmap(imageSource: BufferedSource, upperSide: Side): Bitmap? {
        var imageBitmap: Bitmap? = null
        return try {
            imageBitmap = decodeBitmap(imageSource)
            val height = imageBitmap.height
            val width = imageBitmap.width
            if (width <= 0 || height <= 0) return null
            createBitmap(width / 2, height * 2).applyCanvas {
                // right -> upper
                val rightPart = when (upperSide) {
                    Side.RIGHT -> Rect(width - width / 2, 0, width, height)
                    Side.LEFT -> Rect(0, 0, width / 2, height)
                }
                val upperPart = Rect(0, 0, width / 2, height)
                drawBitmap(imageBitmap, rightPart, upperPart, null)
                // left -> bottom
                val leftPart = when (upperSide) {
                    Side.LEFT -> Rect(width - width / 2, 0, width, height)
                    Side.RIGHT -> Rect(0, 0, width / 2, height)
                }
                val bottomPart = Rect(0, height, width / 2, height * 2)
                drawBitmap(imageBitmap, leftPart, bottomPart, null)
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "splitAndMergeBitmap failed, falling back" }
            null
        } finally {
            if (imageBitmap != null && !imageBitmap.isRecycled) {
                imageBitmap.recycle()
            }
        }
    }

    enum class Side {
        RIGHT,
        LEFT,
    }

    data class ImageDimensions(
        val width: Int,
        val height: Int,
    )

    /**
     * Check whether the image is considered a tall image.
     *
     * @return true if the height:width ratio is greater than 3.
     */
    fun isTallImage(imageSource: BufferedSource): Boolean {
        val options = extractImageOptions(imageSource)
        return (options.outHeight.toFloat() / options.outWidth.toFloat()) > 3F
    }

    /**
     * Splits tall images to improve performance of reader
     */
    fun splitTallImage(tmpDir: UniFile, imageFile: UniFile, filenamePrefix: String): Boolean {
        val imageSource = imageFile.openInputStream().use { Buffer().readFrom(it) }
        if (isAnimatedAndSupported(imageSource) || !isTallImage(imageSource)) {
            return true
        }

        val decoder = try {
            NativeImageDecoder.open(imageSource.peek().inputStream())
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to create image decoder for splitTallImage" }
            return false
        } catch (e: LinkageError) {
            logcat(LogPriority.ERROR, e) { "Failed to load native image decoder for splitTallImage" }
            return false
        }

        val splitDataList = computeSplitData(decoder.width, decoder.height)

        return try {
            splitDataList.forEach { splitData ->
                val splitImageName = splitImageName(filenamePrefix, splitData.index)
                // Remove pre-existing split if exists (this split shouldn't exist under normal circumstances)
                tmpDir.findFile(splitImageName)?.delete()

                val splitFile = tmpDir.createFile(splitImageName)!!

                val region = Rect(
                    0,
                    splitData.topOffset,
                    splitData.splitWidth,
                    splitData.bottomOffset,
                )

                val decoded = decoder.decode(region)
                val splitBitmap = if (decoded.config == Bitmap.Config.RGBA_F16) {
                    try {
                        decoded.copy(Bitmap.Config.ARGB_8888, false)
                            ?: error("Failed to convert split region to ARGB_8888")
                    } finally {
                        decoded.recycle()
                    }
                } else {
                    decoded
                }
                try {
                    splitFile.openOutputStream().use { outputStream ->
                        splitBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    }
                } finally {
                    splitBitmap.recycle()
                }
                logcat {
                    "Success: Split #${splitData.index + 1} with topOffset=${splitData.topOffset} " +
                        "height=${splitData.splitHeight} bottomOffset=${splitData.bottomOffset}"
                }
            }
            imageFile.delete()
            true
        } catch (e: Exception) {
            // Image splits were not successfully saved so delete them and keep the original image
            splitDataList
                .map { splitImageName(filenamePrefix, it.index) }
                .forEach { tmpDir.findFile(it)?.delete() }
            logcat(LogPriority.ERROR, e)
            false
        } finally {
            decoder.close()
        }
    }

    private fun splitImageName(filenamePrefix: String, index: Int) = "${filenamePrefix}__${"%03d".format(
        Locale.ENGLISH,
        index + 1,
    )}.jpg"

    private fun computeSplitData(imageWidth: Int, imageHeight: Int): List<SplitData> {
        // -1 so it doesn't try to split when imageHeight = optimalImageHeight
        val partCount = (imageHeight - 1) / optimalImageHeight + 1
        val optimalSplitHeight = imageHeight / partCount

        logcat {
            "Generating SplitData for image (height: $imageHeight): " +
                "$partCount parts @ ${optimalSplitHeight}px height per part"
        }

        return buildList {
            val range = 0..<partCount
            for (index in range) {
                // Only continue if the list is empty or there is image remaining
                if (isNotEmpty() && imageHeight <= last().bottomOffset) break

                val topOffset = index * optimalSplitHeight
                var splitHeight = min(optimalSplitHeight, imageHeight - topOffset)

                if (index == range.last) {
                    val remainingHeight = imageHeight - (topOffset + splitHeight)
                    splitHeight += remainingHeight
                }

                add(SplitData(index, topOffset, splitHeight, imageWidth))
            }
        }
    }

    data class SplitData(
        val index: Int,
        val topOffset: Int,
        val splitHeight: Int,
        val splitWidth: Int,
    ) {
        val bottomOffset = topOffset + splitHeight
    }

    fun canUseHardwareBitmap(bitmap: Bitmap): Boolean {
        return canUseHardwareBitmap(bitmap.width, bitmap.height)
    }

    fun canUseHardwareBitmap(imageSource: BufferedSource): Boolean {
        return with(extractImageOptions(imageSource)) {
            canUseHardwareBitmap(outWidth, outHeight)
        }
    }

    var hardwareBitmapThreshold: Int = GLUtil.SAFE_TEXTURE_LIMIT

    fun canUseHardwareBitmap(width: Int, height: Int): Boolean {
        if (HARDWARE_BITMAP_UNSUPPORTED) return false
        return maxOf(width, height) <= hardwareBitmapThreshold
    }

    /**
     * Algorithm for determining what background to accompany a comic/manga page
     */
    fun chooseBackground(context: Context, imageStream: InputStream): Drawable {
        val image = decodeChooseBackgroundBitmap(imageStream) ?: return ColorDrawable(Color.WHITE)
        return try {
            computeBackgroundDrawable(context, image)
        } finally {
            image.recycle()
        }
    }

    private fun decodeChooseBackgroundBitmap(imageStream: InputStream): Bitmap? {
        return try {
            NativeImageDecoder.open(imageStream).use { decoder ->
                val w = decoder.width
                val h = decoder.height
                if (w < 50 || h < 50) return null
                // Downsample boundedly (~512px max side) while keeping the minor axis >= 64px so edge heuristics hold.
                val maxSampleForMax = maxOf(w, h) / 512
                val maxSampleForMin = minOf(w, h) / 64
                var sampleSize = minOf(maxSampleForMax, maxSampleForMin).coerceAtLeast(1)
                while (((w.toLong() + sampleSize - 1) / sampleSize) *
                    ((h.toLong() + sampleSize - 1) / sampleSize) > 4_194_304L
                ) {
                    sampleSize++
                }
                val decoded = decoder.decode(sampleSize = sampleSize)
                if (decoded.config == Bitmap.Config.RGBA_F16) {
                    try {
                        decoded.copy(Bitmap.Config.ARGB_8888, false)
                    } finally {
                        decoded.recycle()
                    }
                } else {
                    decoded
                }
            }
        } catch (_: Exception) {
            null
        } catch (_: LinkageError) {
            null
        }
    }

    private fun computeBackgroundDrawable(context: Context, image: Bitmap): Drawable {
        val whiteColor = Color.WHITE
        if (image.width < 50 || image.height < 50) {
            return ColorDrawable(whiteColor)
        }

        val top = 5
        val bot = image.height - 5
        val left = (image.width * 0.0275).toInt()
        val right = image.width - left
        val midX = image.width / 2
        val midY = image.height / 2
        val offsetX = (image.width * 0.01).toInt()
        val leftOffsetX = left - offsetX
        val rightOffsetX = right + offsetX

        val topLeftPixel = image[left, top]
        val topRightPixel = image[right, top]
        val midLeftPixel = image[left, midY]
        val midRightPixel = image[right, midY]
        val topCenterPixel = image[midX, top]
        val botLeftPixel = image[left, bot]
        val bottomCenterPixel = image[midX, bot]
        val botRightPixel = image[right, bot]

        val topLeftIsDark = topLeftPixel.isDark()
        val topRightIsDark = topRightPixel.isDark()
        val midLeftIsDark = midLeftPixel.isDark()
        val midRightIsDark = midRightPixel.isDark()
        val topMidIsDark = topCenterPixel.isDark()
        val botLeftIsDark = botLeftPixel.isDark()
        val botRightIsDark = botRightPixel.isDark()

        var darkBG =
            (
                topLeftIsDark &&
                    (
                        botLeftIsDark ||
                            botRightIsDark ||
                            topRightIsDark ||
                            midLeftIsDark ||
                            topMidIsDark
                        )
                ) ||
                (
                    topRightIsDark &&
                        (
                            botRightIsDark ||
                                botLeftIsDark ||
                                midRightIsDark ||
                                topMidIsDark
                            )
                    )

        val topAndBotPixels = listOf(
            topLeftPixel,
            topCenterPixel,
            topRightPixel,
            botRightPixel,
            bottomCenterPixel,
            botLeftPixel,
        )
        val isNotWhiteAndCloseTo = topAndBotPixels.mapIndexed { index, color ->
            val other = topAndBotPixels[(index + 1) % topAndBotPixels.size]
            !color.isWhite() && color.isCloseTo(other)
        }
        if (isNotWhiteAndCloseTo.all { it }) {
            return ColorDrawable(topLeftPixel)
        }

        val cornerPixels = listOf(topLeftPixel, topRightPixel, botLeftPixel, botRightPixel)
        val numberOfWhiteCorners = cornerPixels.map { cornerPixel -> cornerPixel.isWhite() }
            .filter { it }
            .size
        if (numberOfWhiteCorners > 2) {
            darkBG = false
        }

        var blackColor = when {
            topLeftIsDark -> topLeftPixel
            topRightIsDark -> topRightPixel
            botLeftIsDark -> botLeftPixel
            botRightIsDark -> botRightPixel
            else -> whiteColor
        }

        var overallWhitePixels = 0
        var overallBlackPixels = 0
        var topBlackStreak = 0
        var topWhiteStreak = 0
        var botBlackStreak = 0
        var botWhiteStreak = 0
        outer@ for (x in intArrayOf(left, right, leftOffsetX, rightOffsetX)) {
            var whitePixelsStreak = 0
            var whitePixels = 0
            var blackPixelsStreak = 0
            var blackPixels = 0
            var blackStreak = false
            var whiteStreak = false
            val notOffset = x == left || x == right
            val pixelOffX = (x + (if (x < image.width / 2) -offsetX else offsetX))
                .coerceIn(0, image.width - 1)
            inner@ for ((index, y) in (0..<image.height step image.height / 25).withIndex()) {
                val pixel = image[x, y]
                val pixelOff = image[pixelOffX, y]
                if (pixel.isWhite()) {
                    whitePixelsStreak++
                    whitePixels++
                    if (notOffset) {
                        overallWhitePixels++
                    }
                    if (whitePixelsStreak > 14) {
                        whiteStreak = true
                    }
                    if (whitePixelsStreak > 6 && whitePixelsStreak >= index - 1) {
                        topWhiteStreak = whitePixelsStreak
                    }
                } else {
                    whitePixelsStreak = 0
                    if (pixel.isDark() && pixelOff.isDark()) {
                        blackPixels++
                        if (notOffset) {
                            overallBlackPixels++
                        }
                        blackPixelsStreak++
                        if (blackPixelsStreak >= 14) {
                            blackStreak = true
                        }
                        continue@inner
                    }
                }
                if (blackPixelsStreak > 6 && blackPixelsStreak >= index - 1) {
                    topBlackStreak = blackPixelsStreak
                }
                blackPixelsStreak = 0
            }
            if (blackPixelsStreak > 6) {
                botBlackStreak = blackPixelsStreak
            } else if (whitePixelsStreak > 6) {
                botWhiteStreak = whitePixelsStreak
            }
            when {
                blackPixels > 22 -> {
                    if (x == right || x == rightOffsetX) {
                        blackColor = when {
                            topRightIsDark -> topRightPixel
                            botRightIsDark -> botRightPixel
                            else -> blackColor
                        }
                    }
                    darkBG = true
                    overallWhitePixels = 0
                    break@outer
                }
                blackStreak -> {
                    darkBG = true
                    if (x == right || x == rightOffsetX) {
                        blackColor = when {
                            topRightIsDark -> topRightPixel
                            botRightIsDark -> botRightPixel
                            else -> blackColor
                        }
                    }
                    if (blackPixels > 18) {
                        overallWhitePixels = 0
                        break@outer
                    }
                }
                whiteStreak || whitePixels > 22 -> darkBG = false
            }
        }

        val topIsBlackStreak = topBlackStreak > topWhiteStreak
        val bottomIsBlackStreak = botBlackStreak > botWhiteStreak
        if (overallWhitePixels > 9 && overallWhitePixels > overallBlackPixels) {
            darkBG = false
        }
        if (topIsBlackStreak && bottomIsBlackStreak) {
            darkBG = true
        }

        val isLandscape = context.resources.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) {
            return when {
                darkBG -> ColorDrawable(blackColor)
                else -> ColorDrawable(whiteColor)
            }
        }

        val botCornersIsWhite = botLeftPixel.isWhite() && botRightPixel.isWhite()
        val topCornersIsWhite = topLeftPixel.isWhite() && topRightPixel.isWhite()

        val topCornersIsDark = topLeftIsDark && topRightIsDark
        val botCornersIsDark = botLeftIsDark && botRightIsDark

        val topOffsetCornersIsDark = image[leftOffsetX, top].isDark() && image[rightOffsetX, top].isDark()
        val botOffsetCornersIsDark = image[leftOffsetX, bot].isDark() && image[rightOffsetX, bot].isDark()

        val gradient = when {
            darkBG && botCornersIsWhite -> {
                intArrayOf(blackColor, blackColor, whiteColor, whiteColor)
            }
            darkBG && topCornersIsWhite -> {
                intArrayOf(whiteColor, whiteColor, blackColor, blackColor)
            }
            darkBG -> {
                return ColorDrawable(blackColor)
            }
            topIsBlackStreak ||
                (
                    topCornersIsDark &&
                        topOffsetCornersIsDark &&
                        (
                            topMidIsDark ||
                                overallBlackPixels > 9
                            )
                    ) -> {
                intArrayOf(blackColor, blackColor, whiteColor, whiteColor)
            }
            bottomIsBlackStreak ||
                (
                    botCornersIsDark &&
                        botOffsetCornersIsDark &&
                        (
                            bottomCenterPixel.isDark() ||
                                overallBlackPixels > 9
                            )
                    ) -> {
                intArrayOf(whiteColor, whiteColor, blackColor, blackColor)
            }
            else -> {
                return ColorDrawable(whiteColor)
            }
        }

        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            gradient,
        )
    }

    private fun @receiver:ColorInt Int.isDark(): Boolean =
        red < 40 && blue < 40 && green < 40 && alpha > 200

    private fun @receiver:ColorInt Int.isCloseTo(other: Int): Boolean =
        abs(red - other.red) < 30 && abs(green - other.green) < 30 && abs(blue - other.blue) < 30

    private fun @receiver:ColorInt Int.isWhite(): Boolean =
        red + blue + green > 740

    /**
     * Used to check an image's dimensions without loading it in the memory.
     */
    private fun extractImageOptions(imageSource: BufferedSource): BitmapFactory.Options {
        try {
            val header = ImageHeader.read(imageSource.peek().inputStream())
            if (header?.width != null && header.height != null) {
                return BitmapFactory.Options().apply {
                    outWidth = header.width
                    outHeight = header.height
                }
            }
            return NativeImageDecoder.open(imageSource.peek().inputStream()).use { decoder ->
                BitmapFactory.Options().apply {
                    outWidth = decoder.width
                    outHeight = decoder.height
                }
            }
        } catch (_: Exception) {
            // Keep the platform fallback for formats outside the native build.
        } catch (_: LinkageError) {
            // Keep the platform fallback when native decoder libraries are unavailable (e.g. JVM tests).
        }

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(imageSource.peek().inputStream(), null, options)
        return options
    }

    private fun decodeBitmap(imageSource: BufferedSource): Bitmap {
        val fromCustom = imageSource.peek().use { peeked ->
            decodeBitmapWithCustomDecoder(peeked)
        }
        return fromCustom
            ?: BitmapFactory.decodeStream(imageSource.inputStream())
            ?: error("Failed to decode image")
    }

    private fun decodeBitmapWithCustomDecoder(imageSource: BufferedSource): Bitmap? {
        return try {
            NativeImageDecoder.open(imageSource.inputStream()).use { it.decode() }
        } catch (_: Exception) {
            null
        } catch (_: LinkageError) {
            null
        }
    }

    private val optimalImageHeight = getDisplayMaxHeightInPx * 2

    /**
     * Taken from Coil
     * (https://github.com/coil-kt/coil/blob/1674d3516f061aeacbe749a435b1924f9648fd41/coil-core/src/androidMain/kotlin/coil3/util/hardwareBitmaps.kt)
     * ---
     * Maintains a list of devices with broken/incomplete/unstable hardware bitmap implementations.
     *
     * Model names are retrieved from
     * [Google's official device list](https://support.google.com/googleplay/answer/1727131?hl=en).
     *
     */
    val HARDWARE_BITMAP_UNSUPPORTED = when (Build.VERSION.SDK_INT) {
        26 -> run {
            val model = Build.MODEL ?: return@run false

            // Samsung Galaxy (ALL)
            if (model.removePrefix("SAMSUNG-").startsWith("SM-")) return@run true

            val device = Build.DEVICE ?: return@run false

            return@run device in arrayOf(
                "nora", "nora_8917", "nora_8917_n", // Moto E5
                "james", "rjames_f", "rjames_go", "pettyl", // Moto E5 Play
                "hannah", "ahannah", "rhannah", // Moto E5 Plus

                "ali", "ali_n", // Moto G6
                "aljeter", "aljeter_n", "jeter", // Moto G6 Play
                "evert", "evert_n", "evert_nt", // Moto G6 Plus

                "G3112", "G3116", "G3121", "G3123", "G3125", // Xperia XA1
                "G3412", "G3416", "G3421", "G3423", "G3426", // Xperia XA1 Plus
                "G3212", "G3221", "G3223", "G3226", // Xperia XA1 Ultra

                "BV6800Pro", // BlackView BV6800Pro
                "CatS41", // Cat S41
                "Hi9Pro", // CHUWI Hi9 Pro
                "manning", // Lenovo K8 Note
                "N5702L", // NUU Mobile G3
            )
        }

        27 -> run {
            val device = Build.DEVICE ?: return@run false

            return@run device in arrayOf(
                "mcv1s", // LG Tribute Empire
                "mcv3", // LG K11
                "mcv5a", // LG Q7
                "mcv7a", // LG Stylo 4

                "A30ATMO", // T-Mobile REVVL 2
                "A70AXLTMO", // T-Mobile REVVL 2 PLUS

                "A3A_8_4G_TMO", // Alcatel 9027W
                "Edison_CKT", // Alcatel ONYX
                "EDISON_TF", // Alcatel TCL XL2
                "FERMI_TF", // Alcatel A501DL
                "U50A_ATT", // Alcatel TETRA
                "U50A_PLUS_ATT", // Alcatel 5059R
                "U50A_PLUS_TF", // Alcatel TCL LX
                "U50APLUSTMO", // Alcatel 5059Z
                "U5A_PLUS_4G", // Alcatel 1X

                "RCT6513W87DK5e", // RCA Galileo Pro
                "RCT6873W42BMF9A", // RCA Voyager
                "RCT6A03W13", // RCA 10 Viking
                "RCT6B03W12", // RCA Atlas 10 Pro
                "RCT6B03W13", // RCA Atlas 10 Pro+
                "RCT6T06E13", // RCA Artemis 10

                "A3_Pro", // Umidigi A3 Pro
                "One", // Umidigi One
                "One_Max", // Umidigi One Max
                "One_Pro", // Umidigi One Pro
                "Z2", // Umidigi Z2
                "Z2_PRO", // Umidigi Z2 Pro

                "Armor_3", // Ulefone Armor 3
                "Armor_6", // Ulefone Armor 6

                "Blackview", // Blackview BV6000
                "BV9500", // Blackview BV9500
                "BV9500Pro", // Blackview BV9500Pro

                "A6L-C", // Nuu A6L-C
                "N5002LA", // Nuu A7L
                "N5501LA", // Nuu A5L

                "Power_2_Pro", // Leagoo Power 2 Pro
                "Power_5", // Leagoo Power 5
                "Z9", // Leagoo Z9

                "V0310WW", // Blu VIVO VI+
                "V0330WW", // Blu VIVO XI

                "A3", // BenQ A3
                "ASUS_X018_4", // Asus ZenFone Max Plus M1 (ZB570TL)
                "C210AE", // Wiko Life
                "fireball", // DROID Incredible 4G LTE
                "ILA_X1", // iLA X1
                "Infinix-X605_sprout", // Infinix NOTE 5 Stylus
                "j7maxlte", // Samsung Galaxy J7 Max
                "KING_KONG_3", // Cubot King Kong 3
                "M10500", // Packard Bell M10500
                "S70", // Altice ALTICE S70
                "S80Lite", // Doogee S80Lite
                "SGINO6", // SGiNO 6
                "st18c10bnn", // Barnes and Noble BNTV650
                "TECNO-CA8", // Tecno CAMON X Pro,
                "SHIFT6m", // SHIFT 6m
            )
        }

        else -> false
    }
}

val getDisplayMaxHeightInPx: Int
    get() = Resources.getSystem().displayMetrics.let { max(it.heightPixels, it.widthPixels) }
