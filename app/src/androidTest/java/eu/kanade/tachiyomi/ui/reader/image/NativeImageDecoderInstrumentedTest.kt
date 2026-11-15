package eu.kanade.tachiyomi.ui.reader.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.core.common.util.image.NativeImageDecoder
import tachiyomi.core.common.util.system.ImageUtil
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Real JNI acceptance tests, including the shared background boundary; no activity, application DI,
 * or runtime network access.
 * Fixtures are original synthetic pixels encoded locally by Android's Bitmap.compress. The EXIF
 * APP1 segment below is authored here, not copied from an external image. No bundled assets needed.
 * This SDR slice does not establish embedded-profile, HDR, AVIF, or JPEG XL coverage.
 */
@RunWith(AndroidJUnit4::class)
class NativeImageDecoderInstrumentedTest {

    @Test
    fun auto_background_keeps_encoded_1600x8192_white_margin_page_loadable() {
        val encoded = Bitmap.createBitmap(1600, 8192, Bitmap.Config.ARGB_8888).useBitmap { bitmap ->
            bitmap.eraseColor(Color.WHITE)
            android.graphics.Canvas(bitmap).drawRect(
                320f,
                1024f,
                1280f,
                7168f,
                android.graphics.Paint().apply { color = Color.BLACK },
            )
            ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        }
        NativeImageDecoder.open(encoded.inputStream()).use { decoder ->
            decoder.decode(sampleSize = 16).useBitmap { preview ->
                assertDimensions("Background preview", preview, 100, 512)
                assertEquals("White margin must survive JNI sampling", Color.WHITE, preview.getPixel(0, 0))
                assertEquals("Black art must survive JNI sampling", Color.BLACK, preview.getPixel(50, 256))
            }
        }
        val input = CloseTrackingInputStream(encoded)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val background = ImageUtil.chooseBackground(context, input)

        assertTrue("Valid encoded page must keep its white background", background is ColorDrawable)
        assertEquals(Color.WHITE, (background as ColorDrawable).color)
        assertTrue("Background decode must close its input", input.closed)
    }

    @Test
    fun png_alpha_tiles_reassemble_odd_sized_full_decode() {
        assertTiledDecode(Bitmap.CompressFormat.PNG, withAlpha = true)
    }

    @Test
    fun jpeg_tiles_reassemble_odd_sized_full_decode() {
        assertTiledDecode(Bitmap.CompressFormat.JPEG, withAlpha = false)
    }

    @Suppress("DEPRECATION") // WEBP is available on the app's API 26 minimum, unlike WEBP_LOSSY.
    @Test
    fun webp_alpha_tiles_reassemble_odd_sized_full_decode() {
        assertTiledDecode(Bitmap.CompressFormat.WEBP, withAlpha = true)
    }

    @Test
    fun sampled_full_and_edge_regions_use_ceil_dimensions() {
        // The edge region is 59 x 45, with a nonzero, non-sample-aligned origin.
        val region = Rect(6, 4, 65, 49)
        val expectations = listOf(
            SampleExpectation(1, 65, 49, 59, 45),
            SampleExpectation(2, 33, 25, 30, 23),
            SampleExpectation(4, 17, 13, 15, 12),
            SampleExpectation(8, 9, 7, 8, 6),
        )
        for (format in formats()) {
            val encoded = encodedGrid(format)
            NativeImageDecoder.open(encoded.inputStream()).use { decoder ->
                for (expected in expectations) {
                    val label = "$format sample=${expected.sampleSize}"
                    decoder.decode(sampleSize = expected.sampleSize).useBitmap { bitmap ->
                        assertDimensions(label, bitmap, expected.fullWidth, expected.fullHeight)
                    }
                    decoder.decode(region, expected.sampleSize).useBitmap { bitmap ->
                        assertDimensions(label, bitmap, expected.regionWidth, expected.regionHeight)
                    }
                }
                assertEquals("decode must not offset the caller's Rect", Rect(6, 4, 65, 49), region)
            }
        }
    }

