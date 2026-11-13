package eu.kanade.tachiyomi.ui.player.subtitle.translation

import eu.kanade.tachiyomi.data.translation.TranslationQueueProfileSnapshot
import eu.kanade.tachiyomi.data.translation.profile.TranslationProfileServices
import eu.kanade.tachiyomi.data.translation.profile.withTranslationProfile
import eu.kanade.tachiyomi.data.translation.toTranslationQueueProfileSnapshot
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPromptModifiers
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.MistralTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.MistralTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelTranslationStylePresets
import eu.kanade.tachiyomi.ui.reader.novel.translation.NvidiaTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.NvidiaTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OllamaCloudTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.OllamaCloudTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.normalizeDeepSeekBaseUrl
import eu.kanade.tachiyomi.ui.reader.novel.translation.normalizeGeminiBaseUrl
import eu.kanade.tachiyomi.ui.reader.novel.translation.normalizeMistralBaseUrl
import eu.kanade.tachiyomi.ui.reader.novel.translation.normalizeNvidiaBaseUrl
import eu.kanade.tachiyomi.ui.reader.novel.translation.normalizeOllamaCloudBaseUrl
import eu.kanade.tachiyomi.ui.reader.novel.translation.normalizeOpenRouterBaseUrl
import eu.kanade.tachiyomi.ui.reader.novel.translation.normalizeTranslationReasoningEffort
import eu.kanade.tachiyomi.ui.reader.novel.translation.profileApiRootUrl
import eu.kanade.tachiyomi.ui.reader.novel.translation.resolveNovelTranslationPromptFamily
import kotlinx.coroutines.CancellationException
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.ProfileAssignmentScope
import tachiyomi.domain.translation.profile.ProfileCatalog
import tachiyomi.domain.translation.profile.TranslationLanguages
import tachiyomi.domain.translation.profile.TranslationProfile

