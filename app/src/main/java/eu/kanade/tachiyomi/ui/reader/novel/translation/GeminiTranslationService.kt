package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
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

        val taggedInput = segments.mapIndexed { index, text ->
            "<s i='$index'>$text</s>"
        }.joinToString("\\n")

        val userPrompt = buildUserPrompt(
            sourceLang = params.sourceLang,
            targetLang = params.targetLang,
            taggedInput = taggedInput,
        )
        val systemPrompt = buildSystemPrompt(
            mode = params.promptMode,
            modifiers = params.promptModifiers,
        )
        val fullPrompt = "$systemPrompt\\n\\n$userPrompt"

        val requestBody = buildJsonObject {
            put(
                "contents",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put(
                                "parts",
                                buildJsonArray {
                                    add(buildJsonObject { put("text", fullPrompt) })
                                },
                            )
                        },
                    )
                },
            )
            put(
                "generationConfig",
                buildJsonObject {
                    put("maxOutputTokens", 8192)
                    put("temperature", params.temperature)
                    put("topP", params.topP)
                    put("topK", params.topK)
                    put(
                        "thinkingConfig",
                        buildJsonObject {
                            // Match lnreader behavior: send only thinkingLevel.
                            put("thinkingLevel", params.reasoningEffort)
                        },
                    )
                },
            )
            put(
                "safetySettings",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("category", "HARM_CATEGORY_HARASSMENT")
                            put("threshold", "BLOCK_NONE")
                        },
                    )
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
                    append(params.model)
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
            onLog?.invoke("🚫 Gemini request exception: ${formatGeminiThrowableForLog(error)}")
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

        val candidateText = parts
            .extractTextParts()
            .let { texts ->
                // Prefer fragment that actually looks like XML payload.
                texts.firstOrNull { it.contains("<s i='") || it.contains("<s i=\"") }
                    ?: texts.firstOrNull()
            }
            .orEmpty()
            .trim()

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

        val parsed = GeminiXmlSegmentParser.parse(candidateText, expectedCount = segments.size)
        if (parsed.all { it.isNullOrBlank() }) {
            onLog?.invoke("⚠️ Gemini parse warning: no XML segments found in candidate text")
            onLog?.invoke("🧾 Candidate text preview: ${candidateText.take(600)}")
        }
        return parsed
    }

    private fun buildSystemPrompt(
        mode: GeminiPromptMode,
        modifiers: String,
    ): String {
        val basePrompt = promptResolver.resolveSystemPrompt(mode)
        if (modifiers.isBlank()) return basePrompt
        return basePrompt + "\\n\\n" + modifiers.trim()
    }

    private fun buildUserPrompt(
        sourceLang: String,
        targetLang: String,
        taggedInput: String,
    ): String {
        return "TRANSLATE from $sourceLang to $targetLang.\\n" +
            "Inject soul into the text. Make the reader believe this was written by a Russian author.\\n\\n" +
            "Use popular genre terminology (Magic -> Магия, etc.). Make it sound like high-quality fiction.\\n\\n" +
            "1. Keep the XML structure exactly as is (<s i='...'>...</s>).\\n" +
            "2. NO PREAMBLE. NO ANALYSIS TEXT. NO MARKDOWN HEADERS.\\n" +
            "3. Start your response IMMEDIATELY with the first XML tag.\\n\\n" +
            "INPUT BLOCK:\\n" +
            taggedInput
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
