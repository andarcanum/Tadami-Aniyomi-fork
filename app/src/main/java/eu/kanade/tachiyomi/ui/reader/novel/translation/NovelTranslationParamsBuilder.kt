package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.data.translation.profile.TranslationProfileServices
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import tachiyomi.domain.translation.profile.ProfileAssignmentScope
import tachiyomi.domain.translation.profile.ProfileAuth
import java.util.Locale

internal const val DEEPSEEK_TEMPERATURE_MIN = 1.3f
internal const val DEEPSEEK_TEMPERATURE_MAX = 1.5f
internal const val DEEPSEEK_TOP_P_MIN = 0.9f
internal const val DEEPSEEK_TOP_P_MAX = 0.95f
internal const val DEEPSEEK_DEFAULT_PRESENCE_PENALTY = 0.15f
internal const val DEEPSEEK_DEFAULT_FREQUENCY_PENALTY = 0.15f
internal const val MAX_DEEPSEEK_CONCURRENCY = 32

/**
 * Builds the provider-specific translation parameters from the shared [NovelReaderSettings].
 *
 * Shared by the chapter reader and the background translation processor so both stay in sync.
 */
internal fun NovelReaderSettings.toGeminiTranslationParams(apiKey: String = geminiApiKey): GeminiTranslationParams {
    return GeminiTranslationParams(
        apiKey = apiKey,
        model = geminiModel.normalizeGeminiModelId(),
        sourceLang = geminiSourceLang,
        targetLang = geminiTargetLang,
        reasoningEffort = geminiReasoningEffort,
        budgetTokens = geminiBudgetTokens,
        temperature = geminiTemperature,
        topP = geminiTopP,
        topK = geminiTopK,
        promptMode = geminiPromptMode,
        promptModifiers = resolveTranslationPromptModifiers(family = translationPromptFamily()),
        provider = translationProvider,
        privateUnlocked = geminiPrivateUnlocked,
        privatePythonLikeMode = geminiPrivatePythonLikeMode,
        frozenGenerationConfig = geminiFrozenGenerationConfig,
    )
}

internal fun NovelReaderSettings.toOpenRouterTranslationParams(
    apiKey: String = openRouterApiKey,
): OpenRouterTranslationParams {
    return OpenRouterTranslationParams(
        baseUrl = openRouterBaseUrl,
        apiKey = apiKey,
        model = openRouterModel.trim(),
        sourceLang = geminiSourceLang,
        targetLang = geminiTargetLang,
        promptMode = geminiPromptMode,
        promptModifiers = resolveTranslationPromptModifiers(family = translationPromptFamily()),
        temperature = geminiTemperature,
        topP = geminiTopP,
        reasoningEffort = normalizeTranslationReasoningEffort(
            provider = NovelTranslationProvider.OPENROUTER,
            model = openRouterModel.trim(),
            value = geminiReasoningEffort,
        ),
    )
}

internal fun NovelReaderSettings.toDeepSeekTranslationParams(
    apiKey: String = deepSeekApiKey,
): DeepSeekTranslationParams {
    return DeepSeekTranslationParams(
        baseUrl = deepSeekBaseUrl,
        apiKey = apiKey,
        model = deepSeekModel.trim(),
        sourceLang = geminiSourceLang,
        targetLang = geminiTargetLang,
        promptMode = geminiPromptMode,
        promptModifiers = resolveTranslationPromptModifiers(family = translationPromptFamily()),
        temperature = geminiTemperature.coerceIn(DEEPSEEK_TEMPERATURE_MIN, DEEPSEEK_TEMPERATURE_MAX),
        topP = geminiTopP.coerceIn(DEEPSEEK_TOP_P_MIN, DEEPSEEK_TOP_P_MAX),
        reasoningEffort = normalizeTranslationReasoningEffort(
            provider = NovelTranslationProvider.DEEPSEEK,
            model = deepSeekModel.trim(),
            value = geminiReasoningEffort,
        ) ?: "none",
        presencePenalty = DEEPSEEK_DEFAULT_PRESENCE_PENALTY,
        frequencyPenalty = DEEPSEEK_DEFAULT_FREQUENCY_PENALTY,
    )
}

