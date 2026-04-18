package eu.kanade.tachiyomi.ui.reader.novel
import android.app.Application
import android.os.SystemClock
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.novel.model.toSNovel
import eu.kanade.domain.items.novelchapter.interactor.SyncNovelChaptersWithSource
import eu.kanade.domain.items.novelchapter.model.toSNovelChapter
import eu.kanade.presentation.reader.novel.NovelReaderTtsChapterHandoffPolicy
import eu.kanade.presentation.reader.novel.SeriesInterstitialState
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.data.prefetch.AllowAllContentPrefetchEnvironment
import eu.kanade.tachiyomi.data.prefetch.AndroidContentPrefetchEnvironment
import eu.kanade.tachiyomi.data.prefetch.ContentPrefetchService
import eu.kanade.tachiyomi.data.translation.TranslationJob
import eu.kanade.tachiyomi.data.translation.TranslationQueueManager
import eu.kanade.tachiyomi.data.translation.TranslationStatus
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginStorage
import eu.kanade.tachiyomi.extension.novel.runtime.NovelJsSource
import eu.kanade.tachiyomi.extension.novel.runtime.resolveUrl
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.source.novel.NovelWebUrlSource
import eu.kanade.tachiyomi.ui.novel.resolveNovelResumeChapter
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderOverride
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode
import eu.kanade.tachiyomi.ui.reader.novel.translation.AirforceModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.AirforceTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.AirforceTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekPromptResolver
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPrivateBridge
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPromptModifiers
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPromptResolver
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationCacheEntry
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationSessionCache
import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleUnofficialSelectedTextTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationCacheResolver
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationDiskCacheStore
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelSelectedTextTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelSelectedTextTranslationProviderOutcome
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelSelectedTextTranslationRequest
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelTranslationPromptFamily
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelTranslationStylePresets
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.TranslationPhase
import eu.kanade.tachiyomi.ui.reader.novel.translation.buildNovelSelectedTextTranslationRequestKey
import eu.kanade.tachiyomi.ui.reader.novel.translation.formatGeminiThrowableForLog
import eu.kanade.tachiyomi.ui.reader.novel.translation.normalizeGeminiModelId
import eu.kanade.tachiyomi.ui.reader.novel.translation.resolveNovelTranslationPromptFamily
import eu.kanade.tachiyomi.ui.reader.novel.translation.toTranslationCacheRequirements
import eu.kanade.tachiyomi.ui.reader.novel.translation.translationCacheModelId
import eu.kanade.tachiyomi.ui.reader.novel.tts.AndroidNovelTtsAudioFocusBridge
import eu.kanade.tachiyomi.ui.reader.novel.tts.AndroidNovelTtsEngineInfoSource
import eu.kanade.tachiyomi.ui.reader.novel.tts.AndroidNovelTtsPlatformFactory
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelReaderTtsUiState
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsAudioFocusManager
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsChapterModelBuildOptions
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsChapterModelBuilder
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsChapterRepository
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsEngine
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsEngineRegistry
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsHighlightEstimator
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackProgressListener
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackServiceRuntime
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackState
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsResolvedChapter
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsSession
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsSessionController
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsSessionUiState
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsWordTokenizer
import eu.kanade.tachiyomi.ui.reader.novel.tts.SharedNovelTtsSessionStore
import eu.kanade.tachiyomi.ui.reader.novel.tts.resolveNovelTtsVoiceSelection
import eu.kanade.tachiyomi.util.system.isNightMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.data.achievement.model.AchievementEvent
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.history.novel.model.NovelHistoryUpdate
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.series.novel.interactor.GetNovelSeriesWithEntries
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
class NovelReaderScreenModel(
    private val chapterId: Long,
    private val seriesId: Long? = null,
    private val autoStartGeminiTranslation: Boolean = false,
    private val novelChapterRepository: NovelChapterRepository = Injekt.get(),
    private val syncNovelChaptersWithSource: SyncNovelChaptersWithSource = Injekt.get(),
    private val getNovel: GetNovel = Injekt.get(),
    private val getNovelSeriesWithEntries: GetNovelSeriesWithEntries = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val novelDownloadManager: NovelDownloadManager = NovelDownloadManager(),
    private val pluginStorage: NovelPluginStorage = Injekt.get(),
    private val historyRepository: NovelHistoryRepository? = null,
    private val novelReaderPreferences: NovelReaderPreferences = Injekt.get(),
    private val ttsChapterRepository: NovelTtsChapterRepository = NovelTtsChapterRepository(
        novelChapterRepository = novelChapterRepository,
        getNovel = getNovel,
        sourceManager = sourceManager,
        novelDownloadManager = novelDownloadManager,
        pluginStorage = pluginStorage,
        novelReaderPreferences = novelReaderPreferences,
    ),
    private val eventBus: AchievementEventBus? = runCatching { Injekt.get<AchievementEventBus>() }.getOrNull(),
    private val isSystemDark: () -> Boolean = { Injekt.get<Application>().isNightMode() },
    private val geminiTranslationService: GeminiTranslationService = run {
        val app = Injekt.get<Application>()
        val networkHelper = Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>()
        val json = Injekt.get<Json>()
        GeminiTranslationService(
            client = networkHelper.client,
            json = json,
            promptResolver = GeminiPromptResolver(app),
        )
    },
    private val airforceTranslationService: AirforceTranslationService = run {
        val networkHelper = Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>()
        val json = Injekt.get<Json>()
        val airforceClient = networkHelper.client.newBuilder()
            .readTimeout(180, TimeUnit.SECONDS)
            .build()
        AirforceTranslationService(
            client = airforceClient,
            json = json,
        )
    },
    private val airforceModelsService: AirforceModelsService = run {
        val networkHelper = Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>()
        val json = Injekt.get<Json>()
        AirforceModelsService(
            client = networkHelper.client,
            json = json,
        )
    },
    private val openRouterTranslationService: OpenRouterTranslationService = run {
        val networkHelper = Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>()
        val json = Injekt.get<Json>()
        val openRouterClient = networkHelper.client.newBuilder()
            .readTimeout(180, TimeUnit.SECONDS)
            .build()
        OpenRouterTranslationService(
            client = openRouterClient,
            json = json,
        )
    },
    private val openRouterModelsService: OpenRouterModelsService = run {
        val networkHelper = Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>()
        val json = Injekt.get<Json>()
        OpenRouterModelsService(
            client = networkHelper.client,
            json = json,
        )
    },
    private val deepSeekTranslationService: DeepSeekTranslationService = run {
        val app = Injekt.get<Application>()
        val networkHelper = Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>()
        val json = Injekt.get<Json>()
        val deepSeekClient = networkHelper.client.newBuilder()
            .readTimeout(180, TimeUnit.SECONDS)
            .build()
        DeepSeekTranslationService(
            client = deepSeekClient,
            json = json,
            resolveSystemPrompt = { mode, family ->
                DeepSeekPromptResolver(app).resolveSystemPrompt(mode, family)
            },
        )
    },
    private val deepSeekModelsService: DeepSeekModelsService = run {
        val networkHelper = Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>()
        val json = Injekt.get<Json>()
        DeepSeekModelsService(
            client = networkHelper.client,
            json = json,
        )
    },
    private val selectedTextTranslationProvider: NovelSelectedTextTranslationProvider = run {
        val networkHelper = Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>()
        val json = Injekt.get<Json>()
        GoogleUnofficialSelectedTextTranslationProvider(
            client = networkHelper.client,
            json = json,
        )
    },
    private val googleTranslationService: GoogleTranslationService = run {
        val networkHelper = Injekt.get<NetworkHelper>()
        GoogleTranslationService(client = networkHelper.client)
    },
    private val translationQueueManager: TranslationQueueManager = Injekt.get(),
) : StateScreenModel<NovelReaderScreenModel.State>(State.Loading()) {
    private val contentPrefetchService = ContentPrefetchService(
        environment = runCatching {
            AndroidContentPrefetchEnvironment(Injekt.get<Application>())
        }.getOrElse {
            AllowAllContentPrefetchEnvironment
        },
    )
    private val application = Injekt.get<Application>()
    private val ttsChapterModelBuilder = NovelTtsChapterModelBuilder(NovelTtsWordTokenizer)
    private val ttsHighlightEstimator = NovelTtsHighlightEstimator()
    private val ttsEngineRegistry = NovelTtsEngineRegistry(AndroidNovelTtsEngineInfoSource(application))
    private val ttsEngine = NovelTtsEngine(AndroidNovelTtsPlatformFactory(application))
    private val ttsAudioFocusManager = NovelTtsAudioFocusManager(
        bridge = AndroidNovelTtsAudioFocusBridge(application),
        onPauseRequested = {
            screenModelScope.launch {
                ttsSessionController.pause()
            }
        },
    )
    private val ttsSessionStore = SharedNovelTtsSessionStore
    private val ttsSessionController = NovelTtsSessionController(
        chapterSource = object : eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsChapterSource {
            override suspend fun loadChapter(chapterId: Long): NovelTtsResolvedChapter? {
                return resolveTtsChapter(chapterId)
            }
        },
        speaker = object : eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackSpeaker {
            override suspend fun speak(
                utterance: eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsUtterance,
                flushQueue: Boolean,
                startWordIndex: Int,
            ) {
                val resumedText = utterance.wordRanges
                    .getOrNull(startWordIndex.coerceAtLeast(0))
                    ?.startChar
                    ?.let { startChar -> utterance.text.substring(startChar) }
                    ?: utterance.text
                ttsEngine.speak(utterance.id, resumedText, flushQueue)
            }

            override fun stop() {
                ttsEngine.stop()
            }
        },
        sessionStore = ttsSessionStore,
    )
    private var settingsJob: Job? = null
    private var ttsWordProgressJob: Job? = null
    private var rawHtml: String? = null
    private var currentNovel: Novel? = null
    private var currentChapter: NovelChapter? = null
    private var chapterOrderList: List<NovelChapter> = emptyList()
    private var customCss: String? = null
    private var customJs: String? = null
    private var pluginSite: String? = null
    private var chapterWebUrl: String? = null
    private var parsedContentBlocks: List<ContentBlock>? = null
    private var parsedRichContentResult: NovelRichContentParseResult? = null
    private var lastSavedProgress: Long? = null
    private var lastSavedRead: Boolean? = null
    private var initialProgressIndex: Int = 0
    private var hasProgressChanged: Boolean = false
    private var chapterReadStartTimeMs: Long = System.currentTimeMillis()
    private var pendingHistoryReadDurationMs: Long = 0L
    private var nextChapterPrefetchJob: Job? = null
    private var hasTriggeredNextChapterPrefetch: Boolean = false
    private var nextChapterGeminiPrefetchJob: Job? = null
    private var adjacentJaomixPageJob: Job? = null
    private val attemptedJaomixPages = mutableSetOf<Int>()
    private var hasTriggeredNextChapterGeminiPrefetch: Boolean = false
    private var hasTriggeredGeminiAutoStart: Boolean = false
    private var pendingAutoStartGeminiTranslation: Boolean = autoStartGeminiTranslation
    private var geminiTranslationJob: Job? = null
    private var queueProgressJob: Job? = null
    private var geminiTranslatedByIndex: Map<Int, String> = emptyMap()
    private var isGeminiTranslating: Boolean = false
    private var geminiTranslationProgress: Int = 0
    private var isGeminiTranslationVisible: Boolean = false
    private var hasGeminiTranslationCache: Boolean = false
    private var geminiLogs: List<String> = emptyList()

    // Google Translation
    private var googleTranslationJob: Job? = null
    private var googleTranslatedByIndex: Map<Int, String> = emptyMap()
    private var isGoogleTranslating: Boolean = false
    private var googleTranslationProgress: Int = 0
    private var isGoogleTranslationVisible: Boolean = false
    private var hasGoogleTranslationCache: Boolean = false
    private var googleLogs: List<String> = emptyList()
    private var googleRateLimited: Boolean = false
    private var translationPhase: TranslationPhase = TranslationPhase.IDLE
    private val googleSessionCache = GoogleTranslationSessionCache()
    private var ttsUiState: NovelReaderTtsUiState = NovelReaderTtsUiState()
    private var seriesInterstitialState: SeriesInterstitialState? = null
    private var seriesInterstitialShownForChapterId: Long? = null
    private var initializedTtsEnginePackage: String? = null
    private var pendingTtsStartRequest: eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackStartRequest? = null

    private var airforceModelIds: List<String> = emptyList()
    private var isAirforceModelsLoading: Boolean = false
    private var isTestingAirforceConnection: Boolean = false
    private var openRouterModelIds: List<String> = emptyList()
    private var isOpenRouterModelsLoading: Boolean = false
    private var isTestingOpenRouterConnection: Boolean = false
    private var deepSeekModelIds: List<String> = emptyList()
    private var isDeepSeekModelsLoading: Boolean = false
    private var isTestingDeepSeekConnection: Boolean = false
    private var selectedTextTranslationSelection: NovelSelectedTextSelection? = null
    private var selectedTextTranslationUiState: NovelSelectedTextTranslationUiState =
        NovelSelectedTextTranslationUiState.Idle
    private var selectedTextTranslationJob: Job? = null
    private val selectedTextTranslationSessionCache = NovelSelectedTextTranslationSessionCache()
    private val progressPersistenceMutex = Mutex()
    private var pendingProgressPersistence: PendingProgressPersistence? = null
    private var progressPersistenceJob: Job? = null

    @Volatile
    private var progressPersistenceScheduled = false
    private val structuredJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val resolvedHistoryRepository by lazy {
        historyRepository ?: runCatching { Injekt.get<NovelHistoryRepository>() }.getOrNull()
    }
    init {
        ttsEngine.setProgressListener(
            object : NovelTtsPlaybackProgressListener {
                override fun onUtteranceStart(utteranceId: String) {
                    screenModelScope.launch {
                        handleTtsUtteranceStarted(utteranceId)
                    }
                }

                override fun onUtteranceDone(utteranceId: String) {
                    screenModelScope.launch {
                        ttsWordProgressJob?.cancel()
                        ttsSessionController.onUtteranceCompleted(utteranceId)
                    }
                }

                override fun onUtteranceError(utteranceId: String) {
                    screenModelScope.launch {
                        ttsWordProgressJob?.cancel()
                        ttsUiState = ttsUiState.copy(errorMessage = "Failed to speak utterance")
                        refreshTtsUiState()
                    }
                }
            },
        )
        screenModelScope.launch {
            refreshTtsEngines()
        }
        screenModelScope.launch {
            ttsSessionController.state.collect { sessionState ->
                onTtsSessionStateChanged(sessionState)
            }
        }
        screenModelScope.launch {
            loadChapter()
        }
    }
    private suspend fun loadChapter() {
        val snapshot = try {
            ttsChapterRepository.loadChapterSnapshot(chapterId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to load novel chapter snapshot" }
            return setError(e.message)
        }
        val chapter = snapshot.chapter
        val novel = snapshot.novel
        val source = sourceManager.get(novel.source)
            ?: return setError("Source not found")
        clearChapterTransientState()
        currentNovel = novel
        currentChapter = chapter
        chapterOrderList = snapshot.chapterOrderList
        rawHtml = withContext(Dispatchers.Default) {
            val normalizedChapterHtml = prependChapterHeadingIfMissing(
                rawHtml = snapshot.rawHtml.normalizeStructuredChapterPayload(),
                chapterName = chapter.name,
            )
            val sanitizedChapterHtml = sanitizeChapterHtmlForReader(normalizedChapterHtml)
            if (sanitizedChapterHtml.isBlank()) normalizedChapterHtml else sanitizedChapterHtml
        }
        lastSavedProgress = chapter.lastPageRead
        lastSavedRead = chapter.read
        initialProgressIndex = snapshot.lastSavedIndex
        hasProgressChanged = false
        hasTriggeredNextChapterPrefetch = false
        hasTriggeredNextChapterGeminiPrefetch = false
        hasTriggeredGeminiAutoStart = false
        customCss = snapshot.customCss
        customJs = snapshot.customJs
        pluginSite = snapshot.pluginSite
        chapterWebUrl = snapshot.chapterWebUrl
        val initialSettings = novelReaderPreferences.resolveSettings(novel.source)
        mutableState.value = State.Loading(initialSettings)
        if (rawHtml == null) return setError("Chapter content is empty")
        parseAndCacheContentBlocks(
            rawHtml = rawHtml.orEmpty(),
            chapterWebUrl = chapterWebUrl,
            novelUrl = novel.url,
            pluginSite = pluginSite,
        )
        chapterReadStartTimeMs = System.currentTimeMillis()
        restoreGeminiTranslationFromCache(
            chapterId = chapter.id,
            settings = initialSettings,
        )
        subscribeToQueueProgress(chapter.id)
        settingsJob?.cancel()
        settingsJob = screenModelScope.launch {
            var skippedInitialEmission = false
            novelReaderPreferences.settingsFlow(novel.source)
                .distinctUntilChanged()
                .collect { settings ->
                    if (!skippedInitialEmission && settings == initialSettings) {
                        skippedInitialEmission = true
                        return@collect
                    }
                    skippedInitialEmission = true
                    updateContent(settings)
                    initializeTtsRuntime()
                    maybeAutoStartGeminiTranslation(settings)
                    maybeAutoStartGoogleTranslation()
                }
        }
        saveHistorySnapshot(chapter.id, sessionReadDurationMs = 0L)
        updateContent(initialSettings)
        initializeTtsRuntime()
        maybeRestoreTtsAfterChapterHandoff(
            chapterId = chapter.id,
            settings = initialSettings,
        )
        maybeAutoStartGeminiTranslation(initialSettings)
        maybeAutoStartGoogleTranslation()
        when (initialSettings.translationProvider) {
            NovelTranslationProvider.GEMINI -> Unit
            NovelTranslationProvider.GEMINI_PRIVATE -> Unit
            NovelTranslationProvider.AIRFORCE -> refreshAirforceModels()
            NovelTranslationProvider.OPENROUTER -> refreshOpenRouterModels()
            NovelTranslationProvider.DEEPSEEK -> refreshDeepSeekModels()
        }
    }
    private fun setError(message: String?) {
        mutableState.value = State.Error(message)
    }
    private fun subscribeToQueueProgress(chapterId: Long) {
        queueProgressJob?.cancel()
        queueProgressJob = screenModelScope.launch {
            translationQueueManager.progressUpdates
                .filter { it.chapterId == chapterId }
                .onEach { update ->
                    when (update.status) {
                        TranslationStatus.IN_PROGRESS -> {
                            isGeminiTranslating = true
                            geminiTranslationProgress = update.progress
                            refreshGeminiUiState()
                        }
                        TranslationStatus.COMPLETED -> {
                            isGeminiTranslating = false
                            geminiTranslationProgress = 100
                            val settings = (mutableState.value as? State.Success)?.readerSettings
                            if (settings != null) {
                                restoreGeminiTranslationFromCache(update.chapterId, settings)
                                updateContent(settings)
                            }
                            refreshGeminiUiState()
                        }
                        TranslationStatus.FAILED -> {
                            isGeminiTranslating = false
                            geminiTranslationProgress = 0
                            addGeminiLog("Queue translation failed: ${update.errorMessage ?: "Unknown error"}")
                            refreshGeminiUiState()
                        }
                        TranslationStatus.PENDING -> {
                            isGeminiTranslating = true
                            geminiTranslationProgress = 0
                            refreshGeminiUiState()
                        }
                    }
                }
                .takeWhile { update ->
                    update.status != TranslationStatus.COMPLETED &&
                        update.status != TranslationStatus.FAILED
                }
                .collect { }
        }
    }
    private fun scheduleNextChapterPrefetch(
        novel: Novel,
        currentChapter: NovelChapter,
        source: eu.kanade.tachiyomi.novelsource.NovelSource,
    ) {
        val nextChapter = findNextChapter(currentChapter) ?: return
        nextChapterPrefetchJob?.cancel()
        nextChapterPrefetchJob = screenModelScope.launch(Dispatchers.IO) {
            runCatching {
                val state = mutableState.value as? State.Success ?: return@runCatching
                contentPrefetchService.prefetchNovelChapterText(
                    prefetchEnabled = state.readerSettings.prefetchNextChapter,
                    novel = novel,
                    chapter = nextChapter,
                    source = source,
                    downloadManager = novelDownloadManager,
                    cacheReadChapters = novelReaderPreferences.cacheReadChapters().get(),
                )
            }.onFailure { error ->
                logcat(LogPriority.WARN, error) { "Failed to prefetch next novel chapter" }
            }
        }
    }
    private fun maybeAutoStartGeminiTranslation(settings: NovelReaderSettings) {
        if (hasTriggeredGeminiAutoStart) return
        val requestedAutoStart = pendingAutoStartGeminiTranslation
        val englishSourceAutoStart = settings.geminiAutoTranslateEnglishSource &&
            isGeminiSourceLanguageEnglish(settings.geminiSourceLang)
        if (!settings.geminiEnabled || !(requestedAutoStart || englishSourceAutoStart)) return
        if (!settings.hasConfiguredTranslationProvider()) return
        if (currentParsedTextBlocks().isEmpty()) return
        if (isGeminiTranslating || hasGeminiTranslationCache || geminiTranslatedByIndex.isNotEmpty()) return
        hasTriggeredGeminiAutoStart = true
        pendingAutoStartGeminiTranslation = false
        addGeminiLog("?? Auto-start translation for English source")
        startGeminiTranslation()
    }
    private fun findNextChapter(currentChapter: NovelChapter): NovelChapter? {
        return chapterOrderList
            .indexOfFirst { it.id == currentChapter.id }
            .takeIf { it >= 0 }
            ?.let { chapterOrderList.getOrNull(it + 1) }
    }
    private fun setSeriesInterstitialState(value: SeriesInterstitialState?) {
        seriesInterstitialState = value
        val currentState = mutableState.value
        if (currentState is State.Success) {
            mutableState.value = currentState.copy(seriesInterstitialState = value)
        }
    }
    fun clearSeriesInterstitial() {
        setSeriesInterstitialState(null)
    }
    private suspend fun resolveSeriesInterstitialState(): SeriesInterstitialState? {
        val targetSeriesId = seriesId ?: return null
        val novel = currentNovel ?: return null
        val wrapper = getNovelSeriesWithEntries.subscribe(targetSeriesId).first() ?: return null
        val seriesEntries = wrapper.series.entries
        val currentIndex = seriesEntries.indexOfFirst { it.id == novel.id }
        if (currentIndex < 0) return null
        val nextNovel = seriesEntries.getOrNull(currentIndex + 1)?.novel
        val nextChapter = nextNovel?.let { entryNovel ->
            val chapters = withContext(Dispatchers.IO) {
                novelChapterRepository.getChapterByNovelId(
                    entryNovel.id,
                    applyScanlatorFilter = true,
                ).sortedWith(
                    compareBy<NovelChapter> { it.sourceOrder }
                        .thenBy { it.chapterNumber }
                        .thenBy { it.id },
                )
            }
            resolveNovelResumeChapter(chapters)
        }
        return SeriesInterstitialState(
            seriesTitle = wrapper.series.title,
            currentNovelTitle = novel.title,
            nextNovel = nextNovel,
            nextChapterId = nextChapter?.id,
            nextChapterName = nextChapter?.name,
        )
    }
    private fun maybeShowSeriesInterstitial(chapter: NovelChapter, becameRead: Boolean) {
        if (!becameRead) return
        if (seriesId == null) return
        if (seriesInterstitialState != null) return
        if (seriesInterstitialShownForChapterId == chapter.id) return
        if (findNextChapter(chapter) != null) return
        seriesInterstitialShownForChapterId = chapter.id
        screenModelScope.launch {
            val resolved = resolveSeriesInterstitialState() ?: return@launch
            setSeriesInterstitialState(resolved)
        }
    }
    private suspend fun loadChapterOrderList(novelId: Long): List<NovelChapter> {
        return withContext(Dispatchers.IO) {
            val chapters = novelChapterRepository.getChapterByNovelId(novelId, applyScanlatorFilter = true)
            chapters.sortedWith(
                compareBy<NovelChapter> { it.sourceOrder }
                    .thenBy { it.chapterNumber }
                    .thenBy { it.id },
            )
        }
    }
    private fun maybeEnsureJaomixAdjacentPage(
        chapter: NovelChapter,
        previousChapterId: Long?,
        nextChapterId: Long?,
        settings: NovelReaderSettings,
    ) {
        if (nextChapterId != null && previousChapterId != null) return
        if (adjacentJaomixPageJob?.isActive == true) return
        val novel = currentNovel ?: return
        val source = sourceManager.get(novel.source) as? NovelJsSource ?: return
        if (!source.isJaomixPagedPlugin()) return
        val currentPage = ((chapter.sourceOrder / JAOMIX_PAGE_SOURCE_ORDER_STRIDE) + 1L).toInt().coerceAtLeast(1)
        val targetPage = when {
            nextChapterId == null -> currentPage + 1
            previousChapterId == null && currentPage > 1 -> currentPage - 1
            else -> null
        } ?: return
        if (!attemptedJaomixPages.add(targetPage)) return
        adjacentJaomixPageJob = screenModelScope.launch(Dispatchers.IO) {
            val pageResult = source.getChapterListPage(
                novel = novel.toSNovel(),
                page = targetPage,
            ) ?: return@launch
            val normalizedPageChapters = normalizeJaomixPageChapters(pageResult.chapters)
            if (normalizedPageChapters.isEmpty()) return@launch
            syncNovelChaptersWithSource.await(
                rawSourceChapters = normalizedPageChapters,
                novel = novel,
                source = source,
                manualFetch = true,
                retainMissingChapters = true,
                sourceOrderOffset = (pageResult.page - 1L) * JAOMIX_PAGE_SOURCE_ORDER_STRIDE,
            )
            chapterOrderList = loadChapterOrderList(novel.id)
            withContext(Dispatchers.Main.immediate) {
                updateContent(settings)
            }
        }
    }
    private fun normalizeJaomixPageChapters(
        chapters: List<eu.kanade.tachiyomi.novelsource.model.SNovelChapter>,
    ): List<eu.kanade.tachiyomi.novelsource.model.SNovelChapter> {
        if (chapters.size < 2) return chapters
        val hasChapterNumbers = chapters.any { it.chapter_number > 0f }
        return if (hasChapterNumbers) {
            chapters.sortedWith(
                compareBy<eu.kanade.tachiyomi.novelsource.model.SNovelChapter> { it.chapter_number }
                    .thenBy { it.name },
            )
        } else {
            chapters.asReversed()
        }
    }
    private fun updateContent(settings: NovelReaderSettings) {
        if (!settings.selectedTextTranslationEnabled) {
            clearSelectedTextTranslationSelection(refreshUi = false)
        }
        val html = rawHtml ?: return
        val novel = currentNovel ?: return
        val chapter = currentChapter ?: return
        if (!settings.geminiEnabled && isGeminiTranslating) {
            geminiTranslationJob?.cancel()
            geminiTranslationJob = null
            isGeminiTranslating = false
            isGeminiTranslationVisible = false
            geminiTranslationProgress = 0
            addGeminiLog("Gemini translation stopped because Gemini is disabled.")
        }
        val geminiVisibleInUi = settings.geminiEnabled && isGeminiTranslationVisible
        val geminiCacheAvailableInUi = settings.geminiEnabled && hasGeminiTranslationCache
        val googleVisibleInUi = settings.googleTranslationEnabled && isGoogleTranslationVisible
        val googleCacheAvailableInUi = settings.googleTranslationEnabled && hasGoogleTranslationCache
        val decodedNativeProgress = decodeNativeScrollProgress(chapter.lastPageRead)
        val decodedWebProgressPercent = decodeWebScrollProgressPercent(chapter.lastPageRead)
        val decodedPageReaderProgress = decodePageReaderProgress(chapter.lastPageRead)
        val lastSavedIndex = when {
            decodedNativeProgress != null -> decodedNativeProgress.index
            decodedPageReaderProgress != null -> 0
            decodedWebProgressPercent != null -> 0
            else -> chapter.lastPageRead.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        val lastSavedScrollOffsetPx = decodedNativeProgress?.offsetPx ?: 0
        val lastSavedWebProgressPercent = when {
            decodedWebProgressPercent != null -> decodedWebProgressPercent
            decodedNativeProgress != null || decodedPageReaderProgress != null -> 0
            else -> chapter.lastPageRead.coerceIn(0L, 100L).toInt()
        }
        val chapterNavigation = chapterOrderList.let { chapters ->
            val index = chapters.indexOfFirst { it.id == chapter.id }
            val previousChapter = chapters.getOrNull(index - 1)
            val nextChapter = chapters.getOrNull(index + 1)
            ChapterNavigation(
                previousChapterId = previousChapter?.id,
                previousChapterName = previousChapter?.name,
                nextChapterId = nextChapter?.id,
                nextChapterName = nextChapter?.name,
            )
        }
        maybeEnsureJaomixAdjacentPage(
            chapter = chapter,
            previousChapterId = chapterNavigation.previousChapterId,
            nextChapterId = chapterNavigation.nextChapterId,
            settings = settings,
        )
        val pluginCss = customCss
        val pluginJs = customJs
        val baseContent = normalizeHtml(
            rawHtml = html,
            settings = settings,
            customCss = pluginCss,
            customJs = pluginJs,
        )
        val baseContentBlocks = currentParsedContentBlocks()
        val baseTextBlocks = baseContentBlocks
            .filterIsInstance<ContentBlock.Text>()
            .map { it.text }
        val richContentResult = parsedRichContentResult
            ?: parseNovelRichContent(baseContent)
                .let { parsed ->
                    parsed.copy(
                        blocks = resolveRichContentBlocks(
                            blocks = parsed.blocks,
                            chapterWebUrl = chapterWebUrl,
                            novelUrl = novel.url,
                            pluginSite = pluginSite,
                        ),
                    )
                }
                .also { parsedRichContentResult = it }
        val displayContentBlocks = when {
            geminiVisibleInUi -> applyGeminiTranslationToContentBlocks(baseContentBlocks)
            googleVisibleInUi -> applyGoogleTranslationToContentBlocks(baseContentBlocks)
            else -> baseContentBlocks
        }
        if (googleVisibleInUi) {
            addGoogleLog(
                "Apply UI: baseBlocks=${baseContentBlocks.size}, textBlocks=${baseTextBlocks.size}, translatedSegments=${googleTranslatedByIndex.size}, visible=$googleVisibleInUi",
            )
        }
        val displayRichBlocks = if (geminiVisibleInUi) {
            applyGeminiTranslationToRichContentBlocks(richContentResult.blocks)
        } else if (googleVisibleInUi) {
            applyGoogleTranslationToRichContentBlocks(richContentResult.blocks)
        } else {
            richContentResult.blocks
        }
        val displayContent = when {
            geminiVisibleInUi && geminiTranslatedByIndex.isNotEmpty() -> normalizeHtml(
                rawHtml = buildRawHtmlFromContentBlocks(displayContentBlocks),
                settings = settings,
                customCss = pluginCss,
                customJs = pluginJs,
            )
            googleVisibleInUi && googleTranslatedByIndex.isNotEmpty() -> normalizeHtml(
                rawHtml = buildRawHtmlFromContentBlocks(displayContentBlocks),
                settings = settings,
                customCss = pluginCss,
                customJs = pluginJs,
            )
            else -> baseContent
        }
        ttsSessionController.setPreferredTranslatedText(shouldPreferTranslatedTts(settings))
        mutableState.value = State.Success(
            novel = novel,
            chapter = chapter,
            html = displayContent,
            enableJs = !pluginJs.isNullOrBlank() || settings.selectedTextTranslationEnabled,
            readerSettings = settings,
            contentBlocks = displayContentBlocks,
            richContentBlocks = displayRichBlocks,
            richContentUnsupportedFeaturesDetected = richContentResult.unsupportedFeaturesDetected,
            lastSavedIndex = lastSavedIndex,
            lastSavedScrollOffsetPx = lastSavedScrollOffsetPx,
            lastSavedWebProgressPercent = lastSavedWebProgressPercent,
            lastSavedPageReaderProgress = decodedPageReaderProgress,
            previousChapterId = chapterNavigation.previousChapterId,
            previousChapterName = chapterNavigation.previousChapterName,
            nextChapterId = chapterNavigation.nextChapterId,
            nextChapterName = chapterNavigation.nextChapterName,
            seriesInterstitialState = seriesInterstitialState,
            chapterWebUrl = chapterWebUrl,
            selectedTextTranslationSelection = selectedTextTranslationSelection,
            selectedTextTranslationUiState = selectedTextTranslationUiState,
            isGeminiTranslating = isGeminiTranslating,
            geminiTranslationProgress = geminiTranslationProgress,
            isGeminiTranslationVisible = geminiVisibleInUi,
            hasGeminiTranslationCache = geminiCacheAvailableInUi,
            geminiLogs = geminiLogs,
            isGoogleTranslating = isGoogleTranslating,
            googleTranslationProgress = googleTranslationProgress,
            isGoogleTranslationVisible = googleVisibleInUi,
            hasGoogleTranslationCache = googleCacheAvailableInUi,
            googleLogs = googleLogs,
            translationPhase = translationPhase,
            ttsUiState = ttsUiState.copy(
                enabled = settings.ttsEnabled,
                selectedEnginePackage = settings.ttsEnginePackage,
                selectedVoiceId = settings.ttsVoiceId,
                selectedLocaleTag = settings.ttsLocaleTag,
                speechRate = settings.ttsSpeechRate,
                pitch = settings.ttsPitch,
            ),
            airforceModelIds = airforceModelIds,
            isAirforceModelsLoading = isAirforceModelsLoading,
            isTestingAirforceConnection = isTestingAirforceConnection,
            openRouterModelIds = openRouterModelIds,
            isOpenRouterModelsLoading = isOpenRouterModelsLoading,
            isTestingOpenRouterConnection = isTestingOpenRouterConnection,
            deepSeekModelIds = deepSeekModelIds,
            isDeepSeekModelsLoading = isDeepSeekModelsLoading,
            isTestingDeepSeekConnection = isTestingDeepSeekConnection,
        )
    }
    private suspend fun refreshTtsEngines() {
        val engines = runCatching { ttsEngineRegistry.listEngines() }
            .getOrElse { emptyList() }
        ttsUiState = ttsUiState.copy(availableEngines = engines)
        refreshTtsUiState()
        initializeTtsRuntime()
    }

    private fun readRecentTtsLanguageTags(): List<String> {
        return novelReaderPreferences.ttsRecentLanguageTags().get()
            .split('|')
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
    }

    private fun rememberRecentTtsLanguage(localeTag: String) {
        if (localeTag.isBlank()) return
        val updatedTags = buildList {
            add(localeTag)
            addAll(readRecentTtsLanguageTags().filterNot { it.equals(localeTag, ignoreCase = true) })
        }.take(5)
        novelReaderPreferences.ttsRecentLanguageTags().set(updatedTags.joinToString("|"))
        ttsUiState = ttsUiState.copy(recentLanguageTags = updatedTags)
        refreshTtsUiState()
    }

    private suspend fun initializeTtsRuntime() {
        val settings = (mutableState.value as? State.Success)?.readerSettings ?: currentNovel?.source
            ?.let(novelReaderPreferences::resolveSettings)
            ?: return
        val recentLanguageTags = readRecentTtsLanguageTags()
        val preferredEngine = ttsEngineRegistry.resolvePreferredEngine(
            settings.ttsEnginePackage.takeIf { it.isNotBlank() },
        )
        if (!settings.ttsEnabled) {
            ttsWordProgressJob?.cancel()
            ttsWordProgressJob = null
            pendingTtsStartRequest = null
            ttsAudioFocusManager.abandonPlaybackFocus()
            ttsSessionController.stop()
            ttsEngine.shutdown()
            initializedTtsEnginePackage = null
            ttsUiState = ttsUiState.copy(
                enabled = false,
                playbackState = NovelTtsPlaybackState.IDLE,
                activeSession = null,
                activeHighlightMode = NovelTtsHighlightMode.OFF,
                activeWordRange = null,
                activeUtteranceText = null,
                activeSourceBlockIndex = null,
                availableVoices = emptyList(),
                availableLocales = emptyList(),
                recentLanguageTags = recentLanguageTags,
                isLoadingVoices = false,
                selectedEnginePackage = "",
                selectedVoiceId = "",
                selectedLocaleTag = "",
                speechRate = settings.ttsSpeechRate,
                pitch = settings.ttsPitch,
                errorMessage = null,
            )
            refreshTtsUiState()
            return
        }

        val targetEnginePackage = preferredEngine?.packageName
        val enginePackageChanged = initializedTtsEnginePackage != targetEnginePackage
        if (enginePackageChanged || ttsUiState.availableVoices.isEmpty()) {
            ttsUiState = ttsUiState.copy(
                enabled = true,
                availableVoices = emptyList(),
                availableLocales = emptyList(),
                recentLanguageTags = recentLanguageTags,
                isLoadingVoices = true,
                selectedEnginePackage = targetEnginePackage.orEmpty(),
                selectedVoiceId = "",
                selectedLocaleTag = settings.ttsLocaleTag,
                speechRate = settings.ttsSpeechRate,
                pitch = settings.ttsPitch,
                errorMessage = null,
            )
            refreshTtsUiState()
        }

        runCatching {
            ttsEngine.initialize(targetEnginePackage)
            ttsEngine.setSpeechRate(settings.ttsSpeechRate)
            ttsEngine.setPitch(settings.ttsPitch)
            val capabilities = ttsEngine.capabilities()
            val availableVoices = ttsEngine.availableVoices()
            val availableLocales = ttsEngine.availableLocales()
            val selection = resolveNovelTtsVoiceSelection(
                availableVoices = availableVoices,
                availableLocales = availableLocales,
                capabilities = capabilities,
                preferredVoiceId = settings.ttsVoiceId,
                preferredLocaleTag = settings.ttsLocaleTag,
            )
            ttsEngine.setLocale(selection.selectedLocaleTag.takeIf { it.isNotBlank() })
            ttsEngine.setVoice(selection.selectedVoiceId.takeIf { it.isNotBlank() })
            initializedTtsEnginePackage = targetEnginePackage
            ttsUiState = ttsUiState.copy(
                enabled = true,
                availableVoices = availableVoices,
                availableLocales = availableLocales,
                recentLanguageTags = recentLanguageTags,
                isLoadingVoices = false,
                selectedEnginePackage = targetEnginePackage.orEmpty(),
                selectedVoiceId = selection.selectedVoiceId,
                selectedLocaleTag = selection.selectedLocaleTag,
                speechRate = settings.ttsSpeechRate,
                pitch = settings.ttsPitch,
                capabilities = capabilities,
                activeHighlightMode = capabilities.resolveHighlightMode(settings.ttsHighlightMode),
                errorMessage = null,
            )
        }.onFailure { error ->
            logcat(LogPriority.WARN, error) { "Failed to initialize novel reader TTS" }
            initializedTtsEnginePackage = null
            ttsUiState = ttsUiState.copy(
                enabled = settings.ttsEnabled,
                availableVoices = emptyList(),
                availableLocales = emptyList(),
                recentLanguageTags = recentLanguageTags,
                isLoadingVoices = false,
                selectedEnginePackage = targetEnginePackage.orEmpty(),
                selectedVoiceId = settings.ttsVoiceId,
                selectedLocaleTag = settings.ttsLocaleTag,
                speechRate = settings.ttsSpeechRate,
                pitch = settings.ttsPitch,
                errorMessage = error.message,
            )
        }
        refreshTtsUiState()
    }

    private suspend fun maybeRestoreTtsAfterChapterHandoff(
        chapterId: Long,
        settings: NovelReaderSettings,
    ) {
        if (!settings.ttsEnabled) return
        if (!NovelReaderTtsChapterHandoffPolicy.consumePendingRestore(chapterId)) return
        if (!ttsAudioFocusManager.requestPlaybackFocus()) return
        ttsSessionController.restoreFromCheckpoint()
    }

    private suspend fun resolveTtsChapter(targetChapterId: Long): NovelTtsResolvedChapter? {
        val snapshot = ttsChapterRepository.loadChapterSnapshot(targetChapterId)
        val source = sourceManager.get(snapshot.novel.source) ?: return null
        val normalizedHtml = withContext(Dispatchers.Default) {
            val withHeading = prependChapterHeadingIfMissing(
                rawHtml = snapshot.rawHtml.normalizeStructuredChapterPayload(),
                chapterName = snapshot.chapter.name,
            )
            val sanitized = sanitizeChapterHtmlForReader(withHeading)
            if (sanitized.isBlank()) withHeading else sanitized
        }
        val chapterWebUrl = resolveChapterWebUrl(
            source = source,
            chapterUrl = snapshot.chapter.url,
            novelUrl = snapshot.novel.url,
            pluginSite = snapshot.pluginSite,
        )
        val parsedBlocks = withContext(Dispatchers.Default) {
            extractContentBlocks(
                rawHtml = normalizedHtml,
                chapterWebUrl = chapterWebUrl,
                novelUrl = snapshot.novel.url,
                pluginSite = snapshot.pluginSite,
            ).ifEmpty {
                extractTextBlocks(normalizedHtml).map(ContentBlock::Text)
            }
        }
        val normalizedContent = normalizeHtml(
            rawHtml = normalizedHtml,
            settings = novelReaderPreferences.resolveSettings(snapshot.novel.source),
            customCss = snapshot.customCss,
            customJs = snapshot.customJs,
        )
        val richBlocks = parseNovelRichContent(normalizedContent)
            .let { parsed ->
                resolveRichContentBlocks(
                    blocks = parsed.blocks,
                    chapterWebUrl = chapterWebUrl,
                    novelUrl = snapshot.novel.url,
                    pluginSite = snapshot.pluginSite,
                )
            }
        val currentSettings = novelReaderPreferences.resolveSettings(snapshot.novel.source)
        val originalModel = ttsChapterModelBuilder.build(
            chapterId = snapshot.chapter.id,
            chapterTitle = snapshot.chapter.name,
            contentBlocks = parsedBlocks,
            richContentBlocks = richBlocks,
            options = NovelTtsChapterModelBuildOptions(
                includeChapterTitle = currentSettings.ttsReadChapterTitle,
            ),
        )
        val translatedModel = resolveTranslatedTtsChapterModel(
            chapterId = targetChapterId,
            chapterTitle = snapshot.chapter.name,
            originalContentBlocks = parsedBlocks,
            richContentBlocks = richBlocks,
            settings = currentSettings,
        )
        val nextChapterId = snapshot.chapterOrderList
            .indexOfFirst { it.id == snapshot.chapter.id }
            .takeIf { it >= 0 }
            ?.let { snapshot.chapterOrderList.getOrNull(it + 1)?.id }
        return NovelTtsResolvedChapter(
            chapterId = snapshot.chapter.id,
            nextChapterId = nextChapterId,
            originalModel = originalModel,
            translatedModel = translatedModel,
        )
    }

    private fun resolveTranslatedTtsChapterModel(
        chapterId: Long,
        chapterTitle: String,
        originalContentBlocks: List<ContentBlock>,
        richContentBlocks: List<NovelRichContentBlock>,
        settings: NovelReaderSettings,
    ): eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsChapterModel? {
        if (!shouldPreferTranslatedTts(settings)) return null
        if (chapterId != currentChapter?.id) return null
        val translatedBlocks = when {
            settings.geminiEnabled && geminiTranslatedByIndex.isNotEmpty() -> {
                applyGeminiTranslationToContentBlocks(originalContentBlocks, forceTranslation = true)
            }
            settings.googleTranslationEnabled && googleTranslatedByIndex.isNotEmpty() -> {
                applyGoogleTranslationToContentBlocks(originalContentBlocks)
            }
            else -> return null
        }
        return ttsChapterModelBuilder.build(
            chapterId = chapterId,
            chapterTitle = chapterTitle,
            contentBlocks = translatedBlocks,
            richContentBlocks = emptyList(),
            options = NovelTtsChapterModelBuildOptions(
                includeChapterTitle = settings.ttsReadChapterTitle,
            ),
        )
    }

    private suspend fun onTtsSessionStateChanged(sessionState: NovelTtsSessionUiState) {
        val session = sessionState.session
        val activeUtterance = session?.utterance
        ttsUiState = ttsUiState.copy(
            playbackState = sessionState.playbackState,
            activeSession = session,
            activeUtteranceText = activeUtterance?.text,
            activeSourceBlockIndex = activeUtterance?.sourceBlockIndex,
            activeWordRange = activeUtterance?.wordRanges?.getOrNull(session.wordIndex),
        )
        refreshTtsUiState()
    }

    private suspend fun handleTtsUtteranceStarted(utteranceId: String) {
        val session = ttsSessionController.state.value.session ?: return
        if (session.utterance.id != utteranceId) return
        ttsWordProgressJob?.cancel()
        startEstimatedTtsWordProgress(session)
    }

    private fun startEstimatedTtsWordProgress(session: NovelTtsSession) {
        val highlightMode = ttsUiState.activeHighlightMode
        if (highlightMode == NovelTtsHighlightMode.OFF) return
        ttsWordProgressJob = screenModelScope.launch {
            val utterance = session.utterance
            val estimatedDurationMs = estimateTtsUtteranceDurationMs(
                utterance = utterance,
                speechRate = ttsUiState.speechRate,
            )
            val startTimeMs = SystemClock.elapsedRealtime()
            while (isActive) {
                val elapsedMs = (SystemClock.elapsedRealtime() - startTimeMs).coerceAtLeast(0L)
                val selection = ttsHighlightEstimator.estimateWordRange(
                    utterance = utterance,
                    elapsedMs = elapsedMs,
                    durationMs = estimatedDurationMs,
                    mode = highlightMode,
                    startWordIndex = session.wordIndex,
                )
                if (selection != null) {
                    ttsSessionController.updateWordProgress(selection.wordIndex)
                    ttsUiState = ttsUiState.copy(activeWordRange = selection.wordRange)
                    refreshTtsUiState()
                }
                if (elapsedMs >= estimatedDurationMs) break
                delay(TTS_WORD_PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun estimateTtsUtteranceDurationMs(
        utterance: eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsUtterance,
        speechRate: Float,
    ): Long {
        val words = utterance.wordRanges.size.coerceAtLeast(1)
        val effectiveRate = speechRate.coerceAtLeast(0.5f)
        val millisPerWord = (TTS_BASE_MILLIS_PER_WORD / effectiveRate).roundToInt()
        return (words * millisPerWord)
            .coerceAtLeast(TTS_MIN_UTTERANCE_DURATION_MS.toInt())
            .toLong()
    }

    private fun refreshTtsUiState() {
        val state = mutableState.value
        if (state is State.Success) {
            mutableState.value = state.copy(ttsUiState = ttsUiState)
        }
    }
    private suspend fun parseAndCacheContentBlocks(
        rawHtml: String,
        chapterWebUrl: String?,
        novelUrl: String,
        pluginSite: String?,
    ) {
        val blocks = withContext(Dispatchers.Default) {
            val extractedBlocks = extractContentBlocks(
                rawHtml = rawHtml,
                chapterWebUrl = chapterWebUrl,
                novelUrl = novelUrl,
                pluginSite = pluginSite,
            ).ifEmpty {
                extractTextBlocks(rawHtml).map(ContentBlock::Text)
            }
            extractedBlocks
        }
        parsedContentBlocks = blocks
        parsedRichContentResult = null
    }
    private suspend fun resolveChapterWebUrl(
        source: eu.kanade.tachiyomi.novelsource.NovelSource,
        chapterUrl: String,
        novelUrl: String,
        pluginSite: String?,
    ): String? {
        val sourceResolved = (source as? NovelWebUrlSource)
            ?.getChapterWebUrl(chapterPath = chapterUrl, novelPath = novelUrl)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (sourceResolved != null) {
            sourceResolved.toHttpUrlOrNull()?.let { return it.toString() }
            resolveNovelChapterWebUrl(
                chapterUrl = sourceResolved,
                pluginSite = pluginSite,
                novelUrl = novelUrl,
            )?.let { return it }
        }
        return resolveNovelChapterWebUrl(
            chapterUrl = chapterUrl,
            pluginSite = pluginSite,
            novelUrl = novelUrl,
        )
    }
    fun updateReadingProgress(
        currentIndex: Int,
        totalItems: Int,
        persistedProgress: Long? = null,
    ) {
        val chapter = currentChapter ?: return
        if (totalItems <= 0 || currentIndex < 0) return
        val resolvedPersistedProgress = persistedProgress ?: currentIndex.toLong()
        if (!hasProgressChanged) {
            val isSameInitialIndex = currentIndex == initialProgressIndex
            val isSamePersistedProgress = lastSavedProgress == resolvedPersistedProgress
            if (totalItems > 1 && isSameInitialIndex && isSamePersistedProgress) return
            hasProgressChanged = true
        }
        val readThreshold = when {
            totalItems == 100 -> 0.99f
            else -> 0.95f
        }
        val reachedReadThreshold = totalItems == 1 ||
            ((currentIndex + 1).toFloat() / totalItems.toFloat()) >= readThreshold
        val shouldPersistRead = (lastSavedRead == true) || chapter.read || reachedReadThreshold
        val newProgress = if (shouldPersistRead) {
            maxOf(lastSavedProgress ?: 0L, resolvedPersistedProgress)
        } else {
            resolvedPersistedProgress
        }
        maybePrefetchNextChapterOnProgress(
            currentIndex = currentIndex,
            totalItems = totalItems,
        )
        maybePrefetchNextChapterGeminiTranslationOnProgress(
            currentIndex = currentIndex,
            totalItems = totalItems,
        )
        if (lastSavedRead == shouldPersistRead && lastSavedProgress == newProgress) {
            return
        }
        val becameRead = !chapter.read && shouldPersistRead
        lastSavedRead = shouldPersistRead
        lastSavedProgress = newProgress
        applyLocalChapterProgress(
            chapter = chapter,
            read = shouldPersistRead,
            progress = newProgress,
        )
        maybeShowSeriesInterstitial(
            chapter = chapter,
            becameRead = becameRead,
        )
        val shouldEmitNovelCompleted = becameRead && chapterOrderList.all { it.read }
        enqueueProgressPersistence(
            PendingProgressPersistence(
                chapterId = chapter.id,
                novelId = chapter.novelId,
                chapterNumber = chapter.chapterNumber.toInt(),
                read = shouldPersistRead,
                lastPageRead = newProgress,
                emitReadEvent = becameRead,
                emitNovelCompleted = shouldEmitNovelCompleted,
                sessionReadDurationMs = System.currentTimeMillis() - chapterReadStartTimeMs,
            ),
        )
    }
    fun toggleTtsPlayback(
        startRequest: eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackStartRequest =
            eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackStartRequest(),
    ) {
        val state = mutableState.value as? State.Success ?: return
        if (!state.readerSettings.ttsEnabled) return
        screenModelScope.launch {
            initializeTtsRuntime()
            val playbackState = ttsSessionController.state.value.playbackState
            when (playbackState) {
                eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackState.PLAYING -> {
                    pendingTtsStartRequest = null
                    ttsSessionController.pause()
                }
                eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackState.PAUSED -> {
                    val pendingRequest = pendingTtsStartRequest
                    if (!ttsAudioFocusManager.requestPlaybackFocus()) return@launch
                    if (pendingRequest != null) {
                        startTtsFromRequest(pendingRequest, state.readerSettings)
                    } else {
                        ttsSessionController.resume()
                    }
                    pendingTtsStartRequest = null
                }
                else -> {
                    if (!ttsAudioFocusManager.requestPlaybackFocus()) return@launch
                    startTtsFromRequest(startRequest, state.readerSettings)
                    pendingTtsStartRequest = null
                }
            }
        }
    }

    fun stopTtsPlayback() {
        screenModelScope.launch {
            ttsWordProgressJob?.cancel()
            ttsAudioFocusManager.abandonPlaybackFocus()
            ttsSessionController.stop()
        }
    }

    fun skipToNextTtsSegment() {
        screenModelScope.launch {
            ttsSessionController.skipNext()
        }
    }

    fun skipToPreviousTtsSegment() {
        screenModelScope.launch {
            ttsSessionController.skipPrevious()
        }
    }

    fun pauseTtsForManualNavigation(
        startRequest: eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackStartRequest,
    ) {
        val state = mutableState.value as? State.Success ?: return
        if (!state.readerSettings.ttsPauseOnManualNavigation) return
        if (ttsSessionController.state.value.playbackState !=
            eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackState.PLAYING
        ) {
            pendingTtsStartRequest = startRequest
            return
        }
        screenModelScope.launch {
            pendingTtsStartRequest = startRequest
            ttsSessionController.pause()
        }
    }

    fun setTtsEnginePackage(value: String) = updateTtsSetting(
        setGlobal = { novelReaderPreferences.ttsEnginePackage().set(value) },
        setOverride = { it.copy(ttsEnginePackage = value) },
    )

    fun setTtsVoiceId(value: String) {
        val localeTag = ttsUiState.availableVoices
            .firstOrNull { it.id == value }
            ?.localeTag
            ?: ttsUiState.selectedLocaleTag
        updateTtsSetting(
            setGlobal = {
                novelReaderPreferences.ttsVoiceId().set(value)
                if (localeTag.isNotBlank()) {
                    novelReaderPreferences.ttsLocaleTag().set(localeTag)
                }
            },
            setOverride = {
                it.copy(
                    ttsVoiceId = value,
                    ttsLocaleTag = localeTag.takeIf(String::isNotBlank) ?: it.ttsLocaleTag,
                )
            },
        )
        rememberRecentTtsLanguage(localeTag)
    }

    fun setTtsLocaleTag(value: String) {
        updateTtsSetting(
            setGlobal = { novelReaderPreferences.ttsLocaleTag().set(value) },
            setOverride = { it.copy(ttsLocaleTag = value) },
        )
        rememberRecentTtsLanguage(value)
    }

    fun setTtsSpeechRate(value: Float) = updateTtsSetting(
        setGlobal = { novelReaderPreferences.ttsSpeechRate().set(value) },
        setOverride = { it.copy(ttsSpeechRate = value) },
    )

    fun setTtsPitch(value: Float) = updateTtsSetting(
        setGlobal = { novelReaderPreferences.ttsPitch().set(value) },
        setOverride = { it.copy(ttsPitch = value) },
    )

    fun disableTts() {
        val currentState = mutableState.value as? State.Success ?: return
        if (!currentState.readerSettings.ttsEnabled) return
        val sourceId = currentNovel?.source ?: return
        screenModelScope.launch {
            ttsWordProgressJob?.cancel()
            ttsWordProgressJob = null
            pendingTtsStartRequest = null
            ttsAudioFocusManager.abandonPlaybackFocus()
            ttsSessionController.stop()
            if (novelReaderPreferences.getSourceOverride(sourceId) != null) {
                novelReaderPreferences.updateSourceOverride(sourceId) {
                    it.copy(ttsEnabled = false)
                }
            } else {
                novelReaderPreferences.ttsEnabled().set(false)
            }
            updateContent(currentState.readerSettings.copy(ttsEnabled = false))
        }
    }

    fun createTtsPlaybackServiceRuntime(): NovelTtsPlaybackServiceRuntime {
        return NovelTtsPlaybackServiceRuntime(
            controller = ttsSessionController,
            audioFocusManager = ttsAudioFocusManager,
        )
    }

    private suspend fun startTtsFromRequest(
        startRequest: eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackStartRequest,
        settings: NovelReaderSettings,
    ) {
        val resolvedChapter = resolveTtsChapter(targetChapterId = currentChapter?.id ?: return) ?: return
        val useTranslatedText = shouldPreferTranslatedTts(settings) &&
            resolvedChapter.translatedModel != null
        val sessionModel = if (useTranslatedText) {
            resolvedChapter.translatedModel
        } else {
            resolvedChapter.originalModel
        }
        ttsSessionController.setPreferredTranslatedText(useTranslatedText)
        val utteranceId = startRequest.pageReaderPosition?.let { pageReaderPosition ->
            val utteranceAnchors = eu.kanade.tachiyomi.ui.reader.novel.tts.resolvePlainPageReaderTtsAnchors(
                textBlocks = pageReaderPosition.blockTexts,
                pages = pageReaderPosition.pages,
                chapterModel = sessionModel,
            )
            eu.kanade.tachiyomi.ui.reader.novel.tts.resolvePageReaderTtsStartUtteranceId(
                pageIndex = pageReaderPosition.pageIndex,
                fallbackBlockIndex = startRequest.fallbackBlockIndex,
                chapterModel = sessionModel,
                utteranceAnchors = utteranceAnchors,
            )
        } ?: sessionModel.utterances
            .firstOrNull { it.sourceBlockIndex >= startRequest.fallbackBlockIndex }
            ?.id
            ?: sessionModel.utterances.firstOrNull()?.id
        ttsSessionController.startFromCurrentPosition(
            chapterId = resolvedChapter.chapterId,
            utteranceId = utteranceId,
            preferTranslatedText = useTranslatedText,
            autoAdvanceChapter = settings.ttsAutoAdvanceChapter,
        )
    }

    private fun shouldPreferTranslatedTts(settings: NovelReaderSettings): Boolean {
        return settings.ttsPreferTranslatedText ||
            isGeminiTranslationVisible ||
            isGoogleTranslationVisible
    }
    private fun enqueueProgressPersistence(update: PendingProgressPersistence) {
        progressPersistenceScheduled = true
        screenModelScope.launch(NonCancellable) {
            progressPersistenceMutex.withLock {
                pendingProgressPersistence = pendingProgressPersistence?.merge(update) ?: update
                if (progressPersistenceJob?.isActive == true) {
                    return@launch
                }
                progressPersistenceJob = screenModelScope.launch(NonCancellable) {
                    try {
                        flushPendingProgressPersistence()
                    } finally {
                        progressPersistenceMutex.withLock {
                            progressPersistenceJob = null
                            progressPersistenceScheduled = pendingProgressPersistence != null
                        }
                    }
                }
            }
        }
    }
    suspend fun awaitPendingProgressPersistence() {
        while (true) {
            val activeJob = progressPersistenceMutex.withLock {
                progressPersistenceJob?.takeIf { it.isActive }
            }
            if (activeJob != null) {
                activeJob.join()
                continue
            }
            if (!progressPersistenceScheduled) return
            kotlinx.coroutines.yield()
        }
    }
    private suspend fun flushPendingProgressPersistence() {
        while (true) {
            val nextUpdate = progressPersistenceMutex.withLock {
                val next = pendingProgressPersistence ?: return
                pendingProgressPersistence = null
                next
            }

            novelChapterRepository.updateChapter(
                NovelChapterUpdate(
                    id = nextUpdate.chapterId,
                    read = nextUpdate.read,
                    lastPageRead = nextUpdate.lastPageRead,
                ),
            )

            if (nextUpdate.emitReadEvent) {
                eventBus?.tryEmit(
                    AchievementEvent.NovelChapterRead(
                        novelId = nextUpdate.novelId,
                        chapterNumber = nextUpdate.chapterNumber,
                    ),
                )
                if (nextUpdate.emitNovelCompleted) {
                    eventBus?.tryEmit(AchievementEvent.NovelCompleted(nextUpdate.novelId))
                }
            }

            val now = System.currentTimeMillis()
            pendingHistoryReadDurationMs += nextUpdate.sessionReadDurationMs.coerceAtLeast(0L)
            if (nextUpdate.emitReadEvent) {
                flushPendingHistorySnapshot(nextUpdate.chapterId)
            }
            chapterReadStartTimeMs = now
        }
    }
    private fun maybePrefetchNextChapterOnProgress(
        currentIndex: Int,
        totalItems: Int,
    ) {
        if (hasTriggeredNextChapterPrefetch) return
        if (!hasReachedNextChapterPrefetchThreshold(currentIndex, totalItems)) return
        val state = mutableState.value as? State.Success ?: return
        if (!state.readerSettings.prefetchNextChapter) return
        val novel = currentNovel ?: return
        val chapter = currentChapter ?: return
        val source = sourceManager.get(novel.source) ?: return
        hasTriggeredNextChapterPrefetch = true
        scheduleNextChapterPrefetch(
            novel = novel,
            currentChapter = chapter,
            source = source,
        )
    }
    private fun maybePrefetchNextChapterGeminiTranslationOnProgress(
        currentIndex: Int,
        totalItems: Int,
    ) {
        if (hasTriggeredNextChapterGeminiPrefetch) return
        if (!hasReachedGeminiNextChapterTranslationPrefetchThreshold(currentIndex, totalItems)) return
        val state = mutableState.value as? State.Success ?: return
        val settings = state.readerSettings
        if (!settings.geminiEnabled || !settings.geminiPrefetchNextChapterTranslation) return
        if (settings.geminiDisableCache) return
        if (!settings.hasConfiguredTranslationProvider()) return
        val novel = currentNovel ?: return
        val chapter = currentChapter ?: return
        val source = sourceManager.get(novel.source) ?: return
        val nextChapter = findNextChapter(chapter) ?: return
        if (hasReusableTranslationCache(nextChapter.id, settings)) return
        hasTriggeredNextChapterGeminiPrefetch = true
        scheduleNextChapterGeminiTranslationPrefetch(
            nextChapter = nextChapter,
            source = source,
            settings = settings,
        )
    }
    private fun hasReachedNextChapterPrefetchThreshold(
        currentIndex: Int,
        totalItems: Int,
    ): Boolean {
        if (totalItems <= 0 || currentIndex < 0) return false
        return if (totalItems == 100) {
            currentIndex >= 50
        } else {
            totalItems > 1 && ((currentIndex + 1).toFloat() / totalItems.toFloat()) >= 0.5f
        }
    }
    private fun scheduleNextChapterGeminiTranslationPrefetch(
        nextChapter: NovelChapter,
        source: eu.kanade.tachiyomi.novelsource.NovelSource,
        settings: NovelReaderSettings,
    ) {
        if (hasReusableTranslationCache(nextChapter.id, settings)) return
        nextChapterGeminiPrefetchJob?.cancel()
        nextChapterGeminiPrefetchJob = screenModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (hasReusableTranslationCache(nextChapter.id, settings)) return@runCatching
                val cacheReadChapters = novelReaderPreferences.cacheReadChapters().get()
                val nextHtml = NovelReaderChapterPrefetchCache.get(nextChapter.id)
                    ?: source.getChapterText(nextChapter.toSNovelChapter()).also { fetchedHtml ->
                        NovelReaderChapterPrefetchCache.put(nextChapter.id, fetchedHtml)
                        if (cacheReadChapters) {
                            NovelReaderChapterDiskCacheStore.put(nextChapter.id, fetchedHtml)
                        }
                    }
                if (nextHtml.isBlank()) return@runCatching
                val normalizedNextHtml = prependChapterHeadingIfMissing(
                    rawHtml = nextHtml.normalizeStructuredChapterPayload(),
                    chapterName = nextChapter.name,
                )
                val sanitizedNextHtml = sanitizeChapterHtmlForReader(normalizedNextHtml)
                    .ifBlank { normalizedNextHtml }
                val nextTextBlocks = extractTextBlocks(sanitizedNextHtml)
                if (nextTextBlocks.isEmpty()) return@runCatching
                val chunkSize = settings.geminiBatchSize.coerceIn(1, 80)
                val chunks = nextTextBlocks.chunked(chunkSize)
                val semaphore = Semaphore(settings.translationConcurrencyLimit())
                val translated = mutableMapOf<Int, String>()
                addGeminiLog("?? ${settings.translationRequestConfigLog()} (prefetch)")
                coroutineScope {
                    chunks.mapIndexed { chunkIndex, chunk ->
                        async {
                            semaphore.withPermit {
                                val result = requestTranslationBatch(
                                    segments = chunk,
                                    settings = settings,
                                ) { message ->
                                    addGeminiLog("?? Next chapter: $message")
                                }
                                if (result == null && !settings.geminiRelaxedMode) {
                                    throw IllegalStateException(
                                        "${settings.translationProvider} returned empty response for prefetched chunk ${chunkIndex + 1}",
                                    )
                                }
                                result.orEmpty().forEachIndexed { localIndex, text ->
                                    if (!text.isNullOrBlank()) {
                                        val globalIndex = chunkIndex * chunkSize + localIndex
                                        translated[globalIndex] = text
                                    }
                                }
                            }
                        }
                    }.awaitAll()
                }
                if (translated.isEmpty()) return@runCatching
                NovelReaderTranslationDiskCacheStore.put(
                    GeminiTranslationCacheEntry(
                        chapterId = nextChapter.id,
                        translatedByIndex = translated.toMap(),
                        provider = settings.translationProvider,
                        model = settings.translationCacheModelId(),
                        sourceLang = settings.geminiSourceLang,
                        targetLang = settings.geminiTargetLang,
                        promptMode = settings.geminiPromptMode,
                        stylePreset = settings.geminiStylePreset,
                    ),
                )
                addGeminiLog("?? Cached ${settings.translationProvider} translation for next chapter ${nextChapter.id}")
            }.onFailure { error ->
                logcat(LogPriority.WARN, error) { "Failed to prefetch Gemini translation for next chapter" }
                addGeminiLog("?? Next chapter prefetch failed: ${formatGeminiThrowableForLog(error)}")
            }
        }
    }
    private fun hasReusableTranslationCache(
        chapterId: Long,
        settings: NovelReaderSettings,
    ): Boolean {
        val cached = NovelReaderTranslationDiskCacheStore.get(chapterId) ?: return false
        if (cached.translatedByIndex.isEmpty()) return false
        return cached.provider == settings.translationProvider &&
            cached.model == settings.translationCacheModelId() &&
            cached.sourceLang == settings.geminiSourceLang &&
            cached.targetLang == settings.geminiTargetLang &&
            cached.promptMode == settings.geminiPromptMode &&
            cached.stylePreset == settings.geminiStylePreset
    }
    private fun applyLocalChapterProgress(
        chapter: NovelChapter,
        read: Boolean,
        progress: Long,
    ) {
        val bookmark = currentChapter?.bookmark ?: chapter.bookmark
        val updatedChapter = chapter.copy(
            read = read,
            lastPageRead = progress,
            bookmark = bookmark,
        )
        currentChapter = updatedChapter
        chapterOrderList = updateNovelReaderChapterProgressList(
            chapters = chapterOrderList,
            chapterId = chapter.id,
            read = read,
            progress = progress,
        )
        val currentState = mutableState.value
        if (currentState is State.Success) {
            val decodedNativeProgress = decodeNativeScrollProgress(progress)
            val decodedWebProgressPercent = decodeWebScrollProgressPercent(progress)
            val decodedPageReaderProgress = decodePageReaderProgress(progress)
            val lastSavedIndex = when {
                decodedNativeProgress != null -> decodedNativeProgress.index
                decodedPageReaderProgress != null -> 0
                decodedWebProgressPercent != null -> currentState.lastSavedIndex
                else -> progress.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
            val lastSavedScrollOffsetPx = decodedNativeProgress?.offsetPx ?: 0
            val lastSavedWebProgressPercent = when {
                decodedWebProgressPercent != null -> decodedWebProgressPercent
                decodedNativeProgress != null || decodedPageReaderProgress != null -> 0
                else -> progress.coerceIn(0L, 100L).toInt()
            }
            mutableState.value = currentState.copy(
                chapter = updatedChapter,
                lastSavedIndex = lastSavedIndex,
                lastSavedScrollOffsetPx = lastSavedScrollOffsetPx,
                lastSavedWebProgressPercent = lastSavedWebProgressPercent,
                lastSavedPageReaderProgress = decodedPageReaderProgress,
            )
        }
    }
    fun toggleChapterBookmark() {
        val chapter = currentChapter ?: return
        val bookmarked = !chapter.bookmark
        val updatedChapter = chapter.copy(bookmark = bookmarked)
        currentChapter = updatedChapter
        lastSavedRead = updatedChapter.read
        lastSavedProgress = updatedChapter.lastPageRead
        val state = mutableState.value
        if (state is State.Success) {
            mutableState.value = state.copy(chapter = updatedChapter)
        }
        screenModelScope.launch {
            novelChapterRepository.updateChapter(
                NovelChapterUpdate(
                    id = chapter.id,
                    bookmark = bookmarked,
                ),
            )
        }
    }
    override fun onDispose() {
        val chapterId = currentChapter?.id
        if (chapterId != null) {
            val finalReadDurationMs = (System.currentTimeMillis() - chapterReadStartTimeMs).coerceAtLeast(0L)
            runBlocking(NonCancellable) {
                awaitPendingProgressPersistence()
                flushPendingHistorySnapshot(
                    chapterId = chapterId,
                    additionalReadDurationMs = finalReadDurationMs,
                )
            }
        }
        clearChapterTransientState()
        runBlocking(NonCancellable) {
            settingsJob?.cancelAndJoin()
            nextChapterPrefetchJob?.cancelAndJoin()
            nextChapterGeminiPrefetchJob?.cancelAndJoin()
            adjacentJaomixPageJob?.cancelAndJoin()
            geminiTranslationJob?.cancelAndJoin()
            queueProgressJob?.cancelAndJoin()
            googleTranslationJob?.cancelAndJoin()
            selectedTextTranslationJob?.cancelAndJoin()
            progressPersistenceJob?.cancelAndJoin()
            ttsWordProgressJob?.cancelAndJoin()
        }
        ttsAudioFocusManager.abandonPlaybackFocus()
        ttsEngine.shutdown()
        super.onDispose()
    }

    private fun clearChapterTransientState() {
        currentNovel = null
        currentChapter = null
        chapterOrderList = emptyList()
        rawHtml = null
        customCss = null
        customJs = null
        pluginSite = null
        chapterWebUrl = null
        parsedContentBlocks = null
        parsedRichContentResult = null
        lastSavedProgress = null
        lastSavedRead = null
        initialProgressIndex = 0
        hasProgressChanged = false
        hasTriggeredNextChapterPrefetch = false
        hasTriggeredNextChapterGeminiPrefetch = false
        hasTriggeredGeminiAutoStart = false
        adjacentJaomixPageJob?.cancel()
        adjacentJaomixPageJob = null
        nextChapterPrefetchJob?.cancel()
        nextChapterPrefetchJob = null
        nextChapterGeminiPrefetchJob?.cancel()
        nextChapterGeminiPrefetchJob = null
        geminiTranslationJob?.cancel()
        geminiTranslationJob = null
        queueProgressJob?.cancel()
        queueProgressJob = null
        googleTranslationJob?.cancel()
        googleTranslationJob = null
        clearSelectedTextTranslationSelection(refreshUi = false)
        selectedTextTranslationSessionCache.clear()
        attemptedJaomixPages.clear()
        geminiTranslatedByIndex = emptyMap()
        googleTranslatedByIndex = emptyMap()
        isGeminiTranslating = false
        isGoogleTranslating = false
        geminiTranslationProgress = 0
        googleTranslationProgress = 0
        isGeminiTranslationVisible = false
        isGoogleTranslationVisible = false
        hasGeminiTranslationCache = false
        hasGoogleTranslationCache = false
        geminiLogs = emptyList()
        googleLogs = emptyList()
        googleRateLimited = false
        isAirforceModelsLoading = false
        isTestingAirforceConnection = false
        isOpenRouterModelsLoading = false
        isTestingOpenRouterConnection = false
        isDeepSeekModelsLoading = false
        isTestingDeepSeekConnection = false
        ttsWordProgressJob?.cancel()
        ttsWordProgressJob = null
        pendingTtsStartRequest = null
        seriesInterstitialState = null
        seriesInterstitialShownForChapterId = null
        chapterReadStartTimeMs = System.currentTimeMillis()
    }
    fun addGeminiLog(message: String) {
        val text = message.trim()
        if (text.isBlank()) return
        geminiLogs = (listOf(text) + geminiLogs).take(100)
        refreshGeminiUiState()
    }
    fun clearGeminiLogs() {
        geminiLogs = emptyList()
        refreshGeminiUiState()
    }
    fun clearAllGeminiTranslationCache() {
        NovelReaderTranslationDiskCacheStore.clear()
        addGeminiLog("??? Clear ALL cache")
        val chapter = currentChapter ?: return
        if (NovelReaderTranslationDiskCacheStore.get(chapter.id) == null) {
            hasGeminiTranslationCache = false
            refreshGeminiUiState()
        }
    }
    fun setGeminiApiKey(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.geminiApiKey().set(value) },
        setOverride = { it.copy(geminiApiKey = value) },
    )
    fun setGeminiModel(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.geminiModel().set(value) },
        setOverride = { it.copy(geminiModel = value) },
    )
    fun setGeminiBatchSize(value: Int) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.geminiBatchSize().set(value) },
        setOverride = { it.copy(geminiBatchSize = value) },
    )
    fun setGeminiConcurrency(value: Int) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.geminiConcurrency().set(value) },
        setOverride = { it.copy(geminiConcurrency = value) },
    )
    fun setGeminiRelaxedMode(value: Boolean) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.geminiRelaxedMode().set(value) },
        setOverride = { it.copy(geminiRelaxedMode = value) },
    )
    fun setGeminiDisableCache(value: Boolean) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.geminiDisableCache().set(value) },
        setOverride = { it.copy(geminiDisableCache = value) },
    )
    fun setGeminiReasoningEffort(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.geminiReasoningEffort().set(value) },
        setOverride = { it.copy(geminiReasoningEffort = value) },
    )
    fun setGeminiBudgetTokens(value: Int) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.geminiBudgetTokens().set(value) },
        setOverride = { it.copy(geminiBudgetTokens = value) },
    )
    fun setGeminiTemperature(value: Float) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.geminiTemperature().set(value) },
        setOverride = { it.copy(geminiTemperature = value) },
    )
    fun setGeminiTopP(value: Float) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.geminiTopP().set(value) },
        setOverride = { it.copy(geminiTopP = value) },
    )
    fun setGeminiTopK(value: Int) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.geminiTopK().set(value) },
        setOverride = { it.copy(geminiTopK = value) },
    )
    fun setGeminiPromptMode(value: GeminiPromptMode) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.geminiPromptMode().set(value) },
        setOverride = { it.copy(geminiPromptMode = value) },
    )
    fun setGeminiSourceLang(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.geminiSourceLang().set(value) },
        setOverride = { it.copy(geminiSourceLang = value) },
    )
    fun setGeminiTargetLang(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.geminiTargetLang().set(value) },
        setOverride = { it.copy(geminiTargetLang = value) },
    )
    fun setGeminiStylePreset(value: NovelTranslationStylePreset) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.geminiStylePreset().set(value) },
        setOverride = { it.copy(geminiStylePreset = value) },
    )
    fun setGeminiEnabledPromptModifiers(value: List<String>) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.geminiEnabledPromptModifiers().set(value) },
        setOverride = { it.copy(geminiEnabledPromptModifiers = value) },
    )
    fun setGeminiCustomPromptModifier(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.geminiCustomPromptModifier().set(value) },
        setOverride = { it.copy(geminiCustomPromptModifier = value) },
    )
    fun setGeminiAutoTranslateEnglishSource(value: Boolean) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.geminiAutoTranslateEnglishSource().set(value) },
        setOverride = { it.copy(geminiAutoTranslateEnglishSource = value) },
    )
    fun setGeminiPrefetchNextChapterTranslation(value: Boolean) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.geminiPrefetchNextChapterTranslation().set(value) },
        setOverride = { it.copy(geminiPrefetchNextChapterTranslation = value) },
    )
    fun setGeminiPrivateUnlocked(value: Boolean) {
        novelReaderPreferences.geminiPrivateUnlocked().set(value)
    }
    fun setGeminiPrivatePythonLikeMode(value: Boolean) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.geminiPrivatePythonLikeMode().set(value) },
        setOverride = { it.copy(geminiPrivatePythonLikeMode = value) },
    )
    fun setTranslationProvider(value: NovelTranslationProvider) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.translationProvider().set(value) },
        setOverride = { it.copy(translationProvider = value) },
    ).also {
        when (value) {
            NovelTranslationProvider.GEMINI -> Unit
            NovelTranslationProvider.GEMINI_PRIVATE -> Unit
            NovelTranslationProvider.AIRFORCE -> refreshAirforceModels()
            NovelTranslationProvider.OPENROUTER -> refreshOpenRouterModels()
            NovelTranslationProvider.DEEPSEEK -> refreshDeepSeekModels()
        }
    }
    fun setGoogleTranslationEnabled(value: Boolean) {
        novelReaderPreferences.googleTranslationEnabled().set(value)
    }
    fun setGoogleTranslationAutoStart(value: Boolean) {
        novelReaderPreferences.googleTranslationAutoStart().set(value)
    }
    fun setGoogleTranslationSourceLang(value: String) {
        novelReaderPreferences.googleTranslationSourceLang().set(value)
    }
    fun setGoogleTranslationTargetLang(value: String) {
        novelReaderPreferences.googleTranslationTargetLang().set(value)
    }
    fun setAirforceBaseUrl(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.airforceBaseUrl().set(value) },
        setOverride = { it.copy(airforceBaseUrl = value) },
    )
    fun setAirforceApiKey(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.airforceApiKey().set(value) },
        setOverride = { it.copy(airforceApiKey = value) },
    )
    fun setAirforceModel(value: String) = updateGeminiSetting(
        setGlobal = { novelReaderPreferences.airforceModel().set(value) },
        setOverride = { it.copy(airforceModel = value) },
    )
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
    fun refreshAirforceModels() {
        val settings = (mutableState.value as? State.Success)?.readerSettings ?: return
        if (settings.translationProvider != NovelTranslationProvider.AIRFORCE) return
        if (settings.airforceApiKey.isBlank()) return
        if (settings.airforceBaseUrl.isBlank()) return
        isAirforceModelsLoading = true
        updateContent(settings)
        screenModelScope.launch(Dispatchers.IO) {
            val fetched = runCatching {
                airforceModelsService.fetchModels(
                    baseUrl = settings.airforceBaseUrl,
                    apiKey = settings.airforceApiKey,
                )
            }.getOrElse { error ->
                addGeminiLog("? Airforce models load failed: ${formatGeminiThrowableForLog(error)}")
                emptyList()
            }
            airforceModelIds = fetched
            isAirforceModelsLoading = false
            val currentSettings = (mutableState.value as? State.Success)?.readerSettings ?: settings
            updateContent(currentSettings)
        }
    }
    fun testAirforceConnection() {
        val settings = (mutableState.value as? State.Success)?.readerSettings ?: return
        if (isTestingAirforceConnection) return
        if (settings.translationProvider != NovelTranslationProvider.AIRFORCE) return
        if (!settings.hasConfiguredTranslationProvider()) {
            addGeminiLog("? Airforce config invalid: fill Base URL, API key and Model")
            return
        }
        isTestingAirforceConnection = true
        updateContent(settings)
        screenModelScope.launch {
            runCatching {
                val result = requestTranslationBatch(
                    segments = listOf("Connection test"),
                    settings = settings,
                ) { message ->
                    addGeminiLog("?? Test: $message")
                }
                if (result.isNullOrEmpty() || result.firstOrNull().isNullOrBlank()) {
                    error("Empty response")
                }
            }.onSuccess {
                addGeminiLog("? Airforce connection OK")
            }.onFailure { error ->
                addGeminiLog("? Airforce connection failed: ${formatGeminiThrowableForLog(error)}")
            }
            isTestingAirforceConnection = false
            val currentSettings = (mutableState.value as? State.Success)?.readerSettings ?: settings
            updateContent(currentSettings)
        }
    }
    fun refreshOpenRouterModels() {
        val settings = (mutableState.value as? State.Success)?.readerSettings ?: return
        if (settings.translationProvider != NovelTranslationProvider.OPENROUTER) return
        if (settings.openRouterApiKey.isBlank()) return
        if (settings.openRouterBaseUrl.isBlank()) return
        isOpenRouterModelsLoading = true
        updateContent(settings)
        screenModelScope.launch(Dispatchers.IO) {
            val fetched = runCatching {
                openRouterModelsService.fetchFreeModels(
                    baseUrl = settings.openRouterBaseUrl,
                    apiKey = settings.openRouterApiKey,
                )
            }.getOrElse { error ->
                addGeminiLog("? OpenRouter models load failed: ${formatGeminiThrowableForLog(error)}")
                emptyList()
            }
            openRouterModelIds = fetched
            isOpenRouterModelsLoading = false
            val currentSettings = (mutableState.value as? State.Success)?.readerSettings ?: settings
            updateContent(currentSettings)
        }
    }
    fun testOpenRouterConnection() {
        val settings = (mutableState.value as? State.Success)?.readerSettings ?: return
        if (isTestingOpenRouterConnection) return
        if (settings.translationProvider != NovelTranslationProvider.OPENROUTER) return
        if (!settings.hasConfiguredTranslationProvider()) {
            addGeminiLog("? OpenRouter config invalid: fill Base URL, API key and free Model (:free)")
            return
        }
        isTestingOpenRouterConnection = true
        updateContent(settings)
        screenModelScope.launch {
            runCatching {
                val result = requestTranslationBatch(
                    segments = listOf("Connection test"),
                    settings = settings,
                ) { message ->
                    addGeminiLog("?? Test: $message")
                }
                if (result.isNullOrEmpty() || result.firstOrNull().isNullOrBlank()) {
                    error("Empty response")
                }
            }.onSuccess {
                addGeminiLog("? OpenRouter connection OK")
            }.onFailure { error ->
                addGeminiLog("? OpenRouter connection failed: ${formatGeminiThrowableForLog(error)}")
            }
            isTestingOpenRouterConnection = false
            val currentSettings = (mutableState.value as? State.Success)?.readerSettings ?: settings
            updateContent(currentSettings)
        }
    }
    fun refreshDeepSeekModels() {
        val settings = (mutableState.value as? State.Success)?.readerSettings ?: return
        if (settings.translationProvider != NovelTranslationProvider.DEEPSEEK) return
        if (settings.deepSeekApiKey.isBlank()) return
        if (settings.deepSeekBaseUrl.isBlank()) return
        isDeepSeekModelsLoading = true
        updateContent(settings)
        screenModelScope.launch(Dispatchers.IO) {
            val fetched = runCatching {
                deepSeekModelsService.fetchModels(
                    baseUrl = settings.deepSeekBaseUrl,
                    apiKey = settings.deepSeekApiKey,
                )
            }.getOrElse { error ->
                addGeminiLog("? DeepSeek models load failed: ${formatGeminiThrowableForLog(error)}")
                emptyList()
            }
            deepSeekModelIds = fetched
            isDeepSeekModelsLoading = false
            val currentSettings = (mutableState.value as? State.Success)?.readerSettings ?: settings
            updateContent(currentSettings)
        }
    }
    fun testDeepSeekConnection() {
        val settings = (mutableState.value as? State.Success)?.readerSettings ?: return
        if (isTestingDeepSeekConnection) return
        if (settings.translationProvider != NovelTranslationProvider.DEEPSEEK) return
        if (!settings.hasConfiguredTranslationProvider()) {
            addGeminiLog("? DeepSeek config invalid: fill Base URL, API key and Model")
            return
        }
        isTestingDeepSeekConnection = true
        updateContent(settings)
        screenModelScope.launch {
            runCatching {
                val result = requestTranslationBatch(
                    segments = listOf("Connection test"),
                    settings = settings,
                ) { message ->
                    addGeminiLog("?? Test: $message")
                }
                if (result.isNullOrEmpty() || result.firstOrNull().isNullOrBlank()) {
                    error("Empty response")
                }
            }.onSuccess {
                addGeminiLog("? DeepSeek connection OK")
            }.onFailure { error ->
                addGeminiLog("? DeepSeek connection failed: ${formatGeminiThrowableForLog(error)}")
            }
            isTestingDeepSeekConnection = false
            val currentSettings = (mutableState.value as? State.Success)?.readerSettings ?: settings
            updateContent(currentSettings)
        }
    }
    private fun updateGeminiSetting(
        setGlobal: () -> Unit,
        setOverride: (NovelReaderOverride) -> NovelReaderOverride,
    ) {
        val sourceId = currentNovel?.source ?: return
        if (novelReaderPreferences.getSourceOverride(sourceId) != null) {
            novelReaderPreferences.updateSourceOverride(sourceId, setOverride)
        } else {
            setGlobal()
        }
    }
    private fun updateTtsSetting(
        setGlobal: () -> Unit,
        setOverride: (NovelReaderOverride) -> NovelReaderOverride,
    ) {
        updateGeminiSetting(setGlobal, setOverride)
        screenModelScope.launch {
            initializeTtsRuntime()
        }
    }
    fun updateSelectedTextSelection(selection: NovelSelectedTextSelection?) {
        val currentSettings = (mutableState.value as? State.Success)?.readerSettings
        if (currentSettings != null && !currentSettings.selectedTextTranslationEnabled) {
            clearSelectedTextTranslationSelection(refreshUi = false)
            return
        }
        selectedTextTranslationJob?.cancel()
        selectedTextTranslationJob = null
        selectedTextTranslationSelection = selection
        selectedTextTranslationUiState = if (selection == null) {
            NovelSelectedTextTranslationUiState.Idle
        } else {
            NovelSelectedTextTranslationUiState.SelectionAvailable(selection)
        }
        refreshSelectedTextTranslationUi()
    }

    fun translateSelectedText() {
        val selection = selectedTextTranslationSelection ?: return
        val currentState = mutableState.value as? State.Success ?: return
        val settings = currentState.readerSettings
        if (!settings.selectedTextTranslationEnabled) return
        if (selectedTextTranslationJob?.isActive == true) return

        val request = NovelSelectedTextTranslationRequest(
            selectedText = selection.text,
            targetLanguage = settings.selectedTextTranslationTargetLanguage,
        )
        val cacheKey = buildNovelSelectedTextTranslationRequestKey(
            providerFingerprint = selectedTextTranslationProvider.fingerprint,
            request = request,
        )
        selectedTextTranslationSessionCache.get(cacheKey)?.let { cached ->
            selectedTextTranslationUiState = NovelSelectedTextTranslationUiState.Result(
                selection = selection,
                translationResult = cached,
            )
            refreshSelectedTextTranslationUi()
            return
        }

        selectedTextTranslationUiState = NovelSelectedTextTranslationUiState.Translating(selection)
        refreshSelectedTextTranslationUi()
        selectedTextTranslationJob?.cancel()
        selectedTextTranslationJob = screenModelScope.launch {
            val outcome = selectedTextTranslationProvider.translate(request)
            if (isNovelSelectedTextTranslationResponseStale(selectedTextTranslationSelection, selection.sessionId)) {
                return@launch
            }
            when (outcome) {
                is NovelSelectedTextTranslationProviderOutcome.Success -> {
                    selectedTextTranslationSessionCache.put(cacheKey, outcome.result)
                    selectedTextTranslationUiState = NovelSelectedTextTranslationUiState.Result(
                        selection = selection,
                        translationResult = outcome.result,
                    )
                }
                is NovelSelectedTextTranslationProviderOutcome.Unavailable -> {
                    selectedTextTranslationUiState = when (outcome.reason) {
                        is NovelSelectedTextTranslationErrorReason.Cooldown,
                        NovelSelectedTextTranslationErrorReason.EmptySelection,
                        NovelSelectedTextTranslationErrorReason.TooLongSelection,
                        NovelSelectedTextTranslationErrorReason.WebViewUnavailable,
                        is NovelSelectedTextTranslationErrorReason.BackendUnavailable,
                        -> {
                            NovelSelectedTextTranslationUiState.Unavailable(outcome.reason)
                        }
                        is NovelSelectedTextTranslationErrorReason.NetworkFailure,
                        NovelSelectedTextTranslationErrorReason.ParserFailure,
                        -> {
                            NovelSelectedTextTranslationUiState.Error(
                                selection = selection,
                                reason = outcome.reason,
                            )
                        }
                    }
                }
            }
            refreshSelectedTextTranslationUi()
        }
    }

    fun retrySelectedTextTranslation() {
        translateSelectedText()
    }

    fun dismissSelectedTextTranslation() {
        clearSelectedTextTranslationSelection()
    }

    fun resetSelectedTextTranslationForChapter() {
        clearSelectedTextTranslationSelection()
        selectedTextTranslationSessionCache.clear()
    }

    private fun clearSelectedTextTranslationSelection(refreshUi: Boolean = true) {
        selectedTextTranslationJob?.cancel()
        selectedTextTranslationJob = null
        selectedTextTranslationSelection = null
        selectedTextTranslationUiState = NovelSelectedTextTranslationUiState.Idle
        if (refreshUi) {
            refreshSelectedTextTranslationUi()
        }
    }

    private fun refreshSelectedTextTranslationUi() {
        val settings = (mutableState.value as? State.Success)?.readerSettings ?: return
        updateContent(settings)
    }
    private fun refreshGeminiUiState() {
        val state = mutableState.value as? State.Success ?: return
        mutableState.value = state.copy(
            isGeminiTranslating = isGeminiTranslating,
            geminiTranslationProgress = geminiTranslationProgress,
            isGeminiTranslationVisible = isGeminiTranslationVisible,
            hasGeminiTranslationCache = hasGeminiTranslationCache,
            geminiLogs = geminiLogs,
        )
    }
    fun startGeminiTranslation() {
        if (isGeminiTranslating) return
        val currentState = mutableState.value as? State.Success ?: return
        val chapter = currentChapter ?: return
        val baseTextBlocks = currentParsedTextBlocks()
        if (baseTextBlocks.isEmpty()) return
        val settings = currentState.readerSettings
        if (!settings.geminiEnabled) {
            addGeminiLog("Gemini translation is disabled.")
            return
        }
        if (!settings.hasConfiguredTranslationProvider()) {
            addGeminiLog("? Translation provider is not configured")
            return
        }
        geminiTranslatedByIndex = emptyMap()
        isGeminiTranslationVisible = false
        hasGeminiTranslationCache = false
        isGeminiTranslating = true
        geminiTranslationProgress = 0
        geminiTranslationJob?.cancel()
        geminiTranslationJob = null
        addGeminiLog("Gemini translation queued in background.")
        refreshGeminiUiState()
        geminiTranslationJob = screenModelScope.launch(Dispatchers.IO) {
            try {
                val alreadyQueued = translationQueueManager.hasPendingOrActive(chapter.id)
                if (!alreadyQueued) {
                    translationQueueManager.addToQueue(listOf(chapter.id), currentState.novel.id)
                }
                if (!isActive) return@launch
                val appContext = Injekt.get<Application>()
                if (!TranslationJob.isRunning(appContext)) {
                    TranslationJob.runImmediately(appContext)
                }
                addGeminiLog(
                    if (alreadyQueued) {
                        "Gemini translation is already queued."
                    } else {
                        "Gemini translation started in background."
                    },
                )
            } catch (_: CancellationException) {
                // Job cancelled intentionally by the user or screen teardown.
            } catch (error: Exception) {
                logcat(LogPriority.WARN, error) { "Failed to queue Gemini translation for chapter=${chapter.id}" }
                addGeminiLog("Failed to start background translation: ${error.message ?: error::class.java.simpleName}")
                isGeminiTranslating = false
                geminiTranslationProgress = 0
                refreshGeminiUiState()
            }
        }
    }
    fun stopGeminiTranslation() {
        val chapter = currentChapter ?: return
        translationQueueManager.removeFromQueue(chapter.id)
        TranslationJob.stop(Injekt.get<Application>())
        geminiTranslationJob?.cancel()
        geminiTranslationJob = null
        isGeminiTranslating = false
        isGeminiTranslationVisible = false
        geminiTranslationProgress = 0
        addGeminiLog("?? Stop requested")
        val settings = (mutableState.value as? State.Success)?.readerSettings ?: return
        updateContent(settings)
    }
    fun toggleGeminiTranslationVisibility() {
        if (geminiTranslatedByIndex.isEmpty()) return
        isGeminiTranslationVisible = !isGeminiTranslationVisible
        addGeminiLog("??? Visibility: ${if (isGeminiTranslationVisible) "ON" else "OFF"}")
        val settings = (mutableState.value as? State.Success)?.readerSettings ?: return
        updateContent(settings)
    }
    fun clearGeminiTranslation() {
        val chapter = currentChapter ?: return
        if (isGeminiTranslating) {
            stopGeminiTranslation()
        }
        geminiTranslationJob?.cancel()
        geminiTranslationJob = null
        geminiTranslatedByIndex = emptyMap()
        isGeminiTranslating = false
        isGeminiTranslationVisible = false
        geminiTranslationProgress = 0
        hasGeminiTranslationCache = false
        NovelReaderTranslationDiskCacheStore.remove(chapter.id)
        addGeminiLog("??? Cleared chapter cache")
        val settings = (mutableState.value as? State.Success)?.readerSettings ?: return
        updateContent(settings)
    }

    // Google Translation
    fun startGoogleTranslation() {
        if (isGoogleTranslating) return
        val currentState = mutableState.value as? State.Success ?: return
        val settings = currentState.readerSettings
        if (!settings.googleTranslationEnabled) {
            addGoogleLog("Google Translate is disabled.")
            updateContent(settings)
            return
        }

        if (isGeminiTranslating) {
            addGoogleLog("Cannot start: Gemini translation is active.")
            updateContent(settings)
            return
        }

        val baseTextBlocks = currentParsedTextBlocks()
        if (baseTextBlocks.isEmpty()) return
        addGoogleLog(
            "Start: chapter=${currentChapter?.id ?: -1}, textBlocks=${baseTextBlocks.size}, source=${settings.googleTranslationSourceLang}, target=${settings.googleTranslationTargetLang}, backend=simple, autoStart=${settings.googleTranslationAutoStart}",
        )
        val firstText = baseTextBlocks.firstOrNull()
        val firstTextPreview = firstText?.take(80)?.replace('\n', ' ') ?: ""
        addGoogleLog(
            "Sample: firstTextLen=${firstText?.length ?: 0}, firstTextPreview=$firstTextPreview",
        )

        val params = GoogleTranslationParams(
            sourceLang = settings.googleTranslationSourceLang,
            targetLang = settings.googleTranslationTargetLang,
        )

        googleTranslatedByIndex = emptyMap()
        isGoogleTranslationVisible = false
        hasGoogleTranslationCache = false
        isGoogleTranslating = true
        googleTranslationProgress = 0
        googleLogs = emptyList()
        googleRateLimited = false
        translationPhase = TranslationPhase.IDLE
        updateContent(settings)

        googleTranslationJob = screenModelScope.launch {
            try {
                val response = googleTranslationService.translateBatch(
                    texts = baseTextBlocks,
                    params = params,
                    onLog = { log ->
                        addGoogleLog(log)
                        updateGoogleProgressFromLog(log)
                        updateContent(settings)
                    },
                    onProgress = onProgress@{ phase, percent ->
                        translationPhase = phase
                        googleTranslationProgress = percent
                        updateContent(settings)
                    },
                )
                val results = baseTextBlocks.mapIndexedNotNull { index, text ->
                    response.translatedByText[text]?.takeIf { it.isNotBlank() }?.let { translated ->
                        index to translated
                    }
                }.toMap()
                addGoogleLog(
                    "Finished: translatedSegments=${results.values.count {
                        it.isNotBlank()
                    }}/$baseTextBlocks.size, rateLimited=false",
                )
                googleTranslatedByIndex = results
                val chapter = currentChapter
                if (chapter != null) {
                    googleSessionCache.put(
                        chapterId = chapter.id,
                        sourceLang = params.sourceLang,
                        targetLang = params.targetLang,
                        translatedByIndex = results,
                    )
                }
                hasGoogleTranslationCache = results.isNotEmpty()
                isGoogleTranslating = false
                googleTranslationProgress = 100
                translationPhase = TranslationPhase.IDLE
                if (results.isNotEmpty()) {
                    isGoogleTranslationVisible = true
                }
                updateContent(settings)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                addGoogleLog("Google translation failed: ${error.message ?: error::class.java.simpleName}")
                googleRateLimited = false
                isGoogleTranslating = false
                googleTranslationProgress = 0
                translationPhase = TranslationPhase.IDLE
                updateContent(settings)
            }
        }
    }

    fun stopGoogleTranslation() {
        googleTranslationJob?.cancel()
        googleTranslationJob = null
        isGoogleTranslating = false
        val settings = (mutableState.value as? State.Success)?.readerSettings ?: return
        updateContent(settings)
    }

    fun resumeGoogleTranslation() {
        if (!googleRateLimited) return
        googleRateLimited = false
        addGoogleLog("Resume requested: restarting Google translation")
        startGoogleTranslation()
    }

    fun toggleGoogleTranslationVisibility() {
        if (googleTranslatedByIndex.isEmpty()) return
        isGoogleTranslationVisible = !isGoogleTranslationVisible
        val settings = (mutableState.value as? State.Success)?.readerSettings ?: return
        updateContent(settings)
    }

    fun clearGoogleTranslation() {
        val chapter = currentChapter ?: return
        googleTranslationJob?.cancel()
        googleTranslationJob = null
        googleTranslatedByIndex = emptyMap()
        isGoogleTranslating = false
        isGoogleTranslationVisible = false
        googleTranslationProgress = 0
        hasGoogleTranslationCache = false
        googleLogs = emptyList()
        googleRateLimited = false
        translationPhase = TranslationPhase.IDLE
        googleSessionCache.remove(
            chapterId = chapter.id,
            sourceLang =
            (mutableState.value as? State.Success)?.readerSettings?.googleTranslationSourceLang ?: "auto",
            targetLang =
            (mutableState.value as? State.Success)?.readerSettings?.googleTranslationTargetLang ?: "Russian",
        )
        val settings = (mutableState.value as? State.Success)?.readerSettings ?: return
        updateContent(settings)
    }

    private fun restoreGoogleTranslationFromSessionCache(settings: NovelReaderSettings) {
        val chapter = currentChapter ?: return
        val cached = googleSessionCache.get(
            chapterId = chapter.id,
            sourceLang = settings.googleTranslationSourceLang,
            targetLang = settings.googleTranslationTargetLang,
        )
        if (cached != null && cached.isNotEmpty()) {
            googleTranslatedByIndex = cached
            hasGoogleTranslationCache = true
            isGoogleTranslationVisible = true
            addGoogleLog(
                "Restored session cache: segments=${cached.size}, source=${settings.googleTranslationSourceLang}, target=${settings.googleTranslationTargetLang}",
            )
            updateContent(settings)
        }
    }

    fun maybeAutoStartGoogleTranslation() {
        val settings = (mutableState.value as? State.Success)?.readerSettings ?: return
        if (!settings.googleTranslationEnabled || !settings.googleTranslationAutoStart) return
        if (isGeminiTranslating || isGeminiTranslationVisible) return
        restoreGoogleTranslationFromSessionCache(settings)
        if (!hasGoogleTranslationCache) {
            startGoogleTranslation()
        }
    }

    private fun applyGoogleTranslationToContentBlocks(blocks: List<ContentBlock>): List<ContentBlock> {
        if (googleTranslatedByIndex.isEmpty()) return blocks
        var textIndex = 0
        var replacedCount = 0
        val updated = blocks.map { block ->
            when (block) {
                is ContentBlock.Image -> block
                is ContentBlock.Text -> {
                    val translated = googleTranslatedByIndex[textIndex]
                    textIndex += 1
                    if (translated.isNullOrBlank()) {
                        block
                    } else {
                        replacedCount += 1
                        ContentBlock.Text(translated)
                    }
                }
            }
        }
        addGoogleLog(
            "Applied to content blocks: replaced=$replacedCount/${blocks.count { it is ContentBlock.Text }}",
        )
        return updated
    }

    private fun addGoogleLog(message: String) {
        val text = message.trim()
        if (text.isBlank()) return
        googleLogs = (listOf(text) + googleLogs).take(100)
        logcat(LogPriority.DEBUG) { "[GoogleTranslate] $text" }
    }

    private fun updateGoogleProgressFromLog(message: String) {
        val match = Regex("""Simple chunk (\d+)/(\d+)""").find(message) ?: return
        val current = match.groupValues[1].toIntOrNull() ?: return
        val total = match.groupValues[2].toIntOrNull()?.takeIf { it > 0 } ?: return
        val progress = (current * 100) / total
        googleTranslationProgress = maxOf(googleTranslationProgress, progress.coerceIn(0, 99))
    }

    private fun restoreGeminiTranslationFromCache(
        chapterId: Long,
        settings: NovelReaderSettings,
    ) {
        val cached = NovelReaderTranslationDiskCacheStore.get(chapterId)
        if (cached == null) {
            hasGeminiTranslationCache = false
            return
        }
        val settingsMatch = NovelReaderTranslationCacheResolver.matches(
            cached = cached,
            requirements = settings.toTranslationCacheRequirements(),
        )
        if (!settingsMatch) {
            hasGeminiTranslationCache = false
            return
        }
        geminiTranslatedByIndex = cached.translatedByIndex
        hasGeminiTranslationCache = true
        geminiTranslationProgress = 100
        isGeminiTranslationVisible = true
        addGeminiLog("?? Restored cached translation")
    }
    private fun applyGeminiTranslationToContentBlocks(
        blocks: List<ContentBlock>,
        forceTranslation: Boolean = false,
    ): List<ContentBlock> {
        if ((!forceTranslation && !isGeminiTranslationVisible) || geminiTranslatedByIndex.isEmpty()) return blocks
        var textIndex = 0
        return blocks.map { block ->
            when (block) {
                is ContentBlock.Image -> block
                is ContentBlock.Text -> {
                    val translated = geminiTranslatedByIndex[textIndex]
                    textIndex += 1
                    if (translated.isNullOrBlank()) {
                        block
                    } else {
                        ContentBlock.Text(translated)
                    }
                }
            }
        }
    }
    private fun applyGeminiTranslationToRichContentBlocks(
        blocks: List<NovelRichContentBlock>,
        forceTranslation: Boolean = false,
    ): List<NovelRichContentBlock> {
        if ((!forceTranslation && !isGeminiTranslationVisible) || geminiTranslatedByIndex.isEmpty()) return blocks
        var textIndex = 0
        return blocks.map { block ->
            when (block) {
                is NovelRichContentBlock.BlockQuote -> {
                    val replacement = geminiTranslatedByIndex[textIndex] ?: block.segments.joinToString("") { it.text }
                    textIndex += 1
                    block.copy(segments = listOf(NovelRichTextSegment(replacement)))
                }
                is NovelRichContentBlock.Heading -> {
                    val replacement = geminiTranslatedByIndex[textIndex] ?: block.segments.joinToString("") { it.text }
                    textIndex += 1
                    block.copy(segments = listOf(NovelRichTextSegment(replacement)))
                }
                is NovelRichContentBlock.Image -> block
                is NovelRichContentBlock.HorizontalRule -> block
                is NovelRichContentBlock.Paragraph -> {
                    val replacement = geminiTranslatedByIndex[textIndex] ?: block.segments.joinToString("") { it.text }
                    textIndex += 1
                    block.copy(segments = listOf(NovelRichTextSegment(replacement)))
                }
            }
        }
    }

    private fun applyGoogleTranslationToRichContentBlocks(
        blocks: List<NovelRichContentBlock>,
        forceTranslation: Boolean = false,
    ): List<NovelRichContentBlock> {
        if ((!forceTranslation && !isGoogleTranslationVisible) || googleTranslatedByIndex.isEmpty()) return blocks
        var textIndex = 0
        var replacedCount = 0
        val updated = blocks.map { block ->
            when (block) {
                is NovelRichContentBlock.BlockQuote -> {
                    val replacement = googleTranslatedByIndex[textIndex]
                    textIndex += 1
                    if (replacement.isNullOrBlank()) {
                        block
                    } else {
                        replacedCount += 1
                        block.copy(segments = listOf(NovelRichTextSegment(replacement)))
                    }
                }
                is NovelRichContentBlock.Heading -> {
                    val replacement = googleTranslatedByIndex[textIndex]
                    textIndex += 1
                    if (replacement.isNullOrBlank()) {
                        block
                    } else {
                        replacedCount += 1
                        block.copy(segments = listOf(NovelRichTextSegment(replacement)))
                    }
                }
                is NovelRichContentBlock.Image -> block
                is NovelRichContentBlock.HorizontalRule -> block
                is NovelRichContentBlock.Paragraph -> {
                    val replacement = googleTranslatedByIndex[textIndex]
                    textIndex += 1
                    if (replacement.isNullOrBlank()) {
                        block
                    } else {
                        replacedCount += 1
                        block.copy(segments = listOf(NovelRichTextSegment(replacement)))
                    }
                }
            }
        }
        val richContentBlockCount = blocks.count {
            it is NovelRichContentBlock.BlockQuote ||
                it is NovelRichContentBlock.Heading ||
                it is NovelRichContentBlock.Paragraph
        }
        addGoogleLog("Applied to rich content blocks: replaced=$replacedCount/$richContentBlockCount")
        return updated
    }
    private fun buildRawHtmlFromContentBlocks(blocks: List<ContentBlock>): String {
        return buildString {
            blocks.forEach { block ->
                when (block) {
                    is ContentBlock.Image -> {
                        append("<img src=\"")
                        append(block.url.escapeHtmlAttribute())
                        append("\" alt=\"")
                        append((block.alt ?: "").escapeHtmlAttribute())
                        append("\" />")
                    }
                    is ContentBlock.Text -> {
                        append("<p>")
                        append(block.text.escapeHtml())
                        append("</p>")
                    }
                }
            }
        }
    }
    private fun currentParsedTextBlocks(): List<String> {
        parsedContentBlocks?.let { blocks ->
            return blocks
                .asSequence()
                .filterIsInstance<ContentBlock.Text>()
                .map { it.text }
                .toList()
        }
        val html = rawHtml ?: return emptyList()
        return extractTextBlocks(html)
    }

    private fun currentParsedContentBlocks(): List<ContentBlock> {
        parsedContentBlocks?.let { return it }
        val html = rawHtml ?: return emptyList()
        val novel = currentNovel ?: return emptyList()
        return extractContentBlocks(
            rawHtml = html,
            chapterWebUrl = chapterWebUrl,
            novelUrl = novel.url,
            pluginSite = pluginSite,
        ).ifEmpty {
            extractTextBlocks(html).map(ContentBlock::Text)
        }
    }
    private fun NovelReaderSettings.translationPromptFamily(): NovelTranslationPromptFamily {
        return when (translationProvider) {
            NovelTranslationProvider.GEMINI_PRIVATE,
            NovelTranslationProvider.AIRFORCE,
            -> NovelTranslationPromptFamily.RUSSIAN
            NovelTranslationProvider.GEMINI,
            NovelTranslationProvider.OPENROUTER,
            NovelTranslationProvider.DEEPSEEK,
            -> resolveNovelTranslationPromptFamily(geminiTargetLang)
        }
    }

    private fun NovelReaderSettings.resolveTranslationPromptModifiers(
        family: NovelTranslationPromptFamily = NovelTranslationPromptFamily.RUSSIAN,
    ): String {
        val modifierText = GeminiPromptModifiers.buildPromptText(
            enabledIds = geminiEnabledPromptModifiers,
            customModifier = geminiCustomPromptModifier,
            family = family,
        )
        val styleDirective = NovelTranslationStylePresets.promptDirective(
            geminiStylePreset,
            family = family,
        ).trim()
        return listOf(
            styleDirective,
            modifierText,
            geminiPromptModifiers.trim(),
        ).filter { it.isNotBlank() }
            .joinToString("\n\n")
    }
    private fun NovelReaderSettings.toGeminiTranslationParams(): GeminiTranslationParams {
        return GeminiTranslationParams(
            apiKey = geminiApiKey,
            model = geminiModel.normalizeGeminiModelId(),
            sourceLang = geminiSourceLang,
            targetLang = geminiTargetLang,
            reasoningEffort = geminiReasoningEffort,
            budgetTokens = geminiBudgetTokens,
            temperature = geminiTemperature,
            topP = geminiTopP,
            topK = geminiTopK,
            promptMode = geminiPromptMode,
            promptModifiers = resolveTranslationPromptModifiers(family = translationPromptFamily()),
            provider = translationProvider,
            privateUnlocked = geminiPrivateUnlocked,
            privatePythonLikeMode = geminiPrivatePythonLikeMode,
        )
    }
    private fun NovelReaderSettings.toAirforceTranslationParams(): AirforceTranslationParams {
        return AirforceTranslationParams(
            baseUrl = airforceBaseUrl,
            apiKey = airforceApiKey,
            model = airforceModel,
            sourceLang = geminiSourceLang,
            targetLang = geminiTargetLang,
            promptMode = geminiPromptMode,
            promptModifiers = resolveTranslationPromptModifiers(),
            temperature = geminiTemperature,
            topP = geminiTopP,
        )
    }
    private fun NovelReaderSettings.toOpenRouterTranslationParams(): OpenRouterTranslationParams {
        return OpenRouterTranslationParams(
            baseUrl = openRouterBaseUrl,
            apiKey = openRouterApiKey,
            model = openRouterModel,
            sourceLang = geminiSourceLang,
            targetLang = geminiTargetLang,
            promptMode = geminiPromptMode,
            promptModifiers = resolveTranslationPromptModifiers(family = translationPromptFamily()),
            temperature = geminiTemperature,
            topP = geminiTopP,
        )
    }
    private fun NovelReaderSettings.toDeepSeekTranslationParams(): DeepSeekTranslationParams {
        return DeepSeekTranslationParams(
            baseUrl = deepSeekBaseUrl,
            apiKey = deepSeekApiKey,
            model = deepSeekModel,
            sourceLang = geminiSourceLang,
            targetLang = geminiTargetLang,
            promptMode = geminiPromptMode,
            promptModifiers = resolveTranslationPromptModifiers(family = translationPromptFamily()),
            temperature = geminiTemperature.coerceIn(DEEPSEEK_TEMPERATURE_MIN, DEEPSEEK_TEMPERATURE_MAX),
            topP = geminiTopP.coerceIn(DEEPSEEK_TOP_P_MIN, DEEPSEEK_TOP_P_MAX),
            presencePenalty = DEEPSEEK_DEFAULT_PRESENCE_PENALTY,
            frequencyPenalty = DEEPSEEK_DEFAULT_FREQUENCY_PENALTY,
        )
    }
    private fun NovelReaderSettings.translationRequestConfigLog(): String {
        val common = buildString {
            append("provider=").append(translationProvider.name)
            append(", model=").append(translationCacheModelId())
            append(", lang=").append(geminiSourceLang).append("->").append(geminiTargetLang)
            append(", prompt=").append(geminiPromptMode.name)
            append(", style=").append(geminiStylePreset.name)
            if (shouldUseSinglePrivateChapterRequestMode()) {
                append(", batch=chapter")
                append(", concurrency=1")
            } else {
                append(", batch=").append(geminiBatchSize.coerceIn(1, 80))
                append(", concurrency=").append(translationConcurrencyLimit())
            }
            append(", relaxed=").append(geminiRelaxedMode)
            append(", cache=").append(!geminiDisableCache)
        }
        val sampling = when (translationProvider) {
            NovelTranslationProvider.GEMINI -> {
                "temp=${geminiTemperature.toLogFloat()}, topP=${geminiTopP.toLogFloat()}, topK=$geminiTopK, " +
                    "reasoning=$geminiReasoningEffort, budgetTokens=$geminiBudgetTokens"
            }
            NovelTranslationProvider.GEMINI_PRIVATE -> {
                "temp=${geminiTemperature.toLogFloat()}, topP=${geminiTopP.toLogFloat()}, topK=$geminiTopK, " +
                    "reasoning=$geminiReasoningEffort, budgetTokens=$geminiBudgetTokens, " +
                    "singleRequest=${shouldUseSinglePrivateChapterRequestMode()}, " +
                    "pythonLike=$geminiPrivatePythonLikeMode, " +
                    "bridgeInstalled=${GeminiPrivateBridge.isInstalled()}, bridgeUnlocked=${isPrivateBridgeUnlocked()}"
            }
            NovelTranslationProvider.AIRFORCE -> {
                "baseUrl=${airforceBaseUrl.trim()}, temp=${geminiTemperature.toLogFloat()}, topP=${geminiTopP.toLogFloat()}"
            }
            NovelTranslationProvider.OPENROUTER -> {
                val isFreeModel = openRouterModel.trim().endsWith(":free", ignoreCase = true)
                "baseUrl=${openRouterBaseUrl.trim()}, temp=${geminiTemperature.toLogFloat()}, " +
                    "topP=${geminiTopP.toLogFloat()}, freeModel=$isFreeModel"
            }
            NovelTranslationProvider.DEEPSEEK -> {
                val params = toDeepSeekTranslationParams()
                val presencePenalty = params.presencePenalty.toLogFloat()
                val frequencyPenalty = params.frequencyPenalty.toLogFloat()
                "baseUrl=${params.baseUrl.trim()}, temp=${params.temperature.toLogFloat()}, " +
                    "topP=${params.topP.toLogFloat()}, " +
                    "presencePenalty=$presencePenalty, frequencyPenalty=$frequencyPenalty, " +
                    "stream=false"
            }
        }
        return "$common, $sampling"
    }
    private fun Float.toLogFloat(): String = String.format(Locale.US, "%.3f", this)
    private suspend fun requestTranslationBatch(
        segments: List<String>,
        settings: NovelReaderSettings,
        onLog: ((String) -> Unit)? = null,
    ): List<String?>? {
        return when (settings.translationProvider) {
            NovelTranslationProvider.GEMINI -> {
                geminiTranslationService.translateBatch(
                    segments = segments,
                    params = settings.toGeminiTranslationParams(),
                    onLog = onLog,
                )
            }
            NovelTranslationProvider.GEMINI_PRIVATE -> {
                geminiTranslationService.translateBatch(
                    segments = segments,
                    params = settings.toGeminiTranslationParams(),
                    onLog = onLog,
                )
            }
            NovelTranslationProvider.AIRFORCE -> {
                airforceTranslationService.translateBatch(
                    segments = segments,
                    params = settings.toAirforceTranslationParams(),
                    onLog = onLog,
                )
            }
            NovelTranslationProvider.OPENROUTER -> {
                openRouterTranslationService.translateBatch(
                    segments = segments,
                    params = settings.toOpenRouterTranslationParams(),
                    onLog = onLog,
                )
            }
            NovelTranslationProvider.DEEPSEEK -> {
                deepSeekTranslationService.translateBatch(
                    segments = segments,
                    params = settings.toDeepSeekTranslationParams(),
                    onLog = onLog,
                )
            }
        }
    }
    private fun NovelReaderSettings.hasConfiguredTranslationProvider(): Boolean {
        if (!geminiEnabled) return false
        return when (translationProvider) {
            NovelTranslationProvider.GEMINI -> geminiApiKey.isNotBlank()
            NovelTranslationProvider.GEMINI_PRIVATE -> {
                geminiApiKey.isNotBlank() && isPrivateBridgeUnlocked()
            }
            NovelTranslationProvider.AIRFORCE -> {
                airforceBaseUrl.isNotBlank() && airforceApiKey.isNotBlank() && airforceModel.isNotBlank()
            }
            NovelTranslationProvider.OPENROUTER -> {
                openRouterBaseUrl.isNotBlank() &&
                    openRouterApiKey.isNotBlank() &&
                    openRouterModel.trim().endsWith(":free", ignoreCase = true)
            }
            NovelTranslationProvider.DEEPSEEK -> {
                deepSeekBaseUrl.isNotBlank() &&
                    deepSeekApiKey.isNotBlank() &&
                    deepSeekModel.isNotBlank()
            }
        }
    }
    private fun NovelReaderSettings.translationConcurrencyLimit(): Int {
        return when (translationProvider) {
            NovelTranslationProvider.GEMINI -> geminiConcurrency.coerceIn(1, 8)
            NovelTranslationProvider.GEMINI_PRIVATE -> {
                if (shouldUseSinglePrivateChapterRequestMode()) 1 else geminiConcurrency.coerceIn(1, 8)
            }
            NovelTranslationProvider.AIRFORCE -> 1
            NovelTranslationProvider.OPENROUTER -> 1
            NovelTranslationProvider.DEEPSEEK -> geminiConcurrency.coerceIn(1, MAX_DEEPSEEK_CONCURRENCY)
        }
    }
    private fun NovelReaderSettings.shouldUseSinglePrivateChapterRequestMode(): Boolean {
        return translationProvider == NovelTranslationProvider.GEMINI_PRIVATE &&
            GeminiPrivateBridge.isInstalled() &&
            (GeminiPrivateBridge.forceSingleChapterRequest() || geminiPrivatePythonLikeMode)
    }
    private fun NovelReaderSettings.requiresPrivateBridgeUnlock(): Boolean {
        return translationProvider == NovelTranslationProvider.GEMINI_PRIVATE &&
            GeminiPrivateBridge.isInstalled()
    }
    private fun NovelReaderSettings.isPrivateBridgeUnlocked(): Boolean {
        if (!requiresPrivateBridgeUnlock()) return true
        return geminiPrivateUnlocked || GeminiPrivateBridge.isUnlocked()
    }
    private fun extractTextBlocks(rawHtml: String): List<String> {
        val document = Jsoup.parse(rawHtml)
        val paragraphLikeNodes = document.select("p, li, blockquote, h1, h2, h3, h4, h5, h6, pre")
            .filterNot { node ->
                node.tagName().equals("p", ignoreCase = true) &&
                    node.parent()?.tagName()?.equals("li", ignoreCase = true) == true
            }
            .map { element -> element.text().sanitizeTextBlock() }
            .filter { it.isNotBlank() }
        if (paragraphLikeNodes.isNotEmpty()) {
            return paragraphLikeNodes
        }
        val text = document.body().wholeText()
            .sanitizeTextBlock()
        if (text.isBlank()) return emptyList()
        return text.split(Regex("\n{2,}"))
            .flatMap { block -> block.split('\n') }
            .map { it.sanitizeTextBlock() }
            .filter { it.isNotBlank() }
    }
    private fun extractContentBlocks(
        rawHtml: String,
        chapterWebUrl: String?,
        novelUrl: String,
        pluginSite: String?,
    ): List<ContentBlock> {
        val document = Jsoup.parse(rawHtml)
        val blocks = mutableListOf<ContentBlock>()
        collectContentBlocks(
            node = document.body(),
            blocks = blocks,
            chapterWebUrl = chapterWebUrl,
            novelUrl = novelUrl,
            pluginSite = pluginSite,
        )
        return blocks
    }
    private fun collectContentBlocks(
        node: Node,
        blocks: MutableList<ContentBlock>,
        chapterWebUrl: String?,
        novelUrl: String,
        pluginSite: String?,
    ) {
        when (node) {
            is TextNode -> {
                val text = node.text().sanitizeTextBlock()
                if (text.isNotBlank()) {
                    blocks += ContentBlock.Text(text)
                }
            }
            is Element -> {
                val tag = node.tagName().lowercase()
                when {
                    tag == "script" ||
                        tag == "style" ||
                        tag == "head" ||
                        tag == "meta" ||
                        tag == "link" ||
                        tag == "noscript" -> Unit
                    tag == "img" -> {
                        val rawUrl = node.attr("src")
                            .ifBlank { node.attr("data-src") }
                            .ifBlank { node.attr("data-original") }
                            .trim()
                        if (rawUrl.isBlank()) return
                        val resolvedUrl = resolveContentResourceUrl(
                            rawUrl = rawUrl,
                            chapterWebUrl = chapterWebUrl,
                            novelUrl = novelUrl,
                            pluginSite = pluginSite,
                        ) ?: return
                        blocks += ContentBlock.Image(
                            url = resolvedUrl,
                            alt = node.attr("alt").sanitizeTextBlock().ifBlank { null },
                        )
                    }
                    tag == "p" ||
                        tag == "li" ||
                        tag == "blockquote" ||
                        tag == "h1" ||
                        tag == "h2" ||
                        tag == "h3" ||
                        tag == "h4" ||
                        tag == "h5" ||
                        tag == "h6" ||
                        tag == "pre" -> {
                        val text = node.text().sanitizeTextBlock()
                        if (text.isBlank()) return
                        val structuredBlocks = parseStructuredFragmentToBlocks(
                            rawPayload = text,
                            chapterWebUrl = chapterWebUrl,
                            novelUrl = novelUrl,
                            pluginSite = pluginSite,
                        )
                        if (structuredBlocks.isNotEmpty()) {
                            blocks += structuredBlocks
                            return
                        }
                        val normalizedText = if (tag == "li") {
                            "• $text"
                        } else {
                            text
                        }
                        blocks += ContentBlock.Text(normalizedText)
                    }
                    node.selectFirst("p, li, blockquote, h1, h2, h3, h4, h5, h6, pre, img") == null -> {
                        val text = node.wholeText().sanitizeTextBlock()
                        if (text.isNotBlank()) {
                            blocks += ContentBlock.Text(text)
                        }
                    }
                    else -> {
                        node.childNodes().forEach { child ->
                            collectContentBlocks(
                                node = child,
                                blocks = blocks,
                                chapterWebUrl = chapterWebUrl,
                                novelUrl = novelUrl,
                                pluginSite = pluginSite,
                            )
                        }
                    }
                }
            }
        }
    }
    private fun parseStructuredFragmentToBlocks(
        rawPayload: String,
        chapterWebUrl: String?,
        novelUrl: String,
        pluginSite: String?,
    ): List<ContentBlock> {
        if (!looksLikeStructuredPayload(rawPayload)) return emptyList()
        val parsedRoot = parseStructuredRoot(rawPayload)
        val renderedHtml = if (parsedRoot != null) {
            val attachmentUrls = extractStructuredAttachmentUrls(parsedRoot)
            val structuredNode = findStructuredNode(parsedRoot) ?: return emptyList()
            renderStructuredElementAsHtml(structuredNode, attachmentUrls)
        } else {
            renderStructuredPayloadFallback(rawPayload).orEmpty()
        }.trim()
        if (renderedHtml.isBlank()) return emptyList()
        val renderedDoc = Jsoup.parse("<div>$renderedHtml</div>")
        val renderedCandidates = renderedDoc.select("p, li, blockquote, h1, h2, h3, h4, h5, h6, pre, img")
            .filterNot { node ->
                node.tagName().equals("p", ignoreCase = true) &&
                    node.parent()?.tagName()?.equals("li", ignoreCase = true) == true
            }
        return renderedCandidates.mapNotNull { candidate ->
            if (candidate.tagName().equals("img", ignoreCase = true)) {
                val rawUrl = candidate.attr("src")
                    .ifBlank { candidate.attr("data-src") }
                    .ifBlank { candidate.attr("data-original") }
                    .trim()
                val resolvedUrl = resolveContentResourceUrl(
                    rawUrl = rawUrl,
                    chapterWebUrl = chapterWebUrl,
                    novelUrl = novelUrl,
                    pluginSite = pluginSite,
                ) ?: return@mapNotNull null
                ContentBlock.Image(
                    url = resolvedUrl,
                    alt = candidate.attr("alt").sanitizeTextBlock().ifBlank { null },
                )
            } else {
                val candidateText = candidate.text().sanitizeTextBlock()
                if (candidateText.isBlank()) {
                    null
                } else {
                    val normalized = if (candidate.tagName().equals("li", ignoreCase = true)) {
                        "• $candidateText"
                    } else {
                        candidateText
                    }
                    ContentBlock.Text(normalized)
                }
            }
        }
    }
    private fun resolveContentResourceUrl(
        rawUrl: String,
        chapterWebUrl: String?,
        novelUrl: String,
        pluginSite: String?,
    ): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.startsWith("data:image/", ignoreCase = true)) {
            return trimmed
        }
        if (NovelPluginImage.isSupported(trimmed)) {
            return trimmed
        }
        if (trimmed.startsWith("blob:", ignoreCase = true)) {
            return null
        }
        trimmed.toHttpUrlOrNull()?.let { return it.toString() }
        chapterWebUrl
            ?.let { resolveUrl(trimmed, it).trim().toHttpUrlOrNull() }
            ?.let { return it.toString() }
        return resolveNovelChapterWebUrl(
            chapterUrl = trimmed,
            pluginSite = pluginSite,
            novelUrl = novelUrl,
        )
    }
    private fun resolveRichContentBlocks(
        blocks: List<NovelRichContentBlock>,
        chapterWebUrl: String?,
        novelUrl: String,
        pluginSite: String?,
    ): List<NovelRichContentBlock> {
        return blocks.map { block ->
            when (block) {
                is NovelRichContentBlock.Image -> {
                    val resolvedUrl = resolveContentResourceUrl(
                        rawUrl = block.url,
                        chapterWebUrl = chapterWebUrl,
                        novelUrl = novelUrl,
                        pluginSite = pluginSite,
                    ) ?: block.url
                    block.copy(url = resolvedUrl)
                }
                else -> block
            }
        }
    }
    private fun normalizeHtml(
        rawHtml: String,
        settings: NovelReaderSettings,
        customCss: String?,
        customJs: String?,
    ): String {
        val css = customCss?.takeIf { it.isNotBlank() }
        val js = customJs?.takeIf { it.isNotBlank() }
        val isDarkTheme = when (settings.theme) {
            NovelReaderTheme.SYSTEM -> isSystemDark()
            NovelReaderTheme.DARK -> true
            NovelReaderTheme.LIGHT -> false
        }
        val background = if (isDarkTheme) "#121212" else "#FFFFFF"
        val textColor = if (isDarkTheme) "#EDEDED" else "#1A1A1A"
        val linkColor = if (isDarkTheme) "#80B4FF" else "#1E3A8A"
        val baseStyle = """
            body {
              padding: ${settings.margin}px;
              line-height: ${settings.lineHeight};
              font-size: ${settings.fontSize}px;
              background: $background;
              color: $textColor;
              word-break: break-word;
            }
            img { max-width: 100%; height: auto; }
            a { color: $linkColor; }
        """.trimIndent()
        val injection = buildString {
            append("<style>")
            append('\n')
            append(baseStyle)
            if (css != null) {
                append('\n')
                append(css)
            }
            append('\n')
            append("</style>")
            if (js != null) {
                append('\n')
                append("<script>")
                append('\n')
                append(js)
                append('\n')
                append("</script>")
            }
        }
        if (rawHtml.contains("<html", ignoreCase = true)) {
            return if (injection.isNotBlank()) injectIntoHtml(rawHtml, injection) else rawHtml
        }
        val style = buildString {
            append(baseStyle)
            if (css != null) {
                append('\n')
                append(css)
            }
        }
        return """
            <!doctype html>
            <html>
              <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <style>$style</style>
                ${js?.let { "<script>\n$it\n</script>" } ?: ""}
              </head>
              <body>
                $rawHtml
              </body>
            </html>
        """.trimIndent()
    }
    private fun injectIntoHtml(rawHtml: String, injection: String): String {
        val headClose = Regex("</head>", RegexOption.IGNORE_CASE)
        if (headClose.containsMatchIn(rawHtml)) {
            return rawHtml.replaceFirst(headClose, "$injection</head>")
        }
        val headOpen = Regex("<head[^>]*>", RegexOption.IGNORE_CASE)
        val headMatch = headOpen.find(rawHtml)
        if (headMatch != null) {
            return rawHtml.replaceRange(headMatch.range, headMatch.value + injection)
        }
        val bodyClose = Regex("</body>", RegexOption.IGNORE_CASE)
        if (bodyClose.containsMatchIn(rawHtml)) {
            return rawHtml.replaceFirst(bodyClose, "$injection</body>")
        }
        return injection + rawHtml
    }
    private fun String.sanitizeTextBlock(): String {
        return this
            .replace('\u00A0', ' ')
            .replace("\r", "")
            .trim()
    }
    private fun String.normalizeStructuredChapterPayload(): String {
        val trimmedPayload = trim()
        if (looksLikeHtmlPayload(trimmedPayload)) {
            return this
        }
        val parsedRoot = parseStructuredRoot(this)
        if (parsedRoot != null) {
            val attachmentUrls = extractStructuredAttachmentUrls(parsedRoot)
            val structuredNode = findStructuredNode(parsedRoot) ?: return this
            val rendered = renderStructuredElementAsHtml(
                element = structuredNode,
                attachmentUrls = attachmentUrls,
            ).trim()
            if (rendered.isNotBlank()) {
                return "<div>$rendered</div>"
            }
        }
        val fallbackRendered = renderStructuredPayloadFallback(this).orEmpty().trim()
        return if (fallbackRendered.isBlank()) this else "<div>$fallbackRendered</div>"
    }
    private fun parseStructuredRoot(rawPayload: String): JsonElement? {
        val trimmed = rawPayload
            .trim()
            .removePrefix("\uFEFF")
            .trim()
        if (!looksLikeStructuredPayload(trimmed)) return null
        val parseCandidates = linkedSetOf(trimmed)
        extractJsonCandidate(trimmed)?.let { parseCandidates += it }
        normalizeJsonLikePayload(trimmed)?.let { parseCandidates += it }
        parseCandidates.forEach { candidate ->
            val parsed = parseStructuredCandidate(candidate, decodeDepth = 0) ?: return@forEach
            if (parsed is JsonObject || parsed is JsonArray) {
                return parsed
            }
        }
        return null
    }
    private fun parseStructuredCandidate(
        candidate: String,
        decodeDepth: Int,
    ): JsonElement? {
        if (decodeDepth > 4) return null
        val trimmed = candidate.trim().trimEnd(';').trim()
        if (trimmed.isBlank()) return null
        val directParsed = runCatching { structuredJson.parseToJsonElement(trimmed) }.getOrNull()
        if (directParsed != null) {
            if (directParsed is JsonObject || directParsed is JsonArray) {
                return directParsed
            }
            val primitiveContent = (directParsed as? JsonPrimitive)
                ?.contentOrNull
                ?.trim()
                .orEmpty()
            if (looksLikeStructuredPayload(primitiveContent)) {
                return parseStructuredCandidate(primitiveContent, decodeDepth + 1)
            }
        }
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            val decoded = runCatching { structuredJson.decodeFromString<String>(trimmed) }.getOrNull()
            if (!decoded.isNullOrBlank()) {
                return parseStructuredCandidate(decoded, decodeDepth + 1)
            }
        }
        val normalizedCandidate = normalizeJsonLikePayload(trimmed)
            ?.takeIf { it != trimmed }
            ?: return null
        return parseStructuredCandidate(normalizedCandidate, decodeDepth + 1)
    }
    private fun looksLikeStructuredPayload(rawValue: String): Boolean {
        if (rawValue.isBlank()) return false
        val trimmed = rawValue.trim()
        return trimmed.startsWith("{") ||
            trimmed.startsWith("[") ||
            trimmed.startsWith("\"{") ||
            trimmed.startsWith("\"[") ||
            trimmed.startsWith("'{") ||
            trimmed.startsWith("'[") ||
            trimmed.startsWith("{\\\"") ||
            trimmed.startsWith("[\\\"") ||
            (trimmed.contains("\"type\"") && trimmed.contains("content")) ||
            (trimmed.contains("'type'") && trimmed.contains("content"))
    }
    private fun extractJsonCandidate(rawPayload: String): String? {
        val trimmed = rawPayload.trim()
        if (trimmed.startsWith("<")) {
            val htmlTextCandidate = Jsoup.parse(trimmed).body().wholeText().trim()
            if (looksLikeStructuredPayload(htmlTextCandidate)) {
                return htmlTextCandidate
            }
        }
        val objectStart =
            trimmed.indexOf('{').takeIf { it >= 0 } ?: trimmed.indexOf('[').takeIf { it >= 0 } ?: return null
        val objectEnd = trimmed.lastIndexOf('}').takeIf { it > objectStart }
            ?: trimmed.lastIndexOf(']').takeIf { it > objectStart }
            ?: return null
        return trimmed.substring(objectStart, objectEnd + 1).trim()
    }
    private fun normalizeJsonLikePayload(rawPayload: String): String? {
        var candidate = rawPayload
            .trim()
            .removePrefix("\uFEFF")
            .trim()
        if (candidate.startsWith("return ")) {
            candidate = candidate.removePrefix("return ").trim()
        }
        candidate = candidate.trimEnd(';').trim()
        if (!looksLikeStructuredPayload(candidate)) return null
        if (candidate.startsWith("'") && candidate.endsWith("'")) {
            val inner = candidate.substring(1, candidate.lastIndex).replace("\"", "\\\"")
            candidate = "\"$inner\""
        }
        if (candidate.contains("\\\"")) {
            candidate = candidate.replace("\\\"", "\"")
        }
        if (candidate.contains("\\n")) {
            candidate = candidate.replace("\\n", "\n")
        }
        if (candidate.contains("\\t")) {
            candidate = candidate.replace("\\t", "\t")
        }
        candidate = Regex("([\\{,]\\s*)([A-Za-z_][A-Za-z0-9_\\-]*)(\\s*:)").replace(candidate, "$1\"$2\"$3")
        candidate = Regex("\"([A-Za-z_][A-Za-z0-9_\\-]*)\\s*:\\s*\"").replace(candidate, "\"$1\":\"")
        candidate = Regex("'([^'\\\\]*(?:\\\\.[^'\\\\]*)*)'").replace(candidate) { match ->
            "\"${match.groupValues[1].replace("\"", "\\\"")}\""
        }
        candidate = Regex(",\\s*([}\\]])").replace(candidate, "$1")
        return candidate
    }
    private fun findStructuredNode(element: JsonElement): JsonElement? {
        return when (element) {
            is JsonObject -> {
                if (isStructuredNode(element)) {
                    element
                } else {
                    listOf("content", "data", "body", "result", "payload", "value", "chapter")
                        .firstNotNullOfOrNull { key ->
                            val nested = element[key] ?: return@firstNotNullOfOrNull null
                            findStructuredNode(nested)
                                ?: parseStructuredRoot(nested.asStringOrNull().orEmpty())?.let(::findStructuredNode)
                        }
                }
            }
            is JsonArray -> {
                val hasStructuredObjects = element.any {
                    (it as? JsonObject)?.let(::isStructuredNode) == true
                }
                if (hasStructuredObjects) element else null
            }
            else -> null
        }
    }
    private fun isStructuredNode(element: JsonObject): Boolean {
        val normalizedType = normalizeStructuredType(element["type"].asStringOrNull())
        if (normalizedType != null && normalizedType in STRUCTURED_NODE_TYPES) {
            return true
        }
        return (element["content"] is JsonArray) ||
            (element["content"] is JsonObject) ||
            (element["text"].asStringOrNull() != null) ||
            (element["attrs"] is JsonObject)
    }
    private fun extractStructuredAttachmentUrls(root: JsonElement): Map<String, String> {
        val rootObject = root as? JsonObject ?: return emptyMap()
        val mapping = mutableMapOf<String, String>()
        fun appendAttachmentMapping(attachment: JsonObject) {
            val url = attachment["url"].asStringOrNull()?.trim().orEmpty()
            if (url.isBlank()) return
            attachment["id"].asStringOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let { key ->
                mapping[key] = url
            }
            attachment["name"].asStringOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let { key ->
                mapping[key] = url
            }
        }
        when (val attachments = rootObject["attachments"]) {
            is JsonArray -> attachments.forEach { entry ->
                val attachment = entry as? JsonObject ?: return@forEach
                appendAttachmentMapping(attachment)
            }
            is JsonObject -> attachments.forEach { (key, value) ->
                val valueObject = value as? JsonObject
                val url = valueObject?.get("url").asStringOrNull()?.trim().orEmpty()
                    .ifBlank { value.asStringOrNull().orEmpty().trim() }
                if (url.isNotBlank()) {
                    mapping[key.trim()] = url
                }
            }
            else -> Unit
        }
        return mapping
    }
    private fun renderStructuredElementAsHtml(
        element: JsonElement,
        attachmentUrls: Map<String, String>,
    ): String {
        return when (element) {
            is JsonObject -> renderStructuredNodeAsHtml(element, attachmentUrls)
            is JsonArray -> buildString {
                element.forEach { node ->
                    append(renderStructuredElementAsHtml(node, attachmentUrls))
                }
            }
            else -> ""
        }
    }
    private fun renderStructuredNodeAsHtml(
        node: JsonObject,
        attachmentUrls: Map<String, String>,
    ): String {
        val type = normalizeStructuredType(node["type"].asStringOrNull()).orEmpty()
        val attrs = node["attrs"] as? JsonObject
        val children = node["content"] as? JsonArray
        fun renderChildren(): String {
            if (children == null) return ""
            return buildString {
                children.forEach { child ->
                    append(renderStructuredElementAsHtml(child, attachmentUrls))
                }
            }
        }
        return when (type) {
            "doc" -> renderChildren()
            "paragraph" -> "<p>${renderChildren()}</p>"
            "heading" -> {
                val level = attrs?.get("level").asIntOrNull()?.coerceIn(1, 6) ?: 1
                "<h$level>${renderChildren()}</h$level>"
            }
            "bulletlist" -> "<ul>${renderChildren()}</ul>"
            "orderedlist" -> "<ol>${renderChildren()}</ol>"
            "listitem" -> "<li>${renderChildren()}</li>"
            "blockquote" -> "<blockquote>${renderChildren()}</blockquote>"
            "hardbreak" -> "<br/>"
            "horizontalrule" -> "<hr/>"
            "image" -> renderStructuredImageNode(attrs, attachmentUrls)
            "text" -> {
                val escaped = node["text"].asStringOrNull().orEmpty().escapeHtml()
                applyStructuredMarks(escaped, node["marks"] as? JsonArray)
            }
            else -> {
                val inlineText = node["text"]
                    .asStringOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.escapeHtml()
                if (inlineText != null) {
                    applyStructuredMarks(inlineText, node["marks"] as? JsonArray)
                } else {
                    renderChildren()
                }
            }
        }
    }
    private fun renderStructuredImageNode(
        attrs: JsonObject?,
        attachmentUrls: Map<String, String>,
    ): String {
        if (attrs == null) return ""
        val directUrl = attrs["src"].asStringOrNull()?.trim().orEmpty()
        val altText = attrs["alt"].asStringOrNull().orEmpty().escapeHtml()
        if (directUrl.isNotBlank()) {
            return "<img src=\"${directUrl.escapeHtmlAttribute()}\" alt=\"$altText\" />"
        }
        val imageReferences = mutableListOf<String>()
        attrs["image"].asStringOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let { imageReferences += it }
        when (val imagesNode = attrs["images"]) {
            is JsonArray -> imagesNode.forEach { entry ->
                when (entry) {
                    is JsonObject -> {
                        entry["image"].asStringOrNull()?.trim()?.takeIf {
                            it.isNotBlank()
                        }?.let { imageReferences += it }
                    }
                    is JsonPrimitive -> entry.contentOrNull?.trim()?.takeIf { it.isNotBlank() }?.let {
                        imageReferences +=
                            it
                    }
                    else -> Unit
                }
            }
            is JsonObject -> {
                imagesNode["image"].asStringOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let { imageReferences += it }
            }
            else -> Unit
        }
        val resolvedUrls = imageReferences.mapNotNull { reference ->
            attachmentUrls[reference]
        }
        if (resolvedUrls.isEmpty()) return ""
        return resolvedUrls.joinToString(separator = "") { url ->
            "<img src=\"${url.escapeHtmlAttribute()}\" alt=\"$altText\" />"
        }
    }
    private fun applyStructuredMarks(
        text: String,
        marks: JsonArray?,
    ): String {
        if (marks == null || marks.isEmpty()) return text
        var rendered = text
        marks.forEach { markElement ->
            val mark = markElement as? JsonObject ?: return@forEach
            rendered = when (normalizeStructuredType(mark["type"].asStringOrNull())) {
                "bold", "strong" -> "<strong>$rendered</strong>"
                "italic", "em" -> "<em>$rendered</em>"
                "underline" -> "<u>$rendered</u>"
                "strike", "s" -> "<s>$rendered</s>"
                "code" -> "<code>$rendered</code>"
                "link" -> {
                    val href = (mark["attrs"] as? JsonObject)
                        ?.get("href")
                        .asStringOrNull()
                        .orEmpty()
                    if (href.isBlank()) rendered else "<a href=\"${href.escapeHtmlAttribute()}\">$rendered</a>"
                }
                else -> rendered
            }
        }
        return rendered
    }
    private fun JsonElement?.asStringOrNull(): String? {
        return (this as? JsonPrimitive)?.contentOrNull
    }
    private fun JsonElement?.asIntOrNull(): Int? {
        return (this as? JsonPrimitive)?.intOrNull
    }
    private fun String.escapeHtml(): String {
        return replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
    private fun String.escapeHtmlAttribute(): String {
        return escapeHtml()
    }
    private fun renderStructuredPayloadFallback(rawPayload: String): String? {
        val candidate = extractJsonCandidate(rawPayload) ?: rawPayload.trim()
        if (!looksLikeStructuredPayload(candidate)) return null
        val normalized = normalizeJsonLikePayload(candidate) ?: candidate
        val textSegments = extractStructuredTextFallbackSegments(normalized)
        val imageSegments = extractStructuredImageFallbackUrls(normalized)
        if (textSegments.isEmpty() && imageSegments.isEmpty()) return null
        val html = buildString {
            textSegments.forEach { segment ->
                append("<p>${segment.escapeHtml()}</p>")
            }
            imageSegments.forEach { url ->
                append("<img src=\"${url.escapeHtmlAttribute()}\" alt=\"\" />")
            }
        }.trim()
        return html.takeIf { it.isNotBlank() }
    }
    private fun looksLikeHtmlPayload(rawPayload: String): Boolean {
        val trimmed = rawPayload.trim()
        if (!trimmed.contains('<') || !trimmed.contains('>')) return false
        return Regex("(?is)<\\s*(html|body|div|main|article|section|p|ul|ol|li|h1|h2|h3|h4|h5|h6|span)\\b")
            .containsMatchIn(trimmed)
    }
    private fun extractStructuredTextFallbackSegments(payload: String): List<String> {
        val results = mutableListOf<String>()
        val textRegex = Regex("(?is)\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        textRegex.findAll(payload).forEach { match ->
            val rawText = match.groupValues.getOrNull(1).orEmpty()
            val decodedText = rawText
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\r", "")
                .replace("\\\"", "\"")
                .replace("\\u00A0", " ")
                .sanitizeTextBlock()
            if (decodedText.isBlank()) return@forEach
            val contextStart = (match.range.first - 220).coerceAtLeast(0)
            val context = payload.substring(contextStart, match.range.first).lowercase()
            val isListItemContext = context.contains("listitem") || context.contains("bulletlist")
            val normalized = if (isListItemContext && !decodedText.startsWith("•")) {
                "• $decodedText"
            } else {
                decodedText
            }
            results += normalized
        }
        return results
    }
    private fun extractStructuredImageFallbackUrls(payload: String): List<String> {
        val urlRegex = Regex("(?is)\"url\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val directHttpRegex = Regex("(?i)https?://[^\\s\"'<>]+\\.(?:png|jpe?g|gif|webp|bmp|svg)")
        val urls = linkedSetOf<String>()
        urlRegex.findAll(payload).forEach { match ->
            val url = match.groupValues.getOrNull(1).orEmpty()
                .replace("\\\\", "\\")
                .replace("\\/", "/")
                .replace("\\\"", "\"")
                .trim()
            if (url.startsWith("http://") || url.startsWith("https://")) {
                urls += url
            }
        }
        directHttpRegex.findAll(payload).forEach { match ->
            val url = match.value.trim()
            if (url.isNotBlank()) {
                urls += url
            }
        }
        return urls.toList()
    }
    private fun normalizeStructuredType(type: String?): String? {
        return type
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.lowercase()
            ?.replace("_", "")
            ?.replace("-", "")
    }
    private suspend fun saveHistorySnapshot(chapterId: Long, sessionReadDurationMs: Long) {
        runCatching {
            resolvedHistoryRepository?.upsertNovelHistory(
                NovelHistoryUpdate(
                    chapterId = chapterId,
                    readAt = Date(),
                    sessionReadDuration = sessionReadDurationMs.coerceAtLeast(0L),
                ),
            )
        }.onFailure { error ->
            logcat(LogPriority.ERROR, error) { "Failed to save novel history snapshot" }
        }
    }
    private suspend fun flushPendingHistorySnapshot(
        chapterId: Long,
        additionalReadDurationMs: Long = 0L,
    ) {
        val readDurationMs = (pendingHistoryReadDurationMs + additionalReadDurationMs).coerceAtLeast(0L)
        if (readDurationMs <= 0L) return
        pendingHistoryReadDurationMs = 0L
        saveHistorySnapshot(chapterId, readDurationMs)
    }
    sealed interface State {
        data class Loading(val readerSettings: NovelReaderSettings? = null) : State
        data class Error(val message: String?) : State
        data class Success(
            val novel: Novel,
            val chapter: NovelChapter,
            val html: String,
            val enableJs: Boolean,
            val readerSettings: NovelReaderSettings,
            val contentBlocks: List<ContentBlock>,
            val richContentBlocks: List<NovelRichContentBlock>,
            val richContentUnsupportedFeaturesDetected: Boolean,
            val lastSavedIndex: Int,
            val lastSavedScrollOffsetPx: Int,
            val lastSavedWebProgressPercent: Int,
            val lastSavedPageReaderProgress: PageReaderProgress? = null,
            val previousChapterId: Long?,
            val previousChapterName: String? = null,
            val nextChapterId: Long?,
            val nextChapterName: String? = null,
            val seriesInterstitialState: SeriesInterstitialState? = null,
            val chapterWebUrl: String?,
            val selectedTextTranslationSelection: NovelSelectedTextSelection? = null,
            val selectedTextTranslationUiState: NovelSelectedTextTranslationUiState =
                NovelSelectedTextTranslationUiState.Idle,
            val isGeminiTranslating: Boolean = false,
            val geminiTranslationProgress: Int = 0,
            val isGeminiTranslationVisible: Boolean = false,
            val hasGeminiTranslationCache: Boolean = false,
            val geminiLogs: List<String> = emptyList(),
            val isGoogleTranslating: Boolean = false,
            val googleTranslationProgress: Int = 0,
            val isGoogleTranslationVisible: Boolean = false,
            val hasGoogleTranslationCache: Boolean = false,
            val googleLogs: List<String> = emptyList(),
            val translationPhase: TranslationPhase = TranslationPhase.IDLE,
            val ttsUiState: NovelReaderTtsUiState = NovelReaderTtsUiState(),
            val airforceModelIds: List<String> = emptyList(),
            val isAirforceModelsLoading: Boolean = false,
            val isTestingAirforceConnection: Boolean = false,
            val openRouterModelIds: List<String> = emptyList(),
            val isOpenRouterModelsLoading: Boolean = false,
            val isTestingOpenRouterConnection: Boolean = false,
            val deepSeekModelIds: List<String> = emptyList(),
            val isDeepSeekModelsLoading: Boolean = false,
            val isTestingDeepSeekConnection: Boolean = false,
        ) : State {
            val textBlocks: List<String>
                get() = contentBlocks
                    .asSequence()
                    .filterIsInstance<ContentBlock.Text>()
                    .map { it.text }
                    .toList()
        }
    }
    sealed interface ContentBlock {
        data class Text(val text: String) : ContentBlock
        data class Image(val url: String, val alt: String?) : ContentBlock
    }
    private data class PendingProgressPersistence(
        val chapterId: Long,
        val novelId: Long,
        val chapterNumber: Int,
        val read: Boolean,
        val lastPageRead: Long,
        val emitReadEvent: Boolean,
        val emitNovelCompleted: Boolean,
        val sessionReadDurationMs: Long,
    ) {
        fun merge(other: PendingProgressPersistence): PendingProgressPersistence {
            require(chapterId == other.chapterId) {
                "Pending progress persistence can only merge updates for the same chapter"
            }
            return copy(
                read = other.read,
                lastPageRead = other.lastPageRead,
                emitReadEvent = emitReadEvent || other.emitReadEvent,
                emitNovelCompleted = emitNovelCompleted || other.emitNovelCompleted,
                sessionReadDurationMs = maxOf(sessionReadDurationMs, other.sessionReadDurationMs),
            )
        }
    }
    private data class ChapterNavigation(
        val previousChapterId: Long?,
        val previousChapterName: String?,
        val nextChapterId: Long?,
        val nextChapterName: String?,
    )
    companion object {
        private const val JAOMIX_PAGE_SOURCE_ORDER_STRIDE = 1_000L
        private const val MAX_DEEPSEEK_CONCURRENCY = 32
        private const val PRIVATE_FALLBACK_CHUNK_SIZE = 40
        private const val PRIVATE_FALLBACK_CONCURRENCY = 1
        private const val TTS_BASE_MILLIS_PER_WORD = 360f
        private const val TTS_MIN_UTTERANCE_DURATION_MS = 700L
        private const val TTS_WORD_PROGRESS_UPDATE_INTERVAL_MS = 60L
        private const val DEEPSEEK_TEMPERATURE_MIN = 1.3f
        private const val DEEPSEEK_TEMPERATURE_MAX = 1.5f
        private const val DEEPSEEK_TOP_P_MIN = 0.9f
        private const val DEEPSEEK_TOP_P_MAX = 0.95f
        private const val DEEPSEEK_DEFAULT_PRESENCE_PENALTY = 0.15f
        private const val DEEPSEEK_DEFAULT_FREQUENCY_PENALTY = 0.15f
        private val STRUCTURED_NODE_TYPES = setOf(
            "doc",
            "paragraph",
            "heading",
            "bulletlist",
            "orderedlist",
            "listitem",
            "blockquote",
            "hardbreak",
            "horizontalrule",
            "image",
            "text",
        )
    }
}

internal fun updateNovelReaderChapterProgressList(
    chapters: List<NovelChapter>,
    chapterId: Long,
    read: Boolean,
    progress: Long,
): List<NovelChapter> {
    val chapterIndex = chapters.indexOfFirst { it.id == chapterId }
    if (chapterIndex < 0) return chapters

    val currentChapter = chapters[chapterIndex]
    if (currentChapter.read == read && currentChapter.lastPageRead == progress) {
        return chapters
    }

    val updatedChapters = chapters.toMutableList()
    updatedChapters[chapterIndex] = currentChapter.copy(
        read = read,
        lastPageRead = progress,
    )
    return updatedChapters
}
internal fun sanitizeChapterHtmlForReader(rawHtml: String): String {
    if (rawHtml.isBlank()) return rawHtml
    val document = Jsoup.parseBodyFragment(rawHtml)
    document.outputSettings().prettyPrint(false)
    document.select(
        "script, style, iframe, svg, canvas, object, embed, form, input, button, select, textarea, noscript, meta, link",
    ).remove()
    document.select("*").forEach { element ->
        val attributesToRemove = element.attributes()
            .asList()
            .map { it.key }
            .filter { attributeName -> attributeName.startsWith("on", ignoreCase = true) }
        attributesToRemove.forEach { attributeName ->
            element.removeAttr(attributeName)
        }
        sanitizeReaderInlineStyle(element.attr("style"))?.let { sanitizedStyle ->
            element.attr("style", sanitizedStyle)
        } ?: element.removeAttr("style")
    }
    return document.body().html()
}
internal fun sanitizeReaderInlineStyle(rawStyle: String): String? {
    if (rawStyle.isBlank()) return null
    val allowedProperties = setOf(
        "text-align",
        "text-indent",
        "font-style",
        "font-weight",
        "text-decoration",
        "color",
        "background-color",
    )
    val sanitizedDeclarations = rawStyle.split(';')
        .mapNotNull { declaration ->
            val delimiterIndex = declaration.indexOf(':')
            if (delimiterIndex <= 0) return@mapNotNull null
            val propertyName = declaration.substring(0, delimiterIndex).trim().lowercase(Locale.US)
            val propertyValue = declaration.substring(delimiterIndex + 1).trim()
            if (propertyName !in allowedProperties || propertyValue.isBlank()) return@mapNotNull null
            "$propertyName: $propertyValue"
        }
    return sanitizedDeclarations.joinToString("; ").ifBlank { null }
}
internal fun isGeminiSourceLanguageEnglish(sourceLang: String): Boolean {
    val normalized = sourceLang.trim().lowercase()
    return normalized == "english" || normalized == "en" || normalized == "английский"
}
internal fun hasReachedGeminiNextChapterTranslationPrefetchThreshold(
    currentIndex: Int,
    totalItems: Int,
): Boolean {
    if (totalItems <= 0 || currentIndex < 0) return false
    return if (totalItems == 100) {
        currentIndex >= 30
    } else {
        totalItems > 1 && ((currentIndex + 1).toFloat() / totalItems.toFloat()) >= 0.3f
    }
}
internal object NovelReaderChapterPrefetchCache {
    private const val MAX_ENTRIES = 4
    private val cache = object : LinkedHashMap<Long, String>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, String>?): Boolean {
            return size > MAX_ENTRIES
        }
    }
    fun get(chapterId: Long): String? {
        return synchronized(cache) {
            cache[chapterId]
        }
    }
    fun put(chapterId: Long, html: String) {
        synchronized(cache) {
            cache[chapterId] = html
        }
    }
    fun contains(chapterId: Long): Boolean {
        return synchronized(cache) {
            cache.containsKey(chapterId)
        }
    }
    fun clear() {
        synchronized(cache) {
            cache.clear()
        }
    }
}
