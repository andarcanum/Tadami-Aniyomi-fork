package eu.kanade.tachiyomi.data.coil

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.gif.PixelOpacity
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.maxBitmapSize
import coil3.size.Dimension
import coil3.size.Scale
import coil3.size.Size
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class TachiyomiImageDecoderTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val context: Context by lazy { ApplicationProvider.getApplicationContext() }
    private val imageLoader: ImageLoader by lazy { ImageLoader.Builder(context).build() }

    @Test
    fun `static factory claims still avif heif jxl tiff jp2 and skips animated formats`() {
        val factory = TachiyomiImageDecoder.Factory()
        val animGif = "GIF89a\u0010\u0000\u0010\u0000".toByteArray()
        val animWebp = "RIFF0000WEBPVP8X".toByteArray() + byteArrayOf(10, 0, 0, 0, 2) + ByteArray(11)
        val animHeif = "0000ftypmsf1".toByteArray()
        val animAvif = "0000ftypavis".toByteArray()
        val staticAvif = "0000ftypavif".toByteArray()
        val staticHeif = "0000ftypheic".toByteArray()
        val staticJxl = byteArrayOf(0xFF.toByte(), 0x0A, 0x13, 0x1C)
        val staticTiff = byteArrayOf(0x49, 0x49, 0x2A, 0x00)
        val staticJp2 = byteArrayOf(0xFF.toByte(), 0x4F, 0xFF.toByte(), 0x51)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())

        for (stillBytes in listOf(staticAvif, staticHeif, staticJxl, staticTiff, staticJp2)) {
            assertNotNull(createWith(factory, stillBytes))
        }
        assertNull(createWith(factory, jpeg))

        val customExtras = ImageRequest.Builder(context)
            .customDecoder(true)
            .cropBorders(true)
            .build()
            .extras
        val customOptions = Options(context = context, extras = customExtras)
        assertNotNull(createWith(factory, jpeg, customOptions))

        // Animated images must never be intercepted by TachiyomiImageDecoder, even with customDecoder/cropBorders.
        for (animatedBytes in listOf(animGif, animWebp, animHeif, animAvif)) {
            assertNull(createWith(factory, animatedBytes))
            assertNull(createWith(factory, animatedBytes, customOptions))
        }
    }

    @Test
    fun `animated native factory claims animated heif and avif and skips static images`() {
        val factory = AnimatedNativeImageDecoder.Factory()
        val animHeif = "0000ftypmsf1".toByteArray()
        val animAvif = "0000ftypavis".toByteArray()
        val animWebp = "RIFF0000WEBPVP8X".toByteArray() + byteArrayOf(10, 0, 0, 0, 2) + ByteArray(11)
        val animGif = "GIF89a\u0010\u0000\u0010\u0000".toByteArray()
        val staticHeif = "0000ftypheic".toByteArray()
        val staticAvif = "0000ftypavif".toByteArray()
        val staticWebp = "RIFF0000WEBPVP8X".toByteArray() + byteArrayOf(10, 0, 0, 0, 0) + ByteArray(11)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())

        assertTrue(createWith(factory, animHeif) is AnimatedNativeImageDecoder)
        assertTrue(createWith(factory, animAvif) is AnimatedNativeImageDecoder)
        // Animated WebP on API >= 28 and GIFs are delegated to Coil's platform AnimatedImageDecoder.
        assertNull(createWith(factory, animWebp))
        assertNull(createWith(factory, animGif))

        for (stillBytes in listOf(staticHeif, staticAvif, staticWebp, jpeg)) {
            assertNull(createWith(factory, stillBytes))
        }
    }

    @Test
    @Config(sdk = [26], application = Application::class)
    fun `animated native factory claims animated webp below api 28`() {
        val factory = AnimatedNativeImageDecoder.Factory()
        val animWebp = "RIFF0000WEBPVP8X".toByteArray() + byteArrayOf(10, 0, 0, 0, 2) + ByteArray(11)
        assertTrue(createWith(factory, animWebp) is AnimatedNativeImageDecoder)
    }

    @Test
    fun `file image source is opened via source() instead of failing with missing image source`() {
        val file = tempFolder.newFile("sample.avif").apply {
            writeBytes("0000ftypavif".toByteArray())
        }
        val fileImageSource = ImageSource(file = file.toOkioPath(), fileSystem = FileSystem.SYSTEM)
        assertNull(fileImageSource.sourceOrNull())

        val decoder = TachiyomiImageDecoder(fileImageSource, Options(context))
        val result = runCatching { runBlocking { decoder.decode() } }
        val error = result.exceptionOrNull()
        assertFalse(
            error is IllegalStateException && error.message == "Missing image source",
            "FileImageSource should be readable via source()",
        )
        assertTrue(error is LinkageError, "Expected native library load failure on JVM, got: $error")

        val afterClose = runCatching { fileImageSource.source() }
        assertTrue(afterClose.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `computeDstDimensions clamps against finite maxBitmapSize and preserves undefined axis`() {
        val extras = ImageRequest.Builder(context)
            .maxBitmapSize(Size(500, 600))
            .build()
            .extras
        val fitOptions = Options(
            context = context,
            size = Size(800, Dimension.Undefined),
            scale = Scale.FIT,
            extras = extras,
        )
        assertEquals(500 to Int.MAX_VALUE, fitOptions.computeDstDimensions(1000, 1400))

        val originalOptions = Options(
            context = context,
            size = Size.ORIGINAL,
            scale = Scale.FILL,
            extras = extras,
        )
        assertEquals(500 to 600, originalOptions.computeDstDimensions(1000, 1400))
    }

    @Test
    fun `animated native drawable pauses on setVisible false, exposes isHdr, and releases on close`() {
        val decodeLatch = CountDownLatch(1)
        val source = FakeNativeAnimationSource(
            pixelBytes = byteArrayOf(0, 0, 0, -1),
            durationMs = 200,
            onDecode = { decodeLatch.countDown() },
        )
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val drawable = AnimatedNativeDrawable(
            source = source,
            displayBitmap = bitmap,
            intScratch = IntArray(1),
            hdrScratch = null,
            isHdr = false,
            pageCount = 2,
            initialIndex = 0,
            initialLast = false,
            initialDurationMs = 200,
            scale = Scale.FIT,
            animatedTransformation = null,
            pixelOpacity = PixelOpacity.UNCHANGED,
        )

        assertFalse(drawable.isHdr)
        drawable.start()
        assertTrue(drawable.isRunning)
        assertTrue(decodeLatch.await(2, TimeUnit.SECONDS))

        assertTrue(drawable.setVisible(false, false))
        assertFalse(drawable.isRunning)

        assertTrue(drawable.setVisible(true, false))
        assertTrue(drawable.isRunning)

        drawable.close()
        assertFalse(drawable.isRunning)
        assertTrue(bitmap.isRecycled)
        assertEquals(1, source.closeCalls)

        // Closed drawables ignore restart requests and safe-guard draw().
        drawable.start()
        drawable.setVisible(true, true)
        assertFalse(drawable.isRunning)
        drawable.draw(Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)))

        val hdrSource = FakeNativeAnimationSource(pixelBytes = ByteArray(8), durationMs = 100)
        val hdrBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGBA_F16)
        val hdrDrawable = AnimatedNativeDrawable(
            source = hdrSource,
            displayBitmap = hdrBitmap,
            intScratch = null,
            hdrScratch = ByteBuffer.allocateDirect(8),
            isHdr = true,
            pageCount = 2,
            initialIndex = 0,
            initialLast = false,
            initialDurationMs = 100,
            scale = Scale.FIT,
            animatedTransformation = null,
            pixelOpacity = PixelOpacity.UNCHANGED,
        )
        assertTrue(hdrDrawable.isHdr)
        hdrDrawable.close()
        assertTrue(hdrBitmap.isRecycled)
        assertEquals(1, hdrSource.closeCalls)
    }

    private class FakeNativeAnimationSource(
        private val pixelBytes: ByteArray,
        private val durationMs: Int,
        private val onDecode: () -> Unit = {},
    ) : NativeAnimationSource {
        var closeCalls = 0
            private set

        override fun decodeNext(): NativeFrameData {
            onDecode()
            return NativeFrameData(
                width = 1,
                height = 1,
                image = ByteBuffer.wrap(pixelBytes.copyOf()),
                index = 1,
                duration = durationMs,
            )
        }

        override fun close() {
            closeCalls++
        }
    }

    private fun createWith(
        factory: coil3.decode.Decoder.Factory,
        bytes: ByteArray,
        options: Options = Options(context),
    ): coil3.decode.Decoder? {
        val source = ImageSource(Buffer().write(bytes), FileSystem.SYSTEM)
        return factory.create(
            result = SourceFetchResult(source, mimeType = null, dataSource = DataSource.DISK),
            options = options,
            imageLoader = imageLoader,
        )
    }
}
