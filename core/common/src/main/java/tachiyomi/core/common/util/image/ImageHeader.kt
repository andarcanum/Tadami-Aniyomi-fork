package tachiyomi.core.common.util.image

import java.io.InputStream
import kotlin.math.min

/**
 * Bounded header inspector: classifies format, animation status, and intrinsic pixel dimensions
 * without initializing a native decoder or exhausting large streams.
 */
object ImageHeader {
    enum class Format { AVIF, GIF, HEIF, JPEG, JP2, JXL, PNG, TIFF, WEBP }

    data class Info(
        val format: Format,
        val animated: Boolean = false,
        val width: Int? = null,
        val height: Int? = null,
    )

    /** Result of non-destructively peeking [stream]; callers must read from the returned [stream]. */
    data class PeekResult(val info: Info?, val stream: InputStream)

    private const val INITIAL_PROBE_BYTES = 32
    private const val MAX_FTYP_BYTES = 4096
    private const val MAX_PROBE_BYTES = 65_536

    private val AVIF_BRANDS = setOf("avif", "avis")
    private val HEIF_BRANDS = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs", "mif1", "msf1")
    private val ANIMATED_HEIF_BRANDS = setOf("msf1", "hevc", "hevx", "hevs")
    private val JP2_BRANDS = setOf("jp2 ", "jpx ", "jpm ")

    fun read(stream: InputStream): Info? = readInternal(stream, replay = false).info

    /**
     * Reads bounded header metadata without losing bytes even when [stream] does not support
     * [InputStream.mark]. Callers continue reading from [PeekResult.stream].
     */
    fun peek(stream: InputStream): PeekResult = readInternal(stream, replay = true)

    internal fun parse(bytes: ByteArray, count: Int = bytes.size): Info? =
        inspect(ByteSliceSource(bytes, min(count, bytes.size)))

    private fun readInternal(stream: InputStream, replay: Boolean): PeekResult {
        val markSupported = stream.markSupported()
        if (markSupported) stream.mark(MAX_PROBE_BYTES)
        val source = StreamProbeSource(stream)
        val info = try {
            source.ensure(INITIAL_PROBE_BYTES)
            inspect(source)
        } finally {
            if (markSupported) stream.reset()
        }
        val outStream = if (markSupported || !replay) {
            stream
        } else {
            ReplayInputStream(source.buffer, source.count, stream)
        }
        return PeekResult(info, outStream)
    }

    private fun inspect(source: ProbeSource): Info? {
        if (source.count == 0) return null
        return when {
            source.matches(0, 0xFF, 0xD8, 0xFF) -> parseJpeg(source)
            source.matches(0, 137, 80, 78, 71, 13, 10, 26, 10) -> parsePng(source)
            source.text(0, "GIF87a") || source.text(0, "GIF89a") -> parseGif(source)
            source.text(0, "RIFF") && source.text(8, "WEBP") -> parseWebp(source)
            source.matches(0, 0x49, 0x49, 0x2A, 0x00) ||
                source.matches(0, 0x4D, 0x4D, 0x00, 0x2A) -> Info(Format.TIFF)
            source.matches(0, 0xFF, 0x4F, 0xFF, 0x51) -> parseJ2kCodestream(source)
            source.matches(0, 0x00, 0x00, 0x00, 0x0C, 0x6A, 0x50, 0x20, 0x20, 0x0D, 0x0A, 0x87, 0x0A) ->
                parseJp2Container(source)
            source.matches(0, 0xFF, 0x0A) -> parseRawJxl(source)
            source.matches(0, 0, 0, 0, 12, 74, 88, 76, 32, 13, 10, 135, 10) -> parseJxlContainer(source)
            source.text(4, "ftyp") && source.count >= 12 -> parseFtyp(source)
            else -> null
        }
    }

    private fun parsePng(source: ProbeSource): Info {
        if (source.count < 24 || !source.text(12, "IHDR")) return Info(Format.PNG)
        val width = source.s32be(16)?.takeIf { it > 0 }
        val height = source.s32be(20)?.takeIf { it > 0 }
        return Info(Format.PNG, width = width, height = height)
    }

