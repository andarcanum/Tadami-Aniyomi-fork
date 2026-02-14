package eu.kanade.tachiyomi.ui.entries.novel

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Immutable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.novel.interactor.GetNovelExcludedScanlators
import eu.kanade.domain.entries.novel.interactor.SetNovelExcludedScanlators
import eu.kanade.domain.entries.novel.model.chaptersFiltered
import eu.kanade.domain.entries.novel.model.downloadedFilter
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.entries.novel.model.toSNovel
import eu.kanade.domain.items.novelchapter.interactor.GetAvailableNovelScanlators
import eu.kanade.domain.items.novelchapter.interactor.GetNovelScanlatorChapterCounts
import eu.kanade.domain.items.novelchapter.interactor.SyncNovelChaptersWithSource
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.data.export.novel.NovelEpubExportOptions
import eu.kanade.tachiyomi.data.export.novel.NovelEpubExporter
import eu.kanade.tachiyomi.data.track.MangaTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.Job
import logcat.LogPriority
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.category.novel.interactor.SetNovelCategories
import tachiyomi.domain.entries.applyFilter
import tachiyomi.domain.entries.novel.interactor.GetNovelWithChapters
import tachiyomi.domain.entries.novel.interactor.SetNovelChapterFlags
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.items.novelchapter.model.NoChaptersException
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.items.novelchapter.interactor.SetNovelDefaultChapterFlags
import tachiyomi.domain.items.novelchapter.service.getNovelChapterSort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.track.novel.interactor.GetNovelTracks
import tachiyomi.domain.track.novel.model.NovelTrack
import tachiyomi.domain.category.model.Category
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.time.Instant
import java.util.LinkedHashMap
import kotlin.coroutines.cancellation.CancellationException

enum class NovelDownloadAction {
    NEXT,
    UNREAD,
    ALL,
}

data class NovelEpubExportPreferencesState(
    val destinationTreeUri: String,
    val applyReaderTheme: Boolean,
    val includeCustomCss: Boolean,
    val includeCustomJs: Boolean,
)

