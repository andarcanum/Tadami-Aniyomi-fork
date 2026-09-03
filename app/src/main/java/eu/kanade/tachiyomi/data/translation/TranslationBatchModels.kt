package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import eu.kanade.tachiyomi.ui.reader.novel.translation.NOVEL_TRANSLATION_EXTRACTOR_VERSION
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationCacheRequirements
import eu.kanade.tachiyomi.ui.reader.novel.translation.translationCacheModelIdOf
import eu.kanade.tachiyomi.ui.reader.novel.translation.translationPromptModifiersFingerprintOf
import kotlinx.serialization.Serializable

@Serializable
data class TranslationQueueProfileSnapshot(
    val geminiEnabled: Boolean = false,
    val geminiModel: String = "",
    val geminiBatchSize: Int = 40,
    val geminiConcurrency: Int = 2,
    val geminiDisableCache: Boolean = false,
    val geminiRelaxedMode: Boolean = true,
    val geminiReasoningEffort: String = "minimal",
    val geminiBudgetTokens: Int = 8192,
    val geminiTemperature: Float = 0.7f,
    val geminiTopP: Float = 0.95f,
    val geminiTopK: Int = 40,
    val geminiSourceLang: String = "English",
    val geminiTargetLang: String = "Russian",
    val geminiPromptMode: String = GeminiPromptMode.ADULT_18.name,
    val geminiEnabledPromptModifiers: List<String> = emptyList(),
    val geminiCustomPromptModifier: String = "",
    val geminiStylePreset: String = NovelTranslationStylePreset.PROFESSIONAL.name,
    val geminiPromptModifiers: String = "",
    val geminiAutoTranslateEnglishSource: Boolean = false,
    val geminiPrefetchNextChapterTranslation: Boolean = false,
    val geminiPrivateUnlocked: Boolean = false,
    val geminiPrivatePythonLikeMode: Boolean = false,
    val translationProvider: String = NovelTranslationProvider.GEMINI.name,
    val openRouterBaseUrl: String = "https://openrouter.ai/api/v1",
    val openRouterModel: String = "",
    val deepSeekBaseUrl: String = "https://api.deepseek.com",
    val deepSeekModel: String = "deepseek-chat",
    val mistralBaseUrl: String = "https://api.mistral.ai/v1",
    val mistralModel: String = "mistral-large-latest",
    val nvidiaBaseUrl: String = "https://integrate.api.nvidia.com/v1",
    val nvidiaModel: String = "",
    val ollamaCloudBaseUrl: String = "https://ollama.com/api",
    val ollamaCloudModel: String = "gpt-oss:120b",
) {
    fun toReaderSettings(baseSettings: NovelReaderSettings): NovelReaderSettings {
        return baseSettings.copy(
            geminiEnabled = geminiEnabled,
            geminiModel = geminiModel,
            geminiBatchSize = geminiBatchSize,
            geminiConcurrency = geminiConcurrency,
            geminiDisableCache = geminiDisableCache,
            geminiRelaxedMode = geminiRelaxedMode,
            geminiReasoningEffort = geminiReasoningEffort,
            geminiBudgetTokens = geminiBudgetTokens,
            geminiTemperature = geminiTemperature,
            geminiTopP = geminiTopP,
            geminiTopK = geminiTopK,
            geminiSourceLang = geminiSourceLang,
            geminiTargetLang = geminiTargetLang,
            geminiPromptMode = geminiPromptMode.toGeminiPromptMode(baseSettings.geminiPromptMode),
            geminiEnabledPromptModifiers = geminiEnabledPromptModifiers,
            geminiCustomPromptModifier = geminiCustomPromptModifier,
            geminiStylePreset = geminiStylePreset.toStylePreset(baseSettings.geminiStylePreset),
            geminiPromptModifiers = geminiPromptModifiers,
            geminiAutoTranslateEnglishSource = geminiAutoTranslateEnglishSource,
            geminiPrefetchNextChapterTranslation = geminiPrefetchNextChapterTranslation,
            geminiPrivateUnlocked = geminiPrivateUnlocked,
            geminiPrivatePythonLikeMode = geminiPrivatePythonLikeMode,
            translationProvider = translationProvider.toTranslationProvider(baseSettings.translationProvider),
            openRouterBaseUrl = openRouterBaseUrl,
            openRouterModel = openRouterModel,
            deepSeekBaseUrl = deepSeekBaseUrl,
            deepSeekModel = deepSeekModel,
            mistralBaseUrl = mistralBaseUrl,
            mistralModel = mistralModel,
            nvidiaBaseUrl = nvidiaBaseUrl,
            nvidiaModel = nvidiaModel,
            ollamaCloudBaseUrl = ollamaCloudBaseUrl,
            ollamaCloudModel = ollamaCloudModel,
        )
    }
}

data class TranslationBatchRequest(
    val novelId: Long,
    val batchToken: String,
    val chapterIds: List<Long>,
    val profileSnapshot: TranslationQueueProfileSnapshot,
    val forceRetranslate: Boolean,
)