class NovelReaderAiSubtitleTranslationClient(
    private val preferences: NovelReaderPreferences,
    private val geminiTranslationService: GeminiTranslationService,
    private val openRouterTranslationService: OpenRouterTranslationService,
    private val deepSeekTranslationService: DeepSeekTranslationService,
    private val mistralTranslationService: MistralTranslationService,
    private val nvidiaTranslationService: NvidiaTranslationService,
    private val ollamaCloudTranslationService: OllamaCloudTranslationService,
    private val activeGlobalProfileResolver: () -> Pair<TranslationProfile, ConnectionProfile>? = {
        resolveActiveGlobalProfile(TranslationProfileServices.cachedCatalog)
    },
    private val activeGlobalProfileSuspendResolver: suspend () -> Pair<TranslationProfile, ConnectionProfile>? = {
        resolveActiveGlobalProfile(
            TranslationProfileServices.cachedCatalog ?: runCatchingNonCancellation {
                TranslationProfileServices.repository.getCatalog()
            },
        )
    },
    private val legacyCredentialResolver: suspend (NovelTranslationProvider) -> String? = { provider ->
        runCatchingNonCancellation {
            TranslationProfileServices.legacyCredentials.resolveLegacyOrNull(provider)?.use { it.reveal() }
        }
    },
    private val profileTranslator: suspend (
        TranslationQueueProfileSnapshot,
        List<String>,
    ) -> List<String?> = { snapshot, segments ->
        TranslationProfileServices.translateConfigured(snapshot, segments)
    },
) : AiSubtitleTranslationClient {
    override val fingerprint: String
        get() {
            activeGlobalProfileResolver()?.let { (profile, connection) ->
                val endpoint = runCatching { profileApiRootUrl(connection).toString() }
                    .getOrDefault(connection.baseUrl.trim().trimEnd('/'))
                val generation = profile.model.generation
                val promptHash = listOf(
                    profile.prompt.modeId,
                    profile.prompt.styleId,
                    profile.prompt.modifierIds.joinToString(","),
                    profile.prompt.customText.trim(),
                ).joinToString("|").hashCode()
                return "novel-reader-ai-profile-${profile.id}-${connection.id}-${connection.protocol.name}-" +
                    "$endpoint-${profile.model.exactModelId}-${profile.model.outputTokenField.name}-" +
                    "${generation.reasoning}-t${generation.temperature}-p${generation.topP}-k${generation.topK}-" +
                    "m${generation.maxOutputTokens}-$promptHash-v3"
            }
            val provider = preferences.translationProvider().get()
            val (model, baseUrl) = when (provider) {
                NovelTranslationProvider.GEMINI,
                NovelTranslationProvider.GEMINI_PRIVATE,
                -> preferences.geminiModel().get() to
                    normalizeGeminiBaseUrl("https://generativelanguage.googleapis.com")
                NovelTranslationProvider.OPENROUTER ->
                    preferences.openRouterModel().get() to
                        normalizeOpenRouterBaseUrl(preferences.openRouterBaseUrl().get())
                NovelTranslationProvider.DEEPSEEK ->
                    preferences.deepSeekModel().get() to normalizeDeepSeekBaseUrl(preferences.deepSeekBaseUrl().get())
                NovelTranslationProvider.MISTRAL ->
                    preferences.mistralModel().get() to normalizeMistralBaseUrl(preferences.mistralBaseUrl().get())
                NovelTranslationProvider.NVIDIA ->
                    preferences.nvidiaModel().get() to normalizeNvidiaBaseUrl(preferences.nvidiaBaseUrl().get())
                NovelTranslationProvider.OLLAMA_CLOUD ->
                    preferences.ollamaCloudModel().get() to
                        normalizeOllamaCloudBaseUrl(preferences.ollamaCloudBaseUrl().get())
            }
            val temperature = preferences.geminiTemperature().get()
            val topP = preferences.geminiTopP().get()
            val topK = preferences.geminiTopK().get()
            val promptMode = preferences.geminiPromptMode().get()
            val reasoning = normalizeTranslationReasoningEffort(
                provider,
                model,
                preferences.geminiReasoningEffort().get(),
            ) ?: ""
            val budget = if (
                provider == NovelTranslationProvider.GEMINI ||
                provider == NovelTranslationProvider.GEMINI_PRIVATE
            ) {
                preferences.geminiBudgetTokens().get()
            } else {
                0
            }
            val modifiersHash = preferences.geminiPromptModifiers().get().trim().hashCode()
            val privateMode = if (provider == NovelTranslationProvider.GEMINI_PRIVATE) {
                "-priv${preferences.geminiPrivateUnlocked().get()}-${preferences.geminiPrivatePythonLikeMode().get()}"
            } else {
                ""
            }
            return "novel-reader-ai-${provider.name.lowercase()}-$baseUrl-$model-$reasoning-b$budget-" +
                "t$temperature-p$topP-k$topK-$promptMode-$modifiersHash$privateMode-v3"
        }

    override suspend fun translateIndexedSegments(
        prompt: String,
        segments: List<IndexedSubtitleSegment>,
        sourceLanguage: String,
        targetLanguage: String,
    ): String {
        if (segments.isEmpty()) return ""
        val sourceTexts = segments.map { it.text }
        val activeProfile = activeGlobalProfileSuspendResolver() ?: activeGlobalProfileResolver()
        val translated = if (activeProfile != null) {
            val (profile, connection) = activeProfile
            val effectiveSource = sourceLanguage.ifBlank { profile.languages.source }
            val effectiveTarget = targetLanguage.ifBlank { profile.languages.target }
            val subtitleProfile = profile.copy(languages = TranslationLanguages(effectiveSource, effectiveTarget))
            val systemPrompt = buildProfileSubtitleSystemPrompt(
                profile = subtitleProfile,
                subtitleContextPrompt = prompt,
                targetLanguage = effectiveTarget,
            )
            val snapshot = preferences.resolveSettings(null)
                .withTranslationProfile(subtitleProfile, connection)
                .toTranslationQueueProfileSnapshot()
                .copy(
                    systemPromptSnapshot = systemPrompt,
                    geminiRequestShape = null,
                )
            profileTranslator(snapshot, sourceTexts)
        } else {
            val provider = preferences.translationProvider().get()
            val apiKey = resolveApiKey(provider)
            if (apiKey.isBlank()) {
                throw IllegalStateException("AI subtitle translation API key is not configured")
            }
            val rawReasoning = preferences.geminiReasoningEffort().get()
            val modelId = when (provider) {
                NovelTranslationProvider.GEMINI,
                NovelTranslationProvider.GEMINI_PRIVATE,
                -> preferences.geminiModel().get()
                NovelTranslationProvider.OPENROUTER -> preferences.openRouterModel().get()
                NovelTranslationProvider.DEEPSEEK -> preferences.deepSeekModel().get()
                NovelTranslationProvider.MISTRAL -> preferences.mistralModel().get()
                NovelTranslationProvider.NVIDIA -> preferences.nvidiaModel().get()
                NovelTranslationProvider.OLLAMA_CLOUD -> preferences.ollamaCloudModel().get()
            }
            val normalizedReasoning = normalizeTranslationReasoningEffort(provider, modelId, rawReasoning).orEmpty()
            val resolvedSource = sourceLanguage.ifBlank { preferences.geminiSourceLang().get() }
            val resolvedTarget = targetLanguage.ifBlank { preferences.geminiTargetLang().get() }
            val promptMode = preferences.geminiPromptMode().get()
            val modifiers = subtitlePromptModifiers(preferences.geminiPromptModifiers().get())
            val temperature = preferences.geminiTemperature().get()
            val topP = preferences.geminiTopP().get()
            when (provider) {
                NovelTranslationProvider.GEMINI,
                NovelTranslationProvider.GEMINI_PRIVATE,
                -> geminiTranslationService.translateBatch(
                    segments = sourceTexts,
                    params = GeminiTranslationParams(
                        apiKey = apiKey,
                        model = modelId,
                        sourceLang = resolvedSource,
                        targetLang = resolvedTarget,
                        reasoningEffort = normalizedReasoning,
                        budgetTokens = preferences.geminiBudgetTokens().get(),
                        temperature = temperature,
                        topP = topP,
                        topK = preferences.geminiTopK().get(),
                        promptMode = promptMode,
                        promptModifiers = modifiers,
                        provider = provider,
                        privateUnlocked = preferences.geminiPrivateUnlocked().get(),
                        privatePythonLikeMode = preferences.geminiPrivatePythonLikeMode().get(),
                    ),
                )

                NovelTranslationProvider.OPENROUTER -> openRouterTranslationService.translateBatch(
                    segments = sourceTexts,
                    params = OpenRouterTranslationParams(
                        baseUrl = preferences.openRouterBaseUrl().get(),
                        apiKey = apiKey,
                        model = modelId,
                        sourceLang = resolvedSource,
                        targetLang = resolvedTarget,
                        promptMode = promptMode,
                        promptModifiers = modifiers,
                        temperature = temperature,
                        topP = topP,
                        reasoningEffort = normalizedReasoning,
                    ),
                )

                NovelTranslationProvider.DEEPSEEK -> deepSeekTranslationService.translateBatch(
                    segments = sourceTexts,
                    params = DeepSeekTranslationParams(
                        baseUrl = preferences.deepSeekBaseUrl().get(),
                        apiKey = apiKey,
                        model = modelId,
                        sourceLang = resolvedSource,
                        targetLang = resolvedTarget,
                        promptMode = promptMode,
                        promptModifiers = modifiers,
                        temperature = temperature,
                        topP = topP,
                        reasoningEffort = normalizedReasoning,
                    ),
                )

                NovelTranslationProvider.MISTRAL -> mistralTranslationService.translateBatch(
                    segments = sourceTexts,
                    params = MistralTranslationParams(
                        baseUrl = preferences.mistralBaseUrl().get(),
                        apiKey = apiKey,
                        model = modelId,
                        sourceLang = resolvedSource,
                        targetLang = resolvedTarget,
                        promptMode = promptMode,
                        promptModifiers = modifiers,
                        temperature = temperature,
                        topP = topP,
                        reasoningEffort = normalizedReasoning,
                    ),
                )

                NovelTranslationProvider.NVIDIA -> nvidiaTranslationService.translateBatch(
                    segments = sourceTexts,
                    params = NvidiaTranslationParams(
                        baseUrl = preferences.nvidiaBaseUrl().get(),
                        apiKey = apiKey,
                        model = modelId,
                        sourceLang = resolvedSource,
                        targetLang = resolvedTarget,
                        promptMode = promptMode,
                        promptModifiers = modifiers,
                        temperature = temperature,
                        topP = topP,
                    ),
                )

                NovelTranslationProvider.OLLAMA_CLOUD -> ollamaCloudTranslationService.translateBatch(
                    segments = sourceTexts,
                    params = OllamaCloudTranslationParams(
                        baseUrl = preferences.ollamaCloudBaseUrl().get(),
                        apiKey = apiKey,
                        model = modelId,
                        sourceLang = resolvedSource,
                        targetLang = resolvedTarget,
                        promptMode = promptMode,
                        promptModifiers = modifiers,
                        temperature = temperature,
                        topP = topP,
                        reasoningEffort = normalizedReasoning,
                    ),
                )
            }
        } ?: throw IllegalStateException("AI subtitle translation failed or provider is not configured")

        return translated.mapIndexedNotNull { position, value ->
            val originalIndex = segments.getOrNull(position)?.index ?: return@mapIndexedNotNull null
            val safe = value?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            "<s i=\"$originalIndex\">${escapeXmlText(safe)}</s>"
        }.joinToString("\n")
    }

    /** Subtitles have no novel scope, so the global vault slot wins over any remaining plaintext. */
    private suspend fun resolveApiKey(provider: NovelTranslationProvider): String {
        legacyCredentialResolver(provider)?.takeIf { it.isNotBlank() }?.let { return it }
        return when (provider) {
            NovelTranslationProvider.GEMINI, NovelTranslationProvider.GEMINI_PRIVATE -> preferences.geminiApiKey().get()
            NovelTranslationProvider.OPENROUTER -> preferences.openRouterApiKey().get()
            NovelTranslationProvider.DEEPSEEK -> preferences.deepSeekApiKey().get()
            NovelTranslationProvider.MISTRAL -> preferences.mistralApiKey().get()
            NovelTranslationProvider.NVIDIA -> preferences.nvidiaApiKey().get()
            NovelTranslationProvider.OLLAMA_CLOUD -> preferences.ollamaCloudApiKey().get()
        }
    }

    private fun buildProfileSubtitleSystemPrompt(
        profile: TranslationProfile,
        subtitleContextPrompt: String,
        targetLanguage: String,
    ): String {
        val family = resolveNovelTranslationPromptFamily(targetLanguage)
        val style = NovelTranslationStylePreset.entries.firstOrNull { it.name == profile.prompt.styleId }
            ?: NovelTranslationStylePreset.PROFESSIONAL
        val styleDirective = NovelTranslationStylePresets.promptDirective(style, family).trim()
        val modifiers = GeminiPromptModifiers.buildPromptText(
            enabledIds = profile.prompt.modifierIds,
            customModifier = subtitlePromptModifiers(profile.prompt.customText),
            family = family,
        )
        return listOf(subtitleContextPrompt.trim(), styleDirective, modifiers)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    private fun subtitlePromptModifiers(userModifiers: String): String {
        val subtitleModifiers = """
            Subtitle mode: translate each segment as a concise subtitle cue.
            Do not add explanations, notes, speaker labels, markdown, or extra text.
            Do not merge, split, reorder, omit, or renumber segments.
            Preserve placeholders, line breaks when useful, and protected styling markers.
        """.trimIndent()
        return listOf(userModifiers.trim(), subtitleModifiers)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    private fun escapeXmlText(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}

private fun resolveActiveGlobalProfile(
    catalog: ProfileCatalog?,
): Pair<TranslationProfile, ConnectionProfile>? {
    if (catalog == null) return null
    val profileId = catalog.assignments
        .singleOrNull { it.scope == ProfileAssignmentScope.GLOBAL && it.scopeId == 0L }
        ?.profileId
        ?: return null
    val profile = catalog.profiles.singleOrNull { it.id == profileId } ?: return null
    val connection = catalog.connections.singleOrNull { it.id == profile.connectionId } ?: return null
    return profile to connection
}

private inline fun <T> runCatchingNonCancellation(block: () -> T): T? {
    return try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        null
    }
}
