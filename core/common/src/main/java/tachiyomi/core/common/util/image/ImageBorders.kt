package tachiyomi.core.common.util.image

import android.graphics.Rect
import kotlin.math.roundToInt

/**
 * Finds symmetric ink-free margins along the four sides of a page.
 *
 * Scans inward from each outer edge (top, bottom, left, right) until a scan line contains more
 * non-border pixels than the tolerance threshold (`0.125%` of the orthogonal span), treating uniform
 * dark, light, or transparent margins as removable borders.
 */
object ImageBorders {
    private const val DEFAULT_BAND_SIZE = 64
    private const val MAX_STRIP_PIXELS = 262_144
    private const val TRANSPARENT_ALPHA_MAX = 15
    private const val BLACK_LUMA_MAX = 190
    private const val WHITE_LUMA_MIN = 64

    private enum class BorderKind {
        BLACK,
        WHITE,
        TRANSPARENT,
    }

    private data class EdgeCounts(
        var black: Int = 0,
        var white: Int = 0,
        var transparent: Int = 0,
    )

    fun find(pixels: IntArray, width: Int, height: Int): Rect {
        require(width > 0 && height > 0 && width.toLong() * height <= pixels.size)
        val top = scanInMemory(pixels, width, height, horizontal = true, reverse = false, start = 0, end = width)
        val bottom = scanInMemory(pixels, width, height, horizontal = true, reverse = true, start = 0, end = width)
        if (bottom <= top) return Rect(0, 0, width, height)
        val left = scanInMemory(pixels, width, height, horizontal = false, reverse = false, start = top, end = bottom)
        val right = scanInMemory(pixels, width, height, horizontal = false, reverse = true, start = top, end = bottom)
        return if (right > left && bottom > top) Rect(left, top, right, bottom) else Rect(0, 0, width, height)
    }

    /**
     * Strip-based overload of [find] that scans only thin edge bands via [readStrip], keeping memory
     * bounded regardless of full image dimensions while producing identical crop bounds.
     */
    fun find(
        width: Int,
        height: Int,
        readStrip: (region: Rect, buffer: IntArray) -> Unit,
    ): Rect {
        require(width > 0 && height > 0)
        val buffer = IntArray(minOf(MAX_STRIP_PIXELS.toLong(), width.toLong() * height).toInt())
        val top = scanTop(width, height, buffer, readStrip)
        val bottom = scanBottom(width, height, buffer, readStrip)
        if (bottom <= top) return Rect(0, 0, width, height)
        val left = scanLeft(width, top, bottom, buffer, readStrip)
        val right = scanRight(width, top, bottom, buffer, readStrip)
        return if (right > left && bottom > top) Rect(left, top, right, bottom) else Rect(0, 0, width, height)
    }

    private fun scanInMemory(
        pixels: IntArray,
        width: Int,
        height: Int,
        horizontal: Boolean,
        reverse: Boolean,
        start: Int,
        end: Int,
    ): Int {
        val length = if (horizontal) height else width
        val crossLength = if (horizontal) width else height
        val fallback = if (reverse) length else 0
        if (start >= end || length <= 0 || crossLength <= 0) return fallback
        val limit = noiseLimit(crossLength)

        fun countEdge(line: Int): EdgeCounts {
            val counts = EdgeCounts()
            for (cross in start until end step 2) {
                val pixel = if (horizontal) pixels[line * width + cross] else pixels[cross * width + line]
                accumulateEdge(pixel, counts)
            }
            return counts
        }

        val edge = if (reverse) length - 1 else 0
        val borderKind = resolveBorderKind(countEdge(edge), limit) ?: return fallback
        val range = if (reverse) (length - 2 downTo 0) else (1 until length)
        for (line in range) {
            var fill = 0
            for (cross in start until end step 2) {
                val pixel = if (horizontal) pixels[line * width + cross] else pixels[cross * width + line]
                if (isContentPixel(pixel, borderKind)) fill++
            }
            if (fill > limit) return if (reverse) line + 1 else line
        }
        return fallback
    }

