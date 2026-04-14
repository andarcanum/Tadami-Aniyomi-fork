package eu.kanade.tachiyomi.ui.reader.novel.translation

import android.app.Application
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class DeepSeekPromptResolver(
    private val application: Application,
) {
    private val adultPrompt: String by lazy {
        runCatching {
            application.assets.open(ADULT_PROMPT_ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.onFailure { error ->
            logcat(LogPriority.WARN, error) {
                "Failed to load adult DeepSeek prompt asset, falling back to classic prompt"
            }
        }.getOrElse { GeminiPromptResolver.CLASSIC_SYSTEM_PROMPT }
    }

    internal fun resolveSystemPrompt(mode: GeminiPromptMode): String {
        return resolveSystemPrompt(mode, NovelTranslationPromptFamily.RUSSIAN)
    }

    internal fun resolveSystemPrompt(
        mode: GeminiPromptMode,
        family: NovelTranslationPromptFamily = NovelTranslationPromptFamily.RUSSIAN,
    ): String {
        return when (mode) {
            GeminiPromptMode.CLASSIC -> when (family) {
                NovelTranslationPromptFamily.RUSSIAN -> GeminiPromptResolver.CLASSIC_SYSTEM_PROMPT
                NovelTranslationPromptFamily.ENGLISH -> GeminiPromptResolver.CLASSIC_SYSTEM_PROMPT_EN
            }
            GeminiPromptMode.ADULT_18 -> adultPrompt
        }
    }

    companion object {
        private const val ADULT_PROMPT_ASSET_PATH = "translation/deepseek_prompt_adult_18.txt"
    }
}
