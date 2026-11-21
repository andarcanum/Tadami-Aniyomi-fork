package eu.kanade.tachiyomi.ui.reader

import android.app.Application
import android.net.ConnectivityManager
import android.net.Uri
import androidx.annotation.IntRange
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.manga.interactor.SetMangaViewerFlags
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.entries.manga.model.readerOrientation
import eu.kanade.domain.entries.manga.model.readingMode
import eu.kanade.domain.items.chapter.model.toDbChapter
import eu.kanade.domain.source.interactor.ForegroundIncognitoState
import eu.kanade.domain.source.manga.interactor.GetMangaIncognitoState
import eu.kanade.domain.track.manga.interactor.TrackChapter
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.presentation.reader.manga.MangaSeriesInterstitialState
import eu.kanade.presentation.reader.manga.resolveMangaSeriesInterstitialState
import eu.kanade.tachiyomi.data.database.models.manga.Chapter
import eu.kanade.tachiyomi.data.database.models.manga.isRecognizedNumber
import eu.kanade.tachiyomi.data.database.models.manga.toDomainChapter
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadProvider
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.loader.DownloadPageLoader
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.PageRatioTracker
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderFinaleState
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.model.daysOnShelf
import eu.kanade.tachiyomi.ui.reader.model.shouldCelebrateFinale
import eu.kanade.tachiyomi.ui.reader.model.shouldRecordCompletion
import eu.kanade.tachiyomi.ui.reader.setting.MangaReaderPageDimensions
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.setting.isLikelyWebtoonFromPageDimensions
import eu.kanade.tachiyomi.ui.reader.setting.recommendReadingModeForMangaFormat
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPagePrewarmer
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import eu.kanade.tachiyomi.util.chapter.filterDownloadedChapters
import eu.kanade.tachiyomi.util.chapter.removeDuplicates
import eu.kanade.tachiyomi.util.editCover
import eu.kanade.tachiyomi.util.lang.byteSize
import eu.kanade.tachiyomi.util.lang.takeBytes
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.cacheImageDir
import eu.kanade.tachiyomi.util.system.connectivityManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.common.preference.toggle
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.domain.history.manga.interactor.GetNextChapters
import tachiyomi.domain.history.manga.interactor.UpsertMangaHistory
import tachiyomi.domain.history.manga.model.MangaHistoryUpdate
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.items.chapter.model.ChapterUpdate
import tachiyomi.domain.items.chapter.service.getChapterSort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.series.manga.interactor.GetMangaSeriesWithEntries
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.source.local.entries.manga.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.DateFormat
import java.time.Instant
import java.util.Date
import java.util.concurrent.atomic.AtomicLong

/**
 * Presenter used by the activity to perform background operations.
 */
