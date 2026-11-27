package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import eu.kanade.tachiyomi.ui.reader.novel.translation.toGeminiTranslationParams
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
    /** Replace rules applied to the source before extraction; part of the cache identity. */
    val replaceRulesFingerprint: String = "",
    val snapshotVersion: Int = 0,
    val replaceRulesSnapshot: List<eu.kanade.tachiyomi.ui.reader.novel.replace.ReplaceRule>? = null,
    val geminiGenerationConfig: kotlinx.serialization.json.JsonObject? = null,
    val geminiInputTokenLimit: Int? = null,
    val connectionProfile: tachiyomi.domain.translation.profile.ConnectionProfile? = null,
    val translationProfile: tachiyomi.domain.translation.profile.TranslationProfile? = null,
    val systemPromptSnapshot: String? = null,
    val geminiRequestShape: eu.kanade.tachiyomi.data.translation.profile.GeminiProfileRequestShape? = null,
) {
    fun toReaderSettings(baseSettings: NovelReaderSettings): NovelReaderSettings {
        return baseSettings.copy(
            connectionProfile = if (snapshotVersion == 2) connectionProfile else null,
            translationProfile = if (snapshotVersion == 2) translationProfile else null,
            systemPromptSnapshot = if (snapshotVersion == 2) systemPromptSnapshot else null,
            geminiRequestShape = if (snapshotVersion == 2) geminiRequestShape else null,
            geminiEnabled = geminiEnabled,
            geminiModel = geminiModel,
            geminiBatchSize = geminiBatchSize,
            geminiConcurrency = geminiConcurrency,
            geminiDisableCache = geminiDisableCache,
            geminiRelaxedMode = geminiRelaxedMode,
            geminiReasoningEffort = geminiReasoningEffort,
            geminiBudgetTokens = geminiBudgetTokens,
            geminiFrozenGenerationConfig = geminiGenerationConfig,
            geminiFrozenInputTokenLimit = geminiInputTokenLimit,
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
    val priority: Int = 1,
)

data class TranslationBatchEnqueueResult(
    val batchToken: String,
    val requestedCount: Int,
    val enqueuedCount: Int,
    val skippedAlreadyTranslatedCount: Int,
    val conflictingCount: Int = 0,
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
    val cancelled: Int = 0,
)

internal fun NovelReaderSettings.toTranslationQueueProfileSnapshot(
    replaceRulesFingerprint: String = "",
    replaceRulesSnapshot: List<eu.kanade.tachiyomi.ui.reader.novel.replace.ReplaceRule>? = null,
): TranslationQueueProfileSnapshot {
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
        replaceRulesFingerprint = replaceRulesFingerprint,
        snapshotVersion = if (connectionProfile != null && translationProfile != null) 2 else 1,
        connectionProfile = connectionProfile?.copy(credentialBindings = connectionProfile.credentialBindings.toList()),
        translationProfile = translationProfile?.copy(
            prompt = translationProfile.prompt.copy(
                modifierIds = translationProfile.prompt.modifierIds.toList(),
            ),
        ),
        systemPromptSnapshot = systemPromptSnapshot,
        geminiRequestShape = geminiRequestShape,
        replaceRulesSnapshot = replaceRulesSnapshot,
        geminiGenerationConfig = if (translationProfile != null) {
            geminiFrozenGenerationConfig
        } else if (translationProvider == NovelTranslationProvider.GEMINI) {
            eu.kanade.tachiyomi.ui.reader.novel.translation.buildPublicGeminiGenerationConfig(
                this.toGeminiTranslationParams(),
            )
        } else {
            null
        },
        geminiInputTokenLimit = if (translationProvider == NovelTranslationProvider.GEMINI) {
            eu.kanade.tachiyomi.ui.reader.novel.translation.resolveGeminiModelCapabilities(geminiModel).inputTokenLimit
        } else {
            null
        },
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
