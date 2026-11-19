package eu.kanade.tachiyomi.ui.reader.novel.translation

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import tachiyomi.domain.translation.model.TranslationAttemptException
import tachiyomi.domain.translation.model.TranslationFailure
import tachiyomi.domain.translation.model.TranslationFailureKind
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.GenerationSettings
import tachiyomi.domain.translation.profile.ModelSelection
import tachiyomi.domain.translation.profile.OutputTokenField
import tachiyomi.domain.translation.profile.PresetFlavor
import tachiyomi.domain.translation.profile.ProfileEndpointPolicy
import tachiyomi.domain.translation.profile.ReasoningChoice
import tachiyomi.domain.translation.profile.ReasoningContract
import tachiyomi.domain.translation.profile.TranslationProtocol

/** Immutable secret-free payload. Header credentials are resolved after HTTP admission. */
internal data class PreparedProfileRequest(
    val url: String,
    val body: JsonObject,
    val isPlaintext: Boolean = false,
) {
    override fun toString(): String = "PreparedProfileRequest([CONTENT REDACTED])"
}

/** Normalizes a connection profile's API root URL without modifying explicit custom version subpaths. */
internal fun profileApiRootUrl(connection: ConnectionProfile): HttpUrl {
    val parsed = connection.baseUrl.trim().trimEnd('/').toHttpUrl()
    val segments = parsed.pathSegments.filter { it.isNotEmpty() }.toMutableList()
    when (connection.protocol) {
        TranslationProtocol.OPENAI_CHAT -> {
            if (segments.size >= 2 &&
                segments[segments.size - 2].equals("chat", ignoreCase = true) &&
                segments.last().equals("completions", ignoreCase = true)
            ) {
                segments.removeAt(segments.lastIndex)
                segments.removeAt(segments.lastIndex)
            } else if (segments.lastOrNull()?.equals("models", ignoreCase = true) == true) {
                segments.removeAt(segments.lastIndex)
            }
            when (connection.presetFlavor) {
                PresetFlavor.DEEPSEEK -> Unit
                PresetFlavor.OPENROUTER -> {
                    if (segments.isEmpty()) {
                        segments += listOf("api", "v1")
                    } else if (segments.size == 1 && segments[0].equals("api", ignoreCase = true)) {
                        segments += "v1"
                    }
                }
                else -> if (segments.isEmpty()) {
                    segments += "v1"
                }
            }
        }
        TranslationProtocol.GEMINI_GENERATE -> {
            val lastLower = segments.lastOrNull()?.lowercase()
            if (lastLower != null &&
                (lastLower.endsWith(":generatecontent") || lastLower.endsWith(":streamgeneratecontent"))
            ) {
                segments.removeAt(segments.lastIndex)
                if (segments.lastOrNull()?.equals("models", ignoreCase = true) == true) {
                    segments.removeAt(segments.lastIndex)
                }
            } else if (lastLower == "models") {
                segments.removeAt(segments.lastIndex)
            }
            if (segments.lastOrNull()?.lowercase() !in setOf("v1beta", "v1", "v1alpha")) {
                segments += "v1beta"
            }
        }
        TranslationProtocol.OLLAMA_NATIVE -> {
            if (segments.lastOrNull()?.lowercase() in setOf("chat", "tags")) {
                segments.removeAt(segments.lastIndex)
            }
            if (segments.lastOrNull()?.equals("v1", ignoreCase = true) == true) {
                segments[segments.lastIndex] = "api"
            } else if (segments.isEmpty() || !segments.last().equals("api", ignoreCase = true)) {
                segments += "api"
            }
        }
    }
    return parsed.newBuilder().encodedPath("/").apply {
        segments.forEach(::addPathSegment)
    }.build()
}

private val GEMINI_UNBLOCKED_HARM_CATEGORIES = listOf(
    "HARM_CATEGORY_HATE_SPEECH",
    "HARM_CATEGORY_SEXUALLY_EXPLICIT",
    "HARM_CATEGORY_DANGEROUS_CONTENT",
    "HARM_CATEGORY_HARASSMENT",
    "HARM_CATEGORY_CIVIC_INTEGRITY",
)

internal fun geminiSafetySettingsJsonArray(): JsonArray = buildJsonArray {
    GEMINI_UNBLOCKED_HARM_CATEGORIES.forEach { category ->
        add(
            buildJsonObject {
                put("category", category)
                put("threshold", "BLOCK_NONE")
            },
        )
    }
}

