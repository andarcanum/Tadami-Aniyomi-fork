package mihon.core.archive

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import me.zhanghai.android.libarchive.ArchiveException
import java.io.Closeable
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.channels.FileChannel
import java.util.UUID
import kotlin.concurrent.Volatile

class ArchiveReader(
    pfd: ParcelFileDescriptor,
    cacheDir: File? = null,
) : Closeable {
    private val size = pfd.statSize
    private val lock = Any()
    private val solidLock = Any()

    // ParcelFileDescriptor.AutoCloseInputStream takes formal ownership of [pfd] (satisfying
    // Android fdsan) and stays reachable for the full ArchiveReader lifetime so GC cannot close
    // the underlying fd while mmap/FileChannel is still active.
    private val pfdInputStream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
    private val zipChannel: FileChannel?
    private val zipReader: ZipCentralDirectoryReader?
    private val address: Long

    private val sessionCacheDir: File? = cacheDir?.let { base ->
        File(base, UUID.randomUUID().toString()).apply { mkdirs() }
    }
    private val solidExtractedFiles = LinkedHashMap<String, File>(16, 0.75f, true)
    private var solidExtractedBytes = 0L
    private var solidStream: ArchiveInputStream? = null

    @Volatile
    private var isClosed = false

    // Streams handed out by getInputStream() escape this class and read on other threads
    // (page holders, SSIV tiles). munmap() and pfd.close() while such a stream is alive are
    // use-after-free / ClosedChannelException hazards, so cleanup is refcounted and deferred
    // until the last outstanding stream is closed. All lifecycle fields are guarded by [lock].
    private var openStreamCount = 0
    private var cleanupPending = false

    init {
        try {
            val channel = if (size > 0L) {
                runCatching { pfdInputStream.channel }.getOrNull()
            } else {
                null
            }
            val parsedZip = if (channel != null) {
                ZipCentralDirectoryReader.tryOpen(channel, size)
            } else {
                null
            }
            if (parsedZip != null) {
                zipChannel = channel
                zipReader = parsedZip
                address = 0L
            } else {
                zipChannel = null
                zipReader = null
                address = Os.mmap(0, size, OsConstants.PROT_READ, OsConstants.MAP_PRIVATE, pfd.fileDescriptor, 0)
            }
        } catch (t: Throwable) {
            runCatching { pfdInputStream.close() }
            throw t
        }
    }

    fun <T> useEntries(block: (Sequence<ArchiveEntry>) -> T): T {
        zipReader?.let { reader ->
            synchronized(lock) {
                check(!isClosed) { "ArchiveReader is closed" }
                openStreamCount++
            }
            return try {
                block(reader.entries)
            } finally {
                releaseStream()
            }
        }

        return synchronized(lock) {
            check(!isClosed) { "ArchiveReader is closed" }
            val stream = ArchiveInputStream(address, size)
            openStreamCount++
            try {
                stream.use {
                    block(generateSequence { stream.getNextEntry() })
                }
            } finally {
                releaseStream()
            }
        }
    }

    fun getInputStream(entryName: String): InputStream? {
        zipReader?.let { reader ->
            synchronized(lock) {
                if (isClosed) return null
                if (!reader.contains(entryName)) return null
                openStreamCount++
            }
            return try {
                val stream = reader.openInputStream(entryName)
                if (stream == null) {
                    releaseStream()
                    null
                } else {
                    wrapRefcountedStream(stream)
                }
            } catch (t: Throwable) {
                releaseStream()
                throw t
            }
        }

        if (sessionCacheDir != null) {
            return getSolidCachedInputStream(entryName)
        }

        return getDirectLibarchiveInputStream(entryName)
    }

    private fun getSolidCachedInputStream(entryName: String): InputStream? {
        return synchronized(solidLock) {
            if (isClosed) return@synchronized null
            val cachedFile = solidExtractedFiles[entryName]?.takeIf { it.exists() }
                ?: extractEntryToSessionCache(entryName)
                ?: return@synchronized null

            synchronized(lock) {
                if (isClosed) return@synchronized null
                openStreamCount++
            }
            try {
                wrapRefcountedStream(cachedFile.inputStream())
            } catch (t: Throwable) {
                releaseStream()
                throw t
            }
        }
    }

    private fun extractEntryToSessionCache(targetName: String): File? {
        val cacheRoot = sessionCacheDir ?: return null
        val stream = solidStream ?: ArchiveInputStream(address, size).also { solidStream = it }
        var found = extractUntilMatch(stream, targetName, cacheRoot)
        if (found == null) {
            // Rewind once if the target entry was already passed earlier or evicted from LRU.
            runCatching { stream.close() }
            val fresh = ArchiveInputStream(address, size).also { solidStream = it }
            found = extractUntilMatch(fresh, targetName, cacheRoot)
        }
        return found
    }

    private fun extractUntilMatch(
        stream: ArchiveInputStream,
        targetName: String,
        cacheRoot: File,
    ): File? {
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val entry = stream.getNextEntry() ?: return null
            if (!entry.isFile) continue
            val outFile = File(cacheRoot, "${solidExtractedFiles.size}_${UUID.randomUUID()}.bin")
            var written = 0L
            outFile.outputStream().use { out ->
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    written += read
                }
            }
            if (written > MAX_SOLID_CACHE_BYTES) {
                outFile.delete()
                if (entry.name == targetName) return null
                continue
            }
            solidExtractedFiles[entry.name] = outFile
            solidExtractedBytes += written
            trimSolidCache(protectKey = entry.name)
            if (entry.name == targetName) {
                return outFile
            }
        }
    }

    private fun trimSolidCache(protectKey: String) {
        if (solidExtractedBytes <= MAX_SOLID_CACHE_BYTES) return
        val iter = solidExtractedFiles.entries.iterator()
        while (iter.hasNext() && solidExtractedBytes > MAX_SOLID_CACHE_BYTES) {
            val next = iter.next()
            if (next.key == protectKey) continue
            val len = next.value.length()
            next.value.delete()
            solidExtractedBytes = (solidExtractedBytes - len).coerceAtLeast(0L)
            iter.remove()
        }
    }

    private fun getDirectLibarchiveInputStream(entryName: String): InputStream? = synchronized(lock) {
        if (isClosed) return null
        val archive = ArchiveInputStream(address, size)
        try {
            while (true) {
                val entry = archive.getNextEntry() ?: break
                if (entry.name == entryName) {
                    openStreamCount++
                    return wrapRefcountedStream(archive)
                }
            }
        } catch (e: ArchiveException) {
            archive.close()
            throw e
        }
        archive.close()
        null
    }

    private fun wrapRefcountedStream(delegate: InputStream): InputStream {
        return object : FilterInputStream(delegate) {
            private var released = false

            override fun close() {
                try {
                    super.close()
                } finally {
                    if (!released) {
                        released = true
                        releaseStream()
                    }
                }
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            if (isClosed) return
            isClosed = true
            if (openStreamCount > 0) {
                cleanupPending = true
                return
            }
        }
        cleanupResources()
    }

    private fun releaseStream() {
        var shouldCleanup = false
        synchronized(lock) {
            openStreamCount--
            if (openStreamCount == 0 && cleanupPending) {
                cleanupPending = false
                shouldCleanup = true
            }
        }
        if (shouldCleanup) {
            cleanupResources()
        }
    }

    private fun cleanupResources() {
        synchronized(solidLock) {
            runCatching { solidStream?.close() }
            solidStream = null
            solidExtractedFiles.clear()
            solidExtractedBytes = 0L
        }
        if (address != 0L) {
            runCatching { Os.munmap(address, size) }
        }
        runCatching { pfdInputStream.close() }
        runCatching { sessionCacheDir?.deleteRecursively() }
    }

    private companion object {
        const val MAX_SOLID_CACHE_BYTES = 128L * 1024L * 1024L
    }
}
