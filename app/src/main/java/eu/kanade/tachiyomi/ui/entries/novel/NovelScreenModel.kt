package eu.kanade.tachiyomi.ui.entries.novel

import android.app.Application
import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Immutable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.novel.interactor.GetNovelExcludedScanlators
import eu.kanade.domain.entries.novel.interactor.NovelRatingFetcher
import eu.kanade.domain.entries.novel.interactor.SetNovelExcludedScanlators
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.entries.novel.model.chaptersFiltered
import eu.kanade.domain.entries.novel.model.effectiveDownloadedFilter
import eu.kanade.domain.entries.novel.model.toSNovel
import eu.kanade.domain.items.novelchapter.interactor.GetAvailableNovelScanlators
import eu.kanade.domain.items.novelchapter.interactor.GetNovelScanlatorChapterCounts
import eu.kanade.domain.items.novelchapter.interactor.SyncNovelChaptersWithSource
import eu.kanade.presentation.util.TargetChapterCalculator
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadCache
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadCacheEvent
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadQueueManager
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadQueueState
import eu.kanade.tachiyomi.data.download.novel.NovelQueuedDownloadStatus
import eu.kanade.tachiyomi.data.download.novel.NovelQueuedDownloadType
import eu.kanade.tachiyomi.data.download.novel.NovelTranslatedDownloadFormat
import eu.kanade.tachiyomi.data.download.novel.NovelTranslatedDownloadManager
import eu.kanade.tachiyomi.data.export.novel.NovelEpubExportOptions
import eu.kanade.tachiyomi.data.export.novel.NovelEpubExporter
import eu.kanade.tachiyomi.data.track.MangaTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.translation.TranslationJob
import eu.kanade.tachiyomi.data.translation.TranslationQueueManager
import eu.kanade.tachiyomi.extension.novel.runtime.NovelJsSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.source.novel.NovelSiteSource
import eu.kanade.tachiyomi.source.novel.NovelWebUrlSource
import eu.kanade.tachiyomi.ui.entries.mergeNewItemIds
import eu.kanade.tachiyomi.ui.entries.novel.NovelChapterActionStateResolver
import eu.kanade.tachiyomi.ui.entries.novel.NovelChapterActionUiState
import eu.kanade.tachiyomi.ui.novel.resolveNovelResumeChapter
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationCacheResolver
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationDiskCacheStore
import eu.kanade.tachiyomi.ui.reader.novel.translation.toTranslationCacheRequirements
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.data.achievement.model.AchievementEvent
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.category.novel.interactor.SetNovelCategories
import tachiyomi.domain.entries.applyFilter
import tachiyomi.domain.entries.novel.interactor.GetNovelWithChapters
import tachiyomi.domain.entries.novel.interactor.SetNovelChapterFlags
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import tachiyomi.domain.items.novelchapter.interactor.SetNovelDefaultChapterFlags
import tachiyomi.domain.items.novelchapter.model.NoChaptersException
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.items.novelchapter.service.getNovelChapterSort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.track.novel.interactor.GetNovelTracks
import tachiyomi.domain.track.novel.model.NovelTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.time.Instant
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlin.system.measureTimeMillis

enum class NovelDownloadAction {
    NEXT,
    UNREAD,
    ALL,
    NOT_DOWNLOADED,
}

data class NovelEpubExportPreferencesState(
    val destinationTreeUri: String,
    val applyReaderTheme: Boolean,
    val includeCustomCss: Boolean,
    val includeCustomJs: Boolean,
)

internal fun buildNovelChapterActionUiStates(
    geminiEnabled: Boolean,
    chapters: List<NovelChapter>,
    translatedDownloadFormat: NovelTranslatedDownloadFormat,
    hasTranslationCache: (NovelChapter) -> Boolean,
    isTranslatedDownloaded: (NovelChapter) -> Boolean,
    isTranslatedDownloading: (NovelChapter) -> Boolean,
    isTranslating: (NovelChapter) -> Boolean = { false },
): Map<Long, NovelChapterActionUiState> {
    return chapters.associate { chapter ->
        chapter.id to NovelChapterActionStateResolver.resolve(
            geminiEnabled = geminiEnabled,
            hasTranslationCache = hasTranslationCache(chapter),
            isTranslating = isTranslating(chapter),
            isTranslatedDownloaded = isTranslatedDownloaded(chapter),
            isTranslatedDownloading = isTranslatedDownloading(chapter),
            translatedDownloadFormat = translatedDownloadFormat,
        )
    }
}

