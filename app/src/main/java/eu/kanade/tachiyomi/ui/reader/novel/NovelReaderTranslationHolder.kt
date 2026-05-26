package eu.kanade.tachiyomi.ui.reader.novel

/**
 * Manages translation state for the current chapter.
 *
 * Eliminates the duplication of geminiTranslatedByIndex and googleTranslatedByIndex
 * as two separate maps in ScreenModel. Provides text block resolution:
 * untranslated indices fall back to the original text.
 */
class NovelReaderTranslationHolder(
    private val originalTextBlocks: () -> List<String>,
) {
    /** Sparse translation maps: source name -> (block index -> translated text). */
    private val translations = java.util.concurrent.ConcurrentHashMap<String, Map<Int, String>>()

    // ── Write ──

    fun put(source: String, translatedByIndex: Map<Int, String>) {
        if (translatedByIndex.isNotEmpty()) {
            translations[source] = translatedByIndex
        }
    }

    fun clear(source: String) {
        translations.remove(source)
    }

    fun clearAll() {
        translations.clear()
    }

    // ── Read ──

    fun has(source: String): Boolean =
        translations.containsKey(source)

    fun map(source: String): Map<Int, String> =
        translations[source] ?: emptyMap()

    fun isEmpty(source: String): Boolean =
        translations[source].isNullOrEmpty()

    fun resolvedBlocks(source: String): List<String> {
        val translated = translations[source] ?: return originalTextBlocks()
        val originals = originalTextBlocks()
        return originals.mapIndexed { index, original ->
            translated[index] ?: original
        }
    }

    /** Total byte estimate for all in-memory translations. */
    fun estimatedMemoryBytes(): Long {
        return translations.values.sumOf { map ->
            map.values.sumOf { it.length.toLong() * 2 }
        }
    }
}
