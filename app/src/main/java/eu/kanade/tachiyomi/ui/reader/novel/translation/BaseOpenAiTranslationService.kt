package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.jsonMime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

/** OpenAI request plumbing only; retry and chunk validity belong to the shared executor. */
internal fun normalizeOpenAiBaseUrlRoot(
    baseUrl: String,
    defaultSuffix: String? = null,
): String {
    var current = baseUrl.trim().trimEnd('/')
    if (current.isBlank()) return ""
    var strippedOperation = false
    while (true) {
        val lower = current.lowercase()
        val stripped = when {
            lower.endsWith("/chat/completions") -> current.dropLast("/chat/completions".length)
            lower.endsWith("/models") -> current.dropLast("/models".length)
            else -> break
        }
        strippedOperation = true
        current = stripped.trimEnd('/')
        if (current.isBlank()) return ""
    }
    if (strippedOperation || defaultSuffix.isNullOrBlank()) return current
    val lower = current.lowercase()
    return when (defaultSuffix) {
        "/api/v1" -> when {
            lower.endsWith("/api/v1") || lower.endsWith("/v1") -> current
            lower.endsWith("/api") -> "$current/v1"
            else -> "$current/api/v1"
        }
        else -> if (lower.endsWith(defaultSuffix.lowercase())) current else "$current$defaultSuffix"
    }
}

open class BaseOpenAiTranslationService(client: OkHttpClient, private val json: Json) {
    private val transport = TranslationHttpTransport(client, json)

    protected fun buildMessages(systemPrompt: String, userPrompt: String): JsonArray = buildJsonArray {
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

    protected suspend fun executeChatCompletion(
        baseUrl: String,
        apiKey: String,
        requestBody: String,
        extraHeaders: Map<String, String> = emptyMap(),
        logLabel: String,
        onLog: ((String) -> Unit)? = null,
    ): JsonObject? {
        val normalizedBaseUrl = normalizeOpenAiBaseUrlRoot(baseUrl)
        if (normalizedBaseUrl.isBlank() || apiKey.isBlank()) return null
        validateTranslationCredential(apiKey)
        val request = POST(
            url = validatedTranslationUrl("$normalizedBaseUrl/chat/completions"),
            headers = headersOf(
                "Content-Type",
                "application/json",
                "Authorization",
                "Bearer $apiKey",
                *extraHeaders.flatMap { listOf(it.key, it.value) }.toTypedArray(),
            ),
            body = requestBody.toRequestBody(jsonMime),
        )
        onLog?.invoke("$logLabel request started")
        return parseTranslationPayload(transport.execute(request, logLabel, apiKey), json, logLabel, secret = apiKey)
    }

    protected fun JsonObject.extractAssistantContent(): String =
        this["choices"].asArrayOrNull()?.firstOrNull().asObjectOrNull()?.extractOpenAiStyleChoiceContent().orEmpty()
}
