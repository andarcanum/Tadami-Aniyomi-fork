package eu.kanade.tachiyomi.ui.reader.novel

import android.app.Application
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderOverride
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiModelEntry
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.MistralModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.NvidiaModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OllamaCloudModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.formatAiTranslationThrowableForLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.model.Novel
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
) {

    private var state: NovelAiProviderState = NovelAiProviderState()

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

    fun setOpenRouterApiKey(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.openRouterApiKey().set(value) },
        setOverride = { it.copy(openRouterApiKey = value) },
    )

    fun setOpenRouterModel(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.openRouterModel().set(value) },
        setOverride = { it.copy(openRouterModel = value) },
    )

    fun setDeepSeekBaseUrl(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.deepSeekBaseUrl().set(value) },
        setOverride = { it.copy(deepSeekBaseUrl = value) },
    )

    fun setDeepSeekApiKey(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.deepSeekApiKey().set(value) },
        setOverride = { it.copy(deepSeekApiKey = value) },
    )

    fun setDeepSeekModel(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.deepSeekModel().set(value) },
        setOverride = { it.copy(deepSeekModel = value) },
    )

    fun setMistralBaseUrl(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.mistralBaseUrl().set(value) },
        setOverride = { it.copy(mistralBaseUrl = value) },
    )

    fun setMistralApiKey(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.mistralApiKey().set(value) },
        setOverride = { it.copy(mistralApiKey = value) },
    )

    fun setMistralModel(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.mistralModel().set(value) },
        setOverride = { it.copy(mistralModel = value) },
    )

    fun setNvidiaBaseUrl(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.nvidiaBaseUrl().set(value) },
        setOverride = { it.copy(nvidiaBaseUrl = value) },
    )

    fun setNvidiaApiKey(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.nvidiaApiKey().set(value) },
        setOverride = { it.copy(nvidiaApiKey = value) },
    )

    fun setNvidiaModel(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.nvidiaModel().set(value) },
        setOverride = { it.copy(nvidiaModel = value) },
    )

    fun setOllamaCloudBaseUrl(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.ollamaCloudBaseUrl().set(value) },
        setOverride = { it.copy(ollamaCloudBaseUrl = value) },
    )

    fun setOllamaCloudApiKey(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.ollamaCloudApiKey().set(value) },
        setOverride = { it.copy(ollamaCloudApiKey = value) },
    )

    fun setOllamaCloudModel(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.ollamaCloudModel().set(value) },
        setOverride = { it.copy(ollamaCloudModel = value) },
    )

    /** Resets every provider connection-test status, e.g. when the provider selection changes. */
    fun resetAllApiTestStates() {
        updateState {
            it.copy(
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

    fun refreshGeminiModels() {
        val settings = host.providerReaderSettings() ?: return
        if (settings.translationProvider != NovelTranslationProvider.GEMINI) return
        if (settings.geminiApiKey.isBlank()) return
        updateState { it.copy(isGeminiModelsLoading = true) }
        host.providerUpdateContent(settings)
        host.providerScope.launch(Dispatchers.IO) {
            val fetched = runCatching {
                geminiModelsService.fetchModels(apiKey = settings.geminiApiKey)
            }.getOrElse { error ->
                host.providerAddLog("? Gemini models load failed: ${formatAiTranslationThrowableForLog(error)}")
                emptyList()
            }
            updateState { it.copy(geminiModelEntries = fetched, isGeminiModelsLoading = false) }
            val currentSettings = host.providerReaderSettings() ?: settings
            host.providerUpdateContent(currentSettings)
        }
    }

    fun refreshOpenRouterModels() {
        val settings = host.providerReaderSettings() ?: return
        if (settings.translationProvider != NovelTranslationProvider.OPENROUTER) return
        if (settings.openRouterApiKey.isBlank()) return
        if (settings.openRouterBaseUrl.isBlank()) return
        updateState { it.copy(isOpenRouterModelsLoading = true) }
        host.providerUpdateContent(settings)
        host.providerScope.launch(Dispatchers.IO) {
            val fetched = runCatching {
                openRouterModelsService.fetchModels(
                    baseUrl = settings.openRouterBaseUrl,
                    apiKey = settings.openRouterApiKey,
                )
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
        if (settings.translationProvider != NovelTranslationProvider.NVIDIA) return
        if (settings.nvidiaBaseUrl.isBlank()) return
        if (settings.nvidiaApiKey.isBlank()) return
        updateState { it.copy(isNvidiaModelsLoading = true) }
        host.providerUpdateContent(settings)
        host.providerScope.launch(Dispatchers.IO) {
            val fetched = runCatching {
                nvidiaModelsService.fetchModels(
                    baseUrl = settings.nvidiaBaseUrl,
                    apiKey = settings.nvidiaApiKey,
                )
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
        if (settings.translationProvider != NovelTranslationProvider.OLLAMA_CLOUD) return
        if (settings.ollamaCloudBaseUrl.isBlank()) return
        if (settings.ollamaCloudApiKey.isBlank()) return
        updateState { it.copy(isOllamaCloudModelsLoading = true) }
        host.providerUpdateContent(settings)
        host.providerScope.launch(Dispatchers.IO) {
            val fetched = runCatching {
                ollamaCloudModelsService.fetchModels(
                    baseUrl = settings.ollamaCloudBaseUrl,
                    apiKey = settings.ollamaCloudApiKey,
                )
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
        if (settings.translationProvider != NovelTranslationProvider.DEEPSEEK) return
        if (settings.deepSeekApiKey.isBlank()) return
        if (settings.deepSeekBaseUrl.isBlank()) return
        updateState { it.copy(isDeepSeekModelsLoading = true) }
        host.providerUpdateContent(settings)
        host.providerScope.launch(Dispatchers.IO) {
            val fetched = runCatching {
                deepSeekModelsService.fetchModels(
                    baseUrl = settings.deepSeekBaseUrl,
                    apiKey = settings.deepSeekApiKey,
                )
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
        if (settings.translationProvider != NovelTranslationProvider.MISTRAL) return
        if (settings.mistralApiKey.isBlank()) return
        if (settings.mistralBaseUrl.isBlank()) return
        updateState { it.copy(isMistralModelsLoading = true) }
        host.providerUpdateContent(settings)
        host.providerScope.launch(Dispatchers.IO) {
            val fetched = runCatching {
                mistralModelsService.fetchModels(
                    baseUrl = settings.mistralBaseUrl,
                    apiKey = settings.mistralApiKey,
                )
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
