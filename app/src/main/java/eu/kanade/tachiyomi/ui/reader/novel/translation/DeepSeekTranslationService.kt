package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
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

class DeepSeekTranslationService(
    private val client: OkHttpClient,
    private val json: Json,
    private val resolveSystemPrompt: (GeminiPromptMode, NovelTranslationPromptFamily) -> String,
    private val retryDelay: suspend (Long) -> Unit = { delay(it) },
) {

    suspend fun translateBatch(
        segments: List<String>,
        params: DeepSeekTranslationParams,
        onLog: ((String) -> Unit)? = null,
    ): List<String?>? {
        if (segments.isEmpty()) return emptyList()
        if (params.apiKey.isBlank()) return null
        if (params.model.isBlank()) return null

        val baseUrl = normalizeDeepSeekBaseUrl(params.baseUrl)
        if (baseUrl.isBlank()) return null

        val taggedInput = segments.mapIndexed { index, text ->
            "<s i='$index'>$text</s>"
        }.joinToString("\n")
        val promptFamily = resolveNovelTranslationPromptFamily(params.targetLang)
        val systemPrompt = buildSystemPrompt(
            mode = params.promptMode,
            modifiers = params.promptModifiers,
            family = promptFamily,
        )
        val userPrompt = buildUserPrompt(
            sourceLang = params.sourceLang,
            targetLang = params.targetLang,
            taggedInput = taggedInput,
            family = promptFamily,
        )
        val requestBody = buildJsonObject {
            put("model", params.model.trim())
            put(
                "messages",
                buildJsonArray {
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
                },
            )
            val reasoningEffort = params.reasoningEffort.trim().lowercase()
            val thinkingEnabled = reasoningEffort != "none"
            put(
                "thinking",
                buildJsonObject {
                    put("type", if (thinkingEnabled) "enabled" else "disabled")
                },
            )
            if (thinkingEnabled) {
                put("reasoning_effort", if (reasoningEffort == "max") "max" else "high")
            }
            put("temperature", params.temperature)
            put("top_p", params.topP)
            put("presence_penalty", params.presencePenalty)
            put("frequency_penalty", params.frequencyPenalty)
            put("max_tokens", 4096)
            put("stream", false)
        }

        for (attempt in 1..MAX_ATTEMPTS) {
            when (
                val outcome = executeRequest(
                    baseUrl = baseUrl,
                    apiKey = params.apiKey,
                    requestBody = requestBody.toString(),
                    attempt = attempt,
                )
            ) {
                is DeepSeekRequestOutcome.Failure -> {
                    onLog?.invoke(outcome.message)
                    return null
                }
                is DeepSeekRequestOutcome.RateLimited -> {
                    onLog?.invoke(
                        "DeepSeek rate limited (attempt $attempt/$MAX_ATTEMPTS): ${outcome.details}. " +
                            "Retrying in ${"%.1f".format(outcome.waitMs / 1000f)}s",
                    )
                    if (attempt == MAX_ATTEMPTS) return null
                    retryDelay(outcome.waitMs)
                }
                is DeepSeekRequestOutcome.Success -> {
                    val payload = runCatching { json.parseToJsonElement(outcome.body) as? JsonObject }
                        .getOrNull()
                        ?: run {
                            onLog?.invoke("DeepSeek response is not valid JSON object")
                            onLog?.invoke("DeepSeek response payload: ${outcome.body.take(1200)}")
                            return null
                        }

                    val apiError = payload.extractApiErrorMessage()
                    if (apiError != null) {
                        onLog?.invoke(apiError)
                        onLog?.invoke("DeepSeek response payload: ${payload.toString().take(1200)}")
                        return null
                    }

                    val choice = payload["choices"]
                        .asArrayOrNull()
                        ?.firstOrNull()
                        ?.asObjectOrNull()
                        ?: run {
                            onLog?.invoke("DeepSeek response has no choices")
                            onLog?.invoke("DeepSeek response payload: ${payload.toString().take(1200)}")
                            return null
                        }

                    val candidateText = choice.extractAssistantContent().trim()
                    if (candidateText.isBlank()) {
                        val finishReason = choice["finish_reason"].asStringOrNull()
                        if (!finishReason.isNullOrBlank()) {
                            onLog?.invoke("DeepSeek empty candidate, finish_reason=$finishReason")
                        } else {
                            onLog?.invoke("DeepSeek returned empty message content")
                        }
                        onLog?.invoke("DeepSeek choice payload: ${choice.toString().take(1200)}")
                        return null
                    }

                    val sanitizedCandidate = candidateText.trimNonXmlTail()
                    if (sanitizedCandidate != candidateText) {
                        onLog?.invoke("DeepSeek trimmed non-XML tail from response")
                    }
                    val parsed = GeminiXmlSegmentParser.parse(sanitizedCandidate, expectedCount = segments.size)
                    if (parsed.all { it.isNullOrBlank() }) {
                        onLog?.invoke("DeepSeek parse warning: no XML segments found in message")
                        onLog?.invoke("DeepSeek content preview: ${sanitizedCandidate.take(600)}")
                        return null
                    }
                    return parsed
                }
            }
        }

        return null
    }

    private fun buildSystemPrompt(
        mode: GeminiPromptMode,
        modifiers: String,
        family: NovelTranslationPromptFamily,
    ): String {
        val basePrompt = resolveSystemPrompt(mode, family)
        return if (modifiers.isBlank()) {
            basePrompt
        } else {
            basePrompt + "\n\n" + modifiers.trim()
        }
    }

    private fun buildUserPrompt(
        sourceLang: String,
        targetLang: String,
        taggedInput: String,
        family: NovelTranslationPromptFamily,
    ): String {
        return buildNovelTranslationUserPrompt(
            sourceLang = sourceLang,
            targetLang = targetLang,
            taggedInput = taggedInput,
            family = family,
        )
        return "TRANSLATE from $sourceLang to $targetLang.\n" +
            "Inject soul into the text. Make the reader believe this was written by a Russian author.\n\n" +
            "Use popular genre terminology (Magic -> Магия, etc.). Make it sound like high-quality fiction.\n\n" +
            "1. Keep the XML structure exactly as is (<s i='...'>...</s>).\n" +
            "2. NO PREAMBLE. NO ANALYSIS TEXT. NO MARKDOWN HEADERS.\n" +
            "3. Start your response IMMEDIATELY with the first XML tag.\n\n" +
            "INPUT BLOCK:\n" +
            taggedInput
    }

    private suspend fun executeRequest(
        baseUrl: String,
        apiKey: String,
        requestBody: String,
        attempt: Int,
    ): DeepSeekRequestOutcome {
        return runCatching {
            withIOContext {
                val request = POST(
                    url = "$baseUrl/chat/completions",
                    headers = headersOf(
                        "Content-Type",
                        "application/json",
                        "Authorization",
                        "Bearer $apiKey",
                    ),
                    body = requestBody.toRequestBody(jsonMime),
                )
                val response = client.newCall(request).await()
                response.use {
                    val rawBody = it.body.string()
                    if (!it.isSuccessful) {
                        val details = extractOpenAiApiErrorMessage(rawBody) ?: rawBody.take(1200)
                        if (it.code == 429) {
                            val hintSeconds = extractRetryAfterSeconds(rawBody)
                                ?: it.header("Retry-After")?.toDoubleOrNull()
                            return@withIOContext DeepSeekRequestOutcome.RateLimited(
                                waitMs = computeRateLimitDelayMs(attempt = attempt, hintSeconds = hintSeconds),
                                details = details,
                            )
                        }
                        return@withIOContext DeepSeekRequestOutcome.Failure(
                            "DeepSeek API error ${it.code}: $details",
                        )
                    }
                    DeepSeekRequestOutcome.Success(rawBody)
                }
            }
        }.getOrElse { error ->
            DeepSeekRequestOutcome.Failure("DeepSeek request exception: ${formatGeminiThrowableForLog(error)}")
        }
    }
}

