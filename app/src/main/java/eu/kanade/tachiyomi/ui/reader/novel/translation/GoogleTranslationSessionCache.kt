package eu.kanade.tachiyomi.ui.reader.novel.translation

class GoogleTranslationSessionCache {
    private companion object {
        const val MAX_ENTRIES = 4
    }

    private val cache = object : LinkedHashMap<String, Map<Int, String>>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Map<Int, String>>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    fun buildKey(
        chapterId: Long,
        sourceLang: String,
        targetLang: String,
    ): String {
        return "$chapterId|$sourceLang|$targetLang"
    }

    fun get(
        chapterId: Long,
        sourceLang: String,
        targetLang: String,
    ): Map<Int, String>? {
        return synchronized(cache) {
            cache[buildKey(chapterId, sourceLang, targetLang)]
        }
    }

    fun put(
        chapterId: Long,
        sourceLang: String,
        targetLang: String,
        translatedByIndex: Map<Int, String>,
    ) {
        synchronized(cache) {
            cache[buildKey(chapterId, sourceLang, targetLang)] = translatedByIndex
        }
    }

    fun remove(
        chapterId: Long,
        sourceLang: String,
        targetLang: String,
    ) {
        synchronized(cache) {
            cache.remove(buildKey(chapterId, sourceLang, targetLang))
        }
    }

    fun clear() {
        synchronized(cache) {
            cache.clear()
        }
    }

    fun snapshotSize(): Int {
        return synchronized(cache) {
            cache.size
        }
    }
}
