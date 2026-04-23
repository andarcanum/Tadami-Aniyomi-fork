package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient

class NvidiaTranslationService(
    client: OkHttpClient,
    json: Json,
) : BaseOpenAiTranslationService(client, json) {

    suspend fun translateBatch(
        segments: List<String>,
        params: NvidiaTranslationParams,
        onLog: ((String) -> Unit)? = null,
    ): List<String?>? {
        if (segments.isEmpty()) return emptyList()
        if (params.apiKey.isBlank()) return null
        if (params.model.isBlank()) return null

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
            put("model", params.model.trim())
            put("messages", buildMessages(systemPrompt, userPrompt))
            put("temperature", params.temperature)
            put("top_p", params.topP)
            put("max_tokens", 4096)
            put("stream", false)
        }

        val payload = executeChatCompletion(
            baseUrl = params.baseUrl.trim().ifBlank { NVIDIA_BASE_URL },
            apiKey = params.apiKey,
            requestBody = requestBody.toString(),
            logLabel = "NVIDIA",
            onLog = onLog,
        ) ?: return null

        val candidateText = payload.extractAssistantContent().trim()
        if (candidateText.isBlank()) {
            onLog?.invoke("NVIDIA returned empty message content")
            onLog?.invoke("NVIDIA payload: ${payload.toString().take(1200)}")
            return null
        }

        val parsed = GeminiXmlSegmentParser.parse(
            candidateText.trimNonXmlTail(),
            expectedCount = segments.size,
        )
        if (parsed.all { it.isNullOrBlank() }) {
            onLog?.invoke("NVIDIA parse warning: no XML segments found in message")
            onLog?.invoke("NVIDIA content preview: ${candidateText.take(600)}")
            return null
        }
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
private const val NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"

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