    private fun parseGif(source: ProbeSource): Info {
        if (source.count < 10) return Info(Format.GIF, animated = true)
        val width = source.u16le(6).takeIf { it > 0 }
        val height = source.u16le(8).takeIf { it > 0 }
        return Info(Format.GIF, animated = true, width = width, height = height)
    }

    private fun parseWebp(source: ProbeSource): Info {
        val count = source.count
        return when {
            source.text(12, "VP8X") -> {
                val animated = count > 20 && (source.byteAt(20) and 2) != 0
                val chunkValid = count >= 30 && source.u32le(16) >= 10L
                val width = if (chunkValid) (source.u24le(24) + 1).takeIf { it > 0 } else null
                val height = if (chunkValid) (source.u24le(27) + 1).takeIf { it > 0 } else null
                Info(Format.WEBP, animated = animated, width = width, height = height)
            }
            source.text(12, "VP8L") -> {
                if (count < 25 || source.byteAt(20) != 0x2F) return Info(Format.WEBP)
                val bits = source.u32le(21)
                val width = ((bits and 0x3FFFL).toInt() + 1).takeIf { it > 0 }
                val height = (((bits ushr 14) and 0x3FFFL).toInt() + 1).takeIf { it > 0 }
                Info(Format.WEBP, width = width, height = height)
            }
            source.text(12, "VP8 ") -> {
                if (count < 30 || !source.matches(23, 0x9D, 0x01, 0x2A)) return Info(Format.WEBP)
                val width = (source.u16le(26) and 0x3FFF).takeIf { it > 0 }
                val height = (source.u16le(28) and 0x3FFF).takeIf { it > 0 }
                Info(Format.WEBP, width = width, height = height)
            }
            else -> Info(Format.WEBP)
        }
    }

    private fun parseJpeg(source: ProbeSource): Info {
        var pos = 2
        var swapAxes = false
        while (source.ensure(pos + 2)) {
            if (source.byteAt(pos) != 0xFF) {
                pos++
                continue
            }
            while (source.ensure(pos + 2) && source.byteAt(pos + 1) == 0xFF) {
                pos++
            }
            if (!source.ensure(pos + 2)) break
            val marker = source.byteAt(pos + 1)
            if (marker == 0xD9 || marker == 0xDA) break
            if (marker == 0x00 || marker == 0x01 || marker in 0xD0..0xD8) {
                pos += 2
                continue
            }
            if (!source.ensure(pos + 4)) break
            val segLen = source.u16be(pos + 2)
            if (segLen < 2) break
            val nextPos = pos + 2 + segLen
            if (marker == 0xE1) {
                if (nextPos > MAX_PROBE_BYTES || !source.ensure(nextPos)) break
                if (source.text(pos + 4, "Exif") && source.matches(pos + 8, 0x00, 0x00)) {
                    if (parseExifSwapAxes(source, pos + 10, nextPos)) swapAxes = true
                }
            } else if (isSofMarker(marker)) {
                val needed = pos + 9
                if (nextPos >= needed && source.ensure(needed)) {
                    val sofH = source.u16be(pos + 5).takeIf { it > 0 }
                    val sofW = source.u16be(pos + 7).takeIf { it > 0 }
                    val width = if (swapAxes) sofH else sofW
                    val height = if (swapAxes) sofW else sofH
                    return Info(Format.JPEG, width = width, height = height)
                }
                break
            }
            pos = nextPos
        }
        return Info(Format.JPEG)
    }

    private fun isSofMarker(marker: Int): Boolean =
        marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC

    private fun parseExifSwapAxes(source: ProbeSource, tiffStart: Int, tiffEnd: Int): Boolean {
        if (tiffEnd - tiffStart < 8) return false
        val littleEndian = when {
            source.text(tiffStart, "II") -> true
            source.text(tiffStart, "MM") -> false
            else -> return false
        }
        if (source.u16(tiffStart + 2, littleEndian) != 42) return false
        val ifdOffset = source.u32(tiffStart + 4, littleEndian)
        if (ifdOffset < 8L || tiffStart + ifdOffset + 2L > tiffEnd) return false
        val ifdPos = tiffStart + ifdOffset.toInt()
        val entries = source.u16(ifdPos, littleEndian)
        for (i in 0 until entries) {
            val entryPos = ifdPos + 2 + i * 12
            if (entryPos + 12 > tiffEnd) break
            if (source.u16(entryPos, littleEndian) == 0x0112) {
                val type = source.u16(entryPos + 2, littleEndian)
                val count = source.u32(entryPos + 4, littleEndian)
                if (type == 3 && count == 1L) {
                    return source.u16(entryPos + 8, littleEndian) in 5..8
                }
                return false
            }
        }
        return false
    }

