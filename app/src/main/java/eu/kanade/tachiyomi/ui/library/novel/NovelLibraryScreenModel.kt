package eu.kanade.tachiyomi.ui.library.novel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.asState
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.novel.LocalNovelBookImport
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.entries.novel.model.toSNovel
import eu.kanade.domain.items.novelchapter.interactor.SyncNovelChaptersWithSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.novel.MapNovelTrackStatusToLibrary
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.presentation.library.novel.NovelLibraryItem
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadCache
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadQueueManager
import eu.kanade.tachiyomi.data.download.novel.NovelTranslatedDownloadFormat
import eu.kanade.tachiyomi.data.download.novel.NovelTranslatedDownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.entries.novel.NovelDownloadAction
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreenModel
import eu.kanade.tachiyomi.ui.library.LibrarySearchQuery
import eu.kanade.tachiyomi.ui.library.leadingDebounce
import eu.kanade.tachiyomi.ui.library.resolveLibraryRangeSelectionAdditions
import eu.kanade.tachiyomi.ui.library.sortPinnedSeriesFirst
import eu.kanade.tachiyomi.ui.novel.resolveNovelResumeChapter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.core.archive.epubReader
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.category.novel.interactor.GetVisibleNovelCategories
import tachiyomi.domain.category.novel.interactor.SetNovelCategories
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.items.novelchapter.service.getNovelChapterSort
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibraryTrackStatus
import tachiyomi.domain.library.novel.model.NovelLibrarySort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.series.novel.interactor.AddNovelsToSeries
import tachiyomi.domain.series.novel.interactor.CreateNovelSeries
import tachiyomi.domain.series.novel.interactor.DeleteNovelSeries
import tachiyomi.domain.series.novel.interactor.GetLibraryNovelSeries
import tachiyomi.domain.series.novel.interactor.GetNovelIdsInAnySeries
import tachiyomi.domain.series.novel.interactor.UpdateNovelSeries
import tachiyomi.domain.series.novel.model.NovelSeries
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.domain.track.novel.interactor.GetTracksPerNovel
import tachiyomi.domain.track.novel.model.NovelTrack
import tachiyomi.source.local.entries.novel.Fb2Book
import tachiyomi.source.local.entries.novel.LocalNovelSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import kotlin.random.Random

typealias NovelLibraryMap = PersistentMap<Category, PersistentList<NovelLibraryItem>>