class NovelScreenModel(
    private val lifecycle: Lifecycle,
    private val novelId: Long,
    private val basePreferences: BasePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val getNovelWithChapters: GetNovelWithChapters = Injekt.get(),
    private val updateNovel: UpdateNovel = Injekt.get(),
    private val syncNovelChaptersWithSource: SyncNovelChaptersWithSource = Injekt.get(),
    private val novelChapterRepository: NovelChapterRepository = Injekt.get(),
    private val novelHistoryRepository: NovelHistoryRepository = Injekt.get(),
    private val setNovelChapterFlags: SetNovelChapterFlags = Injekt.get(),
    private val setNovelDefaultChapterFlags: SetNovelDefaultChapterFlags = Injekt.get(),
    private val getAvailableNovelScanlators: GetAvailableNovelScanlators = Injekt.get(),
    private val getNovelScanlatorChapterCounts: GetNovelScanlatorChapterCounts = Injekt.get(),
    private val getNovelExcludedScanlators: GetNovelExcludedScanlators = Injekt.get(),
    private val setNovelExcludedScanlators: SetNovelExcludedScanlators = Injekt.get(),
    private val getNovelCategories: GetNovelCategories = Injekt.get(),
    private val setNovelCategories: SetNovelCategories = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val novelRatingFetcher: NovelRatingFetcher = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    private val getTracks: GetNovelTracks = Injekt.get(),
    private val novelDownloadManager: NovelDownloadManager = NovelDownloadManager(),
    private val novelTranslatedDownloadManager: NovelTranslatedDownloadManager = NovelTranslatedDownloadManager(),
    private val downloadCacheChanges: Flow<NovelDownloadCacheEvent> = runCatching {
        Injekt.get<NovelDownloadCache>().changes
    }.getOrElse { emptyFlow() },
    private val downloadQueueState:
    Flow<eu.kanade.tachiyomi.data.download.novel.NovelDownloadQueueState> = NovelDownloadQueueManager.state,
    private val resolveDownloadedChapterIds: (Novel, List<NovelChapter>) -> Set<Long> = { novel, chapters ->
        novelDownloadManager.getDownloadedChapterIds(novel, chapters)
    },
    private val enqueueOriginal: (Novel, List<NovelChapter>) -> Int = { novel, chapters ->
        NovelDownloadQueueManager.enqueueOriginal(novel, chapters)
    },
    private val enqueueTranslated: (Novel, List<NovelChapter>, NovelTranslatedDownloadFormat) -> Int = {
            novel,
            chapters,
            format,
        ->
        NovelDownloadQueueManager.enqueueTranslated(novel, chapters, format)
    },
    private val novelEpubExporter: NovelEpubExporter = NovelEpubExporter(),
    private val novelReaderPreferences: NovelReaderPreferences = Injekt.get(),
    private val translationQueueManager: TranslationQueueManager = Injekt.get(),
    private val eventBus: AchievementEventBus? = runCatching { Injekt.get<AchievementEventBus>() }.getOrNull(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<NovelScreenModel.State>(State.Loading) {

    private data class QueueNotifySummary(
        val pending: Int = 0,
        val active: Int = 0,
        val failed: Int = 0,
    ) {
        val activeTotal: Int
            get() = pending + active
    }

    private val downloadedStateVersion = AtomicLong(0L)
    private var downloadedStateJob: Job? = null
    private var chapterActionStatesJob: Job? = null

    private val successState: State.Success?
        get() = state.value as? State.Success

    val novel: Novel?
        get() = successState?.novel

    val source: NovelSource?
        get() = successState?.source

    val isAnyChapterSelected: Boolean
        get() = successState?.selectedChapterIds?.isNotEmpty() ?: false

    val chapterSwipeStartAction = libraryPreferences.swipeNovelChapterEndAction().get()
    val chapterSwipeEndAction = libraryPreferences.swipeNovelChapterStartAction().get()

    private var scanlatorSelectionJob: Job? = null

    private val openTranslatedFolderChannel = kotlinx.coroutines.flow.MutableSharedFlow<android.net.Uri>()

    fun openTranslatedFolderEvents() = openTranslatedFolderChannel.asSharedFlow()

    fun isReadingStarted(): Boolean {
        val state = successState ?: return false
        return state.chapters.any { it.read || it.lastPageRead > 0L }
    }

    fun getResumeOrNextChapter(): NovelChapter? {
        val state = successState ?: return null
        return resolveNovelResumeChapter(state.chapters, state.resumeChapterId)
    }

    suspend fun getContinueChapter(): NovelChapter? = withIOContext {
        val state = successState ?: return@withIOContext null
        val historyChapterId = novelHistoryRepository.getHistoryByNovelId(novelId)
            .maxByOrNull { it.readAt?.time ?: Long.MIN_VALUE }
            ?.chapterId
        resolveNovelResumeChapter(state.chapters, historyChapterId)
    }

    fun getNextUnreadChapter(): NovelChapter? {
        val state = successState ?: return null
        return resolveNovelResumeChapter(state.processedChapters)
    }

    init {
        restoreStateFromCache(novelId)?.let {
            mutableState.value = it
            // Refresh chapter action states (translate/download icons) asynchronously
            // to avoid blocking the main thread with SAF filesystem operations.
            refreshChapterActionStatesAsync()
        }

        screenModelScope.launchIO {
            getNovelWithChapters.subscribe(novelId, applyScanlatorFilter = true)
                .distinctUntilChanged()
                .collectLatest { (novel, chapters) ->
                    logcat(LogPriority.DEBUG) {
                        "Novel chapters flow emission id=${novel.id} source=${novel.source} " +
                            "count=${chapters.size}"
                    }
                    val chapterIds = chapters.mapTo(mutableSetOf()) { c -> c.id }
                    val chapterUrls = chapters.mapTo(mutableSetOf()) { c -> c.url }
                    val previousChapterIds = successState
                        ?.chapters
                        ?.let { existingChapters -> existingChapters.mapTo(mutableSetOf()) { it.id } }
                        ?: emptySet()
                    val chapterIdsChanged = previousChapterIds != chapterIds
                    updateSuccessState {
                        it.copy(
                            novel = novel,
                            chapters = chapters,
                            selectedChapterIds = it.selectedChapterIds.intersect(
                                chapterIds,
                            ),
                            downloadedChapterIds = it.downloadedChapterIds.intersect(chapterIds),
                            downloadingChapterIds = it.downloadingChapterIds.intersect(chapterIds),
                            chapterPageVisibleUrls = if (it.chapterPageEnabled) {
                                it.chapterPageVisibleUrls.intersect(chapterUrls)
                            } else {
                                emptySet()
                            },
                        )
                    }
                    if (chapterIdsChanged) {
                        syncDownloadedState()
                        refreshChapterActionStatesAsync(delayMs = 100L)
                    }
                }
        }

        screenModelScope.launchIO {
            getNovelExcludedScanlators.subscribe(novelId)
                .flowWithLifecycle(lifecycle)
                .distinctUntilChanged()
                .collectLatest { excludedScanlators ->
                    updateSuccessState {
                        it.copy(excludedScanlators = excludedScanlators)
                    }
                    maybeNormalizeNovelBranchSelection()
                }
        }

        screenModelScope.launchIO {
            getAvailableNovelScanlators.subscribe(novelId)
                .flowWithLifecycle(lifecycle)
                .distinctUntilChanged()
                .collectLatest { availableScanlators ->
                    updateSuccessState {
                        it.copy(availableScanlators = availableScanlators)
                    }
                    maybeNormalizeNovelBranchSelection()
                }
        }

        screenModelScope.launchIO {
            getNovelScanlatorChapterCounts.subscribe(novelId)
                .flowWithLifecycle(lifecycle)
                .distinctUntilChanged()
                .collectLatest { scanlatorChapterCounts ->
                    updateSuccessState {
                        it.copy(scanlatorChapterCounts = scanlatorChapterCounts)
                    }
                    maybeNormalizeNovelBranchSelection()
                }
        }

        screenModelScope.launchIO {
            downloadCacheChanges
                .onStart { emit(NovelDownloadCacheEvent.InvalidateAll) }
                .flowWithLifecycle(lifecycle)
                .collectLatest(::handleDownloadCacheEvent)
        }

        screenModelScope.launchIO {
            downloadQueueState.collectLatest { queueState ->
                updateSuccessState { current ->
                    val allNovelTasks = queueState.tasks.filter { task -> task.novel.id == current.novel.id }
                    val activeChapterIds = queueState.tasks
                        .asSequence()
                        .filter { task ->
                            task.novel.id == current.novel.id &&
                                task.type == NovelQueuedDownloadType.ORIGINAL &&
                                (
                                    task.status == NovelQueuedDownloadStatus.QUEUED ||
                                        task.status == NovelQueuedDownloadStatus.DOWNLOADING
                                    )
                        }
                        .map { it.chapter.id }
                        .toSet()

                    val queueSummary = QueueNotifySummary(
                        pending = allNovelTasks.count { it.status == NovelQueuedDownloadStatus.QUEUED },
                        active = allNovelTasks.count { it.status == NovelQueuedDownloadStatus.DOWNLOADING },
                        failed = allNovelTasks.count { it.status == NovelQueuedDownloadStatus.FAILED },
                    )
                    maybeNotifyQueueState(queueSummary)

                    if (activeChapterIds == current.downloadingChapterIds) {
                        current
                    } else {
                        current.copy(
                            downloadingChapterIds = activeChapterIds,
                        )
                    }
                }
                // Download queue changes may affect translated-download icon states.
                refreshChapterActionStatesAsync()
            }
        }

        screenModelScope.launchIO {
            val novel = getNovelWithChapters.awaitNovel(novelId)
            if (!novel.favorite) {
                setNovelDefaultChapterFlags.await(novel)
            }

            val availableScanlators = getAvailableNovelScanlators.await(novelId)
            val scanlatorChapterCounts = getNovelScanlatorChapterCounts.await(novelId)
            val storedExcludedScanlators = getNovelExcludedScanlators.await(novelId)
            val initialExcludedScanlators = resolveDefaultNovelExcludedScanlatorsByChapterCount(
                scanlatorChapterCounts = scanlatorChapterCounts,
                availableScanlators = availableScanlators,
                excludedScanlators = storedExcludedScanlators,
            ) ?: storedExcludedScanlators
            val resumeChapterId = novelHistoryRepository.getHistoryByNovelId(novelId)
                .maxByOrNull { it.readAt?.time ?: Long.MIN_VALUE }
                ?.chapterId

            if (initialExcludedScanlators != storedExcludedScanlators) {
                setNovelExcludedScanlators.await(novelId, initialExcludedScanlators)
            }

            val chapters = getNovelWithChapters.awaitChapters(novelId, applyScanlatorFilter = true)
            val source = sourceManager.getOrStub(novel.source)
            val readerSettings = novelReaderPreferences.resolveSettings(novel.source)
            val translatedDownloadFormat = novelReaderPreferences.translatedDownloadFormat(novel.id)
            val isJaomixPagedSource = source.isJaomixPagedSource()
            val shouldAutoRefreshNovel = !novel.initialized
            val shouldAutoRefreshChapters = chapters.isEmpty() || isJaomixPagedSource
            val currentDownloadedIds = (state.value as? State.Success)
                ?.downloadedChapterIds
                ?.intersect(chapters.mapTo(mutableSetOf()) { it.id })
                .orEmpty()
            val initialState = State.Success(
                novel = novel,
                source = source,
                rating = null,
                chapters = chapters,
                availableScanlators = availableScanlators,
                scanlatorChapterCounts = scanlatorChapterCounts,
                excludedScanlators = initialExcludedScanlators,
                downloadedOnly = basePreferences.downloadedOnly().get(),
                geminiEnabled = readerSettings.geminiEnabled,
                translatedDownloadFormat = translatedDownloadFormat,
                isRefreshingData = shouldAutoRefreshNovel || shouldAutoRefreshChapters,
                dialog = null,
                selectedChapterIds = emptySet(),
                downloadedChapterIds = currentDownloadedIds,
                downloadingChapterIds = emptySet(),
                chapterActionStates = buildNovelChapterActionUiStates(
                    geminiEnabled = readerSettings.geminiEnabled,
                    chapters = chapters,
                    translatedDownloadFormat = translatedDownloadFormat,
                    hasTranslationCache = { false },
                    isTranslatedDownloaded = { false },
                    isTranslatedDownloading = { false },
                    isTranslating = { false },
                ),
                chapterPageEnabled = isJaomixPagedSource,
                chapterPageEstimatedTotal = if (isJaomixPagedSource && chapters.isNotEmpty()) {
                    chapters.size
                } else {
                    0
                },
                chapterPageNominalSize = if (isJaomixPagedSource) chapters.size else 0,
                chapterPageVisibleUrls = if (isJaomixPagedSource) {
                    chapters.mapTo(mutableSetOf()) { it.url }
                } else {
                    emptySet()
                },
                resumeChapterId = resumeChapterId,
                hasCompletedChapterRefresh = chapters.isNotEmpty(),
            )
            mutableState.update {
                initialState
            }
            // Seed the UI with lightweight neutral Gemini actions immediately, then
            // resolve translated download and translation cache state in the background.
            refreshChapterActionStatesAsync()
            screenModelScope.launchIO {
                basePreferences.downloadedOnly().changes()
                    .collectLatest { downloadedOnly ->
                        updateSuccessState { it.copy(downloadedOnly = downloadedOnly) }
                    }
            }

            screenModelScope.launchIO {
                novelReaderPreferences.geminiEnabled().changes()
                    .collectLatest {
                        refreshChapterActionStatesAsync(delayMs = 100L)
                    }
            }

            screenModelScope.launchIO {
                translationQueueManager.activeTranslation
                    .drop(1)
                    .collectLatest {
                        refreshChapterActionStatesAsync(delayMs = 100L)
                    }
            }

            logRefreshSnapshot(
                stage = "initial-state",
                source = source,
                novel = novel,
                chapterCount = chapters.size,
                manualFetch = false,
            )
            if (isLikelyWebViewLoginRequired(source, novel, chapters.size)) {
                logcat(LogPriority.WARN) {
                    "Novel ${novel.id} (${source.name}) likely requires WebView login: " +
                        "chapters=0, descriptionBlank=true"
                }
            }
            cacheState(state.value as? State.Success)
            if (!(shouldAutoRefreshNovel || shouldAutoRefreshChapters)) {
                refreshNovelRating(
                    state = state.value as? State.Success,
                    forceRefresh = false,
                )
            }
            observeTrackers()
            syncDownloadedState()

            if ((shouldAutoRefreshNovel || shouldAutoRefreshChapters) && screenModelScope.isActive) {
                refreshChapters(
                    manualFetch = false,
                    refreshNovel = shouldAutoRefreshNovel,
                    refreshChapters = shouldAutoRefreshChapters,
                )
            }
        }
    }

    private inline fun updateSuccessState(
        func: (State.Success) -> State.Success,
    ) {
        mutableState.update {
            when (it) {
                State.Loading -> it
                is State.Success -> func(it)
                    .also(::cacheState)
            }
        }
    }

    /**
     * Schedules a debounced, off-main-thread recomputation of chapter action
     * states (translate/download icons).  This is intentionally decoupled from
     * [updateSuccessState] because the resolution touches the filesystem
     * (translation cache + translated-download existence checks) for every
     * chapter, which is prohibitively expensive to run on every state update.
     */
    private fun refreshChapterActionStatesAsync(delayMs: Long = 300L) {
        chapterActionStatesJob?.cancel()
        chapterActionStatesJob = screenModelScope.launchIO {
            delay(delayMs)
            val current = successState ?: return@launchIO
            val resolved = current.withResolvedChapterActionStates()
            if (resolved.chapterActionStates != current.chapterActionStates ||
                resolved.geminiEnabled != current.geminiEnabled ||
                resolved.translatedDownloadFormat != current.translatedDownloadFormat
            ) {
                mutableState.update {
                    when (it) {
                        State.Loading -> it
                        is State.Success -> it.copy(
                            geminiEnabled = resolved.geminiEnabled,
                            translatedDownloadFormat = resolved.translatedDownloadFormat,
                            chapterActionStates = resolved.chapterActionStates,
                        ).also(::cacheState)
                    }
                }
            }
        }
    }

    private fun updateChapterState(
        novel: Novel,
        chapters: List<NovelChapter>,
    ) {
        val chapterIds = chapters.mapTo(mutableSetOf()) { it.id }
        val chapterUrls = chapters.mapTo(mutableSetOf()) { it.url }
        updateSuccessState { current ->
            if (current.novel.id != novel.id) {
                current
            } else {
                current.copy(
                    novel = novel,
                    chapters = chapters,
                    selectedChapterIds = current.selectedChapterIds.intersect(chapterIds),
                    downloadedChapterIds = current.downloadedChapterIds.intersect(chapterIds),
                    downloadingChapterIds = current.downloadingChapterIds.intersect(chapterIds),
                    chapterPageVisibleUrls = if (current.chapterPageEnabled) {
                        current.chapterPageVisibleUrls.intersect(chapterUrls)
                    } else {
                        emptySet()
                    },
                    hasCompletedChapterRefresh = true,
                )
            }
        }
    }

    private fun maybeNormalizeNovelBranchSelection() {
        val state = successState ?: return
        val normalizedExcludedScanlators = resolveDeferredDefaultNovelExcludedScanlators(
            shouldAttemptAutoSelection = true,
            storedExcludedScanlators = state.excludedScanlators,
            availableScanlators = state.availableScanlators,
            scanlatorChapterCounts = state.scanlatorChapterCounts,
        ) ?: return
        if (normalizedExcludedScanlators == state.excludedScanlators) return
        applyNovelExcludedScanlators(normalizedExcludedScanlators)
    }

    private fun applyNovelExcludedScanlators(excludedScanlators: Set<String>) {
        val state = successState ?: return
        if (excludedScanlators == state.excludedScanlators) return

        updateSuccessState {
            it.copy(excludedScanlators = excludedScanlators)
        }

        scanlatorSelectionJob?.cancel()
        scanlatorSelectionJob = screenModelScope.launchIO {
            setNovelExcludedScanlators.await(novelId, excludedScanlators)

            // Refresh chapters immediately so branch switches don't wait for flow invalidation.
            val chapters = getNovelWithChapters.awaitChapters(novelId, applyScanlatorFilter = true)
            val chapterIds = chapters.mapTo(mutableSetOf()) { chapter -> chapter.id }
            val chapterUrls = chapters.mapTo(mutableSetOf()) { chapter -> chapter.url }
            updateSuccessState {
                it.copy(
                    chapters = chapters,
                    selectedChapterIds = it.selectedChapterIds.intersect(chapterIds),
                    downloadedChapterIds = it.downloadedChapterIds.intersect(chapterIds),
                    downloadingChapterIds = it.downloadingChapterIds.intersect(chapterIds),
                    chapterPageVisibleUrls = if (it.chapterPageEnabled) {
                        it.chapterPageVisibleUrls.intersect(chapterUrls)
                    } else {
                        emptySet()
                    },
                )
            }
            refreshChapterActionStatesAsync(delayMs = 100L)
        }
    }

    private fun syncDownloadedState() {
        val state = successState ?: return
        val requestVersion = downloadedStateVersion.incrementAndGet()

        downloadedStateJob?.cancel()

        downloadedStateJob = screenModelScope.launchIO {
            delay(500)

            var downloadedIds = emptySet<Long>()

            val downloadCache = runCatching { Injekt.get<NovelDownloadCache>() }.getOrNull()
            val cachedIds = downloadCache?.getDownloadedChapterIds(state.novel.id)
            if (cachedIds != null) {
                downloadedIds = cachedIds.intersect(state.chapters.map { it.id }.toSet())
                logcat(LogPriority.DEBUG) {
                    "Novel downloaded-state sync: novel=${state.novel.id}, from_cache=true, count=${downloadedIds.size}"
                }
            } else {
                val elapsed = measureTimeMillis {
                    downloadedIds = resolveDownloadedChapterIds(state.novel, state.chapters)
                }
                if (downloadedStateVersion.get() != requestVersion) return@launchIO
                logcat(LogPriority.DEBUG) {
                    "Novel downloaded-state sync: novel=${state.novel.id}, from_fs=true, count=${downloadedIds.size}, elapsedMs=$elapsed"
                }
                downloadCache?.updateChapterIds(state.novel.id, downloadedIds)
            }

            if (downloadedStateVersion.get() != requestVersion) return@launchIO
            updateSuccessState {
                if (downloadedIds == it.downloadedChapterIds) {
                    it
                } else {
                    it.copy(downloadedChapterIds = downloadedIds)
                }
            }
        }
    }

    internal fun handleDownloadCacheEvent(event: NovelDownloadCacheEvent) {
        // Targeted chapter changes may update state incrementally, but we keep InvalidateAll as the
        // reconciliation path after storage changes, process restarts, or any suspected mismatch.
        when (event) {
            is NovelDownloadCacheEvent.ChaptersChanged -> {
                updateDownloadedStateFromCacheEvent(event)
            }
            NovelDownloadCacheEvent.InvalidateAll -> {
                syncDownloadedState()
            }
            is NovelDownloadCacheEvent.NovelRemoved -> {
                updateDownloadedStateForRemovedNovel(event.novelId)
            }
        }
    }

    private fun updateDownloadedStateFromCacheEvent(event: NovelDownloadCacheEvent.ChaptersChanged) {
        val state = successState ?: return
        if (event.novelId != state.novel.id) return
        val knownChapterIds = state.chapters.asSequence().map { it.id }.toSet()
        val affectedChapterIds = event.chapterIds.intersect(knownChapterIds)
        if (affectedChapterIds.isEmpty()) return
        downloadedStateVersion.incrementAndGet()

        updateSuccessState {
            val downloadedChapterIds = if (event.downloaded) {
                it.downloadedChapterIds + affectedChapterIds
            } else {
                it.downloadedChapterIds - affectedChapterIds
            }
            if (downloadedChapterIds == it.downloadedChapterIds) {
                it
            } else {
                it.copy(downloadedChapterIds = downloadedChapterIds)
            }
        }
    }

    private fun updateDownloadedStateForRemovedNovel(novelId: Long) {
        val state = successState ?: return
        if (state.novel.id != novelId) return
        downloadedStateVersion.incrementAndGet()
        updateSuccessState {
            if (it.downloadedChapterIds.isEmpty()) {
                it
            } else {
                it.copy(downloadedChapterIds = emptySet())
            }
        }
    }

    private fun State.Success.withResolvedChapterActionStates(
        queueState: NovelDownloadQueueState = NovelDownloadQueueManager.state.value,
    ): State.Success {
        val readerSettings = novelReaderPreferences.resolveSettings(novel.source)
        val translatedDownloadFormat = novelReaderPreferences.translatedDownloadFormat(novel.id)
        val translatedQueueChapterIds = resolveTranslatedQueueChapterIds(
            queueState = queueState,
            novelId = novel.id,
            format = translatedDownloadFormat,
        )

        val translationCacheRequirements = readerSettings.toTranslationCacheRequirements()
        val translatedDownloadedIds = mutableSetOf<Long>()
        val translatedNotDownloadedIds = mutableSetOf<Long>()
        val translatingChapterId = translationQueueManager.activeTranslation.value?.chapterId

        return copy(
            geminiEnabled = readerSettings.geminiEnabled,
            translatedDownloadFormat = translatedDownloadFormat,
            chapterActionStates = buildNovelChapterActionUiStates(
                geminiEnabled = readerSettings.geminiEnabled,
                chapters = chapters,
                translatedDownloadFormat = translatedDownloadFormat,
                hasTranslationCache = { chapter ->
                    NovelReaderTranslationCacheResolver.matches(
                        NovelReaderTranslationDiskCacheStore.get(chapter.id),
                        translationCacheRequirements,
                    )
                },
                isTranslatedDownloaded = { chapter ->
                    when {
                        chapter.id in translatedDownloadedIds -> true
                        chapter.id in translatedNotDownloadedIds -> false
                        else -> {
                            val downloaded = novelTranslatedDownloadManager.isTranslatedChapterDownloaded(
                                novel = novel,
                                chapter = chapter,
                                format = translatedDownloadFormat,
                            )
                            if (downloaded) {
                                translatedDownloadedIds.add(chapter.id)
                            } else {
                                translatedNotDownloadedIds.add(chapter.id)
                            }
                            downloaded
                        }
                    }
                },
                isTranslatedDownloading = { chapter ->
                    chapter.id in translatedQueueChapterIds
                },
                isTranslating = { chapter ->
                    chapter.id == translatingChapterId
                },
            ),
        )
    }

    private fun resolveTranslatedQueueChapterIds(
        queueState: NovelDownloadQueueState,
        novelId: Long,
        format: NovelTranslatedDownloadFormat,
    ): Set<Long> {
        val queueFormat = when (format) {
            NovelTranslatedDownloadFormat.TXT -> eu.kanade.tachiyomi.data.download.novel.NovelQueuedDownloadFormat.TXT
            NovelTranslatedDownloadFormat.DOCX -> eu.kanade.tachiyomi.data.download.novel.NovelQueuedDownloadFormat.DOCX
        }
        return queueState.tasks
            .asSequence()
            .filter { task ->
                task.novel.id == novelId &&
                    task.type == NovelQueuedDownloadType.TRANSLATED &&
                    task.format == queueFormat &&
                    (
                        task.status == NovelQueuedDownloadStatus.QUEUED ||
                            task.status == NovelQueuedDownloadStatus.DOWNLOADING
                        )
            }
            .map { it.chapter.id }
            .toSet()
    }

    private fun updateNewChapterIds(
        addedIds: Iterable<Long> = emptyList(),
        clearedIds: Iterable<Long> = emptyList(),
    ) {
        updateSuccessState { successState ->
            successState.copy(
                newChapterIds = mergeNewItemIds(
                    existingNewItemIds = successState.newChapterIds,
                    addedItemIds = addedIds,
                    clearedItemIds = clearedIds,
                ),
            )
        }
    }

    private fun notifyQueueStarted(addedCount: Int) {
        if (addedCount <= 0) return
    }

    private fun maybeNotifyQueueState(summary: QueueNotifySummary) {
        return
    }

    fun toggleFavorite() {
        val novel = successState?.novel ?: return
        screenModelScope.launchIO {
            if (novel.favorite) {
                updateNovel.await(
                    NovelUpdate(
                        id = novel.id,
                        favorite = false,
                        dateAdded = 0L,
                    ),
                )
                return@launchIO
            }

            val added = updateNovel.await(
                NovelUpdate(
                    id = novel.id,
                    favorite = true,
                    dateAdded = Instant.now().toEpochMilli(),
                ),
            )
            if (!added) return@launchIO

            setNovelDefaultChapterFlags.await(novel)

            val categories = getCategories()
            val defaultCategoryId = libraryPreferences.defaultNovelCategory().get().toLong()
            val defaultCategory = categories.find { it.id == defaultCategoryId }
            when {
                defaultCategory != null -> moveNovelToCategories(novel.id, listOf(defaultCategory.id))
                defaultCategoryId == 0L || categories.isEmpty() -> moveNovelToCategories(novel.id, emptyList())
                else -> moveNovelToCategories(novel.id, emptyList())
            }
        }
    }

    private suspend fun getCategories(): List<Category> {
        return getNovelCategories.await()
            .map {
                Category(
                    id = it.id,
                    name = it.name,
                    order = it.order,
                    flags = it.flags,
                    hidden = it.hidden,
                    hiddenFromHomeHub = false,
                )
            }
            .filterNot(Category::isSystemCategory)
    }

    private suspend fun moveNovelToCategories(novelId: Long, categoryIds: List<Long>) {
        setNovelCategories.await(novelId, categoryIds)
    }

    private fun isLikelyWebViewLoginRequired(
        source: NovelSource,
        novel: Novel,
        chapterCount: Int,
    ): Boolean {
        val supportsWebLogin = source is NovelSiteSource || source is NovelWebUrlSource
        return supportsWebLogin &&
            chapterCount == 0 &&
            novel.description.isNullOrBlank() &&
            novel.url.isNotBlank()
    }

    private fun logRefreshSnapshot(
        stage: String,
        source: NovelSource,
        novel: Novel,
        chapterCount: Int,
        manualFetch: Boolean,
    ) {
        logcat {
            "Novel refresh snapshot stage=$stage id=${novel.id} source=${source.name} " +
                "manualFetch=$manualFetch chapters=$chapterCount initialized=${novel.initialized} " +
                "descriptionBlank=${novel.description.isNullOrBlank()}"
        }
    }

    fun refreshChapters(
        manualFetch: Boolean = true,
        refreshNovel: Boolean = true,
        refreshChapters: Boolean = true,
    ) {
        val state = successState ?: return
        screenModelScope.launchIO {
            logRefreshSnapshot(
                stage = "refresh-start",
                source = state.source,
                novel = state.novel,
                chapterCount = state.chapters.size,
                manualFetch = manualFetch,
            )
            updateSuccessState { it.copy(isRefreshingData = true) }
            try {
                supervisorScope {
                    val tasks = listOf(
                        async {
                            if (refreshNovel) {
                                runCatching {
                                    fetchNovelFromSource(state, manualFetch)
                                }.onFailure { error ->
                                    handleRefreshError(error, state)
                                }
                            }
                        },
                        async {
                            if (refreshChapters) {
                                runCatching {
                                    fetchChaptersFromSource(state, manualFetch)
                                }.onFailure { error ->
                                    handleRefreshError(error, state)
                                }
                            }
                        },
                    )
                    tasks.awaitAll()
                }
                successState?.let { latest ->
                    logRefreshSnapshot(
                        stage = "refresh-end",
                        source = latest.source,
                        novel = latest.novel,
                        chapterCount = latest.chapters.size,
                        manualFetch = manualFetch,
                    )
                }
            } finally {
                updateSuccessState { it.copy(isRefreshingData = false) }
            }
        }
    }

    private suspend fun handleRefreshError(
        error: Throwable,
        state: State.Success?,
    ) {
        if (error is CancellationException) throw error
        logcat(LogPriority.WARN, error) {
            "Novel refresh failed for novelId=$novelId"
        }
        val likelyWebViewLoginRequired = state?.let {
            isLikelyWebViewLoginRequired(
                source = it.source,
                novel = it.novel,
                chapterCount = it.chapters.size,
            )
        } ?: false
        val message = resolveNovelRefreshErrorMessage(
            error = error,
            likelyWebViewLoginRequired = likelyWebViewLoginRequired,
        )
        if (message == null) {
            logcat {
                "Suppressed refresh snackbar for novelId=$novelId due to likely WebView login requirement"
            }
            return
        }
        snackbarHostState.showSnackbar(message = message)
    }

    private suspend fun fetchNovelFromSource(
        state: State.Success,
        manualFetch: Boolean,
    ) {
        val networkNovel = state.source.getNovelDetails(state.novel.toSNovel())
        logcat {
            "Fetched novel details for id=${state.novel.id} source=${state.source.name}, " +
                "initialized=${networkNovel.initialized}, " +
                "descriptionBlank=${networkNovel.description.isNullOrBlank()}, " +
                "genreCount=${networkNovel.getGenres()?.size ?: 0}, manualFetch=$manualFetch"
        }
        updateNovel.awaitUpdateFromSource(
            localNovel = state.novel,
            remoteNovel = networkNovel,
            manualFetch = manualFetch,
        )
        refreshNovelRating(
            state = state,
            forceRefresh = manualFetch,
        )
    }

    private fun refreshNovelRating(
        state: State.Success?,
        forceRefresh: Boolean,
    ) {
        if (state == null) return
        screenModelScope.launchIO {
            val rating = novelRatingFetcher.await(
                source = state.source,
                novel = state.novel,
                forceRefresh = forceRefresh,
            )
            logcat {
                "Resolved novel rating for id=${state.novel.id} source=${state.source.name}, rating=$rating"
            }
            updateSuccessState { current ->
                if (current.novel.id != state.novel.id) current else current.copy(rating = rating)
            }
        }
    }

    private suspend fun fetchChaptersFromSource(
        state: State.Success,
        manualFetch: Boolean,
    ) {
        if (fetchChapterPageIfNeeded(state = state, page = state.chapterPageCurrent, manualFetch = manualFetch)) {
            return
        }

        val sourceChapters = state.source.getChapterList(state.novel.toSNovel())
        logcat {
            "Fetched chapters for id=${state.novel.id} source=${state.source.name}, " +
                "count=${sourceChapters.size}, manualFetch=$manualFetch"
        }
        if (isLikelyWebViewLoginRequired(state.source, state.novel, sourceChapters.size)) {
            logcat(LogPriority.WARN) {
                "Novel ${state.novel.id} (${state.source.name}) likely requires " +
                    "WebView login after fetch: chapters=0, descriptionBlank=true"
            }
        }
        val newChapters = syncNovelChaptersWithSource.await(
            rawSourceChapters = sourceChapters,
            novel = state.novel,
            source = state.source,
            manualFetch = manualFetch,
        )
        logcat(LogPriority.DEBUG) {
            "Synced chapters for id=${state.novel.id} source=${state.source.name}, " +
                "newCount=${newChapters.size}, manualFetch=$manualFetch"
        }
        updateNewChapterIds(
            addedIds = newChapters.asSequence()
                .filterNot { it.read }
                .map { it.id }
                .toList(),
        )
    }

    fun selectChapterPage(page: Int) {
        val state = successState ?: return
        if (!state.chapterPageEnabled) return

        val totalPages = state.chapterPageTotal.coerceAtLeast(1)
        val targetPage = page.coerceIn(1, totalPages)
        if (targetPage == state.chapterPageCurrent && state.chapterPageVisibleUrls.isNotEmpty()) {
            return
        }

        updateSuccessState {
            it.copy(chapterPageLoading = true)
        }

        screenModelScope.launchIO {
            runCatching {
                fetchChapterPageIfNeeded(
                    state = state,
                    page = targetPage,
                    manualFetch = true,
                )
            }.onSuccess { handled ->
                if (!handled) {
                    updateSuccessState { current ->
                        current.copy(chapterPageLoading = false)
                    }
                }
            }.onFailure { error ->
                handleRefreshError(error, successState)
                updateSuccessState { current ->
                    current.copy(chapterPageLoading = false)
                }
            }
        }
    }

    private suspend fun fetchChapterPageIfNeeded(
        state: State.Success,
        page: Int,
        manualFetch: Boolean,
    ): Boolean {
        val source = state.source as? NovelJsSource ?: return false
        if (!source.isJaomixPagedPlugin()) return false

        val pageResult = source.getChapterListPage(
            novel = state.novel.toSNovel(),
            page = page,
        ) ?: run {
            updateSuccessState { current ->
                current.copy(
                    chapterPageEnabled = true,
                    chapterPageLoading = false,
                )
            }
            return true
        }

        val pageChapters = normalizeJaomixPageChapters(pageResult.chapters)
        logcat {
            "Fetched jaomix chapter page for id=${state.novel.id} source=${state.source.name}, " +
                "page=${pageResult.page}/${pageResult.totalPages}, count=${pageChapters.size}, manualFetch=$manualFetch"
        }
        if (isLikelyWebViewLoginRequired(state.source, state.novel, pageChapters.size)) {
            logcat(LogPriority.WARN) {
                "Novel ${state.novel.id} (${state.source.name}) likely requires " +
                    "WebView login after page fetch: page=${pageResult.page}, chapters=0, descriptionBlank=true"
            }
        }

        if (pageChapters.isNotEmpty()) {
            val newChapters = syncNovelChaptersWithSource.await(
                rawSourceChapters = pageChapters,
                novel = state.novel,
                source = state.source,
                manualFetch = manualFetch,
                retainMissingChapters = true,
                sourceOrderOffset = (pageResult.page - 1L) * JAOMIX_PAGE_SOURCE_ORDER_STRIDE,
            )
            updateNewChapterIds(
                addedIds = newChapters.asSequence()
                    .filterNot { it.read }
                    .map { it.id }
                    .toList(),
            )
        }

        updateSuccessState { current ->
            current.copy(
                chapterPageEnabled = true,
                chapterPageCurrent = pageResult.page,
                chapterPageTotal = pageResult.totalPages.coerceAtLeast(1),
                chapterPageLoading = false,
                chapterPageNominalSize = resolveJaomixNominalPageSize(
                    currentNominalSize = current.chapterPageNominalSize,
                    loadedPageSize = pageChapters.size,
                ),
                chapterPageEstimatedTotal = resolveJaomixEstimatedChapterTotal(
                    totalPages = pageResult.totalPages.coerceAtLeast(1),
                    currentPage = pageResult.page,
                    loadedPageSize = pageChapters.size,
                    currentNominalSize = current.chapterPageNominalSize,
                ),
                chapterPageVisibleUrls = if (pageChapters.isNotEmpty()) {
                    pageChapters.mapTo(mutableSetOf()) { chapter -> chapter.url }
                } else {
                    current.chapterPageVisibleUrls
                },
            )
        }

        return true
    }

    private fun NovelSource.isJaomixPagedSource(): Boolean {
        return (this as? NovelJsSource)?.isJaomixPagedPlugin() == true
    }

    private fun normalizeJaomixPageChapters(chapters: List<SNovelChapter>): List<SNovelChapter> {
        if (chapters.size < 2) return chapters

        val hasChapterNumbers = chapters.any { it.chapter_number > 0f }
        return if (hasChapterNumbers) {
            chapters.sortedWith(
                compareBy<SNovelChapter> { it.chapter_number }
                    .thenBy { it.name },
            )
        } else {
            chapters.asReversed()
        }
    }

    private fun resolveJaomixNominalPageSize(
        currentNominalSize: Int,
        loadedPageSize: Int,
    ): Int {
        return maxOf(currentNominalSize, loadedPageSize).coerceAtLeast(0)
    }

    private fun resolveJaomixEstimatedChapterTotal(
        totalPages: Int,
        currentPage: Int,
        loadedPageSize: Int,
        currentNominalSize: Int,
    ): Int {
        val safeTotalPages = totalPages.coerceAtLeast(1)
        val nominalSize = resolveJaomixNominalPageSize(currentNominalSize, loadedPageSize)
        if (nominalSize <= 0) return 0

        return if (currentPage >= safeTotalPages) {
            ((safeTotalPages - 1) * nominalSize + loadedPageSize).coerceAtLeast(loadedPageSize)
        } else {
            (safeTotalPages * nominalSize).coerceAtLeast(loadedPageSize)
        }
    }

    fun toggleChapterRead(chapterId: Long) {
        val chapter = successState?.chapters?.firstOrNull { it.id == chapterId } ?: return
        val newRead = !chapter.read
        val shouldEmitReadEvent = !chapter.read && newRead
        val shouldEmitCompletion = shouldEmitReadEvent &&
            (successState?.chapters?.all { it.read || it.id == chapterId } == true)
        screenModelScope.launchIO {
            novelChapterRepository.updateChapter(
                NovelChapterUpdate(
                    id = chapterId,
                    read = newRead,
                    lastPageRead = if (newRead) 0L else chapter.lastPageRead,
                ),
            )
            if (newRead) {
                updateNewChapterIds(clearedIds = listOf(chapterId))
            }
            if (shouldEmitReadEvent) {
                eventBus?.tryEmit(
                    AchievementEvent.NovelChapterRead(
                        novelId = chapter.novelId,
                        chapterNumber = chapter.chapterNumber.toInt(),
                    ),
                )
                if (shouldEmitCompletion) {
                    eventBus?.tryEmit(AchievementEvent.NovelCompleted(chapter.novelId))
                }
            }
        }
    }

    fun toggleChapterBookmark(chapterId: Long) {
        val chapter = successState?.chapters?.firstOrNull { it.id == chapterId } ?: return
        screenModelScope.launchIO {
            novelChapterRepository.updateChapter(
                NovelChapterUpdate(
                    id = chapterId,
                    bookmark = !chapter.bookmark,
                ),
            )
        }
    }

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.NovelSwipeAction.Disabled]
     */
    fun chapterSwipe(chapterId: Long, swipeAction: LibraryPreferences.NovelSwipeAction) {
        when (swipeAction) {
            LibraryPreferences.NovelSwipeAction.ToggleRead -> toggleChapterRead(chapterId)
            LibraryPreferences.NovelSwipeAction.ToggleBookmark -> toggleChapterBookmark(chapterId)
            LibraryPreferences.NovelSwipeAction.Download -> toggleChapterDownload(chapterId)
            LibraryPreferences.NovelSwipeAction.Disabled -> throw IllegalStateException()
        }
    }

    fun toggleAllChaptersRead() {
        val chapters = successState?.chapters ?: return
        if (chapters.isEmpty()) return
        val markRead = chapters.any { !it.read }
        val chaptersBecomingRead = if (markRead) chapters.filter { !it.read } else emptyList()
        screenModelScope.launchIO {
            novelChapterRepository.updateAllChapters(
                chapters.map {
                    NovelChapterUpdate(
                        id = it.id,
                        read = markRead,
                        lastPageRead = if (markRead) 0L else it.lastPageRead,
                    )
                },
            )
            if (markRead) {
                updateNewChapterIds(clearedIds = chapters.map { it.id })
            }
            if (chaptersBecomingRead.isNotEmpty()) {
                chaptersBecomingRead.forEach { chapter ->
                    eventBus?.tryEmit(
                        AchievementEvent.NovelChapterRead(
                            novelId = chapter.novelId,
                            chapterNumber = chapter.chapterNumber.toInt(),
                        ),
                    )
                }
                eventBus?.tryEmit(AchievementEvent.NovelCompleted(chaptersBecomingRead.first().novelId))
            }
        }
    }

    fun toggleSelection(chapterId: Long) {
        val state = successState ?: return
        val selected = state.selectedChapterIds.contains(chapterId)
        updateSuccessState {
            if (selected) {
                it.copy(selectedChapterIds = it.selectedChapterIds - chapterId)
            } else {
                it.copy(selectedChapterIds = it.selectedChapterIds + chapterId)
            }
        }
    }

    fun toggleAllSelection(selectAll: Boolean) {
        val state = successState ?: return
        updateSuccessState {
            it.copy(
                selectedChapterIds = if (selectAll) {
                    state.processedChapters.mapTo(mutableSetOf()) { c -> c.id }
                } else {
                    emptySet()
                },
            )
        }
    }

    fun invertSelection() {
        val state = successState ?: return
        val allIds = state.processedChapters.mapTo(mutableSetOf()) { it.id }
        val inverted = allIds.apply { removeAll(state.selectedChapterIds) }
        updateSuccessState { it.copy(selectedChapterIds = inverted) }
    }

    fun bookmarkChapters(bookmarked: Boolean) {
        val state = successState ?: return
        val selected = state.selectedChapterIds
        if (selected.isEmpty()) return
        screenModelScope.launchIO {
            novelChapterRepository.updateAllChapters(
                state.chapters
                    .asSequence()
                    .filter { it.id in selected }
                    .filter { it.bookmark != bookmarked }
                    .map { NovelChapterUpdate(id = it.id, bookmark = bookmarked) }
                    .toList(),
            )
            toggleAllSelection(false)
        }
    }

    fun markChaptersRead(markRead: Boolean) {
        val state = successState ?: return
        val selected = state.selectedChapterIds
        if (selected.isEmpty()) return
        val chaptersToMarkRead = if (markRead) {
            state.chapters
                .asSequence()
                .filter { it.id in selected }
                .filter { !it.read }
                .toList()
        } else {
            emptyList()
        }
        val updates = state.chapters
            .asSequence()
            .filter { it.id in selected }
            .filter { it.read != markRead || (!markRead && it.lastPageRead > 0L) }
            .map {
                NovelChapterUpdate(
                    id = it.id,
                    read = markRead,
                    lastPageRead = if (markRead) 0L else it.lastPageRead,
                )
            }
            .toList()
        screenModelScope.launchIO {
            novelChapterRepository.updateAllChapters(updates)
            if (markRead) {
                updateNewChapterIds(clearedIds = selected.toList())
            }
            if (chaptersToMarkRead.isNotEmpty()) {
                chaptersToMarkRead.forEach { chapter ->
                    eventBus?.tryEmit(
                        AchievementEvent.NovelChapterRead(
                            novelId = chapter.novelId,
                            chapterNumber = chapter.chapterNumber.toInt(),
                        ),
                    )
                }
                val markedIds = chaptersToMarkRead.mapTo(hashSetOf()) { it.id }
                val willComplete = state.chapters.all { it.read || it.id in markedIds }
                if (willComplete) {
                    eventBus?.tryEmit(AchievementEvent.NovelCompleted(chaptersToMarkRead.first().novelId))
                }
            }
            toggleAllSelection(false)
        }
    }

    fun markPreviousChapterRead(pointer: NovelChapter) {
        val state = successState ?: return
        val novel = state.novel
        val chapters = state.processedChapters
        val prevChapters = if (novel.sortDescending()) chapters.asReversed() else chapters
        val pointerPos = prevChapters.indexOf(pointer)
        if (pointerPos == -1) return
        val chaptersToMark = prevChapters.take(pointerPos)
        if (chaptersToMark.isEmpty()) return
        val chaptersToAchieve = chaptersToMark.filter { !it.read }
        val updates = chaptersToMark
            .asSequence()
            .filter { it.read != true || it.lastPageRead > 0L }
            .map {
                NovelChapterUpdate(
                    id = it.id,
                    read = true,
                    lastPageRead = 0L,
                )
            }
            .toList()
        screenModelScope.launchIO {
            novelChapterRepository.updateAllChapters(updates)
            updateNewChapterIds(clearedIds = chaptersToMark.map { it.id })
            if (chaptersToAchieve.isNotEmpty()) {
                chaptersToAchieve.forEach { chapter ->
                    eventBus?.tryEmit(
                        AchievementEvent.NovelChapterRead(
                            novelId = chapter.novelId,
                            chapterNumber = chapter.chapterNumber.toInt(),
                        ),
                    )
                }
                val markedIds = chaptersToAchieve.mapTo(hashSetOf()) { it.id }
                val willComplete = state.chapters.all { it.read || it.id in markedIds }
                if (willComplete) {
                    eventBus?.tryEmit(AchievementEvent.NovelCompleted(chaptersToAchieve.first().novelId))
                }
            }
            toggleAllSelection(false)
        }
    }

    fun toggleChapterDownload(chapterId: Long) {
        val state = successState ?: return
        val chapter = state.chapters.firstOrNull { it.id == chapterId } ?: return

        if (chapterId in state.downloadedChapterIds) {
            screenModelScope.launchIO {
                novelDownloadManager.deleteChapter(state.novel, chapterId)
                updateSuccessState {
                    it.copy(downloadedChapterIds = it.downloadedChapterIds - chapterId)
                }
            }
            return
        }

        if (chapterId in state.downloadingChapterIds) {
            NovelDownloadQueueManager.cancelTask(
                novelId = state.novel.id,
                chapterId = chapterId,
            )
            return
        }

        screenModelScope.launchIO {
            val added = enqueueOriginal(state.novel, listOf(chapter))
            notifyQueueStarted(added)
            syncDownloadedState()
        }
    }

    fun downloadSelectedChapters() {
        val state = successState ?: return
        val selectedChapters = state.chapters.filter { chapter ->
            chapter.id in state.selectedChapterIds && chapter.id !in state.downloadedChapterIds
        }
        if (selectedChapters.isEmpty()) return

        screenModelScope.launchIO {
            val added = enqueueOriginal(state.novel, selectedChapters)
            notifyQueueStarted(added)
            toggleAllSelection(false)
            syncDownloadedState()
        }
    }

    fun runDownloadAction(
        action: NovelDownloadAction,
        amount: Int = 0,
    ) {
        val state = successState ?: return
        val chaptersToDownload = selectChaptersForDownload(
            action = action,
            novel = state.novel,
            chapters = state.chapters,
            downloadedChapterIds = state.downloadedChapterIds,
            amount = amount,
        )
        if (chaptersToDownload.isEmpty()) return

        screenModelScope.launchIO {
            val added = enqueueOriginal(state.novel, chaptersToDownload)
            notifyQueueStarted(added)
            syncDownloadedState()
        }
    }

    fun getBatchDownloadCandidates(onlyNotDownloaded: Boolean): List<NovelChapter> {
        val state = successState ?: return emptyList()
        val sortedChapters = state.chapters.sortedWith(Comparator(getNovelChapterSort(state.novel)))
        return if (onlyNotDownloaded) {
            sortedChapters.filter { it.id !in state.downloadedChapterIds }
        } else {
            sortedChapters
        }
    }

    fun runDownloadForChapterIds(chapterIds: Set<Long>): Int {
        if (chapterIds.isEmpty()) return 0
        val state = successState ?: return 0
        val chaptersById = state.chapters.associateBy { it.id }
        val chapters = chapterIds
            .asSequence()
            .mapNotNull { chaptersById[it] }
            .filter { it.id !in state.downloadedChapterIds }
            .toList()
        if (chapters.isEmpty()) return 0

        val added = NovelDownloadQueueManager.enqueueOriginal(state.novel, chapters)
        notifyQueueStarted(added)
        syncDownloadedState()
        return added
    }

    fun runTranslatedDownloadAction(
        action: NovelDownloadAction,
        amount: Int = 0,
        format: NovelTranslatedDownloadFormat,
    ): Int {
        val state = successState ?: return 0
        val chaptersWithCache = state.chapters
            .sortedWith(Comparator(getNovelChapterSort(state.novel)))
            .filter { chapter ->
                novelTranslatedDownloadManager.hasTranslationCache(chapter.id)
            }
        if (chaptersWithCache.isEmpty()) return 0

        val downloadedTranslatedChapterIds = chaptersWithCache
            .asSequence()
            .filter { chapter ->
                novelTranslatedDownloadManager.isTranslatedChapterDownloaded(
                    novel = state.novel,
                    chapter = chapter,
                    format = format,
                )
            }
            .map { it.id }
            .toSet()

        val translatedChapters = selectTranslatedChaptersForDownload(
            action = action,
            novel = state.novel,
            chaptersWithCache = chaptersWithCache,
            downloadedTranslatedChapterIds = downloadedTranslatedChapterIds,
            amount = amount,
        )
        if (translatedChapters.isEmpty()) return 0

        val added = enqueueTranslated(
            state.novel,
            translatedChapters,
            format,
        )
        notifyQueueStarted(added)
        return added
    }

    fun getTranslatedDownloadCandidates(
        format: NovelTranslatedDownloadFormat,
        onlyNotDownloaded: Boolean,
    ): List<NovelChapter> {
        val state = successState ?: return emptyList()
        val chaptersWithCache = state.chapters
            .sortedWith(Comparator(getNovelChapterSort(state.novel)))
            .filter { chapter ->
                novelTranslatedDownloadManager.hasTranslationCache(chapter.id)
            }

        if (!onlyNotDownloaded) return chaptersWithCache

        return chaptersWithCache.filterNot { chapter ->
            novelTranslatedDownloadManager.isTranslatedChapterDownloaded(
                novel = state.novel,
                chapter = chapter,
                format = format,
            )
        }
    }

    fun runTranslatedDownloadForChapterIds(
        chapterIds: Set<Long>,
        format: NovelTranslatedDownloadFormat,
    ): Int {
        if (chapterIds.isEmpty()) return 0
        val state = successState ?: return 0
        val chaptersById = state.chapters.associateBy { it.id }
        val chapters = chapterIds
            .asSequence()
            .mapNotNull { chaptersById[it] }
            .filter { chapter -> novelTranslatedDownloadManager.hasTranslationCache(chapter.id) }
            .toList()
        if (chapters.isEmpty()) return 0

        val added = enqueueTranslated(
            state.novel,
            chapters,
            format,
        )
        notifyQueueStarted(added)
        return added
    }

    fun setTranslatedDownloadFormat(format: NovelTranslatedDownloadFormat) {
        val state = successState ?: return
        val currentFormat = novelReaderPreferences.translatedDownloadFormat(state.novel.id)
        if (currentFormat == format) return

        novelReaderPreferences.setTranslatedDownloadFormat(state.novel.id, format)
        refreshChapterActionStatesAsync(delayMs = 100L)
    }

    fun openTranslatedFolder(chapterId: Long) {
        val state = successState ?: return
        val chapter = state.chapters.find { it.id == chapterId } ?: return
        val format = novelReaderPreferences.translatedDownloadFormat(state.novel.id)

        val file = novelTranslatedDownloadManager.getTranslatedFile(state.novel, chapter, format)
        if (file == null) {
            screenModelScope.launchIO {
                snackbarHostState.showSnackbar(message = "File not found")
            }
            return
        }

        // Get parent folder URI to open the folder containing the file
        val parentFile = file.getParentFile()
        if (parentFile == null) {
            screenModelScope.launchIO {
                snackbarHostState.showSnackbar(message = "Unable to find folder")
            }
            return
        }

        screenModelScope.launchIO {
            openTranslatedFolderChannel.emit(parentFile.uri)
        }
    }

    fun deleteTranslatedChapter(chapterId: Long) {
        val state = successState ?: return
        val chapter = state.chapters.find { it.id == chapterId } ?: return
        val format = novelReaderPreferences.translatedDownloadFormat(state.novel.id)

        novelTranslatedDownloadManager.deleteTranslatedChapter(state.novel, chapter, format)
        screenModelScope.launchIO {
            snackbarHostState.showSnackbar(message = "Downloaded translation deleted")
        }
        refreshChapterActionStatesAsync(delayMs = 100L)
    }

    fun addToTranslationQueue(chapterId: Long) {
        val state = successState ?: return
        screenModelScope.launchIO {
            try {
                translationQueueManager.addToQueue(listOf(chapterId), state.novel.id)
                val appContext = Injekt.get<Application>()
                TranslationJob.runImmediately(appContext)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(message = "Ошибка запуска: ${e.message}")
                return@launchIO
            }
            snackbarHostState.showSnackbar(message = "Перевод добавлен в очередь")
        }
    }

    fun deleteDownloadedSelectedChapters() {
        val state = successState ?: return
        val selectedDownloadedIds = state.selectedChapterIds.intersect(state.downloadedChapterIds)
        if (selectedDownloadedIds.isEmpty()) return

        screenModelScope.launchIO {
            novelDownloadManager.deleteChapters(state.novel, selectedDownloadedIds)
            updateSuccessState {
                it.copy(downloadedChapterIds = it.downloadedChapterIds - selectedDownloadedIds)
            }
            toggleAllSelection(false)
        }
    }

    fun getEpubExportPreferences(): NovelEpubExportPreferencesState {
        return NovelEpubExportPreferencesState(
            destinationTreeUri = novelReaderPreferences.epubExportLocation().get(),
            applyReaderTheme = novelReaderPreferences.epubExportUseReaderTheme().get(),
            includeCustomCss = novelReaderPreferences.epubExportUseCustomCSS().get(),
            includeCustomJs = novelReaderPreferences.epubExportUseCustomJS().get(),
        )
    }

    fun saveEpubExportPreferences(
        destinationTreeUri: String,
        applyReaderTheme: Boolean,
        includeCustomCss: Boolean,
        includeCustomJs: Boolean,
    ) {
        novelReaderPreferences.epubExportLocation().set(destinationTreeUri)
        novelReaderPreferences.epubExportUseReaderTheme().set(applyReaderTheme)
        novelReaderPreferences.epubExportUseCustomCSS().set(includeCustomCss)
        novelReaderPreferences.epubExportUseCustomJS().set(includeCustomJs)
    }

    suspend fun exportAsEpub(
        downloadedOnly: Boolean,
        startChapter: Int?,
        endChapter: Int?,
        destinationTreeUri: String,
        applyReaderTheme: Boolean,
        includeCustomCss: Boolean,
        includeCustomJs: Boolean,
    ): File? {
        val state = successState ?: return null
        val readerSettings = novelReaderPreferences.resolveSettings(state.novel.source)
        val stylesheet = NovelEpubStyleBuilder.buildStylesheet(
            settings = readerSettings,
            sourceId = state.novel.source,
            applyReaderTheme = applyReaderTheme,
            includeCustomCss = includeCustomCss,
            linkColor = "#4A90E2",
        )
        val javaScript = NovelEpubStyleBuilder.buildJavaScript(
            settings = readerSettings,
            novel = state.novel,
            includeCustomJs = includeCustomJs,
        )

        return novelEpubExporter.export(
            novel = state.novel,
            chapters = state.chapters,
            options = NovelEpubExportOptions(
                downloadedOnly = downloadedOnly,
                startChapter = startChapter,
                endChapter = endChapter,
                destinationTreeUri = destinationTreeUri.trim().ifBlank { null },
                stylesheet = stylesheet,
                javaScript = javaScript,
            ),
        )
    }

    fun showSettingsDialog() {
        updateSuccessState { it.copy(dialog = Dialog.SettingsSheet) }
    }

    fun dismissDialog() {
        updateSuccessState { it.copy(dialog = null) }
    }

    fun setUnreadFilter(state: TriState) {
        val novel = successState?.novel ?: return
        val flag = when (state) {
            TriState.DISABLED -> Novel.SHOW_ALL
            TriState.ENABLED_IS -> Novel.CHAPTER_SHOW_UNREAD
            TriState.ENABLED_NOT -> Novel.CHAPTER_SHOW_READ
        }
        screenModelScope.launchIO {
            setNovelChapterFlags.awaitSetUnreadFilter(novel, flag)
        }
    }

    fun setDownloadedFilter(state: TriState) {
        val novel = successState?.novel ?: return
        val flag = when (state) {
            TriState.DISABLED -> Novel.SHOW_ALL
            TriState.ENABLED_IS -> Novel.CHAPTER_SHOW_DOWNLOADED
            TriState.ENABLED_NOT -> Novel.CHAPTER_SHOW_NOT_DOWNLOADED
        }
        screenModelScope.launchIO {
            setNovelChapterFlags.awaitSetDownloadedFilter(novel, flag)
        }
    }

    fun setBookmarkedFilter(state: TriState) {
        val novel = successState?.novel ?: return
        val flag = when (state) {
            TriState.DISABLED -> Novel.SHOW_ALL
            TriState.ENABLED_IS -> Novel.CHAPTER_SHOW_BOOKMARKED
            TriState.ENABLED_NOT -> Novel.CHAPTER_SHOW_NOT_BOOKMARKED
        }
        screenModelScope.launchIO {
            setNovelChapterFlags.awaitSetBookmarkFilter(novel, flag)
        }
    }

    fun selectScanlator(scanlator: String?) {
        val state = successState ?: return
        val availableScanlators = state.availableScanlators
        val excluded = resolveNovelExcludedScanlatorsForSelection(
            selectedScanlator = scanlator,
            availableScanlators = availableScanlators,
        )

        if (excluded == state.excludedScanlators) return
        applyNovelExcludedScanlators(excluded)
    }

    fun setDisplayMode(mode: Long) {
        val novel = successState?.novel ?: return
        screenModelScope.launchIO {
            setNovelChapterFlags.awaitSetDisplayMode(novel, mode)
        }
    }

    fun setSorting(sort: Long) {
        val novel = successState?.novel ?: return
        screenModelScope.launchIO {
            setNovelChapterFlags.awaitSetSortingModeOrFlipOrder(novel, sort)
        }
    }

    fun setCurrentSettingsAsDefault(applyToExisting: Boolean) {
        val novel = successState?.novel ?: return
        screenModelScope.launchNonCancellable {
            libraryPreferences.setNovelChapterSettingsDefault(novel)
            if (applyToExisting) {
                setNovelDefaultChapterFlags.awaitAll()
            }
        }
    }

    fun resetToDefaultSettings() {
        val novel = successState?.novel ?: return
        screenModelScope.launchNonCancellable {
            setNovelDefaultChapterFlags.await(novel)
        }
    }

    private fun observeTrackers() {
        screenModelScope.launchIO {
            combine(
                getTracks.subscribe(novelId).catch { logcat(LogPriority.ERROR, it) },
                trackerManager.loggedInTrackersFlow(),
            ) { novelTracks, loggedInTrackers ->
                val loggedInMangaTrackerIds = loggedInTrackers
                    .asSequence()
                    .filter { it is MangaTracker }
                    .map { it.id }
                    .toSet()
                resolveNovelTrackingSummary(
                    tracks = novelTracks,
                    loggedInMangaTrackerIds = loggedInMangaTrackerIds,
                )
            }
                .flowWithLifecycle(lifecycle)
                .distinctUntilChanged()
                .collectLatest { summary ->
                    updateSuccessState {
                        it.copy(
                            trackingCount = summary.trackingCount,
                            hasLoggedInTrackers = summary.hasLoggedInTrackers,
                        )
                    }
                }
        }
    }

    sealed interface Dialog {
        data object SettingsSheet : Dialog
        data object TrackSheet : Dialog
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Success(
            val novel: Novel,
            val source: NovelSource,
            val rating: Float? = null,
            val chapters: List<NovelChapter>,
            val availableScanlators: Set<String>,
            val scanlatorChapterCounts: Map<String, Int>,
            val excludedScanlators: Set<String>,
            val downloadedOnly: Boolean = false,
            val geminiEnabled: Boolean = false,
            val translatedDownloadFormat: NovelTranslatedDownloadFormat = NovelTranslatedDownloadFormat.TXT,
            val newChapterIds: Set<Long> = emptySet(),
            val isRefreshingData: Boolean,
            val dialog: Dialog?,
            val trackingCount: Int = 0,
            val hasLoggedInTrackers: Boolean = false,
            val selectedChapterIds: Set<Long> = emptySet(),
            val downloadedChapterIds: Set<Long> = emptySet(),
            val downloadingChapterIds: Set<Long> = emptySet(),
            val chapterActionStates: Map<Long, NovelChapterActionUiState> = emptyMap(),
            val chapterPageEnabled: Boolean = false,
            val chapterPageCurrent: Int = 1,
            val chapterPageTotal: Int = 1,
            val chapterPageLoading: Boolean = false,
            val chapterPageEstimatedTotal: Int = 0,
            val chapterPageNominalSize: Int = 0,
            val chapterPageVisibleUrls: Set<String> = emptySet(),
            val resumeChapterId: Long? = null,
            val hasCompletedChapterRefresh: Boolean = false,
            val scrollIndex: Int = 0,
            val scrollOffset: Int = 0,
        ) : State {
            val scanlatorFilterActive: Boolean
                get() = excludedScanlators.intersect(availableScanlators).isNotEmpty()

            val selectedScanlator: String?
                get() = resolveSelectedNovelScanlator(
                    availableScanlators = availableScanlators,
                    excludedScanlators = excludedScanlators,
                )

            val showScanlatorSelector: Boolean
                get() = availableScanlators.size > 1

            val filterActive: Boolean
                get() = scanlatorFilterActive || novel.chaptersFiltered(downloadedOnly)

            val trackingAvailable: Boolean
                get() = trackingCount > 0

            val processedChapters by lazy {
                val chapterSort = Comparator(getNovelChapterSort(novel))
                chapters
                    .asSequence()
                    .filter { chapter ->
                        (
                            !chapterPageEnabled ||
                                chapterPageVisibleUrls.isEmpty() ||
                                chapter.url in chapterPageVisibleUrls
                            ) &&
                            applyFilter(novel.unreadFilter) { !chapter.read } &&
                            applyFilter(novel.effectiveDownloadedFilter(downloadedOnly)) {
                                chapter.id in downloadedChapterIds
                            } &&
                            applyFilter(novel.bookmarkedFilter) { chapter.bookmark }
                    }
                    .sortedWith(chapterSort)
                    .toList()
            }

            val targetChapterIndex by lazy {
                TargetChapterCalculator.calculate(processedChapters) { it.read }
            }
        }
    }

    fun showTrackDialog() {
        updateSuccessState { it.copy(dialog = Dialog.TrackSheet) }
    }

    fun saveScrollPosition(index: Int, offset: Int) {
        updateSuccessState { it.copy(scrollIndex = index, scrollOffset = offset) }
    }

    companion object {
        private const val FAST_CACHE_MAX_ITEMS = 24
        private const val JAOMIX_PAGE_SOURCE_ORDER_STRIDE = 1_000L
        private val stateCache = object : LinkedHashMap<Long, State.Success>(
            FAST_CACHE_MAX_ITEMS + 1,
            1f,
            true,
        ) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, State.Success>?): Boolean {
                return size > FAST_CACHE_MAX_ITEMS
            }
        }

        @Synchronized
        private fun restoreStateFromCache(novelId: Long): State.Success? {
            return stateCache[novelId]
        }

        @Synchronized
        private fun cacheState(state: State.Success?) {
            if (state == null) return
            stateCache[state.novel.id] = state.copy(
                isRefreshingData = false,
                dialog = null,
                selectedChapterIds = emptySet(),
                downloadingChapterIds = emptySet(),
                chapterPageLoading = false,
                scrollIndex = 0,
                scrollOffset = 0,
            )
        }

        internal fun selectChaptersForDownload(
            action: NovelDownloadAction,
            novel: Novel,
            chapters: List<NovelChapter>,
            downloadedChapterIds: Set<Long>,
            amount: Int,
        ): List<NovelChapter> {
            val sortedChapters = chapters.sortedWith(Comparator(getNovelChapterSort(novel)))
            return when (action) {
                NovelDownloadAction.NEXT -> {
                    sortedChapters
                        .asSequence()
                        .filter { !it.read && it.id !in downloadedChapterIds }
                        .let { sequence ->
                            if (amount > 0) sequence.take(amount) else sequence
                        }
                        .toList()
                }
                NovelDownloadAction.UNREAD -> {
                    sortedChapters
                        .filter { !it.read && it.id !in downloadedChapterIds }
                }
                NovelDownloadAction.ALL -> {
                    sortedChapters
                        .filter { it.id !in downloadedChapterIds }
                }
                NovelDownloadAction.NOT_DOWNLOADED -> {
                    sortedChapters
                        .filter { it.id !in downloadedChapterIds }
                }
            }
        }

        internal fun selectTranslatedChaptersForDownload(
            action: NovelDownloadAction,
            novel: Novel,
            chaptersWithCache: List<NovelChapter>,
            downloadedTranslatedChapterIds: Set<Long>,
            amount: Int,
        ): List<NovelChapter> {
            val sortedChapters = chaptersWithCache.sortedWith(Comparator(getNovelChapterSort(novel)))
            val notDownloadedChapters = sortedChapters.filter { it.id !in downloadedTranslatedChapterIds }
            return when (action) {
                NovelDownloadAction.NEXT -> {
                    val sequence = notDownloadedChapters.asSequence()
                    if (amount > 0) sequence.take(amount).toList() else sequence.toList()
                }
                NovelDownloadAction.UNREAD -> {
                    notDownloadedChapters.filter { !it.read }
                }
                NovelDownloadAction.ALL -> {
                    sortedChapters
                }
                NovelDownloadAction.NOT_DOWNLOADED -> {
                    notDownloadedChapters
                }
            }
        }
    }
}

