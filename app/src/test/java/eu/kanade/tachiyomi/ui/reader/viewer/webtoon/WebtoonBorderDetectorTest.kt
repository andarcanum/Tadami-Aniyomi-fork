package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class WebtoonBorderDetectorTest {

    private fun createTestBitmap(
        width: Int,
        height: Int,
        backgroundColor: Int,
        contentLeft: Int,
        contentRight: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColor)

        val paint = Paint().apply {
            color = Color.BLUE // Any color different from background
        }
        // Draw content in the specified horizontal range
        canvas.drawRect(
            contentLeft.toFloat(),
            0f,
            contentRight.toFloat(),
            height.toFloat(),
            paint,
        )
        return bitmap
    }

    @Test
    fun `should detect correct boundaries when there are clear side margins`() {
        // 100px wide image, content is between 25px and 75px (50px of content, 25px margin on each side)
        val bitmap = createTestBitmap(
            width = 100,
            height = 100,
            backgroundColor = Color.WHITE,
            contentLeft = 25,
            contentRight = 75,
        )

        val bounds = WebtoonBorderDetector.detectContentBounds(bitmap)
        // Should detect left = 25, right = 75 (or 74 depending on exact pixel drawing)
        bounds.left shouldBe 25
        bounds.right shouldBe 75
        bounds.top shouldBe 0
        bounds.bottom shouldBe 100
    }

    @Test
    fun `should not crop when margins are very small`() {
        // 100px wide image, content is between 2px and 98px (96% of total width)
        // Since it's > 95% of total width, it should return full bounds
        val bitmap = createTestBitmap(
            width = 100,
            height = 100,
            backgroundColor = Color.WHITE,
            contentLeft = 2,
            contentRight = 98,
        )

        val bounds = WebtoonBorderDetector.detectContentBounds(bitmap)
        bounds shouldBe Rect(0, 0, 100, 100)
    }

    @Test
    fun `should not crop when there is no content (solid background)`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
        }

        val bounds = WebtoonBorderDetector.detectContentBounds(bitmap)
        bounds shouldBe Rect(0, 0, 100, 100)
    }
}
