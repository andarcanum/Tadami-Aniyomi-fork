package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.lang.withIOContext

class AirforceModelsService(
    private val client: OkHttpClient,
    private val json: Json,
) {

    suspend fun fetchModels(
        baseUrl: String,
        apiKey: String,
    ): List<String> {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        if (normalizedBaseUrl.isBlank()) return emptyList()
        if (apiKey.isBlank()) return emptyList()

        val responseText = withIOContext {
            val response = client.newCall(
                GET(
                    url = "$normalizedBaseUrl/v1/models",
                    headers = headersOf("Authorization", "Bearer $apiKey"),
                ),
            ).await()
            response.use {
                if (!it.isSuccessful) return@withIOContext null
                it.body.string()
            }
        } ?: return emptyList()

        val payload = runCatching { json.parseToJsonElement(responseText) as? JsonObject }
            .getOrNull()
            ?: return emptyList()
        val data = payload["data"].asArrayOrNull().orEmpty()
        return data.mapNotNull { entry ->
            entry.asObjectOrNull()?.get("id").asStringOrNull()?.trim()?.takeIf { it.isNotBlank() }
        }.distinct()
            .sorted()
    }
}

private fun kotlinx.serialization.json.JsonElement?.asObjectOrNull(): JsonObject? {
    return this as? JsonObject
}

private fun kotlinx.serialization.json.JsonElement?.asArrayOrNull(): kotlinx.serialization.json.JsonArray? {
    return this as? kotlinx.serialization.json.JsonArray
}

private fun kotlinx.serialization.json.JsonElement?.asStringOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    return if (primitive.isString) primitive.content else null
}
