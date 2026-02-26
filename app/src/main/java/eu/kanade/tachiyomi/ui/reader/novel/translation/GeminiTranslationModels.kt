package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode

data class GeminiTranslationParams(
    val apiKey: String,
    val model: String,
    val sourceLang: String,
    val targetLang: String,
    val reasoningEffort: String,
    val budgetTokens: Int,
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val promptMode: GeminiPromptMode,
    val promptModifiers: String,
)

internal data class GeminiTranslationCacheEntry(
    val chapterId: Long,
    val translatedByIndex: Map<Int, String>,
    val model: String,
    val sourceLang: String,
    val targetLang: String,
    val promptMode: GeminiPromptMode,
)