    private fun scanTop(
        width: Int,
        height: Int,
        buffer: IntArray,
        readStrip: (Rect, IntArray) -> Unit,
    ): Int {
        val limit = noiseLimit(width)
        var borderKind: BorderKind? = null
        val stepRows = horizontalBandRows(width)
        var y0 = 0
        while (y0 < height) {
            val y1 = minOf(y0 + stepRows, height)
            val rows = y1 - y0
            if (width <= MAX_STRIP_PIXELS) {
                readStrip(Rect(0, y0, width, y1), buffer)
                for (row in 0 until rows) {
                    val rowOffset = row * width
                    if (borderKind == null) {
                        val counts = EdgeCounts()
                        for (x in 0 until width step 2) {
                            accumulateEdge(buffer[rowOffset + x], counts)
                        }
                        borderKind = resolveBorderKind(counts, limit) ?: return 0
                    } else {
                        var fill = 0
                        for (x in 0 until width step 2) {
                            if (isContentPixel(buffer[rowOffset + x], borderKind)) fill++
                        }
                        if (fill > limit) return y0 + row
                    }
                }
            } else {
                // Extreme width: split rows into even-width horizontal chunks so step-2 parity matches global x.
                val xChunk = evenMaxChunk()
                for (y in y0 until y1) {
                    if (borderKind == null) {
                        val counts = EdgeCounts()
                        var x0 = 0
                        while (x0 < width) {
                            val x1 = minOf(x0 + xChunk, width)
                            readStrip(Rect(x0, y, x1, y + 1), buffer)
                            for (dx in 0 until (x1 - x0) step 2) {
                                accumulateEdge(buffer[dx], counts)
                            }
                            x0 = x1
                        }
                        borderKind = resolveBorderKind(counts, limit) ?: return 0
                    } else {
                        var fill = 0
                        var x0 = 0
                        while (x0 < width) {
                            val x1 = minOf(x0 + xChunk, width)
                            readStrip(Rect(x0, y, x1, y + 1), buffer)
                            for (dx in 0 until (x1 - x0) step 2) {
                                if (isContentPixel(buffer[dx], borderKind)) fill++
                            }
                            x0 = x1
                        }
                        if (fill > limit) return y
                    }
                }
            }
            y0 = y1
        }
        return 0
    }

    private fun scanBottom(
        width: Int,
        height: Int,
        buffer: IntArray,
        readStrip: (Rect, IntArray) -> Unit,
    ): Int {
        val limit = noiseLimit(width)
        var borderKind: BorderKind? = null
        val stepRows = horizontalBandRows(width)
        var y1 = height
        while (y1 > 0) {
            val y0 = maxOf(0, y1 - stepRows)
            val rows = y1 - y0
            if (width <= MAX_STRIP_PIXELS) {
                readStrip(Rect(0, y0, width, y1), buffer)
                for (row in rows - 1 downTo 0) {
                    val rowOffset = row * width
                    if (borderKind == null) {
                        val counts = EdgeCounts()
                        for (x in 0 until width step 2) {
                            accumulateEdge(buffer[rowOffset + x], counts)
                        }
                        borderKind = resolveBorderKind(counts, limit) ?: return height
                    } else {
                        var fill = 0
                        for (x in 0 until width step 2) {
                            if (isContentPixel(buffer[rowOffset + x], borderKind)) fill++
                        }
                        if (fill > limit) return y0 + row + 1
                    }
                }
            } else {
                val xChunk = evenMaxChunk()
                for (y in y1 - 1 downTo y0) {
                    if (borderKind == null) {
                        val counts = EdgeCounts()
                        var x0 = 0
                        while (x0 < width) {
                            val x1 = minOf(x0 + xChunk, width)
                            readStrip(Rect(x0, y, x1, y + 1), buffer)
                            for (dx in 0 until (x1 - x0) step 2) {
                                accumulateEdge(buffer[dx], counts)
                            }
                            x0 = x1
                        }
                        borderKind = resolveBorderKind(counts, limit) ?: return height
                    } else {
                        var fill = 0
                        var x0 = 0
                        while (x0 < width) {
                            val x1 = minOf(x0 + xChunk, width)
                            readStrip(Rect(x0, y, x1, y + 1), buffer)
                            for (dx in 0 until (x1 - x0) step 2) {
                                if (isContentPixel(buffer[dx], borderKind)) fill++
                            }
                            x0 = x1
                        }
                        if (fill > limit) return y + 1
                    }
                }
            }
            y1 = y0
        }
        return height
    }

