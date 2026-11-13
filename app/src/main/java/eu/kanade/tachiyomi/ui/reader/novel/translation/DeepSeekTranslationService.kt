package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

class DeepSeekTranslationService(
    client: OkHttpClient,
    private val json: Json,
    private val resolveSystemPrompt: (GeminiPromptMode, NovelTranslationPromptFamily) -> String,
) {
    private val transport = TranslationHttpTransport(client, json)

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
        validateTranslationCredential(params.apiKey)
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
            "<s i='$index'>${escapeTranslationSource(text)}</s>"
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
        onLog?.invoke(
            "DeepSeek request: ${segments.size} source blocks, reasoningEffort=$effectiveReasoningEffort, " +
                "thinking=${if (thinkingEnabled) "enabled" else "disabled"}, maxTokens=$maxTokens",
        )
        val request = POST(
            url = validatedTranslationUrl("$baseUrl/chat/completions"),
            headers = headersOf(
                "Content-Type",
                "application/json",
                "Authorization",
                "Bearer ${params.apiKey}",
            ),
            body = requestBody.toString().toRequestBody(jsonMime),
        )
        val raw = transport.execute(request, "DeepSeek", params.apiKey)
        val payload = parseTranslationPayload(raw, json, "DeepSeek", secret = params.apiKey)
        return parseOpenAiTranslationResponse(payload, segments.size, "DeepSeek", onLog)
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