    private fun parseJ2kCodestream(source: ProbeSource): Info {
        if (source.count < 16) return Info(Format.JP2)
        val width = source.u32be(8).takeIf { it in 1L..Int.MAX_VALUE.toLong() }?.toInt()
        val height = source.u32be(12).takeIf { it in 1L..Int.MAX_VALUE.toLong() }?.toInt()
        return Info(Format.JP2, width = width, height = height)
    }

    private class BoxHeader(val headerLen: Int, val boxSize: Long)

    private fun readBoxHeader(source: ProbeSource, pos: Int): BoxHeader? {
        if (!source.ensure(pos + 8)) return null
        val size32 = source.u32be(pos)
        return if (size32 == 1L) {
            if (!source.ensure(pos + 16)) return null
            val boxSize = source.u64be(pos + 8)
            // Guard against 0..15 and signed negative overflow (>= 2^63).
            if (boxSize < 16L) return null
            BoxHeader(16, boxSize)
        } else {
            if (size32 < 8L) return null
            BoxHeader(8, size32)
        }
    }

    private fun parseJp2Container(source: ProbeSource): Info {
        if (!source.ensure(24)) return Info(Format.JP2)
        // Standard JP2 starts with signature box (12B) followed by ftyp + ihdr inside jp2h.
        var pos = 12
        while (true) {
            val box = readBoxHeader(source, pos) ?: break
            val type = source.ascii4(pos + 4)
            if (type == "jp2c" || type == "mdat") break
            if (box.boxSize > (MAX_PROBE_BYTES - pos).toLong()) break
            val endPos = (pos + box.boxSize).toInt()
            val contentStart = pos + box.headerLen
            if (type == "jp2h" && source.ensure(endPos)) {
                var child = contentStart
                while (child + 8 <= endPos) {
                    val cSize = source.u32be(child)
                    if (cSize < 8L || child + cSize > endPos) break
                    if (source.ascii4(child + 4) == "ihdr" && child + 22 <= endPos) {
                        val h = source.u32be(child + 8).takeIf { it in 1L..Int.MAX_VALUE.toLong() }?.toInt()
                        val w = source.u32be(child + 12).takeIf { it in 1L..Int.MAX_VALUE.toLong() }?.toInt()
                        return Info(Format.JP2, width = w, height = h)
                    }
                    child += cSize.toInt()
                }
            }
            pos = endPos
        }
        return Info(Format.JP2)
    }

    private fun parseRawJxl(source: ProbeSource): Info {
        val dims = parseJxlSizeHeader(source, 2, source.count)
        return Info(Format.JXL, width = dims?.first, height = dims?.second)
    }

    private fun parseJxlContainer(source: ProbeSource): Info {
        var pos = 12
        while (true) {
            val box = readBoxHeader(source, pos) ?: break
            val type = source.ascii4(pos + 4)
            val contentStart = pos + box.headerLen
            if (type == "jxlc") {
                val probeLen = min(box.boxSize, (contentStart + 16 - pos).toLong()).toInt()
                val probeEnd = pos + probeLen
                if (probeEnd <= MAX_PROBE_BYTES && source.ensure(probeEnd) &&
                    source.matches(contentStart, 0xFF, 0x0A)
                ) {
                    val dims = parseJxlSizeHeader(source, contentStart + 2, probeEnd)
                    return Info(Format.JXL, width = dims?.first, height = dims?.second)
                }
                break
            }
            if (type == "jxlp" && contentStart + 4 <= MAX_PROBE_BYTES && source.ensure(contentStart + 4)) {
                val partIndex = source.u32be(contentStart)
                if ((partIndex and 0x7FFF_FFFFL) == 0L) {
                    val codestreamStart = contentStart + 4
                    val probeLen = min(box.boxSize, (codestreamStart + 16 - pos).toLong()).toInt()
                    val probeEnd = pos + probeLen
                    if (probeEnd <= MAX_PROBE_BYTES && source.ensure(probeEnd) &&
                        source.matches(codestreamStart, 0xFF, 0x0A)
                    ) {
                        val dims = parseJxlSizeHeader(source, codestreamStart + 2, probeEnd)
                        return Info(Format.JXL, width = dims?.first, height = dims?.second)
                    }
                }
            }
            if (box.boxSize > (MAX_PROBE_BYTES - pos).toLong()) break
            pos += box.boxSize.toInt()
        }
        return Info(Format.JXL)
    }