class ReaderViewModel @JvmOverloads constructor(
    private val savedState: SavedStateHandle,
    private val sourceManager: MangaSourceManager = Injekt.get(),
    private val downloadManager: MangaDownloadManager = Injekt.get(),
    private val downloadProvider: MangaDownloadProvider = Injekt.get(),
    private val imageSaver: ImageSaver = Injekt.get(),
    val readerPreferences: ReaderPreferences = Injekt.get(),
    private val basePreferences: BasePreferences = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
    private val trackPreferences: TrackPreferences = Injekt.get(),
    private val trackChapter: TrackChapter = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val getMangaSeriesWithEntries: GetMangaSeriesWithEntries = Injekt.get(),
    private val getNextChapters: GetNextChapters = Injekt.get(),
    private val upsertHistory: UpsertMangaHistory = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val setMangaViewerFlags: SetMangaViewerFlags = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val getIncognitoState: GetMangaIncognitoState = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val eventBus: AchievementEventBus = Injekt.get(),
    private val activityDataRepository: ActivityDataRepository = Injekt.get(),
    private val discoveryRepository: tachiyomi.domain.discovery.repository.DiscoveryRepository = Injekt.get(),
) : ViewModel() {

    /**
     * Taste Engine: «просмотрено» — открытие читалки помечает тайтл consumed
     * (нейтральный вес 0: вкус не трогает, тайтл уходит из ленты «Для вас»).
     * Хук на открытие, не на запись истории — работает и в инкогнито.
     */
    private fun markDiscoveryConsumed(title: String, sourceId: Long) {
        viewModelScope.launchIO {
            eu.kanade.tachiyomi.data.discovery.TasteSignalRecorder.recordConsumed(
                repository = discoveryRepository,
                mediaType = tachiyomi.domain.discovery.model.DiscoveryMediaType.MANGA,
                title = title,
                sourceId = sourceId,
                enrichmentScope = this,
            )
        }
    }

    private val mutableState = MutableStateFlow(
        State(
            autoScrollSpeed = resolveInitialAutoScrollSpeed(readerPreferences),
        ),
    )
    val state = mutableState.asStateFlow()

    // BUFFERED, not rendezvous: trySend(ReloadViewerChapters/PageChanged) silently dropped when
    // the main-thread collector was momentarily busy (e.g. mid-scroll), leaving transition pages
    // stuck on a spinner; NonCancellable send() after the activity died suspended forever.
    private val eventChannel = Channel<Event>(Channel.BUFFERED)
    val eventFlow = eventChannel.receiveAsFlow()

    private val autoWebtoonPageIndexes = mutableSetOf<Int>()
    private val autoWebtoonPageDimensions = mutableListOf<MangaReaderPageDimensions>()
    private var autoWebtoonPromptedMangaId: Long? = null

    /**
     * Cross-chapter page-ratio median for this reading session: estimates placeholder heights in
     * chapters where nothing is known yet (right after crossing into a not-yet-downloaded
     * chapter). Main thread only.
     */
    private val sessionPageRatios = PageRatioTracker()

    val sessionTypicalPageRatio: Float?
        get() = sessionPageRatios.typical

    fun noteSessionPageRatio(ratio: Float) {
        sessionPageRatios.note(ratio)
    }

    private val pagePrewarmer = ReaderPagePrewarmer(
        scope = viewModelScope,
        onDimensionsSniffed = { page, ratio ->
            page.chapter.notePageRatio(ratio)
            noteSessionPageRatio(ratio)
        },
    )
    private var foregroundIncognitoJob: Job? = null
    private var initJob: Job? = null
    private var chapterTransitionJob: Job? = null
    private val chapterLoadGeneration = AtomicLong(0L)

    /**
     * The manga loaded in the reader. It can be null when instantiated for a short time.
     */
    val manga: Manga?
        get() = state.value.manga

    /**
     * The chapter id of the currently loaded chapter. Used to restore from process kill.
     */
    private var chapterId = savedState.get<Long>("chapter_id") ?: -1L
        set(value) {
            savedState["chapter_id"] = value
            field = value
        }

    /**
     * The visible page index of the currently loaded chapter. Used to restore from process kill.
     */
    private var chapterPageIndex = savedState.get<Int>("page_index") ?: -1
        set(value) {
            savedState["page_index"] = value
            field = value
        }

    /**
     * The chapter loader for the loaded manga. It'll be null until [manga] is set.
     */
    private var loader: ChapterLoader? = null

    /**
     * Thread-safe session timer for chapter reading duration.
     * Snapshots elapsed time synchronously on flush/switch so detached IO writes
     * never race with restartReadTimer() or clear the incoming chapter's timer.
     */
    private val readSessionTimer = ReaderReadSessionTimer()
    private var seriesId: Long? = null
    private var seriesInterstitialState: MangaSeriesInterstitialState? = null
    private var seriesInterstitialShownForChapterId: Long? = null
    private var finaleShownForMangaId: Long? = null
    private var pendingFinaleState: ReaderFinaleState? = null

    private var chapterToDownload: MangaDownload? = null

    private val speedTracker = ReadingSpeedTracker()

    /**
     * Hoisted out of onPageSelected: an Injekt lookup + service fetch ran on the main thread for
     * every single page turn.
     */
    private val connectivityManager: ConnectivityManager by lazy {
        Injekt.get<Application>().connectivityManager
    }

    /**
     * Serializes all last_page_read DB writes (page-select, webtoon scroll flush, mode-switch
     * flush) so concurrent detached writers cannot interleave; waiting lockers are served in
     * lock-acquisition order (launch dispatch order is not strictly guaranteed).
     */
    private val progressWriteMutex = Mutex()
    private val latestPageProgressWriteSeq = AtomicLong(0L)

    private var downloadAheadTriggeredForChapterId: Long? = null

    /**
     * Chapters for which a ReloadViewerChapters event was already sent after they became Loaded
     * (see [preload]).
     */
    private var reloadSentForChapterId: Long? = null

    private val chapterListLock = Any()
    private var cachedFullChapterList: List<ReaderChapter>? = null
    private var cachedChapterList: List<ReaderChapter>? = null

    /**
     * Full chapter list for gap detection. This intentionally ignores reader skip filters, so
     * chapters hidden by skip-read or skip-filtered are not treated as missing chapters.
     */
    private val fullChapterList: List<ReaderChapter>
        get() = synchronized(chapterListLock) {
            cachedFullChapterList ?: buildFullChapterList().also { cachedFullChapterList = it }
        }

    private fun getFullChapterListOrNull(): List<ReaderChapter>? = synchronized(chapterListLock) {
        cachedFullChapterList ?: manga?.let { buildFullChapterList(it).also { list -> cachedFullChapterList = list } }
    }

    private fun buildFullChapterList(targetManga: Manga = manga!!): List<ReaderChapter> {
        return runBlocking { getChaptersByMangaId.await(targetManga.id, applyScanlatorFilter = true) }
            .sortedWith(getChapterSort(targetManga, sortDescending = false))
            .map { it.toDbChapter() }
            .map(::ReaderChapter)
    }

    /**
     * Chapter list for the active manga. It's retrieved lazily and should be accessed for the first
     * time in a background thread to avoid blocking the UI.
     */
    private val chapterList: List<ReaderChapter>
        get() = synchronized(chapterListLock) {
            cachedChapterList ?: buildChapterList().also { cachedChapterList = it }
        }

    private fun getChapterListOrNull(): List<ReaderChapter>? = synchronized(chapterListLock) {
        cachedChapterList ?: manga?.let {
            buildChapterList(it, chapterId).also { list -> cachedChapterList = list }
        }
    }

    private fun buildChapterList(
        targetManga: Manga = manga!!,
        targetChapterId: Long = chapterId,
    ): List<ReaderChapter> {
        val chapters = runBlocking { getChaptersByMangaId.await(targetManga.id, applyScanlatorFilter = true) }

        val selectedChapter = chapters.find { it.id == targetChapterId }
            ?: error("Requested chapter of id $targetChapterId not found in chapter list")

        val chaptersForReader = when {
            (readerPreferences.skipRead().get() || readerPreferences.skipFiltered().get()) -> {
                val filteredChapters = chapters.filterNot {
                    when {
                        readerPreferences.skipRead().get() && it.read -> true
                        readerPreferences.skipFiltered().get() -> {
                            (targetManga.unreadFilterRaw == Manga.CHAPTER_SHOW_READ && !it.read) ||
                                (targetManga.unreadFilterRaw == Manga.CHAPTER_SHOW_UNREAD && it.read) ||
                                (
                                    targetManga.downloadedFilterRaw ==
                                        Manga.CHAPTER_SHOW_DOWNLOADED &&
                                        !downloadManager.isChapterDownloaded(
                                            it.name,
                                            it.scanlator,
                                            targetManga.title,
                                            targetManga.source,
                                            mangaId = targetManga.id,
                                            chapterId = it.id,
                                        )
                                    ) ||
                                (
                                    targetManga.downloadedFilterRaw ==
                                        Manga.CHAPTER_SHOW_NOT_DOWNLOADED &&
                                        downloadManager.isChapterDownloaded(
                                            it.name,
                                            it.scanlator,
                                            targetManga.title,
                                            targetManga.source,
                                            mangaId = targetManga.id,
                                            chapterId = it.id,
                                        )
                                    ) ||
                                (targetManga.bookmarkedFilterRaw == Manga.CHAPTER_SHOW_BOOKMARKED && !it.bookmark) ||
                                (targetManga.bookmarkedFilterRaw == Manga.CHAPTER_SHOW_NOT_BOOKMARKED && it.bookmark)
                        }
                        else -> false
                    }
                }

                if (filteredChapters.any { it.id == targetChapterId }) {
                    filteredChapters
                } else {
                    filteredChapters + listOf(selectedChapter)
                }
            }
            else -> chapters
        }

        return chaptersForReader
            .sortedWith(getChapterSort(targetManga, sortDescending = false))
            .run {
                if (basePreferences.downloadedOnly().get()) {
                    filterDownloadedChapters(targetManga)
                } else {
                    this
                }
            }
            .run {
                if (readerPreferences.skipDupe().get()) {
                    removeDuplicates(selectedChapter)
                } else {
                    this
                }
            }
            .map { it.toDbChapter() }
            .map(::ReaderChapter)
    }

    private fun shouldPauseHistory(): Boolean {
        return getIncognitoState.shouldPauseHistory(manga?.source, manga?.favorite == true)
    }

    // Read on demand: a construction-time snapshot ignored preference changes until the reader
    // was recreated.
    private val downloadAheadAmount
        get() = downloadPreferences.autoDownloadWhileReading().get()

    // A-LOW: read on the main thread and cleared from detached IO flushes - make the handoff
    // visibility-safe across threads.
    @Volatile
    private var pendingWebtoonProgress: PendingWebtoonProgress? = null
    private var webtoonProgressSaveJob: Job? = null

    init {
        // To save state
        state.map { it.viewerChapters?.currChapter }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach { currentChapter ->
                flushPendingWebtoonScrollProgress()
                if (chapterPageIndex >= 0) {
                    // Restore from SavedState
                    currentChapter.requestedPage = chapterPageIndex
                    // A-LOW (process kill): the px offset within the page was dropped here even
                    // though the stored progress carries it (webtoon long pages) - reopening
                    // after a process death landed at the TOP of the restored page. Reuse the
                    // stored offset when it belongs to the same page index.
                    val storedProgress = decodeStoredChapterProgress(
                        value = currentChapter.chapter.last_page_read,
                        restoreOffset = readerPreferences.saveLongPagePosition().get(),
                    )
                    if (storedProgress.index == chapterPageIndex) {
                        currentChapter.requestedPageOffset = storedProgress.offsetPx
                        currentChapter.requestedPageOffsetRatioPpm = storedProgress.offsetRatioPpm
                    } else {
                        currentChapter.requestedPageOffset = 0
                        currentChapter.requestedPageOffsetRatioPpm = null
                    }
                } else if (shouldRestoreSavedProgress(
                        currentChapter,
                        readerPreferences.preserveReadingPosition().get(),
                    )
                ) {
                    applySavedProgress(currentChapter)
                }
                // The SavedState restore above is ONE-SHOT (process death). Without this reset
                // the branch re-fired on every subsequent chapter change, stamping the previous
                // chapter's page index onto the new chapter and making applySavedProgress /
                // preserve-position unreachable after the first chapter.
                chapterPageIndex = -1
                chapterId = currentChapter.chapter.id!!
            }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        initJob?.cancel()
        initJob = null
        chapterTransitionJob?.cancel()
        chapterTransitionJob = null
        pagePrewarmer.cancel()
        foregroundIncognitoJob?.cancel()
        ForegroundIncognitoState.set(this, false)
        webtoonProgressSaveJob?.cancel()
        flushPendingWebtoonScrollProgress()
        if (readSessionTimer.currentStartTimeMs() != null) {
            flushReadTimer()
        }

        val currentChapters = state.value.viewerChapters
        if (currentChapters != null) {
            currentChapters.unref()
            chapterToDownload?.let {
                downloadManager.addDownloadsToStartOfQueue(listOf(it))
            }
        }
    }

    /**
     * Resets active session state so a `singleTask` [ReaderActivity] reused via `onNewIntent`
     * can cleanly re-initialize for another manga/chapter. Must be called on the main thread.
     */
    fun resetForNewIntent() {
        chapterLoadGeneration.incrementAndGet()
        initJob?.cancel()
        initJob = null
        chapterTransitionJob?.cancel()
        chapterTransitionJob = null
        pagePrewarmer.cancel()
        foregroundIncognitoJob?.cancel()
        foregroundIncognitoJob = null
        ForegroundIncognitoState.set(this, false)
        webtoonProgressSaveJob?.cancel()
        webtoonProgressSaveJob = null
        flushPendingWebtoonScrollProgress()
        if (readSessionTimer.currentStartTimeMs() != null) {
            flushReadTimer()
        }

        var oldChapters: ViewerChapters? = null
        mutableState.update {
            oldChapters = it.viewerChapters
            it.copy(
                sessionId = it.sessionId + 1L,
                manga = null,
                viewerChapters = null,
                chapterList = emptyList(),
                bookmarked = false,
                isLoadingAdjacentChapter = false,
                currentPage = -1,
                estimatedMinutesLeft = null,
                dialog = null,
                menuVisible = false,
                seriesInterstitialState = null,
                finaleState = null,
                autoScrollEnabled = false,
                isAutoScrollExpanded = false,
            )
        }
        oldChapters?.unref()
        chapterToDownload?.let {
            downloadManager.addDownloadsToStartOfQueue(listOf(it))
        }
        chapterToDownload = null

        synchronized(chapterListLock) {
            cachedFullChapterList = null
            cachedChapterList = null
        }
        loader = null
        seriesId = null
        chapterId = -1L
        chapterPageIndex = -1
        seriesInterstitialState = null
        seriesInterstitialShownForChapterId = null
        finaleShownForMangaId = null
        pendingFinaleState = null
        downloadAheadTriggeredForChapterId = null
        reloadSentForChapterId = null
        autoWebtoonPromptedMangaId = null
        resetAutoWebtoonPageDetection()
    }

    /**
     * Called when the user pressed the back button and is going to leave the reader. Used to
     * trigger deletion of the downloaded chapters.
     */
    fun onActivityFinish() {
        flushPendingWebtoonScrollProgress()
        deletePendingChapters()
    }

    fun saveWebtoonScrollProgressOnExit(viewer: WebtoonViewer) {
        if (!shouldHandleLongPageProgress()) return

        viewer.getCurrentScrollProgress()?.let { progress ->
            onWebtoonScrollProgressChanged(progress, flushImmediately = true)
        }
        flushPendingWebtoonScrollProgress()
    }

    internal fun onWebtoonScrollProgressChanged(
        progress: WebtoonScrollProgress,
        flushImmediately: Boolean = false,
    ) {
        if (!shouldTrackWebtoonChapterProgress()) return

        val currentChapter = getCurrentChapter() ?: return
        val chapterId = currentChapter.chapter.id ?: return
        if (progress.chapterId != null && progress.chapterId != chapterId) return
        val chapterKey = currentChapter.chapter.url.takeIf { it.isNotBlank() }
        val pages = currentChapter.pages ?: return
        if (pages.isEmpty()) return

        val pageIndex = progress.index.coerceIn(0, pages.lastIndex)
        val offsetPx = progress.offsetPx.coerceAtLeast(0)
        val encodedProgress = encodeWebtoonScrollProgress(
            index = pageIndex,
            offsetPx = offsetPx,
            pageHeightPx = progress.pageHeightPx,
            totalPages = pages.size,
        )
        val decodedProgress = decodeStoredChapterProgress(encodedProgress, restoreOffset = true)

        currentChapter.requestedPage = decodedProgress.index
        currentChapter.requestedPageOffset = decodedProgress.offsetPx
        currentChapter.requestedPageOffsetRatioPpm = decodedProgress.offsetRatioPpm
        chapterPageIndex = pageIndex

        // Incognito pauses PERSISTENCE, not live position tracking: the in-memory seed above is
        // what a viewer recreation (rotation) restores from, and applySavedProgress already
        // relies on scroll tracking to keep the live position when history is paused. With the
        // early return at the top of this function every rotation landed at the page top.
        if (shouldPauseHistory()) return

        currentChapter.chapter.last_page_read = encodedProgress

        pendingWebtoonProgress = PendingWebtoonProgress(
            chapterId = chapterId,
            chapterKey = chapterKey,
            encodedProgress = encodedProgress,
        )

        if (flushImmediately) {
            webtoonProgressSaveJob?.cancel()
            flushPendingWebtoonScrollProgress()
        } else {
            scheduleWebtoonProgressFlush()
        }
    }

    private fun scheduleWebtoonProgressFlush() {
        webtoonProgressSaveJob?.cancel()
        webtoonProgressSaveJob = viewModelScope.launchIO {
            delay(WEBTOON_PROGRESS_SAVE_DEBOUNCE_MILLIS)
            flushPendingWebtoonScrollProgress()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun flushPendingWebtoonScrollProgress() {
        val pending = pendingWebtoonProgress ?: return
        pendingWebtoonProgress = null

        // `read` is intentionally NOT written here: the pending snapshot captured it on the main
        // thread while updateChapterProgressOnComplete flips read=true on an IO coroutine, and the
        // debounced flush landed AFTER that write - reverting freshly completed chapters to unread
        // (webtoon fling to the very bottom). The completion/page-change paths own the read flag;
        // the flush owns only the px-precision position. Detached scope (GlobalScope launchIO):
        // onCleared runs after viewModelScope is closed, so a child launch here never dispatched
        // and the final progress write was silently dropped on system destroy.
        //
        // A-LOW (progress churn): the pref-cache read/write (a JSON-backed per-chapter map,
        // string parsing on access) ran synchronously on the MAIN thread on every flush (chapter
        // change, pause, finish); it moved into the same detached IO block as the DB write.
        launchIO {
            writeWebtoonProgressSnapshot(pending)
        }
    }

    /**
     * Suspends until any pending debounced webtoon flush is written. Used by the reading-mode
     * switch, which must remove the long-page cache entry AFTER the flush - not race it.
     */
    private suspend fun flushPendingWebtoonScrollProgressNow() {
        webtoonProgressSaveJob?.cancel()
        webtoonProgressSaveJob = null
        val pending = pendingWebtoonProgress ?: return
        pendingWebtoonProgress = null
        writeWebtoonProgressSnapshot(pending)
    }

    private suspend fun writeWebtoonProgressSnapshot(pending: PendingWebtoonProgress) {
        progressWriteMutex.withLock {
            if (readerPreferences.saveLongPagePosition().get()) {
                val saved = readerPreferences.getLongPageProgressForChapter(
                    chapterId = pending.chapterId,
                    chapterKey = pending.chapterKey,
                )
                if (saved != pending.encodedProgress) {
                    readerPreferences.putLongPageProgressForChapter(
                        chapterId = pending.chapterId,
                        encodedProgress = pending.encodedProgress,
                        chapterKey = pending.chapterKey,
                    )
                }
            }
            updateChapter.await(
                ChapterUpdate(
                    id = pending.chapterId,
                    lastPageRead = pending.encodedProgress,
                ),
            )
        }
    }

    private fun observeForegroundIncognito(sourceId: Long?) {
        foregroundIncognitoJob?.cancel()
        var lastShouldPause: Boolean? = null
        foregroundIncognitoJob = viewModelScope.launch {
            getIncognitoState.subscribe(sourceId).collect { active ->
                ForegroundIncognitoState.set(this@ReaderViewModel, active)
                val currentShouldPause = shouldPauseHistory()
                if (lastShouldPause != null && lastShouldPause != currentShouldPause) {
                    if (currentShouldPause) {
                        readSessionTimer.consumeAndStop()
                    } else {
                        readSessionTimer.restart()
                    }
                }
                lastShouldPause = currentShouldPause
            }
        }
    }

    /**
     * Whether this presenter is initialized yet.
     */
    fun needsInit(): Boolean {
        return manga == null
    }

    /**
     * Launches reader initialization on [viewModelScope] so configuration changes mid-load cannot
     * cancel the initial chapter load and leave the recreated activity stuck on a spinner.
     */
    fun startInit(
        mangaId: Long,
        initialChapterId: Long,
        seriesId: Long? = null,
    ) {
        if (!needsInit() || initJob?.isActive == true) return
        val generation = chapterLoadGeneration.incrementAndGet()
        initJob = viewModelScope.launch {
            val result = initInternal(mangaId, initialChapterId, seriesId, generation)
            if (!result.getOrDefault(false) && chapterLoadGeneration.get() == generation) {
                val exception = result.exceptionOrNull() ?: IllegalStateException("Unknown err")
                eventChannel.send(Event.InitialLoadError(exception))
            }
        }
    }

    /**
     * Initializes this presenter with the given [mangaId] and [initialChapterId]. This method will
     * fetch the manga from the database and initialize the initial chapter.
     */
    suspend fun init(
        mangaId: Long,
        initialChapterId: Long,
        seriesId: Long? = null,
    ): Result<Boolean> {
        if (!needsInit()) return Result.success(true)
        val generation = chapterLoadGeneration.incrementAndGet()
        return initInternal(mangaId, initialChapterId, seriesId, generation)
    }

    private suspend fun initInternal(
        mangaId: Long,
        initialChapterId: Long,
        seriesId: Long?,
        generation: Long,
    ): Result<Boolean> {
        if (!needsInit()) return Result.success(true)
        return withIOContext {
            try {
                val loadedManga = getManga.await(mangaId)
                if (loadedManga != null) {
                    if (chapterLoadGeneration.get() != generation) {
                        return@withIOContext Result.success(true)
                    }
                    sourceManager.isInitialized.first { it }
                    if (chapterLoadGeneration.get() != generation) {
                        return@withIOContext Result.success(true)
                    }
                    val targetChapterId = if (chapterId == -1L) initialChapterId else chapterId
                    val context = Injekt.get<Application>()
                    val source = sourceManager.getOrStub(loadedManga.source)
                    val localLoader = ChapterLoader(context, downloadManager, downloadProvider, loadedManga, source)
                    val builtChapters = buildChapterList(loadedManga, targetChapterId)
                    val builtFullChapters = buildFullChapterList(loadedManga)
                    if (chapterLoadGeneration.get() != generation) {
                        return@withIOContext Result.success(true)
                    }

                    val committed = withUIContext {
                        if (chapterLoadGeneration.get() != generation) {
                            return@withUIContext false
                        }
                        this@ReaderViewModel.seriesId = seriesId
                        this@ReaderViewModel.chapterId = targetChapterId
                        this@ReaderViewModel.loader = localLoader
                        synchronized(chapterListLock) {
                            cachedChapterList = builtChapters
                            cachedFullChapterList = builtFullChapters
                        }
                        mutableState.update { it.copy(manga = loadedManga) }
                        observeForegroundIncognito(loadedManga.source)
                        // Taste Engine: «просмотрено» — нейтральное исключение из ленты
                        // «Для тебя»; хук на открытие читалки работает и в инкогнито.
                        markDiscoveryConsumed(loadedManga.title, loadedManga.source)
                        true
                    }
                    if (!committed) {
                        return@withIOContext Result.success(true)
                    }

                    val initialChapter = builtChapters.find { targetChapterId == it.chapter.id }
                        ?: error(
                            "Initial chapter $targetChapterId is missing from the chapter list " +
                                "(filtered out by downloaded-only/skip filters?)",
                        )
                    loadChapter(
                        loader = localLoader,
                        chapter = initialChapter,
                        expectedGeneration = generation,
                        chaptersSnapshot = builtChapters,
                        fullChaptersSnapshot = builtFullChapters,
                    )
                    Result.success(true)
                } else {
                    // Unlikely but okay
                    Result.success(false)
                }
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                Result.failure(e)
            }
        }
    }

    /**
     * Loads the given [chapter] with this [loader] and updates the currently active chapters.
     * Callers must handle errors.
     */
    private suspend fun loadChapter(
        loader: ChapterLoader,
        chapter: ReaderChapter,
        expectedGeneration: Long? = null,
        chaptersSnapshot: List<ReaderChapter>? = null,
        fullChaptersSnapshot: List<ReaderChapter>? = null,
    ): ViewerChapters? {
        if (expectedGeneration != null && chapterLoadGeneration.get() != expectedGeneration) {
            return null
        }
        loader.loadChapter(chapter)

        val currentChapters = chaptersSnapshot ?: getChapterListOrNull()
        val currentFullChapters = fullChaptersSnapshot ?: getFullChapterListOrNull()
        val newChapters = if (currentChapters != null && currentFullChapters != null) {
            val chapterPos = currentChapters.indexOf(chapter)
            ViewerChapters(
                chapter,
                currentChapters.getOrNull(chapterPos - 1),
                currentChapters.getOrNull(chapterPos + 1),
                currentFullChapters,
            )
        } else {
            null
        }

        return withUIContext {
            if ((expectedGeneration != null && chapterLoadGeneration.get() != expectedGeneration) ||
                newChapters == null ||
                currentChapters == null
            ) {
                // Recycle any PageLoader opened by loader.loadChapter(chapter) when unreferenced.
                chapter.ref()
                chapter.unref()
                return@withUIContext null
            }
            swapViewerChaptersState(
                stateFlow = mutableState,
                newChapters = newChapters,
                chapterList = currentChapters,
            ) { previousChapters ->
                previousChapters?.unref()
            }
            chapterToDownload = cancelQueuedDownloads(newChapters.currChapter)
            newChapters
        }
    }

    /**
     * Called when the user changed to the given [chapter] when changing pages from the viewer.
     * It's used only to set this chapter as active.
     */
    private fun loadNewChapter(chapter: ReaderChapter) {
        val loader = loader ?: return

        // Snapshot outgoing chapter duration and start the incoming chapter's timer synchronously
        // before launching IO work so getCurrentChapter() still points to the outgoing chapter and
        // onPause() cannot interleave before restartReadTimer().
        flushReadTimer()
        restartReadTimer()

        val generation = chapterLoadGeneration.incrementAndGet()
        chapterTransitionJob?.cancel()
        chapterTransitionJob = viewModelScope.launchIO {
            logcat { "Loading ${chapter.chapter.url}" }

            try {
                loadChapter(loader, chapter, expectedGeneration = generation)
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    /**
     * Called when the user is going to load the prev/next chapter through the toolbar buttons.
     * Returns true only when the chapter became active (see [loadNextChapter]).
     */
    private suspend fun loadAdjacent(chapter: ReaderChapter): Boolean {
        // WEBTOON-ARROWS: re-entrancy guard - the progress dialog is shown asynchronously, so a
        // fast double tap used to start two overlapping adjacent switches.
        if (state.value.isLoadingAdjacentChapter) return false
        val loader = loader ?: return false

        logcat { "Loading adjacent ${chapter.chapter.url}" }

        val generation = chapterLoadGeneration.incrementAndGet()
        chapterTransitionJob?.cancel()
        chapterTransitionJob = null

        mutableState.update { it.copy(isLoadingAdjacentChapter = true) }
        return try {
            prepareAdjacentChapterSwitch(::flushReadTimer, ::restartReadTimer)
            val loaded = withIOContext {
                loadChapter(loader, chapter, expectedGeneration = generation)
            }
            loaded != null
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            // WEBTOON-ARROWS (H2): the failure is reported to the caller instead of only being
            // logged - the activity no longer re-anchors the viewer on a failed switch.
            logcat(LogPriority.ERROR, e)
            false
        } finally {
            mutableState.update { it.copy(isLoadingAdjacentChapter = false) }
        }
    }

    /**
     * Called when the viewers decide it's a good time to preload a [chapter] and improve the UX so
     * that the user doesn't have to wait too long to continue reading.
     */
    suspend fun preload(chapter: ReaderChapter) {
        if (chapter.state is ReaderChapter.State.Loaded) {
            // Already loaded by a racing call: the viewer still needs the rebuild event or the
            // transition page waits forever (the old rendezvous channel could drop it).
            // Once per chapter: setChapters ends with checkAndPreload, which re-requests this
            // same loaded chapter - without the dedup the event loop rebuilds the viewer
            // endlessly while the user sits near the chapter end.
            val cid = chapter.chapter.id
            if (reloadSentForChapterId != cid) {
                reloadSentForChapterId = cid
                eventChannel.trySend(Event.ReloadViewerChapters)
            }
            return
        }
        if (chapter.state == ReaderChapter.State.Loading) {
            // The in-flight load sends ReloadViewerChapters when it completes.
            return
        }

        if (chapter.pageLoader?.isLocal == false) {
            val manga = manga ?: return
            val dbChapter = chapter.chapter
            val isDownloaded = downloadManager.isChapterDownloaded(
                dbChapter.name,
                dbChapter.scanlator,
                manga.title,
                manga.source,
                skipCache = true,
                mangaId = manga.id,
                chapterId = dbChapter.id,
            )
            if (isDownloaded) {
                chapter.state = ReaderChapter.State.Wait
            }
        }

        if (chapter.state != ReaderChapter.State.Wait && chapter.state !is ReaderChapter.State.Error) {
            return
        }

        val loader = loader ?: return
        try {
            logcat { "Preloading ${chapter.chapter.url}" }
            loader.loadChapter(chapter)
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            return
        }
        reloadSentForChapterId = chapter.chapter.id
        pagePrewarmer.prewarmChapterLead(chapter.pages, isCropBordersEnabled())
        eventChannel.trySend(Event.ReloadViewerChapters)
    }

    fun onViewerLoaded(viewer: Viewer?) {
        resetAutoWebtoonPageDetection()
        mutableState.update {
            it.copy(viewer = viewer)
        }
    }

    /**
     * Called every time a page changes on the reader. Used to mark the flag of chapters being
     * read, update tracking services, enqueue downloaded chapter deletion, and updating the active chapter if this
     * [page]'s chapter is different from the currently active.
     */
    fun onPageSelected(page: ReaderPage) {
        // InsertPage doesn't change page progress
        if (page is InsertPage) {
            return
        }

        // Track reading speed and update dynamic preloading
        speedTracker.addPageTransition(System.currentTimeMillis())
        val isMetered = connectivityManager.isActiveNetworkMetered
        val bufferSize = calculatePreloadBufferSize(speedTracker.getAverageSpeedSeconds(), isMetered)
        ReaderPreloadManager.dynamicPreloadPagesAfter = bufferSize

        val selectedChapter = page.chapter
        val pages = selectedChapter.pages ?: return

        // Update UI page state and in-memory chapter seed synchronously so the navigator
        // slider and page counter never crawl behind queued SQLite progress transactions.
        val pageIndex = page.index
        val averageSpeed = speedTracker.getAverageSpeedSeconds()
        val estimatedMinutes = if (averageSpeed != null) {
            val remainingPages = pages.size - (pageIndex + 1)
            if (remainingPages > 0) {
                (remainingPages * averageSpeed / 60.0).toInt()
            } else {
                null
            }
        } else {
            null
        }
        mutableState.update {
            it.copy(
                currentPage = pageIndex + 1,
                estimatedMinutesLeft = estimatedMinutes,
            )
        }
        // Reset the intra-page offset only on an actual page turn. On a viewer recreation
        // (rotation) onPageSelected fires for the page being restored; keeping the seed update
        // synchronous prevents an IO coroutine from racing the main-thread restore settle.
        if (selectedChapter.requestedPage != pageIndex) {
            selectedChapter.requestedPageOffset = 0
            selectedChapter.requestedPageOffsetRatioPpm = null
        }
        selectedChapter.requestedPage = pageIndex
        chapterPageIndex = pageIndex

        // Save last page read and mark as read if needed. Serialized with every other
        // last_page_read writer through progressWriteMutex and coalesced so rapid slider scrubs
        // do not queue dozens of stale SQLite writes behind the mutex.
        val writeSeq = latestPageProgressWriteSeq.incrementAndGet()
        val isChapterCompletion = pages.lastIndex == pageIndex
        viewModelScope.launchNonCancellable {
            progressWriteMutex.withLock {
                if (!isChapterCompletion && latestPageProgressWriteSeq.get() != writeSeq) {
                    return@withLock
                }
                updateChapterProgress(selectedChapter, page)
            }
        }

        if (selectedChapter != getCurrentChapter()) {
            logcat { "Setting ${selectedChapter.chapter.url} as active" }
            loadNewChapter(selectedChapter)
        }

        val inDownloadRange = page.number.toDouble() / pages.size > 0.25
        // At most one disk-checked download-ahead per chapter; the cheap in-memory gates below
        // stay per-page. The trigger is consumed only once the async "next is downloaded" gate
        // passes, so a still-downloading next chapter is retried on the following pages.
        if (inDownloadRange && downloadAheadTriggeredForChapterId != selectedChapter.chapter.id) {
            downloadNextChapters(selectedChapter.chapter.id)
        }

        val nextChapterPages = state.value.viewerChapters?.nextChapter?.pages
        pagePrewarmer.prewarmAround(
            currentPage = page,
            currentPages = pages,
            nextChapterPages = nextChapterPages,
            cropBorders = isCropBordersEnabled(),
        )

        eventChannel.trySend(Event.PageChanged)
    }

    private fun isCropBordersEnabled(): Boolean {
        return if (ReadingMode.isPagerType(getMangaReadingMode())) {
            readerPreferences.cropBorders().get()
        } else {
            readerPreferences.cropBordersWebtoon().get()
        }
    }

    private fun downloadNextChapters(triggerChapterId: Long?) {
        if (downloadAheadAmount == 0) return
        val manga = manga ?: return

        // Only download ahead if current + next chapter is already downloaded too to avoid jank
        if (getCurrentChapter()?.pageLoader !is DownloadPageLoader) return
        val nextChapter = state.value.viewerChapters?.nextChapter?.chapter ?: return

        viewModelScope.launchIO {
            val isNextChapterDownloaded = downloadManager.isChapterDownloaded(
                nextChapter.name,
                nextChapter.scanlator,
                manga.title,
                manga.source,
                mangaId = manga.id,
                chapterId = nextChapter.id,
            )
            if (!isNextChapterDownloaded) return@launchIO
            downloadAheadTriggeredForChapterId = triggerChapterId

            val chaptersToDownload = getNextChapters.await(manga.id, nextChapter.id!!)
                .run {
                    if (readerPreferences.skipDupe().get()) {
                        removeDuplicates(nextChapter.toDomainChapter()!!)
                    } else {
                        this
                    }
                }
                .take(downloadAheadAmount)
            downloadManager.downloadChapters(
                manga,
                chaptersToDownload,
            )
        }
    }

    /**
     * Removes [currentChapter] from download queue
     * if setting is enabled and [currentChapter] is queued for download
     */
    private fun cancelQueuedDownloads(currentChapter: ReaderChapter): MangaDownload? {
        return downloadManager.getQueuedDownloadOrNull(currentChapter.chapter.id!!)?.also {
            downloadManager.cancelQueuedDownloads(listOf(it))
        }
    }

    /**
     * Determines if deleting option is enabled and nth to last chapter actually exists.
     * If both conditions are satisfied enqueues chapter for delete
     * @param currentChapter current chapter, which is going to be marked as read.
     */
    private fun deleteChapterIfNeeded(currentChapter: ReaderChapter, orderedChapters: List<Chapter>) {
        val removeAfterReadSlots = downloadPreferences.removeAfterReadSlots().get()
        if (removeAfterReadSlots == -1) return

        // Determine which chapter should be deleted and enqueue. Positions come from the FULL
        // ordered DB snapshot (id-based): the filtered chapterList used to target the wrong
        // chapter whenever skip filters were active, and fullChapterList holds distinct instances
        // with stale read flags.
        val currentPosition = orderedChapters.indexOfFirst { it.id == currentChapter.chapter.id }
        val chapterToDelete = if (currentPosition >= 0) {
            orderedChapters.getOrNull(currentPosition - removeAfterReadSlots)
        } else {
            null
        }

        // If chapter is completely read, no need to download it
        chapterToDownload = null

        if (chapterToDelete != null) {
            enqueueDeleteReadChapters(chapterToDelete)
        }
    }

    /**
     * Saves the chapter progress (last read page and whether it's read)
     * if incognito mode isn't on.
     */
    private suspend fun updateChapterProgress(readerChapter: ReaderChapter, page: Page) {
        val pageIndex = page.index
        val totalPages = readerChapter.pages?.size ?: 0

        if (!shouldPauseHistory() && page.status != Page.State.ERROR) {
            readerChapter.chapter.last_page_read = if (shouldHandleLongPageProgress() || totalPages <= 0) {
                pageIndex.toLong()
            } else {
                encodePagedChapterProgress(
                    index = pageIndex,
                    totalPages = totalPages,
                )
            }

            if (readerChapter.pages?.lastIndex == pageIndex) {
                updateChapterProgressOnComplete(readerChapter)
            }

            updateChapter.await(
                ChapterUpdate(
                    id = readerChapter.chapter.id!!,
                    read = readerChapter.chapter.read,
                    lastPageRead = readerChapter.chapter.last_page_read,
                ),
            )
        }
    }

    private suspend fun updateChapterProgressOnComplete(readerChapter: ReaderChapter) {
        val chapterWasUnread = !readerChapter.chapter.read
        readerChapter.chapter.read = true
        updateTrackChapterRead(readerChapter)

        // Fresh FULL chapter snapshot from the DB in reading order, with the just-finished chapter
        // forced read (its own DB write happens after this function returns). `chapterList` is
        // filtered (skipRead/skipFiltered/downloadedOnly/dedupe) and its sibling instances carry
        // stale in-memory read flags, so completion, duplicate-marking and delete-after-read all
        // used to target the wrong set: finishing the last VISIBLE chapter declared the whole
        // entry completed (event, completedAt keepsake, THE END plate) while unread chapters were
        // merely hidden by a filter.
        val currentManga = manga
        val currentChapterId = readerChapter.chapter.id
        val completionChapters = if (currentManga != null) {
            withIOContext {
                getChaptersByMangaId.await(currentManga.id, applyScanlatorFilter = true)
            }
                .sortedWith(getChapterSort(currentManga, sortDescending = false))
                .map { dbChapter ->
                    dbChapter.toDbChapter().also { converted ->
                        if (dbChapter.id == currentChapterId) converted.read = true
                    }
                }
        } else {
            emptyList()
        }

        deleteChapterIfNeeded(readerChapter, completionChapters)
        maybeShowSeriesInterstitial(readerChapter)

        // Emit ChapterRead event for achievement tracking. Gated on the unread->read transition:
        // re-reaching the last page of an already-read chapter re-emitted the event on every visit
        // (achievement rules recompute from the DB, so the event is only a trigger - but the
        // activity log below accumulates blindly).
        val mangaId = currentManga?.id ?: return
        if (chapterWasUnread) {
            eventBus.tryEmit(
                AchievementEvent.ChapterRead(
                    mangaId = mangaId,
                    chapterNumber = readerChapter.chapter.chapter_number.toInt(),
                ),
            )
        }

        runCatching {
            if (eu.kanade.domain.easteregg.aurora.AuroraNight.isVeilThin()) {
                val manager = Injekt.get<eu.kanade.domain.easteregg.aurora.AuroraHeartManager>()
                manager.registerNightAction()
            }
        }

        // Record reading activity for stats. Session duration is flushed atomically in
        // updateHistory() on chapter switch and pause so mid-chapter sessions count, re-reads
        // still log time, and chapter completion never double-counts or reads a cleared timer.
        val chapterId = readerChapter.chapter.id ?: 0L
        if (chapterId > 0L && chapterWasUnread) {
            activityDataRepository.recordReading(
                id = chapterId,
                chaptersCount = 1,
                durationMs = 0L,
            )
        }

        // Check for manga completion against the full snapshot.
        if (completionChapters.isNotEmpty() && completionChapters.all { it.read }) {
            // Event-driven rules (complete_1_manga, crybaby) unlock purely from this event, so it
            // is gated on the entry's own (possibly custom) COMPLETED status: an exhausted ONGOING
            // series must not count as a completed manga.
            if (currentManga.displayStatus == SManga.COMPLETED.toLong()) {
                eventBus.tryEmit(AchievementEvent.MangaCompleted(mangaId))
            }
            recordCompletionIfNeeded(readerChapter, completionChapters)
            maybeShowFinale(readerChapter, completionChapters, chapterWasUnread)
        }

        val markDuplicateAsRead = libraryPreferences.markDuplicateReadChapterAsRead().get()
            .contains(LibraryPreferences.MARK_DUPLICATE_CHAPTER_READ_EXISTING)
        if (!markDuplicateAsRead) return

        // Duplicates by chapter number are searched in the FULL snapshot as well: the dedupe
        // filters (skipDupe) remove exactly those duplicates from
        // chapterList, so the feature was dead whenever dedupe was active.
        val duplicateUnreadChapters = completionChapters
            .filter {
                !it.read &&
                    it.isRecognizedNumber &&
                    it.chapter_number == readerChapter.chapter.chapter_number
            }
            .map { ChapterUpdate(id = it.id!!, read = true) }
        updateChapter.awaitAll(duplicateUnreadChapters)
    }

    private fun setSeriesInterstitialState(value: MangaSeriesInterstitialState?) {
        seriesInterstitialState = value
        mutableState.update {
            it.copy(seriesInterstitialState = value)
        }
    }

    fun clearSeriesInterstitial() {
        setSeriesInterstitialState(null)
    }

    private suspend fun resolveSeriesInterstitialState(
        chapter: ReaderChapter,
    ): MangaSeriesInterstitialState? {
        val targetSeriesId = seriesId ?: return null
        val currentManga = manga ?: return null
        val wrapper = getMangaSeriesWithEntries.subscribe(targetSeriesId).first() ?: return null
        val chaptersByManga = withIOContext {
            wrapper.series.entries.map { entry ->
                entry to getChaptersByMangaId.await(entry.id, applyScanlatorFilter = true)
            }
        }
        val currentChapter = chapter.chapter.toDomainChapter() ?: return null
        return resolveMangaSeriesInterstitialState(
            series = wrapper.series,
            currentManga = currentManga,
            currentChapter = currentChapter,
            chaptersByManga = chaptersByManga,
        )
    }

    private fun maybeShowSeriesInterstitial(chapter: ReaderChapter) {
        if (seriesId == null) return
        if (seriesInterstitialState != null) return
        val chapterId = chapter.chapter.id ?: return
        // Full list, id-based: chapterList is filtered (the interstitial fired at the last
        // VISIBLE chapter), and fullChapterList holds distinct ReaderChapter instances so the
        // identity-based indexOf would always miss there.
        val fullList = fullChapterList
        val chapterIndex = fullList.indexOfFirst { it.chapter.id == chapterId }
        if (chapterIndex < 0 || chapterIndex != fullList.lastIndex) return
        if (seriesInterstitialShownForChapterId == chapterId) return
        seriesInterstitialShownForChapterId = chapterId
        viewModelScope.launchIO {
            val resolved = resolveSeriesInterstitialState(chapter) ?: return@launchIO
            setSeriesInterstitialState(resolved)
        }
    }

    /**
     * Persists the keepsake completion timestamp (title-screen finished stamp; travels through
     * backups). Independent of the plate preference. Refreshed whenever the final chapter of a
     * completed entry is end-read again.
     */
    private fun recordCompletionIfNeeded(readerChapter: ReaderChapter, chapters: List<Chapter>) {
        val currentManga = manga ?: return
        if (!shouldRecordCompletion(
                currentManga,
                chapters,
                finishedChapterIsLast = readerChapter.chapter.id == chapters.lastOrNull()?.id,
            )
        ) {
            return
        }
        val timestamp = System.currentTimeMillis()
        viewModelScope.launchNonCancellable {
            updateManga.await(MangaUpdate(id = currentManga.id, completedAt = timestamp))
        }
    }

    /**
     * Prepares the one-time «THE END» plate when the final chapter of a truly completed manga
     * gets read (docs/plans/2026-08-02_reader_finale.md). The plate is not shown immediately —
     * that would steal the last page — it waits pending until the end-of-manga transition is
     * reached ([revealPendingFinale]). A multi-work series continuation takes precedence: if the
     * reader is about to be sent to the next work, the interstitial owns that ending and the
     * plate silently steps aside. The plate is also rendered with priority over the interstitial,
     * so the empty "last work" branch never stacks two modals.
     */
    private fun maybeShowFinale(
        readerChapter: ReaderChapter,
        allChapters: List<Chapter>,
        chapterWasUnread: Boolean,
    ) {
        val currentManga = manga ?: return
        if (!shouldCelebrateFinale(
                manga = currentManga,
                chapters = allChapters,
                chapterWasUnread = chapterWasUnread,
                cardEnabled = readerPreferences.showFinaleCard().get(),
                alreadyShownForManga = finaleShownForMangaId == currentManga.id,
            )
        ) {
            return
        }
        finaleShownForMangaId = currentManga.id
        viewModelScope.launchIO {
            if (seriesId != null) {
                val interstitial = resolveSeriesInterstitialState(readerChapter)
                if (interstitial?.nextManga != null) return@launchIO
            }
            pendingFinaleState = ReaderFinaleState(
                title = currentManga.displayTitle,
                coverData = currentManga,
                chapterCount = allChapters.size,
                daysOnShelf = daysOnShelf(currentManga.dateAdded),
                finishedOn = DateFormat.getDateInstance(DateFormat.SHORT).format(Date()),
                nightVeil = eu.kanade.domain.easteregg.aurora.AuroraNight.isVeilThin(),
            )
        }
    }

    /**
     * Called by the viewers when the end-of-manga transition becomes active (swipe past the
     * last page of the final chapter): that is the moment the finale plate is revealed.
     */
    fun revealPendingFinale() {
        val pending = pendingFinaleState ?: return
        pendingFinaleState = null
        setFinaleState(pending)
    }

    private fun setFinaleState(value: ReaderFinaleState?) {
        mutableState.update { it.copy(finaleState = value) }
    }

    fun clearFinale() = setFinaleState(null)

    fun restartReadTimer() {
        readSessionTimer.restart()
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun flushReadTimer() {
        val snapshot = readSessionTimer.consumeAndStop()
        val chapter = getCurrentChapter() ?: return
        // Detached: called from the activity finish/destroy path where viewModelScope may
        // already be closed - the child launch never dispatched and the history entry was
        // lost (launchNonCancellable only protects an already-started coroutine).
        launchIO {
            updateHistory(chapter, snapshot)
        }
    }

    /**
     * Saves the chapter last read history if incognito mode isn't on.
     */
    private suspend fun updateHistory(
        readerChapter: ReaderChapter,
        snapshot: ReadSessionSnapshot,
    ) {
        if (shouldPauseHistory()) return

        val chapterId = readerChapter.chapter.id ?: return
        val readAt = Date(snapshot.readAtMs)

        upsertHistory.await(MangaHistoryUpdate(chapterId, readAt, snapshot.durationMs))

        if (snapshot.durationMs > 0L && chapterId > 0L) {
            activityDataRepository.recordReading(
                id = chapterId,
                chaptersCount = 0,
                durationMs = snapshot.durationMs,
            )
        }
    }

    /**
     * Called from the activity to load and set the next chapter as active.
     *
     * WEBTOON-ARROWS (H2): returns whether the chapter ACTUALLY became active - the activity
     * must only re-anchor the viewer in that case. The old silent `return` (no next chapter,
     * null loader, swallowed load error) still let the activity run moveToPageIndex(0), which
     * scrolled the CURRENT chapter to its start ("the chapter didn't switch, it rewound to the
     * beginning").
     */
    suspend fun loadNextChapter(): Boolean {
        val nextChapter = state.value.viewerChapters?.nextChapter ?: return false
        return loadAdjacent(nextChapter)
    }

    /**
     * Called from the activity to load and set the previous chapter as active.
     * See [loadNextChapter] for the Boolean contract.
     */
    suspend fun loadPreviousChapter(): Boolean {
        val prevChapter = state.value.viewerChapters?.prevChapter ?: return false
        return loadAdjacent(prevChapter)
    }

    /**
     * Called from the chapter list sheet to load and set the selected chapter as active.
     */
    suspend fun jumpToChapter(chapterId: Long) {
        val targetChapter = chapterList.firstOrNull { it.chapter.id == chapterId } ?: return
        if (targetChapter == getCurrentChapter()) return
        loadAdjacent(targetChapter)
    }

    /**
     * Downloads the selected chapter from the chapter list sheet.
     */
    suspend fun downloadChapter(chapterId: Long) {
        val manga = manga ?: return
        val chapter = chapterList.firstOrNull { it.chapter.id == chapterId } ?: return

        withIOContext {
            downloadManager.downloadChapters(
                manga,
                listOf(chapter.chapter.toDomainChapter()!!),
            )
        }
    }

    /**
     * Returns the currently active chapter.
     */
    private fun getCurrentChapter(): ReaderChapter? {
        return state.value.currentChapter
    }

    fun getSource() = manga?.source?.let { sourceManager.getOrStub(it) } as? HttpSource

    fun getChapterUrl(): String? {
        val sChapter = getCurrentChapter()?.chapter ?: return null
        val source = getSource() ?: return null

        return try {
            source.getChapterUrl(sChapter)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    /**
     * Bookmarks the currently active chapter.
     */
    fun toggleChapterBookmark() {
        val chapter = getCurrentChapter()?.chapter ?: return
        val bookmarked = !chapter.bookmark
        chapter.bookmark = bookmarked

        viewModelScope.launchNonCancellable {
            updateChapter.await(
                ChapterUpdate(
                    id = chapter.id!!,
                    bookmark = bookmarked,
                ),
            )
        }

        mutableState.update {
            it.copy(
                bookmarked = bookmarked,
            )
        }
    }

    /**
     * Returns the viewer position used by this manga or the default one.
     */
    fun getMangaReadingMode(resolveDefault: Boolean = true): Int {
        val default = readerPreferences.defaultReadingMode().get()
        val manga = manga
        val readingMode = ReadingMode.fromPreference(manga?.readingMode?.toInt())
        return when {
            resolveDefault && readingMode == ReadingMode.DEFAULT ->
                getAutoWebtoonReadingMode(manga) ?: default
            else -> manga?.readingMode?.toInt() ?: default
        }
    }

    fun isMangaReadingModeAutoWebtoon(): Boolean {
        val manga = manga ?: return false
        return getMangaReadingMode() == ReadingMode.WEBTOON.flagValue &&
            getAutoWebtoonReadingMode(manga) == ReadingMode.WEBTOON.flagValue
    }

    private fun getAutoWebtoonReadingMode(manga: Manga?): Int? {
        manga ?: return null
        if (!readerPreferences.useAutoWebtoon().get()) return null
        if (ReadingMode.fromPreference(manga.readingMode.toInt()) != ReadingMode.DEFAULT) return null

        val sourceName = sourceManager.getOrStub(manga.source).name
        return recommendReadingModeForMangaFormat(
            manga = manga,
            sourceName = sourceName,
        )
    }

    fun onReaderPageImageDimensionsAvailable(
        page: ReaderPage,
        width: Int,
        height: Int,
    ) {
        if (!shouldDetectAutoWebtoonFromPageDimensions()) return
        if (!autoWebtoonPageIndexes.add(page.index)) return

        autoWebtoonPageDimensions += MangaReaderPageDimensions(width = width, height = height)
        val mangaId = manga?.id ?: return
        if (
            isLikelyWebtoonFromPageDimensions(autoWebtoonPageDimensions) &&
            autoWebtoonPromptedMangaId != mangaId &&
            state.value.dialog == null
        ) {
            autoWebtoonPromptedMangaId = mangaId
            mutableState.update { it.copy(dialog = Dialog.AutoWebtoonModeSuggestion) }
        }
    }

    fun acceptAutoWebtoonModeSuggestion() {
        closeDialog()
        setMangaReadingMode(ReadingMode.WEBTOON)
    }

    fun dismissAutoWebtoonModeSuggestion() {
        val mangaId = manga?.id?.toString() ?: return closeDialog()
        val preference = readerPreferences.autoWebtoonPromptDismissedMangaIds()
        preference.set(preference.get() + mangaId)
        closeDialog()
    }

    private fun shouldDetectAutoWebtoonFromPageDimensions(): Boolean {
        val manga = manga ?: return false
        if (!readerPreferences.useAutoWebtoon().get()) return false
        if (ReadingMode.fromPreference(manga.readingMode.toInt()) != ReadingMode.DEFAULT) return false
        if (getAutoWebtoonReadingMode(manga) == ReadingMode.WEBTOON.flagValue) return false
        if (readerPreferences.autoWebtoonPromptDismissedMangaIds().get().contains(manga.id.toString())) return false
        return true
    }

    private fun resetAutoWebtoonPageDetection() {
        autoWebtoonPageIndexes.clear()
        autoWebtoonPageDimensions.clear()
    }

    /**
     * True when this series has a non-default reading mode and/or orientation
     * (series override is active). Mirrors novel "override for source" scope,
     * but only for viewer flags stored on the manga entry.
     */
    fun isSeriesViewerOverrideEnabled(): Boolean {
        val manga = manga ?: return false
        val mode = ReadingMode.fromPreference(manga.readingMode.toInt())
        val orientation = ReaderOrientation.fromPreference(manga.readerOrientation.toInt())
        return mode != ReadingMode.DEFAULT || orientation != ReaderOrientation.DEFAULT
    }

    /**
     * Enable series-specific viewer flags (seeded from current globals) or clear
     * them back to DEFAULT so global defaults apply.
     */
    fun setSeriesViewerOverrideEnabled(enabled: Boolean) {
        val manga = manga ?: return
        if (enabled) {
            val mode = ReadingMode.fromPreference(manga.readingMode.toInt())
            val orientation = ReaderOrientation.fromPreference(manga.readerOrientation.toInt())
            if (mode == ReadingMode.DEFAULT) {
                val globalMode = ReadingMode.fromPreference(readerPreferences.defaultReadingMode().get())
                if (globalMode != ReadingMode.DEFAULT) {
                    setMangaReadingMode(globalMode)
                } else {
                    setMangaReadingMode(ReadingMode.RIGHT_TO_LEFT)
                }
            }
            if (orientation == ReaderOrientation.DEFAULT) {
                val globalOrientation = ReaderOrientation.fromPreference(
                    readerPreferences.defaultOrientationType().get(),
                )
                if (globalOrientation != ReaderOrientation.DEFAULT) {
                    setMangaOrientationType(globalOrientation)
                } else {
                    setMangaOrientationType(ReaderOrientation.FREE)
                }
            }
        } else {
            setMangaReadingMode(ReadingMode.DEFAULT)
            setMangaOrientationType(ReaderOrientation.DEFAULT)
        }
    }

    /**
     * Apply reading mode either as a series override or as the app-wide default,
     * depending on [isSeriesViewerOverrideEnabled].
     */
    fun setReadingModePreference(readingMode: ReadingMode) {
        val target = resolveReadingModeApplyTarget(
            readingMode = readingMode,
            isSeriesOverrideEnabled = isSeriesViewerOverrideEnabled(),
            isAutoWebtoonModeActive = isMangaReadingModeAutoWebtoon(),
        )
        when (target) {
            ReadingModeApplyTarget.SeriesFlags -> setMangaReadingMode(readingMode)
            ReadingModeApplyTarget.GlobalDefault -> {
                readerPreferences.defaultReadingMode().set(readingMode.flagValue)
                // Manga flags stay on DEFAULT, so state.manga never re-emits and the activity would
                // keep the current viewer. Ask it to rebuild the viewer for the new resolved mode.
                viewModelScope.launchIO {
                    val currChapters = state.value.viewerChapters ?: return@launchIO
                    applySavedProgress(currChapters.currChapter)
                    eventChannel.send(Event.RecreateViewer)
                }
            }
        }
    }

    /**
     * Apply orientation either as a series override or as the app-wide default.
     */
    fun setOrientationPreference(orientation: ReaderOrientation) {
        if (orientation == ReaderOrientation.DEFAULT) {
            setMangaOrientationType(ReaderOrientation.DEFAULT)
            return
        }
        if (isSeriesViewerOverrideEnabled()) {
            setMangaOrientationType(orientation)
        } else {
            readerPreferences.defaultOrientationType().set(orientation.flagValue)
            viewModelScope.launchIO {
                eventChannel.send(Event.SetOrientation(getMangaOrientation()))
                eventChannel.send(Event.ReloadViewerChapters)
            }
        }
    }

    /**
     * Updates the viewer position for the open manga.
     */
    fun setMangaReadingMode(readingMode: ReadingMode) {
        val manga = manga ?: return
        // A-M9: was runBlocking(Dispatchers.IO) ON THE MAIN THREAD (called from the reading-mode
        // and series-override dialogs): two DB writes + getManga + a rendezvous send blocked the
        // UI thread (jank/ANR risk on slow IO). The event collector suspends in receive on the
        // main dispatcher, so the send handoff works identically from an IO coroutine.
        viewModelScope.launchIO {
            setMangaViewerFlags.awaitSetReadingMode(
                manga.id,
                readingMode.flagValue.toLong(),
            )
            val currChapters = state.value.viewerChapters
            if (currChapters != null) {
                // Save current page
                val currChapter = currChapters.currChapter
                // РЕШ-8 (part 2): the long-page px cache takes priority over the DB when a
                // chapter is reopened, but only WEBTOON writes it. A mode switch left the stale
                // webtoon entry behind: webtoon(px 50) -> pager(page 80) -> webtoon rolled back
                // to the old px-50 position. Drop the entry so applySavedProgress resolves from
                // the fresh DB progress written by the pager.
                // РЕШ-8 (part 3): a debounced flush still in flight (<=350ms) used to land AFTER
                // the removal, re-adding the entry and reproducing the very rollback this fixes.
                // Write it inline (under the progress mutex) before removing the entry.
                flushPendingWebtoonScrollProgressNow()
                // Under the same mutex as the flush writers: a debounced flush that had already
                // detached its IO block could otherwise land putLongPageProgressForChapter AFTER
                // this removal (millisecond window reproducing the very rollback РЕШ-8 fixed).
                progressWriteMutex.withLock {
                    currChapter.chapter.id?.let { readerPreferences.removeLongPageProgressForChapter(it) }
                }
                applySavedProgress(currChapter)

                mutableState.update {
                    it.copy(
                        manga = getManga.await(manga.id),
                        viewerChapters = currChapters,
                    )
                }
                eventChannel.send(Event.ReloadViewerChapters)
            }
        }
    }

    /**
     * Returns the orientation type used by this manga or the default one.
     */
    fun getMangaOrientation(resolveDefault: Boolean = true): Int {
        val default = readerPreferences.defaultOrientationType().get()
        val orientation = ReaderOrientation.fromPreference(manga?.readerOrientation?.toInt())
        return when {
            resolveDefault && orientation == ReaderOrientation.DEFAULT -> default
            else -> manga?.readerOrientation?.toInt() ?: default
        }
    }

    /**
     * Updates the orientation type for the open manga.
     */
    fun setMangaOrientationType(orientation: ReaderOrientation) {
        val manga = manga ?: return
        viewModelScope.launchIO {
            setMangaViewerFlags.awaitSetOrientation(manga.id, orientation.flagValue.toLong())
            val currChapters = state.value.viewerChapters
            if (currChapters != null) {
                // Save current page
                val currChapter = currChapters.currChapter
                applySavedProgress(currChapter)

                mutableState.update {
                    it.copy(
                        manga = getManga.await(manga.id),
                        viewerChapters = currChapters,
                    )
                }
                eventChannel.send(Event.SetOrientation(getMangaOrientation()))
                eventChannel.send(Event.ReloadViewerChapters)
            }
        }
    }

    fun toggleCropBorders(): Boolean {
        val isPagerType = ReadingMode.isPagerType(getMangaReadingMode())
        return if (isPagerType) {
            readerPreferences.cropBorders().toggle()
        } else {
            readerPreferences.cropBordersWebtoon().toggle()
        }
    }

    /**
     * Generate a filename for the given [manga] and [page]
     */
    private fun generateFilename(
        manga: Manga,
        page: ReaderPage,
    ): String {
        val chapter = page.chapter.chapter
        val filenameSuffix = " - ${page.number}"
        return DiskUtil.buildValidFilename(
            "${manga.title} - ${chapter.name}".takeBytes(
                DiskUtil.MAX_FILE_NAME_BYTES - filenameSuffix.byteSize(),
            ),
        ) + filenameSuffix
    }

    fun showMenus(visible: Boolean) {
        mutableState.update { it.copy(menuVisible = visible) }
    }

    fun showLoadingDialog() {
        mutableState.update { it.copy(dialog = Dialog.Loading) }
    }

    fun openReadingModeSelectDialog() {
        mutableState.update { it.copy(dialog = Dialog.ReadingModeSelect) }
    }

    fun openOrientationModeSelectDialog() {
        mutableState.update { it.copy(dialog = Dialog.OrientationModeSelect) }
    }

    fun openPageDialog(page: ReaderPage) {
        mutableState.update { it.copy(dialog = Dialog.PageActions(page)) }
    }

    fun openSettingsDialog() {
        mutableState.update { it.copy(dialog = Dialog.Settings) }
    }

    fun openChapterListDialog() {
        mutableState.update { it.copy(dialog = Dialog.ChapterList) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun setBrightnessOverlayValue(value: Int) {
        mutableState.update { it.copy(brightnessOverlayValue = value) }
    }

    /**
     * Toggles auto-scroll on/off.
     */
    fun toggleAutoScroll() {
        mutableState.update { it.copy(autoScrollEnabled = !it.autoScrollEnabled) }
    }

    /**
     * Sets the auto-scroll speed (1-100).
     */
    fun setAutoScrollSpeed(speed: Int) {
        mutableState.update {
            it.copy(autoScrollSpeed = persistAutoScrollSpeed(readerPreferences, speed))
        }
    }

    /**
     * Toggles the auto-scroll controls expansion.
     */
    fun toggleAutoScrollExpand() {
        mutableState.update { it.copy(isAutoScrollExpanded = !it.isAutoScrollExpanded) }
    }

    /**
     * Pauses auto-scroll (e.g., when menu is shown).
     */
    fun pauseAutoScroll() {
        mutableState.update { it.copy(autoScrollEnabled = false) }
    }

    private fun applySavedProgress(chapter: ReaderChapter) {
        if (shouldPauseHistory()) {
            // Incognito: progress is not persisted, so the stored position (DB and long-page
            // cache) goes stale during the session. Decoding it on a reading-mode/orientation
            // switch rewound the reader; keep the live position maintained by page-select and
            // webtoon scroll tracking instead.
            return
        }
        val decodedProgress = if (shouldHandleLongPageProgress()) {
            resolveLongPageSavedProgress(chapter)
        } else {
            decodeStoredChapterProgress(
                value = chapter.chapter.last_page_read,
                restoreOffset = false,
            )
        }
        chapter.requestedPage = decodedProgress.index
        chapter.requestedPageOffset = if (shouldHandleLongPageProgress()) decodedProgress.offsetPx else 0
        chapter.requestedPageOffsetRatioPpm =
            if (shouldHandleLongPageProgress()) decodedProgress.offsetRatioPpm else null
    }

    private fun resolveLongPageSavedProgress(chapter: ReaderChapter): ChapterScrollProgress {
        val chapterId = chapter.chapter.id ?: return decodeStoredChapterProgress(
            value = chapter.chapter.last_page_read,
            restoreOffset = true,
        )
        val chapterKey = chapter.chapter.url.takeIf { it.isNotBlank() }

        val cachedProgress = readerPreferences.getLongPageProgressForChapter(
            chapterId = chapterId,
            chapterKey = chapterKey,
        )
        if (cachedProgress != null) {
            return decodeStoredChapterProgress(cachedProgress, restoreOffset = true)
        }

        val decodedLegacy = decodeStoredChapterProgress(
            value = chapter.chapter.last_page_read,
            restoreOffset = true,
        )
        if (shouldImportLegacyLongPageProgress(chapter.chapter.last_page_read, decodedLegacy)) {
            val normalizedProgress = encodeWebtoonScrollProgress(decodedLegacy.index, decodedLegacy.offsetPx)
            val importedProgress = readerPreferences.importLongPageProgressFromLegacyIfMissing(
                chapterId = chapterId,
                legacyProgress = normalizedProgress,
                chapterKey = chapterKey,
            )
            return decodeStoredChapterProgress(importedProgress, restoreOffset = true)
        }

        return decodedLegacy
    }

    private fun shouldImportLegacyLongPageProgress(
        legacyProgress: Long,
        decodedProgress: ChapterScrollProgress,
    ): Boolean {
        return legacyProgress > 0L || decodedProgress.offsetPx > 0
    }

    private fun shouldHandleLongPageProgress(): Boolean {
        if (shouldPauseHistory()) return false
        if (!readerPreferences.saveLongPagePosition().get()) return false
        return shouldTrackWebtoonChapterProgress()
    }

    private fun shouldTrackWebtoonChapterProgress(): Boolean {
        return when (ReadingMode.fromPreference(getMangaReadingMode())) {
            ReadingMode.WEBTOON,
            ReadingMode.CONTINUOUS_VERTICAL,
            -> true
            else -> false
        }
    }

    /**
     * Saves the image of this the selected page on the pictures directory and notifies the UI of the result.
     * There's also a notification to allow sharing the image somewhere else or deleting it.
     */
    fun saveImage() {
        val page = (state.value.dialog as? Dialog.PageActions)?.page
        if (page?.status != Page.State.READY) return
        val manga = manga ?: return

        val context = Injekt.get<Application>()
        val notifier = SaveImageNotifier(context)
        notifier.onClear()

        val filename = generateFilename(manga, page)

        // Pictures directory.
        val relativePath = if (readerPreferences.folderPerManga().get()) {
            DiskUtil.buildValidFilename(
                manga.title,
            )
        } else {
            ""
        }

        // Copy file in background.
        viewModelScope.launchNonCancellable {
            try {
                val uri = imageSaver.save(
                    image = Image.Page(
                        inputStream = page.stream!!,
                        name = filename,
                        location = Location.Pictures.create(relativePath),
                    ),
                )
                withUIContext {
                    notifier.onComplete(uri)
                    eventChannel.send(Event.SavedImage(SaveImageResult.Success(uri)))
                }
            } catch (e: Throwable) {
                notifier.onError(e.message)
                eventChannel.send(Event.SavedImage(SaveImageResult.Error(e)))
            }
        }
    }

    /**
     * Shares the image of this the selected page and notifies the UI with the path of the file to share.
     * The image must be first copied to the internal partition because there are many possible
     * formats it can come from, like a zipped chapter, in which case it's not possible to directly
     * get a path to the file and it has to be decompressed somewhere first. Only the last shared
     * image will be kept so it won't be taking lots of internal disk space.
     */
    fun shareImage(copyToClipboard: Boolean) {
        val page = (state.value.dialog as? Dialog.PageActions)?.page
        if (page?.status != Page.State.READY) return
        val manga = manga ?: return

        val context = Injekt.get<Application>()
        val destDir = context.cacheImageDir

        val filename = generateFilename(manga, page)

        try {
            viewModelScope.launchNonCancellable {
                destDir.deleteRecursively()
                val uri = imageSaver.save(
                    image = Image.Page(
                        inputStream = page.stream!!,
                        name = filename,
                        location = Location.Cache,
                    ),
                )
                eventChannel.send(if (copyToClipboard) Event.CopyImage(uri) else Event.ShareImage(uri, page))
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
        }
    }

    /**
     * Sets the image of this the selected page as cover and notifies the UI of the result.
     */
    fun setAsCover() {
        val page = (state.value.dialog as? Dialog.PageActions)?.page
        if (page?.status != Page.State.READY) return
        val manga = manga ?: return
        val stream = page.stream ?: return

        viewModelScope.launchNonCancellable {
            val result = try {
                stream().use { manga.editCover(Injekt.get(), it) }
                if (manga.isLocal() || manga.favorite) {
                    SetAsCoverResult.Success
                } else {
                    SetAsCoverResult.AddToLibraryFirst
                }
            } catch (e: Exception) {
                SetAsCoverResult.Error
            }
            eventChannel.send(Event.SetCoverResult(result))
        }
    }

    enum class SetAsCoverResult {
        Success,
        AddToLibraryFirst,
        Error,
    }

    sealed interface SaveImageResult {
        class Success(val uri: Uri) : SaveImageResult
        class Error(val error: Throwable) : SaveImageResult
    }

    /**
     * Starts the service that updates the last chapter read in sync services. This operation
     * will run in a background thread and errors are ignored.
     */
    private fun updateTrackChapterRead(readerChapter: ReaderChapter) {
        if (shouldPauseHistory()) return
        if (!trackPreferences.autoUpdateTrack().get()) return

        val manga = manga ?: return
        val context = Injekt.get<Application>()

        viewModelScope.launchNonCancellable {
            trackChapter.await(context, manga.id, readerChapter.chapter.chapter_number.toDouble())
        }
    }

    /**
     * Enqueues this [chapter] to be deleted when [deletePendingChapters] is called. The download
     * manager handles persisting it across process deaths.
     */
    private fun enqueueDeleteReadChapters(chapter: Chapter) {
        if (!chapter.read) return
        val manga = manga ?: return

        viewModelScope.launchNonCancellable {
            downloadManager.enqueueChaptersToDelete(
                listOf(chapter.toDomainChapter()!!),
                manga,
            )
        }
    }

    /**
     * Deletes all the pending chapters. This operation will run in a background thread and errors
     * are ignored.
     */
    @OptIn(DelicateCoroutinesApi::class)
    private fun deletePendingChapters() {
        // Detached, see flushReadTimer: teardown-path work must survive the closed viewModelScope.
        launchIO {
            downloadManager.deletePendingChapters()
        }
    }

    @Immutable
    data class State(
        val sessionId: Long = 0L,
        val manga: Manga? = null,
        val viewerChapters: ViewerChapters? = null,
        val chapterList: List<ReaderChapter> = emptyList(),
        val bookmarked: Boolean = false,
        val isLoadingAdjacentChapter: Boolean = false,
        val currentPage: Int = -1,
        val estimatedMinutesLeft: Int? = null,

        /**
         * Viewer used to display the pages (pager, webtoon, ...).
         */
        val viewer: Viewer? = null,
        val dialog: Dialog? = null,
        val menuVisible: Boolean = false,
        @IntRange(from = -100, to = 100) val brightnessOverlayValue: Int = 0,
        val seriesInterstitialState: MangaSeriesInterstitialState? = null,
        val finaleState: ReaderFinaleState? = null,

        // Auto-scroll state
        val autoScrollEnabled: Boolean = false,
        val autoScrollSpeed: Int = 50,
        val isAutoScrollExpanded: Boolean = false,
    ) {
        val currentChapter: ReaderChapter?
            get() = viewerChapters?.currChapter

        val totalPages: Int
            get() = currentChapter?.pages?.size ?: -1
    }

    sealed interface Dialog {
        data object Loading : Dialog
        data object Settings : Dialog
        data object ChapterList : Dialog
        data object ReadingModeSelect : Dialog
        data object OrientationModeSelect : Dialog
        data class PageActions(val page: ReaderPage) : Dialog
        data object AutoWebtoonModeSuggestion : Dialog
    }

    sealed interface Event {
        data object ReloadViewerChapters : Event

        /**
         * Rebuild the viewer itself, then refill it with the current chapters. Needed when the
         * resolved reading mode changed without touching the manga viewer flags.
         */
        data object RecreateViewer : Event
        data object PageChanged : Event
        data class SetOrientation(val orientation: Int) : Event
        data class SetCoverResult(val result: SetAsCoverResult) : Event

        data class SavedImage(val result: SaveImageResult) : Event
        data class ShareImage(val uri: Uri, val page: ReaderPage) : Event
        data class CopyImage(val uri: Uri) : Event
        data class InitialLoadError(val exception: Throwable) : Event
    }

    private data class PendingWebtoonProgress(
        val chapterId: Long,
        val chapterKey: String?,
        val encodedProgress: Long,
    )
}

/**
 * Where a reading mode picked in the reader is stored, and whether the viewer has to be rebuilt
 * explicitly afterwards.
 */
internal enum class ReadingModeApplyTarget(val requiresViewerRebuild: Boolean) {
    /** Stored on the manga viewer flags; the resulting state.manga change rebuilds the viewer. */
    SeriesFlags(requiresViewerRebuild = false),

    /** Stored as the app-wide default; nothing in the reader state changes on its own. */
    GlobalDefault(requiresViewerRebuild = true),
}

internal fun resolveReadingModeApplyTarget(
    readingMode: ReadingMode,
    isSeriesOverrideEnabled: Boolean,
    isAutoWebtoonModeActive: Boolean,
): ReadingModeApplyTarget {
    return when {
        readingMode == ReadingMode.DEFAULT -> ReadingModeApplyTarget.SeriesFlags
        isSeriesOverrideEnabled -> ReadingModeApplyTarget.SeriesFlags
        // Auto-detect webtoon resolves the mode from the entry's own flags being DEFAULT, so a
        // global write would keep losing to it here (and would move every other series instead).
        // A manual pick must win for this entry: store it as a series override.
        isAutoWebtoonModeActive -> ReadingModeApplyTarget.SeriesFlags
        else -> ReadingModeApplyTarget.GlobalDefault
    }
}

internal fun shouldRestoreSavedProgress(
    chapter: ReaderChapter,
    preserveReadingPosition: Boolean,
): Boolean {
    // РЕШ-1 revival: the old formula (`!read || preserve || last_page_read > 0`) made the
    // "preserve reading position on read chapters" toggle dead in EVERY state - a chapter read
    // in the reader always has last_page_read > 0, and at last_page_read == 0 both branches land
    // on page 0 anyway. OFF now genuinely starts read chapters from the beginning.
    return !chapter.chapter.read || preserveReadingPosition
}

/**
 * Reading-session duration, clamped to zero.
 *
 * History upserts ACCUMULATE time_read (`time_read = time_read + :time_read`), so a system clock
 * rollback mid-session (NTP correction, manual change) used to subtract from the stored reading
 * statistics. The novel pipeline already clamps its session durations in five places.
 */
internal fun resolveSessionReadDurationMs(readAtMs: Long, startMs: Long?): Long {
    return startMs?.let { (readAtMs - it).coerceAtLeast(0L) } ?: 0L
}

internal data class ReadSessionSnapshot(
    val readAtMs: Long,
    val durationMs: Long,
)

internal class ReaderReadSessionTimer(
    private val clockMs: () -> Long = { Instant.now().toEpochMilli() },
) {
    private val lock = Any()
    private var startTimeMs: Long? = null

    fun restart(): Long = synchronized(lock) {
        val now = clockMs()
        startTimeMs = now
        now
    }

    fun consumeAndStop(): ReadSessionSnapshot = synchronized(lock) {
        val now = clockMs()
        val duration = resolveSessionReadDurationMs(readAtMs = now, startMs = startTimeMs)
        startTimeMs = null
        ReadSessionSnapshot(readAtMs = now, durationMs = duration)
    }

    fun currentStartTimeMs(): Long? = synchronized(lock) { startTimeMs }
}

internal fun prepareAdjacentChapterSwitch(
    flushReadTimer: () -> Unit,
    restartReadTimer: () -> Unit,
) {
    flushReadTimer()
    restartReadTimer()
}

private const val WEBTOON_PROGRESS_SAVE_DEBOUNCE_MILLIS = 350L

internal fun resolveInitialAutoScrollSpeed(readerPreferences: ReaderPreferences): Int {
    return readerPreferences.autoScrollSpeed().get().coerceIn(1, 100)
}

internal fun persistAutoScrollSpeed(
    readerPreferences: ReaderPreferences,
    speed: Int,
): Int {
    val clampedSpeed = speed.coerceIn(1, 100)
    readerPreferences.autoScrollSpeed().set(clampedSpeed)
    return clampedSpeed
}

internal inline fun swapViewerChaptersState(
    stateFlow: MutableStateFlow<ReaderViewModel.State>,
    newChapters: ViewerChapters,
    chapterList: List<ReaderChapter>,
    onPreviousChaptersReplaced: (ViewerChapters?) -> Unit,
) {
    // Add new references once before the CAS loop so retries never double-ref or double-unref.
    newChapters.ref()
    var previousChapters: ViewerChapters? = null
    stateFlow.update {
        previousChapters = it.viewerChapters
        it.copy(
            viewerChapters = newChapters,
            chapterList = chapterList,
            bookmarked = newChapters.currChapter.chapter.bookmark,
        )
    }
    onPreviousChaptersReplaced(previousChapters)
}

internal fun calculatePreloadBufferSize(
    averageSpeedSeconds: Double?,
    isMetered: Boolean,
): Int {
    if (isMetered) return 2
    if (averageSpeedSeconds == null) return 3 // Default buffer
    return when {
        averageSpeedSeconds < 4.0 -> 6
        averageSpeedSeconds > 15.0 -> 2
        else -> 3
    }
}
