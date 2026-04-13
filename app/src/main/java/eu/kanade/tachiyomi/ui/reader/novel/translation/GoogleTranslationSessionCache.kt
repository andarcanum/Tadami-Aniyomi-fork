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
        backend: ChapterTranslationBackend,
    ): String {
        return "$chapterId|$sourceLang|$targetLang|${backend.fingerprint()}"
    }

    fun get(
        chapterId: Long,
        sourceLang: String,
        targetLang: String,
        backend: ChapterTranslationBackend,
    ): Map<Int, String>? {
        return synchronized(cache) {
            cache[buildKey(chapterId, sourceLang, targetLang, backend)]
        }
    }

    fun put(
        chapterId: Long,
        sourceLang: String,
        targetLang: String,
        backend: ChapterTranslationBackend,
        translatedByIndex: Map<Int, String>,
    ) {
        synchronized(cache) {
            cache[buildKey(chapterId, sourceLang, targetLang, backend)] = translatedByIndex
        }
    }

    fun remove(
        chapterId: Long,
        sourceLang: String,
        targetLang: String,
        backend: ChapterTranslationBackend,
    ) {
        synchronized(cache) {
            cache.remove(buildKey(chapterId, sourceLang, targetLang, backend))
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
