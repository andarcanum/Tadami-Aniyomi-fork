package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import android.util.LruCache

/**
 * Bounded in-memory LRU cache of lightweight page previews for instant frame-0 rendering while
 * full-resolution tile pyramids initialize and decode.
 *
 * Evicted bitmaps are intentionally NOT manually recycled: Android 8+ reclaims unreferenced
 * software bitmaps via GC without risking `Canvas: trying to use a recycled bitmap` if an
 * active view is still drawing a cached preview during eviction.
 */
object ReaderPreviewCache {

    data class Entry(
        val bitmap: Bitmap,
        val sourceWidth: Int,
        val sourceHeight: Int,
    )

    private const val MIN_CACHE_KB = 8 * 1024
    private const val MAX_CACHE_KB = 28 * 1024

    private val cache = object : LruCache<String, Entry>(resolveCacheBudgetKb()) {
        override fun sizeOf(key: String, value: Entry): Int {
            return (value.bitmap.allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

    internal val maxSizeKb: Int
        get() = cache.maxSize()

    internal val sizeKb: Int
        get() = cache.size()

    fun key(chapterId: Long?, pageIndex: Int, cropBorders: Boolean): String? {
        if (chapterId == null) return null
        return "$chapterId:$pageIndex:${if (cropBorders) 1 else 0}"
    }

    fun get(key: String): Entry? {
        val entry = cache.get(key) ?: return null
        if (entry.bitmap.isRecycled || entry.sourceWidth <= 0 || entry.sourceHeight <= 0) {
            cache.remove(key)
            return null
        }
        return entry
    }

    fun put(key: String, entry: Entry) {
        if (entry.bitmap.isRecycled || entry.sourceWidth <= 0 || entry.sourceHeight <= 0) {
            return
        }
        cache.put(key, entry)
    }

    @Suppress("DEPRECATION")
    fun trimMemory(level: Int) {
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
                level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                cache.evictAll()
            }
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                cache.trimToSize(cache.maxSize() / 2)
            }
        }
    }

    fun clear() {
        cache.evictAll()
    }

    private fun resolveCacheBudgetKb(): Int {
        val maxHeapKb = Runtime.getRuntime().maxMemory() / 1024L
        // ~4% of max JVM heap, bounded to 8–28 MB (16–56 RGB_565 previews at <= 262k px).
        return (maxHeapKb / 25L).coerceIn(MIN_CACHE_KB.toLong(), MAX_CACHE_KB.toLong()).toInt()
    }
}
