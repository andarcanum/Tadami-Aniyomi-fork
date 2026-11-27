package eu.kanade.tachiyomi.ui.reader.novel.translation

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import tachiyomi.domain.translation.model.TranslationAttemptException
import tachiyomi.domain.translation.model.TranslationFailure
import tachiyomi.domain.translation.model.TranslationFailureKind

/** Freeze wire options at enqueue, so descriptor refresh/process restart cannot change active work. */
internal fun buildPublicGeminiGenerationConfig(params: GeminiTranslationParams): JsonObject {
    val caps = resolveGeminiModelCapabilities(params.model)
    val metadata = GeminiModelMetadataStore.lookup(params.model)
    return buildJsonObject {
        put("maxOutputTokens", caps.maxOutputTokens())
        if (caps.thinking != GeminiThinkingContract.Unknown || metadata != null) {
            put("temperature", params.temperature.coerceIn(0f, caps.maxTemperature ?: 2f))
            put("topP", params.topP.coerceIn(0f, 1f))
            if (caps.topK != null) put("topK", params.topK)
        }
        caps.thinkingConfig(params.reasoningEffort, params.budgetTokens)?.let { put("thinkingConfig", it) }
    }
}

internal fun validateFrozenGeminiGenerationConfig(config: JsonObject): JsonObject {
    val allowed = setOf("maxOutputTokens", "temperature", "topP", "topK", "thinkingConfig")
    val max = (config["maxOutputTokens"] as? JsonPrimitive)?.intOrNull
    if (config.keys.any { it !in allowed } || max == null || max !in 1..65_536) {
        throw TranslationAttemptException(
            TranslationFailure(TranslationFailureKind.CONFIGURATION, "Invalid frozen Gemini generation configuration"),
        )
    }
    val thinking = config["thinkingConfig"]
    if (thinking != null && (
            thinking !is JsonObject || thinking.keys.size != 1 ||
                thinking.keys.any { it !in setOf("thinkingLevel", "thinkingBudget") }
            )
    ) {
        throw TranslationAttemptException(
            TranslationFailure(TranslationFailureKind.CONFIGURATION, "Invalid frozen reasoning configuration"),
        )
    }
    return config
}
