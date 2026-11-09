package tachiyomi.core.common.util.image

import java.io.InputStream

/** Bounded classification only: never initializes a decoder or reads a whole archive entry. */
object ImageHeader {
    enum class Format { AVIF, GIF, HEIF, JPEG, JXL, PNG, WEBP }

    data class Info(val format: Format, val animated: Boolean = false, val width: Int? = null, val height: Int? = null)

    fun read(stream: InputStream): Info? {
        val bytes = ByteArray(32)
        val reset = stream.markSupported()
        if (reset) stream.mark(bytes.size)
        val count = try {
            var count = 0
            while (count < bytes.size) {
                val read = stream.read(bytes, count, bytes.size - count)
                if (read < 0) break
                if (read == 0) {
                    val byte = stream.read()
                    if (byte < 0) break
                    bytes[count++] = byte.toByte()
                } else {
                    count += read
                }
            }
            count
        } finally {
            if (reset) stream.reset()
        }
        return parse(bytes, count)
    }

    internal fun parse(bytes: ByteArray, count: Int = bytes.size): Info? {
        fun matches(offset: Int, vararg signature: Int): Boolean =
            count >= offset + signature.size &&
                signature.indices.all { bytes[offset + it].toInt() and 255 == signature[it] }
        fun text(offset: Int, value: String): Boolean =
            count >= offset + value.length && value.indices.all { bytes[offset + it].toInt() == value[it].code }

        return when {
            matches(0, 0xFF, 0xD8, 0xFF) -> Info(Format.JPEG)
            matches(0, 137, 80, 78, 71, 13, 10, 26, 10) -> {
                fun dimension(offset: Int): Int? {
                    if (count < 24 || !text(12, "IHDR")) return null
                    var value = 0
                    for (i in offset until offset + 4) value = (value shl 8) or (bytes[i].toInt() and 255)
                    return value.takeIf { it > 0 }
                }
                Info(Format.PNG, width = dimension(16), height = dimension(20))
            }
            text(0, "GIF87a") || text(0, "GIF89a") -> Info(Format.GIF, animated = true)
            text(0, "RIFF") && text(8, "WEBP") -> Info(
                Format.WEBP,
                animated = text(12, "VP8X") && count > 20 && bytes[20].toInt() and 2 != 0,
            )
            matches(0, 0xFF, 0x0A) || matches(0, 0, 0, 0, 12, 74, 88, 76, 32, 13, 10, 135, 10) -> Info(Format.JXL)
            text(4, "ftyp") && count >= 12 -> {
                // The first brand is followed by a minor version, then compatible brands.
                val offsets = listOf(8, 16, 20, 24, 28).filter { it + 4 <= count }
                when {
                    offsets.any { text(it, "avif") || text(it, "avis") } -> Info(Format.AVIF)
                    offsets.any { offset -> HEIF_BRANDS.any { text(offset, it) } } -> Info(Format.HEIF)
                    else -> null
                }
            }
            else -> null
        }
    }

    private val HEIF_BRANDS = setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1")
}
