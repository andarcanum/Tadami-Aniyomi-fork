package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import tachiyomi.core.common.util.image.NativeImageDecoder
import java.io.InputStream

object WebtoonBorderDetector {

    fun detectContentBounds(bitmap: Bitmap): Rect {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 20 || height <= 20) return Rect(0, 0, width, height)

        val sampleY = computeSampleRows(width, height)
        var leftLimit = width
        var rightLimit = 0

        val cleanBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            bitmap.config == Bitmap.Config.HARDWARE
        ) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return Rect(0, 0, width, height)
        } else {
            bitmap
        }

        try {
            val rowBuffer = IntArray(width)
            for (y in sampleY) {
                if (y !in 0 until height) continue
                cleanBitmap.getPixels(rowBuffer, 0, width, 0, y, width, 1)
                // Skip first/last 5 pixels to avoid 1-pixel borders or edge noise
                val leftColor = rowBuffer[5]
                val rightColor = rowBuffer[width - 6]

                // Scan from left
                for (x in 5 until width - 5) {
                    if (isSignificantDifference(rowBuffer[x], leftColor)) {
                        leftLimit = minOf(leftLimit, x)
                        break
                    }
                }

                // Scan from right
                for (x in width - 6 downTo 5) {
                    if (isSignificantDifference(rowBuffer[x], rightColor)) {
                        rightLimit = maxOf(rightLimit, x + 1)
                        break
                    }
                }
            }
        } finally {
            if (cleanBitmap != bitmap) {
                cleanBitmap.recycle()
            }
        }

        val contentWidth = rightLimit - leftLimit
        // Only apply if the crop is significant (e.g. less than 95% of total width, but more than 10%)
        return if (leftLimit < rightLimit && contentWidth < width * 0.95f && contentWidth > width * 0.10f) {
            Rect(leftLimit, 0, rightLimit, height)
        } else {
            Rect(0, 0, width, height)
        }
    }

    fun detectContentBounds(inputStream: InputStream, cropBorders: Boolean = false): Rect {
        return try {
            NativeImageDecoder.open(inputStream, cropBorders).use { decoder ->
                val width = decoder.width
                val height = decoder.height
                if (width <= 20 || height <= 20) return Rect(0, 0, width, height)
                // NativeImageDecoder trims uniform borders on all SDR images when cropBorders=true;
                // re-scanning already-trimmed edges can misclassify edge artwork as margin.
                if (cropBorders && !decoder.isHdr) {
                    return Rect(0, 0, width, height)
                }
                val sampleY = computeSampleRows(width, height)
                val rowBuffer = IntArray(width)
                var leftLimit = width
                var rightLimit = 0
                for (y in sampleY) {
                    val row = decoder.decode(Rect(0, y, width, y + 1))
                    try {
                        row.getPixels(rowBuffer, 0, width, 0, 0, width, 1)
                        val leftColor = rowBuffer[5]
                        val rightColor = rowBuffer[width - 6]
                        for (x in 5 until width - 5) {
                            if (isSignificantDifference(rowBuffer[x], leftColor)) {
                                leftLimit = minOf(leftLimit, x)
                                break
                            }
                        }
                        for (x in width - 6 downTo 5) {
                            if (isSignificantDifference(rowBuffer[x], rightColor)) {
                                rightLimit = maxOf(rightLimit, x + 1)
                                break
                            }
                        }
                    } finally {
                        row.recycle()
                    }
                }
                val contentWidth = rightLimit - leftLimit
                if (leftLimit < rightLimit && contentWidth < width * 0.95f && contentWidth > width * 0.10f) {
                    Rect(leftLimit, 0, rightLimit, height)
                } else {
                    Rect(0, 0, width, height)
                }
            }
        } catch (_: Exception) {
            Rect()
        }
    }

    /**
     * Scales sample row density with vertical aspect ratio (5 rows on standard pages up to 17 on
     * tall webtoon strips) so wide panels and protruding speech bubbles between coarse bands are
     * never clipped off.
     */
    internal fun computeSampleRows(width: Int, height: Int): List<Int> {
        val aspect = (height / width.coerceAtLeast(1)).coerceAtLeast(1)
        val count = if (aspect <= 2) 5 else (aspect + 3).coerceIn(7, 17)
        val startY = (height * 0.05f).toInt().coerceIn(0, (height - 1).coerceAtLeast(0))
        val endY = (height * 0.95f).toInt().coerceIn(startY, (height - 1).coerceAtLeast(0))
        if (count <= 1 || endY <= startY) return listOf(height / 2)
        val span = endY - startY
        return List(count) { idx ->
            startY + (span * idx) / (count - 1)
        }
    }

    private fun isSignificantDifference(c1: Int, c2: Int): Boolean {
        val r = Math.abs(Color.red(c1) - Color.red(c2))
        val g = Math.abs(Color.green(c1) - Color.green(c2))
        val b = Math.abs(Color.blue(c1) - Color.blue(c2))
        return (r + g + b) > 30
    }
}
