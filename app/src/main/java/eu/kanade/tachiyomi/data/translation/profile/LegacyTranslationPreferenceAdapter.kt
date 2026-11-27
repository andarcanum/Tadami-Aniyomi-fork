package eu.kanade.tachiyomi.data.translation.profile

import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiModelMetadataStore
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiThinkingContract
import eu.kanade.tachiyomi.ui.reader.novel.translation.normalizeGeminiModelId
import eu.kanade.tachiyomi.ui.reader.novel.translation.normalizeMistralBaseUrl
import eu.kanade.tachiyomi.ui.reader.novel.translation.normalizeNvidiaBaseUrl
import eu.kanade.tachiyomi.ui.reader.novel.translation.normalizeOllamaCloudBaseUrl
import eu.kanade.tachiyomi.ui.reader.novel.translation.normalizeTranslationReasoningEffort
import eu.kanade.tachiyomi.ui.reader.novel.translation.resolveGeminiModelCapabilities
import eu.kanade.tachiyomi.ui.reader.novel.translation.resolveTranslationReasoningOptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.translation.credentials.CredentialSecret
import tachiyomi.domain.translation.profile.GenerationSettings
import tachiyomi.domain.translation.profile.LegacyCredentialScope
import tachiyomi.domain.translation.profile.ModelSelection
import tachiyomi.domain.translation.profile.OutputTokenField
import tachiyomi.domain.translation.profile.PresetFlavor
import tachiyomi.domain.translation.profile.ProfileAuth
import tachiyomi.domain.translation.profile.ProfileEndpointPolicy
import tachiyomi.domain.translation.profile.PromptSettings
import tachiyomi.domain.translation.profile.ReasoningChoice
import tachiyomi.domain.translation.profile.ReasoningContract
import tachiyomi.domain.translation.profile.TranslationLanguages
import tachiyomi.domain.translation.profile.TranslationProtocol