    private fun parseJxlSizeHeader(source: ProbeSource, offset: Int, limit: Int): Pair<Int, Int>? {
        if (limit - offset < 2) return null
        val reader = JxlBitReader(source, offset, limit)
        val div8 = reader.readBits(1) ?: return null
        val height: Int
        if (div8 == 1) {
            val hDiv8m1 = reader.readBits(5) ?: return null
            height = (hDiv8m1 + 1) * 8
        } else {
            val h = reader.readU32() ?: return null
            if (h <= 0L || h > Int.MAX_VALUE) return null
            height = h.toInt()
        }
        val ratio = reader.readBits(3) ?: return null
        val width = if (ratio == 0) {
            if (div8 == 1) {
                val wDiv8m1 = reader.readBits(5) ?: return null
                (wDiv8m1 + 1) * 8
            } else {
                val w = reader.readU32() ?: return null
                if (w <= 0L || w > Int.MAX_VALUE) return null
                w.toInt()
            }
        } else {
            when (ratio) {
                1 -> height
                2 -> (height.toLong() * 12L / 10L)
                3 -> (height.toLong() * 4L / 3L)
                4 -> (height.toLong() * 3L / 2L)
                5 -> (height.toLong() * 16L / 9L)
                6 -> (height.toLong() * 5L / 4L)
                7 -> (height.toLong() * 2L)
                else -> return null
            }.takeIf { it in 1L..Int.MAX_VALUE.toLong() }?.toInt() ?: return null
        }
        return width to height
    }

    private fun parseFtyp(source: ProbeSource): Info? {
        val rawFtypSize = source.u32be(0)
        val validBoxSize = rawFtypSize in 12L..MAX_FTYP_BYTES.toLong()
        if (validBoxSize) {
            source.ensure(rawFtypSize.toInt())
        }
        val ftypEnd = if (validBoxSize) {
            min(source.count, rawFtypSize.toInt())
        } else {
            min(source.count, MAX_FTYP_BYTES)
        }
        var hasAvif = false
        var hasAvis = false
        var hasHeif = false
        var hasAnimatedHeif = false
        var hasJp2 = false

        fun inspectBrand(offset: Int) {
            if (offset + 4 > ftypEnd) return
            val brand = source.ascii4(offset)
            if (brand in AVIF_BRANDS) {
                hasAvif = true
                if (brand == "avis") hasAvis = true
            } else if (brand in HEIF_BRANDS) {
                hasHeif = true
                if (brand in ANIMATED_HEIF_BRANDS) hasAnimatedHeif = true
            } else if (brand in JP2_BRANDS) {
                hasJp2 = true
            }
        }

        inspectBrand(8)
        var offset = 16
        while (offset + 4 <= ftypEnd) {
            inspectBrand(offset)
            offset += 4
        }

        val format = when {
            hasAvif -> Format.AVIF
            hasHeif -> Format.HEIF
            hasJp2 -> Format.JP2
            else -> return null
        }
        val animated = when (format) {
            Format.AVIF -> hasAvis
            Format.HEIF -> hasAnimatedHeif
            else -> false
        }
        val dims = if (validBoxSize && (format == Format.AVIF || format == Format.HEIF)) {
            parseBmffDimensions(source, rawFtypSize.toInt())
        } else {
            null
        }
        return Info(format, animated = animated, width = dims?.first, height = dims?.second)
    }