@Immutable
internal data class NovelTrackingSummary(
    val trackingCount: Int,
    val hasLoggedInTrackers: Boolean,
)

internal fun resolveNovelTrackingSummary(
    tracks: List<NovelTrack>,
    loggedInMangaTrackerIds: Set<Long>,
): NovelTrackingSummary {
    val trackingCount = tracks.count { it.trackerId in loggedInMangaTrackerIds }
    return NovelTrackingSummary(
        trackingCount = trackingCount,
        hasLoggedInTrackers = loggedInMangaTrackerIds.isNotEmpty(),
    )
}

internal fun resolveDefaultNovelExcludedScanlatorsByChapterCount(
    scanlatorChapterCounts: Map<String, Int>,
    availableScanlators: Set<String>,
    excludedScanlators: Set<String>,
): Set<String>? {
    val normalizedAvailable = availableScanlators
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
    if (normalizedAvailable.size < 2) return emptySet()

    val normalizedCounts = scanlatorChapterCounts
        .asSequence()
        .mapNotNull { (scanlator, count) ->
            scanlator.trim().takeIf { it.isNotEmpty() }?.let { it to count }
        }
        .filter { (scanlator, _) -> scanlator in normalizedAvailable }
        .toMap()
    if (normalizedCounts.size < 2) return emptySet()

    val currentSelection = resolveSelectedNovelScanlator(
        availableScanlators = normalizedAvailable,
        excludedScanlators = excludedScanlators,
    )
    if (currentSelection != null && currentSelection in normalizedCounts) return null

    val selectedScanlator = normalizedCounts.entries
        .sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.key },
        )
        .firstOrNull()
        ?.key
        ?: return emptySet()

    return normalizedAvailable - selectedScanlator
}

