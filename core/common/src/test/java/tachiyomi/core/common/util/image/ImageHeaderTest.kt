package tachiyomi.core.common.util.image

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageHeaderTest {
    @Test
    fun pngDimensionsAreReadFromTheBoundedIhdrHeader() {
        val bytes =
            byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 3, -24, 0, 0, -100, 64)
        val info = ImageHeader.read(bytes.inputStream())!!
        assertEquals(1000, info.width)
        assertEquals(40000, info.height)
        assertNull(ImageHeader.read(bytes.copyOf(20).inputStream())!!.height)
    }

    @Test
    fun `classifies supported signatures without native loading`() {
        val cases = listOf(
            byteArrayOf(-1, -40, -1) to ImageHeader.Format.JPEG,
            byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10) to ImageHeader.Format.PNG,
            "GIF87a".toByteArray() to ImageHeader.Format.GIF,
            "GIF89a".toByteArray() to ImageHeader.Format.GIF,
            byteArrayOf(-1, 10) to ImageHeader.Format.JXL,
            byteArrayOf(0, 0, 0, 12, 74, 88, 76, 32, 13, 10, -121, 10) to ImageHeader.Format.JXL,
            "RIFF0000WEBPVP8 ".toByteArray() to ImageHeader.Format.WEBP,
            "0000ftypavif".toByteArray() to ImageHeader.Format.AVIF,
            "0000ftypheic".toByteArray() to ImageHeader.Format.HEIF,
            "0000ftypmif10000avif".toByteArray() to ImageHeader.Format.AVIF,
        )
        cases.forEach { (bytes, format) -> assertEquals(format, ImageHeader.read(bytes.inputStream())?.format) }
    }

    @Test
    fun `short reads accumulate and never consume more than 32 bytes`() {
        val bytes = "GIF89a".toByteArray() + ByteArray(4096)
        var consumed = 0
        val stream = object : InputStream() {
            override fun read(): Int = if (consumed < bytes.size) bytes[consumed++].toInt() and 255 else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val value = read()
                if (value < 0) return -1
                b[off] = value.toByte()
                return 1
            }
        }
        assertEquals(ImageHeader.Format.GIF, ImageHeader.read(stream)?.format)
        assertEquals(32, consumed)
    }

    @Test
    fun `markable stream position and ownership are preserved`() {
        var closed = false
        val stream = object : ByteArrayInputStream("GIF89a".toByteArray()) {
            override fun close() {
                closed = true
            }
        }
        ImageHeader.read(stream)
        assertEquals('G'.code, stream.read())
        assertFalse(closed)
    }

    @Test
    fun `only extended WebP animation flag chooses animation`() {
        val bytes = "RIFF0000WEBPVP8X".toByteArray() + byteArrayOf(10, 0, 0, 0, 2) + ByteArray(11)
        assertTrue(ImageHeader.read(bytes.inputStream())!!.animated)
        bytes[20] = 0
        assertFalse(ImageHeader.read(bytes.inputStream())!!.animated)
        assertFalse(ImageHeader.read("RIFF0000WEBPVP8 ".toByteArray().inputStream())!!.animated)
    }

    @Test
    fun `partial and nonimage headers are not guessed`() {
        for (bytes in listOf(byteArrayOf(), byteArrayOf(-1), "GIF8".toByteArray(), "0000ftypmp42".toByteArray())) {
            assertNull(ImageHeader.read(bytes.inputStream()))
        }
    }
}
