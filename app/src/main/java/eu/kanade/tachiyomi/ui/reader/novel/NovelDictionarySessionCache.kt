package eu.kanade.tachiyomi.ui.reader.novel

internal class NovelDictionarySessionCache {
    private companion object {
        const val MAX_ENTRIES = 128

        private val entries = object :
            LinkedHashMap<NovelDictionaryCacheKey, NovelDictionaryResult>(
                MAX_ENTRIES,
                0.75f,
                true,
            ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<
                    NovelDictionaryCacheKey,
                    NovelDictionaryResult,
                    >?,
            ): Boolean {
                return size > MAX_ENTRIES
            }
        }
    }

    fun get(key: NovelDictionaryCacheKey): NovelDictionaryResult? {
        return synchronized(entries) {
            entries[key]
        }
    }

    fun put(
        key: NovelDictionaryCacheKey,
        value: NovelDictionaryResult,
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
