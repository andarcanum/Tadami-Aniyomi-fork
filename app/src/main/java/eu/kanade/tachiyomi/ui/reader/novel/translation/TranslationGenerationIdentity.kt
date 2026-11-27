package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.domain.translation.profile.TranslationProtocol
import tachiyomi.domain.translation.service.TranslationFragmentPlanner

private const val TRANSLATION_PROMPT_TEMPLATE_VERSION = 1

private fun canonicalGenerationConfig(element: kotlinx.serialization.json.JsonElement): String = when (element) {
    is kotlinx.serialization.json.JsonObject -> element.entries.sortedBy {
        it.key
    }.joinToString(prefix = "{", postfix = "}") {
        kotlinx.serialization.json.JsonPrimitive(it.key).toString() + ":" + canonicalGenerationConfig(it.value)
    }
    is kotlinx.serialization.json.JsonArray -> element.joinToString(prefix = "[", postfix = "]") {
        canonicalGenerationConfig(it)
    }
    else -> element.toString()
}

internal fun NovelReaderSettings.translationEndpoint(): String {
    connectionProfile?.let { return profileApiRootUrl(it).toString() }
    return when (translationProvider) {
        NovelTranslationProvider.GEMINI,
        NovelTranslationProvider.GEMINI_PRIVATE,
        -> normalizeGeminiBaseUrl("https://generativelanguage.googleapis.com")
        NovelTranslationProvider.OPENROUTER -> normalizeOpenRouterBaseUrl(openRouterBaseUrl)
        NovelTranslationProvider.DEEPSEEK -> normalizeDeepSeekBaseUrl(deepSeekBaseUrl)
        NovelTranslationProvider.MISTRAL -> normalizeMistralBaseUrl(mistralBaseUrl)
        NovelTranslationProvider.NVIDIA -> normalizeNvidiaBaseUrl(nvidiaBaseUrl)
        NovelTranslationProvider.OLLAMA_CLOUD -> normalizeOllamaCloudBaseUrl(ollamaCloudBaseUrl)
    }
}

/** No credential or execution-only concurrency/retry setting contributes to artifact identity. */
internal fun NovelReaderSettings.translationGenerationFingerprint(): String {
    if (translationProfile != null) return profileGenerationFingerprint()
    val endpoint = translationEndpoint().toHttpUrlOrNull()
    val safeEndpoint = endpoint?.let { "${it.scheme}://${it.host}:${it.port}${it.encodedPath}" }.orEmpty()
    val wire = when (translationProvider) {
        NovelTranslationProvider.GEMINI -> {
            val config = geminiFrozenGenerationConfig?.let(::validateFrozenGeminiGenerationConfig)
                ?: buildPublicGeminiGenerationConfig(toGeminiTranslationParams())
            listOf(canonicalGenerationConfig(config))
        }
        NovelTranslationProvider.GEMINI_PRIVATE -> listOf(
            geminiReasoningEffort,
            geminiBudgetTokens.toString(),
            geminiTemperature.toString(),
            geminiTopP.toString(),
            geminiTopK.toString(),
            geminiPrivatePythonLikeMode.toString(),
        )
        NovelTranslationProvider.DEEPSEEK -> toDeepSeekTranslationParams().let {
            listOf(
                it.reasoningEffort,
                it.temperature.toString(),
                it.topP.toString(),
                it.presencePenalty.toString(),
                it.frequencyPenalty.toString(),
            )
        }
        else -> listOf(geminiReasoningEffort, geminiTemperature.toString(), geminiTopP.toString())
    }
    return TranslationFragmentPlanner.sourceFingerprint(
        listOf(
            translationProvider.name, translationCacheModelId(), safeEndpoint,
            TRANSLATION_PROMPT_TEMPLATE_VERSION.toString(), NOVEL_TRANSLATION_EXTRACTOR_VERSION.toString(),
            geminiSourceLang, geminiTargetLang, geminiPromptMode.name, geminiStylePreset.name,
            translationPromptModifiersFingerprint(),
        ) + wire,
    )
}

private fun NovelReaderSettings.profileGenerationFingerprint(): String {
    val profile = translationProfile ?: profileConfigurationFailure()
    val connection = connectionProfile ?: profileConfigurationFailure()
    val prepared = if (geminiRequestShape != null) {
        val id = geminiModel.removePrefix("models/")
        val url = profileApiRootUrl(connection).newBuilder().addPathSegment("models")
            .addPathSegment("$id:generateContent").build().toString()
        PreparedProfileRequest(
            url,
            buildJsonObject {
                put("shape", geminiRequestShape.name)
                put("generationConfig", geminiFrozenGenerationConfig ?: profileConfigurationFailure())
                put(
                    "safety",
                    if (geminiRequestShape ==
                        eu.kanade.tachiyomi.data.translation.profile.GeminiProfileRequestShape.PUBLIC_LEGACY
                    ) {
                        profile.exactModelId.startsWith("gemini-") && (
                            profile.generation.temperature != null ||
                                profile.model.reasoningContract !=
                                tachiyomi.domain.translation.profile.ReasoningContract.Unknown
                            )
                    } else {
                        true
                    },
                )
            },
        )
    } else {
        ProfileRequestBuilder.build(connection, profile.model, "", "")
    }
    val generation = JsonObject(
        prepared.body.filterKeys {
            it !in setOf("messages", "contents", "systemInstruction")
        },
    )
    return TranslationFragmentPlanner.sourceFingerprint(
        listOf(
            "profile-wire-v2", connection.protocol.name, connection.auth.name, prepared.url,
            TRANSLATION_PROMPT_TEMPLATE_VERSION.toString(), NOVEL_TRANSLATION_EXTRACTOR_VERSION.toString(),
            canonicalGenerationConfig(generation), profile.languages.source, profile.languages.target,
            profile.prompt.modeId, profile.prompt.styleId, profile.prompt.modifierIds.joinToString("\u0000"),
            profile.prompt.customText, systemPromptSnapshot.orEmpty(),
        ),
    )
}

internal fun String.translationLogEndpoint(): String = toHttpUrlOrNull()?.let {
    "${it.scheme}://${it.host}:${it.port}${it.encodedPath}"
} ?: "invalid endpoint"