internal fun NovelReaderSettings.toMistralTranslationParams(apiKey: String = mistralApiKey): MistralTranslationParams {
    return MistralTranslationParams(
        baseUrl = mistralBaseUrl,
        apiKey = apiKey,
        model = mistralModel.trim(),
        sourceLang = geminiSourceLang,
        targetLang = geminiTargetLang,
        promptMode = geminiPromptMode,
        promptModifiers = resolveTranslationPromptModifiers(family = translationPromptFamily()),
        temperature = geminiTemperature,
        topP = geminiTopP,
        reasoningEffort = normalizeTranslationReasoningEffort(
            provider = NovelTranslationProvider.MISTRAL,
            model = mistralModel.trim(),
            value = geminiReasoningEffort,
        ),
    )
}

internal fun NovelReaderSettings.toNvidiaTranslationParams(apiKey: String = nvidiaApiKey): NvidiaTranslationParams {
    return NvidiaTranslationParams(
        baseUrl = nvidiaBaseUrl,
        apiKey = apiKey,
        model = nvidiaModel.trim(),
        sourceLang = geminiSourceLang,
        targetLang = geminiTargetLang,
        promptMode = geminiPromptMode,
        promptModifiers = resolveTranslationPromptModifiers(family = translationPromptFamily()),
        temperature = geminiTemperature,
        topP = geminiTopP,
    )
}

internal fun NovelReaderSettings.toOllamaCloudTranslationParams(
    apiKey: String = ollamaCloudApiKey,
): OllamaCloudTranslationParams {
    return OllamaCloudTranslationParams(
        baseUrl = ollamaCloudBaseUrl,
        apiKey = apiKey,
        model = ollamaCloudModel.trim(),
        sourceLang = geminiSourceLang,
        targetLang = geminiTargetLang,
        promptMode = geminiPromptMode,
        promptModifiers = resolveTranslationPromptModifiers(family = translationPromptFamily()),
        temperature = geminiTemperature,
        topP = geminiTopP,
        reasoningEffort = normalizeTranslationReasoningEffort(
            provider = NovelTranslationProvider.OLLAMA_CLOUD,
            model = ollamaCloudModel.trim(),
            value = geminiReasoningEffort,
        ),
    )
}

internal fun NovelReaderSettings.hasConfiguredTranslationProvider(novelId: Long? = null): Boolean {
    if (!geminiEnabled) return false
    if (translationProfile != null) {
        val conn = connectionProfile ?: return false
        if (conn.id != translationProfile.connectionId) return false
        if (conn.presetFlavor == tachiyomi.domain.translation.profile.PresetFlavor.GEMINI_PRIVATE &&
            GeminiPrivateBridge.isInstalled() && !isPrivateBridgeUnlocked()
        ) {
            return false
        }
        return true
    }
    if (hasAssignedTranslationProfile(novelId)) return true
    // Live preference state never contains secrets. Dispatch performs the authoritative vault check.
    val referenced = translationProvider in translationCredentialProviders
    return when (translationProvider) {
        NovelTranslationProvider.GEMINI -> (referenced || geminiApiKey.isNotBlank())
        NovelTranslationProvider.GEMINI_PRIVATE -> {
            (referenced || geminiApiKey.isNotBlank()) && isPrivateBridgeUnlocked()
        }
        NovelTranslationProvider.OPENROUTER -> {
            openRouterBaseUrl.isNotBlank() &&
                (referenced || openRouterApiKey.isNotBlank()) &&
                openRouterModel.isNotBlank()
        }
        NovelTranslationProvider.DEEPSEEK -> {
            deepSeekBaseUrl.isNotBlank() && (referenced || deepSeekApiKey.isNotBlank()) && deepSeekModel.isNotBlank()
        }
        NovelTranslationProvider.MISTRAL -> {
            mistralBaseUrl.isNotBlank() && (referenced || mistralApiKey.isNotBlank()) && mistralModel.isNotBlank()
        }
        NovelTranslationProvider.NVIDIA -> {
            (referenced || nvidiaApiKey.isNotBlank()) &&
                nvidiaModel.isNotBlank()
        }
        NovelTranslationProvider.OLLAMA_CLOUD -> {
            ollamaCloudBaseUrl.isNotBlank() &&
                (referenced || ollamaCloudApiKey.isNotBlank()) &&
                ollamaCloudModel.isNotBlank()
        }
    }
}

