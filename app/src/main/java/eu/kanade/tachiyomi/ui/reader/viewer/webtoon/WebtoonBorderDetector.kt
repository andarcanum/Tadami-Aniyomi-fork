package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import java.io.InputStream

object WebtoonBorderDetector {

    fun detectContentBounds(bitmap: Bitmap): Rect {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 20 || height <= 20) return Rect(0, 0, width, height)

        val sampleY = listOf(height / 10, height / 3, height / 2, height * 2 / 3, height * 9 / 10)
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
            for (y in sampleY) {
                if (y >= height) continue
                // Skip first/last 5 pixels to avoid 1-pixel borders or edge noise
                val leftColor = cleanBitmap.getPixel(5, y)
                val rightColor = cleanBitmap.getPixel(width - 6, y)

                // Scan from left
                for (x in 5 until width - 5) {
                    if (isSignificantDifference(cleanBitmap.getPixel(x, y), leftColor)) {
                        leftLimit = minOf(leftLimit, x)
                        break
                    }
                }

                // Scan from right
                for (x in width - 6 downTo 5) {
                    if (isSignificantDifference(cleanBitmap.getPixel(x, y), rightColor)) {
                        rightLimit = maxOf(rightLimit, x)
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

    fun detectContentBounds(inputStream: InputStream): Rect {
        return try {
            val decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BitmapRegionDecoder.newInstance(inputStream)
            } else {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(inputStream, false)
            } ?: return Rect()

            val width = decoder.width
            val height = decoder.height
            if (width <= 20 || height <= 20) return Rect(0, 0, width, height)

            val sampleY = listOf(height / 10, height / 3, height / 2, height * 2 / 3, height * 9 / 10)
            var leftLimit = width
            var rightLimit = 0

            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            for (y in sampleY) {
                if (y >= height) continue
                val rect = Rect(0, y, width, y + 1)
                val rowBitmap = decoder.decodeRegion(rect, options) ?: continue

                // Skip first/last 5 pixels to avoid 1-pixel borders or edge noise
                val leftColor = rowBitmap.getPixel(5, 0)
                val rightColor = rowBitmap.getPixel(width - 6, 0)

                for (x in 5 until width - 5) {
                    if (isSignificantDifference(rowBitmap.getPixel(x, 0), leftColor)) {
                        leftLimit = minOf(leftLimit, x)
                        break
                    }
                }

                for (x in width - 6 downTo 5) {
                    if (isSignificantDifference(rowBitmap.getPixel(x, 0), rightColor)) {
                        rightLimit = maxOf(rightLimit, x)
                        break
                    }
                }
                rowBitmap.recycle()
            }
            decoder.recycle()

            val contentWidth = rightLimit - leftLimit
            if (leftLimit < rightLimit && contentWidth < width * 0.95f && contentWidth > width * 0.10f) {
                Rect(leftLimit, 0, rightLimit, height)
            } else {
                Rect(0, 0, width, height)
            }
        } catch (e: Exception) {
            Rect()
        } finally {
            try {
                inputStream.close()
            } catch (ignored: Exception) {}
        }
    }

    private fun isSignificantDifference(c1: Int, c2: Int): Boolean {
        val r = Math.abs(Color.red(c1) - Color.red(c2))
        val g = Math.abs(Color.green(c1) - Color.green(c2))
        val b = Math.abs(Color.blue(c1) - Color.blue(c2))
        return (r + g + b) > 30
    }
}