/** Captures one raw preference image; source fields override globals only when non-null. */
internal class LegacyTranslationPreferenceAdapter(
    private val preferences: NovelReaderPreferences,
    private val preferenceStore: PreferenceStore? = null,
) : LegacyTranslationSettings {
    override suspend fun hasCompletedKeylessMigration(): Boolean =
        preferenceStore?.getBoolean(KEYLESS_MIGRATED_PREF_KEY, false)?.get() ?: false

    override suspend fun markKeylessMigrationComplete() {
        preferenceStore?.getBoolean(KEYLESS_MIGRATED_PREF_KEY, false)?.set(true)
    }

    override suspend fun readSnapshot(): LegacyTranslationSnapshot {
        val raw = preferences.legacyTranslationPreferenceValues().values
        val sources = LegacyPreferenceAccess.sourceObjects(raw)
        if (sources.size > 1_000) invalidLegacySettings()
        val credentials = mutableListOf<LegacyCredentialValue>()
        try {
            val scopes = listOf(LegacyCredentialScope.GLOBAL to 0L) + sources.keys.map { key ->
                val id = key.toLongOrNull() ?: invalidLegacySettings()
                if (id.toString() != key) invalidLegacySettings()
                LegacyCredentialScope.SOURCE to id
            }
            var hasConfiguredKeyless = KEYLESS_CONFIGURATION_KEYS.any { it in raw }
            val configurations = scopes.map { (scope, id) ->
                val source = if (scope == LegacyCredentialScope.SOURCE) {
                    sources[id.toString()] as? JsonObject ?: invalidLegacySettings()
                } else {
                    JsonObject(emptyMap())
                }
                if (scope == LegacyCredentialScope.SOURCE && hasLegacyTranslationOverride(source)) {
                    hasConfiguredKeyless = true
                }
                val fields = LegacySettingsFields(raw, source)
                LegacyProvider.entries.forEach { provider ->
                    val key = if (scope == LegacyCredentialScope.SOURCE) {
                        LegacyPreferenceAccess.sourceCredential(raw, provider, id)
                    } else {
                        LegacyPreferenceAccess.globalCredential(raw, provider)
                    }
                    if (!key.isNullOrBlank()) {
                        credentials += LegacyCredentialValue(
                            provider,
                            scope,
                            id,
                            CredentialSecret(key),
                        )
                    }
                }
                val parsedProvider = runCatching {
                    LegacyProvider.valueOf(
                        fields.string("translationProvider", "translation_provider", "GEMINI").trim().uppercase(),
                    )
                }.getOrNull()
                val selectedProvider = parsedProvider ?: LegacyProvider.GEMINI
                LegacyScopeSettings(
                    scope,
                    id,
                    selectedProvider,
                    LegacyProvider.entries.map { provider ->
                        val config = fields.safeConfiguration(provider)
                        if (parsedProvider == null && provider == selectedProvider && config.issue == null) {
                            config.copy(issue = LegacyConfigurationIssue.INVALID_SETTING)
                        } else {
                            config
                        }
                    },
                    enabled = runCatching {
                        fields.boolean("geminiEnabled", "gemini_enabled", false)
                    }.getOrDefault(false),
                )
            }
            return LegacyTranslationSnapshot(
                configurations,
                credentials,
                hasConfiguredKeylessSettings = hasConfiguredKeyless,
            )
        } catch (error: Exception) {
            credentials.forEach { it.secret.close() }
            // Parse/validation exception causes can contain the raw preference JSON.
            throw IllegalStateException("Legacy translation settings need review")
        }
    }

    override suspend fun compareAndRemoveVerified(values: List<VerifiedLegacyCredential>): LegacyRemovalResult {
        values.forEach { proof ->
            LegacyCredentialReferences.remember(
                tachiyomi.domain.translation.profile.LegacyCredentialReference(
                    proof.provider.name,
                    proof.scope,
                    proof.scopeId,
                    proof.credentialId,
                ),
            )
        }
        return preferences.compareAndRemoveLegacyCredentials(values)
    }

    private companion object {
        const val KEYLESS_MIGRATED_PREF_KEY = "novel_reader_translation_keyless_migrated"

        val KEYLESS_CONFIGURATION_KEYS = setOf(
            "novel_reader_translation_provider",
            "novel_reader_gemini_enabled",
            "novel_reader_gemini_model",
            "novel_reader_gemini_reasoning_effort",
            "novel_reader_gemini_budget_tokens",
            "novel_reader_gemini_temperature",
            "novel_reader_gemini_top_p",
            "novel_reader_gemini_top_k",
            "novel_reader_gemini_concurrency",
            "novel_reader_gemini_source_lang",
            "novel_reader_gemini_target_lang",
            "novel_reader_gemini_prompt_mode",
            "novel_reader_gemini_enabled_prompt_modifiers",
            "novel_reader_gemini_custom_prompt_modifier",
            "novel_reader_gemini_style_preset",
            "novel_reader_gemini_prompt_modifiers",
            "novel_reader_openrouter_base_url",
            "novel_reader_openrouter_model",
            "novel_reader_deepseek_base_url",
            "novel_reader_deepseek_model",
            "novel_reader_mistral_base_url",
            "novel_reader_mistral_model",
            "novel_reader_nvidia_base_url",
            "novel_reader_nvidia_model",
            "novel_reader_ollama_cloud_base_url",
            "novel_reader_ollama_cloud_model",
        )

        val SOURCE_TRANSLATION_OVERRIDE_FIELDS = setOf(
            "translationProvider",
            "geminiEnabled",
            "geminiModel",
            "geminiSourceLang",
            "geminiTargetLang",
            "geminiPromptMode",
            "geminiStylePreset",
            "geminiEnabledPromptModifiers",
            "geminiCustomPromptModifier",
            "geminiPromptModifiers",
            "openRouterBaseUrl",
            "openRouterModel",
            "deepSeekBaseUrl",
            "deepSeekModel",
            "mistralBaseUrl",
            "mistralModel",
            "nvidiaBaseUrl",
            "nvidiaModel",
            "ollamaCloudBaseUrl",
            "ollamaCloudModel",
        )

        fun hasLegacyTranslationOverride(source: JsonObject): Boolean =
            SOURCE_TRANSLATION_OVERRIDE_FIELDS.any { field ->
                val value = source[field]
                value != null && value != JsonNull
            }
    }
}

private class LegacySettingsFields(private val globals: Map<String, *>, private val source: JsonObject) {
    fun safeConfiguration(provider: LegacyProvider): LegacyProviderConfiguration = try {
        configuration(provider)
    } catch (_: Exception) {
        LegacyProviderConfiguration(
            provider = provider,
            baseUrl = defaultEndpoint(provider),
            protocol = protocolFor(provider),
            auth = authFor(provider),
            presetFlavor = flavorFor(provider),
            model = null,
            languages = TranslationLanguages("English", "Russian"),
            issue = LegacyConfigurationIssue.INVALID_SETTING,
        )
    }