data class TranslationBatchEnqueueResult(
    val batchToken: String,
    val requestedCount: Int,
    val enqueuedCount: Int,
    val skippedAlreadyTranslatedCount: Int,
)

enum class TranslationBatchStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class TranslationBatchState(
    val batchToken: String,
    val novelId: Long,
    val status: TranslationBatchStatus,
    val total: Int,
    val enqueued: Int,
    val skipped: Int,
    val completed: Int,
    val failed: Int,
    val lastSuccessfulChapterId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

internal fun NovelReaderSettings.toTranslationQueueProfileSnapshot(): TranslationQueueProfileSnapshot {
    return TranslationQueueProfileSnapshot(
        geminiEnabled = geminiEnabled,
        geminiModel = geminiModel,
        geminiBatchSize = geminiBatchSize,
        geminiConcurrency = geminiConcurrency,
        geminiDisableCache = geminiDisableCache,
        geminiRelaxedMode = geminiRelaxedMode,
        geminiReasoningEffort = geminiReasoningEffort,
        geminiBudgetTokens = geminiBudgetTokens,
        geminiTemperature = geminiTemperature,
        geminiTopP = geminiTopP,
        geminiTopK = geminiTopK,
        geminiSourceLang = geminiSourceLang,
        geminiTargetLang = geminiTargetLang,
        geminiPromptMode = geminiPromptMode.name,
        geminiEnabledPromptModifiers = geminiEnabledPromptModifiers,
        geminiCustomPromptModifier = geminiCustomPromptModifier,
        geminiStylePreset = geminiStylePreset.name,
        geminiPromptModifiers = geminiPromptModifiers,
        geminiAutoTranslateEnglishSource = geminiAutoTranslateEnglishSource,
        geminiPrefetchNextChapterTranslation = geminiPrefetchNextChapterTranslation,
        geminiPrivateUnlocked = geminiPrivateUnlocked,
        geminiPrivatePythonLikeMode = geminiPrivatePythonLikeMode,
        translationProvider = translationProvider.name,
        openRouterBaseUrl = openRouterBaseUrl,
        openRouterModel = openRouterModel,
        deepSeekBaseUrl = deepSeekBaseUrl,
        deepSeekModel = deepSeekModel,
        mistralBaseUrl = mistralBaseUrl,
        mistralModel = mistralModel,
        nvidiaBaseUrl = nvidiaBaseUrl,
        nvidiaModel = nvidiaModel,
        ollamaCloudBaseUrl = ollamaCloudBaseUrl,
        ollamaCloudModel = ollamaCloudModel,
    )
}

private fun String.toGeminiPromptMode(fallback: GeminiPromptMode): GeminiPromptMode {
    return enumValues<GeminiPromptMode>().firstOrNull { it.name == this } ?: fallback
}

private fun String.toStylePreset(fallback: NovelTranslationStylePreset): NovelTranslationStylePreset {
    return enumValues<NovelTranslationStylePreset>().firstOrNull { it.name == this } ?: fallback
}

private fun String.toTranslationProvider(fallback: NovelTranslationProvider): NovelTranslationProvider {
    return enumValues<NovelTranslationProvider>().firstOrNull { it.name == this } ?: fallback
}

/**
 * Cache requirements for the batch skip-check, derived from the enqueued profile snapshot.
 *
 * The skip-check must answer exactly the same "is there a usable translation?" question the reader
 * asks on restore; probing by target language alone used to skip chapters whose cache was produced
 * by a different provider/model/prompt (the reader then showed the original text while the batch
 * reported "already translated" and enqueued nothing).
 */
internal fun TranslationQueueProfileSnapshot.toTranslationCacheRequirements(): NovelReaderTranslationCacheRequirements {
    val provider = translationProvider.toTranslationProvider(NovelTranslationProvider.GEMINI)
    return NovelReaderTranslationCacheRequirements(
        geminiEnabled = geminiEnabled,
        geminiDisableCache = geminiDisableCache,
        translationProvider = provider,
        modelId = translationCacheModelIdOf(
            provider = provider,
            geminiModel = geminiModel,
            openRouterModel = openRouterModel,
            deepSeekModel = deepSeekModel,
            mistralModel = mistralModel,
            nvidiaModel = nvidiaModel,
            ollamaCloudModel = ollamaCloudModel,
        ),
        sourceLang = geminiSourceLang,
        targetLang = geminiTargetLang,
        promptMode = geminiPromptMode.toGeminiPromptMode(GeminiPromptMode.ADULT_18),
        stylePreset = geminiStylePreset.toStylePreset(NovelTranslationStylePreset.PROFESSIONAL),
        extractorVersion = NOVEL_TRANSLATION_EXTRACTOR_VERSION,
        promptModifiersFingerprint = translationPromptModifiersFingerprintOf(
            enabledIds = geminiEnabledPromptModifiers,
            customModifier = geminiCustomPromptModifier,
            rawModifiers = geminiPromptModifiers,
        ),
    )
}
