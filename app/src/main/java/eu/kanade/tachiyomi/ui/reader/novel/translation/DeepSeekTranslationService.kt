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
        if (segments.isEmpty()) {
            onLog?.invoke("DeepSeek translateBatch skipped: no segments")
            return emptyList()
        }
        if (params.apiKey.isBlank()) {
            onLog?.invoke("DeepSeek translateBatch skipped: API key is blank")
            return null
        }
        val model = params.model.trim()
        if (model.isBlank()) {
            onLog?.invoke("DeepSeek translateBatch skipped: model is blank")
            return null
        }

        val baseUrl = normalizeDeepSeekBaseUrl(params.baseUrl)
        if (baseUrl.isBlank()) {
            onLog?.invoke("DeepSeek translateBatch skipped: baseUrl is blank")
            return null
        }

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
        val reasoningEffort = params.reasoningEffort.trim().lowercase()
        val thinkingEnabled = reasoningEffort != "none"
        val effectiveReasoningEffort = if (thinkingEnabled) {
            if (reasoningEffort == "max") "max" else "high"
        } else {
            "none"
        }
        val maxTokens = computeTranslationMaxTokens(
            segments = segments,
            thinkingEnabled = thinkingEnabled,
        )
        val requestBody = buildJsonObject {
            put("model", model)
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
            put(
                "thinking",
                buildJsonObject {
                    put("type", if (thinkingEnabled) "enabled" else "disabled")
                },
            )
            if (thinkingEnabled) {
                put("reasoning_effort", effectiveReasoningEffort)
            }
            put("temperature", params.temperature)
            put("top_p", params.topP)
            put("presence_penalty", params.presencePenalty)
            put("frequency_penalty", params.frequencyPenalty)
            put("max_tokens", maxTokens)
            put("stream", false)
        }
        val requestBodyText = requestBody.toString()
        val thinkingState = if (thinkingEnabled) "enabled" else "disabled"
        onLog?.invoke(
            "DeepSeek request: baseUrl=$baseUrl, model=$model, segments=${segments.size}, " +
                "lang=${params.sourceLang}->${params.targetLang}, prompt=${params.promptMode.name}, " +
                "reasoningEffort=$effectiveReasoningEffort, thinking=$thinkingState, " +
                "temp=${params.temperature}, topP=${params.topP}, " +
                "presencePenalty=${params.presencePenalty}, frequencyPenalty=${params.frequencyPenalty}, " +
                "maxTokens=$maxTokens",
        )
        val requestPreview = requestBodyText.take(1200)
        onLog?.invoke("DeepSeek request payload preview: $requestPreview")

        for (attempt in 1..MAX_ATTEMPTS) {
            onLog?.invoke("DeepSeek request attempt $attempt/$MAX_ATTEMPTS")
            when (
                val outcome = executeRequest(
                    baseUrl = baseUrl,
                    apiKey = params.apiKey,
                    requestBody = requestBodyText,
                    attempt = attempt,
                )
            ) {
                is DeepSeekRequestOutcome.Failure -> {
                    onLog?.invoke(outcome.message)
                    return null
                }
                is DeepSeekRequestOutcome.RateLimited -> {
                    if (attempt == MAX_ATTEMPTS) {
                        onLog?.invoke(
                            "DeepSeek rate limited (attempt $attempt/$MAX_ATTEMPTS): ${outcome.details}. No retries left",
                        )
                        return null
                    }
                    onLog?.invoke(
                        "DeepSeek rate limited (attempt $attempt/$MAX_ATTEMPTS): ${outcome.details}. " +
                            "Retrying in ${"%.1f".format(outcome.waitMs / 1000f)}s",
                    )
                    retryDelay(outcome.waitMs)
                }
                is DeepSeekRequestOutcome.Success -> {
                    onLog?.invoke("DeepSeek response body size: ${outcome.body.length} chars")
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
                            if (finishReason.equals("length", ignoreCase = true)) {
                                val tokenLimitHintPrefix =
                                    "DeepSeek hit the token limit before final content;"
                                val tokenLimitHintSuffix =
                                    "try a larger max_tokens value or a smaller chunk size"
                                onLog?.invoke(
                                    "$tokenLimitHintPrefix $tokenLimitHintSuffix",
                                )
                            }
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
                        val parsedPlaintext = GeminiXmlSegmentParser.parsePlaintext(
                            rawResponse = candidateText,
                            expectedCount = segments.size,
                        )
                        if (parsedPlaintext.any { !it.isNullOrBlank() }) {
                            onLog?.invoke("DeepSeek parse warning: no XML segments found; used plaintext fallback")
                            return parsedPlaintext
                        }
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
            DeepSeekRequestOutcome.Failure("DeepSeek request exception: ${formatAiTranslationThrowableForLog(error)}")
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



private fun computeTranslationMaxTokens(
    segments: List<String>,
    thinkingEnabled: Boolean,
): Int {
    if (thinkingEnabled) {
        return 32_768
    }
    val estimated = segments.sumOf { (it.length / 2).coerceAtLeast(32) } + segments.size * 24
    return estimated.coerceIn(4_096, 8_192)
}

private const val MAX_ATTEMPTS = 3