    private fun scanLeft(
        width: Int,
        top: Int,
        bottom: Int,
        buffer: IntArray,
        readStrip: (Rect, IntArray) -> Unit,
    ): Int {
        val spanH = bottom - top
        if (spanH <= 0) return 0
        val limit = noiseLimit(spanH)
        var borderKind: BorderKind? = null
        val stepCols = verticalBandCols(spanH)
        var x0 = 0
        while (x0 < width) {
            val x1 = minOf(x0 + stepCols, width)
            val cols = x1 - x0
            if (spanH <= MAX_STRIP_PIXELS) {
                readStrip(Rect(x0, top, x1, bottom), buffer)
                for (col in 0 until cols) {
                    if (borderKind == null) {
                        val counts = EdgeCounts()
                        for (dy in 0 until spanH step 2) {
                            accumulateEdge(buffer[dy * cols + col], counts)
                        }
                        borderKind = resolveBorderKind(counts, limit) ?: return 0
                    } else {
                        var fill = 0
                        for (dy in 0 until spanH step 2) {
                            if (isContentPixel(buffer[dy * cols + col], borderKind)) fill++
                        }
                        if (fill > limit) return x0 + col
                    }
                }
            } else {
                // Extreme height (>262K px): split into even-height vertical chunks so step-2 parity matches top.
                val yChunk = evenMaxChunk()
                for (x in x0 until x1) {
                    if (borderKind == null) {
                        val counts = EdgeCounts()
                        var y0 = top
                        while (y0 < bottom) {
                            val y1 = minOf(y0 + yChunk, bottom)
                            readStrip(Rect(x, y0, x + 1, y1), buffer)
                            for (dy in 0 until (y1 - y0) step 2) {
                                accumulateEdge(buffer[dy], counts)
                            }
                            y0 = y1
                        }
                        borderKind = resolveBorderKind(counts, limit) ?: return 0
                    } else {
                        var fill = 0
                        var y0 = top
                        while (y0 < bottom) {
                            val y1 = minOf(y0 + yChunk, bottom)
                            readStrip(Rect(x, y0, x + 1, y1), buffer)
                            for (dy in 0 until (y1 - y0) step 2) {
                                if (isContentPixel(buffer[dy], borderKind)) fill++
                            }
                            y0 = y1
                        }
                        if (fill > limit) return x
                    }
                }
            }
            x0 = x1
        }
        return 0
    }

