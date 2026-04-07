package eu.kanade.tachiyomi.ui.reader.novel

internal class NovelSelectedTextTranslationSessionCache {
    private companion object {
        const val MAX_ENTRIES = 32
    }

    private val entries = object : LinkedHashMap<NovelSelectedTextTranslationCacheKey, NovelSelectedTextTranslationResult>(
        MAX_ENTRIES,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<NovelSelectedTextTranslationCacheKey, NovelSelectedTextTranslationResult>?,
        ): Boolean {
            return size > MAX_ENTRIES
        }
    }

    fun get(key: NovelSelectedTextTranslationCacheKey): NovelSelectedTextTranslationResult? {
        return synchronized(entries) {
            entries[key]
        }
    }

    fun put(
        key: NovelSelectedTextTranslationCacheKey,
        value: NovelSelectedTextTranslationResult,
    ) {
        synchronized(entries) {
            entries[key] = value
        }
    }

    fun clear() {
        synchronized(entries) {
            entries.clear()
        }
    }

    fun snapshotSize(): Int {
        return synchronized(entries) {
            entries.size
        }
    }
}
