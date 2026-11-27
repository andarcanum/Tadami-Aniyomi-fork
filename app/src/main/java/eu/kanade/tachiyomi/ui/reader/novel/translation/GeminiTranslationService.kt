package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

class GeminiTranslationService(
    client: OkHttpClient,
    private val json: Json,
    private val promptResolver: GeminiPromptResolver,
    private val baseUrl: String = "https://generativelanguage.googleapis.com",
) {
    private val transport = TranslationHttpTransport(client, json)

    suspend fun translateBatch(
        segments: List<String>,
        params: GeminiTranslationParams,
        onLog: ((String) -> Unit)? = null,
    ): List<String?>? {
        if (segments.isEmpty()) return emptyList()
        if (params.apiKey.isBlank()) return null
        validateTranslationCredential(params.apiKey)

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
            "<s i='$index'>${escapeTranslationSource(text)}</s>"
        }.joinToString("\n")
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
        val fullPrompt = "$systemPrompt\n\n$userPrompt"
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
        val capabilities = resolveGeminiModelCapabilities(requestModel)
        val modelMetadata = GeminiModelMetadataStore.lookup(requestModel)
        val knownModel = capabilities.thinking != GeminiThinkingContract.Unknown || modelMetadata != null
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
                if (!usePrivateBridge) {
                    params.frozenGenerationConfig?.let(::validateFrozenGeminiGenerationConfig)
                        ?: buildPublicGeminiGenerationConfig(params)
                } else {
                    buildJsonObject {
                        put(
                            "maxOutputTokens",
                            GeminiPrivateBridge.requestMaxOutputTokensOverride(GEMINI_TRANSLATION_MAX_OUTPUT_TOKENS),
                        )
                        put(
                            "temperature",
                            GeminiPrivateBridge.requestTemperatureOverride(params.temperature)
                                .coerceIn(0f, capabilities.maxTemperature ?: 2f),
                        )
                        put(
                            "topP",
                            GeminiPrivateBridge.requestTopPOverride(params.topP).coerceIn(0f, 1f),
                        )
                        put("topK", GeminiPrivateBridge.requestTopKOverride(params.topK))
                        put("frequencyPenalty", GeminiPrivateBridge.requestFrequencyPenaltyOverride(0f))
                        put("presencePenalty", GeminiPrivateBridge.requestPresencePenaltyOverride(0f))
                        put(
                            "thinkingConfig",
                            buildJsonObject {
                                put(
                                    "thinkingLevel",
                                    GeminiPrivateBridge.requestThinkingLevelOverride(params.reasoningEffort),
                                )
                            },
                        )
                    }
                },
            )
            if (usePrivateBridge ||
                (knownModel && requestModel.trim().removePrefix("models/").lowercase().startsWith("gemini-"))
            ) {
                put("safetySettings", geminiSafetySettingsJsonArray())
            }
        }

        val modelId = requestModel.trim().removePrefix("models/")
        if (!Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,199}").matches(modelId)) {
            throw tachiyomi.domain.translation.model.TranslationAttemptException(
                tachiyomi.domain.translation.model.TranslationFailure(
                    tachiyomi.domain.translation.model.TranslationFailureKind.CONFIGURATION,
                    "Invalid Gemini model ID",
                ),
            )
        }
        val normalizedBaseUrl = normalizeGeminiBaseUrl(baseUrl)
        val request = POST(
            url = validatedTranslationUrl("$normalizedBaseUrl/models/$modelId:generateContent"),
            headers = headersOf("Content-Type", "application/json", "x-goog-api-key", params.apiKey),
            body = requestBody.toString().toRequestBody(jsonMime),
        )
        val raw = transport.execute(request, "Gemini", params.apiKey)
        val payload = parseTranslationPayload(raw, json, "Gemini", secret = params.apiKey)
        if (!payload["promptFeedback"].asObjectOrNull()?.get("blockReason").asStringOrNull().isNullOrBlank()) {
            throw tachiyomi.domain.translation.model.TranslationAttemptException(
                tachiyomi.domain.translation.model.TranslationFailure(
                    tachiyomi.domain.translation.model.TranslationFailureKind.REFUSED,
                    "Gemini declined the request",
                ),
            )
        }
        val candidate = payload["candidates"].asArrayOrNull()?.firstOrNull().asObjectOrNull()
            ?: throw tachiyomi.domain.translation.model.TranslationAttemptException(
                tachiyomi.domain.translation.model.TranslationFailure(
                    tachiyomi.domain.translation.model.TranslationFailureKind.INVALID_RESPONSE,
                    "Gemini returned no candidate",
                ),
            )
        validateTranslationFinish(candidate["finishReason"].asStringOrNull(), "Gemini")
        val parts = candidate["content"].asObjectOrNull()?.get("parts").asArrayOrNull().orEmpty()
        val answer = parts.mapNotNull { part ->
            val obj = part.asObjectOrNull() ?: return@mapNotNull null
            if ((obj["thought"] as? kotlinx.serialization.json.JsonPrimitive)?.content ==
                "true"
            ) {
                null
            } else {
                obj["text"].asStringOrNull()
            }
        }.joinToString("").trim()
        if (answer.isBlank()) {
            throw tachiyomi.domain.translation.model.TranslationAttemptException(
                tachiyomi.domain.translation.model.TranslationFailure(
                    tachiyomi.domain.translation.model.TranslationFailureKind.INVALID_RESPONSE,
                    "Gemini returned no answer text",
                ),
            )
        }
        val parsed = if (usePrivatePythonLikeMode) {
            GeminiXmlSegmentParser.parsePlaintext(answer, preparedSegments.size)
        } else {
            GeminiXmlSegmentParser.parseValidated(answer, preparedSegments.size, allowSinglePlaintext = true)
        }
        onLog?.invoke(
            "Gemini response: ${parsed.count {
                !it.isNullOrBlank()
            }}/${preparedSegments.size} valid text blocks",
        )
        return parsed
    }

    private fun buildSystemPrompt(
        mode: GeminiPromptMode,
        modifiers: String,
        family: NovelTranslationPromptFamily,
    ): String {
        val basePrompt = promptResolver.resolveSystemPrompt(mode, family = family)
        if (modifiers.isBlank()) return basePrompt
        return basePrompt + "\n\n" + modifiers.trim()
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
