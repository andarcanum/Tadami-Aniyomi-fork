package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.app.Application
import android.graphics.Bitmap
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
class WebtoonBorderDetectorAdaptiveTest {

    @Test
    fun computeSampleRowsScalesDensityWithWebtoonHeight() {
        val standardRows = WebtoonBorderDetector.computeSampleRows(width = 800, height = 1200)
        assertEquals(5, standardRows.size)

        val tallRows = WebtoonBorderDetector.computeSampleRows(width = 800, height = 12_000)
        assertEquals(17, tallRows.size)
        assertTrue(tallRows.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun tallWebtoonStripPreservesWideProtrudingBubbleBetweenCoarseBands() {
        val width = 200
        val height = 2000
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            // Narrow main column (60..140) along the whole strip
            for (y in 0 until height) {
                for (x in 60 until 140) {
                    bitmap.setPixel(x, y, Color.DKGRAY)
                }
            }
            // Wide protruding panel/speech bubble strictly between legacy 10% (y=200) and 33% (y=666)
            for (y in 380 until 560) {
                for (x in 20 until 180) {
                    bitmap.setPixel(x, y, Color.BLACK)
                }
            }

            val bounds = WebtoonBorderDetector.detectContentBounds(bitmap)
            assertEquals(Rect(20, 0, 180, height), bounds)
        } finally {
            bitmap.recycle()
        }
    }
}
