package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.lang.withIOContext
import java.io.IOException

/** Default request ceiling, not a minimum capability required for model discovery. */
internal const val GEMINI_TRANSLATION_MAX_OUTPUT_TOKENS = 16384

/** A text-generation model on the Gemini API, with optional discovery metadata. */
@Serializable
data class GeminiModelEntry(
    val id: String,
    val displayName: String,
    val inputTokenLimit: Int? = null,
    val outputTokenLimit: Int? = null,
    val temperature: Float? = null,
    val maxTemperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val thinking: Boolean? = null,
    val supportedGenerationMethods: List<String> = emptyList(),
)

/** Loads and validates the complete catalogue before replacing any last-known-good descriptors. */
class GeminiModelsService(
    client: OkHttpClient,
    private val json: Json,
) {
    // The inherited debug logger prints headers. Do not mutate that shared interceptor, and do
    // not forward the API key to a redirect target or reuse another credential's cached response.
    private val client = client.newBuilder()
        .cache(null)
        .followRedirects(false)
        .followSslRedirects(false)
        .apply {
            interceptors().removeAll { it.javaClass.name == GEMINI_HTTP_LOGGING_INTERCEPTOR_CLASS }
            networkInterceptors().removeAll { it.javaClass.name == GEMINI_HTTP_LOGGING_INTERCEPTOR_CLASS }
        }
        .build()

    suspend fun fetchModels(
        apiKey: String,
        baseUrl: String = GEMINI_MODELS_BASE_URL,
    ): List<GeminiModelEntry> {
        if (apiKey.isBlank()) return emptyList()

        val base = baseUrl.trim().toHttpUrlOrNull() ?: throw IOException("Invalid model catalogue endpoint")
        if (base.username.isNotEmpty() || base.password.isNotEmpty() || base.query != null || base.fragment != null) {
            throw IOException("Invalid model catalogue endpoint")
        }
        val headers = try {
            Headers.Builder().set("x-goog-api-key", apiKey.trim()).build()
        } catch (_: IllegalArgumentException) {
            // Invalid header exceptions may include their value; keep credentials out of diagnostics.
            throw IOException("Invalid model catalogue credential")
        }
        val normalizedRoot = normalizeGeminiRootHttpUrl(base)
        val modelsUrl = normalizedRoot.newBuilder()
            .addPathSegment("models")
            .addQueryParameter("pageSize", GEMINI_MODELS_PAGE_SIZE.toString())
            .build()

        return withIOContext {
            val entries = mutableListOf<GeminiModelEntry>()
            val seenTokens = mutableSetOf<String>()
            var pageToken: String? = null
            while (true) {
                currentCoroutineContext().ensureActive()
                val pageUrl = modelsUrl.newBuilder().apply {
                    pageToken?.let { addQueryParameter("pageToken", it) }
                }.build()
                val responseText = client.newCall(
                    GET(pageUrl, headers, CacheControl.FORCE_NETWORK),
                ).await().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    response.body.string()
                }
                val payload = try {
                    json.parseToJsonElement(responseText) as? JsonObject
                } catch (_: SerializationException) {
                    null
                } ?: throw IOException("Invalid model catalogue response")
                entries += extractGeminiModelEntries(payload)
                val nextToken = payload.optionalString("nextPageToken")?.takeIf { it.isNotEmpty() } ?: break
                if (!seenTokens.add(nextToken)) throw IOException("Repeated model catalogue page token")
                pageToken = nextToken
            }
            currentCoroutineContext().ensureActive()
            val completeEntries = entries.distinctBy { it.id }.sortedBy { it.id }
            if (normalizedRoot.isPublicGoogleModelsOrigin()) {
                GeminiModelMetadataStore.recordCompletePublicCatalogue(completeEntries)
            }
            completeEntries
        }
    }
}

private const val GEMINI_MODELS_BASE_URL = "https://generativelanguage.googleapis.com"
private const val GEMINI_MODELS_PAGE_SIZE = 1000
private const val GEMINI_HTTP_LOGGING_INTERCEPTOR_CLASS = "okhttp3.logging.HttpLoggingInterceptor"

internal fun normalizeGeminiBaseUrl(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    if (trimmed.isBlank()) return "$GEMINI_MODELS_BASE_URL/v1beta"
    val parsed = trimmed.toHttpUrlOrNull() ?: return trimmed
    return normalizeGeminiRootHttpUrl(parsed).toString()
}

