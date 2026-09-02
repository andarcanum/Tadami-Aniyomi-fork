package eu.kanade.tachiyomi.ui.reader.novel
import android.app.Application
import android.os.SystemClock
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.entries.novel.model.toSNovel
import eu.kanade.domain.entries.shouldRecordNovelCompletion
import eu.kanade.domain.items.novelchapter.interactor.SyncNovelChaptersWithSource
import eu.kanade.domain.source.interactor.NovelReaderIncognitoState
import eu.kanade.domain.source.novel.interactor.GetNovelIncognitoState
import eu.kanade.presentation.reader.novel.NovelAutoScrollHandoffState
import eu.kanade.presentation.reader.novel.NovelReaderAutoScrollHandoffPolicy
import eu.kanade.presentation.reader.novel.NovelReaderPageReaderHandoffTarget
import eu.kanade.presentation.reader.novel.NovelReaderTtsChapterHandoffPolicy
import eu.kanade.presentation.reader.novel.SeriesInterstitialState
import eu.kanade.presentation.reader.novel.resolveReaderProgressToPersist
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
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.source.novel.NovelWebUrlSource
import eu.kanade.tachiyomi.ui.novel.resolveNovelResumeChapter
import eu.kanade.tachiyomi.ui.novel.sortedByNovelReadingOrder
import eu.kanade.tachiyomi.ui.reader.model.ReaderFinaleState
import eu.kanade.tachiyomi.ui.reader.model.daysOnShelf
import eu.kanade.tachiyomi.ui.reader.novel.dictionary.CompositeNovelDictionaryProvider
import eu.kanade.tachiyomi.ui.reader.novel.dictionary.OfflineStarDictDictionaryProvider
import eu.kanade.tachiyomi.ui.reader.novel.replace.applyReplaceRulesToHtml
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderOverride
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekPromptResolver
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPromptResolver
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleUnofficialSelectedTextTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.MistralModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.MistralPromptResolver
import eu.kanade.tachiyomi.ui.reader.novel.translation.MistralTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelDictionaryProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationCacheResolver
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationDiskCacheStore
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelSelectedTextTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.NvidiaModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.NvidiaTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OllamaCloudModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OllamaCloudTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OnlineDictionaryProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.TranslationPhase
import eu.kanade.tachiyomi.ui.reader.novel.translation.effectiveTranslationBatchSize
import eu.kanade.tachiyomi.ui.reader.novel.translation.hasConfiguredTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.isPrivateBridgeUnlocked
import eu.kanade.tachiyomi.ui.reader.novel.translation.requiresPrivateBridgeUnlock
import eu.kanade.tachiyomi.ui.reader.novel.translation.shouldUseSinglePrivateChapterRequestMode
import eu.kanade.tachiyomi.ui.reader.novel.translation.toDeepSeekTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toGeminiTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toMistralTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toNvidiaTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toOllamaCloudTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toOpenRouterTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toTranslationCacheRequirements
import eu.kanade.tachiyomi.ui.reader.novel.translation.translationCacheModelId
import eu.kanade.tachiyomi.ui.reader.novel.translation.translationConcurrencyLimit
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
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsTextSource
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsWordTokenizer
import eu.kanade.tachiyomi.ui.reader.novel.tts.SharedNovelTtsSessionStore
import eu.kanade.tachiyomi.ui.reader.novel.tts.resolveNovelTtsVoiceSelection
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.system.isNightMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import tachiyomi.domain.book.novel.model.NovelHighlight
import tachiyomi.domain.book.novel.model.NovelHighlightWithChapter
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.series.novel.interactor.GetNovelSeriesWithEntries
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.DateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class ProviderApiTestStatus {
    Idle,
    Loading,
    Success,
    Error,
}

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
    private val getNovelBookState: tachiyomi.domain.book.novel.interactor.GetNovelBookState = Injekt.get(),
    private val setNovelBookProgress: tachiyomi.domain.book.novel.interactor.SetNovelBookProgress =
        Injekt.get(),
    private val pluginStorage: NovelPluginStorage = Injekt.get(),
    private val historyRepository: NovelHistoryRepository? = null,
    private val basePreferences: BasePreferences = Injekt.get(),
    private val getIncognitoState: GetNovelIncognitoState = Injekt.get(),
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
    private val activityDataRepository: ActivityDataRepository = Injekt.get(),
    private val updateNovel: UpdateNovel = Injekt.get(),
    private val mangaReaderPreferences: ReaderPreferences = Injekt.get(),
    private val addNovelHighlight: tachiyomi.domain.book.novel.interactor.AddNovelHighlight = Injekt.get(),
    private val updateNovelHighlight: tachiyomi.domain.book.novel.interactor.UpdateNovelHighlight = Injekt.get(),
    private val deleteNovelHighlight: tachiyomi.domain.book.novel.interactor.DeleteNovelHighlight = Injekt.get(),
    private val novelHighlightRepository:
    tachiyomi.domain.book.novel.repository.NovelHighlightRepository = Injekt.get(),
    private val isSystemDark: () -> Boolean = { Injekt.get<Application>().isNightMode() },
    private val geminiTranslationService: GeminiTranslationService = run {
        val app = Injekt.get<Application>()
        val networkHelper = Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>()
        val json = Injekt.get<Json>()
        val geminiClient = networkHelper.client.newBuilder()
            .callTimeout(300, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .build()
        GeminiTranslationService(
            client = geminiClient,
            json = json,
            promptResolver = GeminiPromptResolver(app),
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
    private val mistralTranslationService: MistralTranslationService = run {
        val app = Injekt.get<Application>()
        val networkHelper = Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>()
        val json = Injekt.get<Json>()
        val mistralClient = networkHelper.client.newBuilder()
            .callTimeout(300, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .build()
        MistralTranslationService(
            client = mistralClient,
            json = json,
            resolveSystemPrompt = { mode, family ->
                MistralPromptResolver(app).resolveSystemPrompt(mode, family)
            },
        )
    },
    private val mistralModelsService: MistralModelsService = run {
        val networkHelper = Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>()
        val json = Injekt.get<Json>()
        MistralModelsService(
            client = networkHelper.client,
            json = json,
        )
    },
    private val nvidiaTranslationService: NvidiaTranslationService = run {
        val networkHelper = Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>()
        val json = Injekt.get<Json>()
        val nvidiaClient = networkHelper.client.newBuilder()
            .callTimeout(300, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .build()
        NvidiaTranslationService(
            client = nvidiaClient,
            json = json,
        )
    },
    private val nvidiaModelsService: NvidiaModelsService = run {
        val networkHelper = Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>()
        val json = Injekt.get<Json>()
        NvidiaModelsService(
            client = networkHelper.client,
            json = json,
        )
    },
    private val ollamaCloudTranslationService: OllamaCloudTranslationService = run {
        val networkHelper = Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>()
        val json = Injekt.get<Json>()
        val ollamaClient = networkHelper.client.newBuilder()
            .callTimeout(300, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .build()
        OllamaCloudTranslationService(
            client = ollamaClient,
            json = json,
        )
    },
    private val ollamaCloudModelsService: OllamaCloudModelsService = run {
        val networkHelper = Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>()
        val json = Injekt.get<Json>()
        OllamaCloudModelsService(
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
    private val novelDictionaryProvider: NovelDictionaryProvider = run {
        val networkHelper = Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>()
        val json = Injekt.get<Json>()
        val prefs = Injekt.get<NovelReaderPreferences>()
        CompositeNovelDictionaryProvider(
            modeProvider = { prefs.novelDictionarySource().get() },
            online = OnlineDictionaryProvider(
                client = networkHelper.client,
                json = json,
            ),
            offline = OfflineStarDictDictionaryProvider(
                context = Injekt.get<Application>(),
                disabledIdsProvider = {
                    prefs.novelDictionaryDisabledOfflineIds().get()
                        .split(",")
                        .mapNotNull { id -> id.trim().takeIf(String::isNotEmpty) }
                        .toSet()
                },
            ),
        )
    },
    private val googleTranslationService: GoogleTranslationService = run {
        val networkHelper = Injekt.get<NetworkHelper>()
        GoogleTranslationService(client = networkHelper.client)
    },
    private val translationQueueManager: TranslationQueueManager = Injekt.get(),
) : StateScreenModel<NovelReaderScreenModel.State>(State.Loading()),
    NovelBookReaderHost,
    NovelTtsHost,
    NovelAiProviderHost,
    NovelTranslationHost,
    NovelProgressPersistenceHost,
    NovelSelectionTranslationHost,
    NovelTranslationBatchHost {
    private val contentPrefetchService = ContentPrefetchService(
        environment = runCatching {
            AndroidContentPrefetchEnvironment(Injekt.get<Application>())
        }.getOrElse {
            AllowAllContentPrefetchEnvironment
        },
    )
    private val application = Injekt.get<Application>()

    /**
     * Book-mode subsystem, extracted into its own controller so the screen model stays focused on
     * the chapter-by-chapter reader. The controller reaches the shared reader state through
     * [NovelBookReaderHost] implemented below.
     */
    private val bookController = NovelBookReaderController(host = this)

    /** Window the mounted renderer holds; renderers pull their sections from it themselves. */
    internal val bookWindow: kotlinx.coroutines.flow.StateFlow<NovelBookWindowState> =
        bookController.bookWindow

    /** Section markup for a renderer that is missing it. */
    internal suspend fun loadBookSectionHtml(sectionIndex: Int): String? =
        bookController.bookSectionHtml(sectionIndex)

    internal val bookEngineSpine: NovelBookSpine
        get() = bookController.bookEngineSpine

    internal val bookEngineLocation: NovelBookLocation
        get() = bookController.bookEngineLocation

    /** Explicit renderer moves; the current position is never pushed back into the renderer. */
    internal val bookSeekRequests: kotlinx.coroutines.flow.StateFlow<BookSeekRequest?> =
        bookController.bookSeekRequests

    internal fun onBookSeekApplied(seekRequestId: Long) =
        bookController.onBookSeekApplied(seekRequestId)

    /** Writes the book position through right away, e.g. on ON_STOP or when leaving the reader. */
    internal fun flushBookModeProgress() = bookController.flushBookModeProgress()

    internal suspend fun loadBookEngineDocument(section: NovelBookSection): NovelBookDocument =
        bookController.loadBookEngineDocument(section)

    fun nativeBookBlocksForSection(sectionIndex: Int): List<NovelRichContentBlock>? =
        bookController.nativeBookBlocksForSection(sectionIndex)

    // ---------------------------------------------------------------------------------------------
    // Book mode delegates. All book-mode logic lives in [bookController]; the screen model only
    // forwards the reader's calls and hosts the shared state through [NovelBookReaderHost].
    // ---------------------------------------------------------------------------------------------

    fun observeReadingModeChanges(loadedChapter: NovelChapter) {
        bookController.observeReadingModeChanges(loadedChapter)
    }

    internal fun onBookModeScroll(sectionIndex: Int, sectionFraction: Float) =
        bookController.onBookModeScroll(sectionIndex, sectionFraction)

    internal fun onBookEngineLocationChanged(location: NovelBookLocation) =
        bookController.onBookEngineLocationChanged(location)

    internal fun seekBookModeToProgress(fraction: Float) = bookController.seekBookModeToProgress(fraction)

    internal fun onBookModeChapterSelected(chapterId: Long): Boolean =
        bookController.onBookModeChapterSelected(chapterId)

    internal fun onBookModeRetrySection(sectionIndex: Int) =
        bookController.onBookModeRetrySection(sectionIndex)

    internal fun prepareWholeBook() = bookController.prepareWholeBook()

    // ---------------------------------------------------------------------------------------------
    // NovelBookReaderHost implementation: the book controller reaches the shared reader state here.
    // ---------------------------------------------------------------------------------------------

    override val bookScope: CoroutineScope get() = screenModelScope

    override fun bookCurrentNovel(): Novel? = currentNovel

    override fun bookCurrentChapter(): NovelChapter? = currentChapter

    override fun bookChapterOrderList(): List<NovelChapter> = chapterOrderList

    override fun bookFullChapterOrderList(): List<NovelChapter> = fullChapterOrderList

    override fun bookMarkChapterReadInMemory(chapterId: Long) {
        val chapterIndex = chapterOrderList.indexOfFirst { it.id == chapterId }
        if (chapterIndex >= 0 && !chapterOrderList[chapterIndex].read) {
            chapterOrderList[chapterIndex] = chapterOrderList[chapterIndex].copy(read = true)
        }
        // The book completion check runs against the full chapter list, so the in-memory read mark
        // has to reach it too (the DB write arrives asynchronously through the progress pipeline).
        val fullChapterIndex = fullChapterOrderList.indexOfFirst { it.id == chapterId }
        if (fullChapterIndex >= 0 && !fullChapterOrderList[fullChapterIndex].read) {
            fullChapterOrderList = fullChapterOrderList.toMutableList().also { list ->
                list[fullChapterIndex] = list[fullChapterIndex].copy(read = true)
            }
        }
    }

    override fun bookUpdateSuccessState(
        transform: (NovelReaderScreenModel.State.Success) -> NovelReaderScreenModel.State.Success,
    ) {
        val successState = mutableState.value as? State.Success ?: return
        mutableState.value = transform(successState)
    }

    override fun bookAdoptBookModeChapter(chapterId: Long) = adoptBookModeChapter(chapterId)

    /**
     * Moves the session's chapter anchor to the chapter under the caret.
     *
     * Over a book the reader never opens a new chapter, so the anchor stayed on the chapter the
     * session was entered from: history, the exit snapshot and the read threshold all described
     * chapter one no matter how far the reader had scrolled. The anchor now follows the text, so
     * the stored position and the history entry describe the same chapter and resuming lands where
     * reading stopped.
     */
    private fun adoptBookModeChapter(chapterId: Long) {
        val chapter = chapterOrderList.firstOrNull { it.id == chapterId }
            ?: fullChapterOrderList.firstOrNull { it.id == chapterId }
            ?: return
        val previousChapterId = currentChapter?.id
        if (previousChapterId == chapter.id) return
        currentChapter = chapter
        // Keep the prev/next chapter targets in sync with the reading position: the chapter reader
        // recomputes them in updateContent, but book mode never re-enters that path, so the anchor
        // following the text has to publish the refreshed neighbours itself.
        publishBookModeChapterNavigation(chapter)
        // The overlay indicator follows the text, not the session: the queue may be working on a
        // chapter the reader has already left behind.
        translationController.onActiveChapterChanged(chapter.id)
        lastSavedRead = chapter.read
        lastSavedProgress = chapter.lastPageRead
        initialProgressIndex = 0
        hasProgressChanged = true
        progressPersistenceController.resetSessionReadTimer()
        if (previousChapterId != null) {
            screenModelScope.launch {
                withContext(NonCancellable) {
                    progressPersistenceController.flushPendingHistorySnapshot(previousChapterId)
                }
            }
        }
    }

    /**
     * Refreshes [State.Success.previousChapterId]/[nextChapterId] after the book reading position
     * crossed into another chapter. Mirrors the navigation computation inside [updateContent].
     */
    private fun publishBookModeChapterNavigation(chapter: NovelChapter) {
        val allChapters = if (fullChapterOrderList.isNotEmpty()) fullChapterOrderList else chapterOrderList
        val previousResult = NovelReaderChapterWindow.navigate(
            currentChapterId = chapter.id,
            allChapters = allChapters,
            direction = -1,
            windowRadius = NovelReaderChapterWindow.DEFAULT_WINDOW_RADIUS,
        )
        val previousChapter = previousResult.newCurrentChapter.takeIf { it.id != chapter.id }
        val nextResult = NovelReaderChapterWindow.navigate(
            currentChapterId = chapter.id,
            allChapters = allChapters,
            direction = 1,
            windowRadius = NovelReaderChapterWindow.DEFAULT_WINDOW_RADIUS,
        )
        val nextChapter = nextResult.newCurrentChapter.takeIf { it.id != chapter.id }
        bookUpdateSuccessState { success ->
            success.copy(
                previousChapterId = previousChapter?.id,
                previousChapterName = previousChapter?.name,
                nextChapterId = nextChapter?.id,
                nextChapterName = nextChapter?.name,
            )
        }
    }

    override fun bookEnqueueProgressPersistence(update: PendingProgressPersistence) =
        progressPersistenceController.enqueueProgressPersistence(update)

    override fun bookApplyBookSectionTranslation(chapterId: Long, bodyHtml: String): String =
        applyBookSectionTranslation(chapterId, bodyHtml)

    override fun bookGeminiTranslationVisible(): Boolean = translationState.isGeminiTranslationVisible

    override fun bookGoogleTranslationVisible(): Boolean = translationState.isGoogleTranslationVisible

    override fun bookTtsChapterRepository(): NovelTtsChapterRepository = ttsChapterRepository

    // ---------------------------------------------------------------------------------------------
    // NovelTtsHost implementation.
    // ---------------------------------------------------------------------------------------------

    override val ttsScope: CoroutineScope get() = screenModelScope

    override fun ttsCurrentNovel(): Novel? = currentNovel

    override fun ttsCurrentChapter(): NovelChapter? = currentChapter

    override fun ttsReaderSettings(): NovelReaderSettings? =
        (mutableState.value as? State.Success)?.readerSettings

    override fun ttsUpdateSuccessState(
        transform: (NovelReaderScreenModel.State.Success) -> NovelReaderScreenModel.State.Success,
    ) {
        val successState = mutableState.value as? State.Success ?: return
        mutableState.value = transform(successState)
    }

    override fun ttsRefreshTtsUiState(uiState: NovelReaderTtsUiState) {
        val state = mutableState.value
        if (state is State.Success) {
            mutableState.value = state.copy(ttsUiState = uiState)
        }
    }

    override fun ttsSetTtsUiState(uiState: NovelReaderTtsUiState) {
        ttsController.setTtsUiStateFromReader(uiState)
    }

    override fun ttsBookChapterAtReadingPosition(): NovelChapter? =
        bookController.bookModeChapterAtReadingPosition()

    override fun ttsActiveTranslationChapterId(): Long? = activeTranslationChapterId()

    override fun ttsIsGeminiTranslating(): Boolean = translationState.isGeminiTranslating

    override fun ttsGeminiTranslationJob(): Job? = translationController.geminiTranslationJob()

    override fun ttsIsGeminiTranslationVisible(): Boolean = translationState.isGeminiTranslationVisible

    override fun ttsIsGoogleTranslationVisible(): Boolean = translationState.isGoogleTranslationVisible

    override fun ttsTranslationHolderEmpty(provider: String): Boolean = translationHolder.isEmpty(provider)

    override fun ttsApplyGeminiTranslationToContentBlocks(
        blocks: List<ContentBlock>,
        forceTranslation: Boolean,
    ): List<ContentBlock> = applyGeminiTranslationToContentBlocks(blocks, forceTranslation)

    override fun ttsApplyGoogleTranslationToContentBlocks(
        blocks: List<ContentBlock>,
    ): List<ContentBlock> = applyGoogleTranslationToContentBlocks(blocks)

    override fun ttsUpdateGeminiSetting(
        setGlobal: () -> Unit,
        setOverride: (NovelReaderOverride) -> NovelReaderOverride,
    ) = updateGeminiSetting(setGlobal, setOverride)

    override fun ttsUpdateContent(settings: NovelReaderSettings) = updateContent(settings)

    // ---------------------------------------------------------------------------------------------
    // NovelAiProviderHost implementation.
    // ---------------------------------------------------------------------------------------------

    override val providerScope: CoroutineScope get() = screenModelScope

    override fun providerCurrentNovel(): Novel? = currentNovel

    override fun providerReaderSettings(): NovelReaderSettings? =
        (mutableState.value as? State.Success)?.readerSettings

    override fun providerUpdateContent(settings: NovelReaderSettings) = updateContent(settings)

    override fun providerAddLog(message: String) = addAiTranslationLog(message)

    override suspend fun providerRequestTranslationBatch(
        segments: List<String>,
        settings: NovelReaderSettings,
        onMessage: (String) -> Unit,
    ): List<String?>? = translationBatchExecutor.requestTranslationBatch(segments, settings, onMessage)

    override fun providerApplyAiProvidersState(state: NovelAiProviderState) {
        val successState = mutableState.value as? State.Success ?: return
        mutableState.value = successState.copy(
            aiProviders = State.ReaderAiProvidersState(
                openRouterModelIds = state.openRouterModelIds,
                isOpenRouterModelsLoading = state.isOpenRouterModelsLoading,
                isTestingOpenRouterConnection = state.isTestingOpenRouterConnection,
                openRouterApiTestStatus = state.openRouterApiTestStatus,
                openRouterApiTestMessage = state.openRouterApiTestMessage,
                deepSeekModelIds = state.deepSeekModelIds,
                isDeepSeekModelsLoading = state.isDeepSeekModelsLoading,
                isTestingDeepSeekConnection = state.isTestingDeepSeekConnection,
                deepSeekApiTestStatus = state.deepSeekApiTestStatus,
                deepSeekApiTestMessage = state.deepSeekApiTestMessage,
                mistralModelIds = state.mistralModelIds,
                isMistralModelsLoading = state.isMistralModelsLoading,
                isTestingMistralConnection = state.isTestingMistralConnection,
                mistralApiTestStatus = state.mistralApiTestStatus,
                mistralApiTestMessage = state.mistralApiTestMessage,
                nvidiaModelIds = state.nvidiaModelIds,
                isNvidiaModelsLoading = state.isNvidiaModelsLoading,
                isTestingNvidiaConnection = state.isTestingNvidiaConnection,
                nvidiaApiTestStatus = state.nvidiaApiTestStatus,
                nvidiaApiTestMessage = state.nvidiaApiTestMessage,
                ollamaCloudModelIds = state.ollamaCloudModelIds,
                isOllamaCloudModelsLoading = state.isOllamaCloudModelsLoading,
                isTestingOllamaCloudConnection = state.isTestingOllamaCloudConnection,
                ollamaCloudApiTestStatus = state.ollamaCloudApiTestStatus,
                ollamaCloudApiTestMessage = state.ollamaCloudApiTestMessage,
            ),
        )
    }

    override fun providerHasConfiguredTranslationProvider(settings: NovelReaderSettings): Boolean =
        settings.hasConfiguredTranslationProvider()

    // ---------------------------------------------------------------------------------------------
    // NovelTranslationHost implementation: the translation controller reaches the shared reader
    // state here.
    // ---------------------------------------------------------------------------------------------

    override val translationScope: CoroutineScope get() = screenModelScope

    override fun translationCurrentChapter(): NovelChapter? = currentChapter

    override fun translationReaderSettings(): NovelReaderSettings? =
        (mutableState.value as? State.Success)?.readerSettings

    override fun translationActiveChapterId(): Long? = activeTranslationChapterId()

    override fun translationCurrentParsedTextBlocks(): List<String> = currentParsedTextBlocks()

    override fun translationHolderClear(provider: String) = translationHolder.clear(provider)

    override fun translationHolderPut(provider: String, map: Map<Int, String>) {
        // The in-memory maps belong to one chapter, and over a book that chapter is not necessarily
        // the one the reader was opened with. Remembering it here is what lets a neighbouring
        // resident chapter keep its own (cached) translation instead of being served this one.
        translationHolderChapterId = activeTranslationChapterId()
        translationHolder.put(provider, map)
    }

    override fun translationHolderIsEmpty(provider: String): Boolean = translationHolder.isEmpty(provider)

    override fun translationHolderMap(provider: String): Map<Int, String> = translationHolder.map(provider)

    override fun translationUpdateContent(settings: NovelReaderSettings) = updateContent(settings)

    override fun translationRefreshBookModeSection(chapterId: Long) = refreshBookModeSection(chapterId)

    override fun translationRefreshBookModeTranslationVariant() {
        bookController.refreshSectionsAfterTranslationVisibilityChange()
    }

    override fun translationIsGeminiTranslating(): Boolean = translationState.isGeminiTranslating

    override fun translationIsGeminiTranslationVisible(): Boolean = translationState.isGeminiTranslationVisible

    override fun translationIsBookRuntimeActive(): Boolean = bookController.isBookRuntimeActive()

    override fun translationHasConfiguredProvider(settings: NovelReaderSettings): Boolean =
        settings.hasConfiguredTranslationProvider()

    override fun translationApplyTranslationState(
        gemini: State.ReaderGeminiState,
        google: State.ReaderGoogleState,
    ) {
        val successState = mutableState.value as? State.Success ?: return
        mutableState.value = successState.copy(
            geminiTranslation = gemini,
            googleTranslation = google,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // NovelProgressPersistenceHost implementation: the progress persistence controller reaches the
    // shared reader state here.
    // ---------------------------------------------------------------------------------------------

    override val progressScope: CoroutineScope get() = screenModelScope

    override fun progressCurrentChapterId(): Long? = currentChapter?.id

    override fun progressCurrentNovel(): Novel? = currentNovel

    // ---------------------------------------------------------------------------------------------
    // NovelSelectionTranslationHost implementation: the selected-text translation + dictionary
    // controller reaches the shared reader state here.
    // ---------------------------------------------------------------------------------------------

    override val selectionScope: CoroutineScope get() = screenModelScope

    override fun selectionReaderSettings(): NovelReaderSettings? =
        (mutableState.value as? State.Success)?.readerSettings

    override fun selectionNovel(): Novel? = currentNovel

    override fun selectionChapter(): NovelChapter? = currentChapter

    override fun selectionSourceLanguage(): String? =
        currentNovel?.source?.let { sourceManager.get(it)?.lang }

    override fun selectionUpdateContent(settings: NovelReaderSettings) = updateContent(settings)

    /** Live highlights of one chapter, for renderer painting and the highlights panel. */
    fun subscribeChapterHighlights(chapterId: Long): Flow<List<NovelHighlight>> =
        novelHighlightRepository.subscribeForChapter(chapterId)

    fun getDefaultHighlightColor(): Long =
        novelReaderPreferences.novelHighlightLastColor().get()

    fun defaultHighlightColorChanges(): Flow<Long> =
        novelReaderPreferences.novelHighlightLastColor().changes()

    fun setDefaultHighlightColor(colorArgb: Long) {
        novelReaderPreferences.novelHighlightLastColor().set(colorArgb)
    }

    /**
     * Общий список хайлайтов новеллы для панели/цитат: join с именами и порядком глав,
     * сортировка по порядку чтения. Осиротевшие записи (глава удалена) скрываются.
     */
    fun subscribeNovelHighlightItems(): Flow<List<NovelHighlightWithChapter>> {
        val novel = currentNovel ?: return emptyFlow()
        return novelHighlightRepository.subscribeForNovel(novel.id).map { highlights ->
            if (highlights.isEmpty()) return@map emptyList()
            val chaptersById = novelChapterRepository.getChapterByNovelId(novel.id).associateBy { it.id }
            highlights.mapNotNull { highlight ->
                val chapter = chaptersById[highlight.chapterId] ?: return@mapNotNull null
                NovelHighlightWithChapter(highlight, novel.title, chapter.name, chapter.sourceOrder)
            }.sortedWith(
                compareBy({ it.chapterSourceOrder }, { it.highlight.blockIndex }, { it.highlight.charStart }),
            )
        }
    }

    fun updateHighlight(highlightId: Long, note: String, colorArgb: Long) {
        screenModelScope.launch {
            updateNovelHighlight.await(
                highlightId = highlightId,
                note = note,
                colorArgb = colorArgb,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    fun deleteHighlight(highlightId: Long) {
        screenModelScope.launch {
            deleteNovelHighlight.await(highlightId)
        }
    }

    override fun saveHighlight(selection: NovelSelectedTextSelection) {
        val anchor = selection.selectionAnchor ?: return
        val novel = currentNovel ?: return
        val now = System.currentTimeMillis()
        val colorArgb = novelReaderPreferences.novelHighlightLastColor().get()
        // Страница фиксируется на момент создания; в режимах без страниц остаётся 0/0.
        val pageProgress = (mutableState.value as? State.Success)?.lastSavedPageReaderProgress
        screenModelScope.launch {
            addNovelHighlight.await(
                NovelHighlight(
                    id = 0L,
                    novelId = novel.id,
                    chapterId = anchor.chapterId,
                    blockIndex = anchor.blockIndex,
                    charStart = anchor.charStart,
                    charEndExclusive = anchor.charEndExclusive,
                    normalizedText = normalizeNovelSelectedText(selection.text),
                    colorArgb = colorArgb,
                    note = "",
                    createdAt = now,
                    updatedAt = now,
                    pageIndex = pageProgress?.let { it.index + 1 } ?: 0,
                    pageCount = pageProgress?.totalItems ?: 0,
                ),
            )
            // «Чернильница»: пересчёт прогресса по факту БД (QuoteRule слушает это событие).
            eventBus?.tryEmit(
                AchievementEvent.FeatureUsed(AchievementEvent.Feature.QUOTE_SAVED),
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // NovelTranslationBatchHost implementation: the batch executor reaches the shared reader state
    // here for whole-chapter batches and next-chapter prefetch.
    // ---------------------------------------------------------------------------------------------

    override fun batchReaderSettings(): NovelReaderSettings? =
        (mutableState.value as? State.Success)?.readerSettings

    override fun batchCurrentNovel(): Novel? = currentNovel

    override fun batchCurrentChapter(): NovelChapter? = currentChapter

    override fun batchSourceManager(): NovelSourceManager = sourceManager

    override fun batchFindNextChapter(chapter: NovelChapter): NovelChapter? = findNextChapter(chapter)

    override fun batchCoroutineScope(): CoroutineScope = screenModelScope

    override fun batchCacheReadChapters(): Boolean = novelReaderPreferences.cacheReadChapters().get()

    override fun batchAddAiTranslationLog(message: String) = addAiTranslationLog(message)

    /**
     * TTS subsystem, extracted into its own controller. The screen model hosts the shared state
     * through [NovelTtsHost] and forwards the reader's `tts*` calls.
     */
    private val ttsController = NovelTtsController(
        host = this,
        application = application,
        novelReaderPreferences = novelReaderPreferences,
        ttsChapterRepository = ttsChapterRepository,
        sourceManager = sourceManager,
    )

    /** Snapshot of the TTS UI state, merged into the reader state by the screen model. */
    private val ttsUiState: NovelReaderTtsUiState
        get() = ttsController.snapshot()

    private var settingsJob: Job? = null
    private var contentModel: NovelReaderContentModel? = null
    private var currentNovel: Novel? = null
    private var currentChapter: NovelChapter? = null
    private var chapterOrderList: MutableList<NovelChapter> = mutableListOf()
    private var fullChapterOrderList: List<NovelChapter> = emptyList()
    private var customCss: String? = null
    private var customJs: String? = null
    private var pluginSite: String? = null
    private var chapterWebUrl: String? = null
    private var lastSavedProgress: Long? = null
    private var lastSavedRead: Boolean? = null
    private var initialProgressIndex: Int = 0
    private var hasProgressChanged: Boolean = false
    private var nextChapterPrefetchJob: Job? = null

    /**
     * Chapter currently owned by this screen model. It starts as the chapter the screen was opened
     * with, but a seamless in-place chapter switch moves it forward/backward without recreating the
     * screen.
     */
    private var currentSessionChapterId: Long = chapterId
    private var seamlessChapterSwitchJob: Job? = null
    private var seamlessChapterSwitchToken: Long = 0L
    private var hasTriggeredNextChapterPrefetch: Boolean = false
    private var adjacentJaomixPageJob: Job? = null
    private val attemptedJaomixPages = mutableSetOf<Int>()

    /**
     * Rendered translation holder: the content pipeline reads translated text straight from here.
     * The translation controller writes into it through [NovelTranslationHost].
     */
    private val translationHolder = NovelReaderTranslationHolder { currentParsedTextBlocks() }

    /** Chapter the in-memory translation maps were produced for, null when there are none. */
    private var translationHolderChapterId: Long? = null

    /**
     * Whole-chapter translation subsystem (Gemini/AI queue + Google). Owns the jobs, visibility and
     * cache flags and the per-provider logs.
     */
    private val translationController = NovelTranslationController(
        host = this,
        application = application,
        novelReaderPreferences = novelReaderPreferences,
        googleTranslationService = googleTranslationService,
        translationQueueManager = translationQueueManager,
    ).also { controller ->
        controller.setPendingAutoStart(autoStartGeminiTranslation)
    }

    /**
     * Dispatches whole-chapter translation batches to the configured provider service.
     */
    private val translationBatchExecutor = NovelTranslationBatchExecutor(
        host = this,
        geminiTranslationService = geminiTranslationService,
        openRouterTranslationService = openRouterTranslationService,
        deepSeekTranslationService = deepSeekTranslationService,
        mistralTranslationService = mistralTranslationService,
        nvidiaTranslationService = nvidiaTranslationService,
        ollamaCloudTranslationService = ollamaCloudTranslationService,
        replaceTextHtml = { html ->
            applyReplaceRulesToHtml(html, novelReaderPreferences.enabledReplaceRules())
        },
    )

    /** Snapshot of the translation UI state, merged into the reader state by the screen model. */
    private val translationState: NovelTranslationState
        get() = translationController.snapshot()

    private var seriesInterstitialState: SeriesInterstitialState? = null
    private var finaleShownForNovelId: Long? = null
    private var finaleState: ReaderFinaleState? = null
    private var seriesInterstitialShownForChapterId: Long? = null

    /**
     * AI translation providers subsystem (model lists, connection tests). Owns its state and
     * pushes it into the reader state through [NovelAiProviderHost].
     */
    private val aiProviderController = NovelAiProviderController(
        host = this,
        application = application,
        novelReaderPreferences = novelReaderPreferences,
        openRouterModelsService = openRouterModelsService,
        deepSeekModelsService = deepSeekModelsService,
        mistralModelsService = mistralModelsService,
        nvidiaModelsService = nvidiaModelsService,
        ollamaCloudModelsService = ollamaCloudModelsService,
    )

    /** Snapshot of the AI provider UI state, merged into the reader state by the screen model. */
    private val aiProvidersState: NovelAiProviderState
        get() = aiProviderController.snapshot()

    /**
     * Chapter progress + reading-history persistence subsystem. Owns the pending-progress queue and
     * the bounded flush pipeline; the screen model forwards the reader's persistence calls here.
     */
    private val progressPersistenceController = NovelProgressPersistenceController(
        host = this,
        novelChapterRepository = novelChapterRepository,
        getIncognitoState = getIncognitoState,
        eventBus = eventBus,
        activityDataRepository = activityDataRepository,
        historyRepository = historyRepository,
    )

    private val selectionTranslationController = NovelSelectionTranslationController(
        host = this,
        application = application,
        novelReaderPreferences = novelReaderPreferences,
        selectedTextTranslationProvider = selectedTextTranslationProvider,
        novelDictionaryProvider = novelDictionaryProvider,
    )

    /** Snapshot of the selection/dictionary UI state, merged into the reader state below. */
    private val selectionTranslationSnapshot: NovelSelectionTranslationSnapshot
        get() = selectionTranslationController.snapshot()
    private var incognitoObservationJob: Job? = null

    private val structuredJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    init {
        ttsController.attach()
        screenModelScope.launch {
            loadChapter()
        }
    }
    private suspend fun loadChapter(
        targetChapterId: Long = currentSessionChapterId,
        seamless: Boolean = false,
    ) {
        currentSessionChapterId = targetChapterId
        val snapshot = try {
            ttsChapterRepository.loadChapterSnapshot(targetChapterId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to load novel chapter snapshot" }
            return setError(e.message)
        }
        val chapter = snapshot.chapter
        val novel = snapshot.novel
        val source = sourceManager.getOrStub(novel.source)
        clearChapterTransientState()
        currentNovel = novel
        observeIncognitoForNovel(novel)
        currentChapter = chapter
        fullChapterOrderList = snapshot.chapterOrderList
        chapterOrderList = NovelReaderChapterWindow.resolveWindow(
            chapters = fullChapterOrderList,
            currentChapterId = chapter.id,
            windowRadius = 50,
        ).toMutableList()
        val normalizedChapterHtml = withContext(Dispatchers.Default) {
            val normalizedChapterHtml = prependChapterHeadingIfMissing(
                rawHtml = snapshot.rawHtml.normalizeStructuredChapterPayload(),
                chapterName = chapter.name,
            )
            val sanitizedChapterHtml = sanitizeChapterHtmlForReader(normalizedChapterHtml)
            if (sanitizedChapterHtml.isBlank()) {
                normalizedChapterHtml
            } else {
                applyReplaceRulesToHtml(
                    rawHtml = sanitizedChapterHtml,
                    rules = novelReaderPreferences.enabledReplaceRules(),
                )
            }
        }
        lastSavedProgress = chapter.lastPageRead
        lastSavedRead = chapter.read
        initialProgressIndex = snapshot.lastSavedIndex
        hasProgressChanged = false
        hasTriggeredNextChapterPrefetch = false
        translationBatchExecutor.resetNextChapterGeminiPrefetchTriggered()
        translationController.resetAutoStartFlags()
        customCss = snapshot.customCss
        customJs = snapshot.customJs
        pluginSite = snapshot.pluginSite
        chapterWebUrl = snapshot.chapterWebUrl
        contentModel = NovelReaderContentModel(
            canonicalHtml = normalizedChapterHtml,
            chapterWebUrl = chapterWebUrl,
            novelUrl = novel.url,
            pluginSite = pluginSite,
        )
        val initialSettings = novelReaderPreferences.resolveSettings(novel.source)
        // A seamless switch keeps the previous Success state on screen while the next chapter is
        // prepared, so the reader never flashes the full-screen chapter loading state.
        if (!seamless) {
            mutableState.value = State.Loading(initialSettings)
        }
        if (contentModel == null) return setError("Chapter content is empty")
        progressPersistenceController.resetSessionReadTimer()
        bookController.startForChapter(chapter)
        observeReadingModeChanges(chapter)
        translationController.restoreGeminiTranslationFromCache(
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
                    ttsController.initializeTtsRuntimePublic()
                    maybeAutoStartGeminiTranslation(settings)
                    maybeAutoStartGoogleTranslation()
                }
        }
        if (eu.kanade.domain.entries.novel.LocalNovelIntegrity.shouldRecordHistoryForChapterHtml(
                normalizedChapterHtml,
            )
        ) {
            progressPersistenceController.saveHistorySnapshot(chapter.id, sessionReadDurationMs = 0L)
        } else {
            logcat(LogPriority.DEBUG) {
                "Skip novel history for empty chapter content novelId=${novel.id} chapterId=${chapter.id}"
            }
        }
        updateContent(initialSettings)
        ttsController.initializeTtsRuntimePublic()
        ttsController.restoreTtsAfterChapterHandoff(
            chapterId = chapter.id,
            settings = initialSettings,
        )
        maybeAutoStartGeminiTranslation(initialSettings)
        maybeAutoStartGoogleTranslation()
        when (initialSettings.translationProvider) {
            NovelTranslationProvider.GEMINI -> Unit
            NovelTranslationProvider.GEMINI_PRIVATE -> Unit
            NovelTranslationProvider.OPENROUTER -> refreshOpenRouterModels()
            NovelTranslationProvider.DEEPSEEK -> refreshDeepSeekModels()
            NovelTranslationProvider.MISTRAL -> refreshMistralModels()
            NovelTranslationProvider.NVIDIA -> refreshNvidiaModels()
            NovelTranslationProvider.OLLAMA_CLOUD -> refreshOllamaCloudModels()
        }
    }
    fun loadFullChapterOrderList() {
        if (fullChapterOrderList.isNotEmpty()) {
            val successState = mutableState.value as? State.Success ?: return
            if (successState.fullChapterOrderList.isEmpty()) {
                mutableState.value = successState.copy(fullChapterOrderList = fullChapterOrderList)
            }
            return
        }
        screenModelScope.launch {
            val novel = currentNovel ?: return@launch
            fullChapterOrderList = loadChapterOrderList(novel.id)
            val successState = mutableState.value as? State.Success ?: return@launch
            mutableState.value = successState.copy(fullChapterOrderList = fullChapterOrderList)
            // Book mode builds its spine from the full chapter list. When the reader opened before
            // that list was available, the spine only covered the loaded window, so the resume
            // position mapped to the wrong section and everything above it was missing from the
            // document. Rebuild the spine once the real list arrives.
            val chapter = currentChapter
            if (chapter != null && fullChapterOrderList.isNotEmpty()) {
                bookController.rebuildSpineIfNeeded(chapter, fullChapterOrderList)
            }
        }
    }

    private fun setError(message: String?) {
        mutableState.value = State.Error(message)
    }

    /**
     * Switches to another chapter without leaving the reader screen: no screen replacement and no
     * loading state, so the live document stays visible until the next chapter is ready.
     *
     * Returns false when an in-place switch is not possible (book mode owns its own continuous
     * document, the reader is not ready yet, or a switch is already running), so callers can fall
     * back to the classic screen replacement.
     */
    fun openChapterInPlace(targetChapterId: Long): Boolean {
        val successState = mutableState.value as? State.Success ?: return false
        // Opt-in feature: without it the reader keeps the classic screen replacement behaviour.
        if (!novelReaderPreferences.seamlessChapterTransition().get()) return false
        if (successState.bookMode.isEnabled) return false
        if (targetChapterId == currentChapter?.id) return false
        if (seamlessChapterSwitchJob?.isActive == true) return false
        val hasTargetChapter = fullChapterOrderList.any { it.id == targetChapterId } ||
            chapterOrderList.any { it.id == targetChapterId }
        if (!hasTargetChapter) return false
        seamlessChapterSwitchJob = screenModelScope.launch {
            // Leave the current frame before touching reader state. A chapter switch is triggered
            // from gesture/tap callbacks that can run inside a composition or layout pass, and
            // swapping the reader content there crashes the Compose runtime while it is disposing
            // subcompositions ("Cannot start a writer when another writer is pending").
            kotlinx.coroutines.yield()
            persistCurrentChapterExitState()
            seamlessChapterSwitchToken += 1L
            loadChapter(targetChapterId = targetChapterId, seamless = true)
        }
        return true
    }

    private fun subscribeToQueueProgress(chapterId: Long) =
        translationController.subscribeToQueueProgress(
            chapterId = chapterId,
            // Over a book one session covers the whole novel, so the queue has to be watched for
            // every chapter of it, not just the chapter the reader was opened with.
            novelId = currentNovel?.id,
        )

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
    private fun maybeAutoStartGeminiTranslation(settings: NovelReaderSettings) =
        translationController.maybeAutoStartGeminiTranslation(settings)
    private fun findNextChapter(currentChapter: NovelChapter): NovelChapter? {
        val list = if (fullChapterOrderList.isNotEmpty()) fullChapterOrderList else chapterOrderList
        return list
            .indexOfFirst { it.id == currentChapter.id }
            .takeIf { it >= 0 }
            ?.let { list.getOrNull(it + 1) }
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

    fun clearFinale() = setFinaleState(null)

    private fun setFinaleState(value: ReaderFinaleState?) {
        finaleState = value
        val currentState = mutableState.value
        if (currentState is State.Success) {
            mutableState.value = currentState.copy(finaleState = value)
        }
    }

    /**
     * Chapter reader and book mode both call this when a completion was witnessed
     * (becameRead && every chapter read). Persists the keepsake date and shows the one-time
     * «THE END» plate — novels have no end-of-series transition page, so the completion
     * moment itself is the reveal point (documented divergence from manga).
     */
    override fun onNovelCompletedWitnessed(chapter: NovelChapter) {
        recordNovelCompletionIfNeeded(chapter)
        maybeShowNovelFinale()
    }

    private fun recordNovelCompletionIfNeeded(chapter: NovelChapter) {
        val novel = currentNovel ?: return
        if (!shouldRecordNovelCompletion(
                novel,
                fullChapterOrderList,
                finishedChapterIsLast = chapter.id == fullChapterOrderList.lastOrNull()?.id,
            )
        ) {
            return
        }
        val timestamp = System.currentTimeMillis()
        screenModelScope.launch {
            updateNovel.await(NovelUpdate(id = novel.id, completedAt = timestamp))
        }
    }

    private fun maybeShowNovelFinale() {
        val novel = currentNovel ?: return
        if (!mangaReaderPreferences.showFinaleCard().get()) return
        if (finaleShownForNovelId == novel.id) return
        if (finaleState != null) return
        if (novel.displayStatus != SManga.COMPLETED.toLong()) return
        finaleShownForNovelId = novel.id
        setFinaleState(
            ReaderFinaleState(
                title = novel.displayTitle,
                coverData = novel,
                chapterCount = fullChapterOrderList.size,
                daysOnShelf = daysOnShelf(novel.dateAdded),
                finishedOn = DateFormat.getDateInstance(DateFormat.SHORT).format(Date()),
                nightVeil = eu.kanade.domain.easteregg.aurora.AuroraNight.isVeilThin(),
            ),
        )
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
                ).sortedByNovelReadingOrder()
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

    fun getChapterOrderList(): List<NovelChapter> {
        return chapterOrderList
    }

    suspend fun downloadChapter(chapterId: Long) {
        val novel = currentNovel ?: return
        val allChapters = if (fullChapterOrderList.isNotEmpty()) fullChapterOrderList else chapterOrderList
        val chapter = allChapters.firstOrNull { it.id == chapterId }
            ?: withContext(Dispatchers.IO) { novelChapterRepository.getChapterById(chapterId) }
            ?: return
        withContext(Dispatchers.IO) {
            novelDownloadManager.downloadChapter(novel, chapter)
        }
    }

    private suspend fun loadChapterOrderList(novelId: Long): List<NovelChapter> {
        return withContext(Dispatchers.IO) {
            val chapters = novelChapterRepository.getChapterByNovelId(novelId, applyScanlatorFilter = true)
            chapters.sortedByNovelReadingOrder()
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
            fullChapterOrderList = loadChapterOrderList(novel.id)
            chapterOrderList = NovelReaderChapterWindow.resolveWindow(
                chapters = fullChapterOrderList,
                currentChapterId = chapter.id,
                windowRadius = 50,
            ).toMutableList()
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
        if (!settings.selectedTextTranslationEnabled && !settings.novelDictionaryEnabled) {
            selectionTranslationController.clearSelection(refreshUi = false)
        }
        val model = contentModel ?: return
        val html = model.canonicalHtml
        val novel = currentNovel ?: return
        val chapter = currentChapter ?: return
        val geminiVisibleInUi = settings.geminiEnabled && translationState.isGeminiTranslationVisible
        val geminiCacheAvailableInUi = settings.geminiEnabled && translationState.hasGeminiTranslationCache
        val googleVisibleInUi = settings.googleTranslationEnabled && translationState.isGoogleTranslationVisible
        val googleCacheAvailableInUi = settings.googleTranslationEnabled && translationState.hasGoogleTranslationCache
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
        val previousResult = NovelReaderChapterWindow.navigate(
            currentChapterId = chapter.id,
            allChapters = if (fullChapterOrderList.isNotEmpty()) fullChapterOrderList else chapterOrderList,
            direction = -1,
            windowRadius = 50,
        )
        val previousChapter = previousResult.newCurrentChapter.takeIf { it.id != chapter.id }

        val nextResult = NovelReaderChapterWindow.navigate(
            currentChapterId = chapter.id,
            allChapters = if (fullChapterOrderList.isNotEmpty()) fullChapterOrderList else chapterOrderList,
            direction = 1,
            windowRadius = 50,
        )
        val nextChapter = nextResult.newCurrentChapter.takeIf { it.id != chapter.id }

        val chapterNavigation = ChapterNavigation(
            previousChapterId = previousChapter?.id,
            previousChapterName = previousChapter?.name,
            nextChapterId = nextChapter?.id,
            nextChapterName = nextChapter?.name,
        )
        maybeEnsureJaomixAdjacentPage(
            chapter = chapter,
            previousChapterId = chapterNavigation.previousChapterId,
            nextChapterId = chapterNavigation.nextChapterId,
            settings = settings,
        )
        val pluginCss = customCss
        val pluginJs = customJs
        // Anchor every top-level block with data-an-b so persistent selection addresses exist in
        // every non-book renderer: native scroll reads them back as block anchors, and the WebView
        // engine mounts this exact HTML, so its selection bridge can report the same domId.
        // Book-mode sections arrive pre-annotated from their own pipeline.
        val baseContent = annotateNovelBlockAnchors(
            rawHtml = model.getNormalizedHtml(
                settings = settings,
                customCss = pluginCss,
                customJs = pluginJs,
            ),
            chapterId = chapter.id,
        )
        val baseContentBlocks = currentParsedContentBlocks()
        val baseTextBlocks = baseContentBlocks
            .filterIsInstance<ContentBlock.Text>()
            .map { it.text }
        val richContentResult = model.parsedRichContentResult
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
                .also { model.parsedRichContentResult = it }
        val displayContentBlocks = when {
            geminiVisibleInUi -> applyGeminiTranslationToContentBlocks(baseContentBlocks)
            googleVisibleInUi -> applyGoogleTranslationToContentBlocks(baseContentBlocks)
            else -> baseContentBlocks
        }
        if (googleVisibleInUi) {
            addGoogleLog(
                "Apply UI: baseBlocks=${baseContentBlocks.size}, " +
                    "textBlocks=${baseTextBlocks.size}, " +
                    "translatedSegments=${translationHolder.map("google").size}, " +
                    "visible=$googleVisibleInUi",
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
            geminiVisibleInUi && !translationHolder.isEmpty("gemini") -> normalizeHtml(
                rawHtml = NovelContentHtmlMapper.buildTranslatedRawHtmlForDisplay(
                    templateHtml = html,
                    fallbackBlocks = displayContentBlocks,
                    translatedByIndex = translationHolder.map("gemini"),
                ),
                settings = settings,
                customCss = pluginCss,
                customJs = pluginJs,
            )
            googleVisibleInUi && !translationHolder.isEmpty("google") -> normalizeHtml(
                rawHtml = NovelContentHtmlMapper.buildTranslatedRawHtmlForDisplay(
                    templateHtml = html,
                    fallbackBlocks = displayContentBlocks,
                    translatedByIndex = translationHolder.map("google"),
                ),
                settings = settings,
                customCss = pluginCss,
                customJs = pluginJs,
            )
            else -> baseContent
        }
        ttsController.setPreferredTranslatedText(settings)
        ttsController.syncActiveTtsSessionOptions(settings)
        ttsController.switchActiveTtsTextSource(settings)
        mutableState.value = State.Success(
            novel = novel,
            chapter = chapter,
            seamlessSwitchToken = seamlessChapterSwitchToken,
            html = displayContent,
            enableJs = !pluginJs.isNullOrBlank() ||
                settings.selectedTextTranslationEnabled ||
                settings.novelDictionaryEnabled ||
                settings.customJS.isNotBlank(),
            readerSettings = settings,
            contentBlocks = displayContentBlocks,
            richContentBlocks = displayRichBlocks,
            richContentUnsupportedFeaturesDetected = richContentResult.unsupportedFeaturesDetected,
            chapterOrderList = chapterOrderList,
            fullChapterOrderList = if (fullChapterOrderList.isNotEmpty()) fullChapterOrderList else emptyList(),
            progress = State.ReaderProgressState(
                lastSavedIndex = lastSavedIndex,
                lastSavedScrollOffsetPx = lastSavedScrollOffsetPx,
                lastSavedWebProgressPercent = lastSavedWebProgressPercent,
                lastSavedPageReaderProgress = decodedPageReaderProgress,
                lastSavedRawProgress = chapter.lastPageRead,
            ),
            previousChapterId = chapterNavigation.previousChapterId,
            previousChapterName = chapterNavigation.previousChapterName,
            nextChapterId = chapterNavigation.nextChapterId,
            nextChapterName = chapterNavigation.nextChapterName,
            seriesInterstitialState = seriesInterstitialState,
            finaleState = finaleState,
            chapterWebUrl = chapterWebUrl,
            selectedTextTranslationSelection = selectionTranslationSnapshot.selection,
            selectedTextTranslationUiState = selectionTranslationSnapshot.translationUiState,
            novelDictionaryUiState = selectionTranslationSnapshot.dictionaryUiState,
            novelDictionaryEnabled = novelReaderPreferences.novelDictionaryEnabled().get(),
            novelDictionaryTargetLanguage = novelReaderPreferences.novelDictionaryTargetLanguage().get(),
            geminiTranslation = State.ReaderGeminiState(
                isGeminiTranslating = translationState.isGeminiTranslating,
                geminiTranslationProgress = translationState.geminiTranslationProgress,
                isGeminiTranslationVisible = geminiVisibleInUi,
                hasGeminiTranslationCache = geminiCacheAvailableInUi,
                geminiLogs = translationState.geminiLogs,
                chapterProgress = translationState.chapterProgress,
            ),
            googleTranslation = State.ReaderGoogleState(
                isGoogleTranslating = translationState.isGoogleTranslating,
                googleTranslationProgress = translationState.googleTranslationProgress,
                isGoogleTranslationVisible = googleVisibleInUi,
                hasGoogleTranslationCache = googleCacheAvailableInUi,
                googleLogs = translationState.googleLogs,
                translationPhase = translationState.translationPhase,
            ),
            ttsUiState = ttsController.snapshot().copy(
                enabled = settings.ttsEnabled,
                selectedEnginePackage = settings.ttsEnginePackage,
                selectedVoiceId = settings.ttsVoiceId,
                selectedLocaleTag = settings.ttsLocaleTag,
                speechRate = settings.ttsSpeechRate,
                pitch = settings.ttsPitch,
            ),
            aiProviders = State.ReaderAiProvidersState(
                openRouterModelIds = aiProvidersState.openRouterModelIds,
                isOpenRouterModelsLoading = aiProvidersState.isOpenRouterModelsLoading,
                isTestingOpenRouterConnection = aiProvidersState.isTestingOpenRouterConnection,
                openRouterApiTestStatus = aiProvidersState.openRouterApiTestStatus,
                openRouterApiTestMessage = aiProvidersState.openRouterApiTestMessage,
                deepSeekModelIds = aiProvidersState.deepSeekModelIds,
                isDeepSeekModelsLoading = aiProvidersState.isDeepSeekModelsLoading,
                isTestingDeepSeekConnection = aiProvidersState.isTestingDeepSeekConnection,
                deepSeekApiTestStatus = aiProvidersState.deepSeekApiTestStatus,
                deepSeekApiTestMessage = aiProvidersState.deepSeekApiTestMessage,
                mistralModelIds = aiProvidersState.mistralModelIds,
                isMistralModelsLoading = aiProvidersState.isMistralModelsLoading,
                isTestingMistralConnection = aiProvidersState.isTestingMistralConnection,
                mistralApiTestStatus = aiProvidersState.mistralApiTestStatus,
                mistralApiTestMessage = aiProvidersState.mistralApiTestMessage,
                nvidiaModelIds = aiProvidersState.nvidiaModelIds,
                isNvidiaModelsLoading = aiProvidersState.isNvidiaModelsLoading,
                isTestingNvidiaConnection = aiProvidersState.isTestingNvidiaConnection,
                nvidiaApiTestStatus = aiProvidersState.nvidiaApiTestStatus,
                nvidiaApiTestMessage = aiProvidersState.nvidiaApiTestMessage,
                ollamaCloudModelIds = aiProvidersState.ollamaCloudModelIds,
                isOllamaCloudModelsLoading = aiProvidersState.isOllamaCloudModelsLoading,
                isTestingOllamaCloudConnection = aiProvidersState.isTestingOllamaCloudConnection,
                ollamaCloudApiTestStatus = aiProvidersState.ollamaCloudApiTestStatus,
                ollamaCloudApiTestMessage = aiProvidersState.ollamaCloudApiTestMessage,
            ),
            bookMode = bookController.bookModeUiState(),
        )
    }
    fun updateReadingProgress(
        currentIndex: Int,
        totalItems: Int,
        persistedProgress: Long? = null,
        isInitialPositionRestored: Boolean = false,
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
        val newProgress = resolveReaderProgressToPersist(
            shouldPersistRead = shouldPersistRead,
            currentIndex = currentIndex,
            resolvedPersistedProgress = resolvedPersistedProgress,
            previousProgress = lastSavedProgress,
            isInitialPositionRestored = isInitialPositionRestored,
        ) ?: return
        maybePrefetchNextChapterOnProgress(
            currentIndex = currentIndex,
            totalItems = totalItems,
        )
        translationBatchExecutor.maybePrefetchNextChapterGeminiTranslationOnProgress(
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
        val allReadNow = novelReaderNovelCompleted(
            fullChapterList = fullChapterOrderList,
            visibleWindow = chapterOrderList,
        )
        val shouldEmitNovelCompleted = becameRead && allReadNow
        // The keepsake date refreshes on every witnessed end-read of the final chapter
        // (including re-reads); the plate itself only shows on a fresh completion.
        if (shouldEmitNovelCompleted) {
            onNovelCompletedWitnessed(chapter)
        } else if (allReadNow && reachedReadThreshold) {
            recordNovelCompletionIfNeeded(chapter)
        }
        progressPersistenceController.enqueueProgressPersistence(
            PendingProgressPersistence(
                chapterId = chapter.id,
                novelId = chapter.novelId,
                chapterNumber = chapter.chapterNumber.toInt(),
                read = shouldPersistRead,
                lastPageRead = newProgress,
                emitReadEvent = becameRead,
                emitNovelCompleted = shouldEmitNovelCompleted,
                sessionReadDurationMs = progressPersistenceController.sessionReadDurationMs(),
            ),
        )
    }
    // ---------------------------------------------------------------------------------------------
    // TTS delegates. All TTS logic lives in [ttsController]; the screen model forwards the
    // reader's calls and hosts the shared state through [NovelTtsHost].
    // ---------------------------------------------------------------------------------------------

    fun toggleTtsPlayback(
        startRequest: eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackStartRequest =
            eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackStartRequest(),
    ) {
        // Handing the book over to TTS is a flush point: the reading position at the moment playback
        // takes over is written through instead of waiting out the debounce window.
        flushBookModeProgress()
        ttsController.toggleTtsPlayback(startRequest)
    }

    fun stopTtsPlayback() = ttsController.stopTtsPlayback()

    fun skipToNextTtsSegment() = ttsController.skipToNextTtsSegment()

    fun skipToPreviousTtsSegment() = ttsController.skipToPreviousTtsSegment()

    fun pauseTtsForManualNavigation(
        startRequest: eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackStartRequest,
    ) = ttsController.pauseTtsForManualNavigation(startRequest)

    fun setTtsEnginePackage(value: String) = ttsController.setTtsEnginePackage(value)

    fun setTtsVoiceId(value: String) = ttsController.setTtsVoiceId(value)

    fun setTtsLocaleTag(value: String) = ttsController.setTtsLocaleTag(value)

    fun setTtsSpeechRate(value: Float) = ttsController.setTtsSpeechRate(value)

    fun setTtsPitch(value: Float) = ttsController.setTtsPitch(value)

    fun previewTtsVoice(voiceId: String) = ttsController.previewTtsVoice(voiceId)

    fun stopTtsVoicePreview() = ttsController.stopTtsVoicePreview()

    fun disableTts() = ttsController.disableTts()

    fun createTtsPlaybackServiceRuntime(): NovelTtsPlaybackServiceRuntime =
        ttsController.createTtsPlaybackServiceRuntime()

    suspend fun awaitPendingProgressPersistence() =
        progressPersistenceController.awaitPendingProgressPersistence()

    suspend fun persistCurrentChapterExitState() =
        progressPersistenceController.persistCurrentChapterExitState()

    suspend fun awaitDisposalCleanup() {
        withTimeoutOrNull(2_000) {
            screenModelScope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach {
                it.join()
            }
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
        val chapterIndex = chapterOrderList.indexOfFirst { it.id == chapter.id }
        if (chapterIndex >= 0) {
            val existingChapter = chapterOrderList[chapterIndex]
            if (existingChapter.read != read || existingChapter.lastPageRead != progress) {
                chapterOrderList[chapterIndex] = existingChapter.copy(
                    read = read,
                    lastPageRead = progress,
                )
            }
        }
        // The novel-completed check runs against the full chapter list, so the in-memory read mark
        // has to reach it too (same as bookMarkChapterReadInMemory); the DB write arrives
        // asynchronously through the progress pipeline.
        val fullChapterIndex = fullChapterOrderList.indexOfFirst { it.id == chapter.id }
        if (fullChapterIndex >= 0 && fullChapterOrderList[fullChapterIndex].read != read) {
            fullChapterOrderList = fullChapterOrderList.toMutableList().also { list ->
                list[fullChapterIndex] = list[fullChapterIndex].copy(read = read)
            }
        }
        val currentState = mutableState.value
        if (currentState is State.Success) {
            val decodedNativeProgress = decodeNativeScrollProgress(progress)
            val decodedWebProgressPercent = decodeWebScrollProgressPercent(progress)
            val decodedPageReaderProgress = decodePageReaderProgress(progress)
            val lastSavedIndex = when {
                decodedNativeProgress != null -> decodedNativeProgress.index
                decodedPageReaderProgress != null -> currentState.lastSavedIndex
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
                progress = currentState.progress.copy(
                    lastSavedIndex = lastSavedIndex,
                    lastSavedScrollOffsetPx = lastSavedScrollOffsetPx,
                    lastSavedWebProgressPercent = lastSavedWebProgressPercent,
                    lastSavedPageReaderProgress = decodedPageReaderProgress,
                    lastSavedRawProgress = progress,
                ),
            )
        }
    }
    fun prepareAutoScrollHandoff(
        targetChapterId: Long,
        speed: Int,
    ) {
        val chapter = currentChapter ?: return
        NovelReaderAutoScrollHandoffPolicy.prepareHandoff(
            fromChapterId = chapter.id,
            targetChapterId = targetChapterId,
            speed = speed,
        )
    }

    fun consumeAutoScrollHandoffIfMatches(chapterId: Long): NovelAutoScrollHandoffState? {
        return NovelReaderAutoScrollHandoffPolicy.consumeIfMatches(chapterId)
    }

    fun cancelAutoScrollHandoff() {
        NovelReaderAutoScrollHandoffPolicy.cancel()
    }

    fun requestAutoScrollNextChapterPrefetch() {
        if (hasTriggeredNextChapterPrefetch) return
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

    fun toggleChapterBookmark() {
        // Over an artifact the reader never switches chapters, so `currentChapter` stays the entry
        // point of the session while the reader is already deep inside another chapter. Bookmarking
        // has to follow the character offset instead, or it would flag the wrong chapter.
        val scrolledChapter = bookController.bookModeChapterAtReadingPosition()
        if (scrolledChapter != null && scrolledChapter.id != currentChapter?.id) {
            val scrolledBookmarked = !scrolledChapter.bookmark
            val scrolledIndex = chapterOrderList.indexOfFirst { it.id == scrolledChapter.id }
            if (scrolledIndex >= 0) {
                chapterOrderList[scrolledIndex] = chapterOrderList[scrolledIndex].copy(bookmark = scrolledBookmarked)
            }
            screenModelScope.launch {
                novelChapterRepository.updateChapter(
                    NovelChapterUpdate(
                        id = scrolledChapter.id,
                        bookmark = scrolledBookmarked,
                    ),
                )
            }
            return
        }
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
        // Fire-and-forget cancellation — no blocking cancelAndJoin calls.
        // Each job is independently cancelled; cleanup happens in clearChapterTransientState().
        settingsJob?.cancel()
        nextChapterPrefetchJob?.cancel()
        translationBatchExecutor.cancelNextChapterGeminiPrefetchJob()
        adjacentJaomixPageJob?.cancel()
        selectionTranslationController.dispose()
        progressPersistenceController.dispose()
        // Flush the debounced book-mode position before the jobs die: leaving the reader mid-section
        // would otherwise lose up to one debounce window of reading progress, and chapters the reader
        // scrolled past would never be marked read.
        bookController.flushAndStop()
        ttsController.shutdown()
        incognitoObservationJob?.cancel()
        NovelReaderIncognitoState.set(false)
        clearChapterTransientState()
        super.onDispose()
    }

    private fun observeIncognitoForNovel(novel: Novel) {
        incognitoObservationJob?.cancel()
        incognitoObservationJob = screenModelScope.launch {
            getIncognitoState.subscribe(novel.source).collect { active ->
                NovelReaderIncognitoState.set(active)
            }
        }
    }

    private fun clearChapterTransientState() {
        incognitoObservationJob?.cancel()
        incognitoObservationJob = null
        NovelReaderIncognitoState.set(false)
        currentNovel = null
        currentChapter = null
        chapterOrderList = mutableListOf()
        contentModel = null
        customCss = null
        customJs = null
        pluginSite = null
        chapterWebUrl = null
        lastSavedProgress = null
        lastSavedRead = null
        initialProgressIndex = 0
        hasProgressChanged = false
        hasTriggeredNextChapterPrefetch = false
        adjacentJaomixPageJob?.cancel()
        adjacentJaomixPageJob = null
        nextChapterPrefetchJob?.cancel()
        nextChapterPrefetchJob = null
        translationBatchExecutor.clearChapterScopedPrefetchState()
        translationController.resetTransientState()
        selectionTranslationController.clearChapterScopedState()
        attemptedJaomixPages.clear()
        translationHolder.clear("gemini")
        translationHolder.clear("google")
        translationHolderChapterId = null
        aiProviderController.resetTransientState()
        ttsController.resetTransientState()
        seriesInterstitialState = null
        seriesInterstitialShownForChapterId = null
        progressPersistenceController.resetSessionReadTimer()
    }
    fun addAiTranslationLog(message: String) = translationController.addAiTranslationLog(message)

    fun clearGeminiLogs() = translationController.clearGeminiLogs()

    fun clearAllGeminiTranslationCache() = translationController.clearAllGeminiTranslationCache()

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
        aiProviderController.resetAllApiTestStates()
        when (value) {
            NovelTranslationProvider.GEMINI -> Unit
            NovelTranslationProvider.GEMINI_PRIVATE -> Unit
            NovelTranslationProvider.OPENROUTER -> refreshOpenRouterModels()
            NovelTranslationProvider.DEEPSEEK -> refreshDeepSeekModels()
            NovelTranslationProvider.MISTRAL -> refreshMistralModels()
            NovelTranslationProvider.NVIDIA -> refreshNvidiaModels()
            NovelTranslationProvider.OLLAMA_CLOUD -> refreshOllamaCloudModels()
        }
    }
    // ---------------------------------------------------------------------------------------------
    // AI provider delegates. Model lists, connection tests and provider settings live in
    // [aiProviderController]; the screen model forwards the reader's calls.
    // ---------------------------------------------------------------------------------------------

    fun setOpenRouterBaseUrl(value: String) = aiProviderController.setOpenRouterBaseUrl(value)

    fun setOpenRouterApiKey(value: String) = aiProviderController.setOpenRouterApiKey(value)

    fun setOpenRouterModel(value: String) = aiProviderController.setOpenRouterModel(value)

    fun setDeepSeekBaseUrl(value: String) = aiProviderController.setDeepSeekBaseUrl(value)

    fun setDeepSeekApiKey(value: String) = aiProviderController.setDeepSeekApiKey(value)

    fun setDeepSeekModel(value: String) = aiProviderController.setDeepSeekModel(value)

    fun setMistralBaseUrl(value: String) = aiProviderController.setMistralBaseUrl(value)

    fun setMistralApiKey(value: String) = aiProviderController.setMistralApiKey(value)

    fun setMistralModel(value: String) = aiProviderController.setMistralModel(value)

    fun setNvidiaBaseUrl(value: String) = aiProviderController.setNvidiaBaseUrl(value)

    fun setNvidiaApiKey(value: String) = aiProviderController.setNvidiaApiKey(value)

    fun setNvidiaModel(value: String) = aiProviderController.setNvidiaModel(value)

    fun setOllamaCloudBaseUrl(value: String) = aiProviderController.setOllamaCloudBaseUrl(value)

    fun setOllamaCloudApiKey(value: String) = aiProviderController.setOllamaCloudApiKey(value)

    fun setOllamaCloudModel(value: String) = aiProviderController.setOllamaCloudModel(value)

    fun refreshOpenRouterModels() = aiProviderController.refreshOpenRouterModels()

    fun refreshNvidiaModels() = aiProviderController.refreshNvidiaModels()

    fun testNvidiaConnection() = aiProviderController.testNvidiaConnection()

    fun refreshOllamaCloudModels() = aiProviderController.refreshOllamaCloudModels()

    fun testOllamaCloudConnection() = aiProviderController.testOllamaCloudConnection()

    fun testOpenRouterConnection() = aiProviderController.testOpenRouterConnection()

    fun refreshDeepSeekModels() = aiProviderController.refreshDeepSeekModels()

    fun testDeepSeekConnection() = aiProviderController.testDeepSeekConnection()

    fun refreshMistralModels() = aiProviderController.refreshMistralModels()

    fun testMistralConnection() = aiProviderController.testMistralConnection()

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

    /**
     * Writes a reader setting either into the source override (when this novel has one) or into the
     * global preference. Shared by the Gemini setters and the TTS controller.
     */
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

    // ---------------------------------------------------------------------------------------------
    // Selected-text translation + dictionary delegates. Selection state, jobs and caches live in
    // [selectionTranslationController]; the screen model forwards the reader's calls.
    // ---------------------------------------------------------------------------------------------

    fun updateSelectedTextSelection(selection: NovelSelectedTextSelection?) =
        selectionTranslationController.updateSelectedTextSelection(selection)

    fun translateSelectedText() = selectionTranslationController.translateSelectedText()

    fun retrySelectedTextTranslation() = selectionTranslationController.retrySelectedTextTranslation()

    fun dismissSelectedTextTranslation() = selectionTranslationController.dismissSelectedTextTranslation()

    fun resetSelectedTextTranslationForChapter() =
        selectionTranslationController.resetSelectedTextTranslationForChapter()

    fun lookupSelectedTextDefinition() = selectionTranslationController.lookupSelectedTextDefinition()

    fun retryNovelDictionary() = selectionTranslationController.retryNovelDictionary()

    fun dismissNovelDictionary() = selectionTranslationController.dismissNovelDictionary()

    fun resetNovelDictionaryForChapter() = selectionTranslationController.resetNovelDictionaryForChapter()

    fun playSelectedTextPronunciation(text: String) =
        selectionTranslationController.playSelectedTextPronunciation(text)
    // ---------------------------------------------------------------------------------------------
    // Whole-chapter translation delegates. Gemini/Google jobs, visibility and logs live in
    // [translationController]; the screen model forwards the reader's calls.
    // ---------------------------------------------------------------------------------------------

    fun startGeminiTranslation() = translationController.startGeminiTranslation()

    fun stopGeminiTranslation() = translationController.stopGeminiTranslation()

    fun toggleGeminiTranslationVisibility() = translationController.toggleGeminiTranslationVisibility()

    fun clearGeminiTranslation() = translationController.clearGeminiTranslation()

    fun startGoogleTranslation() = translationController.startGoogleTranslation()

    fun stopGoogleTranslation() = translationController.stopGoogleTranslation()

    fun resumeGoogleTranslation() = translationController.resumeGoogleTranslation()

    fun toggleGoogleTranslationVisibility() = translationController.toggleGoogleTranslationVisibility()

    fun clearGoogleTranslation() = translationController.clearGoogleTranslation()

    fun maybeAutoStartGoogleTranslation() = translationController.maybeAutoStartGoogleTranslation()

    private fun applyGoogleTranslationToContentBlocks(blocks: List<ContentBlock>): List<ContentBlock> {
        if (translationHolder.isEmpty("google")) return blocks
        var textIndex = 0
        var replacedCount = 0
        val updated = blocks.map { block ->
            when (block) {
                is ContentBlock.Image -> block
                is ContentBlock.Text -> {
                    val translated = translationHolder.map("google")[textIndex]
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

    private fun addGoogleLog(message: String) = translationController.addGoogleLogPublic(message)

    private fun updateGoogleProgressFromLog(message: String) =
        translationController.updateGoogleProgressFromLogPublic(message)

    private fun applyGeminiTranslationToContentBlocks(
        blocks: List<ContentBlock>,
        forceTranslation: Boolean = false,
    ): List<ContentBlock> {
        if ((!forceTranslation && !translationState.isGeminiTranslationVisible) ||
            translationHolder.isEmpty("gemini")
        ) {
            return blocks
        }
        var textIndex = 0
        return blocks.map { block ->
            when (block) {
                is ContentBlock.Image -> block
                is ContentBlock.Text -> {
                    val translated = translationHolder.map("gemini")[textIndex]
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
        if ((!forceTranslation && !translationState.isGeminiTranslationVisible) ||
            translationHolder.isEmpty("gemini")
        ) {
            return blocks
        }
        var textIndex = 0
        return blocks.map { block ->
            when (block) {
                is NovelRichContentBlock.BlockQuote -> {
                    val replacement = translationHolder.map("gemini")[textIndex]
                    textIndex += 1
                    if (replacement.isNullOrBlank()) {
                        block
                    } else {
                        block.copy(
                            segments = NovelContentHtmlMapper.projectTranslatedTextOntoRichSegments(
                                originalSegments = block.segments,
                                translatedText = replacement,
                            ),
                        )
                    }
                }
                is NovelRichContentBlock.Heading -> {
                    val replacement = translationHolder.map("gemini")[textIndex]
                    textIndex += 1
                    if (replacement.isNullOrBlank()) {
                        block
                    } else {
                        block.copy(
                            segments = NovelContentHtmlMapper.projectTranslatedTextOntoRichSegments(
                                originalSegments = block.segments,
                                translatedText = replacement,
                            ),
                        )
                    }
                }
                is NovelRichContentBlock.Image -> block
                is NovelRichContentBlock.HorizontalRule -> block
                is NovelRichContentBlock.Paragraph -> {
                    val replacement = translationHolder.map("gemini")[textIndex]
                    textIndex += 1
                    if (replacement.isNullOrBlank()) {
                        block
                    } else {
                        block.copy(
                            segments = NovelContentHtmlMapper.projectTranslatedTextOntoRichSegments(
                                originalSegments = block.segments,
                                translatedText = replacement,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun applyGoogleTranslationToRichContentBlocks(
        blocks: List<NovelRichContentBlock>,
        forceTranslation: Boolean = false,
    ): List<NovelRichContentBlock> {
        if ((!forceTranslation && !translationState.isGoogleTranslationVisible) ||
            translationHolder.isEmpty("google")
        ) {
            return blocks
        }
        var textIndex = 0
        var replacedCount = 0
        val updated = blocks.map { block ->
            when (block) {
                is NovelRichContentBlock.BlockQuote -> {
                    val replacement = translationHolder.map("google")[textIndex]
                    textIndex += 1
                    if (replacement.isNullOrBlank()) {
                        block
                    } else {
                        replacedCount += 1
                        block.copy(
                            segments = NovelContentHtmlMapper.projectTranslatedTextOntoRichSegments(
                                originalSegments = block.segments,
                                translatedText = replacement,
                            ),
                        )
                    }
                }
                is NovelRichContentBlock.Heading -> {
                    val replacement = translationHolder.map("google")[textIndex]
                    textIndex += 1
                    if (replacement.isNullOrBlank()) {
                        block
                    } else {
                        replacedCount += 1
                        block.copy(
                            segments = NovelContentHtmlMapper.projectTranslatedTextOntoRichSegments(
                                originalSegments = block.segments,
                                translatedText = replacement,
                            ),
                        )
                    }
                }
                is NovelRichContentBlock.Image -> block
                is NovelRichContentBlock.HorizontalRule -> block
                is NovelRichContentBlock.Paragraph -> {
                    val replacement = translationHolder.map("google")[textIndex]
                    textIndex += 1
                    if (replacement.isNullOrBlank()) {
                        block
                    } else {
                        replacedCount += 1
                        block.copy(
                            segments = NovelContentHtmlMapper.projectTranslatedTextOntoRichSegments(
                                originalSegments = block.segments,
                                translatedText = replacement,
                            ),
                        )
                    }
                }
            }
        }
        val richContentBlockCount = blocks.count {
            it is NovelRichContentBlock.BlockQuote ||
                it is NovelRichContentBlock.Heading ||
                it is NovelRichContentBlock.Paragraph
        }
        addGoogleLog(
            "Applied to rich content blocks with inline style projection: replaced=$replacedCount/$richContentBlockCount",
        )
        return updated
    }
    private fun currentParsedTextBlocks(): List<String> {
        return contentModel?.textBlocks ?: emptyList()
    }

    /**
     * Chapter the translator, its cache and the TTS text source have to work on.
     *
     * `currentChapter` is the chapter the reader was opened with. Over a book that chapter only says
     * where the session started: the text on screen belongs to whichever spine chapter the reading
     * position is in. Translating, caching and switching TTS text by `currentChapter` therefore hit
     * a chapter the user is not reading, which is why the translators looked like they did nothing.
     */
    private fun activeTranslationChapterId(): Long? =
        bookController.bookModeChapterAtReadingPosition()?.id ?: currentChapter?.id

    /**
     * Source text blocks for [chapterId].
     *
     * The content model only ever holds the chapter the reader loaded, so over a book the blocks of
     * any other spine chapter have to be parsed from its own payload.
     */
    override suspend fun translationSourceTextBlocks(chapterId: Long): List<String> {
        if (!bookController.isBookRuntimeActive() || chapterId == currentChapter?.id) return currentParsedTextBlocks()
        return runCatching {
            val snapshot = ttsChapterRepository.loadChapterSnapshot(chapterId)
            withContext(Dispatchers.Default) {
                NovelReaderContentModel(
                    canonicalHtml = prependChapterHeadingIfMissing(
                        rawHtml = snapshot.rawHtml.normalizeStructuredChapterPayload(),
                        chapterName = snapshot.chapter.name,
                    ),
                    chapterWebUrl = snapshot.chapterWebUrl,
                    novelUrl = snapshot.novel.url,
                ).textBlocks
            }
        }.getOrElse { emptyList() }
    }

    /**
     * Renders a book section translated when a translation for that chapter exists.
     *
     * The chapter reader shows translations by rebuilding the chapter HTML in [updateContent] and
     * handing it to the chapter WebView. Book mode never takes that path: its document is streamed
     * section by section and the chapter HTML is deliberately empty over a book, so a finished AI or
     * Google translation was computed, cached and then never displayed. The section pipeline applies
     * it here instead, which is the only place the book document is built.
     */
    private fun applyBookSectionTranslation(chapterId: Long, bodyHtml: String): String {
        val settings = (mutableState.value as? State.Success)?.readerSettings ?: return bodyHtml
        // Every chapter of the book is translated, not just the one at the reading position: the
        // in-memory maps are used for the chapter they were produced for, every other chapter falls
        // back to its own cache entry. Gating this on the active chapter made the neighbouring
        // resident chapters lose their translation as soon as the reader crossed a boundary.
        val inMemory = when {
            chapterId != translationHolderChapterId -> emptyMap()
            translationState.isGeminiTranslationVisible && !translationHolder.isEmpty("gemini") ->
                translationHolder.map("gemini")
            translationState.isGoogleTranslationVisible && !translationHolder.isEmpty("google") ->
                translationHolder.map("google")
            else -> emptyMap()
        }
        val translatedByIndex = inMemory.ifEmpty {
            val cached = NovelReaderTranslationDiskCacheStore.get(chapterId) ?: return bodyHtml
            val settingsMatch = NovelReaderTranslationCacheResolver.matches(
                cached = cached,
                requirements = settings.toTranslationCacheRequirements(),
            )
            if (!settingsMatch) return bodyHtml
            cached.translatedByIndex
        }
        if (translatedByIndex.isEmpty()) return bodyHtml
        return NovelContentHtmlMapper.buildTranslatedHtmlFromTemplate(bodyHtml, translatedByIndex) ?: bodyHtml
    }

    /**
     * Re-renders a book section whose translation changed.
     *
     * Sections are cached once they are rendered, so a translation that finishes after the section
     * was mounted would otherwise only appear after leaving and re-entering the book.
     */
    private fun refreshBookModeSection(chapterId: Long) {
        bookController.refreshSectionAfterTranslation(chapterId)
    }

    private fun currentParsedContentBlocks(): List<ContentBlock> {
        return contentModel?.contentBlocks ?: emptyList()
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
            val chapterOrderList: List<NovelChapter> = emptyList(),
            val fullChapterOrderList: List<NovelChapter> = emptyList(),
            val progress: ReaderProgressState = ReaderProgressState(),
            val previousChapterId: Long?,
            val previousChapterName: String? = null,
            val nextChapterId: Long?,
            val nextChapterName: String? = null,
            val seriesInterstitialState: SeriesInterstitialState? = null,
            val finaleState: ReaderFinaleState? = null,
            val chapterWebUrl: String?,
            val selectedTextTranslationSelection: NovelSelectedTextSelection? = null,
            val selectedTextTranslationUiState: NovelSelectedTextTranslationUiState =
                NovelSelectedTextTranslationUiState.Idle,
            val novelDictionaryUiState: NovelDictionaryUiState = NovelDictionaryUiState.Idle,
            val novelDictionaryEnabled: Boolean = false,
            val novelDictionaryTargetLanguage: String = "Russian",
            val geminiTranslation: ReaderGeminiState = ReaderGeminiState(),
            val googleTranslation: ReaderGoogleState = ReaderGoogleState(),
            val ttsUiState: NovelReaderTtsUiState = NovelReaderTtsUiState(),
            val aiProviders: ReaderAiProvidersState = ReaderAiProvidersState(),
            val bookMode: ReaderBookModeState = ReaderBookModeState(),
            /**
             * Incremented on every seamless in-place chapter switch. The reader UI uses it to swap
             * the document without hiding the live WebView.
             */
            val seamlessSwitchToken: Long = 0L,
        ) : State {
            val textBlocks: List<String>
                get() = contentBlocks
                    .asSequence()
                    .filterIsInstance<ContentBlock.Text>()
                    .map { it.text }
                    .toList()

            // Backward-compat getters — keep existing code working
            val lastSavedIndex: Int get() = progress.lastSavedIndex
            val lastSavedScrollOffsetPx: Int get() = progress.lastSavedScrollOffsetPx
            val lastSavedWebProgressPercent: Int get() = progress.lastSavedWebProgressPercent
            val lastSavedPageReaderProgress: PageReaderProgress? get() = progress.lastSavedPageReaderProgress
            val lastSavedRawProgress: Long get() = progress.lastSavedRawProgress

            val isGeminiTranslating: Boolean get() = geminiTranslation.isGeminiTranslating
            val geminiTranslationProgress: Int get() = geminiTranslation.geminiTranslationProgress
            val isGeminiTranslationVisible: Boolean get() = geminiTranslation.isGeminiTranslationVisible
            val hasGeminiTranslationCache: Boolean get() = geminiTranslation.hasGeminiTranslationCache
            val geminiLogs: List<String> get() = geminiTranslation.geminiLogs
            val chapterTranslationProgress: Map<Long, NovelBookChapterTranslationProgress>
                get() = geminiTranslation.chapterProgress

            /** Chapters of the book still being translated somewhere behind or ahead of the reader. */
            fun backgroundTranslatingChapterCount(activeChapterId: Long): Int =
                chapterTranslationProgress.backgroundTranslatingCount(activeChapterId)

            val isGoogleTranslating: Boolean get() = googleTranslation.isGoogleTranslating
            val googleTranslationProgress: Int get() = googleTranslation.googleTranslationProgress
            val isGoogleTranslationVisible: Boolean get() = googleTranslation.isGoogleTranslationVisible
            val hasGoogleTranslationCache: Boolean get() = googleTranslation.hasGoogleTranslationCache
            val googleLogs: List<String> get() = googleTranslation.googleLogs
            val translationPhase: TranslationPhase get() = googleTranslation.translationPhase

            val openRouterModelIds: List<String> get() = aiProviders.openRouterModelIds
            val isOpenRouterModelsLoading: Boolean get() = aiProviders.isOpenRouterModelsLoading
            val isTestingOpenRouterConnection: Boolean get() = aiProviders.isTestingOpenRouterConnection
            val openRouterApiTestStatus: ProviderApiTestStatus get() = aiProviders.openRouterApiTestStatus
            val openRouterApiTestMessage: String? get() = aiProviders.openRouterApiTestMessage
            val deepSeekModelIds: List<String> get() = aiProviders.deepSeekModelIds
            val isDeepSeekModelsLoading: Boolean get() = aiProviders.isDeepSeekModelsLoading
            val isTestingDeepSeekConnection: Boolean get() = aiProviders.isTestingDeepSeekConnection
            val deepSeekApiTestStatus: ProviderApiTestStatus get() = aiProviders.deepSeekApiTestStatus
            val deepSeekApiTestMessage: String? get() = aiProviders.deepSeekApiTestMessage
            val mistralModelIds: List<String> get() = aiProviders.mistralModelIds
            val isMistralModelsLoading: Boolean get() = aiProviders.isMistralModelsLoading
            val isTestingMistralConnection: Boolean get() = aiProviders.isTestingMistralConnection
            val mistralApiTestStatus: ProviderApiTestStatus get() = aiProviders.mistralApiTestStatus
            val mistralApiTestMessage: String? get() = aiProviders.mistralApiTestMessage
            val nvidiaModelIds: List<String> get() = aiProviders.nvidiaModelIds
            val isNvidiaModelsLoading: Boolean get() = aiProviders.isNvidiaModelsLoading
            val isTestingNvidiaConnection: Boolean get() = aiProviders.isTestingNvidiaConnection
            val nvidiaApiTestStatus: ProviderApiTestStatus get() = aiProviders.nvidiaApiTestStatus
            val nvidiaApiTestMessage: String? get() = aiProviders.nvidiaApiTestMessage
            val ollamaCloudModelIds: List<String> get() = aiProviders.ollamaCloudModelIds
            val isOllamaCloudModelsLoading: Boolean get() = aiProviders.isOllamaCloudModelsLoading
            val isTestingOllamaCloudConnection: Boolean get() = aiProviders.isTestingOllamaCloudConnection
            val ollamaCloudApiTestStatus: ProviderApiTestStatus get() = aiProviders.ollamaCloudApiTestStatus
            val ollamaCloudApiTestMessage: String? get() = aiProviders.ollamaCloudApiTestMessage
        }

        data class ReaderProgressState(
            val lastSavedIndex: Int = 0,
            val lastSavedScrollOffsetPx: Int = 0,
            val lastSavedWebProgressPercent: Int = 0,
            val lastSavedPageReaderProgress: PageReaderProgress? = null,
            /**
             * Raw persisted `chapter.lastPageRead` value. Its codec format (page reader / native
             * scroll / web percent / legacy) decides the scale, so consumers that restore a
             * position into a different renderer decode it instead of reusing derived fields.
             */
            val lastSavedRawProgress: Long = 0L,
        )

        /**
         * Book-mode (whole-novel continuous reading) UI state.
         *
         * Only primitives and index lists are exposed here: the spine, the section store and the
         * render coordinator stay inside the screen model. When [isEnabled] is false the reader
         * behaves exactly like the classic per-chapter reader.
         */
        data class ReaderBookModeState(
            val isEnabled: Boolean = false,
            val sectionCount: Int = 0,
            val currentSectionIndex: Int = 0,
            val currentSectionFraction: Float = 0f,
            val bookProgressFraction: Float = 0f,
            val renderedSectionIndices: List<Int> = emptyList(),
            val preparingSectionIndices: List<Int> = emptyList(),
            val failedSectionIndices: List<Int> = emptyList(),
            val showChapterHeadings: Boolean = true,
            val isPreparingWholeBook: Boolean = false,
            /**
             * The renderer is mounted but has not reached the saved position yet.
             *
             * A freshly loaded document paints its own start before the queued resume scroll lands,
             * so the reader flashed the first page of the book and then jumped. The UI covers those
             * frames instead of showing a position the reader never asked for.
             */
            val isRestoringPosition: Boolean = false,
            val preparedChapterCount: Int = 0,
            val totalChapterCount: Int = 0,
        ) {
            val isReady: Boolean get() = isEnabled && sectionCount > 0

            val isPreparing: Boolean get() = preparingSectionIndices.isNotEmpty()
        }

        data class ReaderGeminiState(
            val isGeminiTranslating: Boolean = false,
            val geminiTranslationProgress: Int = 0,
            val isGeminiTranslationVisible: Boolean = false,
            val hasGeminiTranslationCache: Boolean = false,
            val geminiLogs: List<String> = emptyList(),
            /** Queue progress per chapter of the open book, keyed by chapter id. */
            val chapterProgress: Map<Long, NovelBookChapterTranslationProgress> = emptyMap(),
        )

        data class ReaderGoogleState(
            val isGoogleTranslating: Boolean = false,
            val googleTranslationProgress: Int = 0,
            val isGoogleTranslationVisible: Boolean = false,
            val hasGoogleTranslationCache: Boolean = false,
            val googleLogs: List<String> = emptyList(),
            val translationPhase: TranslationPhase = TranslationPhase.IDLE,
        )

        data class ReaderAiProvidersState(
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
    }
    sealed interface ContentBlock {
        data class Text(val text: String) : ContentBlock
        data class Image(val url: String, val alt: String?) : ContentBlock
    }
    private data class ChapterNavigation(
        val previousChapterId: Long?,
        val previousChapterName: String?,
        val nextChapterId: Long?,
        val nextChapterName: String?,
    )
    companion object {
        private const val JAOMIX_PAGE_SOURCE_ORDER_STRIDE = 1_000L
        private const val PRIVATE_FALLBACK_CHUNK_SIZE = 40
        private const val PRIVATE_FALLBACK_CONCURRENCY = 1
        private const val TTS_BASE_MILLIS_PER_WORD = 360f
        private const val TTS_MIN_UTTERANCE_DURATION_MS = 700L
        private const val TTS_WORD_PROGRESS_UPDATE_INTERVAL_MS = 60L
        private const val TTS_PREVIEW_UTTERANCE_ID = "tts-preview"
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

internal val structuredJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal val STRUCTURED_NODE_TYPES = setOf(
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

internal fun extractTextBlocks(rawHtml: String): List<String> {
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

internal fun extractContentBlocks(
    rawHtml: String,
    chapterWebUrl: String?,
    novelUrl: String,
    pluginSite: String?,
): List<NovelReaderScreenModel.ContentBlock> {
    val document = Jsoup.parse(rawHtml)
    val blocks = mutableListOf<NovelReaderScreenModel.ContentBlock>()
    collectContentBlocks(
        node = document.body(),
        blocks = blocks,
        chapterWebUrl = chapterWebUrl,
        novelUrl = novelUrl,
        pluginSite = pluginSite,
    )
    return blocks
}

internal fun collectContentBlocks(
    node: Node,
    blocks: MutableList<NovelReaderScreenModel.ContentBlock>,
    chapterWebUrl: String?,
    novelUrl: String,
    pluginSite: String?,
) {
    when (node) {
        is TextNode -> {
            val text = node.text().sanitizeTextBlock()
            if (text.isNotBlank()) {
                blocks += NovelReaderScreenModel.ContentBlock.Text(text)
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
                tag == "img" || tag == "picture" || tag == "source" -> {
                    collectImageContentBlock(
                        node = node,
                        blocks = blocks,
                        chapterWebUrl = chapterWebUrl,
                        novelUrl = novelUrl,
                        pluginSite = pluginSite,
                    )
                }
                tag == "p" && node.selectFirst("img, picture, source") != null && node.text().isBlank() -> {
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
                    blocks += NovelReaderScreenModel.ContentBlock.Text(normalizedText)
                }
                node.selectFirst("p, li, blockquote, h1, h2, h3, h4, h5, h6, pre, img") == null -> {
                    val text = node.wholeText().sanitizeTextBlock()
                    if (text.isNotBlank()) {
                        blocks += NovelReaderScreenModel.ContentBlock.Text(text)
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

private fun collectImageContentBlock(
    node: Element,
    blocks: MutableList<NovelReaderScreenModel.ContentBlock>,
    chapterWebUrl: String?,
    novelUrl: String,
    pluginSite: String?,
) {
    val imageElement = when (node.tagName().lowercase()) {
        "picture" -> node.selectFirst("img") ?: node.selectFirst("source")
        else -> node
    } ?: return
    val rawUrl = parseReaderImageUrl(imageElement) ?: return
    val resolvedUrl = resolveContentResourceUrl(
        rawUrl = rawUrl,
        chapterWebUrl = chapterWebUrl,
        novelUrl = novelUrl,
        pluginSite = pluginSite,
    ) ?: return
    blocks += NovelReaderScreenModel.ContentBlock.Image(
        url = resolvedUrl,
        alt = imageElement.attr("alt")
            .ifBlank { node.attr("alt") }
            .sanitizeTextBlock()
            .ifBlank { null },
    )
}

private fun parseReaderImageUrl(element: Element): String? {
    val directUrl = element.attr("src")
        .ifBlank { element.attr("data-src") }
        .ifBlank { element.attr("data-original") }
        .ifBlank { element.attr("data-lazy-src") }
        .ifBlank { element.attr("data-url") }
        .trim()
    if (directUrl.isNotBlank()) return directUrl

    val srcSet = element.attr("srcset")
        .ifBlank { element.attr("data-srcset") }
        .trim()
    if (srcSet.isBlank()) return null
    return srcSet
        .split(',')
        .asSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        ?.substringBefore(' ')
        ?.trim()
}

internal fun parseStructuredFragmentToBlocks(
    rawPayload: String,
    chapterWebUrl: String?,
    novelUrl: String,
    pluginSite: String?,
): List<NovelReaderScreenModel.ContentBlock> {
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
            NovelReaderScreenModel.ContentBlock.Image(
                url = resolvedUrl,
                alt = candidate.attr("alt").sanitizeTextBlock().ifBlank { null },
            )
        } else {
            val candidateText = candidate.text().sanitizeTextBlock()
            if (candidateText.isBlank()) return@mapNotNull null
            val normalizedText = if (candidate.tagName().equals("li", ignoreCase = true)) {
                "• $candidateText"
            } else {
                candidateText
            }
            NovelReaderScreenModel.ContentBlock.Text(normalizedText)
        }
    }
}

internal fun looksLikeStructuredPayload(rawValue: String): Boolean {
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

internal fun extractJsonCandidate(rawPayload: String): String? {
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

internal fun normalizeJsonLikePayload(rawPayload: String): String? {
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

internal fun findStructuredNode(element: JsonElement): JsonElement? {
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

internal fun isStructuredNode(element: JsonObject): Boolean {
    val normalizedType = normalizeStructuredType(element["type"].asStringOrNull())
    if (normalizedType != null && normalizedType in STRUCTURED_NODE_TYPES) {
        return true
    }
    return (element["content"] is JsonArray) ||
        (element["content"] is JsonObject) ||
        (element["text"].asStringOrNull() != null) ||
        (element["attrs"] is JsonObject)
}

internal fun extractStructuredAttachmentUrls(root: JsonElement): Map<String, String> {
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

internal fun renderStructuredElementAsHtml(
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

internal fun renderStructuredNodeAsHtml(
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

internal fun renderStructuredImageNode(
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

internal fun applyStructuredMarks(
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

internal fun JsonElement?.asStringOrNull(): String? {
    return (this as? JsonPrimitive)?.contentOrNull
}

internal fun JsonElement?.asIntOrNull(): Int? {
    return (this as? JsonPrimitive)?.intOrNull
}

internal fun String.escapeHtml(): String {
    return replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}

internal fun String.escapeHtmlAttribute(): String {
    return escapeHtml()
}

internal fun renderStructuredPayloadFallback(rawPayload: String): String? {
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

internal fun looksLikeHtmlPayload(rawPayload: String): Boolean {
    val trimmed = rawPayload.trim()
    if (!trimmed.contains('<') || !trimmed.contains('>')) return false
    return Regex("(?is)<\\s*(html|body|div|main|article|section|p|ul|ol|li|h1|h2|h3|h4|h5|h6|span)\\b")
        .containsMatchIn(trimmed)
}

internal fun extractStructuredTextFallbackSegments(payload: String): List<String> {
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

internal fun extractStructuredImageFallbackUrls(payload: String): List<String> {
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

internal fun normalizeStructuredType(type: String?): String? {
    return type
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.lowercase()
        ?.replace("_", "")
        ?.replace("-", "")
}

internal fun String.sanitizeTextBlock(): String {
    return this
        .replace('\u00A0', ' ')
        .replace("\r", "")
        .trim()
}

internal fun String.normalizeStructuredChapterPayload(): String {
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

internal fun parseStructuredCandidate(
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

internal fun parseStructuredRoot(rawPayload: String): JsonElement? {
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

internal fun resolveContentResourceUrl(
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

internal fun resolveRichContentBlocks(
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

internal fun normalizeHtml(
    rawHtml: String,
    settings: NovelReaderSettings,
    customCss: String?,
    customJs: String?,
): String {
    val css = customCss?.takeIf { it.isNotBlank() }
    val js = customJs?.takeIf { it.isNotBlank() }
    val theme = settings.theme
    val isDarkTheme = when (theme) {
        NovelReaderTheme.SYSTEM -> uy.kohesive.injekt.Injekt.get<android.app.Application>().isNightMode()
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
        picture { display: block; text-align: center; }
        img {
          display: block;
          max-width: 100%;
          height: auto;
          margin-left: auto;
          margin-right: auto;
        }
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
            <style>
              $style
            </style>
            ${if (js != null) "<script>$js</script>" else ""}
          </head>
          <body>
            $rawHtml
          </body>
        </html>
    """.trimIndent()
}

internal fun injectIntoHtml(rawHtml: String, injection: String): String {
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
