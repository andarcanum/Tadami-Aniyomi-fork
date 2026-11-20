package eu.kanade.tachiyomi.data.cache

import android.content.Context
import android.text.format.Formatter
import com.jakewharton.disklrucache.DiskLruCache
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.Response
import okio.buffer
import okio.sink
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.items.chapter.model.Chapter
import java.io.File
import java.io.FileNotFoundException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Class used to create chapter cache
 * For each image in a chapter a file is created
 * For each chapter a Json list is created and converted to a file.
 * The files are in format *md5key*.0
 *
 * @param context the application context.
 * @constructor creates an instance of the chapter cache.
 */
class ChapterCache(
    private val context: Context,
    private val json: Json,
    maxSizeBytes: Long = PARAMETER_CACHE_SIZE,
) {

    /** Cache class used for cache management. */
    private val diskCache = DiskLruCache.open(
        File(context.cacheDir, "chapter_disk_cache"),
        PARAMETER_APP_VERSION,
        PARAMETER_VALUE_COUNT,
        maxSizeBytes.coerceAtLeast(MINIMUM_CACHE_SIZE_BYTES),
    )

    /**
     * Returns directory of cache.
     */
    private val cacheDir: File = diskCache.directory

    /**
     * Returns real size of directory.
     */
    private val realSize: Long
        get() = DiskUtil.getDirectorySize(cacheDir)

    /**
     * Returns real size of directory in human readable format.
     */
    val readableSize: String
        get() = Formatter.formatFileSize(context, realSize)

    val maxSizeBytes: Long
        get() = diskCache.getMaxSize()

    /**
     * Get page list from cache.
     *
     * @param chapter the chapter.
     * @return the list of pages.
     */
    fun getPageListFromCache(chapter: Chapter, maxAgeMs: Long = DEFAULT_PAGE_LIST_TTL_MS): List<Page> {
        // Get the key for the chapter.
        val key = DiskUtil.hashKeyForDisk(getKey(chapter))

        // Convert JSON string to list of objects, but tolerate bad or missing cache entries.
        return runCatching {
            diskCache.get(key).use { snapshot ->
                snapshot?.getString(0)?.let { cachedValue ->
                    val envelope = runCatching { json.decodeFromString<CachedPageList>(cachedValue) }.getOrNull()
                    if (envelope != null) {
                        if (System.currentTimeMillis() - envelope.savedAtMs > maxAgeMs) {
                            emptyList()
                        } else {
                            envelope.pages
                        }
                    } else {
                        // Legacy format fallback: plain List<Page> without TTL wrapper.
                        json.decodeFromString<List<Page>>(cachedValue)
                    }
                }.orEmpty()
            }
        }.getOrElse {
            logcat(LogPriority.WARN, it) { "Failed to read page list from cache" }
            emptyList()
        }
    }

    /**
     * Add page list to disk cache.
     *
     * @param chapter the chapter.
     * @param pages list of pages.
     */
    @Synchronized
    fun putPageListToCache(chapter: Chapter, pages: List<Page>) {
        // Initialize the editor (edits the values for an entry).
        var editor: DiskLruCache.Editor? = null

        try {
            // Get editor from md5 key.
            val key = DiskUtil.hashKeyForDisk(getKey(chapter))
            editor = diskCache.edit(key) ?: return

            // Write chapter urls to cache.
            val envelope = CachedPageList(savedAtMs = System.currentTimeMillis(), pages = pages)
            editor.newOutputStream(0).sink().buffer().use {
                it.write(json.encodeToString(envelope).toByteArray())
                it.flush()
            }

            editor.commit()
            editor.abortUnlessCommitted()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to put page list to cache" }
            // Ignore.
        } finally {
            editor?.abortUnlessCommitted()
        }
    }

    /**
     * Returns true if image is in cache.
     *
     * @param imageUrl url of image.
     * @return true if in cache otherwise false.
     */
    fun isImageInCache(imageUrl: String): Boolean {
        return try {
            diskCache.get(DiskUtil.hashKeyForDisk(imageUrl)).use { it != null }
        } catch (e: IOException) {
            false
        }
    }

    /**
     * Removes a single cached image entry so retries can re-download a clean copy.
     */
    fun removeImageFromCache(imageUrl: String): Boolean {
        return runCatching {
            diskCache.remove(DiskUtil.hashKeyForDisk(imageUrl))
        }.onFailure {
            logcat(LogPriority.WARN, it) { "Failed to remove image from cache" }
        }.getOrDefault(false)
    }

    /**
     * Opens a cached image stream via [DiskLruCache.get] so LRU recency is updated and the
     * underlying entry stays protected until the returned stream is closed.
     */
    @Throws(IOException::class)
    fun openImageStream(imageUrl: String): InputStream {
        val key = DiskUtil.hashKeyForDisk(imageUrl)
        val snapshot = diskCache.get(key)
            ?: throw FileNotFoundException("Image not found in cache: $imageUrl")
        return try {
            val stream = snapshot.getInputStream(0)
            object : FilterInputStream(stream) {
                private val closed = AtomicBoolean(false)

                override fun close() {
                    if (!closed.compareAndSet(false, true)) return
                    try {
                        super.close()
                    } finally {
                        snapshot.close()
                    }
                }
            }
        } catch (t: Throwable) {
            snapshot.close()
            throw t
        }
    }

    /**
     * Get image file from url.
     *
     * @param imageUrl url of image.
     * @return path of image.
     */
    fun getImageFile(imageUrl: String): File {
        // Get file from md5 key.
        val imageName = DiskUtil.hashKeyForDisk(imageUrl) + ".0"
        return File(diskCache.directory, imageName)
    }

    /**
     * Add image to cache.
     *
     * @param imageUrl url of image.
     * @param response http response from page.
     * @throws IOException image error.
     */
    @Throws(IOException::class)
    fun putImageToCache(imageUrl: String, response: Response) {
        // Initialize editor (edits the values for an entry).
        var editor: DiskLruCache.Editor? = null

        try {
            val mediaType = response.body.contentType()
            if (mediaType != null &&
                (
                    (
                        mediaType.type.equals("text", ignoreCase = true) &&
                            mediaType.subtype.equals("html", ignoreCase = true)
                        ) ||
                        mediaType.subtype.equals("xhtml+xml", ignoreCase = true)
                    )
            ) {
                throw IOException("Unexpected HTML response for image: $mediaType")
            }

            // Get editor from md5 key.
            val key = DiskUtil.hashKeyForDisk(imageUrl)
            editor = diskCache.edit(key) ?: throw IOException("Unable to edit key")

            // Write image body and verify we did not commit an empty or truncated payload.
            val expectedLength = response.body.contentLength()
            val bytesWritten = editor.newOutputStream(0).sink().buffer().use { sink ->
                val count = sink.writeAll(response.body.source())
                sink.flush()
                count
            }
            if (bytesWritten <= 0L) {
                throw IOException("Empty image response")
            }
            if (expectedLength > 0L && bytesWritten < expectedLength) {
                throw IOException("Truncated image response ($bytesWritten/$expectedLength bytes)")
            }

            editor.commit()
        } finally {
            response.body.close()
            editor?.abortUnlessCommitted()
        }
    }

    fun setMaxSizeBytes(maxSizeBytes: Long) {
        val safeSize = maxSizeBytes.coerceAtLeast(MINIMUM_CACHE_SIZE_BYTES)
        diskCache.setMaxSize(safeSize)
        runCatching { diskCache.flush() }
            .onFailure { logcat(LogPriority.WARN, it) { "Failed to apply chapter cache size" } }
    }

    fun setMaxSizeMb(maxSizeMb: Int) {
        setMaxSizeBytes(maxSizeMb.toLong() * BYTES_PER_MEGABYTE)
    }

    fun clear(): Int {
        var deletedFiles = 0
        cacheDir.listFiles()?.forEach {
            if (removeFileFromCache(it.name)) {
                deletedFiles++
            }
        }
        return deletedFiles
    }

    fun close() {
        diskCache.close()
    }

    /**
     * Remove file from cache.
     *
     * @param file name of file "md5.0".
     * @return status of deletion for the file.
     */
    private fun removeFileFromCache(file: String): Boolean {
        // Make sure we don't delete the journal file (keeps track of cache)
        if (file == "journal" || file.startsWith("journal.")) {
            return false
        }

        return try {
            // Remove the extension from the file to get the key of the cache
            val key = file.substringBeforeLast(".")
            // Remove file from cache
            diskCache.remove(key)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to remove file from cache" }
            false
        }
    }

    private fun getKey(chapter: Chapter): String {
        return "${chapter.mangaId}${chapter.url}"
    }
}

/** Application cache version.  */
private const val PARAMETER_APP_VERSION = 1

/** The number of values per cache entry. Must be positive.  */
private const val PARAMETER_VALUE_COUNT = 1

/** The maximum number of bytes this cache should use to store.  */
private const val BYTES_PER_MEGABYTE = 1024L * 1024L
private const val MINIMUM_CACHE_SIZE_BYTES = 1L * BYTES_PER_MEGABYTE
private const val PARAMETER_CACHE_SIZE = 100L * BYTES_PER_MEGABYTE
private const val DEFAULT_PAGE_LIST_TTL_MS = 12L * 60L * 60L * 1000L

@Serializable
private data class CachedPageList(
    val savedAtMs: Long,
    val pages: List<Page>,
)
