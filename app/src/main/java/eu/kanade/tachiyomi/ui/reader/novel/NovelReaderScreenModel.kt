package eu.kanade.tachiyomi.ui.reader.novel

import android.app.Application
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.novel.model.toSNovel
import eu.kanade.domain.items.novelchapter.interactor.SyncNovelChaptersWithSource
import eu.kanade.domain.items.novelchapter.model.toSNovelChapter
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginStorage
import eu.kanade.tachiyomi.extension.novel.runtime.NovelJsSource
import eu.kanade.tachiyomi.extension.novel.runtime.resolveUrl
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.source.novel.NovelSiteSource
import eu.kanade.tachiyomi.source.novel.NovelWebUrlSource
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderOverride
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
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
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationDiskCacheStore
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelTranslationStylePresets
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.formatGeminiThrowableForLog
import eu.kanade.tachiyomi.util.system.isNightMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
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
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.TimeUnit

class NovelReaderScreenModel(
    private val chapterId: Long,
    private val novelChapterRepository: NovelChapterRepository = Injekt.get(),
    private val syncNovelChaptersWithSource: SyncNovelChaptersWithSource = Injekt.get(),
    private val getNovel: GetNovel = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val novelDownloadManager: NovelDownloadManager = NovelDownloadManager(),
    private val pluginStorage: NovelPluginStorage = Injekt.get(),
    private val historyRepository: NovelHistoryRepository? = null,
    private val novelReaderPreferences: NovelReaderPreferences = Injekt.get(),
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
            resolveSystemPrompt = DeepSeekPromptResolver(app)::resolveSystemPrompt,
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
) : StateScreenModel<NovelReaderScreenModel.State>(State.Loading) {

    private var settingsJob: Job? = null
    private var rawHtml: String? = null
    private var currentNovel: Novel? = null
    private var currentChapter: NovelChapter? = null
    private var chapterOrderList: List<NovelChapter> = emptyList()
    private var customCss: String? = null
    private var customJs: String? = null
    private var pluginSite: String? = null
    private var chapterWebUrl: String? = null
    private var parsedContentBlocks: List<ContentBlock>? = null
    private var parsedTextBlocks: List<String>? = null
    private var parsedRichContentResult: NovelRichContentParseResult? = null
    private var lastSavedProgress: Long? = null
    private var lastSavedRead: Boolean? = null
    private var initialProgressIndex: Int = 0
    private var hasProgressChanged: Boolean = false
    private var chapterReadStartTimeMs: Long = System.currentTimeMillis()
    private var nextChapterPrefetchJob: Job? = null
    private var hasTriggeredNextChapterPrefetch: Boolean = false
    private var nextChapterGeminiPrefetchJob: Job? = null
    private var adjacentJaomixPageJob: Job? = null
    private val attemptedJaomixPages = mutableSetOf<Int>()
    private var hasTriggeredNextChapterGeminiPrefetch: Boolean = false
    private var hasTriggeredGeminiAutoStart: Boolean = false
    private var geminiTranslationJob: Job? = null
    private var geminiTranslatedByIndex: Map<Int, String> = emptyMap()
    private var isGeminiTranslating: Boolean = false
    private var geminiTranslationProgress: Int = 0
    private var isGeminiTranslationVisible: Boolean = false
    private var hasGeminiTranslationCache: Boolean = false
    private var geminiLogs: List<String> = emptyList()
    private var airforceModelIds: List<String> = emptyList()
    private var isAirforceModelsLoading: Boolean = false
    private var isTestingAirforceConnection: Boolean = false
    private var openRouterModelIds: List<String> = emptyList()
    private var isOpenRouterModelsLoading: Boolean = false
    private var isTestingOpenRouterConnection: Boolean = false
    private var deepSeekModelIds: List<String> = emptyList()
    private var isDeepSeekModelsLoading: Boolean = false
    private var isTestingDeepSeekConnection: Boolean = false
    private val structuredJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val resolvedHistoryRepository by lazy {
        historyRepository ?: runCatching { Injekt.get<NovelHistoryRepository>() }.getOrNull()
    }

    init {
        screenModelScope.launch {
            loadChapter()
        }
    }

    private suspend fun loadChapter() {
        val chapter = withContext(Dispatchers.IO) {
            novelChapterRepository.getChapterById(chapterId)
        }
            ?: return setError("Chapter not found")
        val novel = withContext(Dispatchers.IO) {
            getNovel.await(chapter.novelId)
        }
            ?: return setError("Novel not found")
        val source = sourceManager.get(novel.source)
            ?: return setError("Source not found")
        attemptedJaomixPages.clear()
        adjacentJaomixPageJob?.cancel()
        adjacentJaomixPageJob = null
        chapterOrderList = loadChapterOrderList(novel.id)

        val html = try {
            val cacheReadChapters = novelReaderPreferences.cacheReadChapters().get()
            withContext(Dispatchers.IO) {
                novelDownloadManager.getDownloadedChapterText(novel, chapter.id)
                    ?: cacheReadChapters.takeIf { it }?.let { NovelReaderChapterDiskCacheStore.get(chapter.id) }
                    ?: NovelReaderChapterPrefetchCache.get(chapter.id)
                        ?.also { prefetchedHtml ->
                            if (cacheReadChapters) {
                                NovelReaderChapterDiskCacheStore.put(chapter.id, prefetchedHtml)
                            }
                        }
                    ?: source.getChapterText(chapter.toSNovelChapter())
                        .also { fetchedHtml ->
                            if (cacheReadChapters) {
                                NovelReaderChapterDiskCacheStore.put(chapter.id, fetchedHtml)
                            }
                        }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to load novel chapter text" }
            return setError(e.message)
        }

        val pluginPackage = withContext(Dispatchers.IO) {
            pluginStorage.getAll()
                .firstOrNull { it.entry.id.hashCode().toLong() == novel.source }
        }
        val sourceSiteUrl = (source as? NovelSiteSource)?.siteUrl
        rawHtml = withContext(Dispatchers.Default) {
            val normalizedChapterHtml = prependChapterHeadingIfMissing(
                rawHtml = html.normalizeStructuredChapterPayload(),
                chapterName = chapter.name,
            )
            val sanitizedChapterHtml = sanitizeChapterHtmlForReader(normalizedChapterHtml)
            if (sanitizedChapterHtml.isBlank()) normalizedChapterHtml else sanitizedChapterHtml
        }
        currentNovel = novel
        currentChapter = chapter
        parsedContentBlocks = null
        parsedTextBlocks = null
        parsedRichContentResult = null
        geminiTranslationJob?.cancel()
        geminiTranslationJob = null
        geminiTranslatedByIndex = emptyMap()
        isGeminiTranslating = false
        geminiTranslationProgress = 0
        isGeminiTranslationVisible = false
        hasGeminiTranslationCache = false
        geminiLogs = emptyList()
        isAirforceModelsLoading = false
        isTestingAirforceConnection = false
        isOpenRouterModelsLoading = false
        isTestingOpenRouterConnection = false
        isDeepSeekModelsLoading = false
        isTestingDeepSeekConnection = false
        lastSavedProgress = chapter.lastPageRead
        lastSavedRead = chapter.read
        val savedNativeProgress = decodeNativeScrollProgress(chapter.lastPageRead)
        val savedWebProgress = decodeWebScrollProgressPercent(chapter.lastPageRead)
        initialProgressIndex = savedNativeProgress?.index
            ?: savedWebProgress
            ?: chapter.lastPageRead.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        hasProgressChanged = false
        hasTriggeredNextChapterPrefetch = false
        hasTriggeredNextChapterGeminiPrefetch = false
        hasTriggeredGeminiAutoStart = false
        customCss = pluginPackage?.customCss?.toString(Charsets.UTF_8)
        customJs = pluginPackage?.customJs?.toString(Charsets.UTF_8)
        pluginSite = pluginPackage?.entry?.site ?: sourceSiteUrl
        chapterWebUrl = withContext(Dispatchers.IO) {
            resolveChapterWebUrl(
                source = source,
                chapterUrl = chapter.url,
                novelUrl = novel.url,
                pluginSite = pluginSite,
            )
        }
        parseAndCacheContentBlocks(
            rawHtml = rawHtml ?: return setError("Chapter content is empty"),
            chapterWebUrl = chapterWebUrl,
            novelUrl = novel.url,
            pluginSite = pluginSite,
        )
        chapterReadStartTimeMs = System.currentTimeMillis()
        val initialSettings = novelReaderPreferences.resolveSettings(novel.source)
        restoreGeminiTranslationFromCache(
            chapterId = chapter.id,
            settings = initialSettings,
        )

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
                    maybeAutoStartGeminiTranslation(settings)
                }
        }
        saveHistorySnapshot(chapter.id, sessionReadDurationMs = 0L)
        updateContent(initialSettings)
        maybeAutoStartGeminiTranslation(initialSettings)
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

    private fun scheduleNextChapterPrefetch(
        novel: Novel,
        currentChapter: NovelChapter,
        source: eu.kanade.tachiyomi.novelsource.NovelSource,
    ) {
        val nextChapter = findNextChapter(currentChapter) ?: return

        if (NovelReaderChapterPrefetchCache.contains(nextChapter.id)) {
            return
        }

        nextChapterPrefetchJob?.cancel()
        nextChapterPrefetchJob = screenModelScope.launch(Dispatchers.IO) {
            runCatching {
                val cacheReadChapters = novelReaderPreferences.cacheReadChapters().get()
                if (novelDownloadManager.getDownloadedChapterText(novel, nextChapter.id) != null) return@runCatching
                if (cacheReadChapters && NovelReaderChapterDiskCacheStore.contains(nextChapter.id)) return@runCatching
                if (NovelReaderChapterPrefetchCache.contains(nextChapter.id)) return@runCatching

                val nextHtml = source.getChapterText(nextChapter.toSNovelChapter())
                NovelReaderChapterPrefetchCache.put(nextChapter.id, nextHtml)
                if (cacheReadChapters) {
                    NovelReaderChapterDiskCacheStore.put(nextChapter.id, nextHtml)
                }
            }.onFailure { error ->
                logcat(LogPriority.WARN, error) { "Failed to prefetch next novel chapter" }
            }
        }
    }

    private fun maybeAutoStartGeminiTranslation(settings: NovelReaderSettings) {
        if (hasTriggeredGeminiAutoStart) return
        if (!settings.geminiEnabled || !settings.geminiAutoTranslateEnglishSource) return
        if (!isGeminiSourceLanguageEnglish(settings.geminiSourceLang)) return
        if (!settings.hasConfiguredTranslationProvider()) return
        if (parsedTextBlocks.orEmpty().isEmpty()) return
        if (isGeminiTranslating || hasGeminiTranslationCache || geminiTranslatedByIndex.isNotEmpty()) return

        hasTriggeredGeminiAutoStart = true
        addGeminiLog("?? Auto-start translation for English source")
        startGeminiTranslation()
    }

    private fun findNextChapter(currentChapter: NovelChapter): NovelChapter? {
        return chapterOrderList
            .indexOfFirst { it.id == currentChapter.id }
            .takeIf { it >= 0 }
            ?.let { chapterOrderList.getOrNull(it + 1) }
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
        val html = rawHtml ?: return
        val novel = currentNovel ?: return
        val chapter = currentChapter ?: return
        if (!settings.geminiEnabled && isGeminiTranslating) {
            geminiTranslationJob?.cancel()
            geminiTranslationJob = null
            isGeminiTranslating = false
            isGeminiTranslationVisible = false
            geminiTranslationProgress = 0
            addGeminiLog("? Gemini ���������� �������� � ����������")
        }
        val geminiVisibleInUi = settings.geminiEnabled && isGeminiTranslationVisible
        val geminiCacheAvailableInUi = settings.geminiEnabled && hasGeminiTranslationCache
        val decodedNativeProgress = decodeNativeScrollProgress(chapter.lastPageRead)
        val decodedWebProgressPercent = decodeWebScrollProgressPercent(chapter.lastPageRead)
        val lastSavedIndex = when {
            decodedNativeProgress != null -> decodedNativeProgress.index
            decodedWebProgressPercent != null -> 0
            else -> chapter.lastPageRead.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        val lastSavedScrollOffsetPx = decodedNativeProgress?.offsetPx ?: 0
        val lastSavedWebProgressPercent = when {
            decodedWebProgressPercent != null -> decodedWebProgressPercent
            decodedNativeProgress != null -> 0
            else -> chapter.lastPageRead.coerceIn(0L, 100L).toInt()
        }
        val chapterNavigation = chapterOrderList.let { chapters ->
            val index = chapters.indexOfFirst { it.id == chapter.id }
            val previousChapterId = chapters.getOrNull(index - 1)?.id
            val nextChapterId = chapters.getOrNull(index + 1)?.id
            previousChapterId to nextChapterId
        }
        maybeEnsureJaomixAdjacentPage(
            chapter = chapter,
            previousChapterId = chapterNavigation.first,
            nextChapterId = chapterNavigation.second,
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
        val baseContentBlocks = parsedContentBlocks
            ?: extractContentBlocks(
                rawHtml = html,
                chapterWebUrl = chapterWebUrl,
                novelUrl = novel.url,
                pluginSite = pluginSite,
            ).ifEmpty {
                extractTextBlocks(html).map(ContentBlock::Text)
            }.also {
                parsedContentBlocks = it
            }
        val baseTextBlocks = parsedTextBlocks
            ?: baseContentBlocks
                .filterIsInstance<ContentBlock.Text>()
                .map { it.text }
                .also { parsedTextBlocks = it }
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

        val displayContentBlocks = if (geminiVisibleInUi) {
            applyGeminiTranslationToContentBlocks(baseContentBlocks)
        } else {
            baseContentBlocks
        }
        val displayTextBlocks = displayContentBlocks
            .filterIsInstance<ContentBlock.Text>()
            .map { it.text }
        val displayRichBlocks = if (geminiVisibleInUi) {
            applyGeminiTranslationToRichContentBlocks(richContentResult.blocks)
        } else {
            richContentResult.blocks
        }
        val displayContent = if (geminiVisibleInUi && geminiTranslatedByIndex.isNotEmpty()) {
            normalizeHtml(
                rawHtml = buildRawHtmlFromContentBlocks(displayContentBlocks),
                settings = settings,
                customCss = pluginCss,
                customJs = pluginJs,
            )
        } else {
            baseContent
        }

        mutableState.value = State.Success(
            novel = novel,
            chapter = chapter,
            html = displayContent,
            enableJs = !pluginJs.isNullOrBlank(),
            readerSettings = settings,
            contentBlocks = displayContentBlocks,
            textBlocks = displayTextBlocks,
            richContentBlocks = displayRichBlocks,
            richContentUnsupportedFeaturesDetected = richContentResult.unsupportedFeaturesDetected,
            lastSavedIndex = lastSavedIndex,
            lastSavedScrollOffsetPx = lastSavedScrollOffsetPx,
            lastSavedWebProgressPercent = lastSavedWebProgressPercent,
            previousChapterId = chapterNavigation.first,
            nextChapterId = chapterNavigation.second,
            chapterWebUrl = chapterWebUrl,
            isGeminiTranslating = isGeminiTranslating,
            geminiTranslationProgress = geminiTranslationProgress,
            isGeminiTranslationVisible = geminiVisibleInUi,
            hasGeminiTranslationCache = geminiCacheAvailableInUi,
            geminiLogs = geminiLogs,
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
        parsedTextBlocks = blocks
            .filterIsInstance<ContentBlock.Text>()
            .map { it.text }
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
            if (isSameInitialIndex && isSamePersistedProgress) return
            hasProgressChanged = true
        }

        val readThreshold = when {
            totalItems == 100 -> 0.99f
            else -> 0.95f
        }
        val reachedReadThreshold = totalItems > 1 &&
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
        val shouldEmitNovelCompleted = becameRead && chapterOrderList.all { it.read }

        screenModelScope.launch(NonCancellable) {
            novelChapterRepository.updateChapter(
                NovelChapterUpdate(
                    id = chapter.id,
                    read = shouldPersistRead,
                    lastPageRead = newProgress,
                ),
            )
            if (becameRead) {
                eventBus?.tryEmit(
                    AchievementEvent.NovelChapterRead(
                        novelId = chapter.novelId,
                        chapterNumber = chapter.chapterNumber.toInt(),
                    ),
                )
                if (shouldEmitNovelCompleted) {
                    eventBus?.tryEmit(AchievementEvent.NovelCompleted(chapter.novelId))
                }
            }
            val now = System.currentTimeMillis()
            saveHistorySnapshot(chapter.id, now - chapterReadStartTimeMs)
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
        val updatedChapter = chapter.copy(
            read = read,
            lastPageRead = progress,
        )
        currentChapter = updatedChapter
        chapterOrderList = chapterOrderList.map { existing ->
            if (existing.id == chapter.id) {
                existing.copy(
                    read = read,
                    lastPageRead = progress,
                )
            } else {
                existing
            }
        }
        val currentState = mutableState.value
        if (currentState is State.Success) {
            val decodedNativeProgress = decodeNativeScrollProgress(progress)
            val decodedWebProgressPercent = decodeWebScrollProgressPercent(progress)
            val lastSavedIndex = when {
                decodedNativeProgress != null -> decodedNativeProgress.index
                decodedWebProgressPercent != null -> currentState.lastSavedIndex
                else -> progress.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
            val lastSavedScrollOffsetPx = decodedNativeProgress?.offsetPx ?: 0
            val lastSavedWebProgressPercent = when {
                decodedWebProgressPercent != null -> decodedWebProgressPercent
                decodedNativeProgress != null -> currentState.lastSavedWebProgressPercent
                else -> progress.coerceIn(0L, 100L).toInt()
            }
            mutableState.value = currentState.copy(
                chapter = updatedChapter,
                lastSavedIndex = lastSavedIndex,
                lastSavedScrollOffsetPx = lastSavedScrollOffsetPx,
                lastSavedWebProgressPercent = lastSavedWebProgressPercent,
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
        settingsJob?.cancel()
        nextChapterPrefetchJob?.cancel()
        nextChapterGeminiPrefetchJob?.cancel()
        geminiTranslationJob?.cancel()
        super.onDispose()
    }

    fun addGeminiLog(message: String) {
        val text = message.trim()
        if (text.isBlank()) return
        geminiLogs = (listOf(text) + geminiLogs).take(100)
        val settings = (mutableState.value as? State.Success)?.readerSettings ?: return
        updateContent(settings)
    }

    fun clearGeminiLogs() {
        geminiLogs = emptyList()
        val settings = (mutableState.value as? State.Success)?.readerSettings ?: return
        updateContent(settings)
    }

    fun clearAllGeminiTranslationCache() {
        NovelReaderTranslationDiskCacheStore.clear()
        addGeminiLog("??? Clear ALL cache")
        val chapter = currentChapter ?: return
        if (NovelReaderTranslationDiskCacheStore.get(chapter.id) == null) {
            hasGeminiTranslationCache = false
            val settings = (mutableState.value as? State.Success)?.readerSettings ?: return
            updateContent(settings)
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

    fun startGeminiTranslation() {
        if (isGeminiTranslating) return
        val currentState = mutableState.value as? State.Success ?: return
        val chapter = currentChapter ?: return
        val baseTextBlocks = parsedTextBlocks.orEmpty()
        if (baseTextBlocks.isEmpty()) return

        val settings = currentState.readerSettings
        if (!settings.geminiEnabled) {
            addGeminiLog("? Gemini ���������� ��������")
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
        addGeminiLog("?? ${settings.translationProvider} start. Model: ${settings.translationCacheModelId()}")
        addGeminiLog("?? ${settings.translationRequestConfigLog()}")
        updateContent(settings)

        geminiTranslationJob?.cancel()
        geminiTranslationJob = screenModelScope.launch {
            val translated = mutableMapOf<Int, String>()
            val indexedBlocks = baseTextBlocks.mapIndexed { index, text -> index to text }
            val updateMutex = Mutex()

            suspend fun runChunkedTranslation(
                chunks: List<List<Pair<Int, String>>>,
                chunkSize: Int,
                concurrencyLimit: Int,
                strictOnNull: Boolean,
            ) {
                val semaphore = Semaphore(concurrencyLimit)
                var completedChunks = 0
                addGeminiLog(
                    "?? Split into ${chunks.size} chunks. Batch: $chunkSize, Concurrency: $concurrencyLimit",
                )

                coroutineScope {
                    chunks.mapIndexed { chunkIndex, chunk ->
                        async {
                            semaphore.withPermit {
                                addGeminiLog("?? Requesting chunk ${chunkIndex + 1}/${chunks.size}")
                                val result = requestTranslationBatch(
                                    segments = chunk.map { it.second },
                                    settings = settings,
                                    onLog = { message -> addGeminiLog(message) },
                                )
                                if (result == null && strictOnNull) {
                                    throw IllegalStateException(
                                        "${settings.translationProvider} returned empty response for chunk ${chunkIndex + 1}",
                                    )
                                }

                                updateMutex.withLock {
                                    if (result != null) {
                                        var successCount = 0
                                        result.forEachIndexed { localIndex, text ->
                                            val originalIndex =
                                                chunk.getOrNull(localIndex)?.first ?: return@forEachIndexed
                                            if (!text.isNullOrBlank()) {
                                                translated[originalIndex] = text
                                                successCount += 1
                                            }
                                        }
                                        addGeminiLog("? Chunk ${chunkIndex + 1} applied ($successCount/${chunk.size})")
                                    } else {
                                        addGeminiLog("?? Chunk ${chunkIndex + 1} returned empty result")
                                    }

                                    completedChunks += 1
                                    geminiTranslationProgress =
                                        (((completedChunks).toFloat() / chunks.size.toFloat()) * 100f)
                                            .toInt()
                                            .coerceIn(0, 100)
                                    if (translated.isNotEmpty()) {
                                        geminiTranslatedByIndex = translated.toMap()
                                    }
                                    updateContent(settings)
                                }
                            }
                        }
                    }.awaitAll()
                }
            }

            try {
                if (settings.shouldUseSinglePrivateChapterRequestMode()) {
                    addGeminiLog("?? Private Gemini mode: forcing one request for the whole chapter")
                    val singleResult = requestTranslationBatch(
                        segments = indexedBlocks.map { it.second },
                        settings = settings,
                        onLog = { message -> addGeminiLog(message) },
                    )
                    val singleSuccess = singleResult?.any { !it.isNullOrBlank() } == true

                    if (singleSuccess) {
                        var successCount = 0
                        singleResult.orEmpty().forEachIndexed { index, text ->
                            if (!text.isNullOrBlank()) {
                                translated[index] = text
                                successCount += 1
                            }
                        }
                        addGeminiLog("? Single chapter request applied ($successCount/${indexedBlocks.size})")
                        geminiTranslationProgress = 100
                        if (translated.isNotEmpty()) {
                            geminiTranslatedByIndex = translated.toMap()
                        }
                        updateContent(settings)
                    } else {
                        addGeminiLog(
                            "?? Single chapter request failed, fallback to chunked mode " +
                                "(batch=$PRIVATE_FALLBACK_CHUNK_SIZE, concurrency=$PRIVATE_FALLBACK_CONCURRENCY)",
                        )
                        geminiTranslationProgress = 0
                        updateContent(settings)
                        val fallbackChunks = indexedBlocks.chunked(PRIVATE_FALLBACK_CHUNK_SIZE)
                        runChunkedTranslation(
                            chunks = fallbackChunks,
                            chunkSize = PRIVATE_FALLBACK_CHUNK_SIZE,
                            concurrencyLimit = PRIVATE_FALLBACK_CONCURRENCY,
                            strictOnNull = !settings.geminiRelaxedMode,
                        )
                    }
                } else {
                    val chunkSize = settings.geminiBatchSize.coerceIn(1, 80)
                    val chunks = indexedBlocks.chunked(chunkSize)
                    runChunkedTranslation(
                        chunks = chunks,
                        chunkSize = chunkSize,
                        concurrencyLimit = settings.translationConcurrencyLimit(),
                        strictOnNull = !settings.geminiRelaxedMode,
                    )
                }

                if (translated.isNotEmpty()) {
                    geminiTranslatedByIndex = translated.toMap()
                    hasGeminiTranslationCache = true
                    isGeminiTranslationVisible = true
                    geminiTranslationProgress = 100
                    if (!settings.geminiDisableCache) {
                        NovelReaderTranslationDiskCacheStore.put(
                            GeminiTranslationCacheEntry(
                                chapterId = chapter.id,
                                translatedByIndex = geminiTranslatedByIndex,
                                provider = settings.translationProvider,
                                model = settings.translationCacheModelId(),
                                sourceLang = settings.geminiSourceLang,
                                targetLang = settings.geminiTargetLang,
                                promptMode = settings.geminiPromptMode,
                                stylePreset = settings.geminiStylePreset,
                            ),
                        )
                        addGeminiLog("?? Cache saved for chapter ${chapter.id}")
                    }
                    addGeminiLog("?? ${settings.translationProvider} complete. Translated blocks: ${translated.size}")
                } else {
                    geminiTranslationProgress = 0
                    addGeminiLog("?? ${settings.translationProvider} finished with no translated blocks")
                }
            } catch (_: CancellationException) {
                // Job cancelled intentionally by user.
                addGeminiLog("?? ${settings.translationProvider} translation cancelled")
            } catch (error: Exception) {
                logcat(LogPriority.WARN, error) { "Translation failed for chapter=${chapter.id}" }
                addGeminiLog("? ${settings.translationProvider} failed: ${formatGeminiThrowableForLog(error)}")
                if (translated.isEmpty()) {
                    geminiTranslatedByIndex = emptyMap()
                    hasGeminiTranslationCache = false
                    isGeminiTranslationVisible = false
                    geminiTranslationProgress = 0
                }
            } finally {
                isGeminiTranslating = false
                updateContent(settings)
            }
        }
    }

    fun stopGeminiTranslation() {
        geminiTranslationJob?.cancel()
        geminiTranslationJob = null
        isGeminiTranslating = false
        isGeminiTranslationVisible = false
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

    private fun restoreGeminiTranslationFromCache(
        chapterId: Long,
        settings: NovelReaderSettings,
    ) {
        if (!settings.geminiEnabled) {
            hasGeminiTranslationCache = false
            return
        }
        if (settings.geminiDisableCache) {
            hasGeminiTranslationCache = false
            return
        }
        val cached = NovelReaderTranslationDiskCacheStore.get(chapterId)
        if (cached == null) {
            hasGeminiTranslationCache = false
            return
        }
        val settingsMatch = cached.provider == settings.translationProvider &&
            cached.model == settings.translationCacheModelId() &&
            cached.sourceLang == settings.geminiSourceLang &&
            cached.targetLang == settings.geminiTargetLang &&
            cached.promptMode == settings.geminiPromptMode &&
            cached.stylePreset == settings.geminiStylePreset
        if (!settingsMatch || cached.translatedByIndex.isEmpty()) {
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
    ): List<ContentBlock> {
        if (!isGeminiTranslationVisible || geminiTranslatedByIndex.isEmpty()) return blocks
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
    ): List<NovelRichContentBlock> {
        if (!isGeminiTranslationVisible || geminiTranslatedByIndex.isEmpty()) return blocks
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

    private fun NovelReaderSettings.resolveTranslationPromptModifiers(): String {
        val modifierText = GeminiPromptModifiers.buildPromptText(
            enabledIds = geminiEnabledPromptModifiers,
            customModifier = geminiCustomPromptModifier,
        )
        val styleDirective = NovelTranslationStylePresets.promptDirective(geminiStylePreset).trim()
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
            promptModifiers = resolveTranslationPromptModifiers(),
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
            promptModifiers = resolveTranslationPromptModifiers(),
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
            promptModifiers = resolveTranslationPromptModifiers(),
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

    private fun NovelReaderSettings.translationCacheModelId(): String {
        return when (translationProvider) {
            NovelTranslationProvider.GEMINI -> geminiModel.normalizeGeminiModelId()
            NovelTranslationProvider.GEMINI_PRIVATE -> geminiModel.normalizeGeminiModelId()
            NovelTranslationProvider.AIRFORCE -> airforceModel.trim()
            NovelTranslationProvider.OPENROUTER -> openRouterModel.trim()
            NovelTranslationProvider.DEEPSEEK -> deepSeekModel.trim()
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

    private fun String.normalizeGeminiModelId(): String {
        return when (trim()) {
            // Legacy key kept for backward compatibility with old settings.
            "gemini-3-flash" -> "gemini-3-flash-preview"
            "gemini-2.5-flash" -> "gemini-3.1-flash-lite-preview"
            else -> this
        }
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

        val text = (document.body()?.wholeText() ?: document.text())
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
        val candidates = document.body()?.select("p, li, blockquote, h1, h2, h3, h4, h5, h6, pre, img")
            ?.filterNot { node ->
                node.tagName().equals("p", ignoreCase = true) &&
                    node.parent()?.tagName()?.equals("li", ignoreCase = true) == true
            }
            .orEmpty()

        for (element in candidates) {
            if (element.tagName().equals("img", ignoreCase = true)) {
                val rawUrl = element.attr("src")
                    .ifBlank { element.attr("data-src") }
                    .ifBlank { element.attr("data-original") }
                    .trim()
                if (rawUrl.isBlank()) continue
                val resolvedUrl = resolveContentResourceUrl(
                    rawUrl = rawUrl,
                    chapterWebUrl = chapterWebUrl,
                    novelUrl = novelUrl,
                    pluginSite = pluginSite,
                ) ?: continue
                blocks += ContentBlock.Image(
                    url = resolvedUrl,
                    alt = element.attr("alt").sanitizeTextBlock().ifBlank { null },
                )
            } else {
                val text = element.text().sanitizeTextBlock()
                if (text.isNotBlank()) {
                    val structuredBlocks = parseStructuredFragmentToBlocks(
                        rawPayload = text,
                        chapterWebUrl = chapterWebUrl,
                        novelUrl = novelUrl,
                        pluginSite = pluginSite,
                    )
                    if (structuredBlocks.isNotEmpty()) {
                        blocks += structuredBlocks
                        continue
                    }
                    val normalizedText = if (element.tagName().equals("li", ignoreCase = true)) {
                        "• $text"
                    } else {
                        text
                    }
                    blocks += ContentBlock.Text(normalizedText)
                }
            }
        }

        return blocks
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
            val htmlTextCandidate = Jsoup.parse(trimmed).body()?.wholeText().orEmpty().trim()
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

    sealed interface State {
        data object Loading : State
        data class Error(val message: String?) : State
        data class Success(
            val novel: Novel,
            val chapter: NovelChapter,
            val html: String,
            val enableJs: Boolean,
            val readerSettings: NovelReaderSettings,
            val contentBlocks: List<ContentBlock>,
            val textBlocks: List<String>,
            val richContentBlocks: List<NovelRichContentBlock>,
            val richContentUnsupportedFeaturesDetected: Boolean,
            val lastSavedIndex: Int,
            val lastSavedScrollOffsetPx: Int,
            val lastSavedWebProgressPercent: Int,
            val previousChapterId: Long?,
            val nextChapterId: Long?,
            val chapterWebUrl: String?,
            val isGeminiTranslating: Boolean = false,
            val geminiTranslationProgress: Int = 0,
            val isGeminiTranslationVisible: Boolean = false,
            val hasGeminiTranslationCache: Boolean = false,
            val geminiLogs: List<String> = emptyList(),
            val airforceModelIds: List<String> = emptyList(),
            val isAirforceModelsLoading: Boolean = false,
            val isTestingAirforceConnection: Boolean = false,
            val openRouterModelIds: List<String> = emptyList(),
            val isOpenRouterModelsLoading: Boolean = false,
            val isTestingOpenRouterConnection: Boolean = false,
            val deepSeekModelIds: List<String> = emptyList(),
            val isDeepSeekModelsLoading: Boolean = false,
            val isTestingDeepSeekConnection: Boolean = false,
        ) : State
    }

    sealed interface ContentBlock {
        data class Text(val text: String) : ContentBlock
        data class Image(val url: String, val alt: String?) : ContentBlock
    }

    companion object {
        private const val JAOMIX_PAGE_SOURCE_ORDER_STRIDE = 1_000L
        private const val MAX_DEEPSEEK_CONCURRENCY = 32
        private const val PRIVATE_FALLBACK_CHUNK_SIZE = 40
        private const val PRIVATE_FALLBACK_CONCURRENCY = 1
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
