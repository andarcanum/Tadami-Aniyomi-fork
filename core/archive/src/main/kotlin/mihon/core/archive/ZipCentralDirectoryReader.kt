package mihon.core.archive

import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import kotlin.math.min

class ZipCentralDirectoryReader private constructor(
    private val channel: FileChannel,
    private val entriesList: List<ZipEntryMeta>,
    private val entriesByName: Map<String, ZipEntryMeta>,
) {
    data class ZipEntryMeta(
        val name: String,
        val isFile: Boolean,
        val method: Int,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val localHeaderOffset: Long,
    )

    val entries: Sequence<ArchiveEntry>
        get() = entriesList.asSequence().map { ArchiveEntry(it.name, it.isFile) }

    fun contains(entryName: String): Boolean = entriesByName.containsKey(entryName)

    @Throws(IOException::class)
    fun openInputStream(entryName: String): InputStream? {
        val entry = entriesByName[entryName] ?: return null
        if (!entry.isFile) return null

        val lfh = ByteBuffer.allocate(LOCAL_FILE_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        readFully(channel, lfh, entry.localHeaderOffset)
        lfh.flip()
        if (lfh.getInt(0) != LOCAL_FILE_HEADER_SIG) {
            throw IOException("Invalid ZIP local file header for $entryName")
        }
        val fileNameLen = lfh.getShort(26).toInt() and 0xFFFF
        val extraLen = lfh.getShort(28).toInt() and 0xFFFF
        val dataOffset = entry.localHeaderOffset + LOCAL_FILE_HEADER_SIZE + fileNameLen + extraLen

        val sliceStream = PositionalChannelInputStream(channel, dataOffset, entry.compressedSize)
        return when (entry.method) {
            METHOD_STORED -> sliceStream
            METHOD_DEFLATED -> {
                val inflater = Inflater(true)
                object : InflaterInputStream(sliceStream, inflater, 8192) {
                    private var inflaterEnded = false
                    override fun close() {
                        if (inflaterEnded) return
                        inflaterEnded = true
                        try {
                            super.close()
                        } finally {
                            inflater.end()
                        }
                    }
                }
            }
            else -> {
                sliceStream.close()
                throw IOException("Unsupported ZIP compression method: ${entry.method}")
            }
        }
    }

    private class PositionalChannelInputStream(
        private val channel: FileChannel,
        startOffset: Long,
        length: Long,
    ) : InputStream() {
        private var position = startOffset
        private val endOffset = startOffset + length
        private val singleByte = ByteBuffer.allocate(1)

        override fun read(): Int {
            if (position >= endOffset) return -1
            singleByte.clear()
            val read = channel.read(singleByte, position)
            if (read <= 0) return -1
            position += read
            return singleByte.get(0).toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (off < 0 || len < 0 || off + len > b.size) {
                throw IndexOutOfBoundsException()
            }
            if (len == 0) return 0
            val remaining = endOffset - position
            if (remaining <= 0L) return -1
            val toRead = min(len.toLong(), remaining).toInt()
            val buf = ByteBuffer.wrap(b, off, toRead)
            val read = channel.read(buf, position)
            if (read <= 0) return -1
            position += read
            return read
        }
    }

    companion object {
        private const val EOCD_SIG = 0x06054b50
        private const val CD_HEADER_SIG = 0x02014b50
        private const val LOCAL_FILE_HEADER_SIG = 0x04034b50
        private const val EOCD_MIN_SIZE = 22
        private const val MAX_EOCD_SEARCH = 65_535 + EOCD_MIN_SIZE
        private const val CD_HEADER_SIZE = 46
        private const val LOCAL_FILE_HEADER_SIZE = 30
        private const val METHOD_STORED = 0
        private const val METHOD_DEFLATED = 8

        /**
         * Attempts to parse a standard (non-ZIP64, unencrypted, STORE/DEFLATE) ZIP Central Directory
         * over a seekable [channel]. Returns `null` if the channel is non-seekable, not a ZIP, or
         * uses unsupported features so callers can transparently fall back to libarchive.
         */
        fun tryOpen(channel: FileChannel, fileSize: Long): ZipCentralDirectoryReader? {
            if (fileSize < EOCD_MIN_SIZE) return null
            return runCatching {
                val tailSize = min(fileSize, MAX_EOCD_SEARCH.toLong()).toInt()
                val tailStart = fileSize - tailSize
                val tailBuf = ByteBuffer.allocate(tailSize).order(ByteOrder.LITTLE_ENDIAN)
                readFully(channel, tailBuf, tailStart)
                val tailBytes = tailBuf.array()

                var eocdPos = -1
                for (i in tailSize - EOCD_MIN_SIZE downTo 0) {
                    if (
                        tailBytes[i] == 0x50.toByte() &&
                        tailBytes[i + 1] == 0x4b.toByte() &&
                        tailBytes[i + 2] == 0x05.toByte() &&
                        tailBytes[i + 3] == 0x06.toByte()
                    ) {
                        val commentLen = (
                            (tailBytes[i + 20].toInt() and 0xFF) or
                                ((tailBytes[i + 21].toInt() and 0xFF) shl 8)
                            )
                        if (i + EOCD_MIN_SIZE + commentLen == tailSize) {
                            eocdPos = i
                            break
                        }
                    }
                }
                if (eocdPos < 0) return null

                tailBuf.position(eocdPos)
                val diskNum = tailBuf.getShort(eocdPos + 4).toInt() and 0xFFFF
                val cdDiskNum = tailBuf.getShort(eocdPos + 6).toInt() and 0xFFFF
                val entryCount = tailBuf.getShort(eocdPos + 10).toInt() and 0xFFFF
                val cdSize = tailBuf.getInt(eocdPos + 12).toLong() and 0xFFFFFFFFL
                val cdOffset = tailBuf.getInt(eocdPos + 16).toLong() and 0xFFFFFFFFL

                if (
                    diskNum != 0 ||
                    cdDiskNum != 0 ||
                    entryCount == 0xFFFF ||
                    cdSize == 0xFFFFFFFFL ||
                    cdOffset == 0xFFFFFFFFL ||
                    cdOffset + cdSize > fileSize
                ) {
                    return null
                }

                val cdBuf = ByteBuffer.allocate(cdSize.toInt()).order(ByteOrder.LITTLE_ENDIAN)
                readFully(channel, cdBuf, cdOffset)

                val list = ArrayList<ZipEntryMeta>(entryCount)
                val map = HashMap<String, ZipEntryMeta>(entryCount)
                var pos = 0
                for (idx in 0 until entryCount) {
                    if (pos + CD_HEADER_SIZE > cdSize) return null
                    if (cdBuf.getInt(pos) != CD_HEADER_SIG) return null

                    val flag = cdBuf.getShort(pos + 8).toInt() and 0xFFFF
                    val method = cdBuf.getShort(pos + 10).toInt() and 0xFFFF
                    if ((flag and 0x0001) != 0) return null // Encrypted
                    if (method != METHOD_STORED && method != METHOD_DEFLATED) return null

                    val compressedSize = cdBuf.getInt(pos + 20).toLong() and 0xFFFFFFFFL
                    val uncompressedSize = cdBuf.getInt(pos + 24).toLong() and 0xFFFFFFFFL
                    if (compressedSize == 0xFFFFFFFFL || uncompressedSize == 0xFFFFFFFFL) return null

                    val nameLen = cdBuf.getShort(pos + 28).toInt() and 0xFFFF
                    val extraLen = cdBuf.getShort(pos + 30).toInt() and 0xFFFF
                    val commentLen = cdBuf.getShort(pos + 32).toInt() and 0xFFFF
                    val externalAttrs = cdBuf.getInt(pos + 38).toLong() and 0xFFFFFFFFL
                    val localHeaderOffset = cdBuf.getInt(pos + 42).toLong() and 0xFFFFFFFFL

                    val nameStart = pos + CD_HEADER_SIZE
                    if (nameStart + nameLen + extraLen + commentLen > cdSize) return null
                    val name = String(cdBuf.array(), nameStart, nameLen, Charsets.UTF_8)
                    val unixMode = (externalAttrs ushr 16).toInt()
                    val isSymlink = (unixMode and 0xF000) == 0xA000
                    val isFile = !name.endsWith("/") && !isSymlink

                    val meta = ZipEntryMeta(
                        name = name,
                        isFile = isFile,
                        method = method,
                        compressedSize = compressedSize,
                        uncompressedSize = uncompressedSize,
                        localHeaderOffset = localHeaderOffset,
                    )
                    list.add(meta)
                    if (isFile) {
                        map[name] = meta
                    }
                    pos = nameStart + nameLen + extraLen + commentLen
                }
                ZipCentralDirectoryReader(channel, list, map)
            }.getOrNull()
        }

        private fun readFully(channel: FileChannel, dst: ByteBuffer, startPosition: Long) {
            var pos = startPosition
            while (dst.hasRemaining()) {
                val read = channel.read(dst, pos)
                if (read <= 0) {
                    throw IOException("Unexpected EOF at $pos")
                }
                pos += read
            }
        }
    }
}
