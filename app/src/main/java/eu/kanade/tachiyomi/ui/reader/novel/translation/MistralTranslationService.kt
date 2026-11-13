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

class MistralTranslationService(
    client: OkHttpClient,
    private val json: Json,
    private val resolveSystemPrompt: (GeminiPromptMode, NovelTranslationPromptFamily) -> String,
) {
    private val transport = TranslationHttpTransport(client, json)

    suspend fun translateBatch(
        segments: List<String>,
        params: MistralTranslationParams,
        onLog: ((String) -> Unit)? = null,
    ): List<String?>? {
        if (segments.isEmpty()) return emptyList()
        if (params.apiKey.isBlank()) return null
        validateTranslationCredential(params.apiKey)
        if (params.model.isBlank()) return null

        val baseUrl = normalizeMistralBaseUrl(params.baseUrl)
        if (baseUrl.isBlank()) return null

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
            put("temperature", params.temperature)
            put("top_p", params.topP)
            params.reasoningEffort?.let { effort ->
                put("reasoning_effort", effort)
            }
            put("max_tokens", computeOpenAiStyleMaxTokens(segments))
            put("stream", false)
        }

        onLog?.invoke("Mistral request: ${segments.size} source blocks")
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
        val raw = transport.execute(request, "Mistral", params.apiKey)
        val payload = parseTranslationPayload(raw, json, "Mistral", secret = params.apiKey)
        return parseOpenAiTranslationResponse(payload, segments.size, "Mistral", onLog)
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
