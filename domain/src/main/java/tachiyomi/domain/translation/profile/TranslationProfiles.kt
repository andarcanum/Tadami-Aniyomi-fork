package tachiyomi.domain.translation.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tachiyomi.domain.translation.credentials.CredentialIds
import java.util.UUID

@Serializable
enum class TranslationProtocol { OPENAI_CHAT, GEMINI_GENERATE, OLLAMA_NATIVE }

@Serializable
enum class ProfileAuth { BEARER, X_GOOG_API_KEY, NONE }

@Serializable
enum class PresetFlavor { NONE, GEMINI_PRIVATE, OPENROUTER, DEEPSEEK, MISTRAL, NVIDIA, OLLAMA }

@Serializable
enum class CredentialRoutingMode { PRIORITY, ROUND_ROBIN }

@Serializable
enum class OutputTokenField(val wireName: String?) {
    MAX_TOKENS("max_tokens"),
    MAX_COMPLETION_TOKENS("max_completion_tokens"),
    MAX_OUTPUT_TOKENS("maxOutputTokens"),
    OMIT(null),
}

@Serializable
data class CredentialBinding(
    val credentialId: String,
    val priority: Int = 0,
    val enabled: Boolean = true,
    /** Shared by default; independent labels are an explicit user assertion, never inferred from keys. */
    val quotaGroupLabel: String = "shared",
) {
    init {
        CredentialIds.requireValid(credentialId)
        require(priority in 0..1000) { "Credential priority is outside the supported range" }
        requireProfileText(quotaGroupLabel, 80)
    }
    override fun toString(): String = "CredentialBinding(id=$credentialId, priority=$priority, enabled=$enabled)"
}

/** API root convention: OpenAI /v1, Gemini /v1beta, native Ollama /api; adapters append operations. */
@Serializable
data class ConnectionProfile(
    val id: String,
    val name: String,
    val protocol: TranslationProtocol,
    val baseUrl: String,
    val auth: ProfileAuth,
    val presetFlavor: PresetFlavor = PresetFlavor.NONE,
    val credentialBindings: List<CredentialBinding> = emptyList(),
    val routingMode: CredentialRoutingMode = CredentialRoutingMode.PRIORITY,
    val allowInsecureLocalHTTP: Boolean = false,
    val maxConcurrency: Int = 2,
) {
    init {
        requireProfileUuid(id)
        requireProfileText(name, 120)
        require(maxConcurrency in 1..8) { "Invalid connection concurrency" }
        require(credentialBindings.size <= 100) { "Too many credential bindings" }
        require(
            credentialBindings.map {
                it.credentialId
            }.distinct().size == credentialBindings.size,
        ) { "Duplicate credential reference" }
        ProfileEndpointPolicy.origin(baseUrl, allowInsecureLocalHTTP)
    }
    override fun toString(): String =
        "ConnectionProfile(id=$id, protocol=$protocol, auth=$auth, bindings=${credentialBindings.size})"
}

@Serializable
sealed interface ReasoningContract {
    @Serializable
    @SerialName("unknown")
    data object Unknown : ReasoningContract

    @Serializable
    @SerialName("none")
    data object None : ReasoningContract

    @Serializable
    @SerialName("toggle")
    data object Toggle : ReasoningContract

    @Serializable
    @SerialName("levels")
    data class Levels(val options: List<String>) : ReasoningContract {
        init {
            require(options.isNotEmpty() && options.size <= 16 && options.distinct().size == options.size) {
                "Invalid reasoning levels"
            }
            options.forEach { requireProfileToken(it, 40) }
        }
    }

    @Serializable
    @SerialName("budget")
    data class Budget(
        val minimum: Int,
        val maximum: Int,
        val canDisable: Boolean = false,
        val canUseDynamic: Boolean = false,
    ) : ReasoningContract {
        init {
            require(minimum >= 0 && maximum >= minimum) { "Invalid reasoning budget range" }
        }
        fun accepts(tokens: Int): Boolean = when (tokens) {
            -1 -> canUseDynamic
            0 -> canDisable
            else -> tokens > 0 && tokens in minimum..maximum
        }
    }
}

@Serializable
sealed interface ReasoningChoice {
    @Serializable
    @SerialName("default")
    data object ApiDefault : ReasoningChoice

    @Serializable
    @SerialName("toggle")
    data class Toggle(val enabled: Boolean) : ReasoningChoice

    @Serializable
    @SerialName("level")
    data class Level(val value: String) : ReasoningChoice {
        init {
            requireProfileToken(value, 40)
        }
        override fun toString(): String = "ReasoningChoice.Level"
    }

    @Serializable
    @SerialName("budget")
    data class Budget(val tokens: Int) : ReasoningChoice
}

/** Null means omit. Values are explicit choices, not guessed support or universal wire defaults. */
@Serializable
data class GenerationSettings(
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val maxOutputTokens: Int? = null,
    val reasoning: ReasoningChoice = ReasoningChoice.ApiDefault,
) {
    init {
        require(temperature == null || (temperature.isFinite() && temperature >= 0)) { "Invalid temperature" }
        require(topP == null || (topP.isFinite() && topP in 0.0..1.0)) { "Invalid top-p" }
        require(topK == null || topK >= 0) { "Invalid top-k" }
        require(maxOutputTokens == null || maxOutputTokens > 0) { "Invalid output token limit" }
    }
    override fun toString(): String =
        "GenerationSettings(outputSet=${maxOutputTokens != null}, reasoning=${reasoning.javaClass.simpleName})"
}