class NovelScreenModel(
    private val lifecycle: Lifecycle,
    private val novelId: Long,
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val getNovelWithChapters: GetNovelWithChapters = Injekt.get(),
    private val updateNovel: UpdateNovel = Injekt.get(),
    private val syncNovelChaptersWithSource: SyncNovelChaptersWithSource = Injekt.get(),
    private val novelChapterRepository: NovelChapterRepository = Injekt.get(),
    private val setNovelChapterFlags: SetNovelChapterFlags = Injekt.get(),
    private val setNovelDefaultChapterFlags: SetNovelDefaultChapterFlags = Injekt.get(),
    private val getAvailableNovelScanlators: GetAvailableNovelScanlators = Injekt.get(),
    private val getNovelScanlatorChapterCounts: GetNovelScanlatorChapterCounts = Injekt.get(),
    private val getNovelExcludedScanlators: GetNovelExcludedScanlators = Injekt.get(),
    private val setNovelExcludedScanlators: SetNovelExcludedScanlators = Injekt.get(),
    private val getNovelCategories: GetNovelCategories = Injekt.get(),
    private val setNovelCategories: SetNovelCategories = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    private val getTracks: GetNovelTracks = Injekt.get(),
    private val novelDownloadManager: NovelDownloadManager = NovelDownloadManager(),
    private val novelEpubExporter: NovelEpubExporter = NovelEpubExporter(),
    private val novelReaderPreferences: NovelReaderPreferences = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<NovelScreenModel.State>(State.Loading) {

    private val successState: State.Success?
        get() = state.value as? State.Success

    val novel: Novel?
        get() = successState?.novel

    val source: NovelSource?
        get() = successState?.source

    val isAnyChapterSelected: Boolean
        get() = successState?.selectedChapterIds?.isNotEmpty() ?: false

    private var scanlatorSelectionJob: Job? = null

    fun isReadingStarted(): Boolean {
        val state = successState ?: return false
        return state.chapters.any { it.read || it.lastPageRead > 0L }
    }

    fun getResumeOrNextChapter(): NovelChapter? {
        val state = successState ?: return null
        val chapters = state.chapters.sortedWith(Comparator(getNovelChapterSort(state.novel)))
        if (chapters.isEmpty()) return null

        chapters.firstOrNull { it.lastPageRead > 0L && !it.read }?.let { return it }

        val lastReadIndex = chapters.indexOfLast { it.read || it.lastPageRead > 0L }
        if (lastReadIndex >= 0) {
            chapters.drop(lastReadIndex + 1).firstOrNull { !it.read }?.let { return it }
        }

        return chapters.firstOrNull { !it.read } ?: chapters.firstOrNull()
    }

    fun getNextUnreadChapter(): NovelChapter? {
        val state = successState ?: return null
        val chapters = state.processedChapters
        return if (state.novel.sortDescending()) {
            chapters.findLast { !it.read }
        } else {
            chapters.find { !it.read }
        }
    }

    init {
        restoreStateFromCache(novelId)?.let {
            mutableState.value = it
        }

        screenModelScope.launchIO {
            getNovelWithChapters.subscribe(novelId, applyScanlatorFilter = true)
                .distinctUntilChanged()
                .collectLatest { (novel, chapters) ->
                    val chapterIds = chapters.mapTo(mutableSetOf()) { c -> c.id }
                    updateSuccessState {
                        it.copy(
                            novel = novel,
                            chapters = chapters,
                            selectedChapterIds = it.selectedChapterIds.intersect(
                                chapterIds,
                            ),
                            downloadedChapterIds = it.downloadedChapterIds.intersect(chapterIds),
                            downloadingChapterIds = it.downloadingChapterIds.intersect(chapterIds),
                        )
                    }
                    val downloadedChapterIds = novelDownloadManager.getDownloadedChapterIds(novel, chapters)
                    updateSuccessState {
                        if (it.novel.id != novel.id || it.downloadedChapterIds == downloadedChapterIds) {
                            it
                        } else {
                            it.copy(downloadedChapterIds = downloadedChapterIds)
                        }
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

            if (initialExcludedScanlators != storedExcludedScanlators) {
                setNovelExcludedScanlators.await(novelId, initialExcludedScanlators)
            }

            val chapters = getNovelWithChapters.awaitChapters(novelId, applyScanlatorFilter = true)
            val shouldAutoRefreshNovel = !novel.initialized
            val shouldAutoRefreshChapters = chapters.isEmpty()
            val currentDownloadedIds = (state.value as? State.Success)
                ?.downloadedChapterIds
                ?.intersect(chapters.mapTo(mutableSetOf()) { it.id })
                .orEmpty()
            mutableState.update {
                State.Success(
                    novel = novel,
                    source = sourceManager.getOrStub(novel.source),
                    chapters = chapters,
                    availableScanlators = availableScanlators,
                    scanlatorChapterCounts = scanlatorChapterCounts,
                    excludedScanlators = initialExcludedScanlators,
                    isRefreshingData = shouldAutoRefreshNovel || shouldAutoRefreshChapters,
                    dialog = null,
                    selectedChapterIds = emptySet(),
                    downloadedChapterIds = currentDownloadedIds,
                    downloadingChapterIds = emptySet(),
                )
            }
            cacheState(state.value as? State.Success)
            observeTrackers()

            val downloadedChapterIds = novelDownloadManager.getDownloadedChapterIds(novel, chapters)
            updateSuccessState {
                if (it.novel.id != novel.id || it.downloadedChapterIds == downloadedChapterIds) {
                    it
                } else {
                    it.copy(downloadedChapterIds = downloadedChapterIds)
                }
            }

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
                is State.Success -> func(it).also(::cacheState)
            }
        }
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
                )
            }
            .filterNot(Category::isSystemCategory)
    }

    private suspend fun moveNovelToCategories(novelId: Long, categoryIds: List<Long>) {
        setNovelCategories.await(novelId, categoryIds)
    }

    fun refreshChapters(
        manualFetch: Boolean = true,
        refreshNovel: Boolean = true,
        refreshChapters: Boolean = true,
    ) {
        val state = successState ?: return
        screenModelScope.launchIO {
            updateSuccessState { it.copy(isRefreshingData = true) }
            try {
                supervisorScope {
                    val tasks = listOf(
                        async {
                            if (refreshNovel) {
                                runCatching {
                                    fetchNovelFromSource(state, manualFetch)
                                }.onFailure { error ->
                                    handleRefreshError(error)
                                }
                            }
                        },
                        async {
                            if (refreshChapters) {
                                runCatching {
                                    fetchChaptersFromSource(state, manualFetch)
                                }.onFailure { error ->
                                    handleRefreshError(error)
                                }
                            }
                        },
                    )
                    tasks.awaitAll()
                }
            } finally {
                updateSuccessState { it.copy(isRefreshingData = false) }
            }
        }
    }

    private suspend fun handleRefreshError(error: Throwable) {
        if (error is CancellationException) throw error
        val message = when (error) {
            is NoChaptersException -> "No chapters found"
            else -> error.message ?: "Failed to refresh"
        }
        snackbarHostState.showSnackbar(message = message)
    }

    private suspend fun fetchNovelFromSource(
        state: State.Success,
        manualFetch: Boolean,
    ) {
        val networkNovel = state.source.getNovelDetails(state.novel.toSNovel())
        updateNovel.awaitUpdateFromSource(
            localNovel = state.novel,
            remoteNovel = networkNovel,
            manualFetch = manualFetch,
        )
    }

    private suspend fun fetchChaptersFromSource(
        state: State.Success,
        manualFetch: Boolean,
    ) {
        val sourceChapters = state.source.getChapterList(state.novel.toSNovel())
        syncNovelChaptersWithSource.await(
            rawSourceChapters = sourceChapters,
            novel = state.novel,
            source = state.source,
            manualFetch = manualFetch,
        )
    }

    fun toggleChapterRead(chapterId: Long) {
        val chapter = successState?.chapters?.firstOrNull { it.id == chapterId } ?: return
        val newRead = !chapter.read
        screenModelScope.launchIO {
            novelChapterRepository.updateChapter(
                NovelChapterUpdate(
                    id = chapterId,
                    read = newRead,
                    lastPageRead = if (newRead) 0L else chapter.lastPageRead,
                ),
            )
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

    fun toggleAllChaptersRead() {
        val chapters = successState?.chapters ?: return
        if (chapters.isEmpty()) return
        val markRead = chapters.any { !it.read }
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
        screenModelScope.launchIO {
            novelChapterRepository.updateAllChapters(
                state.chapters
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
                    .toList(),
            )
            toggleAllSelection(false)
        }
    }

    fun toggleChapterDownload(chapterId: Long) {
        val state = successState ?: return
        val chapter = state.chapters.firstOrNull { it.id == chapterId } ?: return
        if (chapterId in state.downloadingChapterIds) return

        if (chapterId in state.downloadedChapterIds) {
            screenModelScope.launchIO {
                novelDownloadManager.deleteChapter(state.novel, chapterId)
                updateSuccessState {
                    it.copy(downloadedChapterIds = it.downloadedChapterIds - chapterId)
                }
            }
            return
        }

        updateSuccessState {
            it.copy(downloadingChapterIds = it.downloadingChapterIds + chapterId)
        }

        screenModelScope.launchIO {
            val success = runCatching {
                novelDownloadManager.downloadChapter(state.novel, chapter)
            }.getOrElse {
                snackbarHostState.showSnackbar(message = it.message ?: "Failed to download chapter")
                false
            }
            updateSuccessState {
                it.copy(
                    downloadedChapterIds = if (success) {
                        it.downloadedChapterIds + chapterId
                    } else {
                        it.downloadedChapterIds
                    },
                    downloadingChapterIds = it.downloadingChapterIds - chapterId,
                )
            }
        }
    }

    fun downloadSelectedChapters() {
        val state = successState ?: return
        val selectedChapters = state.chapters.filter { chapter ->
            chapter.id in state.selectedChapterIds && chapter.id !in state.downloadedChapterIds
        }
        if (selectedChapters.isEmpty()) return

        val chapterIds = selectedChapters.mapTo(mutableSetOf()) { it.id }
        updateSuccessState {
            it.copy(downloadingChapterIds = it.downloadingChapterIds + chapterIds)
        }

        screenModelScope.launchIO {
            val downloadedIds = novelDownloadManager.downloadChapters(state.novel, selectedChapters)
            updateSuccessState {
                it.copy(
                    downloadedChapterIds = it.downloadedChapterIds + downloadedIds,
                    downloadingChapterIds = it.downloadingChapterIds - chapterIds,
                )
            }
            toggleAllSelection(false)
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

        val chapterIds = chaptersToDownload.mapTo(mutableSetOf()) { it.id }
        updateSuccessState {
            it.copy(downloadingChapterIds = it.downloadingChapterIds + chapterIds)
        }

        screenModelScope.launchIO {
            val downloadedIds = novelDownloadManager.downloadChapters(state.novel, chaptersToDownload)
            updateSuccessState {
                it.copy(
                    downloadedChapterIds = it.downloadedChapterIds + downloadedIds,
                    downloadingChapterIds = it.downloadingChapterIds - chapterIds,
                )
            }
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

        updateSuccessState {
            it.copy(excludedScanlators = excluded)
        }

        scanlatorSelectionJob?.cancel()
        scanlatorSelectionJob = screenModelScope.launchIO {
            setNovelExcludedScanlators.await(novelId, excluded)

            // Refresh chapters immediately so branch switches don't wait for flow invalidation.
            val chapters = getNovelWithChapters.awaitChapters(novelId, applyScanlatorFilter = true)
            val chapterIds = chapters.mapTo(mutableSetOf()) { chapter -> chapter.id }
            updateSuccessState {
                it.copy(
                    chapters = chapters,
                    selectedChapterIds = it.selectedChapterIds.intersect(chapterIds),
                    downloadedChapterIds = it.downloadedChapterIds.intersect(chapterIds),
                    downloadingChapterIds = it.downloadingChapterIds.intersect(chapterIds),
                )
            }
        }
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
            val chapters: List<NovelChapter>,
            val availableScanlators: Set<String>,
            val scanlatorChapterCounts: Map<String, Int>,
            val excludedScanlators: Set<String>,
            val isRefreshingData: Boolean,
            val dialog: Dialog?,
            val trackingCount: Int = 0,
            val hasLoggedInTrackers: Boolean = false,
            val selectedChapterIds: Set<Long> = emptySet(),
            val downloadedChapterIds: Set<Long> = emptySet(),
            val downloadingChapterIds: Set<Long> = emptySet(),
        ) : State {
            val scanlatorFilterActive: Boolean
                get() = excludedScanlators.intersect(availableScanlators).isNotEmpty()

            val selectedScanlator: String?
                get() = resolveSelectedNovelScanlator(
                    availableScanlators = availableScanlators,
                    excludedScanlators = excludedScanlators,
                )

            val showScanlatorSelector: Boolean
                get() = scanlatorChapterCounts.size > 1

            val filterActive: Boolean
                get() = scanlatorFilterActive || novel.chaptersFiltered()

            val trackingAvailable: Boolean
                get() = trackingCount > 0

            val processedChapters by lazy {
                val chapterSort = Comparator(getNovelChapterSort(novel))
                chapters
                    .asSequence()
                    .filter { chapter ->
                        applyFilter(novel.unreadFilter) { !chapter.read } &&
                            applyFilter(novel.downloadedFilter) { chapter.id in downloadedChapterIds } &&
                            applyFilter(novel.bookmarkedFilter) { chapter.bookmark }
                    }
                    .sortedWith(chapterSort)
                    .toList()
            }
        }
    }

    fun showTrackDialog() {
        updateSuccessState { it.copy(dialog = Dialog.TrackSheet) }
    }

    companion object {
        private const val FAST_CACHE_MAX_ITEMS = 24
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
    if (availableScanlators.size < 2) return null
    if (excludedScanlators.intersect(availableScanlators).isNotEmpty()) return null

    val availableByNormalized = availableScanlators
        .asSequence()
        .map { it.trim() to it.trim() }
        .filter { (normalized, _) -> normalized.isNotEmpty() }
        .associate { it }
    if (availableByNormalized.size < 2) return null

    val preferredScanlator = scanlatorChapterCounts
        .asSequence()
        .map { it.key.trim() to it.value }
        .filter { (normalized, _) -> normalized in availableByNormalized.keys }
        .sortedWith(
            compareByDescending<Pair<String, Int>> { it.second }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first },
        )
        .map { it.first }
        .firstOrNull() ?: return null

    return availableByNormalized
        .filterKeys { it != preferredScanlator }
        .values
        .toSet()
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
