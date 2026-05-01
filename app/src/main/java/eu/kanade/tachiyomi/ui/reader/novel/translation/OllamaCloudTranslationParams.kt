package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode

data class OllamaCloudTranslationParams(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val sourceLang: String,
    val targetLang: String,
    val promptMode: GeminiPromptMode,
    val promptModifiers: String,
    val temperature: Float,
    val topP: Float,
    val reasoningEffort: String? = null,
)
