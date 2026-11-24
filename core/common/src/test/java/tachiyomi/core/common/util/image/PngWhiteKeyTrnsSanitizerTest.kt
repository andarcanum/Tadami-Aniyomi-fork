package tachiyomi.core.common.util.image

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import javax.imageio.ImageIO
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PngWhiteKeyTrnsSanitizerTest {

    @Test
    fun `drops white color-key tRNS on 8-bit RGB PNG even after large iCCP chunk`() {
        val iccpData = ByteArray(3144) { (it and 0xFF).toByte() }
        val trnsWhite = byteArrayOf(0x00, 0xFF.toByte(), 0x00, 0xFF.toByte(), 0x00, 0xFF.toByte())
        val raw = buildRgbPng(
            width = 2,
            height = 1,
            bitDepth = 8,
            pixels = byteArrayOf(
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFD.toByte(),
                0xFD.toByte(),
                0xFD.toByte(),
            ),
            extraPreIdatChunks = listOf("iCCP" to iccpData, "tRNS" to trnsWhite),
        )
        val expected = buildRgbPng(
            width = 2,
            height = 1,
            bitDepth = 8,
            pixels = byteArrayOf(
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFD.toByte(),
                0xFD.toByte(),
                0xFD.toByte(),
            ),
            extraPreIdatChunks = listOf("iCCP" to iccpData),
        )

        val rawImage = ImageIO.read(ByteArrayInputStream(raw))
        assertNotNull(rawImage)
        assertEquals(0, (rawImage.getRGB(0, 0) ushr 24) and 0xFF, "Unsanitized white pixel must be transparent")
        assertEquals(255, (rawImage.getRGB(1, 0) ushr 24) and 0xFF, "Near-white pixel must remain opaque")

        val sanitized = sanitize(raw)
        assertContentEquals(expected, sanitized)

        val sanitizedImage = ImageIO.read(ByteArrayInputStream(sanitized))
        assertNotNull(sanitizedImage)
        assertEquals(0xFFFFFFFF.toInt(), sanitizedImage.getRGB(0, 0), "Sanitized white pixel must be opaque white")
        assertEquals(0xFFFDFDFD.toInt(), sanitizedImage.getRGB(1, 0), "Near-white pixel must be preserved")
    }

    @Test
    fun `drops white color-key tRNS on 16-bit RGB and all grayscale bit depths`() {
        val rgb16WhiteTrns = byteArrayOf(
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
        )
        val rgb16 = buildMinimalPng(colorType = 2, bitDepth = 16, preIdat = listOf("tRNS" to rgb16WhiteTrns))
        val rgb16Expected = buildMinimalPng(colorType = 2, bitDepth = 16, preIdat = emptyList())
        assertContentEquals(rgb16Expected, sanitize(rgb16))

        for (bitDepth in listOf(1, 2, 4, 8, 16)) {
            val maxSample = (1 shl bitDepth) - 1
            val trns = byteArrayOf((maxSample ushr 8).toByte(), (maxSample and 0xFF).toByte())
            val gray = buildMinimalPng(colorType = 0, bitDepth = bitDepth, preIdat = listOf("tRNS" to trns))
            val grayExpected = buildMinimalPng(colorType = 0, bitDepth = bitDepth, preIdat = emptyList())
            assertContentEquals(grayExpected, sanitize(gray), "Expected white tRNS dropped for gray bitDepth=$bitDepth")
        }
    }

    @Test
    fun `preserves non-white color-key tRNS, palette tRNS, and RGBA PNGs verbatim`() {
        val greenKey = byteArrayOf(0x00, 0x00, 0x00, 0xFF.toByte(), 0x00, 0x00)
        val rgbGreen = buildMinimalPng(colorType = 2, bitDepth = 8, preIdat = listOf("tRNS" to greenKey))
        assertContentEquals(rgbGreen, sanitize(rgbGreen))

        val blackGrayKey = byteArrayOf(0x00, 0x00)
        val grayBlack = buildMinimalPng(colorType = 0, bitDepth = 8, preIdat = listOf("tRNS" to blackGrayKey))
        assertContentEquals(grayBlack, sanitize(grayBlack))

        val palette = buildMinimalPng(
            colorType = 3,
            bitDepth = 8,
            preIdat = listOf(
                "PLTE" to byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x00, 0x00, 0x00),
                "tRNS" to byteArrayOf(0x00, 0xFF.toByte()),
            ),
        )
        assertContentEquals(palette, sanitize(palette))

        val rgba = buildMinimalPng(colorType = 6, bitDepth = 8, preIdat = emptyList())
        assertContentEquals(rgba, sanitize(rgba))
    }

    @Test
    fun `handles single-byte reads, tiny buffer reads, and never returns zero for positive len`() {
        val trnsWhite = byteArrayOf(0x00, 0xFF.toByte(), 0x00, 0xFF.toByte(), 0x00, 0xFF.toByte())
        val raw = buildMinimalPng(
            colorType = 2,
            bitDepth = 8,
            preIdat = listOf("tEXt" to "comment\u0000hello".toByteArray(), "tRNS" to trnsWhite),
        )
        val expected = buildMinimalPng(
            colorType = 2,
            bitDepth = 8,
            preIdat = listOf("tEXt" to "comment\u0000hello".toByteArray()),
        )

        // Single-byte read()
        val viaSingle = ByteArrayOutputStream().apply {
            PngWhiteKeyTrnsSanitizer.wrap(oneByteAtATimeStream(raw)).use { stream ->
                while (true) {
                    val value = stream.read()
                    if (value < 0) break
                    write(value)
                }
            }
        }.toByteArray()
        assertContentEquals(expected, viaSingle)

        // Small buffer read(b, off, len) must never return 0 for len > 0.
        val viaSmallChunks = ByteArrayOutputStream().apply {
            val buf = ByteArray(7)
            PngWhiteKeyTrnsSanitizer.wrap(oneByteAtATimeStream(raw)).use { stream ->
                while (true) {
                    val n = stream.read(buf, 0, buf.size)
                    if (n < 0) break
                    assertTrue(n > 0, "read(b, off, len) must never return 0 when len > 0")
                    write(buf, 0, n)
                }
            }
        }.toByteArray()
        assertContentEquals(expected, viaSmallChunks)
    }

    @Test
    fun `passes non-PNG and truncated streams through untouched and propagates close`() {
        val nonPng = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)
        assertContentEquals(nonPng, sanitize(nonPng))

        val truncatedSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        assertContentEquals(truncatedSignature, sanitize(truncatedSignature))

        val trnsWhite = byteArrayOf(0x00, 0xFF.toByte(), 0x00, 0xFF.toByte(), 0x00, 0xFF.toByte())
        val valid = buildMinimalPng(colorType = 2, bitDepth = 8, preIdat = listOf("tRNS" to trnsWhite))
        val truncatedMidTrns = valid.copyOf(33 + 10)
        assertContentEquals(truncatedMidTrns, sanitize(truncatedMidTrns))

        var closed = false
        val tracking = object : ByteArrayInputStream(nonPng) {
            override fun close() {
                closed = true
                super.close()
            }
        }
        assertFalse(closed)
        PngWhiteKeyTrnsSanitizer.wrap(tracking).close()
        assertTrue(closed)
    }

    private fun sanitize(bytes: ByteArray): ByteArray =
        PngWhiteKeyTrnsSanitizer.wrap(ByteArrayInputStream(bytes)).use { it.readBytes() }

    private fun oneByteAtATimeStream(bytes: ByteArray): InputStream = object : InputStream() {
        private var pos = 0

        override fun read(): Int = if (pos < bytes.size) bytes[pos++].toInt() and 0xFF else -1

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (pos >= bytes.size) return -1
            b[off] = bytes[pos++]
            return 1
        }
    }

    private fun buildMinimalPng(
        colorType: Int,
        bitDepth: Int,
        preIdat: List<Pair<String, ByteArray>>,
    ): ByteArray = ByteArrayOutputStream().apply {
        write(PNG_SIGNATURE)
        writeChunk("IHDR", ihdrData(width = 1, height = 1, bitDepth = bitDepth, colorType = colorType))
        for ((type, data) in preIdat) {
            writeChunk(type, data)
        }
        writeChunk("IDAT", byteArrayOf(0x78, 0x9C.toByte(), 0x03, 0x00, 0x00, 0x00, 0x00, 0x01))
        writeChunk("IEND", ByteArray(0))
    }.toByteArray()

    private fun buildRgbPng(
        width: Int,
        height: Int,
        bitDepth: Int,
        pixels: ByteArray,
        extraPreIdatChunks: List<Pair<String, ByteArray>>,
    ): ByteArray {
        val rawScanlines = ByteArrayOutputStream().apply {
            val rowBytes = width * 3 * (bitDepth / 8)
            for (y in 0 until height) {
                write(0) // Filter type None
                write(pixels, y * rowBytes, rowBytes)
            }
        }.toByteArray()
        val idat = zlibCompress(rawScanlines)
        return ByteArrayOutputStream().apply {
            write(PNG_SIGNATURE)
            writeChunk("IHDR", ihdrData(width, height, bitDepth, colorType = 2))
            for ((type, data) in extraPreIdatChunks) {
                writeChunk(type, data)
            }
            writeChunk("IDAT", idat)
            writeChunk("IEND", ByteArray(0))
        }.toByteArray()
    }

    private fun ihdrData(width: Int, height: Int, bitDepth: Int, colorType: Int): ByteArray =
        ByteArrayOutputStream(13).apply {
            writeBe32(width)
            writeBe32(height)
            write(bitDepth)
            write(colorType)
            write(0)
            write(0)
            write(0)
        }.toByteArray()

    private fun ByteArrayOutputStream.writeChunk(type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        writeBe32(data.size)
        write(typeBytes)
        write(data)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }.value.toInt()
        writeBe32(crc)
    }

    private fun ByteArrayOutputStream.writeBe32(value: Int) {
        write((value ushr 24) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun zlibCompress(input: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(input)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(256)
        while (!deflater.finished()) {
            val count = deflater.deflate(buf)
            out.write(buf, 0, count)
        }
        deflater.end()
        return out.toByteArray()
    }

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )
    }
}
