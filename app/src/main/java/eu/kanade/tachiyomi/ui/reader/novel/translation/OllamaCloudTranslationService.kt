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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext

class OllamaCloudTranslationService(
    private val client: OkHttpClient,
    private val json: Json,
    private val retryDelay: suspend (Long) -> Unit = { delay(it) },
) {

    suspend fun translateBatch(
        segments: List<String>,
        params: OllamaCloudTranslationParams,
        onLog: ((String) -> Unit)? = null,
    ): List<String?>? {
        if (segments.isEmpty()) return emptyList()
        if (params.apiKey.isBlank()) return null

        val model = params.model.trim()
        if (model.isBlank()) return null

        val baseUrl = normalizeOllamaCloudBaseUrl(params.baseUrl)
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
        val userPrompt = buildNovelTranslationUserPrompt(
            sourceLang = params.sourceLang,
            targetLang = params.targetLang,
            taggedInput = taggedInput,
            family = promptFamily,
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
            put("stream", false)
            when (val think = normalizeOllamaCloudThink(params.reasoningEffort)) {
                is OllamaCloudThink.Disabled -> put("think", false)
                is OllamaCloudThink.Level -> put("think", think.value)
            }
            put(
                "options",
                buildJsonObject {
                    put("temperature", params.temperature)
                    put("top_p", params.topP)
                    put("num_predict", computeOllamaTranslationNumPredict(segments))
                },
            )
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
                is OllamaCloudRequestOutcome.Failure -> {
                    onLog?.invoke(outcome.message)
                    return null
                }
                is OllamaCloudRequestOutcome.Fatal -> {
                    throw OllamaCloudFatalTranslationException(outcome.message)
                }
                is OllamaCloudRequestOutcome.Retriable -> {
                    if (attempt == MAX_ATTEMPTS) {
                        onLog?.invoke(
                            "Ollama Cloud temporary failure (attempt $attempt/$MAX_ATTEMPTS): " +
                                "${outcome.details}. No retries left",
                        )
                        return null
                    }
                    onLog?.invoke(
                        "Ollama Cloud temporary failure (attempt $attempt/$MAX_ATTEMPTS): " +
                            "${outcome.details}. Retrying in ${"%.1f".format(outcome.waitMs / 1000f)}s",
                    )
                    retryDelay(outcome.waitMs)
                }
                is OllamaCloudRequestOutcome.Success -> {
                    val payload = runCatching { json.parseToJsonElement(outcome.body) as? JsonObject }
                        .getOrNull()
                        ?: run {
                            onLog?.invoke("Ollama Cloud response is not valid JSON object")
                            onLog?.invoke("Ollama Cloud response payload: ${outcome.body.take(1200)}")
                            return null
                        }

                    val apiError = payload.extractOllamaCloudApiErrorMessage()
                    if (apiError != null) {
                        onLog?.invoke(apiError)
                        onLog?.invoke("Ollama Cloud response payload: ${payload.toString().take(1200)}")
                        return null
                    }

                    val candidateText = payload.extractOllamaCloudAssistantContent().trim()
                    if (candidateText.isBlank()) {
                        val doneReason = payload["done_reason"].asStringOrNull()
                        if (!doneReason.isNullOrBlank()) {
                            onLog?.invoke("Ollama Cloud empty message, done_reason=$doneReason")
                        } else {
                            onLog?.invoke("Ollama Cloud returned empty message content")
                        }
                        onLog?.invoke("Ollama Cloud payload: ${payload.toString().take(1200)}")
                        return null
                    }

                    val sanitizedCandidate = candidateText.trimNonXmlTail()
                    if (sanitizedCandidate != candidateText) {
                        onLog?.invoke("Ollama Cloud trimmed non-XML tail from response")
                    }
                    val parsed = GeminiXmlSegmentParser.parse(sanitizedCandidate, expectedCount = segments.size)
                    if (parsed.all { it.isNullOrBlank() }) {
                        val parsedPlaintext = GeminiXmlSegmentParser.parsePlaintext(
                            rawResponse = candidateText,
                            expectedCount = segments.size,
                        )
                        if (parsedPlaintext.any { !it.isNullOrBlank() }) {
                            onLog?.invoke("Ollama Cloud parse warning: no XML segments found; used plaintext fallback")
                            return parsedPlaintext
                        }
                        onLog?.invoke("Ollama Cloud parse warning: no XML segments found in message")
                        onLog?.invoke("Ollama Cloud content preview: ${sanitizedCandidate.take(600)}")
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
        val basePrompt = when (mode) {
            GeminiPromptMode.CLASSIC -> when (family) {
                NovelTranslationPromptFamily.RUSSIAN -> GeminiPromptResolver.CLASSIC_SYSTEM_PROMPT
                NovelTranslationPromptFamily.ENGLISH -> GeminiPromptResolver.CLASSIC_SYSTEM_PROMPT_EN
            }
            GeminiPromptMode.ADULT_18 -> when (family) {
                NovelTranslationPromptFamily.RUSSIAN -> GeminiPromptResolver.CLASSIC_SYSTEM_PROMPT
                NovelTranslationPromptFamily.ENGLISH -> GeminiPromptResolver.CLASSIC_SYSTEM_PROMPT_EN
            }
        }
        return if (modifiers.isBlank()) {
            basePrompt
        } else {
            basePrompt + "\n\n" + modifiers.trim()
        }
    }

    private suspend fun executeRequest(
        baseUrl: String,
        apiKey: String,
        requestBody: String,
        attempt: Int,
    ): OllamaCloudRequestOutcome {
        return runCatching {
            withIOContext {
                val request = POST(
                    url = "$baseUrl/chat",
                    headers = ollamaCloudHeaders(
                        apiKey = apiKey,
                        contentTypeJson = true,
                    ),
                    body = requestBody.toRequestBody(jsonMime),
                )
                val response = client.newCall(request).await()
                response.use {
                    val rawBody = it.body.string()
                    if (!it.isSuccessful) {
                        val details = extractOllamaCloudApiErrorMessage(rawBody) ?: rawBody.take(1200)
                        if (it.code == 403 && isOllamaCloudSubscriptionRequired(details)) {
                            return@withIOContext OllamaCloudRequestOutcome.Fatal(
                                "Ollama Cloud API error 403: $details",
                            )
                        }
                        if (it.code == 429 || it.code in 500..599) {
                            val hintSeconds = it.header("Retry-After")?.toDoubleOrNull()
                                ?: extractOllamaRetryAfterSeconds(rawBody)
                            return@withIOContext OllamaCloudRequestOutcome.Retriable(
                                waitMs = computeOllamaRetryDelayMs(
                                    attempt = attempt,
                                    hintSeconds = hintSeconds,
                                ),
                                details = "API error ${it.code}: $details",
                            )
                        }
                        return@withIOContext OllamaCloudRequestOutcome.Failure(
                            "Ollama Cloud API error ${it.code}: $details",
                        )
                    }
                    OllamaCloudRequestOutcome.Success(rawBody)
                }
            }
        }.getOrElse { error ->
            val message = formatAiTranslationThrowableForLog(error)
            if (attempt < MAX_ATTEMPTS && error.isLikelyTransientOllamaFailure()) {
                OllamaCloudRequestOutcome.Retriable(
                    waitMs = computeOllamaRetryDelayMs(attempt = attempt, hintSeconds = null),
                    details = "request exception: $message",
                )
            } else {
                OllamaCloudRequestOutcome.Failure("Ollama Cloud request exception: $message")
            }
        }
    }
}

private sealed interface OllamaCloudRequestOutcome {
    data class Success(val body: String) : OllamaCloudRequestOutcome
    data class Retriable(val waitMs: Long, val details: String) : OllamaCloudRequestOutcome
    data class Failure(val message: String) : OllamaCloudRequestOutcome
    data class Fatal(val message: String) : OllamaCloudRequestOutcome
}

class OllamaCloudFatalTranslationException(message: String) : IllegalStateException(message)

private sealed interface OllamaCloudThink {
    data object Disabled : OllamaCloudThink
    data class Level(val value: String) : OllamaCloudThink
}

private fun normalizeOllamaCloudThink(reasoningEffort: String?): OllamaCloudThink {
    return when (reasoningEffort?.trim()?.lowercase()) {
        "low", "medium", "high" -> OllamaCloudThink.Level(reasoningEffort.trim().lowercase())
        else -> OllamaCloudThink.Disabled
    }
}

private fun JsonObject.extractOllamaCloudApiErrorMessage(): String? {
    val error = this["error"]
    val message = when (error) {
        is JsonPrimitive -> error.contentOrNull
        is JsonObject -> error["message"].asLooseStringOrNull()
            ?: error["error"].asLooseStringOrNull()
            ?: error.toString()
        else -> null
    }?.trim().orEmpty()
    return message.takeIf { it.isNotBlank() }?.let { "Ollama Cloud API error: $it" }
}

private fun JsonObject.extractOllamaCloudAssistantContent(): String {
    val message = this["message"].asObjectOrNull()
    val sources = listOf(
        message?.get("content"),
        this["response"],
        this["content"],
        this["text"],
    )
    return sources.firstNotNullOfOrNull { it.asLooseStringOrNull()?.trim()?.takeIf { text -> text.isNotBlank() } }
        .orEmpty()
}

private fun extractOllamaCloudApiErrorMessage(rawBody: String): String? {
    val payload = runCatching { Json.parseToJsonElement(rawBody) as? JsonObject }.getOrNull()
    if (payload != null) return payload.extractOllamaCloudApiErrorMessage()
    return null
}

private val retryAfterSecondsRegex =
    Regex("(?i)try\\s+again\\s+in\\s+([0-9]+(?:\\.[0-9]+)?)\\s*seconds?")

private fun extractOllamaRetryAfterSeconds(raw: String): Double? {
    val match = retryAfterSecondsRegex.find(raw) ?: return null
    return match.groupValues.getOrNull(1)?.toDoubleOrNull()
}

private fun computeOllamaRetryDelayMs(
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

private fun computeOllamaTranslationNumPredict(segments: List<String>): Int {
    val estimated = segments.sumOf { (it.length / 2).coerceAtLeast(32) } + segments.size * 24
    return estimated.coerceIn(4096, 8192)
}

private fun Throwable.isLikelyTransientOllamaFailure(): Boolean {
    val text = (message ?: javaClass.simpleName).lowercase()
    return text.contains("timeout") ||
        text.contains("timed out") ||
        text.contains("connection") ||
        text.contains("socket") ||
        text.contains("reset") ||
        text.contains("temporarily")
}

private fun isOllamaCloudSubscriptionRequired(details: String): Boolean {
    val normalized = details.lowercase()
    return normalized.contains("requires a subscription") ||
        normalized.contains("upgrade for access") ||
        normalized.contains("ollama.com/upgrade")
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

private fun kotlinx.serialization.json.JsonElement?.asLooseStringOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.contentOrNull
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

private const val MAX_ATTEMPTS = 3
