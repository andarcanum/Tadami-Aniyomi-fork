package eu.kanade.tachiyomi.ui.reader.novel.translation

import android.app.Application
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Whether the provider has a real ADULT_18 system prompt. OpenRouter/NVIDIA/OllamaCloud build
 * their prompts inline and only carry the CLASSIC texts, so selecting ADULT_18 there silently
 * downgrades - the UI gates the option and the processor logs the fallback instead.
 */
internal fun NovelTranslationProvider.supportsAdultPromptMode(): Boolean {
    return when (this) {
        NovelTranslationProvider.GEMINI,
        NovelTranslationProvider.GEMINI_PRIVATE,
        NovelTranslationProvider.DEEPSEEK,
        NovelTranslationProvider.MISTRAL,
        -> true
        NovelTranslationProvider.OPENROUTER,
        NovelTranslationProvider.NVIDIA,
        NovelTranslationProvider.OLLAMA_CLOUD,
        -> false
    }
}

class GeminiPromptResolver(
    private val application: Application,
) {
    private val adultPrompt: String by lazy {
        runCatching {
            application.assets.open(ADULT_PROMPT_ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.onFailure { error ->
            logcat(LogPriority.WARN, error) {
                "Failed to load adult Gemini prompt asset, falling back to classic prompt"
            }
        }.getOrElse { CLASSIC_SYSTEM_PROMPT }
    }

    internal fun resolveSystemPrompt(
        mode: GeminiPromptMode,
        family: NovelTranslationPromptFamily = NovelTranslationPromptFamily.RUSSIAN,
    ): String {
        return when (mode) {
            GeminiPromptMode.CLASSIC -> when (family) {
                NovelTranslationPromptFamily.RUSSIAN -> CLASSIC_SYSTEM_PROMPT
                NovelTranslationPromptFamily.ENGLISH -> CLASSIC_SYSTEM_PROMPT_EN
            }
            GeminiPromptMode.ADULT_18 -> adultPromptForFamily(adultPrompt, family)
        }
    }

    companion object {
        private const val ADULT_PROMPT_ASSET_PATH = "translation/gemini_prompt_adult_18.txt"

        internal const val ADULT_NON_RUSSIAN_LANGUAGE_OVERRIDE =
            "\n\n### TARGET LANGUAGE OVERRIDE\n" +
                "Apply all uncensored literary fidelity and XML formatting rules above, " +
                "but write the entire translation strictly in the requested target language " +
                "(not Russian) using natural, idiomatic phrasing for that target language."

        internal fun adultPromptForFamily(
            baseAdultPrompt: String,
            family: NovelTranslationPromptFamily,
        ): String = if (family == NovelTranslationPromptFamily.RUSSIAN) {
            baseAdultPrompt
        } else {
            baseAdultPrompt + ADULT_NON_RUSSIAN_LANGUAGE_OVERRIDE
        }

        internal const val CLASSIC_SYSTEM_PROMPT =
            "### ROLE\n" +
                "You are a professional literary translator for novels and light novels.\n" +
                "Your output must read naturally in Russian while preserving tone, intent, and narrative voice.\n\n" +
                "### GOALS\n" +
                "1. Preserve meaning, plot details, and character voice.\n" +
                "2. Prioritize fluent Russian prose over literal calques.\n" +
                "3. Keep terminology consistent across segments.\n" +
                "4. Keep honorifics and culture-specific terms only when they are important for context.\n\n" +
                "### STYLE RULES\n" +
                "- Prefer idiomatic Russian syntax and punctuation.\n" +
                "- Avoid machine-like phrasing and over-literal word order.\n" +
                "- Keep dialogue natural and character-appropriate.\n\n" +
                "### OUTPUT FORMAT\n" +
                "1. Return ONLY XML tags in the same shape as input: <s i='N'>...</s>.\n" +
                "2. No preamble, no explanations, no markdown.\n" +
                "3. Preserve the same indexes and segment count whenever possible."

        internal const val CLASSIC_SYSTEM_PROMPT_EN =
            "### ROLE\n" +
                "You are a professional literary translator for novels and light novels.\n" +
                "Your output must read naturally in the requested target language while preserving tone, intent, " +
                "and narrative voice.\n\n" +
                "### GOALS\n" +
                "1. Preserve meaning, plot details, and character voice.\n" +
                "2. Prioritize fluent prose in the target language over literal calques.\n" +
                "3. Keep terminology consistent across segments.\n" +
                "4. Keep honorifics and culture-specific terms only when they are important for context.\n\n" +
                "### STYLE RULES\n" +
                "- Prefer idiomatic syntax and punctuation in the target language.\n" +
                "- Avoid machine-like phrasing and over-literal word order.\n" +
                "- Keep dialogue natural and character-appropriate.\n\n" +
                "### OUTPUT FORMAT\n" +
                "1. Return ONLY XML tags in the same shape as input: <s i='N'>...</s>.\n" +
                "2. No preamble, no explanations, no markdown.\n" +
                "3. Preserve the same indexes and segment count whenever possible."
    }
}
