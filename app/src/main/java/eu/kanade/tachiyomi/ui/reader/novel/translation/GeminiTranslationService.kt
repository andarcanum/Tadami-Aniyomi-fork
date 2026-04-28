package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext

class GeminiTranslationService(
    private val client: OkHttpClient,
    private val json: Json,
    private val promptResolver: GeminiPromptResolver,
) {

    suspend fun translateBatch(
        segments: List<String>,
        params: GeminiTranslationParams,
        onLog: ((String) -> Unit)? = null,
    ): List<String?>? {
        if (segments.isEmpty()) return emptyList()
        if (params.apiKey.isBlank()) return null

        val usePrivateBridge =
            params.provider == NovelTranslationProvider.GEMINI_PRIVATE &&
                GeminiPrivateBridge.isInstalled()
        val bridgeUnlocked = !usePrivateBridge || params.privateUnlocked || GeminiPrivateBridge.isUnlocked()
        if (usePrivateBridge && !bridgeUnlocked) {
            onLog?.invoke("🔒 Private Gemini bridge is locked")
            return null
        }
        val preparedSegments = if (usePrivateBridge) GeminiPrivateBridge.preprocessSegments(segments) else segments

        val usePrivatePythonLikeMode = usePrivateBridge && params.privatePythonLikeMode
        val promptFamily = if (usePrivateBridge) {
            NovelTranslationPromptFamily.RUSSIAN
        } else {
            resolveNovelTranslationPromptFamily(params.targetLang)
        }
        val taggedInput = preparedSegments.mapIndexed { index, text ->
            "<s i='$index'>$text</s>"
        }.joinToString("\\n")
        val plainChapterInput = preparedSegments.joinToString("\n\n")

        val effectiveModifiers = if (usePrivateBridge && GeminiPrivateBridge.disablePromptModifiers()) {
            ""
        } else {
            params.promptModifiers
        }

        val userPrompt = buildUserPrompt(
            sourceLang = params.sourceLang,
            targetLang = params.targetLang,
            taggedInput = taggedInput,
            family = promptFamily,
        )
        val defaultSystemPrompt = buildSystemPrompt(
            mode = params.promptMode,
            modifiers = effectiveModifiers,
            family = promptFamily,
        )
        val systemPrompt = if (usePrivateBridge) {
            GeminiPrivateBridge.systemPromptOverride() ?: defaultSystemPrompt
        } else {
            defaultSystemPrompt
        }
        val fullPrompt = "$systemPrompt\\n\\n$userPrompt"
        val userContentText = when {
            usePrivatePythonLikeMode -> plainChapterInput
            usePrivateBridge -> taggedInput
            else -> fullPrompt
        }

        val requestModel = if (usePrivateBridge) {
            GeminiPrivateBridge.requestModelOverride(params.model)
        } else {
            params.model
        }
        val requestTemperature = if (usePrivateBridge) {
            GeminiPrivateBridge.requestTemperatureOverride(params.temperature)
        } else {
            params.temperature
        }
        val requestTopP = if (usePrivateBridge) {
            GeminiPrivateBridge.requestTopPOverride(params.topP)
        } else {
            params.topP
        }
        val requestTopK = if (usePrivateBridge) {
            GeminiPrivateBridge.requestTopKOverride(params.topK)
        } else {
            params.topK
        }
        val requestMaxOutputTokens = if (usePrivateBridge) {
            GeminiPrivateBridge.requestMaxOutputTokensOverride(8192)
        } else {
            8192
        }
        val requestFrequencyPenalty = if (usePrivateBridge) {
            GeminiPrivateBridge.requestFrequencyPenaltyOverride(0f)
        } else {
            0f
        }
        val requestPresencePenalty = if (usePrivateBridge) {
            GeminiPrivateBridge.requestPresencePenaltyOverride(0f)
        } else {
            0f
        }
        val requestThinkingLevel = if (usePrivateBridge) {
            GeminiPrivateBridge.requestThinkingLevelOverride(params.reasoningEffort)
        } else {
            params.reasoningEffort
        }
        if (usePrivatePythonLikeMode) {
            onLog?.invoke("🧪 GeminiNSFW python-like mode enabled (plain chapter payload, parse full response)")
        }

        val requestBody = buildJsonObject {
            if (usePrivateBridge) {
                put(
                    "systemInstruction",
                    buildJsonObject {
                        put("role", "system")
                        put(
                            "parts",
                            buildJsonArray {
                                add(buildJsonObject { put("text", systemPrompt) })
                            },
                        )
                    },
                )
            }
            put(
                "contents",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put(
                                "parts",
                                buildJsonArray {
                                    add(buildJsonObject { put("text", userContentText) })
                                },
                            )
                        },
                    )
                },
            )
            put(
                "generationConfig",
                buildJsonObject {
                    put("maxOutputTokens", requestMaxOutputTokens)
                    put("temperature", requestTemperature)
                    put("topP", requestTopP)
                    put("topK", requestTopK)
                    if (usePrivateBridge) {
                        put("frequencyPenalty", requestFrequencyPenalty)
                        put("presencePenalty", requestPresencePenalty)
                    }
                    put(
                        "thinkingConfig",
                        buildJsonObject {
                            // Match lnreader behavior: send only thinkingLevel.
                            put("thinkingLevel", requestThinkingLevel)
                        },
                    )
                },
            )
            put(
                "safetySettings",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("category", "HARM_CATEGORY_HATE_SPEECH")
                            put("threshold", "BLOCK_NONE")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT")
                            put("threshold", "BLOCK_NONE")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("category", "HARM_CATEGORY_DANGEROUS_CONTENT")
                            put("threshold", "BLOCK_NONE")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("category", "HARM_CATEGORY_HARASSMENT")
                            put("threshold", "BLOCK_NONE")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("category", "HARM_CATEGORY_CIVIC_INTEGRITY")
                            put("threshold", "BLOCK_NONE")
                        },
                    )
                },
            )
        }

        val responseText = runCatching {
            withIOContext {
                val url = buildString {
                    append("https://generativelanguage.googleapis.com/v1beta/models/")
                    append(requestModel)
                    append(":generateContent?key=")
                    append(params.apiKey)
                }
                val request = POST(
                    url = url,
                    headers = headersOf("Content-Type", "application/json"),
                    body = requestBody.toString().toRequestBody(jsonMime),
                )
                val response = client.newCall(request).await()
                response.use {
                    if (!it.isSuccessful) {
                        val rawBody = it.body.string()
                        val modelError = extractGeminiApiErrorMessage(rawBody)
                        val details = modelError ?: rawBody.take(1200)
                        onLog?.invoke("🚫 Gemini API error ${it.code}: $details")
                        return@withIOContext null
                    }
                    it.body.string()
                }
            }
        }.onFailure { error ->
            onLog?.invoke("🚫 Gemini request exception: ${formatAiTranslationThrowableForLog(error)}")
        }.getOrNull() ?: return null

        val payload = runCatching { json.parseToJsonElement(responseText) as? JsonObject }
            .getOrNull()
            ?: run {
                onLog?.invoke("🚫 Gemini response is not valid JSON object. Raw: ${responseText.take(600)}")
                return null
            }

        payload["promptFeedback"]?.asObjectOrNull()?.let { feedback ->
            onLog?.invoke("🛡️ Prompt feedback: ${feedback.toString().take(240)}")
        }
        payload["usageMetadata"]?.asObjectOrNull()?.let { usage ->
            onLog?.invoke("🧠 Usage: ${usage.toString().take(240)}")
        }

        val firstCandidate = payload["candidates"]
            .asArrayOrNull()
            ?.firstOrNull()
            ?.asObjectOrNull()

        if (firstCandidate == null) {
            onLog?.invoke("🚫 Gemini response has no candidates. Payload: ${payload.toString().take(600)}")
            return null
        }

        val parts = firstCandidate["content"]
            .asObjectOrNull()
            ?.get("parts")
            .asArrayOrNull()

        val rawCandidateText = parts
            .extractTextParts()
            .let { texts ->
                // Prefer fragment that actually looks like XML payload.
                texts.firstOrNull { it.contains("<s i='") || it.contains("<s i=\"") }
                    ?: texts.firstOrNull()
            }
            .orEmpty()
            .trim()
        if (usePrivateBridge && rawCandidateText.isNotBlank()) {
            onLog?.invoke("📥 GeminiNSFW raw response:")
            logLargeTextToGeminiLog(rawCandidateText, onLog, prefix = "📥")
        }
        val candidateText = rawCandidateText

        if (candidateText.isBlank()) {
            val finishReason = firstCandidate["finishReason"].asStringOrNull()
            if (finishReason != null) {
                onLog?.invoke("🚫 Gemini empty candidate, finishReason=$finishReason")
            } else {
                onLog?.invoke("🚫 Gemini candidate has no text parts")
            }
            onLog?.invoke("🧩 Candidate payload: ${firstCandidate.toString().take(600)}")
            val safetyRatings = firstCandidate["safetyRatings"]?.asArrayOrNull()?.toString()
            if (!safetyRatings.isNullOrBlank()) {
                onLog?.invoke("🛡️ Safety ratings: ${safetyRatings.take(240)}")
            }
            return null
        }

        val parsed = if (usePrivatePythonLikeMode) {
            val parsedPlain = GeminiXmlSegmentParser.parsePlaintext(
                rawResponse = candidateText,
                expectedCount = preparedSegments.size,
            )
            val translatedCount = parsedPlain.count { !it.isNullOrBlank() }
            if (translatedCount < preparedSegments.size) {
                onLog?.invoke(
                    "⚠️ GeminiNSFW python-like: expected ${preparedSegments.size}, got $translatedCount",
                )
            }
            parsedPlain
        } else {
            GeminiXmlSegmentParser.parse(candidateText, expectedCount = preparedSegments.size)
        }
        if (parsed.all { it.isNullOrBlank() }) {
            if (usePrivatePythonLikeMode) {
                onLog?.invoke("⚠️ Gemini parse warning: python-like response is empty")
            } else {
                onLog?.invoke("⚠️ Gemini parse warning: no XML segments found in candidate text")
            }
            onLog?.invoke("🧾 Candidate text preview: ${candidateText.take(600)}")
        }
        return parsed
    }

    private fun buildSystemPrompt(
        mode: GeminiPromptMode,
        modifiers: String,
        family: NovelTranslationPromptFamily,
    ): String {
        val basePrompt = promptResolver.resolveSystemPrompt(mode, family = family)
        if (modifiers.isBlank()) return basePrompt
        return basePrompt + "\\n\\n" + modifiers.trim()
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
}

private fun kotlinx.serialization.json.JsonElement?.asObjectOrNull(): JsonObject? {
    return this as? JsonObject
}

private fun kotlinx.serialization.json.JsonElement?.asArrayOrNull(): JsonArray? {
    return this as? JsonArray
}

private fun JsonArray?.extractTextParts(): List<String> {
    return this.orEmpty().mapNotNull { part ->
        part.asObjectOrNull()
            ?.get("text")
            .asStringOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}

private fun kotlinx.serialization.json.JsonElement?.asStringOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    return primitive.content
}

private fun logLargeTextToGeminiLog(
    text: String,
    onLog: ((String) -> Unit)?,
    prefix: String,
    chunkSize: Int = 1200,
) {
    if (onLog == null) return
    if (text.isBlank()) {
        onLog("$prefix <empty>")
        return
    }
    val chunks = text.chunked(chunkSize.coerceAtLeast(200))
    chunks.forEachIndexed { index, chunk ->
        onLog("$prefix [${index + 1}/${chunks.size}] $chunk")
    }
}
