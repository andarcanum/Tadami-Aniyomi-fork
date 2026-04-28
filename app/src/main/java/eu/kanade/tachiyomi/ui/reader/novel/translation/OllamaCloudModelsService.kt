package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Headers
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.lang.withIOContext

class OllamaCloudModelsService(
    private val client: OkHttpClient,
    private val json: Json,
) {

    suspend fun fetchModels(baseUrl: String, apiKey: String): List<String> {
        val normalizedBaseUrl = normalizeOllamaCloudBaseUrl(baseUrl)
        if (normalizedBaseUrl.isBlank()) return emptyList()
        if (apiKey.isBlank()) return emptyList()

        val responseText = withIOContext {
            val request = GET(
                url = "$normalizedBaseUrl/tags",
                headers = ollamaCloudHeaders(apiKey = apiKey),
            )
            val response = client.newCall(request).await()
            response.use {
                if (!it.isSuccessful) return@withIOContext null
                it.body.string()
            }
        } ?: return emptyList()

        val payload = runCatching { json.parseToJsonElement(responseText) as? JsonObject }
            .getOrNull()
            ?: return emptyList()

        return payload["models"]
            .asArrayOrNull()
            .orEmpty()
            .mapNotNull { entry ->
                val obj = entry.asObjectOrNull() ?: return@mapNotNull null
                (obj["name"].asStringOrNull() ?: obj["model"].asStringOrNull())
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
            .distinct()
            .sorted()
    }
}

internal const val OLLAMA_CLOUD_DEFAULT_BASE_URL = "https://ollama.com/api"
internal const val OLLAMA_CLOUD_DEFAULT_MODEL = "gpt-oss:120b"

internal fun normalizeOllamaCloudBaseUrl(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    if (trimmed.isBlank()) return OLLAMA_CLOUD_DEFAULT_BASE_URL
    val lower = trimmed.lowercase()
    return when {
        lower.endsWith("/api/chat") -> trimmed.dropLast("/chat".length)
        lower.endsWith("/api/tags") -> trimmed.dropLast("/tags".length)
        lower.endsWith("/api") -> trimmed
        lower.endsWith("/api/v1") -> trimmed.dropLast("/v1".length)
        lower.endsWith("/chat") -> trimmed.dropLast("/chat".length).trimEnd('/').let { base ->
            if (base.lowercase().endsWith("/api")) base else "$base/api"
        }
        lower.endsWith("/tags") -> trimmed.dropLast("/tags".length).trimEnd('/').let { base ->
            if (base.lowercase().endsWith("/api")) base else "$base/api"
        }
        lower.endsWith("/v1") -> trimmed.dropLast("/v1".length).trimEnd('/') + "/api"
        else -> "$trimmed/api"
    }
}

internal fun ollamaCloudHeaders(
    apiKey: String,
    contentTypeJson: Boolean = false,
): Headers {
    val pairs = mutableListOf<String>()
    if (contentTypeJson) {
        pairs += "Content-Type"
        pairs += "application/json"
    }
    val normalizedApiKey = apiKey.trim()
    if (normalizedApiKey.isNotBlank()) {
        pairs += "Authorization"
        pairs += "Bearer $normalizedApiKey"
    }
    return headersOf(*pairs.toTypedArray())
}

private fun kotlinx.serialization.json.JsonElement?.asObjectOrNull(): JsonObject? {
    return this as? JsonObject
}

private fun kotlinx.serialization.json.JsonElement?.asArrayOrNull(): JsonArray? {
    return this as? JsonArray
}

private fun kotlinx.serialization.json.JsonElement?.asStringOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    return if (primitive.isString) primitive.content else null
}
