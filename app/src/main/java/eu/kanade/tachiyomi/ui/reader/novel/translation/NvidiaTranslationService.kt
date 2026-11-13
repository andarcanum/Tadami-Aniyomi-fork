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
            put("model", params.model.trim())
            put("messages", buildMessages(systemPrompt, userPrompt))
            put("temperature", params.temperature)
            put("top_p", params.topP)
            put("max_tokens", computeTranslationMaxTokens(segments))
            put("stream", false)
        }

        val baseUrl = normalizeNvidiaBaseUrl(params.baseUrl)
        if (baseUrl.isBlank()) return null

        val payload = executeChatCompletion(
            baseUrl = baseUrl,
            apiKey = params.apiKey,
            requestBody = requestBody.toString(),
            logLabel = "NVIDIA",
            onLog = onLog,
        ) ?: return null

        return parseOpenAiTranslationResponse(payload, segments.size, "NVIDIA", onLog)
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

internal fun normalizeNvidiaBaseUrl(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    if (trimmed.isBlank()) return NVIDIA_BASE_URL
    return normalizeOpenAiBaseUrlRoot(trimmed, "/v1")
}

private fun computeTranslationMaxTokens(segments: List<String>): Int {
    val estimated = segments.sumOf { (it.length / 2).coerceAtLeast(32) } + segments.size * 24
    return estimated.coerceIn(4096, 8192)
}
