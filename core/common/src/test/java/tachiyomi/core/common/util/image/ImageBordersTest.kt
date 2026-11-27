package tachiyomi.core.common.util.image

import android.app.Application
import android.graphics.Color
import android.graphics.Rect
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class ImageBordersTest {
    @Test
    fun whiteBorderKeepsLastContentPixel() {
        val pixels = IntArray(100 * 100) { Color.WHITE }
        for (y in 20 until 80) for (x in 25 until 75) pixels[y * 100 + x] = Color.BLACK
        assertEquals(Rect(25, 20, 75, 80), ImageBorders.find(pixels, 100, 100))
    }

    @Test
    fun blackBorderKeepsWhiteContent() {
        val pixels = IntArray(100 * 100) { Color.BLACK }
        for (y in 20 until 80) for (x in 25 until 75) pixels[y * 100 + x] = Color.WHITE
        assertEquals(Rect(25, 20, 75, 80), ImageBorders.find(pixels, 100, 100))
    }

    @Test
    fun uniformImageIsNotEmptied() {
        for (color in listOf(Color.BLACK, Color.WHITE, Color.TRANSPARENT)) {
            assertEquals(Rect(0, 0, 100, 100), ImageBorders.find(IntArray(10_000) { color }, 100, 100))
        }
    }

    @Test
    fun singlePixelImageKeepsValidBounds() {
        assertEquals(Rect(0, 0, 1, 1), ImageBorders.find(intArrayOf(Color.WHITE), 1, 1))
    }

    @Test
    fun mixedEdgePreservesFullWidth() {
        val pixels = IntArray(100 * 100) { index -> if (index / 100 < 50) Color.BLACK else Color.WHITE }
        // Opposite edge colors meet at the same boundary: an empty crop must fall back to the full image.
        assertEquals(Rect(0, 0, 100, 100), ImageBorders.find(pixels, 100, 100))
    }

    @Test
    fun midGrayBorderIsNotMisclassifiedAsBlackAndOvercropped() {
        val gray = Color.rgb(128, 128, 128)
        val pixels = IntArray(100 * 100) { gray }
        for (y in 20 until 80) for (x in 20 until 80) pixels[y * 100 + x] = Color.BLACK
        assertEquals(Rect(0, 0, 100, 100), ImageBorders.find(pixels, 100, 100))
    }

    @Test
    fun transparentBorderCropsCleanlyDownToOpaqueWhiteAndBlackArt() {
        for (artColor in listOf(Color.WHITE, Color.BLACK, Color.RED)) {
            val pixels = IntArray(100 * 100) { Color.TRANSPARENT }
            // Transparent white (alpha=0) must still be treated as transparent margin.
            for (x in 0 until 100) pixels[x] = 0x00FFFFFF
            for (y in 15 until 85) for (x in 20 until 80) pixels[y * 100 + x] = artColor
            assertEquals(Rect(20, 15, 80, 85), ImageBorders.find(pixels, 100, 100))
        }
    }

    @Test
    fun transparentCutoutInsideWhiteBorderStopsCropAndTinySpeckIsTolerated() {
        val width = 1000
        val height = 100
        val pixels = IntArray(width * height) { Color.WHITE }
        // Tiny 1px transparent hole inside white margin (below noise limit=1) should be ignored.
        pixels[5 * width + 10] = 0x00FFFFFF
        // Wide transparent cutout at rows 20..80 must stop white border cropping.
        for (y in 20 until 80) {
            for (x in 100 until 900) {
                pixels[y * width + x] = 0x00FFFFFF
            }
        }
        assertEquals(Rect(100, 20, 900, 80), ImageBorders.find(pixels, width, height))
    }

    @Test
    fun reverseScanInspectsEdgeRowZero() {
        val pixels = IntArray(100 * 100) { Color.WHITE }
        for (x in 0 until 100) pixels[x] = Color.BLACK
        // Row 0 has black content across the full width; bottom scan must stop at y=1, not leave crop [0,1..100).
        assertEquals(Rect(0, 0, 100, 100), ImageBorders.find(pixels, 100, 100))
    }

    @Test
    fun stripScanSupportsGreaterThanOneMegapixelAndExtremeTallImages() {
        val w = 1400
        val h = 1000 // 1.4 MP
        val expected = Rect(40, 85, 1360, 915)
        var maxStripArea = 0
        val found = ImageBorders.find(w, h) { region, buffer ->
            val area = region.width() * region.height()
            maxStripArea = maxOf(maxStripArea, area)
            assertTrue(area <= 262_144, "Strip exceeded 262_144 pixel budget: $region")
            for (dy in 0 until region.height()) {
                val y = region.top + dy
                val rowOff = dy * region.width()
                for (dx in 0 until region.width()) {
                    val x = region.left + dx
                    buffer[rowOff + dx] = if (expected.contains(x, y)) Color.BLACK else Color.WHITE
                }
            }
        }
        assertEquals(expected, found)
        assertTrue(maxStripArea > 0)

        val tallW = 20
        val tallH = 300_000
        val tallExpected = Rect(2, 120, 18, 299_800)
        val tallFound = ImageBorders.find(tallW, tallH) { region, buffer ->
            assertTrue(region.width().toLong() * region.height() <= 262_144L, "Strip exceeded budget: $region")
            for (dy in 0 until region.height()) {
                val y = region.top + dy
                val rowOff = dy * region.width()
                for (dx in 0 until region.width()) {
                    val x = region.left + dx
                    buffer[rowOff + dx] = if (tallExpected.contains(x, y)) Color.BLACK else Color.WHITE
                }
            }
        }
        assertEquals(tallExpected, tallFound)
    }
}