    private fun parseBmffDimensions(source: ProbeSource, startOffset: Int): Pair<Int, Int>? {
        var pos = startOffset
        while (true) {
            val box = readBoxHeader(source, pos) ?: break
            val type = source.ascii4(pos + 4)
            if (type == "mdat") break
            val clampedEnd = if (box.boxSize > (MAX_PROBE_BYTES - pos).toLong()) {
                MAX_PROBE_BYTES + 1L
            } else {
                pos + box.boxSize
            }
            if (type == "meta") {
                val metaDims = parseMetaDimensions(source, pos + box.headerLen + 4, clampedEnd)
                if (metaDims != null) return metaDims
            }
            if (clampedEnd > MAX_PROBE_BYTES) break
            val boxEnd = clampedEnd.toInt()
            if (type == "moov") {
                if (!source.ensure(boxEnd)) break
                val moovDims = parseMoovDimensions(source, pos + box.headerLen, boxEnd)
                if (moovDims != null) return moovDims
            }
            pos = boxEnd
        }
        return null
    }

    private fun parseMetaDimensions(source: ProbeSource, start: Int, endLong: Long): Pair<Int, Int>? {
        val end = min(endLong, MAX_PROBE_BYTES.toLong()).toInt()
        if (start >= end) return null
        var primaryItemId: Long? = null
        val ispeByPropIndex = mutableMapOf<Int, Pair<Int, Int>>()
        val swapByPropIndex = mutableSetOf<Int>()
        var ipmaStart = -1
        var ipmaEnd = -1

        var pos = start
        while (pos + 8 <= end && source.ensure(pos + 8)) {
            val boxSize = source.u32be(pos)
            if (boxSize < 8L || pos + boxSize > end) break
            val boxEnd = (pos + boxSize).toInt()
            val type = source.ascii4(pos + 4)
            if (type == "idat") break
            when (type) {
                "pitm" -> {
                    if (source.ensure(boxEnd) && pos + 14 <= boxEnd) {
                        val version = source.byteAt(pos + 8)
                        primaryItemId = if (version == 0) {
                            source.u16be(pos + 12).toLong()
                        } else if (pos + 16 <= boxEnd) {
                            source.u32be(pos + 12)
                        } else {
                            null
                        }
                    }
                }
                "iprp" -> {
                    if (!source.ensure(boxEnd)) break
                    var child = pos + 8
                    while (child + 8 <= boxEnd) {
                        val cSize = source.u32be(child)
                        if (cSize < 8L || child + cSize > boxEnd) break
                        val cEnd = (child + cSize).toInt()
                        val cType = source.ascii4(child + 4)
                        if (cType == "ipco") {
                            var propIdx = 1
                            var prop = child + 8
                            while (prop + 8 <= cEnd) {
                                val pSize = source.u32be(prop)
                                if (pSize < 8L || prop + pSize > cEnd) break
                                val pEnd = (prop + pSize).toInt()
                                val pType = source.ascii4(prop + 4)
                                if (pType == "ispe" && prop + 20 <= pEnd) {
                                    val w = source.u32be(prop + 12).takeIf { it in 1L..Int.MAX_VALUE.toLong() }?.toInt()
                                    val h = source.u32be(prop + 16).takeIf { it in 1L..Int.MAX_VALUE.toLong() }?.toInt()
                                    if (w != null && h != null) {
                                        ispeByPropIndex[propIdx] = w to h
                                    }
                                } else if (pType == "irot" && prop + 9 <= pEnd) {
                                    val angle = source.byteAt(prop + 8) and 0x03
                                    if ((angle and 1) == 1) {
                                        swapByPropIndex.add(propIdx)
                                    }
                                }
                                propIdx++
                                prop = pEnd
                            }
                        } else if (cType == "ipma") {
                            ipmaStart = child + 8
                            ipmaEnd = cEnd
                        }
                        child = cEnd
                    }
                }
            }
            pos = boxEnd
        }

        if (ipmaStart >= 0 && primaryItemId != null) {
            val matched = findPrimaryProperties(source, ipmaStart, ipmaEnd, primaryItemId)
            if (matched != null) {
                val baseDims = matched.firstNotNullOfOrNull { ispeByPropIndex[it] }
                if (baseDims != null) {
                    val swap = matched.any { it in swapByPropIndex }
                    return if (swap) baseDims.second to baseDims.first else baseDims
                }
            }
        }
        return if ((ipmaStart < 0 || primaryItemId == null) && ispeByPropIndex.size == 1) {
            val dims = ispeByPropIndex.entries.first().value
            if (swapByPropIndex.size == 1) {
                dims.second to dims.first
            } else {
                dims
            }
        } else {
            null
        }
    }

