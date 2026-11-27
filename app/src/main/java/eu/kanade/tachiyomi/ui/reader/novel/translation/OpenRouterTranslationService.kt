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

class OpenRouterTranslationService(
    client: OkHttpClient,
    private val json: Json,
) {
    private val transport = TranslationHttpTransport(client, json)

    suspend fun translateBatch(
        segments: List<String>,
        params: OpenRouterTranslationParams,
        onLog: ((String) -> Unit)? = null,
    ): List<String?>? {
        if (segments.isEmpty()) return emptyList()
        if (params.apiKey.isBlank()) return null
        validateTranslationCredential(params.apiKey)

        val model = params.model.trim()
        if (model.isBlank()) return null

        val baseUrl = normalizeOpenRouterBaseUrl(params.baseUrl)
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
            put("temperature", params.temperature)
            put("top_p", params.topP)
            params.reasoningEffort
                ?.trim()
                ?.takeIf { it.isNotBlank() && !it.equals("none", ignoreCase = true) }
                ?.let { effort ->
                    put(
                        "reasoning",
                        buildJsonObject {
                            put("effort", effort)
                            put("exclude", true)
                        },
                    )
                }
            put("max_tokens", computeOpenAiStyleMaxTokens(segments, minTokens = 4_096, maxTokens = 16_384))
            put("stream", false)
        }

        onLog?.invoke("OpenRouter request: ${segments.size} source blocks")
        val request = POST(
            url = validatedTranslationUrl("$baseUrl/chat/completions"),
            headers = headersOf(
                "Content-Type",
                "application/json",
                "Authorization",
                "Bearer ${params.apiKey}",
                "HTTP-Referer",
                "https://aniyomi.org",
                "X-Title",
                "Aniyomi Novel Reader",
            ),
            body = requestBody.toString().toRequestBody(jsonMime),
        )
        val raw = transport.execute(request, "OpenRouter", params.apiKey)
        val payload = parseTranslationPayload(raw, json, "OpenRouter", secret = params.apiKey)
        return parseOpenAiTranslationResponse(payload, segments.size, "OpenRouter", onLog)
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
