package tachiyomi.core.common.util.image

import android.app.Application
import android.graphics.Color
import android.graphics.Rect
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

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
        for (color in listOf(Color.BLACK, Color.WHITE)) {
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
}
