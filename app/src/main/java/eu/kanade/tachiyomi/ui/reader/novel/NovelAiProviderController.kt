package eu.kanade.tachiyomi.ui.reader.novel

import android.app.Application
import eu.kanade.presentation.more.settings.translation.AiTranslationModelUi
import eu.kanade.tachiyomi.data.translation.profile.TranslationProfileServices
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderOverride
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiModelEntry
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiModelMetadataStore
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.MistralModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.NvidiaModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OllamaCloudModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.formatAiTranslationThrowableForLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.translation.credentials.CredentialSecret
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.PresetFlavor
import tachiyomi.domain.translation.profile.ReasoningContract
import tachiyomi.domain.translation.profile.TranslationProtocol
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Host the AI provider controller uses to reach the shared reader state owned by
 * [NovelReaderScreenModel].
 */
internal interface NovelAiProviderHost {
    val providerScope: CoroutineScope

    fun providerCurrentNovel(): Novel?
    fun providerReaderSettings(): NovelReaderSettings?
    fun providerUpdateContent(settings: NovelReaderSettings)
    fun providerAddLog(message: String)
    suspend fun providerRequestTranslationBatch(
        segments: List<String>,
        settings: NovelReaderSettings,
        onMessage: (String) -> Unit,
    ): List<String?>?
    fun providerApplyAiProvidersState(state: NovelAiProviderState)
    fun providerHasConfiguredTranslationProvider(settings: NovelReaderSettings): Boolean
}

/**
 * Snapshot of the AI provider UI state (model lists and connection-test statuses), merged into the
 * reader state by the screen model.
 */
data class NovelAiProviderState(
    val geminiModelEntries: List<GeminiModelEntry> = emptyList(),
    val isGeminiModelsLoading: Boolean = false,
    val openRouterModelIds: List<String> = emptyList(),
    val isOpenRouterModelsLoading: Boolean = false,
    val isTestingOpenRouterConnection: Boolean = false,
    val openRouterApiTestStatus: ProviderApiTestStatus = ProviderApiTestStatus.Idle,
    val openRouterApiTestMessage: String? = null,
    val deepSeekModelIds: List<String> = emptyList(),
    val isDeepSeekModelsLoading: Boolean = false,
    val isTestingDeepSeekConnection: Boolean = false,
    val deepSeekApiTestStatus: ProviderApiTestStatus = ProviderApiTestStatus.Idle,
    val deepSeekApiTestMessage: String? = null,
    val mistralModelIds: List<String> = emptyList(),
    val isMistralModelsLoading: Boolean = false,
    val isTestingMistralConnection: Boolean = false,
    val mistralApiTestStatus: ProviderApiTestStatus = ProviderApiTestStatus.Idle,
    val mistralApiTestMessage: String? = null,
    val nvidiaModelIds: List<String> = emptyList(),
    val isNvidiaModelsLoading: Boolean = false,
    val isTestingNvidiaConnection: Boolean = false,
    val nvidiaApiTestStatus: ProviderApiTestStatus = ProviderApiTestStatus.Idle,
    val nvidiaApiTestMessage: String? = null,
    val ollamaCloudModelIds: List<String> = emptyList(),
    val isOllamaCloudModelsLoading: Boolean = false,
    val isTestingOllamaCloudConnection: Boolean = false,
    val ollamaCloudApiTestStatus: ProviderApiTestStatus = ProviderApiTestStatus.Idle,
    val ollamaCloudApiTestMessage: String? = null,
)

/**
 * AI translation providers subsystem (Gemini model list, OpenRouter / DeepSeek / Mistral / NVIDIA /
 * Ollama Cloud):
 * model-list refresh, connection tests and the provider settings that feed the shared
 * translation pipeline. Gemini and Google whole-chapter translation stay in the screen model.
 */