    fun configuration(provider: LegacyProvider): LegacyProviderConfiguration {
        val modelId = modelId(provider).trim()
        val generation = if (modelId.isBlank()) null else selection(provider, modelId)
        val mode = string("geminiPromptMode", "gemini_prompt_mode", "ADULT_18").trim()
        val style = string("geminiStylePreset", "gemini_style_preset", "PROFESSIONAL").trim()
        val issue = if (GeminiPromptMode.entries.none { it.name == mode } ||
            NovelTranslationStylePreset.entries.none { it.name == style }
        ) {
            LegacyConfigurationIssue.UNSUPPORTED_SETTING
        } else {
            null
        }
        val baseUrl = endpoint(provider)
        val sourceLanguage = string("geminiSourceLang", "gemini_source_lang", "English")
            .trim()
            .ifBlank { "English" }
            .take(80)
        val targetLanguage = string("geminiTargetLang", "gemini_target_lang", "Russian")
            .trim()
            .ifBlank { "Russian" }
            .take(80)
        val combinedCustomPrompt = listOf(
            string("geminiCustomPromptModifier", "gemini_custom_prompt_modifier", ""),
            string("geminiPromptModifiers", "gemini_prompt_modifiers", ""),
        ).filter { it.isNotBlank() }.joinToString("\n\n").take(20_000)
        return LegacyProviderConfiguration(
            provider = provider,
            baseUrl = baseUrl,
            protocol = protocolFor(provider),
            auth = authFor(provider),
            presetFlavor = flavorFor(provider),
            model = generation,
            languages = TranslationLanguages(sourceLanguage, targetLanguage),
            prompt = PromptSettings(
                mode.ifBlank { "INVALID" }.take(80),
                style.ifBlank { "INVALID" }.take(80),
                modifiers(),
                combinedCustomPrompt,
            ),
            allowInsecureLocalHTTP = ProfileEndpointPolicy.isLocalEndpoint(baseUrl),
            maxConcurrency = integer("geminiConcurrency", "gemini_concurrency", 2).coerceIn(1, 8),
            issue = issue,
        )
    }

    private fun protocolFor(provider: LegacyProvider): TranslationProtocol = when (provider) {
        LegacyProvider.GEMINI, LegacyProvider.GEMINI_PRIVATE -> TranslationProtocol.GEMINI_GENERATE
        LegacyProvider.OLLAMA_CLOUD -> TranslationProtocol.OLLAMA_NATIVE
        else -> TranslationProtocol.OPENAI_CHAT
    }

    private fun authFor(provider: LegacyProvider): ProfileAuth =
        if (provider == LegacyProvider.GEMINI || provider == LegacyProvider.GEMINI_PRIVATE) {
            ProfileAuth.X_GOOG_API_KEY
        } else {
            ProfileAuth.BEARER
        }

    private fun flavorFor(provider: LegacyProvider): PresetFlavor = when (provider) {
        LegacyProvider.GEMINI -> PresetFlavor.NONE
        LegacyProvider.GEMINI_PRIVATE -> PresetFlavor.GEMINI_PRIVATE
        LegacyProvider.OPENROUTER -> PresetFlavor.OPENROUTER
        LegacyProvider.DEEPSEEK -> PresetFlavor.DEEPSEEK
        LegacyProvider.MISTRAL -> PresetFlavor.MISTRAL
        LegacyProvider.NVIDIA -> PresetFlavor.NVIDIA
        LegacyProvider.OLLAMA_CLOUD -> PresetFlavor.OLLAMA
    }

