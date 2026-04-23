package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset

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
    val provider: NovelTranslationProvider = NovelTranslationProvider.GEMINI,
    val privateUnlocked: Boolean = false,
    val privatePythonLikeMode: Boolean = false,
)

internal data class GeminiTranslationCacheEntry(
    val chapterId: Long,
    val translatedByIndex: Map<Int, String>,
    val provider: NovelTranslationProvider = NovelTranslationProvider.GEMINI,
    val model: String,
    val sourceLang: String,
    val targetLang: String,
    val promptMode: GeminiPromptMode,
    val stylePreset: NovelTranslationStylePreset = NovelTranslationStylePreset.PROFESSIONAL,
)

data class AirforceTranslationParams(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val sourceLang: String,
    val targetLang: String,
    val promptMode: GeminiPromptMode,
    val promptModifiers: String,
    val temperature: Float,
    val topP: Float,
)

data class OpenRouterTranslationParams(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val sourceLang: String,
    val targetLang: String,
    val promptMode: GeminiPromptMode,
    val promptModifiers: String,
    val temperature: Float,
    val topP: Float,
)

data class DeepSeekTranslationParams(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val sourceLang: String,
    val targetLang: String,
    val promptMode: GeminiPromptMode,
    val promptModifiers: String,
    val temperature: Float,
    val topP: Float,
    val presencePenalty: Float = 0.15f,
    val frequencyPenalty: Float = 0.15f,
)

data class MistralTranslationParams(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val sourceLang: String,
    val targetLang: String,
    val promptMode: GeminiPromptMode,
    val promptModifiers: String,
    val temperature: Float,
    val topP: Float,
)
