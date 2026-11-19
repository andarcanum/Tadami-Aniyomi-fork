package eu.kanade.tachiyomi.ui.reader.viewer.panel

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * Normalized [0f..1f] bounding box for a detected manga/comic panel on a page.
 */
data class PanelRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f
    val area: Float get() = width * height
}

/**
 * Viewport-space camera target for smoothly animating [SubsamplingScaleImageView] onto a panel.
 */
data class PanelCameraTarget(
    val scale: Float,
    val centerX: Float,
    val centerY: Float,
)

/**
 * Fast, pure-Kotlin manga panel detector using adaptive gutter polarity detection and recursive
 * XY-cut projection. Operates on a compact downsampled pixel buffer (~320px wide) so detection
 * completes in a few milliseconds without allocating large intermediate bitmaps.
 */
object MangaPanelDetector {

    private const val MAX_ANALYSIS_WIDTH = 320
    private const val MAX_RECURSION_DEPTH = 4
    private const val MAX_PANELS = 16

    private const val LIGHT_GUTTER_LUMA = 210
    private const val DARK_GUTTER_LUMA = 45
    private const val ALPHA_TRANSPARENT_THRESHOLD = 128

    private const val GUTTER_LINE_MATCH_RATIO = 0.94f
    private const val TRIM_LINE_MATCH_RATIO = 0.96f

    private const val MIN_PANEL_WIDTH_FRACTION = 0.11f
    private const val MIN_PANEL_HEIGHT_FRACTION = 0.09f
    private const val MIN_PANEL_AREA_FRACTION = 0.018f
    private const val MIN_TOTAL_COVERAGE_FRACTION = 0.30f
    private const val PANEL_BREATHING_PADDING = 0.015f

    private data class IntBox(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    /**
     * Detects reading-ordered panels from an Android [Bitmap].
     * Never recycles the caller's [bitmap].
     */
    fun detectFromBitmap(bitmap: Bitmap, isRtl: Boolean): List<PanelRect> {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return emptyList()

        val sampleBitmap: Bitmap
        val ownsCopy: Boolean
        if (bitmap.width > MAX_ANALYSIS_WIDTH) {
            val targetW = MAX_ANALYSIS_WIDTH
            val targetH = max(1, (bitmap.height.toLong() * targetW / bitmap.width).toInt())
            sampleBitmap = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            ownsCopy = sampleBitmap !== bitmap
        } else {
            sampleBitmap = bitmap
            ownsCopy = false
        }

        return try {
            val w = sampleBitmap.width
            val h = sampleBitmap.height
            val pixels = IntArray(w * h)
            sampleBitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            detectFromPixels(pixels = pixels, width = w, height = h, isRtl = isRtl)
        } finally {
            if (ownsCopy && !sampleBitmap.isRecycled) {
                sampleBitmap.recycle()
            }
        }
    }

    /**
     * Pure-JVM entry point operating directly on ARGB [pixels].
     * Returns reading-ordered [PanelRect]s, or an empty list if the page is a single splash/bleed
     * illustration that should remain in full-page view.
     */
    fun detectFromPixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        isRtl: Boolean,
    ): List<PanelRect> {
        if (width < 24 || height < 24 || pixels.size < width * height) return emptyList()

        val luma = ByteArray(width * height)
        val transparent = BooleanArray(width * height)
        for (i in 0 until width * height) {
            val argb = pixels[i]
            val a = (argb ushr 24) and 0xFF
            if (a < ALPHA_TRANSPARENT_THRESHOLD) {
                transparent[i] = true
                luma[i] = 0
            } else {
                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF
                luma[i] = ((77 * r + 150 * g + 29 * b) ushr 8).toByte()
            }
        }

        val isLightGutter = detectLightGutterPolarity(luma, transparent, width, height)
        val isGutter = BooleanArray(width * height) { idx ->
            if (transparent[idx]) {
                true
            } else {
                val y = luma[idx].toInt() and 0xFF
                if (isLightGutter) y >= LIGHT_GUTTER_LUMA else y <= DARK_GUTTER_LUMA
            }
        }

        val minGutterW = max(2, (width * 0.012f).toInt())
        val minGutterH = max(2, (height * 0.012f).toInt())
        val minPanelW = max(10, (width * MIN_PANEL_WIDTH_FRACTION).toInt())
        val minPanelH = max(10, (height * MIN_PANEL_HEIGHT_FRACTION).toInt())

        val root = trimBox(
            box = IntBox(0, 0, width, height),
            isGutter = isGutter,
            stride = width,
            minW = minPanelW,
            minH = minPanelH,
        ) ?: return emptyList()

        val rawLeaves = ArrayList<IntBox>(8)
        splitRecursive(
            box = root,
            depth = 0,
            isRtl = isRtl,
            isGutter = isGutter,
            stride = width,
            minGutterW = minGutterW,
            minGutterH = minGutterH,
            minPanelW = minPanelW,
            minPanelH = minPanelH,
            out = rawLeaves,
        )

        if (rawLeaves.size < 2) return emptyList()

        val invW = 1f / width.toFloat()
        val invH = 1f / height.toFloat()
        val panels = rawLeaves.mapNotNull { box ->
            val left = (box.left * invW - PANEL_BREATHING_PADDING).coerceIn(0f, 1f)
            val top = (box.top * invH - PANEL_BREATHING_PADDING).coerceIn(0f, 1f)
            val right = (box.right * invW + PANEL_BREATHING_PADDING).coerceIn(0f, 1f)
            val bottom = (box.bottom * invH + PANEL_BREATHING_PADDING).coerceIn(0f, 1f)
            val rect = PanelRect(left, top, right, bottom)
            if (
                rect.width < MIN_PANEL_WIDTH_FRACTION ||
                rect.height < MIN_PANEL_HEIGHT_FRACTION ||
                rect.area < MIN_PANEL_AREA_FRACTION
            ) {
                null
            } else {
                rect
            }
        }

        if (panels.size < 2 || panels.size > MAX_PANELS) return emptyList()

        val totalArea = panels.fold(0f) { acc, r -> acc + r.area }
        if (totalArea < MIN_TOTAL_COVERAGE_FRACTION) return emptyList()

        return panels
    }

