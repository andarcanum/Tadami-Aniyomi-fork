package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.extension.novel.normalizeNovelLang

enum class NovelTranslationPromptFamily {
    RUSSIAN,
    ENGLISH,
}

internal fun resolveNovelTranslationPromptFamily(targetLang: String?): NovelTranslationPromptFamily {
    val normalized = normalizeNovelLang(targetLang)
    val rootLang = normalized.substringBefore('-').lowercase()
    return if (rootLang == "ru") {
        NovelTranslationPromptFamily.RUSSIAN
    } else {
        NovelTranslationPromptFamily.ENGLISH
    }
}