internal fun resolveDeferredDefaultNovelExcludedScanlators(
    shouldAttemptAutoSelection: Boolean,
    storedExcludedScanlators: Set<String>,
    availableScanlators: Set<String>,
    scanlatorChapterCounts: Map<String, Int>,
): Set<String>? {
    if (!shouldAttemptAutoSelection) return null
    return resolveDefaultNovelExcludedScanlatorsByChapterCount(
        scanlatorChapterCounts = scanlatorChapterCounts,
        availableScanlators = availableScanlators,
        excludedScanlators = storedExcludedScanlators,
    )
}

internal fun resolveSelectedNovelScanlator(
    availableScanlators: Set<String>,
    excludedScanlators: Set<String>,
): String? {
    if (availableScanlators.isEmpty()) return null
    val effectiveExcluded = excludedScanlators.intersect(availableScanlators)
    val included = availableScanlators - effectiveExcluded
    return included.singleOrNull()
}

internal fun resolveNovelExcludedScanlatorsForSelection(
    selectedScanlator: String?,
    availableScanlators: Set<String>,
): Set<String> {
    val selection = selectedScanlator?.trim().orEmpty()
    if (selection.isEmpty()) return emptySet()
    val normalizedAvailable = availableScanlators
        .asSequence()
        .map { scanlator -> scanlator.trim() }
        .filter { scanlator -> scanlator.isNotEmpty() }
        .toSet()
    if (selection !in normalizedAvailable) return emptySet()
    return normalizedAvailable - selection
}