    @Test
    fun crop_borders_uses_content_relative_half_open_regions() {
        // Asymmetric white borders surround dark, nonuniform content at [7, 5, 58, 42).
        val original = IntArray(WIDTH * HEIGHT) { Color.WHITE }
        for (y in 5 until 42) {
            for (x in 7 until 58) {
                original[y * WIDTH + x] = Color.rgb(x, y, 32)
            }
        }
        val encoded = encode(Bitmap.CompressFormat.PNG, original)
        NativeImageDecoder.open(encoded.inputStream(), cropBorders = true).use { decoder ->
            assertEquals(51, decoder.width)
            assertEquals(37, decoder.height)
            decoder.decode().useBitmap { full ->
                assertDimensions("cropped content", full, 51, 37)
                val expected = IntArray(51 * 37) { index ->
                    original[(index / 51 + 5) * WIDTH + index % 51 + 7]
                }
                assertArrayEquals("crop must preserve every content pixel", expected, pixels(full))
                assertRegionMatchesFull(decoder, full, Rect(0, 0, 51, 37))
                assertRegionMatchesFull(decoder, full, Rect(50, 0, 51, 37))
                assertRegionMatchesFull(decoder, full, Rect(0, 36, 51, 37))
                assertRegionMatchesFull(decoder, full, Rect(50, 36, 51, 37))
            }
        }
    }

    @Test
    fun successful_open_closes_input_before_any_caller_decode() {
        for (format in formats()) {
            val encoded = encodedGrid(format)
            for (cropBorders in listOf(false, true)) {
                val input = CloseTrackingInputStream(encoded)
                NativeImageDecoder.open(input, cropBorders).use { decoder ->
                    assertTrue("$format crop=$cropBorders input still open", input.closed)
                    // This stream rejects reads after close: decoding must no longer depend on it.
                    decoder.decode().useBitmap { bitmap ->
                        assertDimensions("$format closed input", bitmap, decoder.width, decoder.height)
                        assertTrue(pixels(bitmap).isNotEmpty())
                    }
                }
            }
        }
    }

    @Test
    fun malformed_open_closes_input_and_next_healthy_decode_still_works() {
        val healthy = encodedGrid(Bitmap.CompressFormat.PNG)
        val malformed = listOf(
            byteArrayOf(),
            "not an encoded image".toByteArray(Charsets.US_ASCII),
            // A PNG signature with no IHDR or image data is deterministically incomplete.
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
        )
        for ((index, bytes) in malformed.withIndex()) {
            for (cropBorders in listOf(false, true)) {
                val input = CloseTrackingInputStream(bytes)
                assertThrows("malformed fixture $index crop=$cropBorders", Exception::class.java) {
                    // Close an unexpectedly successful decoder before the assertion fails.
                    NativeImageDecoder.open(input, cropBorders).use { }
                }
                assertTrue("malformed fixture $index input still open", input.closed)
                NativeImageDecoder.open(healthy.inputStream()).use { decoder ->
                    decoder.decode().useBitmap { bitmap ->
                        assertDimensions("healthy after malformed", bitmap, WIDTH, HEIGHT)
                        assertArrayEquals(gridPixels(withAlpha = true), pixels(bitmap))
                    }
                }
            }
        }
    }

    @Test
    fun input_read_failure_still_closes_input() {
        val input = object : InputStream() {
            var closed = false

            override fun read(): Int = throw IOException("Synthetic input read failure")

            override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
                throw IOException("Synthetic input read failure")

            override fun close() {
                closed = true
            }
        }
        assertThrows(Exception::class.java) {
            NativeImageDecoder.open(input).use { }
        }
        assertTrue("failing input still open", input.closed)
        NativeImageDecoder.open(encodedGrid(Bitmap.CompressFormat.PNG).inputStream()).use { decoder ->
            decoder.decode().useBitmap { bitmap ->
                assertArrayEquals(gridPixels(withAlpha = true), pixels(bitmap))
            }
        }
    }

    @Test
    fun returned_bitmap_survives_native_close_and_double_close() {
        for (format in formats()) {
            val decoder = NativeImageDecoder.open(encodedGrid(format).inputStream())
            try {
                decoder.decode().useBitmap { bitmap ->
                    val expected = pixels(bitmap)
                    decoder.close()
                    decoder.close()
                    assertFalse("$format caller-owned bitmap recycled by close", bitmap.isRecycled)
                    assertDimensions("$format after close", bitmap, WIDTH, HEIGHT)
                    assertArrayEquals("$format pixels after close", expected, pixels(bitmap))
                }
            } finally {
                decoder.close()
            }
        }
    }

