package eu.kanade.tachiyomi.data.translation.profile

import android.app.Application
import eu.kanade.tachiyomi.data.translation.TranslationQueueProfileSnapshot
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekPromptResolver
import eu.kanade.tachiyomi.ui.reader.novel.translation.GEMINI_TRANSLATION_MAX_OUTPUT_TOKENS
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPrivateBridge
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPromptModifiers
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPromptResolver
import eu.kanade.tachiyomi.ui.reader.novel.translation.MistralPromptResolver
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelTranslationPromptFamily
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelTranslationStylePresets
import eu.kanade.tachiyomi.ui.reader.novel.translation.ProfileRequestBuilder
import eu.kanade.tachiyomi.ui.reader.novel.translation.profileConfigurationFailure
import eu.kanade.tachiyomi.ui.reader.novel.translation.resolveGeminiModelCapabilities
import eu.kanade.tachiyomi.ui.reader.novel.translation.resolveNovelTranslationPromptFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.PresetFlavor
import tachiyomi.domain.translation.profile.ReasoningChoice
import tachiyomi.domain.translation.profile.TranslationProfile
import tachiyomi.domain.translation.profile.TranslationProtocol
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Serializable
enum class GeminiProfileRequestShape { PUBLIC_LEGACY, PRIVATE_TAGGED, PRIVATE_PLAIN }

/** Public display/runtime configuration only. Nothing resolves or copies a credential here. */
internal fun NovelReaderSettings.withTranslationProfile(
    profile: TranslationProfile,
    connection: ConnectionProfile,
): NovelReaderSettings {
    if (profile.connectionId != connection.id) profileConfigurationFailure()
    val mode =
        GeminiPromptMode.entries.firstOrNull { it.name == profile.prompt.modeId } ?: profileConfigurationFailure()
    val style = NovelTranslationStylePreset.entries.firstOrNull { it.name == profile.prompt.styleId }
        ?: profileConfigurationFailure()
    val same = profile == translationProfile && connection == connectionProfile
    val provider = when (connection.presetFlavor) {
        PresetFlavor.GEMINI_PRIVATE -> NovelTranslationProvider.GEMINI_PRIVATE
        PresetFlavor.DEEPSEEK -> NovelTranslationProvider.DEEPSEEK
        PresetFlavor.MISTRAL -> NovelTranslationProvider.MISTRAL
        PresetFlavor.NVIDIA -> NovelTranslationProvider.NVIDIA
        PresetFlavor.OLLAMA -> NovelTranslationProvider.OLLAMA_CLOUD
        else -> when (connection.protocol) {
            TranslationProtocol.GEMINI_GENERATE -> NovelTranslationProvider.GEMINI
            TranslationProtocol.OLLAMA_NATIVE -> NovelTranslationProvider.OLLAMA_CLOUD
            TranslationProtocol.OPENAI_CHAT -> NovelTranslationProvider.OPENROUTER
        }
    }
    return copy(
        connectionProfile = connection, translationProfile = profile,
        systemPromptSnapshot = if (same) systemPromptSnapshot else null,
        geminiRequestShape = if (same) geminiRequestShape else null,
        geminiFrozenGenerationConfig = if (same) geminiFrozenGenerationConfig else null,
        translationProvider = provider,
        geminiApiKey = "", openRouterApiKey = "", deepSeekApiKey = "", mistralApiKey = "",
        nvidiaApiKey = "", ollamaCloudApiKey = "",
        geminiModel = profile.exactModelId, openRouterModel = profile.exactModelId,
        deepSeekModel = profile.exactModelId, mistralModel = profile.exactModelId,
        nvidiaModel = profile.exactModelId, ollamaCloudModel = profile.exactModelId,
        openRouterBaseUrl = connection.baseUrl, deepSeekBaseUrl = connection.baseUrl,
        mistralBaseUrl = connection.baseUrl, nvidiaBaseUrl = connection.baseUrl,
        ollamaCloudBaseUrl = connection.baseUrl,
        geminiConcurrency = connection.maxConcurrency,
        geminiSourceLang = profile.languages.source, geminiTargetLang = profile.languages.target,
        geminiPromptMode = mode, geminiStylePreset = style,
        geminiEnabledPromptModifiers = profile.prompt.modifierIds.toList(),
        geminiCustomPromptModifier = profile.prompt.customText, geminiPromptModifiers = "",
        geminiTemperature = profile.generation.temperature?.toFloat() ?: geminiTemperature,
        geminiTopP = profile.generation.topP?.toFloat() ?: geminiTopP,
        geminiTopK = profile.generation.topK ?: geminiTopK,
        geminiBudgetTokens = (profile.generation.reasoning as? ReasoningChoice.Budget)?.tokens ?: 8192,
        geminiReasoningEffort = when (val choice = profile.generation.reasoning) {
            is ReasoningChoice.Level -> choice.value
            is ReasoningChoice.Toggle -> if (choice.enabled) "high" else "minimal"
            else -> "minimal"
        },
    )
}

internal suspend fun TranslationQueueProfileSnapshot.freezeForProbe(): TranslationQueueProfileSnapshot =
    withContext(Dispatchers.IO) {
        if (systemPromptSnapshot != null) this@freezeForProbe else freezeProfileWire(Injekt.get<Application>())
    }