internal fun resolveNovelRefreshErrorMessage(
    error: Throwable,
    likelyWebViewLoginRequired: Boolean,
): String? {
    val isConnectivityLikeError = error.message?.contains("Could not reach", ignoreCase = true) == true
    if (likelyWebViewLoginRequired && (error is NoChaptersException || isConnectivityLikeError)) {
        return null
    }
    return when (error) {
        is NoChaptersException -> "No chapters found"
        else -> error.message ?: "Failed to refresh"
    }
}

@Immutable
internal sealed interface NovelChapterDisplayRow {
    @Immutable
    data class BranchChapter(
        val chapter: NovelChapter,
        val displayNumber: Int,
    ) : NovelChapterDisplayRow

    @Immutable
    data class ChapterGroup(
        val chapterNumber: Double,
        val displayNumber: Int,
        val chapters: List<NovelChapter>,
    ) : NovelChapterDisplayRow {
        val groupKey: Long = chapterNumber.toBits()
    }

    @Immutable
    data class ChapterVariant(
        val chapter: NovelChapter,
        val displayNumber: Int,
    ) : NovelChapterDisplayRow
}

@Immutable
internal data class NovelChapterDisplayData(
    val chapterGroups: List<NovelChapterDisplayRow.ChapterGroup>,
    val displayRows: List<NovelChapterDisplayRow>,
)

