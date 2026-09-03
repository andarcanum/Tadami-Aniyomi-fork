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
    val extractorVersion: Int,
    val promptModifiersFingerprint: String = "",
)

internal object NovelReaderTranslationCacheResolver {
    fun matches(
        cached: GeminiTranslationCacheEntry?,
        requirements: NovelReaderTranslationCacheRequirements,
    ): Boolean {
        if (!requirements.geminiEnabled || requirements.geminiDisableCache) return false
        if (cached == null) return false
        if (cached.translatedByIndex.isEmpty()) return false

        return requirements.matchesEntryMetadata(
            entryProvider = cached.provider,
            entryModel = cached.model,
            entrySourceLang = cached.sourceLang,
            entryTargetLang = cached.targetLang,
            entryPromptMode = cached.promptMode,
            entryStylePreset = cached.stylePreset,
            entryExtractorVersion = cached.extractorVersion,
            entryPromptModifiersFingerprint = cached.promptModifiersFingerprint,
        )
    }
}

/**
 * Single source of truth for "does this cached metadata belong to the current requirements".
 * Shared by the resolver, the disk-cache index lookups and their file-scan fallbacks so the
 * comparisons cannot drift apart again.
 */
internal fun NovelReaderTranslationCacheRequirements.matchesEntryMetadata(
    entryProvider: NovelTranslationProvider,
    entryModel: String,
    entrySourceLang: String,
    entryTargetLang: String,
    entryPromptMode: GeminiPromptMode,
    entryStylePreset: NovelTranslationStylePreset,
    entryExtractorVersion: Int,
    entryPromptModifiersFingerprint: String,
): Boolean {
    return entryProvider == translationProvider &&
        entryModel == modelId &&
        entrySourceLang == sourceLang &&
        entryTargetLang == targetLang &&
        entryPromptMode == promptMode &&
        entryStylePreset == stylePreset &&
        entryExtractorVersion == extractorVersion &&
        entryPromptModifiersFingerprint == promptModifiersFingerprint
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
        extractorVersion = NOVEL_TRANSLATION_EXTRACTOR_VERSION,
        promptModifiersFingerprint = translationPromptModifiersFingerprint(),
    )
}

/**
 * Stable fingerprint of everything that shapes the prompt beyond provider/model/languages/mode/
 * style: the enabled modifier ids, the custom directive and the raw modifier text. Cached
 * translations produced under different modifiers must not be served or re-stamped.
 */
internal fun NovelReaderSettings.translationPromptModifiersFingerprint(): String {
    return translationPromptModifiersFingerprintOf(
        enabledIds = geminiEnabledPromptModifiers,
        customModifier = geminiCustomPromptModifier,
        rawModifiers = geminiPromptModifiers,
    )
}

/**
 * Shared primitive form of the modifiers fingerprint so the settings-based and the queue
 * snapshot-based requirements cannot drift apart.
 */
internal fun translationPromptModifiersFingerprintOf(
    enabledIds: List<String>,
    customModifier: String,
    rawModifiers: String,
): String {
    return listOf(
        enabledIds.sorted().joinToString(","),
        customModifier.trim(),
        rawModifiers.trim(),
    )
        .filter { it.isNotBlank() }
        .joinToString("\u0000")
}

/**
 * Namespace for the in-memory per-segment translation cache: every setting that changes the
 * produced translation gets a slot, so switching provider/model/prompt/style/modifiers can never
 * serve (or poison) another configuration's segments.
 */
internal fun NovelReaderSettings.translationCacheNamespace(): String {
    return listOf(
        translationProvider.name,
        translationCacheModelId(),
        geminiSourceLang,
        geminiTargetLang,
        geminiPromptMode.name,
        geminiStylePreset.name,
        translationPromptModifiersFingerprint(),
    ).joinToString("\u0000")
}

internal fun NovelReaderSettings.translationCacheModelId(): String {
    return translationCacheModelIdOf(
        provider = translationProvider,
        geminiModel = geminiModel,
        openRouterModel = openRouterModel,
        deepSeekModel = deepSeekModel,
        mistralModel = mistralModel,
        nvidiaModel = nvidiaModel,
        ollamaCloudModel = ollamaCloudModel,
    )
}

/** Shared primitive form of the cache model id (see [translationPromptModifiersFingerprintOf]). */
internal fun translationCacheModelIdOf(
    provider: NovelTranslationProvider,
    geminiModel: String,
    openRouterModel: String,
    deepSeekModel: String,
    mistralModel: String,
    nvidiaModel: String,
    ollamaCloudModel: String,
): String {
    return when (provider) {
        NovelTranslationProvider.GEMINI -> geminiModel.normalizeGeminiModelId()
        NovelTranslationProvider.GEMINI_PRIVATE -> geminiModel.normalizeGeminiModelId()
        NovelTranslationProvider.OPENROUTER -> openRouterModel.trim()
        NovelTranslationProvider.DEEPSEEK -> deepSeekModel.trim()
        NovelTranslationProvider.MISTRAL -> mistralModel.trim()
        NovelTranslationProvider.NVIDIA -> nvidiaModel.trim()
        NovelTranslationProvider.OLLAMA_CLOUD -> ollamaCloudModel.trim()
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