    private fun selection(provider: LegacyProvider, modelId: String): ModelSelection {
        val temperature = finite(number("geminiTemperature", "gemini_temperature", 0.7), 0.7)
        val topP = finite(number("geminiTopP", "gemini_top_p", 0.95), 0.95)
        val effort = string("geminiReasoningEffort", "gemini_reasoning_effort", "minimal")
        if (provider == LegacyProvider.GEMINI || provider == LegacyProvider.GEMINI_PRIVATE) {
            val normalizedModelId = modelId.normalizeGeminiModelId()
            val caps = resolveGeminiModelCapabilities(normalizedModelId)
            val metadata = GeminiModelMetadataStore.lookup(normalizedModelId)
            val budget = integer("geminiBudgetTokens", "gemini_budget_tokens", 8192)
            val thinking = caps.thinkingConfig(effort, budget)
            val contract = when (val value = caps.thinking) {
                GeminiThinkingContract.Unknown -> ReasoningContract.Unknown
                GeminiThinkingContract.Unsupported -> ReasoningContract.None
                GeminiThinkingContract.Toggle -> ReasoningContract.Toggle
                is GeminiThinkingContract.Levels -> ReasoningContract.Levels(value.options)
                is GeminiThinkingContract.TokenBudget -> ReasoningContract.Budget(
                    value.minimum,
                    value.maximum,
                    value.canDisable,
                    value.canUseDynamic,
                )
            }
            val choice = when {
                thinking == null -> ReasoningChoice.ApiDefault
                contract is ReasoningContract.Budget -> when {
                    contract.accepts(budget) -> ReasoningChoice.Budget(budget)
                    budget > 0 -> ReasoningChoice.Budget(budget.coerceIn(contract.minimum, contract.maximum))
                    else -> ReasoningChoice.ApiDefault
                }
                contract == ReasoningContract.Toggle ->
                    ReasoningChoice.Toggle(effort.trim().equals("high", ignoreCase = true))
                contract is ReasoningContract.Levels -> {
                    val normalizedEffort = effort.trim().lowercase()
                    if (normalizedEffort in contract.options) {
                        ReasoningChoice.Level(normalizedEffort)
                    } else {
                        ReasoningChoice.ApiDefault
                    }
                }
                else -> ReasoningChoice.ApiDefault
            }
            val known = caps.thinking != GeminiThinkingContract.Unknown || metadata != null
            return ModelSelection(
                normalizedModelId,
                contract,
                GenerationSettings(
                    temperature = if (known) {
                        temperature.coerceIn(
                            0.0,
                            (caps.maxTemperature ?: 2f).toDouble(),
                        )
                    } else {
                        null
                    },
                    topP = if (known) topP.coerceIn(0.0, 1.0) else null,
                    topK = if (caps.topK != null) integer("geminiTopK", "gemini_top_k", 40).coerceAtLeast(0) else null,
                    maxOutputTokens = caps.maxOutputTokens(),
                    reasoning = choice,
                ),
                OutputTokenField.MAX_OUTPUT_TOKENS,
            )
        }
        val native = NovelTranslationProvider.valueOf(provider.name)
        val options = resolveTranslationReasoningOptions(native, modelId)
        val effortChoice = normalizeTranslationReasoningEffort(native, modelId, effort)
        val contract = if (options.isEmpty()) ReasoningContract.None else ReasoningContract.Levels(options)
        return ModelSelection(
            modelId,
            contract,
            GenerationSettings(
                temperature = if (provider == LegacyProvider.DEEPSEEK) {
                    temperature.coerceIn(1.3, 1.5)
                } else {
                    temperature.coerceIn(0.0, 2.0)
                },
                topP = if (provider == LegacyProvider.DEEPSEEK) {
                    topP.coerceIn(0.9, 0.95)
                } else {
                    topP.coerceIn(0.0, 1.0)
                },
                reasoning = effortChoice?.let(ReasoningChoice::Level) ?: ReasoningChoice.ApiDefault,
            ),
        )
    }

    private fun modelId(provider: LegacyProvider): String = when (provider) {
        LegacyProvider.GEMINI, LegacyProvider.GEMINI_PRIVATE ->
            string("geminiModel", "gemini_model", "gemini-3.1-flash-lite-preview")
        LegacyProvider.OPENROUTER -> string("openRouterModel", "openrouter_model", "")
        LegacyProvider.DEEPSEEK -> string("deepSeekModel", "deepseek_model", "deepseek-chat")
        LegacyProvider.MISTRAL -> string("mistralModel", "mistral_model", "mistral-large-latest")
        LegacyProvider.NVIDIA -> string("nvidiaModel", "nvidia_model", "")
        LegacyProvider.OLLAMA_CLOUD -> string("ollamaCloudModel", "ollama_cloud_model", "gpt-oss:120b")
    }

