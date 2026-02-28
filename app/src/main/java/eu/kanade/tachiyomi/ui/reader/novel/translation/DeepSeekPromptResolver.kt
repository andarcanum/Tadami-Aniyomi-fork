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

    fun resolveSystemPrompt(mode: GeminiPromptMode): String {
        return when (mode) {
            GeminiPromptMode.CLASSIC -> GeminiPromptResolver.CLASSIC_SYSTEM_PROMPT
            GeminiPromptMode.ADULT_18 -> adultPrompt
        }
    }

    companion object {
        private const val ADULT_PROMPT_ASSET_PATH = "translation/deepseek_prompt_adult_18.txt"
    }
}