@Serializable
data class ModelSelection(
    val exactModelId: String,
    val reasoningContract: ReasoningContract = ReasoningContract.Unknown,
    val generation: GenerationSettings = GenerationSettings(),
    val outputTokenField: OutputTokenField = OutputTokenField.OMIT,
) {
    init {
        requireProfileToken(exactModelId, 200)
        require(outputTokenField != OutputTokenField.OMIT || generation.maxOutputTokens == null) {
            "Output token value has no confirmed wire field"
        }
        val choice = generation.reasoning
        val valid = choice == ReasoningChoice.ApiDefault || when (reasoningContract) {
            ReasoningContract.Unknown, ReasoningContract.None -> false
            ReasoningContract.Toggle -> choice is ReasoningChoice.Toggle
            is ReasoningContract.Levels -> choice is ReasoningChoice.Level && choice.value in reasoningContract.options
            is ReasoningContract.Budget -> choice is ReasoningChoice.Budget && reasoningContract.accepts(choice.tokens)
        }
        require(valid) { "Reasoning choice is unsupported or unknown for this selection" }
    }
    override fun toString(): String =
        "ModelSelection(reasoning=${reasoningContract.javaClass.simpleName}, outputField=$outputTokenField)"
}

@Serializable
data class TranslationLanguages(val source: String, val target: String) {
    init {
        requireProfileText(source, 80)
        requireProfileText(target, 80)
    }
}

/** String IDs preserve legacy modes/styles, including values an adapter must explicitly reject. */
@Serializable
data class PromptSettings(
    val modeId: String = "CLASSIC",
    val styleId: String = "PROFESSIONAL",
    val modifierIds: List<String> = emptyList(),
    val customText: String = "",
) {
    init {
        requireProfileToken(modeId, 80)
        requireProfileToken(styleId, 80)
        require(modifierIds.size <= 100 && modifierIds.distinct().size == modifierIds.size) {
            "Invalid prompt modifiers"
        }
        modifierIds.forEach { requireProfileToken(it, 100) }
        require(customText.length <= 20_000) { "Custom prompt is too long" }
    }
    override fun toString(): String = "PromptSettings(modifiers=${modifierIds.size}, custom=${customText.isNotEmpty()})"
}

@Serializable
enum class ProfileRetryMode { OFF, BOUNDED, WAIT_FOR_SERVICE }

@Serializable
data class ExecutionPolicy(
    val retryMode: ProfileRetryMode = ProfileRetryMode.BOUNDED,
    val maxAttempts: Int = 8,
    val repairAttempts: Int = 3,
    val maxRecoveryMillis: Long = 10 * 60_000L,
    val waitCooldownMinMillis: Long = 5 * 60_000L,
    val waitCooldownMaxMillis: Long = 15 * 60_000L,
    val maxRequests: Long? = null,
    /** Conservative reserved token units, not a monetary ceiling or a server price guarantee. */
    val maxReservedTokens: Long? = null,
) {
    init {
        require(maxAttempts in 1..100 && repairAttempts in 1..3) { "Invalid attempt limits" }
        require(maxRecoveryMillis in 1..24 * 60 * 60_000L) { "Invalid recovery period" }
        require(
            waitCooldownMinMillis >= 5 * 60_000L && waitCooldownMaxMillis <= 15 * 60_000L &&
                waitCooldownMaxMillis >= waitCooldownMinMillis,
        ) { "Invalid service-wait cooldown" }
        require(maxRequests == null || maxRequests > 0) { "Invalid request budget" }
        require(maxReservedTokens == null || maxReservedTokens > 0) { "Invalid reserved-token budget" }
    }
}

@Serializable
data class TranslationProfile(
    val id: String,
    val name: String,
    val connectionId: String,
    val model: ModelSelection,
    val languages: TranslationLanguages,
    val prompt: PromptSettings = PromptSettings(),
    val execution: ExecutionPolicy = ExecutionPolicy(),
) {
    init {
        requireProfileUuid(id)
        requireProfileUuid(connectionId)
        requireProfileText(name, 120)
    }
    val exactModelId: String get() = model.exactModelId
    val generation: GenerationSettings get() = model.generation
    override fun toString(): String = "TranslationProfile(id=$id, connection=$connectionId)"
}

internal fun requireProfileUuid(id: String) {
    require(
        id.length == 36 && runCatching {
            UUID.fromString(id).toString() == id
        }.getOrDefault(false),
    ) { "Invalid profile UUID" }
}
internal fun requireProfileText(value: String, maxLength: Int) {
    require(value.isNotBlank() && value.length <= maxLength && value.none(Char::isISOControl)) {
        "Invalid profile text"
    }
}
internal fun requireProfileToken(value: String, maxLength: Int) {
    requireProfileText(value, maxLength)
    require(value.none(Char::isWhitespace)) { "Invalid profile identifier" }
}