    private fun defaultEndpoint(provider: LegacyProvider): String = when (provider) {
        LegacyProvider.GEMINI, LegacyProvider.GEMINI_PRIVATE -> "https://generativelanguage.googleapis.com/v1beta"
        LegacyProvider.OPENROUTER -> "https://openrouter.ai/api/v1"
        LegacyProvider.DEEPSEEK -> "https://api.deepseek.com"
        LegacyProvider.MISTRAL -> "https://api.mistral.ai/v1"
        LegacyProvider.NVIDIA -> "https://integrate.api.nvidia.com/v1"
        LegacyProvider.OLLAMA_CLOUD -> "https://ollama.com/api"
    }

    private fun endpoint(provider: LegacyProvider): String = when (provider) {
        LegacyProvider.GEMINI, LegacyProvider.GEMINI_PRIVATE -> defaultEndpoint(provider)
        LegacyProvider.OPENROUTER -> string(
            "openRouterBaseUrl",
            "openrouter_base_url",
            "https://openrouter.ai/api/v1",
        ).trim().ifBlank { "https://openrouter.ai/api/v1" }.trimEnd('/')
            .let { if (it.endsWith("/v1", true)) it else "$it/api/v1" }
        LegacyProvider.DEEPSEEK -> string("deepSeekBaseUrl", "deepseek_base_url", "https://api.deepseek.com")
            .trim().ifBlank { "https://api.deepseek.com" }.trimEnd('/')
        LegacyProvider.MISTRAL -> normalizeMistralBaseUrl(
            string("mistralBaseUrl", "mistral_base_url", "https://api.mistral.ai/v1"),
        )
        LegacyProvider.NVIDIA -> normalizeNvidiaBaseUrl(
            string("nvidiaBaseUrl", "nvidia_base_url", "https://integrate.api.nvidia.com/v1"),
        )
        LegacyProvider.OLLAMA_CLOUD -> normalizeOllamaCloudBaseUrl(
            string("ollamaCloudBaseUrl", "ollama_cloud_base_url", "https://ollama.com/api"),
        )
    }

    private fun finite(value: Double, fallback: Double): Double = if (value.isFinite()) value else fallback

    private fun element(field: String, suffix: String): JsonElement? {
        source[field]?.takeIf { it != JsonNull }?.let { return it }
        return when (val raw = globals["novel_reader_$suffix"]) {
            null -> null
            is String -> JsonPrimitive(raw)
            is Boolean -> JsonPrimitive(raw)
            is Number -> JsonPrimitive(raw)
            else -> invalidLegacySettings()
        }
    }
    fun string(field: String, suffix: String, fallback: String): String =
        element(field, suffix)?.let {
            (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content
                ?: invalidLegacySettings()
        } ?: fallback
    fun boolean(field: String, suffix: String, fallback: Boolean): Boolean =
        element(field, suffix)?.let { (it as? JsonPrimitive)?.booleanOrNull ?: invalidLegacySettings() } ?: fallback
    private fun integer(field: String, suffix: String, fallback: Int): Int =
        element(field, suffix)?.let { (it as? JsonPrimitive)?.intOrNull ?: invalidLegacySettings() } ?: fallback
    private fun number(field: String, suffix: String, fallback: Double): Double =
        element(field, suffix)?.let { (it as? JsonPrimitive)?.doubleOrNull ?: invalidLegacySettings() } ?: fallback
    private fun modifiers(): List<String> {
        val raw = source["geminiEnabledPromptModifiers"]?.takeIf { it != JsonNull }
            ?: globals["novel_reader_gemini_enabled_prompt_modifiers"]?.let {
                if (it !is String) invalidLegacySettings()
                try {
                    Json.parseToJsonElement(it)
                } catch (_: Exception) {
                    invalidLegacySettings()
                }
            } ?: return emptyList()
        return (raw as? JsonArray)?.mapNotNull { element ->
            val token = (element as? JsonPrimitive)?.takeIf { value -> value.isString }?.content
                ?: invalidLegacySettings()
            token.trim().takeIf { it.isNotEmpty() && it.length <= 100 }
        }?.distinct()?.take(100) ?: invalidLegacySettings()
    }
}

private fun invalidLegacySettings(): Nothing = throw IllegalStateException("Invalid legacy translation setting")