/** Called on IO at admission, not in composition. A non-null frozen prompt is never recalculated. */
internal fun TranslationQueueProfileSnapshot.freezeProfileWire(
    application: Application,
): TranslationQueueProfileSnapshot {
    if (snapshotVersion != 2) return this
    val connection = connectionProfile ?: profileConfigurationFailure()
    val profile = translationProfile ?: profileConfigurationFailure()
    if (profile.connectionId != connection.id) profileConfigurationFailure()
    if (systemPromptSnapshot != null) {
        if (systemPromptSnapshot.isBlank() || systemPromptSnapshot.length > 100_000) profileConfigurationFailure()
        if (geminiRequestShape != null && (
                connection.protocol != TranslationProtocol.GEMINI_GENERATE ||
                    geminiGenerationConfig == null
                )
        ) {
            profileConfigurationFailure()
        }
        return this
    }
    val privateBridge = connection.presetFlavor == PresetFlavor.GEMINI_PRIVATE && GeminiPrivateBridge.isInstalled()
    if (privateBridge && !geminiPrivateUnlocked && !GeminiPrivateBridge.isUnlocked()) profileConfigurationFailure()
    val google = connection.protocol == TranslationProtocol.GEMINI_GENERATE &&
        connection.baseUrl.toHttpUrl().host == "generativelanguage.googleapis.com"
    val shape = when {
        privateBridge && geminiPrivatePythonLikeMode -> GeminiProfileRequestShape.PRIVATE_PLAIN
        privateBridge -> GeminiProfileRequestShape.PRIVATE_TAGGED
        google -> GeminiProfileRequestShape.PUBLIC_LEGACY
        else -> null
    }
    val wireModel = if (privateBridge) {
        GeminiPrivateBridge.requestModelOverride(profile.exactModelId)
    } else {
        profile.exactModelId
    }
    val renderer = ProfilePromptRenderer(application)
    val system = renderer.systemPrompt(profile, connection, privateBridge, shape != null)
    val generation = if (connection.protocol != TranslationProtocol.GEMINI_GENERATE) {
        null
    } else if (privateBridge) {
        val caps = resolveGeminiModelCapabilities(wireModel)
        buildJsonObject {
            put(
                "maxOutputTokens",
                GeminiPrivateBridge.requestMaxOutputTokensOverride(
                    profile.generation.maxOutputTokens ?: GEMINI_TRANSLATION_MAX_OUTPUT_TOKENS,
                ),
            )
            put(
                "temperature",
                GeminiPrivateBridge.requestTemperatureOverride(geminiTemperature)
                    .coerceIn(0f, caps.maxTemperature ?: 2f),
            )
            put("topP", GeminiPrivateBridge.requestTopPOverride(geminiTopP).coerceIn(0f, 1f))
            put("topK", GeminiPrivateBridge.requestTopKOverride(geminiTopK))
            put("frequencyPenalty", GeminiPrivateBridge.requestFrequencyPenaltyOverride(0f))
            put("presencePenalty", GeminiPrivateBridge.requestPresencePenaltyOverride(0f))
            put(
                "thinkingConfig",
                buildJsonObject {
                    put("thinkingLevel", GeminiPrivateBridge.requestThinkingLevelOverride(geminiReasoningEffort))
                },
            )
        }
    } else {
        ProfileRequestBuilder.build(connection, profile.model, "", "").body["generationConfig"] as JsonObject
    }
    return copy(
        systemPromptSnapshot = system,
        geminiRequestShape = shape,
        geminiModel = wireModel,
        geminiGenerationConfig = generation,
    )
}

private class ProfilePromptRenderer(application: Application) {
    private val gemini = GeminiPromptResolver(application)
    private val deepSeek = DeepSeekPromptResolver(application)
    private val mistral = MistralPromptResolver(application)

    fun systemPrompt(
        profile: TranslationProfile,
        connection: ConnectionProfile,
        privateBridge: Boolean,
        legacyGemini: Boolean,
    ): String {
        val family = if (privateBridge) {
            NovelTranslationPromptFamily.RUSSIAN
        } else {
            resolveNovelTranslationPromptFamily(profile.languages.target)
        }
        val mode = GeminiPromptMode.entries.firstOrNull { it.name == profile.prompt.modeId }
            ?: profileConfigurationFailure()
        val style = NovelTranslationStylePreset.entries.firstOrNull { it.name == profile.prompt.styleId }
            ?: profileConfigurationFailure()
        val base = when (connection.presetFlavor) {
            PresetFlavor.DEEPSEEK -> deepSeek.resolveSystemPrompt(mode, family)
            PresetFlavor.MISTRAL -> mistral.resolveSystemPrompt(mode, family)
            PresetFlavor.OPENROUTER, PresetFlavor.NVIDIA, PresetFlavor.OLLAMA ->
                gemini.resolveSystemPrompt(GeminiPromptMode.CLASSIC, family)
            else -> gemini.resolveSystemPrompt(mode, family)
        }
        val modifiers = if (privateBridge && GeminiPrivateBridge.disablePromptModifiers()) {
            ""
        } else {
            listOf(
                NovelTranslationStylePresets.promptDirective(style, family).trim(),
                GeminiPromptModifiers.buildPromptText(profile.prompt.modifierIds, profile.prompt.customText, family),
            ).filter { it.isNotBlank() }.joinToString("\n\n")
        }
        val ordinary = base + if (modifiers.isBlank()) "" else "\n\n$modifiers"
        return if (privateBridge) GeminiPrivateBridge.systemPromptOverride() ?: ordinary else ordinary
    }
}