    /**
     * Computes the scale and source-pixel center required to comfortably frame [panel] inside the
     * viewport with [paddingFraction] breathing room on each side.
     */
    fun computeCameraTarget(
        panel: PanelRect,
        sourceWidth: Int,
        sourceHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
        minScale: Float,
        maxScale: Float,
        paddingFraction: Float = 0.05f,
    ): PanelCameraTarget? {
        if (sourceWidth <= 0 || sourceHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return null
        val panelPxW = panel.width * sourceWidth
        val panelPxH = panel.height * sourceHeight
        if (panelPxW <= 1f || panelPxH <= 1f) return null

        val usableFraction = (1f - 2f * paddingFraction).coerceIn(0.5f, 1f)
        val targetScaleX = (viewWidth * usableFraction) / panelPxW
        val targetScaleY = (viewHeight * usableFraction) / panelPxH
        val clampedScale = min(targetScaleX, targetScaleY).coerceIn(
            minimumValue = minScale.coerceAtLeast(0.01f),
            maximumValue = max(minScale, maxScale),
        )

        return PanelCameraTarget(
            scale = clampedScale,
            centerX = (panel.centerX * sourceWidth).coerceIn(0f, sourceWidth.toFloat()),
            centerY = (panel.centerY * sourceHeight).coerceIn(0f, sourceHeight.toFloat()),
        )
    }

    private fun detectLightGutterPolarity(
        luma: ByteArray,
        transparent: BooleanArray,
        width: Int,
        height: Int,
    ): Boolean {
        var lightCount = 0
        var darkCount = 0
        var opaqueCount = 0

        fun sample(x: Int, y: Int) {
            val idx = y * width + x
            if (transparent[idx]) return
            opaqueCount++
            val v = luma[idx].toInt() and 0xFF
            if (v >= LIGHT_GUTTER_LUMA) lightCount++
            if (v <= DARK_GUTTER_LUMA) darkCount++
        }

        val border = min(3, min(width, height) / 4).coerceAtLeast(1)
        for (y in 0 until border) {
            for (x in 0 until width) {
                sample(x, y)
                sample(x, height - 1 - y)
            }
        }
        for (y in border until (height - border)) {
            for (x in 0 until border) {
                sample(x, y)
                sample(width - 1 - x, y)
            }
        }

        if (opaqueCount == 0) return true
        if (lightCount >= darkCount) return true
        return darkCount * 100 < opaqueCount * 55
    }

    private fun splitRecursive(
        box: IntBox,
        depth: Int,
        isRtl: Boolean,
        isGutter: BooleanArray,
        stride: Int,
        minGutterW: Int,
        minGutterH: Int,
        minPanelW: Int,
        minPanelH: Int,
        out: MutableList<IntBox>,
    ) {
        if (depth >= MAX_RECURSION_DEPTH || out.size >= MAX_PANELS) {
            out.add(box)
            return
        }

        // Prefer horizontal row cuts first (natural manga strip hierarchy: top -> bottom).
        val hSplits = findHorizontalSplits(box, isGutter, stride, minGutterH, minPanelH)
        if (hSplits.size > 1) {
            for (sub in hSplits) {
                val trimmed = trimBox(sub, isGutter, stride, minPanelW, minPanelH) ?: continue
                splitRecursive(
                    box = trimmed,
                    depth = depth + 1,
                    isRtl = isRtl,
                    isGutter = isGutter,
                    stride = stride,
                    minGutterW = minGutterW,
                    minGutterH = minGutterH,
                    minPanelW = minPanelW,
                    minPanelH = minPanelH,
                    out = out,
                )
            }
            return
        }

        // Otherwise split vertically into columns, respecting RTL (manga) vs LTR (western comics).
        val vSplits = findVerticalSplits(box, isGutter, stride, minGutterW, minPanelW)
        if (vSplits.size > 1) {
            val ordered = if (isRtl) vSplits.asReversed() else vSplits
            for (sub in ordered) {
                val trimmed = trimBox(sub, isGutter, stride, minPanelW, minPanelH) ?: continue
                splitRecursive(
                    box = trimmed,
                    depth = depth + 1,
                    isRtl = isRtl,
                    isGutter = isGutter,
                    stride = stride,
                    minGutterW = minGutterW,
                    minGutterH = minGutterH,
                    minPanelW = minPanelW,
                    minPanelH = minPanelH,
                    out = out,
                )
            }
            return
        }

        out.add(box)
    }

    private fun findHorizontalSplits(
        box: IntBox,
        isGutter: BooleanArray,
        stride: Int,
        minGutterH: Int,
        minPanelH: Int,
    ): List<IntBox> {
        val rowThreshold = (box.width * GUTTER_LINE_MATCH_RATIO).toInt().coerceAtLeast(1)
        val cuts = ArrayList<Int>()

        var runStart = -1
        for (y in box.top until box.bottom) {
            val rowOffset = y * stride
            var gutterCount = 0
            for (x in box.left until box.right) {
                if (isGutter[rowOffset + x]) gutterCount++
            }
            val isGutterRow = gutterCount >= rowThreshold
            if (isGutterRow) {
                if (runStart < 0) runStart = y
            } else if (runStart >= 0) {
                val runLen = y - runStart
                if (runLen >= minGutterH && runStart > box.top && y < box.bottom) {
                    cuts.add((runStart + y) / 2)
                }
                runStart = -1
            }
        }

        if (cuts.isEmpty()) return listOf(box)

        val regions = ArrayList<IntBox>(cuts.size + 1)
        var currentTop = box.top
        for (cutY in cuts) {
            if (cutY - currentTop >= minPanelH && box.bottom - cutY >= minPanelH) {
                regions.add(IntBox(box.left, currentTop, box.right, cutY))
                currentTop = cutY
            }
        }
        if (regions.isEmpty()) return listOf(box)
        regions.add(IntBox(box.left, currentTop, box.right, box.bottom))
        return regions
    }

    private fun findVerticalSplits(
        box: IntBox,
        isGutter: BooleanArray,
        stride: Int,
        minGutterW: Int,
        minPanelW: Int,
    ): List<IntBox> {
        val colThreshold = (box.height * GUTTER_LINE_MATCH_RATIO).toInt().coerceAtLeast(1)
        val cuts = ArrayList<Int>()

        var runStart = -1
        for (x in box.left until box.right) {
            var gutterCount = 0
            for (y in box.top until box.bottom) {
                if (isGutter[y * stride + x]) gutterCount++
            }
            val isGutterCol = gutterCount >= colThreshold
            if (isGutterCol) {
                if (runStart < 0) runStart = x
            } else if (runStart >= 0) {
                val runLen = x - runStart
                if (runLen >= minGutterW && runStart > box.left && x < box.right) {
                    cuts.add((runStart + x) / 2)
                }
                runStart = -1
            }
        }

        if (cuts.isEmpty()) return listOf(box)

        val regions = ArrayList<IntBox>(cuts.size + 1)
        var currentLeft = box.left
        for (cutX in cuts) {
            if (cutX - currentLeft >= minPanelW && box.right - cutX >= minPanelW) {
                regions.add(IntBox(currentLeft, box.top, cutX, box.bottom))
                currentLeft = cutX
            }
        }
        if (regions.isEmpty()) return listOf(box)
        regions.add(IntBox(currentLeft, box.top, box.right, box.bottom))
        return regions
    }

    private fun trimBox(
        box: IntBox,
        isGutter: BooleanArray,
        stride: Int,
        minW: Int,
        minH: Int,
    ): IntBox? {
        var left = box.left
        var right = box.right
        var top = box.top
        var bottom = box.bottom

        fun rowIsGutter(y: Int): Boolean {
            val w = right - left
            if (w <= 0) return true
            val threshold = (w * TRIM_LINE_MATCH_RATIO).toInt().coerceAtLeast(1)
            val offset = y * stride
            var count = 0
            for (x in left until right) {
                if (isGutter[offset + x]) count++
            }
            return count >= threshold
        }

        fun colIsGutter(x: Int): Boolean {
            val h = bottom - top
            if (h <= 0) return true
            val threshold = (h * TRIM_LINE_MATCH_RATIO).toInt().coerceAtLeast(1)
            var count = 0
            for (y in top until bottom) {
                if (isGutter[y * stride + x]) count++
            }
            return count >= threshold
        }

        while (top < bottom && rowIsGutter(top)) top++
        while (bottom > top && rowIsGutter(bottom - 1)) bottom--
        while (left < right && colIsGutter(left)) left++
        while (right > left && colIsGutter(right - 1)) right--

        if (right - left < minW || bottom - top < minH) return null
        return IntBox(left, top, right, bottom)
    }
}
