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

class AirforceTranslationService(
    private val client: OkHttpClient,
    private val json: Json,
    private val retryDelay: suspend (Long) -> Unit = { delay(it) },
) {

    suspend fun translateBatch(
        segments: List<String>,
        params: AirforceTranslationParams,
        onLog: ((String) -> Unit)? = null,
    ): List<String?>? {
        if (segments.isEmpty()) return emptyList()
        if (params.apiKey.isBlank()) return null
        if (params.model.isBlank()) return null

        val baseUrl = params.baseUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) return null

        val taggedInput = segments.mapIndexed { index, text ->
            "<s i='$index'>$text</s>"
        }.joinToString("\n")
        val systemPrompt = buildSystemPrompt(
            mode = params.promptMode,
            modifiers = params.promptModifiers,
        )
        val userPrompt = buildUserPrompt(
            sourceLang = params.sourceLang,
            targetLang = params.targetLang,
            taggedInput = taggedInput,
        )
        val requestBody = buildJsonObject {
            put("model", params.model)
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
            put("temperature", params.temperature)
            put("top_p", params.topP)
            // Some "thinking" models return empty final content unless completion budget is explicit.
            put("max_tokens", 4096)
        }

        for (attempt in 1..MAX_ATTEMPTS) {
            when (
                val response = executeChatCompletionRequest(
                    client = client,
                    baseUrl = baseUrl,
                    apiKey = params.apiKey,
                    requestBody = requestBody.withStreamFlag(stream = false).toString(),
                    attempt = attempt,
                )
            ) {
                is AirforceRequestOutcome.Failure -> {
                    onLog?.invoke(response.message)
                    return null
                }
                is AirforceRequestOutcome.RateLimited -> {
                    val waitMs = response.waitMs.coerceAtLeast(1_200L)
                    onLog?.invoke(
                        "Airforce rate limited (attempt $attempt/$MAX_ATTEMPTS): ${response.details}. " +
                            "Retrying in ${"%.1f".format(waitMs / 1000f)}s",
                    )
                    if (attempt == MAX_ATTEMPTS) {
                        return null
                    }
                    retryDelay(waitMs)
                }
                is AirforceRequestOutcome.Success -> {
                    val payload = runCatching { json.parseToJsonElement(response.body) as? JsonObject }
                        .getOrNull()
                        ?: run {
                            onLog?.invoke("Airforce response is not valid JSON object. Raw: ${response.body.take(600)}")
                            return null
                        }

                    payload.extractApiErrorMessage()?.let { apiError ->
                        onLog?.invoke(apiError)
                        onLog?.invoke("Airforce response payload: ${payload.toString().take(1200)}")
                        return null
                    }

                    val usage = payload["usage"].asObjectOrNull()
                    usage?.let { onLog?.invoke("Airforce usage: ${it.toString().take(240)}") }

                    val firstChoice = payload["choices"]
                        .asArrayOrNull()
                        ?.firstOrNull()
                        ?.asObjectOrNull()
                        ?: run {
                            onLog?.invoke("Airforce response has no choices. Payload: ${payload.toString().take(600)}")
                            return null
                        }

                    val extracted = firstChoice.extractAssistantContent()
                    var candidateText = extracted.text.trim()
                    var usedStreamFallback = false

                    if (looksLikeAirforceRateLimitBanner(candidateText)) {
                        val waitMs = computeRateLimitDelayMs(
                            attempt = attempt,
                            hintSeconds = extractRetryAfterSeconds(candidateText),
                        )
                        onLog?.invoke(
                            "Airforce returned rate-limit banner in content (attempt $attempt/$MAX_ATTEMPTS). " +
                                "Retrying in ${"%.1f".format(waitMs / 1000f)}s",
                        )
                        if (attempt == MAX_ATTEMPTS) {
                            onLog?.invoke("Airforce response payload: ${payload.toString().take(1200)}")
                            return null
                        }
                        retryDelay(waitMs)
                        continue
                    }

                    if (candidateText.isBlank()) {
                        val finishReason = firstChoice["finish_reason"].asStringOrNull()
                        if (!finishReason.isNullOrBlank()) {
                            onLog?.invoke("Airforce empty candidate, finish_reason=$finishReason")
                        } else {
                            onLog?.invoke("Airforce returned empty message content")
                        }
                        val completionTokens = usage?.get("completion_tokens").asLongOrNull()
                        if (completionTokens != null) {
                            onLog?.invoke("Airforce completion_tokens=$completionTokens")
                        }
                        onLog?.invoke("Airforce choice payload: ${firstChoice.toString().take(1200)}")

                        when (
                            val streamFallback = requestStreamFallbackCandidate(
                                baseUrl = baseUrl,
                                apiKey = params.apiKey,
                                requestBody = requestBody,
                                attempt = attempt,
                            )
                        ) {
                            is StreamFallbackOutcome.Success -> {
                                candidateText = streamFallback.text.trim()
                                if (candidateText.isNotBlank()) {
                                    usedStreamFallback = true
                                    onLog?.invoke("Airforce content fallback used: stream.delta")
                                }
                            }
                            is StreamFallbackOutcome.RateLimited -> {
                                val waitMs = streamFallback.waitMs.coerceAtLeast(1_200L)
                                onLog?.invoke(
                                    "Airforce rate limited " +
                                        "(stream fallback attempt $attempt/$MAX_ATTEMPTS): " +
                                        "${streamFallback.details}. " +
                                        "Retrying in ${"%.1f".format(waitMs / 1000f)}s",
                                )
                                if (attempt == MAX_ATTEMPTS) {
                                    return null
                                }
                                retryDelay(waitMs)
                                continue
                            }
                            is StreamFallbackOutcome.Failure -> {
                                onLog?.invoke(streamFallback.message)
                                onLog?.invoke("Airforce response payload: ${payload.toString().take(1200)}")
                                return null
                            }
                        }
                    }

                    if (candidateText.isBlank()) {
                        onLog?.invoke("Airforce response payload: ${payload.toString().take(1200)}")
                        return null
                    }

                    if (looksLikeAirforceRateLimitBanner(candidateText)) {
                        val waitMs = computeRateLimitDelayMs(
                            attempt = attempt,
                            hintSeconds = extractRetryAfterSeconds(candidateText),
                        )
                        onLog?.invoke(
                            "Airforce returned rate-limit banner after fallback (attempt $attempt/$MAX_ATTEMPTS). " +
                                "Retrying in ${"%.1f".format(waitMs / 1000f)}s",
                        )
                        if (attempt == MAX_ATTEMPTS) {
                            onLog?.invoke("Airforce response payload: ${payload.toString().take(1200)}")
                            return null
                        }
                        retryDelay(waitMs)
                        continue
                    }

                    if (!usedStreamFallback && extracted.source != "message.content") {
                        onLog?.invoke("Airforce content fallback used: ${extracted.source}")
                    }

                    val sanitizedCandidate = candidateText.trimNonXmlTail()
                    if (sanitizedCandidate != candidateText) {
                        onLog?.invoke("Airforce trimmed non-XML tail from response")
                    }
                    val parsed = GeminiXmlSegmentParser.parse(sanitizedCandidate, expectedCount = segments.size)
                    if (parsed.all { it.isNullOrBlank() }) {
                        onLog?.invoke("Airforce parse warning: no XML segments found in message")
                        onLog?.invoke("Airforce content preview: ${sanitizedCandidate.take(600)}")
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
    ): String {
        val basePrompt = when (mode) {
            GeminiPromptMode.CLASSIC -> GeminiPromptResolver.CLASSIC_SYSTEM_PROMPT
            GeminiPromptMode.ADULT_18 -> GeminiPromptResolver.CLASSIC_SYSTEM_PROMPT
        }
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
    ): String {
        return "TRANSLATE from $sourceLang to $targetLang.\n" +
            "Inject soul into the text. Make the reader believe this was written by a Russian author.\n\n" +
            "Use popular genre terminology (Magic -> Магия, etc.). Make it sound like high-quality fiction.\n\n" +
            "1. Keep the XML structure exactly as is (<s i='...'>...</s>).\n" +
            "2. NO PREAMBLE. NO ANALYSIS TEXT. NO MARKDOWN HEADERS.\n" +
            "3. Start your response IMMEDIATELY with the first XML tag.\n\n" +
            "INPUT BLOCK:\n" +
            taggedInput
    }

    private suspend fun requestStreamFallbackCandidate(
        baseUrl: String,
        apiKey: String,
        requestBody: JsonObject,
        attempt: Int,
    ): StreamFallbackOutcome {
        return when (
            val response = executeChatCompletionRequest(
                client = client,
                baseUrl = baseUrl,
                apiKey = apiKey,
                requestBody = requestBody.withStreamFlag(stream = true).toString(),
                attempt = attempt,
            )
        ) {
            is AirforceRequestOutcome.Failure -> {
                StreamFallbackOutcome.Failure("Airforce stream fallback failed: ${response.message}")
            }
            is AirforceRequestOutcome.RateLimited -> {
                StreamFallbackOutcome.RateLimited(waitMs = response.waitMs, details = response.details)
            }
            is AirforceRequestOutcome.Success -> {
                val extracted = extractStreamAssistantText(rawBody = response.body, json = json)
                extracted.apiError?.let { return StreamFallbackOutcome.Failure(it) }
                StreamFallbackOutcome.Success(text = extracted.text)
            }
        }
    }
}

private sealed interface AirforceRequestOutcome {
    data class Success(val body: String) : AirforceRequestOutcome
    data class RateLimited(val waitMs: Long, val details: String) : AirforceRequestOutcome
    data class Failure(val message: String) : AirforceRequestOutcome
}

private sealed interface StreamFallbackOutcome {
    data class Success(val text: String) : StreamFallbackOutcome
    data class RateLimited(val waitMs: Long, val details: String) : StreamFallbackOutcome
    data class Failure(val message: String) : StreamFallbackOutcome
}

private data class StreamAssistantText(
    val text: String,
    val apiError: String? = null,
)

private suspend fun executeChatCompletionRequest(
    client: OkHttpClient,
    baseUrl: String,
    apiKey: String,
    requestBody: String,
    attempt: Int,
): AirforceRequestOutcome {
    return runCatching {
        withIOContext {
            val request = POST(
                url = "$baseUrl/v1/chat/completions",
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
                        return@withIOContext AirforceRequestOutcome.RateLimited(
                            waitMs = computeRateLimitDelayMs(attempt = attempt, hintSeconds = hintSeconds),
                            details = details,
                        )
                    }
                    return@withIOContext AirforceRequestOutcome.Failure("Airforce API error ${it.code}: $details")
                }
                AirforceRequestOutcome.Success(rawBody)
            }
        }
    }.getOrElse { error ->
        AirforceRequestOutcome.Failure("Airforce request exception: ${formatGeminiThrowableForLog(error)}")
    }
}

private data class ExtractedContent(
    val text: String,
    val source: String,
)

private fun JsonObject.extractAssistantContent(): ExtractedContent {
    val message = this["message"].asObjectOrNull()
    val delta = this["delta"].asObjectOrNull()

    val sources = listOf(
        "message.content" to message?.get("content").extractTextCandidates(),
        "message.text" to message?.get("text").extractTextCandidates(),
        "choice.delta.content" to delta?.get("content").extractTextCandidates(),
        "choice.delta.text" to delta?.get("text").extractTextCandidates(),
        "choice.text" to this["text"].extractTextCandidates(),
        "choice.output_text" to this["output_text"].extractTextCandidates(),
        "choice.content" to this["content"].extractTextCandidates(),
        "message.reasoning_content" to message?.get("reasoning_content").extractTextCandidates(),
        "message.reasoning" to message?.get("reasoning").extractTextCandidates(),
        "choice.delta.reasoning_content" to delta?.get("reasoning_content").extractTextCandidates(),
        "choice.delta.reasoning" to delta?.get("reasoning").extractTextCandidates(),
        "message.tool_calls" to message?.get("tool_calls").extractTextCandidates(),
    )

    for ((source, candidates) in sources) {
        val best = candidates.pickPreferredAssistantText()
        if (best != null) {
            return ExtractedContent(text = best, source = source)
        }
    }

    return ExtractedContent(text = "", source = "none")
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
        is JsonArray -> {
            flatMap { it.extractTextCandidates() }
        }
        is JsonObject -> {
            val keys = listOf("text", "content", "output_text", "reasoning_content", "reasoning", "arguments")
            val direct = keys.flatMap { key -> this[key].extractTextCandidates() }
            val functionArgs = this["function"].asObjectOrNull()?.get("arguments").extractTextCandidates()
            (direct + functionArgs).distinct()
        }
        else -> emptyList()
    }
}