private sealed interface DeepSeekRequestOutcome {
    data class Success(val body: String) : DeepSeekRequestOutcome
    data class RateLimited(val waitMs: Long, val details: String) : DeepSeekRequestOutcome
    data class Failure(val message: String) : DeepSeekRequestOutcome
}

private fun normalizeDeepSeekBaseUrl(baseUrl: String): String {
    return baseUrl.trim().trimEnd('/')
}

private fun JsonObject.extractApiErrorMessage(): String? {
    val error = this["error"].asObjectOrNull() ?: return null
    val type = error["type"].asStringOrNull()
    val message = error["message"].asLooseStringOrNull().orEmpty().ifBlank { error.toString() }
    return if (type.isNullOrBlank()) {
        "DeepSeek API error: $message"
    } else {
        "DeepSeek API error $type: $message"
    }
}

private fun JsonObject.extractAssistantContent(): String {
    val message = this["message"].asObjectOrNull()
    val sources = listOf(
        message?.get("content"),
        message?.get("text"),
        this["text"],
        this["output_text"],
        this["content"],
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

private fun JsonElement?.asObjectOrNull(): JsonObject? {
    return this as? JsonObject
}

private fun JsonElement?.asArrayOrNull(): JsonArray? {
    return this as? JsonArray
}

private fun JsonElement?.asStringOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    return if (primitive.isString) primitive.content else null
}

private fun JsonElement?.asLooseStringOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.content.takeIf { it.isNotBlank() }
}

private val retryAfterSecondsRegex =
    Regex("(?i)try\\s+again\\s+in\\s+([0-9]+(?:\\.[0-9]+)?)\\s*seconds?")

private fun extractRetryAfterSeconds(raw: String): Double? {
    val match = retryAfterSecondsRegex.find(raw) ?: return null
    return match.groupValues.getOrNull(1)?.toDoubleOrNull()
}

private fun computeRateLimitDelayMs(
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

private val xmlSegmentStartRegex =
    Regex("(?i)<s\\s+i=['\"]\\d+['\"]>")
private val xmlSegmentEndRegex =
    Regex("(?i)</s>")

private fun String.trimNonXmlTail(): String {
    val source = trim()
    val start = xmlSegmentStartRegex.find(source)?.range?.first ?: return source
    val end = xmlSegmentEndRegex.findAll(source).lastOrNull()?.range?.last ?: return source
    if (end < start) return source
    return source.substring(start, end + 1).trim()
}

private const val MAX_ATTEMPTS = 1