    private fun findPrimaryProperties(
        source: ProbeSource,
        ipmaStart: Int,
        ipmaEnd: Int,
        primaryItemId: Long,
    ): List<Int>? {
        if (ipmaStart + 8 > ipmaEnd) return null
        val version = source.byteAt(ipmaStart)
        val flags = (source.byteAt(ipmaStart + 1) shl 16) or
            (source.byteAt(ipmaStart + 2) shl 8) or
            source.byteAt(ipmaStart + 3)
        val entryCount = source.u32be(ipmaStart + 4).coerceAtMost(1024L).toInt()
        val largeProp = (flags and 1) != 0
        var pos = ipmaStart + 8
        for (i in 0 until entryCount) {
            val idSize = if (version < 1) 2 else 4
            if (pos + idSize + 1 > ipmaEnd) break
            val itemId = if (version < 1) source.u16be(pos).toLong() else source.u32be(pos)
            pos += idSize
            val assocCount = source.byteAt(pos)
            pos++
            val assocBytes = assocCount * (if (largeProp) 2 else 1)
            if (pos + assocBytes > ipmaEnd) break
            if (itemId == primaryItemId) {
                val props = ArrayList<Int>(assocCount)
                for (j in 0 until assocCount) {
                    val propIndex = if (largeProp) {
                        val v = source.u16be(pos)
                        pos += 2
                        v and 0x7FFF
                    } else {
                        val v = source.byteAt(pos)
                        pos++
                        v and 0x7F
                    }
                    if (propIndex > 0) props.add(propIndex)
                }
                return props
            }
            pos += assocBytes
        }
        return null
    }

    private fun parseMoovDimensions(source: ProbeSource, start: Int, end: Int): Pair<Int, Int>? {
        var pos = start
        while (pos + 8 <= end) {
            val boxSize = source.u32be(pos)
            if (boxSize < 8L || pos + boxSize > end) break
            val boxEnd = (pos + boxSize).toInt()
            if (source.ascii4(pos + 4) == "trak") {
                var child = pos + 8
                while (child + 8 <= boxEnd) {
                    val cSize = source.u32be(child)
                    if (cSize < 8L || child + cSize > boxEnd) break
                    val cEnd = (child + cSize).toInt()
                    if (source.ascii4(child + 4) == "tkhd" && cEnd - child >= 92) {
                        // tkhd width and height are unsigned 16.16 fixed-point at the final 8 bytes.
                        val w = source.u16be(cEnd - 8).takeIf { it > 0 }
                        val h = source.u16be(cEnd - 4).takeIf { it > 0 }
                        if (w != null && h != null) return w to h
                    }
                    child = cEnd
                }
            }
            pos = boxEnd
        }
        return null
    }

    private interface ProbeSource {
        val count: Int
        fun ensure(target: Int): Boolean
        fun byteAt(offset: Int): Int
        fun matches(offset: Int, vararg signature: Int): Boolean =
            count >= offset + signature.size &&
                signature.indices.all { byteAt(offset + it) == signature[it] }
        fun text(offset: Int, value: String): Boolean =
            count >= offset + value.length &&
                value.indices.all { byteAt(offset + it) == value[it].code }
        fun ascii4(offset: Int): String = charArrayOf(
            byteAt(offset).toChar(),
            byteAt(offset + 1).toChar(),
            byteAt(offset + 2).toChar(),
            byteAt(offset + 3).toChar(),
        ).concatToString()
        fun u16be(offset: Int): Int = (byteAt(offset) shl 8) or byteAt(offset + 1)
        fun u16le(offset: Int): Int = byteAt(offset) or (byteAt(offset + 1) shl 8)
        fun u24le(offset: Int): Int =
            byteAt(offset) or (byteAt(offset + 1) shl 8) or (byteAt(offset + 2) shl 16)
        fun u16(offset: Int, littleEndian: Boolean): Int =
            if (littleEndian) u16le(offset) else u16be(offset)
        fun s32be(offset: Int): Int =
            (byteAt(offset) shl 24) or (byteAt(offset + 1) shl 16) or
                (byteAt(offset + 2) shl 8) or byteAt(offset + 3)
        fun u32be(offset: Int): Long = s32be(offset).toLong() and 0xFFFF_FFFFL
        fun u32le(offset: Int): Long =
            (byteAt(offset).toLong() and 0xFFL) or
                ((byteAt(offset + 1).toLong() and 0xFFL) shl 8) or
                ((byteAt(offset + 2).toLong() and 0xFFL) shl 16) or
                ((byteAt(offset + 3).toLong() and 0xFFL) shl 24)
        fun u32(offset: Int, littleEndian: Boolean): Long =
            if (littleEndian) u32le(offset) else u32be(offset)
        fun u64be(offset: Int): Long =
            (u32be(offset) shl 32) or u32be(offset + 4)
    }