/** Mirrors the repository's NOVEL → SOURCE → GLOBAL precedence over the secret-free cached catalog. */
private fun NovelReaderSettings.hasAssignedTranslationProfile(novelId: Long?): Boolean {
    val catalog = TranslationProfileServices.cachedCatalog ?: return false
    fun assigned(scope: ProfileAssignmentScope, id: Long?): String? = id?.let { value ->
        catalog.assignments.singleOrNull { it.scope == scope && it.scopeId == value }?.profileId
    }
    val profileId = assigned(ProfileAssignmentScope.NOVEL, novelId)
        ?: assigned(ProfileAssignmentScope.SOURCE, translationSourceId)
        ?: assigned(ProfileAssignmentScope.GLOBAL, 0)
    val profile = catalog.profiles.singleOrNull { it.id == profileId } ?: return false
    val connection = catalog.connections.singleOrNull { it.id == profile.connectionId } ?: return false
    return connection.auth == ProfileAuth.NONE || connection.credentialBindings.isNotEmpty()
}

internal fun NovelReaderSettings.translationConcurrencyLimit(): Int {
    connectionProfile?.let { return it.maxConcurrency }
    return when (translationProvider) {
        NovelTranslationProvider.GEMINI -> geminiConcurrency.coerceIn(1, 8)
        NovelTranslationProvider.GEMINI_PRIVATE -> {
            if (shouldUseSinglePrivateChapterRequestMode()) 1 else geminiConcurrency.coerceIn(1, 8)
        }
        NovelTranslationProvider.OPENROUTER -> 1
        NovelTranslationProvider.DEEPSEEK -> geminiConcurrency.coerceIn(1, MAX_DEEPSEEK_CONCURRENCY)
        NovelTranslationProvider.OLLAMA_CLOUD -> geminiConcurrency.coerceIn(1, 8)
        else -> geminiConcurrency.coerceIn(1, 8)
    }
}

internal fun NovelReaderSettings.effectiveTranslationBatchSize(): Int {
    val requested = geminiBatchSize.coerceIn(1, 80)
    return when (translationProvider) {
        else -> requested
    }
}

internal fun NovelReaderSettings.shouldUseSinglePrivateChapterRequestMode(): Boolean {
    return translationProvider == NovelTranslationProvider.GEMINI_PRIVATE &&
        GeminiPrivateBridge.isInstalled() &&
        (GeminiPrivateBridge.forceSingleChapterRequest() || geminiPrivatePythonLikeMode)
}

internal fun NovelReaderSettings.requiresPrivateBridgeUnlock(): Boolean {
    return translationProvider == NovelTranslationProvider.GEMINI_PRIVATE &&
        GeminiPrivateBridge.isInstalled()
}

internal fun NovelReaderSettings.isPrivateBridgeUnlocked(): Boolean {
    if (!requiresPrivateBridgeUnlock()) return true
    return geminiPrivateUnlocked || GeminiPrivateBridge.isUnlocked()
}

private fun NovelReaderSettings.translationPromptFamily(): NovelTranslationPromptFamily {
    return when (translationProvider) {
        NovelTranslationProvider.GEMINI_PRIVATE,
        -> NovelTranslationPromptFamily.RUSSIAN
        NovelTranslationProvider.GEMINI,
        NovelTranslationProvider.OPENROUTER,
        NovelTranslationProvider.DEEPSEEK,
        NovelTranslationProvider.MISTRAL,
        NovelTranslationProvider.NVIDIA,
        NovelTranslationProvider.OLLAMA_CLOUD,
        ->
            resolveNovelTranslationPromptFamily(geminiTargetLang)
    }
}

private fun NovelReaderSettings.resolveTranslationPromptModifiers(
    family: NovelTranslationPromptFamily = NovelTranslationPromptFamily.RUSSIAN,
): String {
    val modifierText = GeminiPromptModifiers.buildPromptText(
        enabledIds = geminiEnabledPromptModifiers,
        customModifier = geminiCustomPromptModifier,
        family = family,
    )
    val styleDirective = NovelTranslationStylePresets.promptDirective(
        geminiStylePreset,
        family = family,
    ).trim()
    return listOf(
        styleDirective,
        modifierText,
        geminiPromptModifiers.trim(),
    ).filter { it.isNotBlank() }
        .joinToString("\n\n")
}

