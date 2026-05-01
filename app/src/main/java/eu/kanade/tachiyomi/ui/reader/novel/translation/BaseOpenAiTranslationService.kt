package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.jsonMime
import kotlinx.coroutines.delay
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
    private val retryDelay: suspend (Long) -> Unit = { delay(it) },
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
        maxAttempts: Int = OPENAI_LIKE_MAX_ATTEMPTS,
    ): JsonObject? {
        if (baseUrl.isBlank() || apiKey.isBlank()) return null

        val attempts = maxAttempts.coerceAtLeast(1)
        for (attempt in 1..attempts) {
            when (
                val outcome = executeChatCompletionRequest(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    requestBody = requestBody,
                    extraHeaders = extraHeaders,
                    logLabel = logLabel,
                    attempt = attempt,
                )
            ) {
                is BaseOpenAiRequestOutcome.Success -> {
                    return runCatching { json.parseToJsonElement(outcome.body) as? JsonObject }
                        .getOrNull()
                        ?: run {
                            onLog?.invoke("$logLabel response is not valid JSON object")
                            onLog?.invoke("$logLabel response payload: ${outcome.body.take(1200)}")
                            null
                        }
                }
                is BaseOpenAiRequestOutcome.Retriable -> {
                    if (attempt == attempts) {
                        onLog?.invoke(
                            "$logLabel temporary API failure (attempt $attempt/$attempts): ${outcome.message}. " +
                                "No retries left",
                        )
                        return null
                    }
                    onLog?.invoke(
                        "$logLabel temporary API failure (attempt $attempt/$attempts): ${outcome.message}. " +
                            "Retrying in ${"%.1f".format(outcome.waitMs / 1000f)}s",
                    )
                    retryDelay(outcome.waitMs)
                }
                is BaseOpenAiRequestOutcome.Failure -> {
                    onLog?.invoke(outcome.message)
                    return null
                }
            }
        }

        return null
    }

    private suspend fun executeChatCompletionRequest(
        baseUrl: String,
        apiKey: String,
        requestBody: String,
        extraHeaders: Map<String, String>,
        logLabel: String,
        attempt: Int,
    ): BaseOpenAiRequestOutcome {
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
                        if (it.code == 429 || it.code in 500..599) {
                            val hintSeconds = it.header("Retry-After")?.toDoubleOrNull()
                                ?: extractRetryAfterSeconds(rawBody)
                            return@withIOContext BaseOpenAiRequestOutcome.Retriable(
                                waitMs = computeOpenAiLikeRetryDelayMs(
                                    attempt = attempt,
                                    hintSeconds = hintSeconds,
                                ),
                                message = "$logLabel API error ${it.code}: $details",
                            )
                        }
                        return@withIOContext BaseOpenAiRequestOutcome.Failure(
                            "$logLabel API error ${it.code}: $details",
                        )
                    }
                    BaseOpenAiRequestOutcome.Success(rawBody)
                }
            }
        }.getOrElse { error ->
            BaseOpenAiRequestOutcome.Failure(
                "$logLabel request exception: ${formatAiTranslationThrowableForLog(error)}",
            )
        }
    }

    protected fun JsonObject.extractAssistantContent(): String {
        val choice = this["choices"]
            .asArrayOrNull()
            ?.firstOrNull()
            ?.asObjectOrNull()
            ?: return ""

        val message = choice["message"].asObjectOrNull()
        message?.get("content")
            .extractContentArrayTextCandidates()
            .firstOrNull()
            ?.let { return it }

        val sources =
            listOf(
                message?.get("content"),
                message?.get("text"),
                choice["text"],
                choice["output_text"],
                choice["content"],
            )
        return sources.firstNotNullOfOrNull {
            it.extractTextCandidates(includeThinking = false).firstOrNull()
        }.orEmpty()
    }

    private fun JsonElement?.extractContentArrayTextCandidates(): List<String> {
        val array = this as? JsonArray ?: return emptyList()
        return array
            .flatMap { entry ->
                val obj = entry as? JsonObject ?: return@flatMap entry.extractTextCandidates(includeThinking = false)
                if (obj["type"].asStringOrNull()?.equals("thinking", ignoreCase = true) == true) {
                    emptyList()
                } else {
                    obj["text"].extractTextCandidates(includeThinking = false)
                }
            }
            .distinct()
    }

    private fun JsonElement?.extractTextCandidates(includeThinking: Boolean): List<String> {
        return when (this) {
            is JsonPrimitive -> {
                if (isString) {
                    content.trim().takeIf { it.isNotBlank() }?.let(::listOf).orEmpty()
                } else {
                    emptyList()
                }
            }
            is JsonArray -> flatMap { it.extractTextCandidates(includeThinking = includeThinking) }
            is JsonObject -> {
                val isThinking = this["type"].asStringOrNull()?.equals("thinking", ignoreCase = true) == true
                if (isThinking && !includeThinking) return emptyList()
                val direct = listOf("text", "content", "output_text")
                    .flatMap { key -> this[key].extractTextCandidates(includeThinking = includeThinking) }
                val functionArgs = this["function"].asObjectOrNull()?.get("arguments")
                    .extractTextCandidates(includeThinking = includeThinking)
                (direct + functionArgs).distinct()
            }
            else -> emptyList()
        }
    }
}

private sealed interface BaseOpenAiRequestOutcome {
    data class Success(val body: String) : BaseOpenAiRequestOutcome
    data class Retriable(val waitMs: Long, val message: String) : BaseOpenAiRequestOutcome
    data class Failure(val message: String) : BaseOpenAiRequestOutcome
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

private val retryAfterSecondsRegex =
    Regex("(?i)try\\s+again\\s+in\\s+([0-9]+(?:\\.[0-9]+)?)\\s*seconds?")

private fun extractRetryAfterSeconds(raw: String): Double? {
    val match = retryAfterSecondsRegex.find(raw) ?: return null
    return match.groupValues.getOrNull(1)?.toDoubleOrNull()
}

private fun computeOpenAiLikeRetryDelayMs(
    attempt: Int,
    hintSeconds: Double?,
): Long {
    if (hintSeconds != null) {
        val ms = ((hintSeconds + 0.3) * 1000.0).toLong()
        return ms.coerceIn(1_200L, 120_000L)
    }
    return when (attempt) {
        1 -> 2_000L
        2 -> 5_000L
        3 -> 15_000L
        else -> 60_000L
    }
}

private const val OPENAI_LIKE_MAX_ATTEMPTS = 3