    private class ByteSliceSource(
        private val bytes: ByteArray,
        override val count: Int,
    ) : ProbeSource {
        override fun ensure(target: Int): Boolean = target <= count
        override fun byteAt(offset: Int): Int = bytes[offset].toInt() and 0xFF
    }

    private class StreamProbeSource(private val stream: InputStream) : ProbeSource {
        var buffer = ByteArray(INITIAL_PROBE_BYTES)
            private set
        override var count: Int = 0
            private set
        private var eof = false

        override fun ensure(target: Int): Boolean {
            if (target <= count) return true
            if (eof || target > MAX_PROBE_BYTES) return false
            if (target > buffer.size) {
                var newSize = buffer.size
                while (newSize < target) newSize = min(newSize * 2, MAX_PROBE_BYTES)
                buffer = buffer.copyOf(newSize)
            }
            while (!eof && count < target) {
                val read = stream.read(buffer, count, target - count)
                if (read < 0) {
                    eof = true
                    break
                }
                if (read == 0) {
                    val b = stream.read()
                    if (b < 0) {
                        eof = true
                        break
                    }
                    buffer[count++] = b.toByte()
                } else {
                    count += read
                }
            }
            return count >= target
        }

        override fun byteAt(offset: Int): Int = buffer[offset].toInt() and 0xFF
    }

    private class JxlBitReader(
        private val source: ProbeSource,
        private val startOffset: Int,
        private val limitOffset: Int,
    ) {
        private var bitOffset = 0
        private val totalBits = (limitOffset - startOffset) * 8

        fun readBits(count: Int): Int? {
            if (bitOffset + count > totalBits) return null
            var result = 0
            for (i in 0 until count) {
                val bitIndex = bitOffset + i
                val byteVal = source.byteAt(startOffset + (bitIndex ushr 3))
                val bit = (byteVal ushr (bitIndex and 7)) and 1
                result = result or (bit shl i)
            }
            bitOffset += count
            return result
        }

        fun readU32(): Long? {
            val selector = readBits(2) ?: return null
            return when (selector) {
                0 -> 1L + (readBits(9) ?: return null)
                1 -> 1L + (readBits(13) ?: return null)
                2 -> 1L + (readBits(18) ?: return null)
                else -> {
                    val low = readBits(15) ?: return null
                    val high = readBits(15) ?: return null
                    1L + (low.toLong() or (high.toLong() shl 15))
                }
            }
        }
    }

    private class ReplayInputStream(
        private val prefix: ByteArray,
        private val prefixLength: Int,
        private val tail: InputStream,
    ) : InputStream() {
        private var pos = 0

        override fun read(): Int {
            if (pos < prefixLength) return prefix[pos++].toInt() and 0xFF
            return tail.read()
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (off < 0 || len < 0 || len > b.size - off) throw IndexOutOfBoundsException()
            if (len == 0) return 0
            if (pos < prefixLength) {
                val n = min(len, prefixLength - pos)
                System.arraycopy(prefix, pos, b, off, n)
                pos += n
                return n
            }
            return tail.read(b, off, len)
        }

        override fun available(): Int {
            val total = (prefixLength - pos).toLong() + tail.available().coerceAtLeast(0).toLong()
            return total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

        override fun close() = tail.close()
    }
}