internal fun resolveNovelBranchChapterRows(
    chapters: List<NovelChapter>,
): List<NovelChapterDisplayRow.BranchChapter> {
    return chapters.mapIndexed { index, chapter ->
        NovelChapterDisplayRow.BranchChapter(
            chapter = chapter,
            displayNumber = index + 1,
        )
    }
}

private fun resolveNovelChapterGroups(
    chapters: List<NovelChapter>,
): List<NovelChapterDisplayRow.ChapterGroup> {
    return chapters
        .groupBy { it.chapterNumber }
        .values
        .mapIndexed { index, groupedChapters ->
            val sortedVariants = groupedChapters.sortedWith(
                compareBy<NovelChapter> { it.sourceOrder }
                    .thenBy { it.scanlator.orEmpty() }
                    .thenBy { it.id },
            )
            NovelChapterDisplayRow.ChapterGroup(
                chapterNumber = sortedVariants.first().chapterNumber,
                displayNumber = index + 1,
                chapters = sortedVariants,
            )
        }
}

internal fun resolveNovelChapterDisplayData(
    chapters: List<NovelChapter>,
    groupedByChapter: Boolean,
    expandedGroupKeys: Set<Long>,
): NovelChapterDisplayData {
    if (!groupedByChapter) {
        val displayRows = resolveNovelBranchChapterRows(chapters)
        return NovelChapterDisplayData(
            chapterGroups = emptyList(),
            displayRows = displayRows,
        )
    }

    val chapterGroups = resolveNovelChapterGroups(chapters)
    val displayRows = buildList {
        chapterGroups.forEach { group ->
            add(group)
            if (group.groupKey in expandedGroupKeys) {
                group.chapters.forEach { chapter ->
                    add(
                        NovelChapterDisplayRow.ChapterVariant(
                            chapter = chapter,
                            displayNumber = group.displayNumber,
                        ),
                    )
                }
            }
        }
    }

    return NovelChapterDisplayData(
        chapterGroups = chapterGroups,
        displayRows = displayRows,
    )
}