    @Test
    fun closed_decoder_rejects_decode() {
        val decoder = NativeImageDecoder.open(encodedGrid(Bitmap.CompressFormat.PNG).inputStream())
        try {
            decoder.close()
            assertThrows(Exception::class.java) {
                // Recycle even if a regression incorrectly lets this decode succeed.
                decoder.decode().useBitmap { }
            }
            assertThrows(Exception::class.java) {
                decoder.decode(Rect(0, 0, 1, 1)).useBitmap { }
            }
        } finally {
            decoder.close()
        }
    }

    @Test
    fun repeated_open_decode_close_preserves_pixels() {
        for (format in formats()) {
            val encoded = encodedGrid(format)
            val expected = NativeImageDecoder.open(encoded.inputStream()).use { decoder ->
                decoder.decode().useBitmap { pixels(it) }
            }
            repeat(24) { iteration ->
                NativeImageDecoder.open(encoded.inputStream()).use { decoder ->
                    decoder.decode().useBitmap { full ->
                        assertDimensions("$format iteration=$iteration", full, WIDTH, HEIGHT)
                        assertArrayEquals("$format iteration=$iteration", expected, pixels(full))
                        assertRegionMatchesFull(decoder, full, Rect(17, 13, WIDTH, HEIGHT))
                    }
                }
            }
        }
    }

    @Test
    fun invalid_decode_request_does_not_poison_next_healthy_decode() {
        val encoded = encodedGrid(Bitmap.CompressFormat.PNG)
        NativeImageDecoder.open(encoded.inputStream()).use { decoder ->
            val invalidRegions = listOf(
                Rect(0, 0, 0, 1),
                Rect(0, 0, 1, 0),
                Rect(10, 10, 9, 11),
                Rect(-1, 0, WIDTH, HEIGHT),
                Rect(0, -1, WIDTH, HEIGHT),
                Rect(0, 0, WIDTH + 1, HEIGHT),
                Rect(0, 0, WIDTH, HEIGHT + 1),
            )
            for (region in invalidRegions) {
                assertThrows("invalid region $region", IllegalArgumentException::class.java) {
                    decoder.decode(region).useBitmap { }
                }
            }
            for (sampleSize in listOf(0, -1)) {
                assertThrows("invalid sample $sampleSize", IllegalArgumentException::class.java) {
                    decoder.decode(sampleSize = sampleSize).useBitmap { }
                }
            }
            decoder.decode().useBitmap { full ->
                assertArrayEquals(gridPixels(withAlpha = true), pixels(full))
                assertRegionMatchesFull(decoder, full, Rect(WIDTH - 1, HEIGHT - 1, WIDTH, HEIGHT))
            }
        }
        NativeImageDecoder.open(encoded.inputStream()).use { decoder ->
            decoder.decode().useBitmap { bitmap ->
                assertArrayEquals(gridPixels(withAlpha = true), pixels(bitmap))
            }
        }
    }

