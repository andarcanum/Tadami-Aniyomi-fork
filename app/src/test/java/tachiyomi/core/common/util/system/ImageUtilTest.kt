package tachiyomi.core.common.util.system

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import okio.Buffer
import okio.BufferedSource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tachiyomi.core.common.util.image.NativeImageDecoder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pure-header dimension, tall-image, and animated-format routing tests run without JNI via
 * `ImageHeader` and `BitmapFactory`, while pixel-decoding paths require an Android instrumentation
 * environment with `libimagedecoder.so`.
 */
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

    @Test
    fun `large png header dimensions above 32767px resolve cleanly without native library`() {
        val largePng = Buffer().write(
            byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 17, 0, 0, -100, 65),
        )
        val dims = ImageUtil.getImageDimensions(largePng)
        assertNotNull(dims)
        assertEquals(17, dims.width)
        assertEquals(40001, dims.height)
        assertTrue(ImageUtil.isTallImage(largePng))
    }

    @Test
    fun `animated format detection covers gif webp heif and avif across api levels`() {
        val gif = Buffer().write("GIF89a\u0010\u0000\u0010\u0000".toByteArray())
        val animWebp = Buffer().write("RIFF0000WEBPVP8X".toByteArray() + byteArrayOf(10, 0, 0, 0, 2) + ByteArray(11))
        val staticWebp = Buffer().write("RIFF0000WEBPVP8X".toByteArray() + byteArrayOf(10, 0, 0, 0, 0) + ByteArray(11))
        val animHeif = Buffer().write("0000ftypmsf1".toByteArray())
        val staticHeif = Buffer().write("0000ftypheic".toByteArray())
        val animAvif = Buffer().write("0000ftypavis".toByteArray())
        val staticAvif = Buffer().write("0000ftypavif".toByteArray())

        assertTrue(ImageUtil.isAnimatedAndSupported(gif))
        assertTrue(ImageUtil.isAnimatedAndSupported(animWebp))
        assertFalse(ImageUtil.isAnimatedAndSupported(staticWebp))
        assertTrue(ImageUtil.isAnimatedAndSupported(animHeif))
        assertFalse(ImageUtil.isAnimatedAndSupported(staticHeif))
        assertTrue(ImageUtil.isAnimatedAndSupported(animAvif))
        assertFalse(ImageUtil.isAnimatedAndSupported(staticAvif))
    }

    @Test
    fun `sniffImageMetadata reads dimensions and animation in a single bounded pass`() {
        val pngHeader =
            byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 17, 0, 0, -100, 65)
        val trailingPayload = ByteArray(128 * 1024)
        var bytesRead = 0
        val countingStream = object : java.io.ByteArrayInputStream(pngHeader + trailingPayload) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                return super.read(b, off, len).also { if (it > 0) bytesRead += it }
            }
        }
        val meta = ImageUtil.sniffImageMetadata(countingStream)
        assertFalse(meta.isAnimated)
        assertEquals(ImageUtil.ImageDimensions(17, 40001), meta.dimensions)
        assertTrue(bytesRead <= 65_536, "Expected bounded header read, read $bytesRead bytes")

        val animGif = "GIF89a\u0010\u0000\u0010\u0000".toByteArray()
        val gifMeta = ImageUtil.sniffImageMetadata(animGif.inputStream())
        assertTrue(gifMeta.isAnimated)
        assertEquals(null, gifMeta.dimensions)
    }

    @Test
    fun `findSmartSplitX locates off-center uniform gutter column`() {
        val decoder = mockk<NativeImageDecoder>()
        every { decoder.width } returns 1000
        every { decoder.height } returns 800
        // Central band is 450..550 (width 100). Put a uniform white gutter at col 30 (x = 480)
        // while all other columns alternate black/white vertically (simulating speech bubble/art).
        every { decoder.decode(any(), any()) } answers {
            val rect = firstArg<android.graphics.Rect>()
            val bmp = Bitmap.createBitmap(rect.width(), 64, Bitmap.Config.ARGB_8888)
            for (x in 0 until bmp.width) {
                for (y in 0 until bmp.height) {
                    val color = if (x == 30) {
                        Color.WHITE
                    } else if (y % 2 == 0) {
                        Color.BLACK
                    } else {
                        Color.WHITE
                    }
                    bmp.setPixel(x, y, color)
                }
            }
            bmp
        }

        val splitX = ImageUtil.findSmartSplitX(decoder)
        assertEquals(480, splitX)
    }

    @Test
    fun `chooseBackground handles unreadable or small streams safely`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val emptyBg = ImageUtil.chooseBackground(app, ByteArray(0).inputStream())
        assertTrue(emptyBg is ColorDrawable)
        assertEquals(Color.WHITE, emptyBg.color)
    }

    @Test
    fun `chooseBackground keeps downsampled white-margin pages loadable`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        mockkObject(NativeImageDecoder.Companion)
        try {
            for (previewWidth in 100..109) {
                val preview = Bitmap.createBitmap(previewWidth, 512, Bitmap.Config.ARGB_8888).apply {
                    val pixels = IntArray(previewWidth * 512) { index ->
                        val x = index % previewWidth
                        val y = index / previewWidth
                        if (x in 20 until previewWidth - 20 && y in 64 until 448) Color.BLACK else Color.WHITE
                    }
                    setPixels(pixels, 0, previewWidth, 0, 0, previewWidth, 512)
                }
                val decoder = mockk<NativeImageDecoder>()
                every { decoder.width } returns previewWidth * 16
                every { decoder.height } returns 8192
                every { decoder.decode(any(), any()) } returns preview
                every { decoder.close() } returns Unit
                try {
                    // Only JNI opening/decoding is replaced; sampling and background heuristics stay real.
                    every { NativeImageDecoder.open(any(), any()) } returns decoder
                    val background = byteArrayOf(1).inputStream().use { ImageUtil.chooseBackground(app, it) }

                    assertTrue(background is ColorDrawable, "Preview width $previewWidth")
                    assertEquals(Color.WHITE, background.color, "Preview width $previewWidth")
                    verify(exactly = 1) { decoder.decode(sampleSize = 16) }
                    verify(exactly = 1) { decoder.close() }
                    assertTrue(preview.isRecycled)
                } finally {
                    if (!preview.isRecycled) preview.recycle()
                }
            }
        } finally {
            unmockkObject(NativeImageDecoder.Companion)
        }
    }

    private fun createImageSource(width: Int, height: Int): BufferedSource {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val output = Buffer()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output.outputStream())
        bitmap.recycle()
        return output
    }
}