internal fun resolveNovelGroupedChapterRows(
    chapters: List<NovelChapter>,
    expandedGroupKeys: Set<Long>,
): List<NovelChapterDisplayRow> {
    return resolveNovelChapterDisplayData(
        chapters = chapters,
        groupedByChapter = true,
        expandedGroupKeys = expandedGroupKeys,
    ).displayRows
}

internal fun resolveNovelVisibleChapterRows(
    rows: List<NovelChapterDisplayRow>,
    visibleTopLevelCount: Int,
    groupedByChapter: Boolean,
): List<NovelChapterDisplayRow> {
    if (visibleTopLevelCount <= 0) return emptyList()
    if (!groupedByChapter) {
        return rows.take(visibleTopLevelCount)
    }

    return buildList {
        var visibleGroups = 0
        rows.forEach { row ->
            when (row) {
                is NovelChapterDisplayRow.BranchChapter -> {
                    if (visibleGroups >= visibleTopLevelCount) return@buildList
                    add(row)
                }
                is NovelChapterDisplayRow.ChapterGroup -> {
                    if (visibleGroups >= visibleTopLevelCount) return@buildList
                    visibleGroups++
                    add(row)
                }
                is NovelChapterDisplayRow.ChapterVariant -> {
                    if (visibleGroups in 1..visibleTopLevelCount) {
                        add(row)
                    }
                }
            }
        }
    }
}

