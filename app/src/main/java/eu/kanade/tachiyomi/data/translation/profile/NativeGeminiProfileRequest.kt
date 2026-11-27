package eu.kanade.tachiyomi.data.translation.profile

import eu.kanade.tachiyomi.data.translation.TranslationQueueProfileSnapshot
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPrivateBridge
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelTranslationPromptFamily
import eu.kanade.tachiyomi.ui.reader.novel.translation.PreparedProfileRequest
import eu.kanade.tachiyomi.ui.reader.novel.translation.buildNovelTranslationUserPrompt
import eu.kanade.tachiyomi.ui.reader.novel.translation.escapeTranslationSource
import eu.kanade.tachiyomi.ui.reader.novel.translation.geminiSafetySettingsJsonArray
import eu.kanade.tachiyomi.ui.reader.novel.translation.profileApiRootUrl
import eu.kanade.tachiyomi.ui.reader.novel.translation.profileConfigurationFailure
import eu.kanade.tachiyomi.ui.reader.novel.translation.resolveNovelTranslationPromptFamily
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import tachiyomi.domain.translation.profile.ReasoningContract

/** Preserves Google's existing public/private payload shape with already-frozen wire options. */
internal fun TranslationQueueProfileSnapshot.nativeGeminiRequest(segments: List<String>): PreparedProfileRequest {
    val shape = geminiRequestShape ?: profileConfigurationFailure()
    val connection = connectionProfile ?: profileConfigurationFailure()
    val profile = translationProfile ?: profileConfigurationFailure()
    val system = systemPromptSnapshot ?: profileConfigurationFailure()
    val generation = geminiGenerationConfig ?: profileConfigurationFailure()
    validateNativeGeminiGeneration(generation)
    val private = shape != GeminiProfileRequestShape.PUBLIC_LEGACY
    if (private && !GeminiPrivateBridge.isInstalled()) profileConfigurationFailure()
    val source = if (private) GeminiPrivateBridge.preprocessSegments(segments) else segments
    if (source.size != segments.size) profileConfigurationFailure()
    val tagged = source.mapIndexed { index, text -> "<s i='$index'>${escapeTranslationSource(text)}</s>" }
        .joinToString("\n")
    val family = if (private) {
        NovelTranslationPromptFamily.RUSSIAN
    } else {
        resolveNovelTranslationPromptFamily(profile.languages.target)
    }
    val user = buildNovelTranslationUserPrompt(profile.languages.source, profile.languages.target, tagged, family)
    val content = when (shape) {
        GeminiProfileRequestShape.PUBLIC_LEGACY -> "$system\n\n$user"
        GeminiProfileRequestShape.PRIVATE_TAGGED -> tagged
        GeminiProfileRequestShape.PRIVATE_PLAIN -> source.joinToString("\n\n")
    }
    val id = geminiModel.removePrefix("models/")
    if (!Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,199}").matches(id)) profileConfigurationFailure()
    val url = profileApiRootUrl(connection).newBuilder()
        .addPathSegment("models").addPathSegment("$id:generateContent").build()
    val body = buildJsonObject {
        if (private) {
            put(
                "systemInstruction",
                buildJsonObject {
                    put("role", "system")
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) })
                },
            )
        }
        put(
            "contents",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("parts", buildJsonArray { add(buildJsonObject { put("text", content) }) })
                    },
                )
            },
        )
        put("generationConfig", generation)
        if (!id.startsWith("gemma-", ignoreCase = true) && (
                private || (
                    id.startsWith("gemini-") && (
                        profile.model.reasoningContract != ReasoningContract.Unknown ||
                            profile.generation.temperature != null
                        )
                    )
                )
        ) {
            put("safetySettings", geminiSafetySettingsJsonArray())
        }
    }
    return PreparedProfileRequest(
        url = url.toString(),
        body = body,
        isPlaintext = shape == GeminiProfileRequestShape.PRIVATE_PLAIN,
    )
}

internal fun validateNativeGeminiGeneration(config: JsonObject) {
    val allowed = setOf(
        "maxOutputTokens",
        "temperature",
        "topP",
        "topK",
        "thinkingConfig",
        "frequencyPenalty",
        "presencePenalty",
    )
    if (config.keys.any { it !in allowed }) profileConfigurationFailure()
    val output = (config["maxOutputTokens"] as? JsonPrimitive)?.intOrNull
    if ((output != null && output !in 1..65_536) || (config["maxOutputTokens"] != null && output == null)) {
        profileConfigurationFailure()
    }
    listOf("temperature", "topP", "frequencyPenalty", "presencePenalty").forEach { field ->
        config[field]?.let { value ->
            val number = (value as? JsonPrimitive)?.doubleOrNull ?: profileConfigurationFailure()
            if (!number.isFinite() || (field == "temperature" && number < 0) ||
                (field == "topP" && number !in 0.0..1.0)
            ) {
                profileConfigurationFailure()
            }
        }
    }
    config["topK"]?.let { value ->
        if ((value as? JsonPrimitive)?.intOrNull?.let { it >= 0 } != true) profileConfigurationFailure()
    }
    config["thinkingConfig"]?.let { value ->
        val thinking = value as? JsonObject ?: profileConfigurationFailure()
        if (thinking.size != 1 || thinking.keys.any { it != "thinkingLevel" && it != "thinkingBudget" }) {
            profileConfigurationFailure()
        }
        thinking["thinkingLevel"]?.let {
            val level = (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: profileConfigurationFailure()
            if (level !in setOf("minimal", "low", "medium", "high")) profileConfigurationFailure()
        }
        thinking["thinkingBudget"]?.let {
            if ((it as? JsonPrimitive)?.intOrNull?.let { budget -> budget >= -1 } != true) profileConfigurationFailure()
        }
    }
}