    @Test
    fun png_axes_above_native_limit_remain_region_decodable() {
        for ((width, height) in listOf(17 to 40001, 40001 to 17)) {
            val encoded = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).useBitmap { bitmap ->
                bitmap.eraseColor(Color.WHITE)
                bitmap.setPixel(width - 1, height - 1, Color.BLUE)
                ByteArrayOutputStream().use { out ->
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
                    out.toByteArray()
                }
            }
            val input = CloseTrackingInputStream(encoded)
            NativeImageDecoder.open(input).use { decoder ->
                assertTrue(input.closed)
                assertEquals(width, decoder.width)
                assertEquals(height, decoder.height)
                val edge = Rect(width - 5, height - 5, width, height)
                decoder.decode(edge).useBitmap { bitmap ->
                    assertDimensions("large PNG edge", bitmap, 5, 5)
                    assertEquals(Color.BLUE, bitmap.getPixel(4, 4))
                }
                decoder.decode(edge, 2).useBitmap { bitmap ->
                    assertDimensions("sampled large PNG edge", bitmap, 3, 3)
                }
                decoder.close()
                decoder.close()
            }
        }
    }

    @Test
    fun large_png_supports_border_cropping_and_non_power_of_two_sampling() {
        val width = 17
        val height = 40001
        val encoded = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).useBitmap { bitmap ->
            bitmap.eraseColor(Color.WHITE)
            android.graphics.Canvas(bitmap).drawRect(
                0f,
                100f,
                width.toFloat(),
                39900f,
                android.graphics.Paint().apply { color = Color.GREEN },
            )
            ByteArrayOutputStream().use { out ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
                out.toByteArray()
            }
        }
        NativeImageDecoder.open(encoded.inputStream(), cropBorders = true).use { decoder ->
            assertEquals(17, decoder.width)
            assertEquals(39800, decoder.height)
            decoder.decode(Rect(0, 0, 17, 1)).useBitmap { top ->
                assertEquals(Color.GREEN, top.getPixel(8, 0))
            }
        }
        NativeImageDecoder.open(encoded.inputStream()).use { decoder ->
            decoder.decode(Rect(0, 0, 17, 40001), sampleSize = 3).useBitmap { sampled3 ->
                assertDimensions("large PNG sampleSize=3", sampled3, 6, 13334)
            }
            // sampleSize=32 exceeds width=17: LargePngRegionDecoder must clamp inSampleSize instead of failing.
            decoder.decode(Rect(0, 0, 17, 40001), sampleSize = 32).useBitmap { sampled32 ->
                assertDimensions("large PNG sampleSize=32", sampled32, 1, 1251)
            }
        }
    }

    @Test
    fun crop_budget_never_discards_an_unsampled_thin_fringe() {
        val encoded = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888).useBitmap { bitmap ->
            bitmap.eraseColor(Color.WHITE)
            val black = android.graphics.Paint().apply { color = Color.BLACK }
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawRect(200f, 200f, 1800f, 1800f, black)
            // 1px-tall thin horizontal line at odd y=5: exceeds 2048px noise limit (3) yet disappears under sample=2.
            for (x in 200 until 220) {
                bitmap.setPixel(x, 5, Color.BLACK)
            }
            ByteArrayOutputStream().use { out ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
                out.toByteArray()
            }
        }
        NativeImageDecoder.open(encoded.inputStream(), cropBorders = true).use { decoder ->
            assertFalse(decoder.isHdr)
            assertFalse(decoder.hasGainmap)
            assertEquals(1600, decoder.width)
            assertEquals(1795, decoder.height)
            decoder.decode(Rect(0, 0, 20, 1)).useBitmap { fringe ->
                assertEquals(Color.BLACK, fringe.getPixel(10, 0))
            }
        }
    }

    @Test
    fun embedded_display_p3_profile_matches_platform_srgb_conversion() {
        val encoded = Bitmap.createBitmap(
            3,
            1,
            Bitmap.Config.ARGB_8888,
            false,
            ColorSpace.get(ColorSpace.Named.DISPLAY_P3),
        ).useBitmap { source ->
            source.setPixels(
                intArrayOf(Color.rgb(192, 48, 32), Color.rgb(32, 144, 64), Color.rgb(32, 64, 160)),
                0,
                3,
                0,
                0,
                3,
                1,
            )
            ByteArrayOutputStream().use { out ->
                assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, out))
                out.toByteArray()
            }
        }
        assertTrue(
            "Fixture must contain an embedded ICC profile",
            encoded.toString(Charsets.ISO_8859_1).contains("iCCP"),
        )
        val options = BitmapFactory.Options().apply { inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB) }
        BitmapFactory.decodeByteArray(encoded, 0, encoded.size, options).useBitmap { reference ->
            NativeImageDecoder.open(encoded.inputStream()).use { decoder ->
                decoder.decode().useBitmap { actual ->
                    val expected = pixels(reference)
                    pixels(actual).forEachIndexed { index, color ->
                        for (shift in listOf(0, 8, 16)) {
                            val difference = kotlin.math.abs(
                                (color ushr shift and 255) - (expected[index] ushr shift and 255),
                            )
                            assertTrue("ICC channel difference $difference at pixel $index", difference <= 3)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun input_close_failure_does_not_leave_an_open_decoder() {
        val input = object : ByteArrayInputStream(encodedGrid(Bitmap.CompressFormat.PNG)) {
            override fun close() {
                throw IOException("Synthetic close failure")
            }
        }
        assertThrows(IOException::class.java) { NativeImageDecoder.open(input).use { } }
        NativeImageDecoder.open(encodedGrid(Bitmap.CompressFormat.PNG).inputStream()).use { decoder ->
            decoder.decode().useBitmap { assertArrayEquals(gridPixels(withAlpha = true), pixels(it)) }
        }
    }

    @Test
    fun jpeg_exif_orientation_6_rotates_dimensions_and_pixels_clockwise() {
        val jpeg = encodedGrid(Bitmap.CompressFormat.JPEG)
        val original = NativeImageDecoder.open(jpeg.inputStream()).use { decoder ->
            decoder.decode().useBitmap { bitmap ->
                assertDimensions("unoriented JPEG", bitmap, WIDTH, HEIGHT)
                pixels(bitmap)
            }
        }
        // Compare to the same JPEG's decoded pixels, never to the pre-compression RGB grid.
        val clockwise = IntArray(WIDTH * HEIGHT)
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH) {
                clockwise[x * HEIGHT + HEIGHT - 1 - y] = original[y * WIDTH + x]
            }
        }
        NativeImageDecoder.open(withExifOrientation6(jpeg).inputStream()).use { decoder ->
            assertEquals(HEIGHT, decoder.width)
            assertEquals(WIDTH, decoder.height)
            decoder.decode().useBitmap { bitmap ->
                assertDimensions("EXIF orientation 6", bitmap, HEIGHT, WIDTH)
                assertArrayEquals("EXIF 6 must rotate clockwise", clockwise, pixels(bitmap))
                assertRegionMatchesFull(decoder, bitmap, Rect(HEIGHT - 1, WIDTH - 1, HEIGHT, WIDTH))
            }
        }
    }

    private fun assertTiledDecode(format: Bitmap.CompressFormat, withAlpha: Boolean) {
        val original = gridPixels(withAlpha)
        val encoded = encode(format, original)
        NativeImageDecoder.open(encoded.inputStream()).use { decoder ->
            assertEquals("$format width", WIDTH, decoder.width)
            assertEquals("$format height", HEIGHT, decoder.height)
            decoder.decode().useBitmap { full ->
                assertDimensions("$format full", full, WIDTH, HEIGHT)
                // JPEG and lossy WebP expectations must come from these same decoded bytes.
                val expected = pixels(full)
                if (format == Bitmap.CompressFormat.PNG) {
                    assertArrayEquals("PNG must preserve the authored RGBA grid", original, expected)
                }
                if (withAlpha) {
                    assertTrue("$format transparent pixels lost", expected.any { Color.alpha(it) == 0 })
                    assertTrue("$format partial alpha lost", expected.any { Color.alpha(it) in 1..254 })
                    assertTrue("$format opaque pixels lost", expected.any { Color.alpha(it) == 255 })
                }
                // Unaligned internal boundaries and one-pixel final row/column exercise half-open
                // region edges. Copy raw pixels rather than Canvas alpha-compositing the tile union.
                val xEdges = intArrayOf(0, 17, 32, 64, 65)
                val yEdges = intArrayOf(0, 13, 32, 48, 49)
                val union = IntArray(WIDTH * HEIGHT)
                for (y in 0 until yEdges.lastIndex) {
                    for (x in 0 until xEdges.lastIndex) {
                        val region = Rect(xEdges[x], yEdges[y], xEdges[x + 1], yEdges[y + 1])
                        decoder.decode(region).useBitmap { tile ->
                            assertDimensions("$format tile $region", tile, region.width(), region.height())
                            tile.getPixels(
                                union,
                                region.top * WIDTH + region.left,
                                WIDTH,
                                0,
                                0,
                                tile.width,
                                tile.height,
                            )
                        }
                    }
                }
                assertArrayEquals("$format contiguous tile union differs from full decode", expected, union)
                assertArrayEquals("$format later decodes mutated an existing bitmap", expected, pixels(full))
            }
        }
    }

    private fun assertRegionMatchesFull(decoder: NativeImageDecoder, full: Bitmap, region: Rect) {
        val requested = Rect(region)
        val expected = IntArray(region.width() * region.height())
        full.getPixels(expected, 0, region.width(), region.left, region.top, region.width(), region.height())
        decoder.decode(region).useBitmap { tile ->
            assertEquals("decode mutated the requested region", requested, region)
            assertDimensions("region $region", tile, requested.width(), requested.height())
            assertArrayEquals("region $region pixels", expected, pixels(tile))
        }
    }

    @Suppress("DEPRECATION")
    private fun formats() = listOf(Bitmap.CompressFormat.PNG, Bitmap.CompressFormat.JPEG, Bitmap.CompressFormat.WEBP)

    private fun encodedGrid(format: Bitmap.CompressFormat): ByteArray =
        encode(format, gridPixels(withAlpha = format != Bitmap.CompressFormat.JPEG))

    private fun gridPixels(withAlpha: Boolean): IntArray = IntArray(WIDTH * HEIGHT) { index ->
        val x = index % WIDTH
        val y = index / WIDTH
        val alpha = if (withAlpha) ((x / 7 + y / 5) % 4) * 85 else 255
        val color = PALETTE[(x / 5 + y / 3 * 3) % PALETTE.size]
        // RGB channels are 0 or 255, so premultiply/unpremultiply is exact at the chosen alpha values.
        if (alpha == 0) Color.TRANSPARENT else (color and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun encode(format: Bitmap.CompressFormat, pixels: IntArray): ByteArray =
        Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).useBitmap { bitmap ->
            bitmap.setPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
            ByteArrayOutputStream().use { output ->
                assertTrue("$format fixture encoder failed", bitmap.compress(format, 90, output))
                output.toByteArray()
            }
        }

    private fun withExifOrientation6(jpeg: ByteArray): ByteArray {
        assertTrue("fixture must be JPEG", jpeg.size >= 2)
        assertEquals(0xFF, jpeg[0].toInt() and 0xFF)
        assertEquals(0xD8, jpeg[1].toInt() and 0xFF)
        val app1 = byteArrayOf(
            0xFF.toByte(), 0xE1.toByte(), 0x00, 0x22, // APP1, length 34 including length bytes.
            0x45, 0x78, 0x69, 0x66, 0x00, 0x00, // Exif\0\0.
            0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00, // Little-endian TIFF, IFD0 at 8.
            0x01, 0x00, // One IFD entry.
            0x12, 0x01, 0x03, 0x00, // Orientation tag 0x0112, SHORT.
            0x01, 0x00, 0x00, 0x00, // Count = 1.
            0x06, 0x00, 0x00, 0x00, // Inline orientation = 6 (90 degrees clockwise).
            0x00, 0x00, 0x00, 0x00, // No next IFD.
        )
        return ByteArrayOutputStream(jpeg.size + app1.size).use { output ->
            output.write(jpeg, 0, 2)
            output.write(app1)
            output.write(jpeg, 2, jpeg.size - 2)
            output.toByteArray()
        }
    }

    private fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun assertDimensions(label: String, bitmap: Bitmap, width: Int, height: Int) {
        assertEquals("$label width", width, bitmap.width)
        assertEquals("$label height", height, bitmap.height)
    }

    private inline fun <T> Bitmap.useBitmap(block: (Bitmap) -> T): T = try {
        block(this)
    } finally {
        recycle()
    }

    private class CloseTrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
            private set

        override fun read(): Int {
            if (closed) throw IOException("Read after input ownership ended")
            return super.read()
        }

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            if (closed) throw IOException("Read after input ownership ended")
            return super.read(bytes, offset, length)
        }

        override fun close() {
            closed = true
            super.close()
        }
    }

    private data class SampleExpectation(
        val sampleSize: Int,
        val fullWidth: Int,
        val fullHeight: Int,
        val regionWidth: Int,
        val regionHeight: Int,
    )

    private companion object {
        const val WIDTH = 65
        const val HEIGHT = 49
        val PALETTE = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.MAGENTA, Color.CYAN)
    }
}
