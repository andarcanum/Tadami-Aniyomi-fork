package tachiyomi.core.common.util.system

import android.app.Application
import android.graphics.Bitmap
import okio.Buffer
import okio.BufferedSource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class ImageUtilTest {

    @Test
    fun `very tall image is detected as tall`() {
        val imageSource = createImageSource(width = 200, height = 900)

        assertTrue(ImageUtil.isTallImage(imageSource))
    }

    @Test
    fun `normal image is not detected as tall`() {
        val imageSource = createImageSource(width = 900, height = 200)

        assertFalse(ImageUtil.isTallImage(imageSource))
    }

    @Test
    fun `borderline tall image is detected as tall`() {
        val imageSource = createImageSource(width = 200, height = 700)

        assertTrue(ImageUtil.isTallImage(imageSource))
    }

    private fun createImageSource(width: Int, height: Int): BufferedSource {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val output = Buffer()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output.outputStream())
        bitmap.recycle()
        return output
    }
}