private fun List<String>.pickPreferredAssistantText(): String? {
    if (isEmpty()) return null
    val cleaned = map { it.trim() }.filter { it.isNotBlank() }
    if (cleaned.isEmpty()) return null
    return cleaned.firstOrNull { it.contains("<s i='") || it.contains("<s i=\"") }
        ?: cleaned.firstOrNull()
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

private fun JsonElement?.asLongOrNull(): Long? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.content.toLongOrNull()
}

private fun JsonElement?.asLooseStringOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.content.takeIf { it.isNotBlank() }
}

private fun JsonObject.withStreamFlag(stream: Boolean): JsonObject {
    return buildJsonObject {
        this@withStreamFlag.forEach { (key, value) -> put(key, value) }
        put("stream", stream)
    }
}

private fun JsonObject.extractApiErrorMessage(): String? {
    val error = this["error"].asObjectOrNull() ?: return null
    return error.toAirforceApiErrorMessage()
}

private fun JsonObject.toAirforceApiErrorMessage(): String {
    val code = this["code"].asLooseStringOrNull()
    val message = this["message"].asLooseStringOrNull().orEmpty().ifBlank { this.toString() }
    return if (code.isNullOrBlank()) {
        "Airforce API error: $message"
    } else {
        "Airforce API error $code: $message"
    }
}

