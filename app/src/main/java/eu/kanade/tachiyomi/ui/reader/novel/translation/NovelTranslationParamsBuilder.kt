package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
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
internal fun NovelReaderSettings.toGeminiTranslationParams(): GeminiTranslationParams {
    return GeminiTranslationParams(
        apiKey = geminiApiKey,
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
    )
}

internal fun NovelReaderSettings.toOpenRouterTranslationParams(): OpenRouterTranslationParams {
    return OpenRouterTranslationParams(
        baseUrl = openRouterBaseUrl,
        apiKey = openRouterApiKey,
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

internal fun NovelReaderSettings.toDeepSeekTranslationParams(): DeepSeekTranslationParams {
    return DeepSeekTranslationParams(
        baseUrl = deepSeekBaseUrl,
        apiKey = deepSeekApiKey,
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

internal fun NovelReaderSettings.toMistralTranslationParams(): MistralTranslationParams {
    return MistralTranslationParams(
        baseUrl = mistralBaseUrl,
        apiKey = mistralApiKey,
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

internal fun NovelReaderSettings.toNvidiaTranslationParams(): NvidiaTranslationParams {
    return NvidiaTranslationParams(
        baseUrl = nvidiaBaseUrl,
        apiKey = nvidiaApiKey,
        model = nvidiaModel.trim(),
        sourceLang = geminiSourceLang,
        targetLang = geminiTargetLang,
        promptMode = geminiPromptMode,
        promptModifiers = resolveTranslationPromptModifiers(family = translationPromptFamily()),
        temperature = geminiTemperature,
        topP = geminiTopP,
    )
}

internal fun NovelReaderSettings.toOllamaCloudTranslationParams(): OllamaCloudTranslationParams {
    return OllamaCloudTranslationParams(
        baseUrl = ollamaCloudBaseUrl,
        apiKey = ollamaCloudApiKey,
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

internal fun NovelReaderSettings.hasConfiguredTranslationProvider(): Boolean {
    if (!geminiEnabled) return false
    return when (translationProvider) {
        NovelTranslationProvider.GEMINI -> geminiApiKey.isNotBlank()
        NovelTranslationProvider.GEMINI_PRIVATE -> {
            geminiApiKey.isNotBlank() && isPrivateBridgeUnlocked()
        }
        NovelTranslationProvider.OPENROUTER -> {
            openRouterBaseUrl.isNotBlank() &&
                openRouterApiKey.isNotBlank() &&
                openRouterModel.isNotBlank()
        }
        NovelTranslationProvider.DEEPSEEK -> {
            deepSeekBaseUrl.isNotBlank() && deepSeekApiKey.isNotBlank() && deepSeekModel.isNotBlank()
        }
        NovelTranslationProvider.MISTRAL -> {
            mistralBaseUrl.isNotBlank() && mistralApiKey.isNotBlank() && mistralModel.isNotBlank()
        }
        NovelTranslationProvider.NVIDIA -> {
            nvidiaApiKey.isNotBlank() &&
                nvidiaModel.isNotBlank()
        }
        NovelTranslationProvider.OLLAMA_CLOUD -> {
            ollamaCloudBaseUrl.isNotBlank() &&
                ollamaCloudApiKey.isNotBlank() &&
                ollamaCloudModel.isNotBlank()
        }
    }
}

internal fun NovelReaderSettings.translationConcurrencyLimit(): Int {
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
            "baseUrl=${openRouterBaseUrl.trim()}, temp=${geminiTemperature.toLogFloat()}, " +
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
            "baseUrl=${deepSeekBaseUrl.trim()}, temp=${geminiTemperature.toLogFloat()}, " +
                "topP=${geminiTopP.toLogFloat()}, " +
                "presencePenalty=$presencePenalty, frequencyPenalty=$frequencyPenalty, " +
                "reasoning=$reasoning, thinking=${if (reasoning == "none") "disabled" else "enabled"}, " +
                "stream=false"
        }
        NovelTranslationProvider.MISTRAL -> {
            "baseUrl=${mistralBaseUrl.trim()}, temp=${geminiTemperature.toLogFloat()}, " +
                "topP=${geminiTopP.toLogFloat()}, stream=false"
        }
        NovelTranslationProvider.NVIDIA -> {
            "baseUrl=${nvidiaBaseUrl.trim()}, temp=${geminiTemperature.toLogFloat()}, " +
                "topP=${geminiTopP.toLogFloat()}, stream=false"
        }
        NovelTranslationProvider.OLLAMA_CLOUD -> {
            val params = toOllamaCloudTranslationParams()
            val reasoning = params.reasoningEffort ?: "none"
            "baseUrl=${params.baseUrl.trim()}, temp=${params.temperature.toLogFloat()}, " +
                "topP=${params.topP.toLogFloat()}, think=$reasoning, stream=false"
        }
    }
    return "$common, $sampling"
}

private fun Float.toLogFloat(): String = String.format(Locale.US, "%.3f", this)
