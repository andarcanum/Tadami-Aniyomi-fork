package eu.kanade.tachiyomi.ui.reader.viewer.panel

import android.util.LruCache

/**
 * Lightweight LRU cache for detected manga panel layouts so flipping back/forth across pages
 * never re-runs pixel projection.
 */
object MangaPanelCache {

    private const val MAX_ENTRIES = 64

    private val cache = LruCache<String, List<PanelRect>>(MAX_ENTRIES)

    fun key(
        chapterId: Long?,
        pageIndex: Int,
        isRtl: Boolean,
        cropBorders: Boolean,
    ): String? {
        if (chapterId == null) return null
        return "$chapterId:$pageIndex:${if (isRtl) 1 else 0}:${if (cropBorders) 1 else 0}"
    }

    fun get(key: String): List<PanelRect>? = cache.get(key)

    fun put(key: String, panels: List<PanelRect>) {
        cache.put(key, panels)
    }

    fun clear() {
        cache.evictAll()
    }
}
