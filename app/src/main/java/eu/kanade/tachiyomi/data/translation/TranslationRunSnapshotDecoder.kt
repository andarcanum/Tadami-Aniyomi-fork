package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.data.translation.profile.GeminiProfileRequestShape
import eu.kanade.tachiyomi.data.translation.profile.validateNativeGeminiGeneration
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import eu.kanade.tachiyomi.ui.reader.novel.translation.ProfileRequestBuilder
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import tachiyomi.domain.translation.model.TranslationAttemptException
import tachiyomi.domain.translation.model.TranslationFailure
import tachiyomi.domain.translation.model.TranslationFailureKind
import tachiyomi.domain.translation.profile.PresetFlavor
import tachiyomi.domain.translation.profile.TranslationProtocol

/** Stored intent is not a tolerant settings import: unknown/coerced metadata never becomes a live request. */
internal fun decodeTranslationRunSnapshot(encoded: String, json: Json): TranslationQueueProfileSnapshot = try {
    require(encoded.toByteArray(Charsets.UTF_8).size <= 1_048_576)
    val strict = Json(json) {
        ignoreUnknownKeys = false
        coerceInputValues = false
        isLenient = false
    }
    val root = strict.parseToJsonElement(encoded) as JsonObject
    val version = root["snapshotVersion"] as? JsonPrimitive
    require(version == null || (!version.isString && version.intOrNull != null))
    val snapshot = strict.decodeFromString<TranslationQueueProfileSnapshot>(encoded)
    require(snapshot.snapshotVersion in 0..2)
    require(NovelTranslationProvider.entries.any { it.name == snapshot.translationProvider })
    require(GeminiPromptMode.entries.any { it.name == snapshot.geminiPromptMode })
    require(NovelTranslationStylePreset.entries.any { it.name == snapshot.geminiStylePreset })
    if (snapshot.snapshotVersion < 2) {
        require(
            snapshot.connectionProfile == null && snapshot.translationProfile == null &&
                snapshot.systemPromptSnapshot == null && snapshot.geminiRequestShape == null,
        )
    } else {
        val connection = requireNotNull(snapshot.connectionProfile)
        val profile = requireNotNull(snapshot.translationProfile)
        val prompt = requireNotNull(snapshot.systemPromptSnapshot)
        require(profile.connectionId == connection.id && prompt.isNotBlank() && prompt.length <= 100_000)
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
        require(snapshot.translationProvider == provider.name)
        require(
            snapshot.geminiSourceLang == profile.languages.source &&
                snapshot.geminiTargetLang == profile.languages.target,
        )
        require(
            snapshot.geminiPromptMode == profile.prompt.modeId && snapshot.geminiStylePreset == profile.prompt.styleId,
        )
        val publicRequest = ProfileRequestBuilder.build(connection, profile.model, prompt, "")
        if (snapshot.geminiRequestShape != null) {
            require(connection.protocol == TranslationProtocol.GEMINI_GENERATE)
            require(
                snapshot.geminiRequestShape == GeminiProfileRequestShape.PUBLIC_LEGACY ||
                    connection.presetFlavor == PresetFlavor.GEMINI_PRIVATE,
            )
            require(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,199}").matches(snapshot.geminiModel.removePrefix("models/")))
            validateNativeGeminiGeneration(requireNotNull(snapshot.geminiGenerationConfig))
        } else {
            require(snapshot.geminiModel == profile.exactModelId)
            require(
                snapshot.geminiGenerationConfig == if (connection.protocol == TranslationProtocol.GEMINI_GENERATE) {
                    publicRequest.body["generationConfig"]
                } else {
                    null
                },
            )
        }
    }
    snapshot
} catch (_: Exception) {
    throw TranslationAttemptException(
        TranslationFailure(
            TranslationFailureKind.CONFIGURATION,
            "Stored translation profile is invalid or needs migration",
            attempted = false,
        ),
    )
}
