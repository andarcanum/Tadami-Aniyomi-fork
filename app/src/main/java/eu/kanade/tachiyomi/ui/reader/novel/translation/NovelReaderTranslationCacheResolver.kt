package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset

internal data class NovelReaderTranslationCacheRequirements(
    val geminiEnabled: Boolean,
    val geminiDisableCache: Boolean,
    val translationProvider: NovelTranslationProvider,
    val modelId: String,
    val sourceLang: String,
    val targetLang: String,
    val promptMode: GeminiPromptMode,
    val stylePreset: NovelTranslationStylePreset,
)

internal object NovelReaderTranslationCacheResolver {
    fun matches(
        cached: GeminiTranslationCacheEntry?,
        requirements: NovelReaderTranslationCacheRequirements,
    ): Boolean {
        if (!requirements.geminiEnabled || requirements.geminiDisableCache) return false
        if (cached == null) return false
        if (cached.translatedByIndex.isEmpty()) return false

        return cached.provider == requirements.translationProvider &&
            cached.model == requirements.modelId &&
            cached.sourceLang == requirements.sourceLang &&
            cached.targetLang == requirements.targetLang &&
            cached.promptMode == requirements.promptMode &&
            cached.stylePreset == requirements.stylePreset
    }
}

internal fun NovelReaderSettings.toTranslationCacheRequirements(): NovelReaderTranslationCacheRequirements {
    return NovelReaderTranslationCacheRequirements(
        geminiEnabled = geminiEnabled,
        geminiDisableCache = geminiDisableCache,
        translationProvider = translationProvider,
        modelId = translationCacheModelId(),
        sourceLang = geminiSourceLang,
        targetLang = geminiTargetLang,
        promptMode = geminiPromptMode,
        stylePreset = geminiStylePreset,
    )
}

internal fun NovelReaderSettings.translationCacheModelId(): String {
    return when (translationProvider) {
        NovelTranslationProvider.GEMINI -> geminiModel.normalizeGeminiModelId()
        NovelTranslationProvider.GEMINI_PRIVATE -> geminiModel.normalizeGeminiModelId()
        NovelTranslationProvider.AIRFORCE -> airforceModel.trim()
        NovelTranslationProvider.OPENROUTER -> openRouterModel.trim()
        NovelTranslationProvider.DEEPSEEK -> deepSeekModel.trim()
    }
}

internal fun String.normalizeGeminiModelId(): String {
    return when (trim()) {
        // Legacy key kept for backward compatibility with old settings.
        "gemini-3-flash" -> "gemini-3-flash-preview"
        "gemini-2.5-flash" -> "gemini-3.1-flash-lite-preview"
        else -> this
    }
}
