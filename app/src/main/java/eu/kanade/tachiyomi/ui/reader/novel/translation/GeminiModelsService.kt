package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.lang.withIOContext
import java.io.IOException

/** Output budget the Gemini translation request always asks for (see GeminiTranslationService). */
internal const val GEMINI_TRANSLATION_MAX_OUTPUT_TOKENS = 16384

/** One selectable Gemini model: API id (without the `models/` prefix) plus its UI label. */
data class GeminiModelEntry(
    val id: String,
    val displayName: String,
)

/**
 * Fetches the model catalogue available to the user's Gemini API key, mirroring the other
 * provider model services (OpenRouter, DeepSeek, ...). The picker in GeminiTranslationDialog
 * merges the result with its static fallback entries, so an empty list here is always safe.
 */
class GeminiModelsService(
    private val client: OkHttpClient,
    private val json: Json,
) {

    suspend fun fetchModels(
        apiKey: String,
        baseUrl: String = GEMINI_MODELS_BASE_URL,
    ): List<GeminiModelEntry> {
        if (apiKey.isBlank()) return emptyList()

        val responseText = withIOContext {
            val response = client.newCall(
                GET(
                    url = "$baseUrl/v1beta/models" +
                        "?key=$apiKey&pageSize=$GEMINI_MODELS_PAGE_SIZE",
                ),
            ).await()
            response.use {
                if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                it.body.string()
            }
        }

        val payload = runCatching { json.parseToJsonElement(responseText) as? JsonObject }
            .getOrNull()
        return extractGeminiModelEntries(payload)
    }
}

private const val GEMINI_MODELS_BASE_URL = "https://generativelanguage.googleapis.com"
private const val GEMINI_MODELS_PAGE_SIZE = 1000

/** Specialized Gemini variants that accept generateContent but cannot translate text chapters. */
private val GEMINI_MODEL_EXCLUDED_ID_SUBSTRINGS = listOf(
    "-tts",
    "-image",
    "native-audio",
    "computer-use",
    "embed",
)

internal fun extractGeminiModelEntries(payload: JsonObject?): List<GeminiModelEntry> {
    val models = payload?.get("models").asArrayOrNull().orEmpty()
    return models.mapNotNull { element ->
        val model = element.asObjectOrNull() ?: return@mapNotNull null
        val id = model.get("name")
            .asStringOrNull()
            ?.trim()
            ?.removePrefix("models/")
            ?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        val lowerId = id.lowercase()
        if (!lowerId.startsWith("gemini-")) return@mapNotNull null
        if (GEMINI_MODEL_EXCLUDED_ID_SUBSTRINGS.any { it in lowerId }) return@mapNotNull null
        val generationMethods = model.get("supportedGenerationMethods")
            .asArrayOrNull()
            .orEmpty()
            .mapNotNull { it.asStringOrNull() }
        if ("generateContent" !in generationMethods) return@mapNotNull null
        val outputTokenLimit = (model.get("outputTokenLimit") as? JsonPrimitive)?.intOrNull
        if (outputTokenLimit != null && outputTokenLimit < GEMINI_TRANSLATION_MAX_OUTPUT_TOKENS) {
            return@mapNotNull null
        }
        val displayName = model.get("displayName")
            .asStringOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: id
        GeminiModelEntry(id = id, displayName = displayName)
    }.distinctBy { it.id }
        .sortedBy { it.id }
}
