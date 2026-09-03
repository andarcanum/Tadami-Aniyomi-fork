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

/**
 * Version of the canonical text-block extraction that `translatedByIndex` keys address.
 *
 * Bumped whenever the extraction order/space changes (v2: the select-based walk was replaced by
 * the reader's collect-based walk, where a blockquote is one block and loose text nodes count).
 * Entries written by older extractors never match current requirements and get retranslated
 * instead of being overlaid onto the wrong blocks.
 */
internal const val NOVEL_TRANSLATION_EXTRACTOR_VERSION = 2

internal data class GeminiTranslationCacheEntry(
    val chapterId: Long,
    val translatedByIndex: Map<Int, String>,
    val provider: NovelTranslationProvider = NovelTranslationProvider.GEMINI,
    val model: String,
    val sourceLang: String,
    val targetLang: String,
    val promptMode: GeminiPromptMode,
    val stylePreset: NovelTranslationStylePreset = NovelTranslationStylePreset.PROFESSIONAL,
    /** Legacy entries (disk JSON without the field) decode to 0 and never match. */
    val extractorVersion: Int = 0,
    /** Fingerprint of the prompt-shaping modifiers active at write time; part of cache identity. */
    val promptModifiersFingerprint: String = "",
    /** Source segment count at write time; 0 = unknown (legacy). Fewer translations = incomplete. */
    val sourceSegmentCount: Int = 0,
) {
    /**
     * A relaxed-mode run can persist a partially translated chapter; skip-checks must treat it as
     * not-cached so a later batch heals it, while display restore may still show what exists.
     */
    val isTranslationComplete: Boolean
        get() = sourceSegmentCount <= 0 || translatedByIndex.size >= sourceSegmentCount
}

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
    val reasoningEffort: String? = null,
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
    val reasoningEffort: String = "none",
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
    val reasoningEffort: String? = null,
)
