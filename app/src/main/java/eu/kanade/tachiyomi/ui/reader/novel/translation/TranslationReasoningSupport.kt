package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider

internal fun resolveTranslationReasoningOptions(
    provider: NovelTranslationProvider,
    model: String,
): List<String> {
    val normalizedModel = model.trim().lowercase()
    return when (provider) {
        NovelTranslationProvider.GEMINI,
        NovelTranslationProvider.GEMINI_PRIVATE,
        -> resolveGeminiModelCapabilities(normalizedModel).reasoningOptions
        NovelTranslationProvider.OPENROUTER -> when {
            normalizedModel.contains("/gemini-3") -> listOf("minimal", "low", "medium", "high")
            else -> emptyList()
        }
        NovelTranslationProvider.MISTRAL -> when {
            normalizedModel == "mistral-small-latest" -> listOf("none", "high")
            else -> emptyList()
        }
        NovelTranslationProvider.DEEPSEEK -> when {
            normalizedModel.isNotBlank() -> listOf("none", "high", "max")
            else -> emptyList()
        }
        NovelTranslationProvider.NVIDIA -> emptyList()
        NovelTranslationProvider.OLLAMA_CLOUD -> when {
            normalizedModel.startsWith("gpt-oss") ||
                normalizedModel.startsWith("qwen3") ||
                normalizedModel.startsWith("deepseek-r1") -> listOf("none", "low", "medium", "high")
            else -> listOf("none")
        }
    }
}

internal fun normalizeTranslationReasoningEffort(
    provider: NovelTranslationProvider,
    model: String,
    value: String,
): String? {
    val normalizedValue = value.trim().lowercase()
    val supportedOptions = resolveTranslationReasoningOptions(provider, model)
    if (supportedOptions.isEmpty()) return null
    return normalizedValue.takeIf { it in supportedOptions } ?: supportedOptions.first()
}