internal fun NovelReaderSettings.translationRequestConfigLog(): String {
    val common = buildString {
        append("provider=").append(translationProvider.name)
        append(", model=").append(translationCacheModelId())
        append(", lang=").append(geminiSourceLang).append("->").append(geminiTargetLang)
        append(", prompt=").append(geminiPromptMode.name)
        append(", style=").append(geminiStylePreset.name)
        if (shouldUseSinglePrivateChapterRequestMode()) {
            append(", batch=chapter")
            append(", concurrency=1")
        } else {
            append(", batch=").append(effectiveTranslationBatchSize())
            append(", concurrency=").append(translationConcurrencyLimit())
        }
        append(", relaxed=").append(geminiRelaxedMode)
        append(", cache=").append(!geminiDisableCache)
    }
    val sampling = when (translationProvider) {
        NovelTranslationProvider.GEMINI -> {
            "temp=${geminiTemperature.toLogFloat()}, topP=${geminiTopP.toLogFloat()}, topK=$geminiTopK, " +
                "reasoning=$geminiReasoningEffort, budgetTokens=$geminiBudgetTokens"
        }
        NovelTranslationProvider.GEMINI_PRIVATE -> {
            "temp=${geminiTemperature.toLogFloat()}, topP=${geminiTopP.toLogFloat()}, topK=$geminiTopK, " +
                "reasoning=$geminiReasoningEffort, budgetTokens=$geminiBudgetTokens, " +
                "singleRequest=${shouldUseSinglePrivateChapterRequestMode()}, " +
                "pythonLike=$geminiPrivatePythonLikeMode, " +
                "bridgeInstalled=${GeminiPrivateBridge.isInstalled()}, bridgeUnlocked=${isPrivateBridgeUnlocked()}"
        }
        NovelTranslationProvider.OPENROUTER -> {
            "baseUrl=${openRouterBaseUrl.trim().translationLogEndpoint()}, temp=${geminiTemperature.toLogFloat()}, " +
                "topP=${geminiTopP.toLogFloat()}"
        }
        NovelTranslationProvider.DEEPSEEK -> {
            val presencePenalty = DEEPSEEK_DEFAULT_PRESENCE_PENALTY.toLogFloat()
            val frequencyPenalty = DEEPSEEK_DEFAULT_FREQUENCY_PENALTY.toLogFloat()
            val reasoning = normalizeTranslationReasoningEffort(
                provider = NovelTranslationProvider.DEEPSEEK,
                model = deepSeekModel.trim(),
                value = geminiReasoningEffort,
            ) ?: "none"
            "baseUrl=${deepSeekBaseUrl.trim().translationLogEndpoint()}, temp=${geminiTemperature.toLogFloat()}, " +
                "topP=${geminiTopP.toLogFloat()}, " +
                "presencePenalty=$presencePenalty, frequencyPenalty=$frequencyPenalty, " +
                "reasoning=$reasoning, thinking=${if (reasoning == "none") "disabled" else "enabled"}, " +
                "stream=false"
        }
        NovelTranslationProvider.MISTRAL -> {
            "baseUrl=${mistralBaseUrl.trim().translationLogEndpoint()}, temp=${geminiTemperature.toLogFloat()}, " +
                "topP=${geminiTopP.toLogFloat()}, stream=false"
        }
        NovelTranslationProvider.NVIDIA -> {
            "baseUrl=${nvidiaBaseUrl.trim().translationLogEndpoint()}, temp=${geminiTemperature.toLogFloat()}, " +
                "topP=${geminiTopP.toLogFloat()}, stream=false"
        }
        NovelTranslationProvider.OLLAMA_CLOUD -> {
            val params = toOllamaCloudTranslationParams()
            val reasoning = params.reasoningEffort ?: "none"
            "baseUrl=${params.baseUrl.trim().translationLogEndpoint()}, temp=${params.temperature.toLogFloat()}, " +
                "topP=${params.topP.toLogFloat()}, think=$reasoning, stream=false"
        }
    }
    return redactTranslationDiagnostic(
        "$common, $sampling",
        listOf(geminiApiKey, openRouterApiKey, deepSeekApiKey, mistralApiKey, nvidiaApiKey, ollamaCloudApiKey),
    )
}

private fun Float.toLogFloat(): String = String.format(Locale.US, "%.3f", this)