private fun normalizeGeminiRootHttpUrl(parsed: HttpUrl): HttpUrl {
    val segments = parsed.pathSegments.filter { it.isNotEmpty() }.toMutableList()
    val lastLower = segments.lastOrNull()?.lowercase()
    if (lastLower != null && (lastLower.endsWith(":generatecontent") || lastLower.endsWith(":streamgeneratecontent"))) {
        segments.removeAt(segments.lastIndex)
        if (segments.lastOrNull()?.equals("models", ignoreCase = true) == true) {
            segments.removeAt(segments.lastIndex)
        }
    } else if (lastLower == "models") {
        segments.removeAt(segments.lastIndex)
    }
    if (segments.lastOrNull()?.lowercase() !in setOf("v1beta", "v1", "v1alpha")) {
        segments += "v1beta"
    }
    return parsed.newBuilder().encodedPath("/").apply {
        segments.forEach(::addPathSegment)
    }.build()
}

internal fun HttpUrl.isPublicGoogleModelsOrigin(): Boolean =
    scheme == "https" &&
        host == "generativelanguage.googleapis.com" &&
        port == 443 &&
        encodedPath.trimEnd('/').lowercase() in setOf("", "/v1beta", "/v1", "/v1alpha")

internal fun extractGeminiModelEntries(payload: JsonObject?): List<GeminiModelEntry> {
    val models = payload?.get("models") as? JsonArray ?: throw IOException("Invalid model catalogue response")
    return models.mapNotNull { element ->
        val model = element as? JsonObject ?: throw IOException("Invalid model catalogue entry")
        val id = model.optionalString("name")
            ?.trim()
            ?.removePrefix("models/")
            ?.takeIf { it.isNotEmpty() }
            ?: throw IOException("Invalid model catalogue entry")
        if (!isSupportedGeminiTextModelId(id)) return@mapNotNull null
        val methodsValue = model["supportedGenerationMethods"]
        val generationMethods = when (methodsValue) {
            null, JsonNull -> emptyList()
            is JsonArray -> methodsValue.map { method ->
                (method as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?: throw IOException("Invalid model catalogue generation methods")
            }
            else -> throw IOException("Invalid model catalogue generation methods")
        }
        if ("generateContent" !in generationMethods) return@mapNotNull null
        GeminiModelEntry(
            id = id,
            displayName = model.optionalString("displayName")?.takeIf { it.isNotBlank() } ?: id,
            inputTokenLimit = model.optionalInt("inputTokenLimit")?.requirePositiveTokenLimit(),
            outputTokenLimit = model.optionalInt("outputTokenLimit")?.requirePositiveTokenLimit(),
            temperature = model.optionalFloat("temperature"),
            maxTemperature = model.optionalFloat("maxTemperature"),
            topP = model.optionalFloat("topP"),
            topK = model.optionalInt("topK"),
            thinking = model.optionalBoolean("thinking"),
            supportedGenerationMethods = generationMethods,
        )
    }.distinctBy { it.id }.sortedBy { it.id }
}

private fun JsonObject.optionalPrimitive(field: String): JsonPrimitive? = when (val value = get(field)) {
    null, JsonNull -> null
    is JsonPrimitive -> value
    else -> throw IOException("Invalid model catalogue metadata")
}

private fun JsonObject.optionalString(field: String): String? = optionalPrimitive(field)?.let {
    it.takeIf { value -> value.isString }?.content ?: throw IOException("Invalid model catalogue metadata")
}

private fun JsonObject.optionalInt(field: String): Int? = optionalPrimitive(field)?.let {
    it.takeUnless { value -> value.isString }?.intOrNull ?: throw IOException("Invalid model catalogue metadata")
}

private fun JsonObject.optionalFloat(field: String): Float? = optionalPrimitive(field)?.let {
    it.takeUnless { value -> value.isString }?.floatOrNull?.takeIf { value -> value.isFinite() }
        ?: throw IOException("Invalid model catalogue metadata")
}

private fun JsonObject.optionalBoolean(field: String): Boolean? = optionalPrimitive(field)?.let {
    it.takeUnless { value -> value.isString }?.booleanOrNull ?: throw IOException("Invalid model catalogue metadata")
}

private fun Int.requirePositiveTokenLimit(): Int = takeIf { it > 0 }
    ?: throw IOException("Invalid model catalogue token limit")
