package eu.kanade.tachiyomi.ui.reader.novel.translation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal data class GoogleSelectedTextTranslationParsedPayload(
    val translation: String,
    val detectedSourceLanguage: String? = null,
)

internal object GoogleSelectedTextTranslationParser {

    fun parse(
        rawBody: String,
        json: Json,
    ): GoogleSelectedTextTranslationParsedPayload? {
        val payload = runCatching {
            json.parseToJsonElement(rawBody.trim())
        }.getOrNull() as? JsonArray ?: return null

        val translation = payload
            .firstOrNull()
            .asArrayOrNull()
            ?.flatMap { segment ->
                segment.asArrayOrNull()
                    ?.firstOrNull()
                    .asStringOrNull()
                    .let { text -> text?.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty() }
            }
            ?.joinToString(separator = "")
            ?.trim()
            .orEmpty()

        if (translation.isBlank()) return null

        val detectedSourceLanguage = payload
            .getOrNull(2)
            .asStringOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return GoogleSelectedTextTranslationParsedPayload(
            translation = translation,
            detectedSourceLanguage = detectedSourceLanguage,
        )
    }
}

private fun JsonElement?.asArrayOrNull(): JsonArray? {
    return this as? JsonArray
}

private fun JsonElement?.asObjectOrNull(): JsonObject? {
    return this as? JsonObject
}

private fun JsonElement?.asStringOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    return if (primitive.isString) primitive.content else null
}
