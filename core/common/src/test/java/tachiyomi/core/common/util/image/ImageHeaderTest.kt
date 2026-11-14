package tachiyomi.core.common.util.image

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
            byteArrayOf(0x49, 0x49, 0x2A, 0x00) to ImageHeader.Format.TIFF,
            byteArrayOf(0x4D, 0x4D, 0x00, 0x2A) to ImageHeader.Format.TIFF,
            byteArrayOf(-1, 0x4F, -1, 0x51) to ImageHeader.Format.JP2,
            byteArrayOf(0, 0, 0, 12, 0x6A, 0x50, 0x20, 0x20, 13, 10, -121, 10) to ImageHeader.Format.JP2,
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
    fun `parses ftyp boxes past 32 bytes without crossing box boundaries and detects animated brands`() {
        val ftypLarge = ByteArrayOutputStream().apply {
            write(be32(48))
            write("ftyp".toByteArray())
            write("mif1".toByteArray())
            write(ByteArray(4))
            repeat(7) { write("iso8".toByteArray()) }
            write("avif".toByteArray())
        }.toByteArray()
        val avifInfo = ImageHeader.read(ftypLarge.inputStream())
        assertEquals(ImageHeader.Format.AVIF, avifInfo?.format)
        assertFalse(avifInfo!!.animated)

        val avisBytes = "0000ftypavis".toByteArray()
        val avisInfo = ImageHeader.read(avisBytes.inputStream())
        assertEquals(ImageHeader.Format.AVIF, avisInfo?.format)
        assertTrue(avisInfo!!.animated)

        for (heifAnimBrand in listOf("msf1", "hevc", "hevx")) {
            val info = ImageHeader.read("0000ftyp$heifAnimBrand".toByteArray().inputStream())
            assertEquals(ImageHeader.Format.HEIF, info?.format)
            assertTrue(info!!.animated, "$heifAnimBrand should be marked animated")
        }

        // Exact 20-byte HEIF ftyp immediately followed by ASCII "avif" inside the next box must stay HEIF.
        val boundedHeif = ByteArrayOutputStream().apply {
            write(be32(20))
            write("ftyp".toByteArray())
            write("mif1".toByteArray())
            write(ByteArray(4))
            write("heic".toByteArray())
            write(be32(16))
            write("meta".toByteArray())
            write("avifavif".toByteArray())
        }.toByteArray()
        assertEquals(ImageHeader.Format.HEIF, ImageHeader.read(boundedHeif.inputStream())?.format)
    }

    @Test
    fun `extracts cheap dimensions for gif webp jpeg exif avif and jxl`() {
        val gif = "GIF89a".toByteArray() + byteArrayOf(0x40, 0x01, 0xF0.toByte(), 0x00)
        val gifInfo = ImageHeader.read(gif.inputStream())!!
        assertEquals(320, gifInfo.width)
        assertEquals(240, gifInfo.height)
        assertTrue(gifInfo.animated)

        val vp8 = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 \u0010\u0000\u0000\u0000\u0000\u0000\u0000".toByteArray() +
            byteArrayOf(0x9D.toByte(), 0x01, 0x2A, 0x20, 0x03, 0x58, 0x02) // 800 x 600
        val vp8Info = ImageHeader.read(vp8.inputStream())!!
        assertEquals(800, vp8Info.width)
        assertEquals(600, vp8Info.height)

        // VP8L: 14-bit (width-1)=499 (0x1F3), (height-1)=299 (0x12B) => (0x12B shl 14) or 0x1F3 = 0x004AC1F3
        val vp8lBits = (299 shl 14) or 499
        val vp8l = "RIFF\u0000\u0000\u0000\u0000WEBPVP8L\u0010\u0000\u0000\u0000".toByteArray() +
            byteArrayOf(
                0x2F,
                (vp8lBits and 0xFF).toByte(),
                ((vp8lBits ushr 8) and 0xFF).toByte(),
                ((vp8lBits ushr 16) and 0xFF).toByte(),
                ((vp8lBits ushr 24) and 0xFF).toByte(),
            )
        val vp8lInfo = ImageHeader.read(vp8l.inputStream())!!
        assertEquals(500, vp8lInfo.width)
        assertEquals(300, vp8lInfo.height)

        // VP8X: 1024x2048 -> width-1=1023 (0x03FF), height-1=2047 (0x07FF)
        val vp8x = "RIFF\u0000\u0000\u0000\u0000WEBPVP8X".toByteArray() +
            byteArrayOf(10, 0, 0, 0, 2, 0, 0, 0, 0xFF.toByte(), 0x03, 0, 0xFF.toByte(), 0x07, 0)
        val vp8xInfo = ImageHeader.read(vp8x.inputStream())!!
        assertTrue(vp8xInfo.animated)
        assertEquals(1024, vp8xInfo.width)
        assertEquals(2048, vp8xInfo.height)

        // JPEG with EXIF orientation=6 (90 deg rotation) + SOF0 800x1200 -> reports 1200x800
        val jpegRotated = buildJpegWithExifOrientation6AndSof0(width = 800, height = 1200)
        val jpegInfo = ImageHeader.read(jpegRotated.inputStream())!!
        assertEquals(ImageHeader.Format.JPEG, jpegInfo.format)
        assertEquals(1200, jpegInfo.width)
        assertEquals(800, jpegInfo.height)

        // AVIF with ftyp + meta (pitm + iprp(ipco(ispe 1080x1920, irot=1), ipma)) -> swaps to 1920x1080
        val avifRotated = buildAvifWithIspeAndIrot(width = 1080, height = 1920, irotAngle = 1)
        val avifMetaInfo = ImageHeader.read(avifRotated.inputStream())!!
        assertEquals(ImageHeader.Format.AVIF, avifMetaInfo.format)
        assertEquals(1920, avifMetaInfo.width)
        assertEquals(1080, avifMetaInfo.height)

        // Raw JXL codestream div8=1: hDiv8m1=9 (80px), ratio=0, wDiv8m1=14 (120px)
        // Bits LSB-first: [0]=1, [1..5]=01001, [6..8]=000, [9..13]=01110 -> 16-bit word = 0x1C13
        val jxlRaw = byteArrayOf(0xFF.toByte(), 0x0A, 0x13, 0x1C)
        val jxlInfo = ImageHeader.read(jxlRaw.inputStream())!!
        assertEquals(ImageHeader.Format.JXL, jxlInfo.format)
        assertEquals(120, jxlInfo.width)
        assertEquals(80, jxlInfo.height)
    }

    @Test
    fun `peek replays exact bytes on a non-markable stream`() {
        val jpeg = buildJpegWithExifOrientation6AndSof0(width = 640, height = 480) + byteArrayOf(1, 2, 3, 4, 5)
        val nonMarkable = object : InputStream() {
            private var pos = 0
            override fun markSupported(): Boolean = false
            override fun read(): Int = if (pos < jpeg.size) jpeg[pos++].toInt() and 0xFF else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val b0 = read()
                if (b0 < 0) return -1
                b[off] = b0.toByte()
                return 1
            }
        }
        val peeked = ImageHeader.peek(nonMarkable)
        assertNotNull(peeked.info)
        assertEquals(ImageHeader.Format.JPEG, peeked.info.format)
        assertEquals(480, peeked.info.width)
        assertEquals(640, peeked.info.height)
        assertContentEquals(jpeg, peeked.stream.readBytes())
    }

    @Test
    fun `partial and nonimage headers are not guessed`() {
        for (bytes in listOf(byteArrayOf(), byteArrayOf(-1), "GIF8".toByteArray(), "0000ftypmp42".toByteArray())) {
            assertNull(ImageHeader.read(bytes.inputStream()))
        }
    }

    @Test
    fun `corrupt 64-bit zero and overflow box sizes terminate safely`() {
        val jp2Sig = byteArrayOf(0, 0, 0, 12, 'j'.code.toByte(), 'P'.code.toByte(), 32, 32, 13, 10, -121, 10)
        val jp2Ftyp = be32(20) + "ftypjp2 \u0000\u0000\u0000\u0000jp2 ".toByteArray()
        val jxlSig = byteArrayOf(
            0, 0, 0, 12, 'J'.code.toByte(), 'X'.code.toByte(), 'L'.code.toByte(), 32, 13, 10, -121, 10,
        )
        val avifFtyp = be32(20) + "ftypavif\u0000\u0000\u0000\u0000mif1".toByteArray()

        val corruptSizes = listOf(
            byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0),
            byteArrayOf(0, 0, 0, 0, 0, 0, 0, 15),
            byteArrayOf(-128, 0, 0, 0, 0, 0, 0, 0),
            byteArrayOf(127, -1, -1, -1, -1, -1, -1, -1),
        )
        for (size64 in corruptSizes) {
            val badBox = be32(1) + "uuid".toByteArray() + size64 + ByteArray(8)
            val jp2Info = ImageHeader.read((jp2Sig + jp2Ftyp + badBox).inputStream())
            assertEquals(ImageHeader.Format.JP2, jp2Info?.format)
            assertNull(jp2Info?.width)

            val jxlInfo = ImageHeader.read((jxlSig + badBox).inputStream())
            assertEquals(ImageHeader.Format.JXL, jxlInfo?.format)
            assertNull(jxlInfo?.width)

            val avifInfo = ImageHeader.read((avifFtyp + badBox).inputStream())
            assertEquals(ImageHeader.Format.AVIF, avifInfo?.format)
            assertNull(avifInfo?.width)
        }
    }

    @Test
    fun `peek replay stream clamps available against Int MAX_VALUE`() {
        val jpeg = buildJpegWithExifOrientation6AndSof0(width = 640, height = 480)
        val backing = ByteArrayInputStream(jpeg)
        val hugeAvailableStream = object : InputStream() {
            override fun read(): Int = backing.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = backing.read(b, off, len)
            override fun available(): Int = Int.MAX_VALUE
        }

        val peeked = ImageHeader.peek(hugeAvailableStream)
        assertEquals(ImageHeader.Format.JPEG, peeked.info?.format)
        assertEquals(Int.MAX_VALUE, peeked.stream.available())
    }

    private fun be32(value: Int): ByteArray = byteArrayOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    private fun buildJpegWithExifOrientation6AndSof0(width: Int, height: Int): ByteArray {
        val app1 = byteArrayOf(
            0xFF.toByte(), 0xE1.toByte(), 0x00, 0x22,
            0x45, 0x78, 0x69, 0x66, 0x00, 0x00,
            0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00,
            0x01, 0x00,
            0x12, 0x01, 0x03, 0x00,
            0x01, 0x00, 0x00, 0x00,
            0x06, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
        )
        val sof0 = byteArrayOf(
            0xFF.toByte(), 0xC0.toByte(), 0x00, 0x0B, 0x08,
            ((height ushr 8) and 0xFF).toByte(), (height and 0xFF).toByte(),
            ((width ushr 8) and 0xFF).toByte(), (width and 0xFF).toByte(),
            0x01, 0x11, 0x00, 0x00,
        )
        return byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + app1 + sof0 + byteArrayOf(0xFF.toByte(), 0xDA.toByte())
    }

    private fun buildAvifWithIspeAndIrot(width: Int, height: Int, irotAngle: Int): ByteArray {
        fun box(type: String, payload: ByteArray): ByteArray {
            return be32(8 + payload.size) + type.toByteArray() + payload
        }
        val ftyp = box("ftyp", "avif".toByteArray() + ByteArray(4) + "mif1".toByteArray())
        val pitm = box("pitm", byteArrayOf(0, 0, 0, 0, 0, 1))
        val ispe = box("ispe", ByteArray(4) + be32(width) + be32(height))
        val irot = box("irot", byteArrayOf((irotAngle and 3).toByte()))
        val ipco = box("ipco", ispe + irot)
        val ipma = box("ipma", byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 2, 1, 2))
        val iprp = box("iprp", ipco + ipma)
        val meta = box("meta", ByteArray(4) + pitm + iprp)
        return ftyp + meta
    }
}
