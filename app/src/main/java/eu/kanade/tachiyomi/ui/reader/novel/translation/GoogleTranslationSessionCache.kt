package eu.kanade.tachiyomi.ui.reader.novel.translation

class GoogleTranslationSessionCache {

    private val cache = LinkedHashMap<String, Map<Int, String>>()

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
        return cache[buildKey(chapterId, sourceLang, targetLang)]
    }

    fun put(
        chapterId: Long,
        sourceLang: String,
        targetLang: String,
        translatedByIndex: Map<Int, String>,
    ) {
        cache[buildKey(chapterId, sourceLang, targetLang)] = translatedByIndex
    }

    fun remove(
        chapterId: Long,
        sourceLang: String,
        targetLang: String,
    ) {
        cache.remove(buildKey(chapterId, sourceLang, targetLang))
    }

    fun clear() {
        cache.clear()
    }
}
