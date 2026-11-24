package tachiyomi.core.common.util.image

import java.io.InputStream

/**
 * Streaming PNG filter that drops white single-color-key `tRNS` chunks on non-alpha grayscale
 * (`colorType == 0`) and RGB (`colorType == 2`) PNGs before `IDAT`.
 *
 * Some manga/webtoon PNG encoders emit an accidental `tRNS` chunk marking pure white as
 * transparent. Decoders that properly premultiply alpha turn those white pixels into `(0, 0, 0, 0)`,
 * causing black halos/cutouts over dark reader backgrounds while preserving near-white (`#FDFDFD`).
 * Real alpha channels (`colorType == 4`, `colorType == 6`), indexed palettes (`colorType == 3`),
 * and non-white color keys are passed through untouched.
 */
internal object PngWhiteKeyTrnsSanitizer {
    fun wrap(input: InputStream): InputStream = PngWhiteKeyTrnsInputStream(input)
}

private class PngWhiteKeyTrnsInputStream(
    private val upstream: InputStream,
) : InputStream() {
    private val pending = ByteArray(HEADER_AND_IHDR_BYTES)
    private var pendingPos = 0
    private var pendingEnd = 0

    private val singleByte = ByteArray(1)

    private var headerParsed = false
    private var passthrough = false
    private var chunkBytesRemaining = 0L
    private var expectedTrnsLength = 0
    private var maxSample = 0

    override fun read(): Int {
        val n = read(singleByte, 0, 1)
        return if (n < 0) -1 else singleByte[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (off < 0 || len < 0 || len > b.size - off) {
            throw IndexOutOfBoundsException()
        }
        if (len == 0) return 0

        while (true) {
            if (pendingPos < pendingEnd) {
                val count = minOf(len, pendingEnd - pendingPos)
                System.arraycopy(pending, pendingPos, b, off, count)
                pendingPos += count
                return count
            }

            if (passthrough) {
                return upstream.read(b, off, len)
            }

            if (chunkBytesRemaining > 0L) {
                val toRead = minOf(len.toLong(), chunkBytesRemaining).toInt()
                val n = upstream.read(b, off, toRead)
                if (n < 0) {
                    passthrough = true
                    chunkBytesRemaining = 0L
                    return -1
                }
                chunkBytesRemaining -= n.toLong()
                if (n > 0) return n
                continue
            }

            if (!headerParsed) {
                headerParsed = true
                val read = readFully(pending, 0, HEADER_AND_IHDR_BYTES)
                if (read == 0) {
                    passthrough = true
                    return -1
                }
                pendingPos = 0
                pendingEnd = read
                if (!parseIhdr(read)) {
                    passthrough = true
                }
                continue
            }

            val headerRead = readFully(pending, 0, CHUNK_HEADER_BYTES)
            if (headerRead == 0) {
                passthrough = true
                return -1
            }
            if (headerRead < CHUNK_HEADER_BYTES) {
                pendingPos = 0
                pendingEnd = headerRead
                passthrough = true
                continue
            }

            val chunkLen = readS32Be(pending, 0)
            if (chunkLen < 0) {
                pendingPos = 0
                pendingEnd = CHUNK_HEADER_BYTES
                passthrough = true
                continue
            }

            if (matchesType(pending, 4, 'I', 'D', 'A', 'T') ||
                matchesType(pending, 4, 'I', 'E', 'N', 'D')
            ) {
                pendingPos = 0
                pendingEnd = CHUNK_HEADER_BYTES
                passthrough = true
                continue
            }

            if (matchesType(pending, 4, 't', 'R', 'N', 'S') && chunkLen == expectedTrnsLength) {
                val bodyTarget = chunkLen + CRC_BYTES
                val bodyRead = readFully(pending, CHUNK_HEADER_BYTES, bodyTarget)
                if (bodyRead == bodyTarget && isWhiteColorKey(pending, CHUNK_HEADER_BYTES)) {
                    continue
                }
                pendingPos = 0
                pendingEnd = CHUNK_HEADER_BYTES + bodyRead
                if (bodyRead < bodyTarget) {
                    passthrough = true
                }
                continue
            }

            pendingPos = 0
            pendingEnd = CHUNK_HEADER_BYTES
            chunkBytesRemaining = chunkLen.toLong() + CRC_BYTES.toLong()
        }
    }

    override fun available(): Int = when {
        pendingPos < pendingEnd -> pendingEnd - pendingPos
        passthrough -> upstream.available()
        else -> 0
    }

    override fun close() {
        upstream.close()
    }

    private fun parseIhdr(read: Int): Boolean {
        if (read < HEADER_AND_IHDR_BYTES) return false
        for (i in PNG_SIGNATURE.indices) {
            if (pending[i] != PNG_SIGNATURE[i]) return false
        }
        if (readS32Be(pending, 8) != IHDR_DATA_BYTES) return false
        if (!matchesType(pending, 12, 'I', 'H', 'D', 'R')) return false
        val bitDepth = pending[24].toInt() and 0xFF
        val colorType = pending[25].toInt() and 0xFF
        return when (colorType) {
            PNG_COLOR_TYPE_GRAY -> {
                if (bitDepth !in VALID_GRAY_BIT_DEPTHS) return false
                expectedTrnsLength = 2
                maxSample = (1 shl bitDepth) - 1
                true
            }
            PNG_COLOR_TYPE_RGB -> {
                if (bitDepth != 8 && bitDepth != 16) return false
                expectedTrnsLength = 6
                maxSample = (1 shl bitDepth) - 1
                true
            }
            else -> false
        }
    }

    private fun isWhiteColorKey(buf: ByteArray, offset: Int): Boolean = when (expectedTrnsLength) {
        2 -> readU16Be(buf, offset) == maxSample
        6 -> readU16Be(buf, offset) == maxSample &&
            readU16Be(buf, offset + 2) == maxSample &&
            readU16Be(buf, offset + 4) == maxSample
        else -> false
    }

    private fun readFully(target: ByteArray, offset: Int, count: Int): Int {
        var total = 0
        while (total < count) {
            val n = upstream.read(target, offset + total, count - total)
            if (n < 0) break
            total += n
        }
        return total
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
        const val CHUNK_HEADER_BYTES = 8
        const val IHDR_DATA_BYTES = 13
        const val CRC_BYTES = 4
        const val HEADER_AND_IHDR_BYTES = 8 + CHUNK_HEADER_BYTES + IHDR_DATA_BYTES + CRC_BYTES

        const val PNG_COLOR_TYPE_GRAY = 0
        const val PNG_COLOR_TYPE_RGB = 2
        val VALID_GRAY_BIT_DEPTHS = setOf(1, 2, 4, 8, 16)

        fun readS32Be(buf: ByteArray, offset: Int): Int =
            ((buf[offset].toInt() and 0xFF) shl 24) or
                ((buf[offset + 1].toInt() and 0xFF) shl 16) or
                ((buf[offset + 2].toInt() and 0xFF) shl 8) or
                (buf[offset + 3].toInt() and 0xFF)

        fun readU16Be(buf: ByteArray, offset: Int): Int =
            ((buf[offset].toInt() and 0xFF) shl 8) or
                (buf[offset + 1].toInt() and 0xFF)

        fun matchesType(buf: ByteArray, offset: Int, c0: Char, c1: Char, c2: Char, c3: Char): Boolean =
            buf[offset] == c0.code.toByte() &&
                buf[offset + 1] == c1.code.toByte() &&
                buf[offset + 2] == c2.code.toByte() &&
                buf[offset + 3] == c3.code.toByte()
    }
}