private fun extractStreamAssistantText(
    rawBody: String,
    json: Json,
): StreamAssistantText {
    val body = rawBody.trim()
    if (body.isBlank()) return StreamAssistantText(text = "")

    val parsedObject = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
    if (parsedObject != null) {
        parsedObject.extractApiErrorMessage()?.let { return StreamAssistantText(text = "", apiError = it) }
        val choice = parsedObject["choices"]
            .asArrayOrNull()
            ?.firstOrNull()
            ?.asObjectOrNull()
            ?: return StreamAssistantText(text = "")
        val extracted = choice.extractAssistantContent()
        return StreamAssistantText(text = extracted.text.trim())
    }

    val streamLines = body.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("data:") }
        .map { it.removePrefix("data:").trim() }
        .filter { it.isNotBlank() && it != "[DONE]" }
        .toList()

    if (streamLines.isEmpty()) return StreamAssistantText(text = "")

    val chunks = streamLines.mapNotNull { line ->
        runCatching { json.parseToJsonElement(line) as? JsonObject }.getOrNull()
    }

    chunks.firstNotNullOfOrNull { it.extractApiErrorMessage() }?.let { apiError ->
        return StreamAssistantText(text = "", apiError = apiError)
    }

    val builder = StringBuilder()
    var fallbackText = ""
    chunks.forEach { chunk ->
        val choice = chunk["choices"]
            .asArrayOrNull()
            ?.firstOrNull()
            ?.asObjectOrNull()
            ?: return@forEach

        val extracted = choice.extractAssistantContent()
        if (extracted.text.isBlank()) return@forEach

        if (extracted.source.startsWith("choice.delta.")) {
            builder.append(extracted.text)
        } else if (fallbackText.isBlank()) {
            fallbackText = extracted.text
        }
    }

    val streamed = builder.toString().trim()
    return StreamAssistantText(text = if (streamed.isNotBlank()) streamed else fallbackText.trim())
}

