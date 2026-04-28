package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.jsonMime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext

open class BaseOpenAiTranslationService(
    private val client: OkHttpClient,
    private val json: Json,
) {
    protected fun buildMessages(
        systemPrompt: String,
        userPrompt: String,
    ): JsonArray {
        return buildJsonArray {
            add(
                buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                },
            )
            add(
                buildJsonObject {
                    put("role", "user")
                    put("content", userPrompt)
                },
            )
        }
    }

    protected suspend fun executeChatCompletion(
        baseUrl: String,
        apiKey: String,
        requestBody: String,
        extraHeaders: Map<String, String> = emptyMap(),
        logLabel: String,
        onLog: ((String) -> Unit)? = null,
    ): JsonObject? {
        if (baseUrl.isBlank() || apiKey.isBlank()) return null
        return runCatching {
            withIOContext {
                val request = POST(
                    url = "$baseUrl/chat/completions",
                    headers = headersOf(
                        "Content-Type",
                        "application/json",
                        "Authorization",
                        "Bearer $apiKey",
                        *extraHeaders.flatMap { listOf(it.key, it.value) }.toTypedArray(),
                    ),
                    body = requestBody.toRequestBody(jsonMime),
                )
                val response = client.newCall(request).await()
                response.use {
                    val rawBody = it.body.string()
                    if (!it.isSuccessful) {
                        val details = extractOpenAiApiErrorMessage(rawBody) ?: rawBody.take(1200)
                        onLog?.invoke("$logLabel API error ${it.code}: $details")
                        return@withIOContext null
                    }
                    runCatching { json.parseToJsonElement(rawBody) as? JsonObject }
                        .getOrNull()
                        ?: run {
                            onLog?.invoke("$logLabel response is not valid JSON object")
                            onLog?.invoke("$logLabel response payload: ${rawBody.take(1200)}")
                            null
                        }
                }
            }
        }.onFailure { error ->
            onLog?.invoke("$logLabel request exception: ${formatAiTranslationThrowableForLog(error)}")
        }.getOrNull()
    }

    protected fun JsonObject.extractAssistantContent(): String {
        val choice = this["choices"]
            .asArrayOrNull()
            ?.firstOrNull()
            ?.asObjectOrNull()
            ?: return ""

        val message = choice["message"].asObjectOrNull()
        val sources = listOf(
            message?.get("content"),
            message?.get("text"),
            choice["text"],
            choice["output_text"],
            choice["content"],
        )
        return sources.firstNotNullOfOrNull { it.extractTextCandidates().firstOrNull() }.orEmpty()
    }

    private fun JsonElement?.extractTextCandidates(): List<String> {
        return when (this) {
            is JsonPrimitive -> {
                if (isString) {
                    content.trim().takeIf { it.isNotBlank() }?.let(::listOf).orEmpty()
                } else {
                    emptyList()
                }
            }
            is JsonArray -> flatMap { it.extractTextCandidates() }
            is JsonObject -> {
                val direct = listOf("text", "content", "output_text")
                    .flatMap { key -> this[key].extractTextCandidates() }
                val functionArgs = this["function"].asObjectOrNull()?.get("arguments").extractTextCandidates()
                (direct + functionArgs).distinct()
            }
            else -> emptyList()
        }
    }
}

private fun kotlinx.serialization.json.JsonElement?.asObjectOrNull(): JsonObject? {
    return this as? JsonObject
}

private fun kotlinx.serialization.json.JsonElement?.asArrayOrNull(): JsonArray? {
    return this as? JsonArray
}
