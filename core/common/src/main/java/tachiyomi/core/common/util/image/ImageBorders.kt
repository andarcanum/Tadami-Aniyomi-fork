package tachiyomi.core.common.util.image

import android.graphics.Rect
import kotlin.math.roundToInt

/**
 * Kotlin port of Mihon's Apache-2.0 border detector (SSIV 72a0537151, crop/borders.cpp).
 * Operates on a bounded preview. Rect right/bottom are exclusive, including the last content pixel.
 */
object ImageBorders {
    fun find(pixels: IntArray, width: Int, height: Int): Rect {
        require(width > 0 && height > 0 && width.toLong() * height <= pixels.size)
        fun luma(x: Int, y: Int): Int {
            val pixel = pixels[y * width + x]
            return (((pixel ushr 16 and 255) * 299 + (pixel ushr 8 and 255) * 587 + (pixel and 255) * 114) / 1000)
        }
        fun border(horizontal: Boolean, reverse: Boolean, start: Int, end: Int): Int {
            val length = if (horizontal) height else width
            val crossLength = if (horizontal) width else height
            val limit = (crossLength * 0.0025 / 2).roundToInt()
            fun value(line: Int, cross: Int) = if (horizontal) luma(cross, line) else luma(line, cross)
            val edge = if (reverse) length - 1 else 0
            var black = 0
            var white = 0
            for (cross in start until end step 2) {
                val v = value(edge, cross)
                if (v < 191) {
                    black++
                } else if (v > 63) {
                    white++
                }
            }
            val fallback = if (reverse) length else 0
            if (white > limit && black > limit) return fallback
            val blackBorder = black > limit
            val range = if (reverse) (length - 2 downTo 1) else (1 until length)
            for (line in range) {
                var fill = 0
                for (cross in start until end step 2) {
                    val v = value(line, cross)
                    if (if (blackBorder) v > 63 else v < 191) fill++
                }
                if (fill > limit) return if (reverse) line + 1 else line
            }
            return fallback
        }
        val top = border(horizontal = true, reverse = false, start = 0, end = width)
        val bottom = border(horizontal = true, reverse = true, start = 0, end = width)
        val left = border(horizontal = false, reverse = false, start = top, end = bottom)
        val right = border(horizontal = false, reverse = true, start = top, end = bottom)
        return if (right > left && bottom > top) Rect(left, top, right, bottom) else Rect(0, 0, width, height)
    }
}
