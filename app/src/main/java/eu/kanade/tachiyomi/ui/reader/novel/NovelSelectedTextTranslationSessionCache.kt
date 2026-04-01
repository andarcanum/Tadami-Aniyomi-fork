package eu.kanade.tachiyomi.ui.reader.novel

internal class NovelSelectedTextTranslationSessionCache {
    private val entries = linkedMapOf<NovelSelectedTextTranslationCacheKey, NovelSelectedTextTranslationResult>()

    fun get(key: NovelSelectedTextTranslationCacheKey): NovelSelectedTextTranslationResult? {
        return entries[key]
    }

    fun put(
        key: NovelSelectedTextTranslationCacheKey,
        value: NovelSelectedTextTranslationResult,
    ) {
        entries[key] = value
    }

    fun clear() {
        entries.clear()
    }

    fun snapshotSize(): Int {
        return entries.size
    }
}