class NovelLibraryScreenModel(
    private val getLibraryNovel: GetLibraryNovel = Injekt.get(),
    private val getLibraryNovelSeries: GetLibraryNovelSeries = Injekt.get(),
    private val getNovelIdsInAnySeries: GetNovelIdsInAnySeries = Injekt.get(),
    private val deleteNovelSeries: DeleteNovelSeries = Injekt.get(),
    private val createNovelSeries: CreateNovelSeries = Injekt.get(),
    private val addNovelsToSeries: AddNovelsToSeries = Injekt.get(),
    private val updateNovelSeries: UpdateNovelSeries = Injekt.get(),
    private val getNovelCategories: GetNovelCategories = Injekt.get(),
    private val getVisibleNovelCategories: GetVisibleNovelCategories = Injekt.get(),
    private val getTracksPerNovel: GetTracksPerNovel = Injekt.get(),
    private val setNovelCategories: SetNovelCategories = Injekt.get(),
    private val updateNovel: UpdateNovel = Injekt.get(),
    private val chapterRepository: NovelChapterRepository = Injekt.get(),
    private val getNovelBookState: tachiyomi.domain.book.novel.interactor.GetNovelBookState = Injekt.get(),
    private val basePreferences: BasePreferences = Injekt.get(),
    val libraryPreferences: LibraryPreferences = Injekt.get(),
    val sourceManager: NovelSourceManager = Injekt.get(),
    val downloadCache: NovelDownloadCache = Injekt.get(),
    // F9: shared singletons - fresh per-ScreenModel instances defeated the managers' own SAF
    // scan caches (a negative-scan cache miss paid a full downloads-tree walk per novel).
    // runCatching fallback keeps the test harnesses (no DI registration) working.
    private val novelDownloadManager: NovelDownloadManager =
        runCatching { Injekt.get<NovelDownloadManager>() }.getOrElse { NovelDownloadManager() },
    private val novelTranslatedDownloadManager: NovelTranslatedDownloadManager =
        runCatching { Injekt.get<NovelTranslatedDownloadManager>() }
            .getOrElse { NovelTranslatedDownloadManager() },
    private val downloadedIdsFlow: StateFlow<Set<Long>> = downloadCache.downloadedIds,
    private val searchDebounceMillis: Long = SEARCH_DEBOUNCE_MILLIS,
    private val trackerManager: TrackerManager = Injekt.get(),
    private val startActive: Boolean = true,
    private val localNovelBookArtifactBuilderFactory:
    () -> eu.kanade.tachiyomi.data.book.novel.LocalNovelBookArtifactBuilder = {
        eu.kanade.tachiyomi.data.book.novel.LocalNovelBookArtifactBuilder()
    },
    private val libraryDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : StateScreenModel<NovelLibraryScreenModel.State>(
    State(
        groupType = if (libraryPreferences.globalGroupLibrary().get()) {
            libraryPreferences.globalGroupLibraryBy().get()
        } else {
            libraryPreferences.novelGroupLibraryBy().get()
        },
        downloadedOnly = basePreferences.downloadedOnly().get(),
        downloadedFilter = libraryPreferences.filterDownloadedNovel().get(),
        unreadFilter = libraryPreferences.filterUnreadNovel().get(),
        startedFilter = libraryPreferences.filterStartedNovel().get(),
        bookmarkedFilter = libraryPreferences.filterBookmarkedNovel().get(),
        completedFilter = libraryPreferences.filterCompletedNovel().get(),
        filterIntervalCustom = libraryPreferences.filterIntervalCustom().get(),
        sort = libraryPreferences.novelSortingMode().get(),
        randomSortSeed = libraryPreferences.randomNovelSortSeed().get(),
    ),
) {
    var activeCategoryIndex: Int by libraryPreferences.lastUsedNovelCategory().asState(screenModelScope)

    // F1: shared instances - the raw factories were subscribed multiple times inside the same
    // pipeline (filter flow 4x, badge flow 2x, sort flow 2x), so one preference toggle re-ran
    // the full library recompute several times.
    private val sharedFilterPreferencesFlow: Flow<FilterPreferences> =
        getFilterPreferencesFlow()
            .distinctUntilChanged()
            .shareIn(screenModelScope, SharingStarted.Eagerly, replay = 1)

    private val sharedSortPreferencesFlow: Flow<SortPreferences> =
        getSortPreferencesFlow()
            .shareIn(screenModelScope, SharingStarted.Eagerly, replay = 1)

    private val sharedBadgePreferencesFlow: Flow<BadgePreferences> =
        getBadgePreferencesFlow()
            .distinctUntilChanged()
            .shareIn(screenModelScope, SharingStarted.Eagerly, replay = 1)

    private val libraryPipelineActive = MutableStateFlow(startActive)

    fun setLibraryPipelineActive(active: Boolean) {
        libraryPipelineActive.value = active
    }

    init {
        screenModelScope.launch {
            libraryPipelineActive
                .flatMapLatest { active ->
                    if (!active) {
                        emptyFlow<Triple<NovelLibraryMap, List<String>, Int>>()
                    } else {
                        val baseLibraryFlow = combine(
                            getLibraryFlow(),
                            sharedFilterPreferencesFlow,
                            sharedSortPreferencesFlow,
                            getTracksPerNovel.subscribe(),
                            state.map { it.groupType }.distinctUntilChanged(),
                            getDownloadedIdsFlow(),
                            sharedBadgePreferencesFlow,
                        ) { flowsArray ->
                            @Suppress("UNCHECKED_CAST")
                            val library = flowsArray[0] as NovelLibraryMap
                            val filterPrefs = flowsArray[1] as FilterPreferences
                            val sortPrefs = flowsArray[2] as SortPreferences

                            @Suppress("UNCHECKED_CAST")
                            val tracks = flowsArray[3] as Map<Long, List<NovelTrack>>
                            val groupType = flowsArray[4] as Int

                            @Suppress("UNCHECKED_CAST")
                            val downloadedIds = flowsArray[5] as Set<Long>
                            val badgePrefs = flowsArray[6] as BadgePreferences

                            val effectiveDownloadedFilter = if (filterPrefs.downloadedOnly) {
                                TriState.ENABLED_IS
                            } else {
                                filterPrefs.downloadedFilter
                            }
                            val downloadedNovelIds = if (effectiveDownloadedFilter != TriState.DISABLED) {
                                val novelIdSet = library.values.flatten().flatMapTo(HashSet()) { item ->
                                    when (item) {
                                        is NovelLibraryItem.Single -> listOf(item.libraryNovel.novel.id)
                                        is NovelLibraryItem.Series ->
                                            item.librarySeries.entries.map { it.novel.id }
                                    }
                                }
                                downloadedIds.intersect(novelIdSet)
                            } else {
                                emptySet()
                            }

                            val hasActiveFilters = filterPrefs.hasActiveFilters()
                            val sourceCategories = library.keys.toList()

                            val languageCache = HashMap<Long, String>()
                            // F4: collect the language set in place - the whole-library
                            // flatten+mapNotNull ran on every emission and allocated an
                            // item-sized list just to derive a tiny set.
                            val languageSet = HashSet<String>()
                            library.values.forEach { items ->
                                items.forEach { item ->
                                    val sourceId = (item as? NovelLibraryItem.Single)?.libraryNovel?.novel?.source
                                        ?: (item as? NovelLibraryItem.Series)?.coverNovel?.source
                                    sourceId?.let {
                                        languageSet += languageCache.getOrPut(it) { sourceManager.getOrStub(it).lang }
                                    }
                                }
                            }
                            val libraryLanguages = languageSet.sorted()

                            NovelBaseLibraryResult(
                                groupType = groupType,
                                hasActiveFilters = hasActiveFilters,
                                library = library
                                    .applyFilters(
                                        effectiveDownloadedFilter = effectiveDownloadedFilter,
                                        downloadedNovelIds = downloadedNovelIds,
                                        unreadFilter = filterPrefs.unreadFilter,
                                        startedFilter = filterPrefs.startedFilter,
                                        bookmarkedFilter = filterPrefs.bookmarkedFilter,
                                        completedFilter = filterPrefs.completedFilter,
                                        filterIntervalCustom = filterPrefs.filterIntervalCustom,
                                        languages = filterPrefs.languages,
                                    )
                                    // E1: grouping BEFORE sorting - applySort sorts each map key's
                                    // list, and the pseudo-categories produced by applyGrouping
                                    // carry the effective global sort in their flags. The old
                                    // order sorted only within real categories, so grouped views
                                    // showed a concatenation of per-category runs and the sort
                                    // selection was silently ignored.
                                    .applyGrouping(groupType, tracks)
                                    .applySort(sortPrefs.sortMode, sortPrefs.randomSortSeed)
                                    .withFilteredEmptyPlaceholder(sourceCategories, hasActiveFilters)
                                    .withBadgeMetadata(downloadedIds, badgePrefs),
                                libraryLanguages = libraryLanguages,
                            )
                        }

                        combine(
                            baseLibraryFlow,
                            state.map { it.searchQuery }.distinctUntilChanged().leadingDebounce(searchDebounceMillis),
                        ) { baseLibrary, searchQuery ->
                            val librarySearchQuery = searchQuery?.let(::LibrarySearchQuery)
                            // F4: with no active query the per-category lists are unchanged -
                            // rebuilding the whole PersistentMap per emission was pure churn.
                            val searchedMap = if (librarySearchQuery == null) {
                                baseLibrary.library
                            } else {
                                baseLibrary.library
                                    .mapValues { (_, value) ->
                                        value.filter {
                                            it.matches(librarySearchQuery, sourceManager)
                                        }.toPersistentList()
                                    }
                                    .toPersistentMap()
                            }
                            val filteredMap = if (
                                baseLibrary.groupType == LibraryGroup.BY_DEFAULT ||
                                searchQuery != null ||
                                baseLibrary.hasActiveFilters
                            ) {
                                // Keep categories visible when searching so empty-result pages can
                                // still show the global search action.
                                searchedMap
                            } else {
                                searchedMap.filterValues { it.isNotEmpty() }.toPersistentMap()
                            }
                            // F2: deduped entry count computed here (background dispatcher), once
                            // per emission - the per-State lazy recomputed flatten+distinctBy
                            // over the WHOLE library on MAIN for every selection/keystroke copy.
                            val libraryCount = filteredMap.values
                                .flatten()
                                .distinctBy {
                                    when (it) {
                                        is NovelLibraryItem.Single -> it.libraryNovel.novel.id
                                        is NovelLibraryItem.Series -> it.librarySeries.id
                                    }
                                }
                                .size
                            Triple(filteredMap, baseLibrary.libraryLanguages, libraryCount)
                        }
                    }
                }
                .flowOn(libraryDispatcher)
                .collectLatest { (libraryMap, libraryLanguages, libraryCount) ->
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            hasLoaded = true,
                            library = libraryMap,
                            libraryLanguages = libraryLanguages,
                            libraryCount = libraryCount,
                        )
                    }
                }
        }

        combine(
            libraryPreferences.categoryTabs().changes(),
            libraryPreferences.categoryNumberOfItems().changes(),
            libraryPreferences.showContinueViewingButton().changes(),
        ) { a, b, c -> arrayOf(a, b, c) }
            .onEach { (showCategoryTabs, showNovelCount, showNovelContinueButton) ->
                mutableState.update { state ->
                    state.copy(
                        showCategoryTabs = showCategoryTabs,
                        showNovelCount = showNovelCount,
                        showNovelContinueButton = showNovelContinueButton,
                    )
                }
            }
            .launchIn(screenModelScope)

        sharedFilterPreferencesFlow
            .onEach { filterPrefs ->
                mutableState.update { state ->
                    state.copy(
                        downloadedOnly = filterPrefs.downloadedOnly,
                        downloadedFilter = filterPrefs.downloadedFilter,
                        unreadFilter = filterPrefs.unreadFilter,
                        startedFilter = filterPrefs.startedFilter,
                        bookmarkedFilter = filterPrefs.bookmarkedFilter,
                        completedFilter = filterPrefs.completedFilter,
                        filterIntervalCustom = filterPrefs.filterIntervalCustom,
                        languageFilter = filterPrefs.languages,
                    )
                }
            }
            .launchIn(screenModelScope)

        sharedSortPreferencesFlow
            .onEach { sortPrefs ->
                mutableState.update { state ->
                    state.copy(
                        sort = sortPrefs.sortMode,
                        randomSortSeed = sortPrefs.randomSortSeed,
                    )
                }
            }
            .launchIn(screenModelScope)

        sharedFilterPreferencesFlow
            .map { filterPrefs ->
                filterPrefs.hasActiveFilters()
            }
            .distinctUntilChanged()
            .onEach { hasActiveFilters ->
                mutableState.update { state ->
                    state.copy(hasActiveFilters = hasActiveFilters)
                }
            }
            .launchIn(screenModelScope)

        libraryPreferences.globalGroupLibrary().changes()
            .combine(libraryPreferences.globalGroupLibraryBy().changes()) { isGlobal, globalType ->
                isGlobal to globalType
            }
            .combine(libraryPreferences.novelGroupLibraryBy().changes()) { (isGlobal, globalType), mediaType ->
                if (isGlobal) globalType else mediaType
            }
            .onEach { groupType ->
                // B1: the pref flows emit their current value at collection start. Resetting
                // activeCategoryIndex on that initial emission zeroed the persisted "last used
                // category" on every screen model creation (category restore was dead). React
                // only to an actual group type change.
                if (state.value.groupType == groupType) return@onEach
                mutableState.update { it.copy(groupType = groupType) }
                activeCategoryIndex = 0
            }
            .launchIn(screenModelScope)
    }

    fun search(query: String?) {
        mutableState.update { current ->
            current.copy(searchQuery = query?.trim().orEmpty().ifBlank { null })
        }
    }

    fun showSettingsDialog() {
        mutableState.update { it.copy(dialog = Dialog.Settings) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun clearSelection() {
        mutableState.update { it.copy(selection = persistentListOf()) }
    }

    fun toggleSelection(novel: NovelLibraryItem) {
        mutableState.update { current ->
            val mutable = current.selection.toMutableList()
            val existingIndex = mutable.indexOfFirst { it.id == novel.id }
            if (existingIndex >= 0) {
                mutable.removeAt(existingIndex)
            } else {
                mutable.add(novel)
            }
            current.copy(selection = persistentListOf<NovelLibraryItem>().addingAll(mutable))
        }
    }

    fun toggleRangeSelection(novel: NovelLibraryItem) {
        mutableState.update { current ->
            val mutable = current.selection.toMutableList()
            val toAdd = resolveLibraryRangeSelectionAdditions(
                selectedItems = mutable,
                targetItem = novel,
                visibleGroups = current.library.values,
                itemId = { it.id },
            )
            mutable.addAll(toAdd)
            current.copy(selection = persistentListOf<NovelLibraryItem>().addingAll(mutable))
        }
    }

    fun selectAll(index: Int) {
        mutableState.update { current ->
            val targetCategoryId = current.categories.getOrNull(index)?.id
            val scopeItems = if (targetCategoryId == null) {
                current.items
            } else {
                current.getLibraryItemsByCategoryId(targetCategoryId).orEmpty()
            }
            val selectedIds = current.selectedIds
            val mutable = current.selection.toMutableList()
            mutable.addAll(scopeItems.filterNot { it.id in selectedIds })
            current.copy(selection = persistentListOf<NovelLibraryItem>().addingAll(mutable))
        }
    }

    fun invertSelection(index: Int) {
        mutableState.update { current ->
            val targetCategoryId = current.categories.getOrNull(index)?.id
            val scopeItems = if (targetCategoryId == null) {
                current.items
            } else {
                current.getLibraryItemsByCategoryId(targetCategoryId).orEmpty()
            }
            val selectedIds = current.selectedIds
            val toRemoveIds = scopeItems.mapNotNullTo(HashSet()) { item -> item.id.takeIf { it in selectedIds } }
            val mutable = current.selection.filterNot { it.id in toRemoveIds }.toMutableList()
            mutable.addAll(scopeItems.filterNot { it.id in selectedIds })
            current.copy(selection = persistentListOf<NovelLibraryItem>().addingAll(mutable))
        }
    }

    fun openChangeCategoryDialog() {
        val novels = state.value.selection
            .selectedNovelEntries()
            .map { it.novel }
        openChangeCategoryDialog(novels)
    }
    fun openDeleteNovelsDialog() {
        val novels = state.value.selection.selectedNovels()
        if (novels.isNotEmpty()) {
            mutableState.update { it.copy(dialog = Dialog.DeleteNovels(novels)) }
        }
    }
    fun openChangeCategoryDialog(novel: Novel) {
        openChangeCategoryDialog(listOf(novel))
    }

    private fun openChangeCategoryDialog(novels: List<Novel>) {
        if (novels.isEmpty()) return
        screenModelScope.launchIO {
            val categories = getCategories()
            if (categories.isEmpty()) return@launchIO
            // F7: fetch each novel's categories ONCE and derive common+mix from it - the two
            // former helpers each re-ran getNovelCategories.await per novel (2N sequential
            // queries before the dialog opened).
            val perNovelCategories = novels.map { getNovelCategories.await(it.id).toSet() }
            val commonSource = perNovelCategories.reduce { left, right -> left.intersect(right) }
            val mixSource = perNovelCategories.flatten().distinct().subtract(commonSource)
            val common = commonSource
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
            val mix = mixSource
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
            val preselected = categories
                .map { category ->
                    when (category) {
                        in common -> CheckboxState.State.Checked(category)
                        in mix -> CheckboxState.TriState.Exclude(category)
                        else -> CheckboxState.State.None(category)
                    }
                }
                .toImmutableList()
            mutableState.update {
                it.copy(dialog = Dialog.ChangeCategory(novels, preselected))
            }
        }
    }

    fun openDeleteNovelDialog() {
        val novels = state.value.selection.selectedNovels()
        if (novels.isEmpty()) return
        mutableState.update { it.copy(dialog = Dialog.DeleteNovels(novels)) }
    }

    fun updateNovelCategories(
        novels: List<Novel>,
        addCategories: List<Long>,
        removeCategories: List<Long>,
    ) {
        if (novels.isEmpty()) return
        // D2: non-cancellable - navigation mid-loop used to leave half the selection with
        // stale category membership.
        screenModelScope.launchNonCancellable {
            novels.forEach { novel ->
                val categoryIds = getNovelCategories.await(novel.id)
                    .map { it.id }
                    .subtract(removeCategories.toSet())
                    .plus(addCategories)
                    .toList()
                setNovelCategories.await(novel.id, categoryIds)
            }
        }
    }

    fun markReadSelection(read: Boolean) {
        val selected = state.value.selection.selectedNovels()
        if (selected.isEmpty()) return
        // D2: non-cancellable - cancellation mid-loop left novels k..N unread with the
        // selection already cleared and no feedback.
        screenModelScope.launchNonCancellable {
            // F7: ONE batched chapter update for the whole selection - the per-novel
            // get + updateAll pair was 2N transactions (the fetch stays per-novel until a
            // batched query exists).
            val updates = selected.flatMap { novel ->
                chapterRepository.getChapterByNovelId(
                    novelId = novel.id,
                    applyScanlatorFilter = true,
                ).map {
                    NovelChapterUpdate(
                        id = it.id,
                        read = read,
                        lastPageRead = if (read) 0L else it.lastPageRead,
                    )
                }
            }
            if (updates.isNotEmpty()) {
                chapterRepository.updateAllChapters(updates)
            }
        }
        clearSelection()
    }

    fun removeNovels(
        novels: List<Novel>,
        deleteFromLibrary: Boolean,
        deleteChapters: Boolean,
    ) {
        // D2: non-cancellable - cancellation between the unfavorite batch and the download
        // cleanup used to orphan downloaded files on disk.
        screenModelScope.launchNonCancellable {
            val toDelete = novels.distinctBy { it.id }
            if (deleteFromLibrary) {
                updateNovel.awaitAll(
                    toDelete.map {
                        NovelUpdate(
                            id = it.id,
                            favorite = false,
                        )
                    },
                )
            }
            if (deleteChapters) {
                toDelete.forEach { novel ->
                    novelDownloadManager.deleteNovel(novel)
                }
            }
        }
    }

    suspend fun runDownloadActionSelection(
        action: NovelDownloadAction,
        amount: Int = 0,
    ): Int {
        val selected = state.value.selection.selectedNovels()
        if (selected.isEmpty()) return 0
        var totalAdded = 0
        selected.forEach { novel ->
            val chapters = getSortedNovelChapters(novel)
            if (chapters.isEmpty()) return@forEach
            // F8: one batched directory listing per novel instead of up to 3 SAF findFile
            // calls PER CHAPTER (+ a full-tree scan fallback on misses).
            val downloadedChapterIds = novelDownloadManager.getDownloadedChapterIds(novel)
            val toQueue = NovelScreenModel.selectChaptersForDownload(
                action = action,
                novel = novel,
                chapters = chapters,
                downloadedChapterIds = downloadedChapterIds,
                amount = amount,
            )
            totalAdded += NovelDownloadQueueManager.enqueueOriginal(novel, toQueue)
        }
        clearSelection()
        return totalAdded
    }

    suspend fun runTranslatedDownloadActionSelection(
        action: NovelDownloadAction,
        amount: Int = 0,
        format: NovelTranslatedDownloadFormat,
    ): Int {
        val selected = state.value.selection.mapNotNull {
            (it as? NovelLibraryItem.Single)?.libraryNovel?.novel
        }.distinctBy { it.id }
        if (selected.isEmpty()) return 0
        var totalAdded = 0
        selected.forEach { novel ->
            val chaptersWithCache = getSortedNovelChapters(novel)
                .filter { chapter -> novelTranslatedDownloadManager.hasTranslationCache(chapter.id) }
            if (chaptersWithCache.isEmpty()) return@forEach
            // F8: batched per-(novel, format) listing with cache instead of exists() checks
            // per filename variant per chapter.
            val downloadedTranslatedIds = novelTranslatedDownloadManager.getTranslatedChapterIds(
                novel = novel,
                chapters = chaptersWithCache,
                format = format,
            )
            val toQueue = NovelScreenModel.selectTranslatedChaptersForDownload(
                action = action,
                novel = novel,
                chaptersWithCache = chaptersWithCache,
                downloadedTranslatedChapterIds = downloadedTranslatedIds,
                amount = amount,
            )
            totalAdded += NovelDownloadQueueManager.enqueueTranslated(
                novel = novel,
                chapters = toQueue,
                format = format,
            )
        }
        clearSelection()
        return totalAdded
    }

    suspend fun getSingleSelectionDownloadCandidates(onlyNotDownloaded: Boolean): List<NovelChapter> {
        val novel =
            (state.value.selection.singleOrNull() as? NovelLibraryItem.Single)?.libraryNovel?.novel
                ?: return emptyList()
        val chapters = getSortedNovelChapters(novel)
        if (!onlyNotDownloaded) return chapters
        // F8: batched listing (see runDownloadActionSelection).
        val downloadedChapterIds = novelDownloadManager.getDownloadedChapterIds(novel)
        return chapters.filterNot { it.id in downloadedChapterIds }
    }

    suspend fun runDownloadForSingleSelectionChapterIds(chapterIds: Set<Long>): Int {
        if (chapterIds.isEmpty()) return 0
        val novel = (state.value.selection.singleOrNull() as? NovelLibraryItem.Single)?.libraryNovel?.novel ?: return 0
        val chaptersById = getSortedNovelChapters(novel).associateBy { it.id }
        val chapters = chapterIds.mapNotNull { chaptersById[it] }
        if (chapters.isEmpty()) return 0
        val added = NovelDownloadQueueManager.enqueueOriginal(novel, chapters)
        clearSelection()
        return added
    }

    suspend fun getSingleSelectionTranslatedCandidates(
        format: NovelTranslatedDownloadFormat,
        onlyNotDownloaded: Boolean,
    ): List<NovelChapter> {
        val novel =
            (state.value.selection.singleOrNull() as? NovelLibraryItem.Single)?.libraryNovel?.novel
                ?: return emptyList()
        val chaptersWithCache = getSortedNovelChapters(novel)
            .filter { chapter -> novelTranslatedDownloadManager.hasTranslationCache(chapter.id) }
        if (!onlyNotDownloaded) return chaptersWithCache
        // F8: batched listing (see runTranslatedDownloadActionSelection).
        val downloadedTranslatedIds = novelTranslatedDownloadManager.getTranslatedChapterIds(
            novel = novel,
            chapters = chaptersWithCache,
            format = format,
        )
        return chaptersWithCache.filterNot { it.id in downloadedTranslatedIds }
    }

    suspend fun runTranslatedDownloadForSingleSelectionChapterIds(
        chapterIds: Set<Long>,
        format: NovelTranslatedDownloadFormat,
    ): Int {
        if (chapterIds.isEmpty()) return 0
        val novel = (state.value.selection.singleOrNull() as? NovelLibraryItem.Single)?.libraryNovel?.novel ?: return 0
        val chaptersById = getSortedNovelChapters(novel).associateBy { it.id }
        val chapters = chapterIds.mapNotNull { chaptersById[it] }
            .filter { chapter -> novelTranslatedDownloadManager.hasTranslationCache(chapter.id) }
        if (chapters.isEmpty()) return 0
        val added = NovelDownloadQueueManager.enqueueTranslated(
            novel = novel,
            chapters = chapters,
            format = format,
        )
        clearSelection()
        return added
    }

    fun resetFilters() {
        basePreferences.downloadedOnly().set(false)
        libraryPreferences.filterDownloadedNovel().set(TriState.DISABLED)
        libraryPreferences.filterUnreadNovel().set(TriState.DISABLED)
        libraryPreferences.filterStartedNovel().set(TriState.DISABLED)
        libraryPreferences.filterBookmarkedNovel().set(TriState.DISABLED)
        libraryPreferences.filterCompletedNovel().set(TriState.DISABLED)
        libraryPreferences.filterIntervalCustom().set(TriState.DISABLED)
        libraryPreferences.filterNovelLanguages().set(emptySet())
    }

    fun toggleDownloadedFilter() {
        // D5: compute the next value from a synchronous pref read - state.value is an async
        // mirror, so a fast double tap read the stale value and lost one toggle
        // (toggleLanguage below is the race-free pattern).
        libraryPreferences.filterDownloadedNovel().getAndSet { it.next() }
    }

    fun setDownloadedFilter(filter: TriState) {
        libraryPreferences.filterDownloadedNovel().set(filter)
    }

    fun toggleUnreadFilter() {
        libraryPreferences.filterUnreadNovel().getAndSet { it.next() } // D5
    }

    fun setUnreadFilter(filter: TriState) {
        libraryPreferences.filterUnreadNovel().set(filter)
    }

    fun toggleLanguage(language: String) {
        val current = libraryPreferences.filterNovelLanguages().get()
        libraryPreferences.filterNovelLanguages().set(
            if (language in current) current - language else current + language,
        )
    }

    fun setAllLanguages(languages: Set<String>) {
        libraryPreferences.filterNovelLanguages().set(languages)
    }

    fun clearLanguageFilter() {
        libraryPreferences.filterNovelLanguages().set(emptySet())
    }

    fun toggleStartedFilter() {
        libraryPreferences.filterStartedNovel().getAndSet { it.next() } // D5
    }

    fun setStartedFilter(filter: TriState) {
        libraryPreferences.filterStartedNovel().set(filter)
    }

    fun toggleBookmarkedFilter() {
        libraryPreferences.filterBookmarkedNovel().getAndSet { it.next() } // D5
    }

    fun setBookmarkedFilter(filter: TriState) {
        libraryPreferences.filterBookmarkedNovel().set(filter)
    }

    fun toggleCompletedFilter() {
        libraryPreferences.filterCompletedNovel().getAndSet { it.next() } // D5
    }

    fun setCompletedFilter(filter: TriState) {
        libraryPreferences.filterCompletedNovel().set(filter)
    }

    fun toggleIntervalCustomFilter() {
        libraryPreferences.filterIntervalCustom().getAndSet { it.next() } // D5
    }

    fun setIntervalCustomFilter(filter: TriState) {
        libraryPreferences.filterIntervalCustom().set(filter)
    }

    fun setSort(type: NovelLibrarySort.Type, direction: NovelLibrarySort.Direction) {
        libraryPreferences.novelSortingMode().set(NovelLibrarySort(type, direction))
        if (type == NovelLibrarySort.Type.Random) {
            libraryPreferences.randomNovelSortSeed().set(Random.nextInt())
        }
    }

    fun reshuffleRandomSort() {
        if (state.value.sort.type == NovelLibrarySort.Type.Random) {
            libraryPreferences.randomNovelSortSeed().set(Random.nextInt())
        }
    }

    suspend fun getNextUnreadChapter(novel: Novel): NovelChapter? {
        val chapters = chapterRepository.getChapterByNovelId(
            novelId = novel.id,
            applyScanlatorFilter = true,
        )
        // Book-mode titles keep their reading position in the book state; without it the resolver
        // fell back to the per-chapter heuristic and "continue" reopened the book at the wrong
        // chapter.
        val bookState = getNovelBookState.await(novel.id)
        return resolveNovelResumeChapter(chapters, null, bookState)
    }

    suspend fun getNextUnreadChapter(item: NovelLibraryItem): NovelChapter? {
        return when (item) {
            is NovelLibraryItem.Single -> getNextUnreadChapter(item.libraryNovel.novel)
            is NovelLibraryItem.Series -> {
                for (novel in item.librarySeries.entries) {
                    val chapter = getNextUnreadChapter(novel.novel)
                    if (chapter != null) return chapter
                }
                null
            }
        }
    }

    private suspend fun getSortedNovelChapters(novel: Novel): List<NovelChapter> {
        return chapterRepository.getChapterByNovelId(
            novelId = novel.id,
            applyScanlatorFilter = true,
        ).sortedWith(Comparator(getNovelChapterSort(novel)))
    }

    private fun applyFilter(
        filter: TriState,
        predicate: () -> Boolean,
    ): Boolean {
        return when (filter) {
            TriState.DISABLED -> true
            TriState.ENABLED_IS -> predicate()
            TriState.ENABLED_NOT -> !predicate()
        }
    }

    private fun NovelLibraryMap.applyFilters(
        effectiveDownloadedFilter: TriState,
        downloadedNovelIds: Set<Long>,
        unreadFilter: TriState,
        startedFilter: TriState,
        bookmarkedFilter: TriState,
        completedFilter: TriState,
        filterIntervalCustom: TriState,
        languages: Set<String>,
    ): NovelLibraryMap {
        val filterFnDownloaded: (NovelLibraryItem) -> Boolean = { item ->
            applyFilter(effectiveDownloadedFilter) {
                when (item) {
                    is NovelLibraryItem.Single -> item.libraryNovel.novel.id in downloadedNovelIds
                    is NovelLibraryItem.Series ->
                        item.librarySeries.entries.any { it.novel.id in downloadedNovelIds }
                }
            }
        }
        val filterFnUnread: (NovelLibraryItem) -> Boolean = { item ->
            applyFilter(unreadFilter) { item.unreadCount > 0 }
        }
        val filterFnStarted: (NovelLibraryItem) -> Boolean = { item ->
            applyFilter(startedFilter) { item.hasStarted }
        }
        val filterFnBookmarked: (NovelLibraryItem) -> Boolean = { item ->
            applyFilter(bookmarkedFilter) { item.hasBookmarks }
        }
        val filterFnCompleted: (NovelLibraryItem) -> Boolean = { item ->
            applyFilter(completedFilter) {
                when (item) {
                    is NovelLibraryItem.Single ->
                        item.libraryNovel.novel.status.toInt() == SManga.COMPLETED
                    // Manga parity: a series counts as completed when every entry is completed.
                    is NovelLibraryItem.Series ->
                        item.librarySeries.entries.isNotEmpty() &&
                            item.librarySeries.entries.all { it.novel.status.toInt() == SManga.COMPLETED }
                }
            }
        }
        val filterFnIntervalCustom: (NovelLibraryItem) -> Boolean = { item ->
            applyFilter(filterIntervalCustom) {
                when (item) {
                    is NovelLibraryItem.Single -> item.libraryNovel.novel.fetchInterval < 0
                    is NovelLibraryItem.Series ->
                        item.librarySeries.entries.any { it.novel.fetchInterval < 0 }
                }
            }
        }
        val languageBySourceId = HashMap<Long, String>()
        fun NovelLibraryItem.sourceLang(): String {
            val sourceId = (this as? NovelLibraryItem.Single)?.libraryNovel?.novel?.source
                ?: (this as? NovelLibraryItem.Series)?.coverNovel?.source
                ?: return ""
            return languageBySourceId.getOrPut(sourceId) { sourceManager.getOrStub(sourceId).lang }
        }
        val filterFnLanguage: (NovelLibraryItem) -> Boolean = { item ->
            languages.isEmpty() || item.sourceLang() in languages
        }
        val filterFn: (NovelLibraryItem) -> Boolean = {
            filterFnDownloaded(it) &&
                filterFnUnread(it) &&
                filterFnStarted(it) &&
                filterFnBookmarked(it) &&
                filterFnCompleted(it) &&
                filterFnIntervalCustom(it) &&
                filterFnLanguage(it)
        }
        return mapValues { (_, value) -> value.filter(filterFn).toPersistentList() }
            .toPersistentMap()
    }

    private fun NovelLibraryMap.applySort(
        sort: NovelLibrarySort,
        randomSortSeed: Int,
    ): NovelLibraryMap {
        return mapValues { (_, value) -> sortItems(value, sort, randomSortSeed).toPersistentList() }
            .toPersistentMap()
    }

    private fun NovelLibraryMap.withFilteredEmptyPlaceholder(
        sourceCategories: List<Category>,
        hasActiveFilters: Boolean,
    ): NovelLibraryMap {
        if (isNotEmpty() || !hasActiveFilters) return this
        val fallbackCategory = sourceCategories.firstOrNull() ?: return this
        return persistentMapOf(fallbackCategory to persistentListOf())
    }

    private fun FilterPreferences.hasActiveFilters(): Boolean {
        return downloadedOnly ||
            languages.isNotEmpty() ||
            listOf(
                downloadedFilter,
                unreadFilter,
                startedFilter,
                bookmarkedFilter,
                completedFilter,
                filterIntervalCustom,
            ).any { it != TriState.DISABLED }
    }

    private fun NovelLibraryMap.withBadgeMetadata(
        downloadedIds: Set<Long>,
        badgePreferences: BadgePreferences,
    ): NovelLibraryMap {
        if (isEmpty() || (!badgePreferences.showDownloadBadge && !badgePreferences.showLanguageBadge)) return this
        val languageBySourceId = HashMap<Long, String>()
        fun NovelLibraryItem.withBadgeMetadata(): NovelLibraryItem {
            val novel = coverNovel
            val isDownloaded = badgePreferences.showDownloadBadge && when (this) {
                is NovelLibraryItem.Single -> novel?.id in downloadedIds
                // Any downloaded entry earns the series the badge, not just the cover novel.
                is NovelLibraryItem.Series -> librarySeries.entries.any { it.novel.id in downloadedIds }
            }
            val sourceLanguage = if (badgePreferences.showLanguageBadge) {
                novel?.source?.let { sourceId ->
                    languageBySourceId.getOrPut(sourceId) {
                        sourceManager.getOrStub(sourceId).lang
                    }
                }.orEmpty()
            } else {
                ""
            }
            return when (this) {
                is NovelLibraryItem.Single -> copy(
                    isDownloaded = isDownloaded,
                    sourceLanguage = sourceLanguage,
                )
                is NovelLibraryItem.Series -> copy(
                    isDownloaded = isDownloaded,
                    sourceLanguage = sourceLanguage,
                )
            }
        }
        return mapValues { (_, value) -> value.map { it.withBadgeMetadata() }.toPersistentList() }
            .toPersistentMap()
    }

    private fun NovelLibraryMap.applyGrouping(
        groupType: Int,
        tracks: Map<Long, List<NovelTrack>>,
    ): NovelLibraryMap {
        if (groupType == LibraryGroup.BY_DEFAULT) return this

        val items = this.values.flatten().distinctBy { it.id }
        val sortFlags = libraryPreferences.novelSortingMode().get().flag

        val grouped = when (groupType) {
            LibraryGroup.UNGROUPED -> {
                val ungroupedCategory = Category(
                    id = -1L,
                    name = "Ungrouped",
                    order = 0,
                    flags = sortFlags,
                    hidden = false,
                    hiddenFromHomeHub = false,
                )
                mapOf(ungroupedCategory to items.toPersistentList())
            }
            LibraryGroup.BY_STATUS -> {
                val statusCategories = LinkedHashMap<Long, Pair<Category, MutableList<NovelLibraryItem>>>()
                items.forEach { item ->
                    // Manga parity: a series groups by its first entry (librarySeries.entries.first()).
                    val status = when (item) {
                        is NovelLibraryItem.Single -> item.libraryNovel.novel.status
                        is NovelLibraryItem.Series ->
                            item.librarySeries.entries.firstOrNull()?.novel?.status ?: 0L
                    }
                    val statusInt = status.toInt()
                    val (statusName, statusId) = when (statusInt) {
                        SManga.ONGOING -> "Ongoing" to -21L
                        SManga.COMPLETED -> "Completed" to -22L
                        SManga.LICENSED -> "Licensed" to -23L
                        SManga.PUBLISHING_FINISHED -> "Publishing Finished" to -24L
                        SManga.CANCELLED -> "Cancelled" to -25L
                        SManga.ON_HIATUS -> "On Hiatus" to -26L
                        else -> "Unknown" to -20L
                    }
                    val (_, list) = statusCategories.getOrPut(statusId) {
                        Category(
                            id = statusId,
                            name = statusName,
                            order = statusId,
                            flags = sortFlags,
                            hidden = false,
                            hiddenFromHomeHub = false,
                        ) to mutableListOf()
                    }
                    list.add(item)
                }
                statusCategories.entries.associate { (_, pair) ->
                    pair.first to pair.second.toPersistentList()
                }.toPersistentMap()
            }
            LibraryGroup.BY_SOURCE -> {
                val sourceCategories = LinkedHashMap<Long, Pair<Category, MutableList<NovelLibraryItem>>>()
                items.forEach { item ->
                    val sourceId = when (item) {
                        is NovelLibraryItem.Single -> item.libraryNovel.novel.source
                        is NovelLibraryItem.Series ->
                            item.librarySeries.entries.firstOrNull()?.novel?.source ?: 0L
                    }
                    val sourceName = sourceManager.getOrStub(sourceId).name
                    val categoryId = -sourceId - 1000L
                    val (_, list) = sourceCategories.getOrPut(categoryId) {
                        Category(
                            id = categoryId,
                            name = sourceName,
                            order = categoryId,
                            flags = sortFlags,
                            hidden = false,
                            hiddenFromHomeHub = false,
                        ) to mutableListOf()
                    }
                    list.add(item)
                }
                sourceCategories.entries.associate { (_, pair) ->
                    pair.first to pair.second.toPersistentList()
                }.toPersistentMap()
            }
            LibraryGroup.BY_TRACK_STATUS -> {
                val trackMapper = MapNovelTrackStatusToLibrary(trackerManager)
                val trackCategories = LinkedHashMap<Long, Pair<Category, MutableList<NovelLibraryItem>>>()
                items.forEach { item ->
                    val representativeNovelId = when (item) {
                        is NovelLibraryItem.Single -> item.libraryNovel.novel.id
                        is NovelLibraryItem.Series -> item.librarySeries.entries.firstOrNull()?.novel?.id
                    }
                    val itemTracks = representativeNovelId?.let { tracks[it] }.orEmpty()
                    if (itemTracks.isEmpty()) {
                        val categoryId = -2L
                        val (_, list) = trackCategories.getOrPut(categoryId) {
                            Category(
                                id = categoryId,
                                name = "Untracked",
                                order = categoryId,
                                flags = sortFlags,
                                hidden = false,
                                hiddenFromHomeHub = false,
                            ) to mutableListOf()
                        }
                        list.add(item)
                    } else {
                        val statuses = itemTracks.map { track ->
                            trackMapper.map(track.trackerId, track.status)
                        }.distinct()
                        statuses.forEach { status ->
                            val statusName = when (status) {
                                LibraryTrackStatus.READING -> "Reading"
                                LibraryTrackStatus.REPEATING -> "Repeating"
                                LibraryTrackStatus.COMPLETED -> "Completed"
                                LibraryTrackStatus.ON_HOLD -> "On Hold"
                                LibraryTrackStatus.DROPPED -> "Dropped"
                                LibraryTrackStatus.PLAN_TO_READ -> "Plan to read"
                                LibraryTrackStatus.OTHER -> "Other"
                            }
                            val statusId = -(status.int + 10L)
                            val (_, list) = trackCategories.getOrPut(statusId) {
                                Category(
                                    id = statusId,
                                    name = statusName,
                                    order = statusId,
                                    flags = sortFlags,
                                    hidden = false,
                                    hiddenFromHomeHub = false,
                                ) to mutableListOf()
                            }
                            list.add(item)
                        }
                    }
                }
                trackCategories.entries.associate { (_, pair) ->
                    pair.first to pair.second.toPersistentList()
                }.toPersistentMap()
            }
            else -> this
        }

        return grouped.entries
            .sortedBy { entry -> entry.key.id }
            .associate { entry -> entry.key to entry.value }
            .toPersistentMap()
    }

    private fun getLibraryFlow(): Flow<NovelLibraryMap> {
        val libraryNovelsFlow = combine(
            getLibraryNovel.subscribe(),
            getLibraryNovelSeries.subscribe(),
        ) { novels, series ->
            // F6: membership ids derived from the already-loaded series entries - the extra
            // getNovelIdsInAnySeries subscription re-queried the same novel_series_entries
            // table on every DB change.
            val idsInSeries = series.flatMapTo(HashSet()) { librarySeries ->
                librarySeries.entries.map { it.novel.id }
            }
            val singleItems = novels.filterNot {
                it.novel.id in idsInSeries
            }.map { NovelLibraryItem.Single(it) }
            val seriesItems = series
                // Manga parity: a series whose entries all left the library must not render as a
                // ghost card (no cover, zero counts, and it pins the system category tab open).
                .filter { it.entries.isNotEmpty() }
                .map { NovelLibraryItem.Series(it) }
            (singleItems + seriesItems).groupBy { it.category }
        }

        return combine(getVisibleNovelCategories.subscribe(), libraryNovelsFlow) { categories, libraryNovels ->
            val mappedCategories = categories.map {
                Category(
                    id = it.id,
                    name = it.name,
                    order = it.order,
                    flags = it.flags,
                    hidden = it.hidden,
                    hiddenFromHomeHub = false,
                )
            }
            val displayCategories = if (libraryNovels.isNotEmpty() && !libraryNovels.containsKey(0L)) {
                mappedCategories.filterNot { it.isSystemCategory }
            } else {
                mappedCategories
            }

            displayCategories
                .associateWith { libraryNovels[it.id].orEmpty().toPersistentList() }
                .toPersistentMap()
        }
    }

    private fun sortItems(
        items: List<NovelLibraryItem>,
        sort: NovelLibrarySort,
        randomSortSeed: Int,
    ): List<NovelLibraryItem> {
        if (items.isEmpty()) return items
        val isPinned: (NovelLibraryItem) -> Boolean = {
            when (it) {
                is NovelLibraryItem.Single -> it.libraryNovel.pinned
                is NovelLibraryItem.Series -> it.librarySeries.pinned
            }
        }
        val isSeries: (NovelLibraryItem) -> Boolean = { it is NovelLibraryItem.Series }
        val alphabeticSortKeys = HashMap<Long, String>()
        fun NovelLibraryItem.alphabeticSortKey(): String {
            return alphabeticSortKeys.getOrPut(id) { title.lowercase() }
        }

        val comparator = Comparator<NovelLibraryItem> { left, right ->
            when (sort.type) {
                NovelLibrarySort.Type.Alphabetical -> {
                    left.alphabeticSortKey().compareToWithCollator(right.alphabeticSortKey())
                }
                NovelLibrarySort.Type.LastRead -> left.lastRead.compareTo(right.lastRead)
                NovelLibrarySort.Type.LastUpdate -> {
                    left.lastUpdateValue().compareTo(right.lastUpdateValue())
                }
                NovelLibrarySort.Type.UnreadCount -> {
                    when {
                        left.unreadCount == right.unreadCount -> 0
                        left.unreadCount == 0L -> if (sort.isAscending) 1 else -1
                        right.unreadCount == 0L -> if (sort.isAscending) -1 else 1
                        else -> left.unreadCount.compareTo(right.unreadCount)
                    }
                }
                NovelLibrarySort.Type.TotalChapters -> left.totalChapters.compareTo(right.totalChapters)
                NovelLibrarySort.Type.LatestChapter -> {
                    left.latestUploadValue().compareTo(right.latestUploadValue())
                }
                NovelLibrarySort.Type.ChapterFetchDate -> {
                    left.chapterFetchedAtValue().compareTo(right.chapterFetchedAtValue())
                }
                NovelLibrarySort.Type.DateAdded -> left.dateAdded.compareTo(right.dateAdded)
                NovelLibrarySort.Type.TrackerMean -> 0
                NovelLibrarySort.Type.Random -> 0
            }
        }
            .let { if (sort.isAscending) it else it.reversed() }
            .thenComparator { left, right ->
                left.alphabeticSortKey().compareToWithCollator(right.alphabeticSortKey())
            }

        return items.sortPinnedSeriesFirst(
            isPinned = isPinned,
            isSeries = isSeries,
            comparator = comparator,
            randomSeed = if (sort.type == NovelLibrarySort.Type.Random) randomSortSeed else null,
        )
    }

    // Manga parity: a series sorts by the newest activity across its entries instead of collapsing
    // to 0 (LibraryNovelSeries.latestUpload already aggregates; lastUpdate/chapterFetchedAt do not).
    private fun NovelLibraryItem.lastUpdateValue(): Long = when (this) {
        is NovelLibraryItem.Single -> libraryNovel.novel.lastUpdate
        is NovelLibraryItem.Series -> librarySeries.entries.maxOfOrNull { it.novel.lastUpdate } ?: 0L
    }

    private fun NovelLibraryItem.latestUploadValue(): Long = when (this) {
        is NovelLibraryItem.Single -> libraryNovel.latestUpload
        is NovelLibraryItem.Series -> librarySeries.latestUpload
    }

    private fun NovelLibraryItem.chapterFetchedAtValue(): Long = when (this) {
        is NovelLibraryItem.Single -> libraryNovel.chapterFetchedAt
        is NovelLibraryItem.Series -> librarySeries.entries.maxOfOrNull { it.chapterFetchedAt } ?: 0L
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

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val hasLoaded: Boolean = false,
        val library: NovelLibraryMap = persistentMapOf(),
        val searchQuery: String? = null,
        val selection: PersistentList<NovelLibraryItem> = persistentListOf(),
        val hasActiveFilters: Boolean = false,
        val showCategoryTabs: Boolean = false,
        val showNovelCount: Boolean = false,
        val showNovelContinueButton: Boolean = false,
        val groupType: Int = LibraryGroup.BY_DEFAULT,
        val downloadedOnly: Boolean = false,
        val downloadedFilter: TriState = TriState.DISABLED,
        val unreadFilter: TriState = TriState.DISABLED,
        val startedFilter: TriState = TriState.DISABLED,
        val bookmarkedFilter: TriState = TriState.DISABLED,
        val completedFilter: TriState = TriState.DISABLED,
        val filterIntervalCustom: TriState = TriState.DISABLED,
        val languageFilter: Set<String> = emptySet(),
        val libraryLanguages: List<String> = emptyList(),
        val libraryCount: Int = 0,
        val downloadedNovelIds: Set<Long> = emptySet(),
        val sort: NovelLibrarySort = NovelLibrarySort.default,
        val randomSortSeed: Int = 0,
        val dialog: Dialog? = null,
    ) {
        val categories = library.keys.toList()
        private val pages = library.values.toList()

        val items: List<NovelLibraryItem> by lazy { pages.flatten() }

        val rawItems: List<NovelLibraryItem>
            get() = items

        val effectiveDownloadedFilter: TriState
            get() = if (downloadedOnly) TriState.ENABLED_IS else downloadedFilter

        // F2: libraryCount is a pipeline-computed field now (see the second combine) - the
        // per-State lazy recomputed flatten+distinctBy over the whole library on MAIN for
        // every selection/keystroke state copy that read isLibraryEmpty or the toolbar count.
        val isLibraryEmpty: Boolean
            get() = libraryCount == 0

        val selectionMode = selection.isNotEmpty()

        val selectedIds: ImmutableSet<Long> by lazy {
            persistentSetOf<Long>().mutate { ids ->
                selection.forEach { ids.add(it.id) }
            }
        }

        fun getLibraryItemsByCategoryId(categoryId: Long): List<NovelLibraryItem>? {
            val index = categories.indexOfFirst { it.id == categoryId }
            return pages.getOrNull(index)
        }

        fun getLibraryItemsByPage(page: Int): List<NovelLibraryItem> {
            return pages.getOrNull(page).orEmpty()
        }

        fun getNovelCountForCategory(category: Category): Int? {
            return if (showNovelCount || !searchQuery.isNullOrEmpty()) library[category]?.size else null
        }

        fun getToolbarTitle(
            defaultTitle: String,
            defaultCategoryTitle: String,
            page: Int,
        ): LibraryToolbarTitle {
            val category = categories.getOrNull(page) ?: return LibraryToolbarTitle(defaultTitle)
            val categoryName = category.let {
                if (it.isSystemCategory) defaultCategoryTitle else it.name
            }
            val title = if (showCategoryTabs) defaultTitle else categoryName
            val count = when {
                !showNovelCount -> null
                !showCategoryTabs -> getNovelCountForCategory(category)
                else -> libraryCount
            }

            return LibraryToolbarTitle(title, count)
        }
    }

    private data class FilterPreferences(
        val downloadedOnly: Boolean,
        val downloadedFilter: TriState,
        val unreadFilter: TriState,
        val startedFilter: TriState,
        val bookmarkedFilter: TriState,
        val completedFilter: TriState,
        val filterIntervalCustom: TriState,
        val languages: Set<String> = emptySet(),
    )

    private data class SortPreferences(
        val sortMode: NovelLibrarySort,
        val randomSortSeed: Int,
    )

    private data class BadgePreferences(
        val showDownloadBadge: Boolean,
        val showLanguageBadge: Boolean,
    )

    private data class NovelBaseLibraryResult(
        val groupType: Int,
        val hasActiveFilters: Boolean,
        val library: NovelLibraryMap,
        val libraryLanguages: List<String>,
    )

    private data class RecomputeInput(
        val query: String?,
        val novels: List<NovelLibraryItem>,
        val filterPreferences: FilterPreferences,
        val sortPreferences: SortPreferences,
        val downloadedIds: Set<Long>,
    )

    private data class RecomputedState(
        val items: List<NovelLibraryItem>,
        val downloadedNovelIds: Set<Long>,
    )

    private fun getBadgePreferencesFlow(): Flow<BadgePreferences> {
        return combine(
            libraryPreferences.downloadBadge().changes(),
            libraryPreferences.languageBadge().changes(),
        ) { showDownloadBadge, showLanguageBadge ->
            BadgePreferences(
                showDownloadBadge = showDownloadBadge,
                showLanguageBadge = showLanguageBadge,
            )
        }
    }

    /**
     * Only subscribe to download events while the UI needs them (downloaded filter
     * active or download badge enabled). Without this gate every download/delete
     * event re-runs the whole library pipeline on a large library.
     */
    private fun getDownloadedIdsFlow(): Flow<Set<Long>> {
        return combine(
            sharedFilterPreferencesFlow,
            sharedBadgePreferencesFlow,
        ) { filterPrefs, badgePrefs ->
            filterPrefs.downloadedOnly ||
                filterPrefs.downloadedFilter != TriState.DISABLED ||
                badgePrefs.showDownloadBadge
        }
            .distinctUntilChanged()
            .flatMapLatest { needsDownloadState ->
                if (needsDownloadState) {
                    downloadedIdsFlow
                } else {
                    flowOf(emptySet())
                }
            }
    }

    private fun getFilterPreferencesFlow(): Flow<FilterPreferences> {
        return combine(
            basePreferences.downloadedOnly().changes(),
            libraryPreferences.filterDownloadedNovel().changes(),
            libraryPreferences.filterUnreadNovel().changes(),
            libraryPreferences.filterStartedNovel().changes(),
            libraryPreferences.filterBookmarkedNovel().changes(),
        ) { downloadedOnly, downloadedFilter, unreadFilter, startedFilter, bookmarkedFilter ->
            FilterPreferences(
                downloadedOnly = downloadedOnly,
                downloadedFilter = downloadedFilter,
                unreadFilter = unreadFilter,
                startedFilter = startedFilter,
                bookmarkedFilter = bookmarkedFilter,
                completedFilter = TriState.DISABLED,
                filterIntervalCustom = TriState.DISABLED,
            )
        }
            .combine(libraryPreferences.filterIntervalCustom().changes()) { prefs, filterIntervalCustom ->
                prefs.copy(filterIntervalCustom = filterIntervalCustom)
            }
            .combine(libraryPreferences.filterCompletedNovel().changes()) { prefs, completedFilter ->
                prefs.copy(completedFilter = completedFilter)
            }
            .combine(libraryPreferences.filterNovelLanguages().changes()) { prefs, languages ->
                prefs.copy(languages = languages)
            }
            .distinctUntilChanged()
    }

    private fun getSortPreferencesFlow(): Flow<SortPreferences> {
        return combine(
            libraryPreferences.novelSortingMode().changes(),
            libraryPreferences.randomNovelSortSeed().changes(),
        ) { sort, randomSortSeed ->
            SortPreferences(
                sortMode = sort,
                randomSortSeed = randomSortSeed,
            )
        }.distinctUntilChanged()
    }

    /**
     * Imports a local book file (EPUB or FB2) into `localnovel/` and inserts a LocalNovel row.
     * Kept as [importEpub] for call-site compatibility.
     */
    suspend fun importEpub(uri: Uri) = importLocalBook(uri)

    suspend fun importLocalBook(uri: Uri) {
        withContext(Dispatchers.IO) {
            val context = Injekt.get<Application>()
            val storageManager = Injekt.get<StorageManager>()
            val localDir = storageManager.getLocalNovelSourceDirectory()
                ?: throw IOException("Local novel directory not configured. Set a storage location first.")
            val novelRepository = Injekt.get<NovelRepository>()
            val sourcePreferences = Injekt.get<SourcePreferences>()

            val displayName = getLocalBookDisplayName(context, uri)
            val sanitizedName = LocalNovelBookImport.sanitizeFileName(displayName)
            if (!LocalNovelBookImport.isSupportedImportFileName(sanitizedName)) {
                throw IOException(
                    "Unsupported local novel format (got: $sanitizedName)",
                )
            }

            // D8: copy to a temp name and rename on success - a failed/interrupted copy used to
            // leave a truncated book under the FINAL filename, which the LocalNovelSource
            // directory pipeline then picked up as a corrupt entry. The temp extension is not a
            // supported book format, so a leftover temp file is never picked up.
            val tempName = "$sanitizedName.importing"
            localDir.findFile(tempName)?.delete()
            val tempFile = localDir.createFile(tempName)
                ?: throw IOException("Cannot create temp file: $tempName")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.openOutputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IOException("Cannot open selected file")

                // SAF rename fails onto an existing name; an older book with the same filename
                // is being replaced by this import (matches the previous overwrite behavior).
                localDir.findFile(sanitizedName)?.delete()
                if (!tempFile.renameTo(sanitizedName)) {
                    throw IOException("Cannot finalize import: $sanitizedName")
                }
            } catch (e: Throwable) {
                tempFile.delete()
                throw e
            }

            val targetFile = localDir.findFile(sanitizedName)
                ?: throw IOException("Imported file disappeared: $sanitizedName")

            val meta = readLocalBookMetadata(context, targetFile, LocalNovelBookImport.extensionOf(sanitizedName))
            val finalTitle = meta.title?.takeIf { it.isNotBlank() }
                ?: LocalNovelBookImport.titleFallbackFromFileName(sanitizedName)

            val addToLibrary = sourcePreferences.importEpubAddToLibrary().get()
            val novel = Novel.create().copy(
                source = LocalNovelSource.ID,
                url = sanitizedName,
                title = finalTitle,
                author = meta.author,
                description = meta.description,
                favorite = addToLibrary,
                dateAdded = if (addToLibrary) System.currentTimeMillis() else 0L,
                initialized = true,
            )

            val insertedId = novelRepository.insertNovel(novel)

            // Compile the book artifact right after the import if auto-compile is enabled.
            // The explicit null check keeps the smart cast for the artifact build below.
            if (insertedId != null &&
                shouldCompileLocalBookArtifact(insertedId, sourcePreferences.autoCompileLocalEpubBook().get())
            ) {
                compileLocalBookArtifact(
                    novel = novel.copy(id = insertedId),
                    sourceManager = Injekt.get(),
                    syncChapters = { rawSourceChapters, novelToSync, source ->
                        Injekt.get<SyncNovelChaptersWithSource>().await(
                            rawSourceChapters = rawSourceChapters,
                            novel = novelToSync,
                            source = source,
                        )
                    },
                    compile = { novelToCompile, syncedChapters ->
                        localNovelBookArtifactBuilderFactory()
                            .ensureArtifact(novelToCompile, syncedChapters)
                    },
                )
            }
        }
    }

    private data class LocalBookMetadata(
        val title: String?,
        val author: String?,
        val description: String?,
    )

    /**
     * Imports several picked book files one by one; a failure of any single file
     * does not abort the rest. Returns (succeeded, total).
     */
    suspend fun importLocalBooks(uris: List<Uri>): Pair<Int, Int> {
        var succeeded = 0
        uris.forEach { uri ->
            // D8: rethrow CancellationException - swallowing it kept the loop spinning after a
            // cancel (each next withContext rethrew and was caught again), reporting 0 succeeded.
            runCatching { importLocalBook(uri) }
                .onFailure { error -> if (error is CancellationException) throw error }
                .onSuccess { succeeded++ }
        }
        return succeeded to uris.size
    }

    /**
     * Imports a SAF-picked folder as a local novel: every supported file inside
     * (any nesting depth) is copied into localnovel/<folderName>/ preserving the
     * relative structure, and a LocalNovel row is inserted. Chapters come from the
     * regular LocalNovelSource directory pipeline (one file = one chapter).
     */
    suspend fun importLocalBookFolder(treeUri: Uri) {
        withContext(Dispatchers.IO) {
            val context = Injekt.get<Application>()
            val storageManager = Injekt.get<StorageManager>()
            val localDir = storageManager.getLocalNovelSourceDirectory()
                ?: throw IOException("Local novel directory not configured. Set a storage location first.")
            val novelRepository = Injekt.get<NovelRepository>()
            val sourcePreferences = Injekt.get<SourcePreferences>()

            val tree = DocumentFile.fromTreeUri(context, treeUri)
                ?: throw IOException("Cannot open selected folder")
            val folderName = LocalNovelBookImport.sanitizeFileName(
                tree.name?.takeIf { it.isNotBlank() } ?: "imported-folder",
            )
            val targetDir = localDir.findFile(folderName)?.takeIf { it.isDirectory }
                ?: localDir.createDirectory(folderName)
                ?: throw IOException("Cannot create folder: $folderName")

            val copied = copySupportedFilesRecursively(context, tree, targetDir)
            if (copied == 0) throw IOException("No supported files found in the selected folder")

            val addToLibrary = sourcePreferences.importEpubAddToLibrary().get()
            val novel = Novel.create().copy(
                source = LocalNovelSource.ID,
                url = folderName,
                title = folderName,
                favorite = addToLibrary,
                dateAdded = if (addToLibrary) System.currentTimeMillis() else 0L,
                initialized = true,
            )
            novelRepository.insertNovel(novel)
        }
    }

    private fun copySupportedFilesRecursively(
        context: Application,
        sourceDir: DocumentFile,
        targetDir: com.hippo.unifile.UniFile,
    ): Int {
        var copied = 0
        for (entry in sourceDir.listFiles()) {
            val entryName = entry.name.orEmpty()
            if (entryName.isBlank() || entryName.startsWith('.')) continue
            if (entry.isDirectory) {
                val childTarget = targetDir.findFile(entryName)
                    ?.takeIf { it.isDirectory }
                    ?: targetDir.createDirectory(entryName) ?: continue
                copied += copySupportedFilesRecursively(context, entry, childTarget)
            } else {
                if (!LocalNovelBookImport.isSupportedImportFileName(entryName)) continue
                val safeName = LocalNovelBookImport.sanitizeFileName(entryName)
                // D8: copy via a temp name and rename on success - a failed copy used to leave a
                // truncated chapter under the final filename inside the imported folder.
                val tempName = "$safeName.importing"
                targetDir.findFile(tempName)?.delete()
                val temp = targetDir.createFile(tempName) ?: continue
                val finalized = runCatching {
                    context.contentResolver.openInputStream(entry.uri)?.use { input ->
                        temp.openOutputStream().use { output -> input.copyTo(output) }
                    } ?: throw IOException("Cannot open ${entry.name}")
                    targetDir.findFile(safeName)?.takeIf { it.isFile }?.delete()
                    temp.renameTo(safeName)
                }.getOrElse {
                    temp.delete()
                    false
                }
                if (finalized) copied++
            }
        }
        return copied
    }

    private fun readLocalBookMetadata(
        context: Application,
        targetFile: com.hippo.unifile.UniFile,
        extension: String,
    ): LocalBookMetadata {
        return when (extension) {
            "epub" -> readEpubMetadata(context, targetFile)
            "fb2" -> readFb2Metadata(targetFile)
            else -> LocalBookMetadata(null, null, null)
        }
    }

    private fun readEpubMetadata(
        context: Application,
        targetFile: com.hippo.unifile.UniFile,
    ): LocalBookMetadata {
        return try {
            targetFile.epubReader(context).use { epub ->
                val ref = epub.getPackageHref()
                val doc = epub.getPackageDocument(ref)

                var title = doc.getElementsByTag("dc:title").firstOrNull()?.text()
                if (title.isNullOrBlank()) {
                    title = doc.select("docTitle").firstOrNull()?.text()
                }
                if (title.isNullOrBlank()) {
                    title = doc.select("meta[name=title]").firstOrNull()?.attr("content")
                }

                val collection = doc.select("meta[property=belongs-to-collection]").firstOrNull()?.text()
                val resolvedTitle = collection.takeIf { !it.isNullOrBlank() } ?: title
                val author = doc.getElementsByTag("dc:creator").firstOrNull()?.text()

                var desc = doc.getElementsByTag("dc:description").firstOrNull()?.text()
                if (desc.isNullOrBlank()) {
                    desc = doc.select("dc\\:description").firstOrNull()?.text()
                }

                LocalBookMetadata(
                    title = resolvedTitle?.trim()?.takeIf { it.isNotBlank() },
                    author = author?.trim()?.takeIf { it.isNotBlank() },
                    description = desc?.trim()?.takeIf { it.isNotBlank() },
                )
            }
        } catch (_: Exception) {
            LocalBookMetadata(null, null, null)
        }
    }

    private fun readFb2Metadata(targetFile: com.hippo.unifile.UniFile): LocalBookMetadata {
        return try {
            targetFile.openInputStream().use { stream ->
                val book = Fb2Book.parse(stream)
                LocalBookMetadata(
                    title = book.bookTitle,
                    author = book.authors.takeIf { it.isNotEmpty() }?.joinToString(", "),
                    description = book.annotation,
                )
            }
        } catch (_: Exception) {
            LocalBookMetadata(null, null, null)
        }
    }

    private fun getLocalBookDisplayName(context: Application, uri: Uri): String {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) return name
                }
            }
            return cursor.runCatching { getString(0) }.getOrNull() ?: uri.lastPathSegment ?: "untitled.epub"
        }
        return uri.lastPathSegment ?: "untitled.epub"
    }
    fun openCreateSeries() {
        mutableState.update { it.copy(dialog = Dialog.CreateSeries) }
    }

    fun openAddToSeries() {
        screenModelScope.launchIO {
            val allSeries = getLibraryNovelSeries.subscribe().first()
            val series = allSeries.map { it.series }
            mutableState.update { it.copy(dialog = Dialog.AddToSeries(series)) }
        }
    }

    fun createSeries(name: String) {
        val selection = state.value.selection
        if (selection.isEmpty()) return

        // D3: capture the ids and clear the selection synchronously - clearing only after the
        // DB inserts left a window where a re-opened dialog passed the guard again and created
        // a duplicate series. D2: non-cancellable so navigation cannot cut the writes in half.
        val novelIds = selection.filterIsInstance<NovelLibraryItem.Single>().map { it.libraryNovel.novel.id }
        clearSelection()
        if (novelIds.isEmpty()) return
        screenModelScope.launchNonCancellable {
            createNovelSeries.await(name, 0L, novelIds)
        }
    }

    fun addSelectionToSeries(series: NovelSeries) {
        val selection = state.value.selection
        if (selection.isEmpty()) return

        // D3/D2: see createSeries.
        val novelIds = selection.filterIsInstance<NovelLibraryItem.Single>().map { it.libraryNovel.novel.id }
        clearSelection()
        if (novelIds.isEmpty()) return
        screenModelScope.launchNonCancellable {
            addNovelsToSeries.await(series.id, novelIds)
        }
    }

    fun togglePinned(item: NovelLibraryItem) {
        setPinned(item, !item.pinned)
    }

    fun setPinned(item: NovelLibraryItem, pinned: Boolean) {
        // D1: non-cancellable - a tab switch mid-write used to silently drop the pin.
        screenModelScope.launchNonCancellable {
            setPinnedInternal(item, pinned)
        }
    }

    /**
     * D1: one non-cancellable batch for the bottom-menu pin action - the previous per-item
     * cancellable launches could be cut in half by a tab switch (partial pin application).
     */
    fun setPinnedSelection(pinned: Boolean) {
        val items = state.value.selection.toList()
        if (items.isEmpty()) return
        screenModelScope.launchNonCancellable {
            items.forEach { setPinnedInternal(it, pinned) }
        }
    }

    private suspend fun setPinnedInternal(item: NovelLibraryItem, pinned: Boolean) {
        when (item) {
            is NovelLibraryItem.Single -> updateNovel.await(
                NovelUpdate(
                    id = item.libraryNovel.id,
                    pinned = pinned,
                ),
            )
            is NovelLibraryItem.Series -> updateNovelSeries.await(
                item.librarySeries.series.copy(
                    pinned = pinned,
                ),
            )
        }
    }

    sealed interface Dialog {
        data object Settings : Dialog
        data class ChangeCategory(
            val novels: List<Novel>,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog
        data class DeleteNovels(val novels: List<Novel>) : Dialog
        data object CreateSeries : Dialog
        data class AddToSeries(val series: List<NovelSeries>) : Dialog
    }
}