internal class NovelAiProviderController(
    private val host: NovelAiProviderHost,
    private val application: Application = Injekt.get(),
    private val novelReaderPreferences: NovelReaderPreferences = Injekt.get(),
    private val geminiModelsService: GeminiModelsService,
    private val openRouterModelsService: OpenRouterModelsService,
    private val deepSeekModelsService: DeepSeekModelsService,
    private val mistralModelsService: MistralModelsService,
    private val nvidiaModelsService: NvidiaModelsService,
    private val ollamaCloudModelsService: OllamaCloudModelsService,
    private val fetchProfileModels: suspend (ConnectionProfile) -> List<AiTranslationModelUi> = {
        TranslationProfileServices.models.models(it)
    },
) {

    private var state: NovelAiProviderState = NovelAiProviderState()
    private var geminiModelsRefreshGeneration = 0L
    private var geminiModelsRefreshJob: Job? = null
    private var profileModelsRefreshGeneration = 0L
    private var profileModelsRefreshJob: Job? = null

    /** Snapshot of the AI provider UI state, merged into the reader state by the screen model. */
    fun snapshot(): NovelAiProviderState = state

    /** Restores provider state from the reader's current State (used after the screen model builds one). */
    fun restoreFromReaderState(providerState: NovelAiProviderState) {
        state = providerState
    }

    private fun updateState(transform: (NovelAiProviderState) -> NovelAiProviderState) {
        state = transform(state)
        host.providerApplyAiProvidersState(state)
    }

    // ---------------------------------------------------------------------------------------------
    // Provider settings
    // ---------------------------------------------------------------------------------------------

    fun setOpenRouterBaseUrl(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.openRouterBaseUrl().set(value) },
        setOverride = { it.copy(openRouterBaseUrl = value) },
    )

    fun setOpenRouterApiKey(value: String) {
        saveProviderCredential(NovelTranslationProvider.OPENROUTER, value)
    }

    fun setOpenRouterModel(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.openRouterModel().set(value) },
        setOverride = { it.copy(openRouterModel = value) },
    )

    fun setDeepSeekBaseUrl(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.deepSeekBaseUrl().set(value) },
        setOverride = { it.copy(deepSeekBaseUrl = value) },
    )

    fun setDeepSeekApiKey(value: String) {
        saveProviderCredential(NovelTranslationProvider.DEEPSEEK, value)
    }

    fun setDeepSeekModel(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.deepSeekModel().set(value) },
        setOverride = { it.copy(deepSeekModel = value) },
    )

    fun setMistralBaseUrl(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.mistralBaseUrl().set(value) },
        setOverride = { it.copy(mistralBaseUrl = value) },
    )

    fun setMistralApiKey(value: String) {
        saveProviderCredential(NovelTranslationProvider.MISTRAL, value)
    }

    fun setMistralModel(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.mistralModel().set(value) },
        setOverride = { it.copy(mistralModel = value) },
    )

    fun setNvidiaBaseUrl(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.nvidiaBaseUrl().set(value) },
        setOverride = { it.copy(nvidiaBaseUrl = value) },
    )

    fun setNvidiaApiKey(value: String) {
        saveProviderCredential(NovelTranslationProvider.NVIDIA, value)
    }

    fun setNvidiaModel(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.nvidiaModel().set(value) },
        setOverride = { it.copy(nvidiaModel = value) },
    )

    fun setOllamaCloudBaseUrl(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.ollamaCloudBaseUrl().set(value) },
        setOverride = { it.copy(ollamaCloudBaseUrl = value) },
    )

    fun setOllamaCloudApiKey(value: String) {
        saveProviderCredential(NovelTranslationProvider.OLLAMA_CLOUD, value)
    }

    fun setOllamaCloudModel(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.ollamaCloudModel().set(value) },
        setOverride = { it.copy(ollamaCloudModel = value) },
    )

    private fun saveProviderCredential(provider: NovelTranslationProvider, value: String) {
        val novel = host.providerCurrentNovel() ?: return
        val sourceId = novel.source.takeIf { novelReaderPreferences.getSourceOverride(it) != null }
        resetAllApiTestStates()
        host.providerScope.launch {
            try {
                TranslationProfileServices.legacyCredentials.saveLegacy(provider, sourceId, value)
                if (host.providerCurrentNovel()?.source == novel.source) {
                    host.providerUpdateContent(novelReaderPreferences.resolveSettings(novel.source))
                }
            } catch (
                cancelled: CancellationException,
            ) {
                throw cancelled
            } catch (
                error: Exception,
            ) {
                host.providerAddLog("Credential could not be saved (${error.javaClass.simpleName})")
            }
        }
    }

    private suspend fun <T> withResolvedCredential(
        settings: NovelReaderSettings,
        provider: NovelTranslationProvider,
        block: suspend (String) -> T,
    ): T {
        val carrier = resolvedCredential(settings, provider)
        return try {
            block(carrier.reveal())
        } finally {
            carrier.close()
        }
    }

    private suspend fun resolvedCredential(
        settings: NovelReaderSettings,
        provider: NovelTranslationProvider,
    ): CredentialSecret {
        val direct = when (provider) {
            NovelTranslationProvider.GEMINI, NovelTranslationProvider.GEMINI_PRIVATE -> settings.geminiApiKey
            NovelTranslationProvider.OPENROUTER -> settings.openRouterApiKey
            NovelTranslationProvider.DEEPSEEK -> settings.deepSeekApiKey
            NovelTranslationProvider.MISTRAL -> settings.mistralApiKey
            NovelTranslationProvider.NVIDIA -> settings.nvidiaApiKey
            NovelTranslationProvider.OLLAMA_CLOUD -> settings.ollamaCloudApiKey
        }
        return if (settings.translationSourceId == null && direct.isNotBlank()) {
            CredentialSecret(direct)
        } else {
            TranslationProfileServices.legacyCredentials.resolveLegacy(provider, settings.translationSourceId)
        }
    }

    /** Live settings carry no secret, so a rotated key is detected by re-resolving, not by comparing fields. */
    private suspend fun geminiCredentialUnchanged(settings: NovelReaderSettings, key: String): Boolean =
        runCatching {
            resolvedCredential(settings, NovelTranslationProvider.GEMINI).use { it.reveal() == key }
        }.getOrDefault(false)

    private enum class ProfileModelTarget {
        GEMINI,
        OPENROUTER,
        DEEPSEEK,
        MISTRAL,
        NVIDIA,
        OLLAMA,
    }

    private fun invalidateProfileModelsRefresh(): Long {
        profileModelsRefreshGeneration++
        profileModelsRefreshJob?.cancel()
        profileModelsRefreshJob = null
        return profileModelsRefreshGeneration
    }

    private fun resolveProfileModelTarget(connection: ConnectionProfile): ProfileModelTarget? {
        return when {
            connection.protocol == TranslationProtocol.GEMINI_GENERATE -> ProfileModelTarget.GEMINI
            connection.protocol == TranslationProtocol.OLLAMA_NATIVE -> ProfileModelTarget.OLLAMA
            connection.presetFlavor == PresetFlavor.GEMINI_PRIVATE -> ProfileModelTarget.GEMINI
            connection.presetFlavor == PresetFlavor.OPENROUTER -> ProfileModelTarget.OPENROUTER
            connection.presetFlavor == PresetFlavor.DEEPSEEK -> ProfileModelTarget.DEEPSEEK
            connection.presetFlavor == PresetFlavor.MISTRAL -> ProfileModelTarget.MISTRAL
            connection.presetFlavor == PresetFlavor.NVIDIA -> ProfileModelTarget.NVIDIA
            connection.presetFlavor == PresetFlavor.OLLAMA -> ProfileModelTarget.OLLAMA
            else -> null
        }
    }

    private fun clearProfileModelsLoading(current: NovelAiProviderState): NovelAiProviderState = current.copy(
        isGeminiModelsLoading = false,
        isOpenRouterModelsLoading = false,
        isDeepSeekModelsLoading = false,
        isMistralModelsLoading = false,
        isNvidiaModelsLoading = false,
        isOllamaCloudModelsLoading = false,
    )

    private fun setProfileModelsLoading(
        current: NovelAiProviderState,
        target: ProfileModelTarget,
        loading: Boolean,
    ): NovelAiProviderState = when (target) {
        ProfileModelTarget.GEMINI -> current.copy(isGeminiModelsLoading = loading)
        ProfileModelTarget.OPENROUTER -> current.copy(isOpenRouterModelsLoading = loading)
        ProfileModelTarget.DEEPSEEK -> current.copy(isDeepSeekModelsLoading = loading)
        ProfileModelTarget.MISTRAL -> current.copy(isMistralModelsLoading = loading)
        ProfileModelTarget.NVIDIA -> current.copy(isNvidiaModelsLoading = loading)
        ProfileModelTarget.OLLAMA -> current.copy(isOllamaCloudModelsLoading = loading)
    }

    private fun mergeGeminiModelEntries(
        models: List<AiTranslationModelUi>,
        existing: List<GeminiModelEntry>,
    ): List<GeminiModelEntry> {
        val existingById = existing.associateBy { it.id }
        return models.map { model ->
            val id = model.selection.exactModelId
            val base = runCatching { GeminiModelMetadataStore.lookup(id) }.getOrNull() ?: existingById[id]
            val selection = model.selection
            val generation = selection.generation
            val inferredThinking = when (selection.reasoningContract) {
                ReasoningContract.None -> false
                ReasoningContract.Toggle,
                is ReasoningContract.Levels,
                is ReasoningContract.Budget,
                -> true
                ReasoningContract.Unknown -> null
            }
            GeminiModelEntry(
                id = id,
                displayName = model.label.ifBlank { base?.displayName ?: id },
                inputTokenLimit = base?.inputTokenLimit,
                outputTokenLimit = base?.outputTokenLimit ?: generation.maxOutputTokens,
                temperature = base?.temperature ?: generation.temperature?.toFloat(),
                maxTemperature = base?.maxTemperature,
                topP = base?.topP ?: generation.topP?.toFloat(),
                topK = base?.topK ?: generation.topK,
                thinking = base?.thinking ?: inferredThinking,
                supportedGenerationMethods = base?.supportedGenerationMethods ?: emptyList(),
            )
        }
    }

    private fun refreshProfileModels(settings: NovelReaderSettings): Boolean {
        val connection = settings.connectionProfile ?: return false
        val target = resolveProfileModelTarget(connection) ?: return true
        val generation = invalidateProfileModelsRefresh()
        updateState { setProfileModelsLoading(clearProfileModelsLoading(it), target, loading = true) }
        host.providerUpdateContent(settings)
        profileModelsRefreshJob = host.providerScope.launch {
            try {
                val models = fetchProfileModels(connection)
                if (generation != profileModelsRefreshGeneration) return@launch
                if (host.providerReaderSettings()?.connectionProfile != connection) return@launch
                val ids = models.map { it.selection.exactModelId }
                updateState { current ->
                    when (target) {
                        ProfileModelTarget.GEMINI -> current.copy(
                            geminiModelEntries = mergeGeminiModelEntries(
                                models = models,
                                existing = current.geminiModelEntries,
                            ),
                            isGeminiModelsLoading = false,
                        )
                        ProfileModelTarget.OPENROUTER -> current.copy(
                            openRouterModelIds = ids,
                            isOpenRouterModelsLoading = false,
                        )
                        ProfileModelTarget.DEEPSEEK -> current.copy(
                            deepSeekModelIds = ids,
                            isDeepSeekModelsLoading = false,
                        )
                        ProfileModelTarget.MISTRAL -> current.copy(
                            mistralModelIds = ids,
                            isMistralModelsLoading = false,
                        )
                        ProfileModelTarget.NVIDIA -> current.copy(
                            nvidiaModelIds = ids,
                            isNvidiaModelsLoading = false,
                        )
                        ProfileModelTarget.OLLAMA -> current.copy(
                            ollamaCloudModelIds = ids,
                            isOllamaCloudModelsLoading = false,
                        )
                    }
                }
            } catch (
                cancelled: CancellationException,
            ) {
                throw cancelled
            } catch (
                error: Exception,
            ) {
                if (generation == profileModelsRefreshGeneration) {
                    host.providerAddLog("Model catalogue failed (${error.javaClass.simpleName})")
                }
            } finally {
                if (generation == profileModelsRefreshGeneration) {
                    updateState { setProfileModelsLoading(it, target, loading = false) }
                    host.providerReaderSettings()?.let { host.providerUpdateContent(it) }
                }
            }
        }
        return true
    }

    /** Resets every provider connection-test status, e.g. when the provider selection changes. */
    fun resetAllApiTestStates() {
        invalidateGeminiModelsRefresh()
        updateState {
            clearProfileModelsLoading(it).copy(
                openRouterApiTestStatus = ProviderApiTestStatus.Idle,
                openRouterApiTestMessage = null,
                deepSeekApiTestStatus = ProviderApiTestStatus.Idle,
                deepSeekApiTestMessage = null,
                mistralApiTestStatus = ProviderApiTestStatus.Idle,
                mistralApiTestMessage = null,
                nvidiaApiTestStatus = ProviderApiTestStatus.Idle,
                nvidiaApiTestMessage = null,
                ollamaCloudApiTestStatus = ProviderApiTestStatus.Idle,
                ollamaCloudApiTestMessage = null,
            )
        }
    }

    /** Resets provider state that must not survive a chapter switch. */
    fun resetTransientState() {
        invalidateGeminiModelsRefresh()
        updateState {
            it.copy(
                isGeminiModelsLoading = false,
                isOpenRouterModelsLoading = false,
                isTestingOpenRouterConnection = false,
                isDeepSeekModelsLoading = false,
                isTestingDeepSeekConnection = false,
                isMistralModelsLoading = false,
                isTestingMistralConnection = false,
                isNvidiaModelsLoading = false,
                isTestingNvidiaConnection = false,
                isOllamaCloudModelsLoading = false,
                isTestingOllamaCloudConnection = false,
            )
        }
    }

    private fun setProviderApiTestState(
        provider: NovelTranslationProvider,
        status: ProviderApiTestStatus,
        message: String? = null,
    ) {
        updateState {
            when (provider) {
                NovelTranslationProvider.OPENROUTER -> it.copy(
                    openRouterApiTestStatus = status,
                    openRouterApiTestMessage = message,
                )
                NovelTranslationProvider.DEEPSEEK -> it.copy(
                    deepSeekApiTestStatus = status,
                    deepSeekApiTestMessage = message,
                )
                NovelTranslationProvider.MISTRAL -> it.copy(
                    mistralApiTestStatus = status,
                    mistralApiTestMessage = message,
                )
                NovelTranslationProvider.NVIDIA -> it.copy(
                    nvidiaApiTestStatus = status,
                    nvidiaApiTestMessage = message,
                )
                NovelTranslationProvider.OLLAMA_CLOUD -> it.copy(
                    ollamaCloudApiTestStatus = status,
                    ollamaCloudApiTestMessage = message,
                )
                NovelTranslationProvider.GEMINI,
                NovelTranslationProvider.GEMINI_PRIVATE,
                -> it
            }
        }
    }

    private fun updateGeminiSetting(
        setGlobal: () -> Unit,
        setOverride: (NovelReaderOverride) -> NovelReaderOverride,
    ) {
        val sourceId = host.providerCurrentNovel()?.source ?: return
        if (novelReaderPreferences.getSourceOverride(sourceId) != null) {
            novelReaderPreferences.updateSourceOverride(sourceId, setOverride)
        } else {
            setGlobal()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Model refresh + connection tests
    // ---------------------------------------------------------------------------------------------

    private fun invalidateGeminiModelsRefresh(): Long {
        invalidateProfileModelsRefresh()
        geminiModelsRefreshGeneration++
        geminiModelsRefreshJob?.cancel()
        geminiModelsRefreshJob = null
        return geminiModelsRefreshGeneration
    }

    fun refreshGeminiModels() {
        val generation = invalidateGeminiModelsRefresh()
        val settings = host.providerReaderSettings()
        if (settings != null && refreshProfileModels(settings)) return
        if (settings == null || settings.translationProvider != NovelTranslationProvider.GEMINI ||
            (
                settings.geminiApiKey.isBlank() &&
                    NovelTranslationProvider.GEMINI !in settings.translationCredentialProviders
                )
        ) {
            updateState { it.copy(isGeminiModelsLoading = false) }
            return
        }
        updateState { it.copy(isGeminiModelsLoading = true) }
        host.providerUpdateContent(settings)
        // fetchModels owns its IO context. Resume on the reader scope for settings/state fencing,
        // rather than mutating reader state from an unrelated Dispatchers.IO continuation.
        geminiModelsRefreshJob = host.providerScope.launch {
            // Resolution stays inside the failure path: an unavailable vault must log, not leak.
            val carrier = runCatching { resolvedCredential(settings, NovelTranslationProvider.GEMINI) }.getOrNull()
            val key = carrier?.reveal()
            val fetched = if (key == null) {
                Result.failure(IllegalStateException("Translation credential is missing"))
            } else {
                try {
                    Result.success(geminiModelsService.fetchModels(apiKey = key))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Result.failure(error)
                } finally {
                    carrier?.close()
                }
            }
            if (generation != geminiModelsRefreshGeneration) return@launch
            val currentSettings = host.providerReaderSettings()
            if (currentSettings == null || currentSettings.translationProvider != settings.translationProvider ||
                currentSettings.translationSourceId != settings.translationSourceId ||
                key == null || !geminiCredentialUnchanged(currentSettings, key)
            ) {
                updateState { it.copy(isGeminiModelsLoading = false) }
                return@launch
            }
            fetched.exceptionOrNull()?.let { error ->
                host.providerAddLog("? Gemini models load failed: ${formatAiTranslationThrowableForLog(error)}")
            }
            updateState {
                it.copy(
                    geminiModelEntries = fetched.getOrNull() ?: it.geminiModelEntries,
                    isGeminiModelsLoading = false,
                )
            }
            host.providerUpdateContent(currentSettings)
        }
    }

    fun refreshOpenRouterModels() {
        val settings = host.providerReaderSettings() ?: return
        if (refreshProfileModels(settings)) return
        if (settings.translationProvider != NovelTranslationProvider.OPENROUTER) return
        if (settings.openRouterApiKey.isBlank() &&
            NovelTranslationProvider.OPENROUTER !in settings.translationCredentialProviders
        ) {
            return
        }
        if (settings.openRouterBaseUrl.isBlank()) return
        updateState { it.copy(isOpenRouterModelsLoading = true) }
        host.providerUpdateContent(settings)
        host.providerScope.launch(Dispatchers.IO) {
            val fetched = runCatching {
                withResolvedCredential(settings, NovelTranslationProvider.OPENROUTER) { key ->
                    openRouterModelsService.fetchModels(baseUrl = settings.openRouterBaseUrl, apiKey = key)
                }
            }.getOrElse { error ->
                host.providerAddLog("? OpenRouter models load failed: ${formatAiTranslationThrowableForLog(error)}")
                emptyList()
            }
            updateState { it.copy(openRouterModelIds = fetched, isOpenRouterModelsLoading = false) }
            val currentSettings = host.providerReaderSettings() ?: settings
            host.providerUpdateContent(currentSettings)
        }
    }

    fun refreshNvidiaModels() {
        val settings = host.providerReaderSettings() ?: return
        if (refreshProfileModels(settings)) return
        if (settings.translationProvider != NovelTranslationProvider.NVIDIA) return
        if (settings.nvidiaBaseUrl.isBlank()) return
        if (settings.nvidiaApiKey.isBlank() &&
            NovelTranslationProvider.NVIDIA !in settings.translationCredentialProviders
        ) {
            return
        }
        updateState { it.copy(isNvidiaModelsLoading = true) }
        host.providerUpdateContent(settings)
        host.providerScope.launch(Dispatchers.IO) {
            val fetched = runCatching {
                withResolvedCredential(settings, NovelTranslationProvider.NVIDIA) { key ->
                    nvidiaModelsService.fetchModels(baseUrl = settings.nvidiaBaseUrl, apiKey = key)
                }
            }.getOrElse { error ->
                host.providerAddLog("? NVIDIA models load failed: ${formatAiTranslationThrowableForLog(error)}")
                emptyList()
            }
            updateState { it.copy(nvidiaModelIds = fetched, isNvidiaModelsLoading = false) }
            val currentSettings = host.providerReaderSettings() ?: settings
            host.providerUpdateContent(currentSettings)
        }
    }

    fun testNvidiaConnection() {
        val settings = host.providerReaderSettings() ?: return
        if (state.isTestingNvidiaConnection) return
        if (settings.translationProvider != NovelTranslationProvider.NVIDIA) return
        if (!host.providerHasConfiguredTranslationProvider(settings)) {
            host.providerAddLog("? NVIDIA config invalid: fill Base URL, API key and Model")
            setProviderApiTestState(
                provider = NovelTranslationProvider.NVIDIA,
                status = ProviderApiTestStatus.Error,
                message = application.stringResource(AYMR.strings.novel_reader_ai_translator_api_invalid_config),
            )
            host.providerUpdateContent(settings)
            return
        }
        updateState { it.copy(isTestingNvidiaConnection = true) }
        setProviderApiTestState(
            provider = NovelTranslationProvider.NVIDIA,
            status = ProviderApiTestStatus.Loading,
        )
        host.providerUpdateContent(settings)
        host.providerScope.launch {
            runCatching {
                val result = host.providerRequestTranslationBatch(
                    segments = listOf("Connection test"),
                    settings = settings,
                ) { message ->
                    host.providerAddLog("?? Test: $message")
                }
                if (result.isNullOrEmpty() || result.firstOrNull().isNullOrBlank()) {
                    error(application.stringResource(AYMR.strings.novel_reader_ai_translator_api_empty_response))
                }
            }.onSuccess {
                host.providerAddLog("? NVIDIA connection OK")
                setProviderApiTestState(
                    provider = NovelTranslationProvider.NVIDIA,
                    status = ProviderApiTestStatus.Success,
                )
            }.onFailure { error ->
                host.providerAddLog("? NVIDIA connection failed: ${formatAiTranslationThrowableForLog(error)}")
                setProviderApiTestState(
                    provider = NovelTranslationProvider.NVIDIA,
                    status = ProviderApiTestStatus.Error,
                    message = formatAiTranslationThrowableForLog(error),
                )
            }
            updateState { it.copy(isTestingNvidiaConnection = false) }
            val currentSettings = host.providerReaderSettings() ?: settings
            host.providerUpdateContent(currentSettings)
        }
    }

    fun refreshOllamaCloudModels() {
        val settings = host.providerReaderSettings() ?: return
        if (refreshProfileModels(settings)) return
        if (settings.translationProvider != NovelTranslationProvider.OLLAMA_CLOUD) return
        if (settings.ollamaCloudBaseUrl.isBlank()) return
        if (settings.ollamaCloudApiKey.isBlank() &&
            NovelTranslationProvider.OLLAMA_CLOUD !in settings.translationCredentialProviders
        ) {
            return
        }
        updateState { it.copy(isOllamaCloudModelsLoading = true) }
        host.providerUpdateContent(settings)
        host.providerScope.launch(Dispatchers.IO) {
            val fetched = runCatching {
                withResolvedCredential(settings, NovelTranslationProvider.OLLAMA_CLOUD) { key ->
                    ollamaCloudModelsService.fetchModels(baseUrl = settings.ollamaCloudBaseUrl, apiKey = key)
                }
            }.getOrElse { error ->
                host.providerAddLog("? Ollama Cloud models load failed: ${formatAiTranslationThrowableForLog(error)}")
                emptyList()
            }
            updateState { it.copy(ollamaCloudModelIds = fetched, isOllamaCloudModelsLoading = false) }
            val currentSettings = host.providerReaderSettings() ?: settings
            host.providerUpdateContent(currentSettings)
        }
    }

    fun testOllamaCloudConnection() {
        val settings = host.providerReaderSettings() ?: return
        if (state.isTestingOllamaCloudConnection) return
        if (settings.translationProvider != NovelTranslationProvider.OLLAMA_CLOUD) return
        if (!host.providerHasConfiguredTranslationProvider(settings)) {
            host.providerAddLog("? Ollama Cloud config invalid: fill Base URL, API key and Model")
            setProviderApiTestState(
                provider = NovelTranslationProvider.OLLAMA_CLOUD,
                status = ProviderApiTestStatus.Error,
                message = application.stringResource(AYMR.strings.novel_reader_ai_translator_api_invalid_config),
            )
            host.providerUpdateContent(settings)
            return
        }
        updateState { it.copy(isTestingOllamaCloudConnection = true) }
        setProviderApiTestState(
            provider = NovelTranslationProvider.OLLAMA_CLOUD,
            status = ProviderApiTestStatus.Loading,
        )
        host.providerUpdateContent(settings)
        host.providerScope.launch {
            runCatching {
                val result = host.providerRequestTranslationBatch(
                    segments = listOf("Connection test"),
                    settings = settings,
                ) { message ->
                    host.providerAddLog("?? Test: $message")
                }
                if (result.isNullOrEmpty() || result.firstOrNull().isNullOrBlank()) {
                    error(application.stringResource(AYMR.strings.novel_reader_ai_translator_api_empty_response))
                }
            }.onSuccess {
                host.providerAddLog("? Ollama Cloud connection OK")
                setProviderApiTestState(
                    provider = NovelTranslationProvider.OLLAMA_CLOUD,
                    status = ProviderApiTestStatus.Success,
                )
            }.onFailure { error ->
                host.providerAddLog("? Ollama Cloud connection failed: ${formatAiTranslationThrowableForLog(error)}")
                setProviderApiTestState(
                    provider = NovelTranslationProvider.OLLAMA_CLOUD,
                    status = ProviderApiTestStatus.Error,
                    message = formatAiTranslationThrowableForLog(error),
                )
            }
            updateState { it.copy(isTestingOllamaCloudConnection = false) }
            val currentSettings = host.providerReaderSettings() ?: settings
            host.providerUpdateContent(currentSettings)
        }
    }

    fun testOpenRouterConnection() {
        val settings = host.providerReaderSettings() ?: return
        if (state.isTestingOpenRouterConnection) return
        if (settings.translationProvider != NovelTranslationProvider.OPENROUTER) return
        if (!host.providerHasConfiguredTranslationProvider(settings)) {
            host.providerAddLog("? OpenRouter config invalid: fill Base URL, API key and Model")
            setProviderApiTestState(
                provider = NovelTranslationProvider.OPENROUTER,
                status = ProviderApiTestStatus.Error,
                message = application.stringResource(
                    AYMR.strings.novel_reader_ai_translator_api_invalid_openrouter_config,
                ),
            )
            host.providerUpdateContent(settings)
            return
        }
        updateState { it.copy(isTestingOpenRouterConnection = true) }
        setProviderApiTestState(
            provider = NovelTranslationProvider.OPENROUTER,
            status = ProviderApiTestStatus.Loading,
        )
        host.providerUpdateContent(settings)
        host.providerScope.launch {
            runCatching {
                val result = host.providerRequestTranslationBatch(
                    segments = listOf("Connection test"),
                    settings = settings,
                ) { message ->
                    host.providerAddLog("?? Test: $message")
                }
                if (result.isNullOrEmpty() || result.firstOrNull().isNullOrBlank()) {
                    error(application.stringResource(AYMR.strings.novel_reader_ai_translator_api_empty_response))
                }
            }.onSuccess {
                host.providerAddLog("? OpenRouter connection OK")
                setProviderApiTestState(
                    provider = NovelTranslationProvider.OPENROUTER,
                    status = ProviderApiTestStatus.Success,
                )
            }.onFailure { error ->
                host.providerAddLog("? OpenRouter connection failed: ${formatAiTranslationThrowableForLog(error)}")
                setProviderApiTestState(
                    provider = NovelTranslationProvider.OPENROUTER,
                    status = ProviderApiTestStatus.Error,
                    message = formatAiTranslationThrowableForLog(error),
                )
            }
            updateState { it.copy(isTestingOpenRouterConnection = false) }
            val currentSettings = host.providerReaderSettings() ?: settings
            host.providerUpdateContent(currentSettings)
        }
    }

    fun refreshDeepSeekModels() {
        val settings = host.providerReaderSettings() ?: return
        if (refreshProfileModels(settings)) return
        if (settings.translationProvider != NovelTranslationProvider.DEEPSEEK) return
        if (settings.deepSeekApiKey.isBlank() &&
            NovelTranslationProvider.DEEPSEEK !in settings.translationCredentialProviders
        ) {
            return
        }
        if (settings.deepSeekBaseUrl.isBlank()) return
        updateState { it.copy(isDeepSeekModelsLoading = true) }
        host.providerUpdateContent(settings)
        host.providerScope.launch(Dispatchers.IO) {
            val fetched = runCatching {
                withResolvedCredential(settings, NovelTranslationProvider.DEEPSEEK) { key ->
                    deepSeekModelsService.fetchModels(baseUrl = settings.deepSeekBaseUrl, apiKey = key)
                }
            }.getOrElse { error ->
                host.providerAddLog("? DeepSeek models load failed: ${formatAiTranslationThrowableForLog(error)}")
                emptyList()
            }
            updateState { it.copy(deepSeekModelIds = fetched, isDeepSeekModelsLoading = false) }
            val currentSettings = host.providerReaderSettings() ?: settings
            host.providerUpdateContent(currentSettings)
        }
    }

    fun testDeepSeekConnection() {
        val settings = host.providerReaderSettings() ?: return
        if (state.isTestingDeepSeekConnection) return
        if (settings.translationProvider != NovelTranslationProvider.DEEPSEEK) return
        if (!host.providerHasConfiguredTranslationProvider(settings)) {
            host.providerAddLog("? DeepSeek config invalid: fill Base URL, API key and Model")
            setProviderApiTestState(
                provider = NovelTranslationProvider.DEEPSEEK,
                status = ProviderApiTestStatus.Error,
                message = application.stringResource(AYMR.strings.novel_reader_ai_translator_api_invalid_config),
            )
            host.providerUpdateContent(settings)
            return
        }
        updateState { it.copy(isTestingDeepSeekConnection = true) }
        setProviderApiTestState(
            provider = NovelTranslationProvider.DEEPSEEK,
            status = ProviderApiTestStatus.Loading,
        )
        host.providerUpdateContent(settings)
        host.providerScope.launch {
            runCatching {
                val result = host.providerRequestTranslationBatch(
                    segments = listOf("Connection test"),
                    settings = settings,
                ) { message ->
                    host.providerAddLog("?? Test: $message")
                }
                if (result.isNullOrEmpty() || result.firstOrNull().isNullOrBlank()) {
                    error(application.stringResource(AYMR.strings.novel_reader_ai_translator_api_empty_response))
                }
            }.onSuccess {
                host.providerAddLog("? DeepSeek connection OK")
                setProviderApiTestState(
                    provider = NovelTranslationProvider.DEEPSEEK,
                    status = ProviderApiTestStatus.Success,
                )
            }.onFailure { error ->
                host.providerAddLog("? DeepSeek connection failed: ${formatAiTranslationThrowableForLog(error)}")
                setProviderApiTestState(
                    provider = NovelTranslationProvider.DEEPSEEK,
                    status = ProviderApiTestStatus.Error,
                    message = formatAiTranslationThrowableForLog(error),
                )
            }
            updateState { it.copy(isTestingDeepSeekConnection = false) }
            val currentSettings = host.providerReaderSettings() ?: settings
            host.providerUpdateContent(currentSettings)
        }
    }

    fun refreshMistralModels() {
        val settings = host.providerReaderSettings() ?: return
        if (refreshProfileModels(settings)) return
        if (settings.translationProvider != NovelTranslationProvider.MISTRAL) return
        if (settings.mistralApiKey.isBlank() &&
            NovelTranslationProvider.MISTRAL !in settings.translationCredentialProviders
        ) {
            return
        }
        if (settings.mistralBaseUrl.isBlank()) return
        updateState { it.copy(isMistralModelsLoading = true) }
        host.providerUpdateContent(settings)
        host.providerScope.launch(Dispatchers.IO) {
            val fetched = runCatching {
                withResolvedCredential(settings, NovelTranslationProvider.MISTRAL) { key ->
                    mistralModelsService.fetchModels(baseUrl = settings.mistralBaseUrl, apiKey = key)
                }
            }.getOrElse { error ->
                host.providerAddLog("? Mistral models load failed: ${formatAiTranslationThrowableForLog(error)}")
                emptyList()
            }
            updateState { it.copy(mistralModelIds = fetched, isMistralModelsLoading = false) }
            val currentSettings = host.providerReaderSettings() ?: settings
            host.providerUpdateContent(currentSettings)
        }
    }

    fun testMistralConnection() {
        val settings = host.providerReaderSettings() ?: return
        if (state.isTestingMistralConnection) return
        if (settings.translationProvider != NovelTranslationProvider.MISTRAL) return
        if (!host.providerHasConfiguredTranslationProvider(settings)) {
            host.providerAddLog("? Mistral config invalid: fill Base URL, API key and Model")
            setProviderApiTestState(
                provider = NovelTranslationProvider.MISTRAL,
                status = ProviderApiTestStatus.Error,
                message = application.stringResource(AYMR.strings.novel_reader_ai_translator_api_invalid_config),
            )
            host.providerUpdateContent(settings)
            return
        }
        updateState { it.copy(isTestingMistralConnection = true) }
        setProviderApiTestState(
            provider = NovelTranslationProvider.MISTRAL,
            status = ProviderApiTestStatus.Loading,
        )
        host.providerUpdateContent(settings)
        host.providerScope.launch {
            runCatching {
                val result = host.providerRequestTranslationBatch(
                    segments = listOf("Connection test"),
                    settings = settings,
                ) { message ->
                    host.providerAddLog("?? Test: $message")
                }
                if (result.isNullOrEmpty() || result.firstOrNull().isNullOrBlank()) {
                    error(application.stringResource(AYMR.strings.novel_reader_ai_translator_api_empty_response))
                }
            }.onSuccess {
                host.providerAddLog("? Mistral connection OK")
                setProviderApiTestState(
                    provider = NovelTranslationProvider.MISTRAL,
                    status = ProviderApiTestStatus.Success,
                )
            }.onFailure { error ->
                host.providerAddLog("? Mistral connection failed: ${formatAiTranslationThrowableForLog(error)}")
                setProviderApiTestState(
                    provider = NovelTranslationProvider.MISTRAL,
                    status = ProviderApiTestStatus.Error,
                    message = formatAiTranslationThrowableForLog(error),
                )
            }
            updateState { it.copy(isTestingMistralConnection = false) }
            val currentSettings = host.providerReaderSettings() ?: settings
            host.providerUpdateContent(currentSettings)
        }
    }
}