internal fun resolveNovelChapterRowCount(
    chapters: List<NovelChapter>,
    expandedGroupKeys: Set<Long>,
    groupedByChapter: Boolean,
): Int {
    if (!groupedByChapter) return chapters.size
    return resolveNovelGroupedChapterRows(chapters, expandedGroupKeys).size
}

internal fun resolveNovelChapterRowIndex(
    rows: List<NovelChapterDisplayRow>,
    targetChapterId: Long,
): Int {
    return rows.indexOfFirst { row ->
        when (row) {
            is NovelChapterDisplayRow.BranchChapter -> row.chapter.id == targetChapterId
            is NovelChapterDisplayRow.ChapterGroup -> row.chapters.any { it.id == targetChapterId }
            is NovelChapterDisplayRow.ChapterVariant -> row.chapter.id == targetChapterId
        }
    }
}

internal fun resolveNovelChapterRowIndex(
    chapters: List<NovelChapter>,
    expandedGroupKeys: Set<Long>,
    groupedByChapter: Boolean,
    targetChapterId: Long,
): Int {
    val rows = resolveNovelChapterDisplayData(
        chapters = chapters,
        groupedByChapter = groupedByChapter,
        expandedGroupKeys = expandedGroupKeys,
    ).displayRows
    return resolveNovelChapterRowIndex(
        rows = rows,
        targetChapterId = targetChapterId,
    )
}

internal fun resolveNovelChapterGroupKey(chapterNumber: Double): Long {
    return chapterNumber.toBits()
}
