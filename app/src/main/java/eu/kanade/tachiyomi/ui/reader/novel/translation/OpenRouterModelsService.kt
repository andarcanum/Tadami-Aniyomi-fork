package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.lang.withIOContext
import java.io.IOException

class OpenRouterModelsService(
    client: OkHttpClient,
    private val json: Json,
) {
    private val client = client.translationSafeClient().newBuilder().cache(null).build()

    suspend fun fetchModels(
        baseUrl: String,
        apiKey: String,
    ): List<String> {
        val normalizedBaseUrl = normalizeOpenRouterBaseUrl(baseUrl)
        if (normalizedBaseUrl.isBlank()) return emptyList()
        if (apiKey.isBlank()) return emptyList()
        validateTranslationCredential(apiKey)

        val responseText = withIOContext {
            val response = client.newCall(
                GET(
                    url = "$normalizedBaseUrl/models",
                    headers = headersOf(
                        "Authorization",
                        "Bearer $apiKey",
                    ),
                ),
            ).await()
            response.use {
                if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                it.body.string()
            }
        }

        val payload = runCatching { json.parseToJsonElement(responseText) as? JsonObject }
            .getOrNull()
            ?: return emptyList()
        val data = payload["data"].asArrayOrNull().orEmpty()
        return data.mapNotNull { entry ->
            entry.asObjectOrNull()?.get("id").asStringOrNull()?.trim()?.takeIf { it.isNotBlank() }
        }.distinct()
            .sorted()
    }

    suspend fun fetchFreeModels(
        baseUrl: String,
        apiKey: String,
    ): List<String> {
        return fetchModels(baseUrl, apiKey)
            .filter { it.endsWith(":free", ignoreCase = true) }
    }
}

internal fun normalizeOpenRouterBaseUrl(baseUrl: String): String =
    normalizeOpenAiBaseUrlRoot(baseUrl, "/api/v1")