private const val MAX_ATTEMPTS = 1

private val retryAfterSecondsRegex =
    Regex("(?i)try\\s+again\\s+in\\s+([0-9]+(?:\\.[0-9]+)?)\\s*seconds?")
private val airforceRateLimitBannerRegex =
    Regex("(?i)ratelimit\\s*exceeded|rate\\s*limit\\s*exceeded|discord\\.gg/airforce")
private val xmlSegmentStartRegex =
    Regex("(?i)<s\\s+i=['\"]\\d+['\"]>")
private val xmlSegmentEndRegex =
    Regex("(?i)</s>")

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
    val fallback = when (attempt) {
        1 -> 2_000L
        2 -> 5_000L
        3 -> 15_000L
        else -> 60_000L
    }
    return fallback
}

private fun looksLikeAirforceRateLimitBanner(text: String): Boolean {
    if (text.isBlank()) return false
    return airforceRateLimitBannerRegex.containsMatchIn(text)
}

private fun String.trimNonXmlTail(): String {
    val source = trim()
    val start = xmlSegmentStartRegex.find(source)?.range?.first ?: return source
    val end = xmlSegmentEndRegex.findAll(source).lastOrNull()?.range?.last ?: return source
    if (end < start) return source
    return source.substring(start, end + 1).trim()
}