private fun List<NovelLibraryItem>.selectedNovelEntries(): List<tachiyomi.domain.library.novel.LibraryNovel> {
    return asSequence()
        .flatMap { item ->
            when (item) {
                is NovelLibraryItem.Single -> sequenceOf(item.libraryNovel)
                is NovelLibraryItem.Series -> item.librarySeries.entries.asSequence()
            }
        }
        .distinctBy { it.id }
        .toList()
}

private fun List<NovelLibraryItem>.selectedNovels(): List<Novel> {
    return selectedNovelEntries().map { it.novel }
}

/**
 * Whether the local book artifact should be compiled right after an import.
 *
 * Auto-compilation is opt-in via [SourcePreferences.autoCompileLocalEpubBook] and only makes
 * sense when the novel row was actually inserted. Extracted so the import gate is unit-testable.
 */
internal fun shouldCompileLocalBookArtifact(insertedId: Long?, autoCompileEnabled: Boolean): Boolean {
    return insertedId != null && autoCompileEnabled
}

/**
 * Compiles the local book artifact of a just-imported novel, best effort only.
 *
 * The import itself has already succeeded, so failures here are swallowed: the title screen
 * still compiles the artifact on open (when auto-compile is enabled). Extracted so the import
 * flow is unit-testable without Android/Injekt — the source manager, chapter sync step and
 * artifact builder are injected.
 */
internal suspend fun compileLocalBookArtifact(
    novel: Novel,
    sourceManager: NovelSourceManager,
    syncChapters: suspend (List<SNovelChapter>, Novel, NovelSource) -> List<NovelChapter>,
    compile: suspend (Novel, List<NovelChapter>) -> Boolean,
) {
    runCatching {
        val localSource = sourceManager.get(LocalNovelSource.ID)
        if (localSource != null) {
            val sourceChapters = localSource.getChapterList(novel.toSNovel())
            val syncedChapters = syncChapters(sourceChapters, novel, localSource)
            compile(novel, syncedChapters)
        }
    }
}