internal object ProfileRequestBuilder {
    fun build(
        connection: ConnectionProfile,
        model: ModelSelection,
        systemPrompt: String,
        userPrompt: String,
        segments: List<String> = emptyList(),
    ): PreparedProfileRequest {
        ProfileEndpointPolicy.origin(connection.baseUrl, connection.allowInsecureLocalHTTP)
        validateOptions(connection, model)
        val root = profileApiRootUrl(connection).newBuilder()
        val geminiId = model.exactModelId.removePrefix("models/")
        val url = when (connection.protocol) {
            TranslationProtocol.OPENAI_CHAT -> root.addPathSegment("chat").addPathSegment("completions").build()
            TranslationProtocol.GEMINI_GENERATE -> {
                if (!Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,199}").matches(geminiId)) profileConfigurationFailure()
                root.addPathSegment("models").addPathSegment("$geminiId:generateContent").build()
            }
            TranslationProtocol.OLLAMA_NATIVE -> root.addPathSegment("chat").build()
        }
        val body = when (connection.protocol) {
            TranslationProtocol.GEMINI_GENERATE -> buildJsonObject {
                val lowerId = geminiId.lowercase()
                val inlineSystem = lowerId.startsWith("gemma-") && !lowerId.startsWith("gemma-4-")
                val hasSystem = systemPrompt.isNotBlank()
                if (hasSystem && !inlineSystem) {
                    put(
                        "systemInstruction",
                        buildJsonObject {
                            put("role", "system")
                            put("parts", buildJsonArray { add(buildJsonObject { put("text", systemPrompt) }) })
                        },
                    )
                }
                val contentText = if (hasSystem && inlineSystem) "$systemPrompt\n\n$userPrompt" else userPrompt
                put(
                    "contents",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("role", "user")
                                put("parts", buildJsonArray { add(buildJsonObject { put("text", contentText) }) })
                            },
                        )
                    },
                )
                put("generationConfig", buildJsonObject { geminiGeneration(model.generation) })
                val shouldAttachSafetySettings = connection.presetFlavor == PresetFlavor.GEMINI_PRIVATE ||
                    (
                        lowerId.startsWith("gemini-") && (
                            model.reasoningContract != ReasoningContract.Unknown ||
                                resolveGeminiModelCapabilities(geminiId).thinking != GeminiThinkingContract.Unknown ||
                                model.generation.temperature != null
                            )
                        )
                if (shouldAttachSafetySettings) {
                    put("safetySettings", geminiSafetySettingsJsonArray())
                }
            }
            TranslationProtocol.OPENAI_CHAT, TranslationProtocol.OLLAMA_NATIVE -> buildJsonObject {
                put("model", model.exactModelId)
                put("stream", false)
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
                if (connection.protocol == TranslationProtocol.OLLAMA_NATIVE) {
                    put(
                        "options",
                        buildJsonObject {
                            model.generation.temperature?.let { put("temperature", it) }
                            model.generation.topP?.let { put("top_p", it) }
                            model.generation.topK?.let { put("top_k", it) }
                            val output =
                                model.generation.maxOutputTokens
                                    ?: if (connection.presetFlavor == PresetFlavor.OLLAMA) {
                                        nativeOutputLimit(segments, 8_192)
                                    } else {
                                        null
                                    }
                            output?.let { put("num_predict", it) }
                        },
                    )
                    when (val reasoning = model.generation.reasoning) {
                        is ReasoningChoice.Toggle -> put("think", reasoning.enabled)
                        is ReasoningChoice.Level -> if (reasoning.value == "none") {
                            put("think", false)
                        } else if (ollamaModelSupportsStringThinkLevel(model.exactModelId)) {
                            put("think", reasoning.value)
                        } else {
                            put("think", true)
                        }
                        else -> Unit
                    }
                } else {
                    openAiGeneration(connection, model, segments)
                }
            }
        }
        return PreparedProfileRequest(url.toString(), body)
    }

    private fun JsonObjectBuilder.openAiGeneration(
        connection: ConnectionProfile,
        model: ModelSelection,
        segments: List<String>,
    ) {
        model.generation.temperature?.let { put("temperature", it) }
        model.generation.topP?.let { put("top_p", it) }
        val choice = model.generation.reasoning
        when (connection.presetFlavor) {
            PresetFlavor.DEEPSEEK -> {
                val enabled = when (choice) {
                    is ReasoningChoice.Toggle -> choice.enabled
                    is ReasoningChoice.Level -> choice.value != "none"
                    else -> null
                }
                if (enabled != null) {
                    put("thinking", buildJsonObject { put("type", if (enabled) "enabled" else "disabled") })
                    if (enabled) {
                        put("reasoning_effort", "high")
                    }
                }
                put("presence_penalty", DEEPSEEK_DEFAULT_PRESENCE_PENALTY)
                put("frequency_penalty", DEEPSEEK_DEFAULT_FREQUENCY_PENALTY)
            }
            PresetFlavor.OPENROUTER -> {
                val effort = when (choice) {
                    is ReasoningChoice.Level -> choice.value.takeIf { it != "none" }
                    is ReasoningChoice.Toggle -> if (choice.enabled) "high" else null
                    else -> null
                }
                if (effort != null || choice is ReasoningChoice.Budget) {
                    put(
                        "reasoning",
                        buildJsonObject {
                            effort?.let { put("effort", it) }
                            if (choice is ReasoningChoice.Budget) put("max_tokens", choice.tokens)
                            put("exclude", true)
                        },
                    )
                }
            }
            else -> when (choice) {
                is ReasoningChoice.Toggle -> put("reasoning_effort", if (choice.enabled) "high" else "none")
                is ReasoningChoice.Level -> put("reasoning_effort", choice.value)
                else -> Unit
            }
        }
        val native = connection.presetFlavor in setOf(
            PresetFlavor.DEEPSEEK,
            PresetFlavor.OPENROUTER,
            PresetFlavor.MISTRAL,
            PresetFlavor.NVIDIA,
        )
        val thinking = (choice as? ReasoningChoice.Level)?.value?.let { it != "none" } == true ||
            (choice as? ReasoningChoice.Toggle)?.enabled == true
        val output = model.generation.maxOutputTokens ?: if (native) {
            if (connection.presetFlavor == PresetFlavor.DEEPSEEK && thinking) {
                32_768
            } else {
                nativeOutputLimit(segments, if (connection.presetFlavor == PresetFlavor.OPENROUTER) 16_384 else 8_192)
            }
        } else {
            null
        }
        val field = if (model.generation.maxOutputTokens != null) model.outputTokenField.wireName else "max_tokens"
        output?.let { value -> field?.let { put(it, value) } }
    }

    private fun JsonObjectBuilder.geminiGeneration(settings: GenerationSettings) {
        settings.temperature?.let { put("temperature", it) }
        settings.topP?.let { put("topP", it) }
        settings.topK?.let { put("topK", it) }
        settings.maxOutputTokens?.let { put("maxOutputTokens", it) }
        when (val reasoning = settings.reasoning) {
            ReasoningChoice.ApiDefault -> Unit
            is ReasoningChoice.Toggle -> put(
                "thinkingConfig",
                buildJsonObject {
                    put("thinkingLevel", if (reasoning.enabled) "high" else "minimal")
                },
            )
            is ReasoningChoice.Level -> put("thinkingConfig", buildJsonObject { put("thinkingLevel", reasoning.value) })
            is ReasoningChoice.Budget -> put(
                "thinkingConfig",
                buildJsonObject {
                    put("thinkingBudget", reasoning.tokens)
                },
            )
        }
    }

    private fun validateOptions(connection: ConnectionProfile, model: ModelSelection) {
        val flavor = connection.presetFlavor
        val validFlavor = when (connection.protocol) {
            TranslationProtocol.OPENAI_CHAT -> flavor in setOf(
                PresetFlavor.NONE,
                PresetFlavor.DEEPSEEK,
                PresetFlavor.OPENROUTER,
                PresetFlavor.MISTRAL,
                PresetFlavor.NVIDIA,
            )
            TranslationProtocol.GEMINI_GENERATE -> flavor == PresetFlavor.NONE || flavor == PresetFlavor.GEMINI_PRIVATE
            TranslationProtocol.OLLAMA_NATIVE -> flavor == PresetFlavor.NONE || flavor == PresetFlavor.OLLAMA
        }
        val validOutput = when (connection.protocol) {
            TranslationProtocol.GEMINI_GENERATE ->
                model.outputTokenField in
                    setOf(OutputTokenField.OMIT, OutputTokenField.MAX_OUTPUT_TOKENS)
            TranslationProtocol.OPENAI_CHAT -> model.outputTokenField != OutputTokenField.MAX_OUTPUT_TOKENS
            TranslationProtocol.OLLAMA_NATIVE -> true // Explicit native mapping is options.num_predict.
        }
        val validReasoning = model.generation.reasoning !is ReasoningChoice.Budget ||
            connection.protocol == TranslationProtocol.GEMINI_GENERATE || flavor == PresetFlavor.OPENROUTER
        if (!validFlavor || !validOutput || !validReasoning ||
            (connection.protocol == TranslationProtocol.OPENAI_CHAT && model.generation.topK != null) ||
            (flavor == PresetFlavor.NVIDIA && model.generation.reasoning != ReasoningChoice.ApiDefault)
        ) {
            profileConfigurationFailure()
        }
    }

    private fun nativeOutputLimit(segments: List<String>, maximum: Int): Int {
        val estimate = segments.sumOf { (it.length.toLong() / 2).coerceAtLeast(32) } + segments.size.toLong() * 24
        return estimate.coerceIn(4_096, maximum.toLong()).toInt()
    }
}

internal fun profileConfigurationFailure(): Nothing = throw TranslationAttemptException(
    TranslationFailure(
        TranslationFailureKind.CONFIGURATION,
        "Model options are not supported by this connection protocol",
        attempted = false,
    ),
)
