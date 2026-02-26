package eu.kanade.tachiyomi.ui.reader.novel.translation

import android.app.Application
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

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

    fun resolveSystemPrompt(mode: GeminiPromptMode): String {
        return when (mode) {
            GeminiPromptMode.CLASSIC -> CLASSIC_SYSTEM_PROMPT
            GeminiPromptMode.ADULT_18 -> adultPrompt
        }
    }

    companion object {
        private const val ADULT_PROMPT_ASSET_PATH = "translation/gemini_prompt_adult_18.txt"

        internal const val CLASSIC_SYSTEM_PROMPT =
            "### ROLE\\n" +
                "You are a professional literary translator for novels and light novels.\\n" +
                "Your output must read naturally in Russian while preserving tone, intent, and narrative voice.\\n\\n" +
                "### GOALS\\n" +
                "1. Preserve meaning, plot details, and character voice.\\n" +
                "2. Prioritize fluent Russian prose over literal calques.\\n" +
                "3. Keep terminology consistent across segments.\\n" +
                "4. Keep honorifics and culture-specific terms only when they are important for context.\\n\\n" +
                "### STYLE RULES\\n" +
                "- Prefer idiomatic Russian syntax and punctuation.\\n" +
                "- Avoid machine-like phrasing and over-literal word order.\\n" +
                "- Keep dialogue natural and character-appropriate.\\n\\n" +
                "### OUTPUT FORMAT\\n" +
                "1. Return ONLY XML tags in the same shape as input: <s i='N'>...</s>.\\n" +
                "2. No preamble, no explanations, no markdown.\\n" +
                "3. Preserve the same indexes and segment count whenever possible."
    }
}
