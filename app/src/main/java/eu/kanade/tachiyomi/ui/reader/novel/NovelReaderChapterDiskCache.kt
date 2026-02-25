package eu.kanade.tachiyomi.ui.reader.novel

import android.app.Application
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private const val DEFAULT_CHAPTER_CACHE_MAX_ENTRIES = 1000
private const val DEFAULT_CHAPTER_CACHE_MAX_TOTAL_BYTES = 512L * 1024L * 1024L
private const val DEFAULT_CHAPTER_CACHE_MAX_ENTRY_BYTES = 2L * 1024L * 1024L

internal data class NovelReaderChapterDiskCacheConfig(
    val maxEntries: Int = DEFAULT_CHAPTER_CACHE_MAX_ENTRIES,
    val maxTotalBytes: Long = DEFAULT_CHAPTER_CACHE_MAX_TOTAL_BYTES,
    val maxEntryBytes: Long = DEFAULT_CHAPTER_CACHE_MAX_ENTRY_BYTES,
    val unlimited: Boolean = false,
)

internal data class NovelReaderChapterDiskCacheStats(
    val entryCount: Int,
    val totalBytes: Long,
)

internal class NovelReaderChapterDiskCache(
    private val directory: File,
    private val configProvider: () -> NovelReaderChapterDiskCacheConfig = { NovelReaderChapterDiskCacheConfig() },
) {
    private val lock = Any()

    constructor(
        directory: File,
        maxEntries: Int = DEFAULT_CHAPTER_CACHE_MAX_ENTRIES,
        maxTotalBytes: Long = DEFAULT_CHAPTER_CACHE_MAX_TOTAL_BYTES,
        maxEntryBytes: Long = DEFAULT_CHAPTER_CACHE_MAX_ENTRY_BYTES,
        unlimited: Boolean = false,
    ) : this(
        directory = directory,
        configProvider = {
            NovelReaderChapterDiskCacheConfig(
                maxEntries = maxEntries,
                maxTotalBytes = maxTotalBytes,
                maxEntryBytes = maxEntryBytes,
                unlimited = unlimited,
            )
        },
    )

    fun get(chapterId: Long): String? {
        synchronized(lock) {
            val config = configProvider()
            val file = fileFor(chapterId)
            if (!file.isFile) return null
            if (file.length() <= 0L || file.length() > config.maxEntryBytes) {
                file.delete()
                return null
            }

            return runCatching {
                GZIPInputStream(file.inputStream().buffered()).use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                }
            }.onSuccess {
                file.setLastModified(System.currentTimeMillis())
            }.onFailure {
                file.delete()
            }.getOrNull()
        }
    }

    fun put(chapterId: Long, html: String) {
        if (html.isBlank()) return

        val config = configProvider()
        val compressed = runCatching { gzip(html) }.getOrNull() ?: return
        val compressedSize = compressed.size.toLong()
        if (compressedSize <= 0L || compressedSize > config.maxEntryBytes) return

        synchronized(lock) {
            ensureDirectory()
            val file = fileFor(chapterId)
            val tempFile = File(directory, "$chapterId.tmp")
            runCatching {
                tempFile.outputStream().buffered().use { output ->
                    output.write(compressed)
                }
                if (file.exists() && !file.delete()) {
                    throw IOException("Failed to replace chapter cache file")
                }
                if (!tempFile.renameTo(file)) {
                    throw IOException("Failed to move chapter cache file into place")
                }
                file.setLastModified(System.currentTimeMillis())
                pruneLocked(config)
            }.onFailure {
                tempFile.delete()
                logcat(LogPriority.WARN, it) { "Failed to write novel reader chapter cache" }
            }
        }
    }

    fun contains(chapterId: Long): Boolean {
        synchronized(lock) {
            return fileFor(chapterId).isFile
        }
    }

    fun clear() {
        synchronized(lock) {
            directory.listFiles()?.forEach { it.delete() }
        }
    }

    fun stats(): NovelReaderChapterDiskCacheStats {
        synchronized(lock) {
            val files = chapterFilesLocked()
            return NovelReaderChapterDiskCacheStats(
                entryCount = files.size,
                totalBytes = files.sumOf { it.length().coerceAtLeast(0L) },
            )
        }
    }

    fun trimToLimits(config: NovelReaderChapterDiskCacheConfig = configProvider()) {
        synchronized(lock) {
            pruneLocked(config)
        }
    }

    private fun ensureDirectory() {
        if (!directory.exists()) {
            directory.mkdirs()
        }
    }

    private fun pruneLocked(config: NovelReaderChapterDiskCacheConfig) {
        val files = chapterFilesLocked().sortedBy { it.lastModified() }.toMutableList()
        if (config.unlimited) return

        var totalBytes = files.sumOf { it.length().coerceAtLeast(0L) }
        while (files.size > config.maxEntries || totalBytes > config.maxTotalBytes) {
            val oldest = files.removeFirstOrNull() ?: break
            totalBytes -= oldest.length().coerceAtLeast(0L)
            oldest.delete()
        }
    }

    private fun chapterFilesLocked(): List<File> {
        return directory.listFiles()
            ?.filter { it.isFile && it.extension.equals("gz", ignoreCase = true) }
            .orEmpty()
    }

    private fun fileFor(chapterId: Long): File {
        return File(directory, "$chapterId.gz")
    }

    private fun gzip(html: String): ByteArray {
        val sourceBytes = html.toByteArray(Charsets.UTF_8)
        val output = ByteArrayOutputStream(sourceBytes.size.coerceAtMost(8192))
        GZIPOutputStream(output).use { gzip ->
            ByteArrayInputStream(sourceBytes).use { input ->
                input.copyTo(gzip)
            }
        }
        return output.toByteArray()
    }

    companion object {
        internal const val DEFAULT_MAX_ENTRIES = DEFAULT_CHAPTER_CACHE_MAX_ENTRIES
        internal const val DEFAULT_MAX_TOTAL_BYTES = DEFAULT_CHAPTER_CACHE_MAX_TOTAL_BYTES
        internal const val DEFAULT_MAX_ENTRY_BYTES = DEFAULT_CHAPTER_CACHE_MAX_ENTRY_BYTES
    }
}

internal object NovelReaderChapterDiskCacheStore {
    private val prefs by lazy { Injekt.get<NovelReaderPreferences>() }
    private fun config(unlimitedOverride: Boolean? = null): NovelReaderChapterDiskCacheConfig {
        return NovelReaderChapterDiskCacheConfig(
            unlimited = unlimitedOverride ?: prefs.cacheReadChaptersUnlimited().get(),
        )
    }

    private val cache by lazy {
        val app = Injekt.get<Application>()
        NovelReaderChapterDiskCache(
            directory = File(app.cacheDir, "novel_reader_chapter_cache"),
            configProvider = { config() },
        )
    }

    fun get(chapterId: Long): String? = cache.get(chapterId)

    fun put(chapterId: Long, html: String) = cache.put(chapterId, html)

    fun contains(chapterId: Long): Boolean = cache.contains(chapterId)

    fun stats(): NovelReaderChapterDiskCacheStats = cache.stats()

    fun trimToCurrentLimits(unlimitedOverride: Boolean? = null) = cache.trimToLimits(config(unlimitedOverride))

    fun clear() = cache.clear()
}