    private fun scanRight(
        width: Int,
        top: Int,
        bottom: Int,
        buffer: IntArray,
        readStrip: (Rect, IntArray) -> Unit,
    ): Int {
        val spanH = bottom - top
        if (spanH <= 0) return width
        val limit = noiseLimit(spanH)
        var borderKind: BorderKind? = null
        val stepCols = verticalBandCols(spanH)
        var x1 = width
        while (x1 > 0) {
            val x0 = maxOf(0, x1 - stepCols)
            val cols = x1 - x0
            if (spanH <= MAX_STRIP_PIXELS) {
                readStrip(Rect(x0, top, x1, bottom), buffer)
                for (col in cols - 1 downTo 0) {
                    if (borderKind == null) {
                        val counts = EdgeCounts()
                        for (dy in 0 until spanH step 2) {
                            accumulateEdge(buffer[dy * cols + col], counts)
                        }
                        borderKind = resolveBorderKind(counts, limit) ?: return width
                    } else {
                        var fill = 0
                        for (dy in 0 until spanH step 2) {
                            if (isContentPixel(buffer[dy * cols + col], borderKind)) fill++
                        }
                        if (fill > limit) return x0 + col + 1
                    }
                }
            } else {
                val yChunk = evenMaxChunk()
                for (x in x1 - 1 downTo x0) {
                    if (borderKind == null) {
                        val counts = EdgeCounts()
                        var y0 = top
                        while (y0 < bottom) {
                            val y1 = minOf(y0 + yChunk, bottom)
                            readStrip(Rect(x, y0, x + 1, y1), buffer)
                            for (dy in 0 until (y1 - y0) step 2) {
                                accumulateEdge(buffer[dy], counts)
                            }
                            y0 = y1
                        }
                        borderKind = resolveBorderKind(counts, limit) ?: return width
                    } else {
                        var fill = 0
                        var y0 = top
                        while (y0 < bottom) {
                            val y1 = minOf(y0 + yChunk, bottom)
                            readStrip(Rect(x, y0, x + 1, y1), buffer)
                            for (dy in 0 until (y1 - y0) step 2) {
                                if (isContentPixel(buffer[dy], borderKind)) fill++
                            }
                            y0 = y1
                        }
                        if (fill > limit) return x + 1
                    }
                }
            }
            x1 = x0
        }
        return width
    }

    private fun noiseLimit(crossLength: Int): Int =
        (crossLength * 0.0025 / 2).roundToInt().coerceAtLeast(0)

    private fun horizontalBandRows(width: Int): Int =
        minOf(DEFAULT_BAND_SIZE, (MAX_STRIP_PIXELS / width).coerceAtLeast(1))

    private fun verticalBandCols(spanH: Int): Int =
        minOf(DEFAULT_BAND_SIZE, (MAX_STRIP_PIXELS / spanH).coerceAtLeast(1))

    private fun evenMaxChunk(): Int = MAX_STRIP_PIXELS and 1.inv()

    private fun accumulateEdge(pixel: Int, counts: EdgeCounts) {
        val alpha = pixel ushr 24 and 255
        if (alpha <= TRANSPARENT_ALPHA_MAX) {
            counts.transparent++
            return
        }
        val v = luma(pixel)
        if (v <= BLACK_LUMA_MAX) counts.black++
        if (v >= WHITE_LUMA_MIN) counts.white++
    }

    private fun resolveBorderKind(counts: EdgeCounts, limit: Int): BorderKind? {
        val isBlack = counts.black > limit
        val isWhite = counts.white > limit
        val isTransparent = counts.transparent > limit
        val activeKinds = (if (isBlack) 1 else 0) + (if (isWhite) 1 else 0) + (if (isTransparent) 1 else 0)
        if (activeKinds != 1) return null
        return when {
            isTransparent -> BorderKind.TRANSPARENT
            isBlack -> BorderKind.BLACK
            else -> BorderKind.WHITE
        }
    }

    private fun isContentPixel(pixel: Int, borderKind: BorderKind): Boolean {
        val alpha = pixel ushr 24 and 255
        val transparent = alpha <= TRANSPARENT_ALPHA_MAX
        return when (borderKind) {
            BorderKind.TRANSPARENT -> !transparent
            BorderKind.BLACK -> transparent || luma(pixel) >= WHITE_LUMA_MIN
            BorderKind.WHITE -> transparent || luma(pixel) <= BLACK_LUMA_MAX
        }
    }

    private fun luma(pixel: Int): Int =
        ((pixel ushr 16 and 255) * 299 + (pixel ushr 8 and 255) * 587 + (pixel and 255) * 114) / 1000
}
