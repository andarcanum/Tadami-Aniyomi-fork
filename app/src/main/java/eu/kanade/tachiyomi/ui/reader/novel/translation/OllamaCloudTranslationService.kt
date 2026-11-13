package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

class OllamaCloudTranslationService(
    client: OkHttpClient,
    private val json: Json,
) {
    private val transport = TranslationHttpTransport(client, json)

    suspend fun translateBatch(
        segments: List<String>,
        params: OllamaCloudTranslationParams,
        onLog: ((String) -> Unit)? = null,
    ): List<String?>? {
        if (segments.isEmpty()) return emptyList()
        if (params.apiKey.isBlank()) return null
        validateTranslationCredential(params.apiKey)

        val model = params.model.trim()
        if (model.isBlank()) return null

        val baseUrl = normalizeOllamaCloudBaseUrl(params.baseUrl)
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
        val request = POST(
            url = validatedTranslationUrl("$baseUrl/chat"),
            headers = ollamaCloudHeaders(params.apiKey, contentTypeJson = true),
            body = requestBody.toString().toRequestBody(jsonMime),
        )
        val raw = transport.execute(request, "Ollama Cloud", params.apiKey)
        val payload = parseTranslationPayload(raw, json, "Ollama Cloud", secret = params.apiKey)
        validateTranslationFinish(payload["done_reason"].asStringOrNull(), "Ollama Cloud")
        if ((payload["done"] as? JsonPrimitive)?.content == "false") {
            throw tachiyomi.domain.translation.model.TranslationAttemptException(
                tachiyomi.domain.translation.model.TranslationFailure(
                    tachiyomi.domain.translation.model.TranslationFailureKind.TRUNCATED,
                    "Ollama Cloud returned an unfinished answer",
                ),
            )
        }
        val candidate = payload.extractOllamaCloudAssistantContent()
        if (candidate.isBlank()) {
            throw tachiyomi.domain.translation.model.TranslationAttemptException(
                tachiyomi.domain.translation.model.TranslationFailure(
                    tachiyomi.domain.translation.model.TranslationFailureKind.INVALID_RESPONSE,
                    "Ollama Cloud returned no answer text",
                ),
            )
        }
        val parsed = GeminiXmlSegmentParser.parseValidated(candidate, segments.size, allowSinglePlaintext = true)
        onLog?.invoke("Ollama Cloud response: ${parsed.count { !it.isNullOrBlank() }}/${segments.size} valid blocks")
        return parsed
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
}

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

private fun computeOllamaTranslationNumPredict(segments: List<String>): Int {
    val estimated = segments.sumOf { (it.length / 2).coerceAtLeast(32) } + segments.size * 24
    return estimated.coerceIn(4096, 8192)
}
