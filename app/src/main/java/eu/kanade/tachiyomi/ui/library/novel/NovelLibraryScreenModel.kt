package eu.kanade.tachiyomi.ui.library.novel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.asState
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
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
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.entries.novel.NovelDownloadAction
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreenModel
import eu.kanade.tachiyomi.ui.library.sortPinnedSeriesFirst
import eu.kanade.tachiyomi.ui.novel.resolveNovelResumeChapter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.core.common.util.lang.launchIO
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
import tachiyomi.source.local.entries.novel.LocalNovelSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import kotlin.random.Random

typealias NovelLibraryMap = Map<Category, List<NovelLibraryItem>>

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
    private val basePreferences: BasePreferences = Injekt.get(),
    val libraryPreferences: LibraryPreferences = Injekt.get(),
    val sourceManager: NovelSourceManager = Injekt.get(),
    val downloadCache: NovelDownloadCache = Injekt.get(),
    private val novelDownloadManager: NovelDownloadManager = NovelDownloadManager(),
    private val novelTranslatedDownloadManager: NovelTranslatedDownloadManager = NovelTranslatedDownloadManager(),
    private val downloadedIdsFlow: StateFlow<Set<Long>> = downloadCache.downloadedIds,
    private val searchDebounceMillis: Long = SEARCH_DEBOUNCE_MILLIS,
    private val trackerManager: TrackerManager = Injekt.get(),
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

    init {
        screenModelScope.launch {
            combine(
                state.map { it.searchQuery }.debounce(searchDebounceMillis),
                getLibraryFlow(),
                getFilterPreferencesFlow(),
                getSortPreferencesFlow(),
                getTracksPerNovel.subscribe(),
                state.map { it.groupType }.distinctUntilChanged(),
                downloadedIdsFlow,
            ) { flowsArray ->
                val searchQuery = flowsArray[0] as String?

                @Suppress("UNCHECKED_CAST")
                val library = flowsArray[1] as NovelLibraryMap
                val filterPrefs = flowsArray[2] as FilterPreferences
                val sortPrefs = flowsArray[3] as SortPreferences

                @Suppress("UNCHECKED_CAST")
                val tracks = flowsArray[4] as Map<Long, List<NovelTrack>>
                val groupType = flowsArray[5] as Int

                @Suppress("UNCHECKED_CAST")
                val downloadedIds = flowsArray[6] as Set<Long>

                val effectiveDownloadedFilter = if (filterPrefs.downloadedOnly) {
                    TriState.ENABLED_IS
                } else {
                    filterPrefs.downloadedFilter
                }
                val downloadedNovelIds = if (effectiveDownloadedFilter != TriState.DISABLED) {
                    val novelIdSet = library.values.flatten().mapNotNullTo(HashSet()) {
                        (it as? NovelLibraryItem.Single)?.libraryNovel?.novel?.id
                    }
                    downloadedIds.intersect(novelIdSet)
                } else {
                    emptySet()
                }

                library
                    .applyFilters(
                        effectiveDownloadedFilter = effectiveDownloadedFilter,
                        downloadedNovelIds = downloadedNovelIds,
                        unreadFilter = filterPrefs.unreadFilter,
                        startedFilter = filterPrefs.startedFilter,
                        bookmarkedFilter = filterPrefs.bookmarkedFilter,
                        completedFilter = filterPrefs.completedFilter,
                        filterIntervalCustom = filterPrefs.filterIntervalCustom,
                    )
                    .applySort(sortPrefs.sortMode, sortPrefs.randomSortSeed)
                    .applyGrouping(groupType, tracks)
                    .mapValues { (_, value) ->
                        if (searchQuery != null) {
                            value.filter { it.title.contains(searchQuery, ignoreCase = true) }
                        } else {
                            value
                        }
                    }
                    .let { map ->
                        if (groupType == LibraryGroup.BY_DEFAULT || searchQuery != null) {
                            // Keep categories visible when searching so empty-result pages can
                            // still show the global search action.
                            map
                        } else {
                            map.filterValues { it.isNotEmpty() }
                        }
                    }
            }
                .collectLatest { libraryMap ->
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            library = libraryMap,
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

        getFilterPreferencesFlow()
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
                    )
                }
            }
            .launchIn(screenModelScope)

        getSortPreferencesFlow()
            .onEach { sortPrefs ->
                mutableState.update { state ->
                    state.copy(
                        sort = sortPrefs.sortMode,
                        randomSortSeed = sortPrefs.randomSortSeed,
                    )
                }
            }
            .launchIn(screenModelScope)

        getFilterPreferencesFlow()
            .map { filterPrefs ->
                filterPrefs.downloadedOnly ||
                    listOf(
                        filterPrefs.downloadedFilter,
                        filterPrefs.unreadFilter,
                        filterPrefs.startedFilter,
                        filterPrefs.bookmarkedFilter,
                        filterPrefs.completedFilter,
                        filterPrefs.filterIntervalCustom,
                    ).any { it != TriState.DISABLED }
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
            current.copy(selection = persistentListOf<NovelLibraryItem>().addAll(mutable))
        }
    }

    fun toggleRangeSelection(novel: NovelLibraryItem) {
        mutableState.update { current ->
            val mutable = current.selection.toMutableList()
            val lastSelected = mutable.lastOrNull()
            if (lastSelected?.category != novel.category) {
                val existingIndex = mutable.indexOfFirst { it.id == novel.id }
                if (existingIndex >= 0) {
                    mutable.removeAt(existingIndex)
                } else {
                    mutable.add(novel)
                }
                return@update current.copy(selection = persistentListOf<NovelLibraryItem>().addAll(mutable))
            }

            val items = current.library.entries.firstNotNullOfOrNull { entry ->
                entry.value.takeIf { entry.key.id == novel.category }
            }.orEmpty()
            val lastIndex = items.indexOfFirst { it.id == lastSelected.id }
            val currentIndex = items.indexOfFirst { it.id == novel.id }
            if (lastIndex < 0 || currentIndex < 0 || lastIndex == currentIndex) {
                return@update current
            }

            val selectedIds = mutable.map { it.id }.toSet()
            val start = minOf(lastIndex, currentIndex)
            val end = maxOf(lastIndex, currentIndex)
            val toAdd = items
                .subList(start, end + 1)
                .filterNot { it.id in selectedIds }
            mutable.addAll(toAdd)
            current.copy(selection = persistentListOf<NovelLibraryItem>().addAll(mutable))
        }
    }

    fun selectAll(index: Int) {
        mutableState.update { current ->
            val targetCategoryId = current.categories.getOrNull(index)?.id
            val scopeItems = if (targetCategoryId == null) {
                current.library.values.flatten()
            } else {
                current.library.entries.firstNotNullOfOrNull { entry ->
                    entry.value.takeIf { entry.key.id == targetCategoryId }
                }.orEmpty()
            }
            val selectedIds = current.selection.map { it.id }.toSet()
            val mutable = current.selection.toMutableList()
            mutable.addAll(scopeItems.filterNot { it.id in selectedIds })
            current.copy(selection = persistentListOf<NovelLibraryItem>().addAll(mutable))
        }
    }

    fun invertSelection(index: Int) {
        mutableState.update { current ->
            val targetCategoryId = current.categories.getOrNull(index)?.id
            val scopeItems = if (targetCategoryId == null) {
                current.library.values.flatten()
            } else {
                current.library.entries.firstNotNullOfOrNull { entry ->
                    entry.value.takeIf { entry.key.id == targetCategoryId }
                }.orEmpty()
            }
            val selectedIds = current.selection.map { it.id }.toSet()
            val toRemoveIds = scopeItems.filter { it.id in selectedIds }.map { it.id }.toSet()
            val mutable = current.selection.filterNot { it.id in toRemoveIds }.toMutableList()
            mutable.addAll(scopeItems.filterNot { it.id in selectedIds })
            current.copy(selection = persistentListOf<NovelLibraryItem>().addAll(mutable))
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
            val common = getCommonCategories(novels)
            val mix = getMixCategories(novels)
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
        screenModelScope.launchIO {
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
        screenModelScope.launchIO {
            selected.forEach { novel ->
                val chapters = chapterRepository.getChapterByNovelId(
                    novelId = novel.id,
                    applyScanlatorFilter = true,
                )
                if (chapters.isEmpty()) return@forEach
                chapterRepository.updateAllChapters(
                    chapters.map {
                        NovelChapterUpdate(
                            id = it.id,
                            read = read,
                            lastPageRead = if (read) 0L else it.lastPageRead,
                        )
                    },
                )
            }
        }
        clearSelection()
    }

    fun removeNovels(
        novels: List<Novel>,
        deleteFromLibrary: Boolean,
        deleteChapters: Boolean,
    ) {
        screenModelScope.launchIO {
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
            val downloadedChapterIds = chapters
                .filter { chapter ->
                    novelDownloadManager.isChapterDownloaded(novel, chapter.id)
                }
                .mapTo(mutableSetOf()) { it.id }
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
            val downloadedTranslatedIds = chaptersWithCache
                .filter { chapter ->
                    novelTranslatedDownloadManager.isTranslatedChapterDownloaded(
                        novel = novel,
                        chapter = chapter,
                        format = format,
                    )
                }
                .mapTo(mutableSetOf()) { it.id }
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
        return chapters.filterNot { chapter ->
            novelDownloadManager.isChapterDownloaded(novel, chapter.id)
        }
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
        return chaptersWithCache.filterNot { chapter ->
            novelTranslatedDownloadManager.isTranslatedChapterDownloaded(
                novel = novel,
                chapter = chapter,
                format = format,
            )
        }
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
    }

    fun toggleDownloadedFilter() {
        setDownloadedFilter(state.value.downloadedFilter.next())
    }

    fun setDownloadedFilter(filter: TriState) {
        libraryPreferences.filterDownloadedNovel().set(filter)
    }

    fun toggleUnreadFilter() {
        setUnreadFilter(state.value.unreadFilter.next())
    }

    fun setUnreadFilter(filter: TriState) {
        libraryPreferences.filterUnreadNovel().set(filter)
    }

    fun toggleStartedFilter() {
        setStartedFilter(state.value.startedFilter.next())
    }

    fun setStartedFilter(filter: TriState) {
        libraryPreferences.filterStartedNovel().set(filter)
    }

    fun toggleBookmarkedFilter() {
        setBookmarkedFilter(state.value.bookmarkedFilter.next())
    }

    fun setBookmarkedFilter(filter: TriState) {
        libraryPreferences.filterBookmarkedNovel().set(filter)
    }

    fun toggleCompletedFilter() {
        setCompletedFilter(state.value.completedFilter.next())
    }

    fun setCompletedFilter(filter: TriState) {
        libraryPreferences.filterCompletedNovel().set(filter)
    }

    fun toggleIntervalCustomFilter() {
        setIntervalCustomFilter(state.value.filterIntervalCustom.next())
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
        return resolveNovelResumeChapter(chapters)
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

    private suspend fun getCommonCategories(novels: List<Novel>): Collection<Category> {
        if (novels.isEmpty()) return emptyList()
        return novels
            .map { getNovelCategories.await(it.id).toSet() }
            .reduce { left, right -> left.intersect(right) }
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

    private suspend fun getMixCategories(novels: List<Novel>): Collection<Category> {
        if (novels.isEmpty()) return emptyList()
        val novelCategories = novels.map { getNovelCategories.await(it.id).toSet() }
        val common = novelCategories.reduce { left, right -> left.intersect(right) }
        return novelCategories
            .flatten()
            .distinct()
            .subtract(common)
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
    ): NovelLibraryMap {
        val filterFnDownloaded: (NovelLibraryItem) -> Boolean = { item ->
            applyFilter(effectiveDownloadedFilter) {
                (item as? NovelLibraryItem.Single)?.libraryNovel?.novel?.id in downloadedNovelIds
            }
        }
        val filterFnUnread: (NovelLibraryItem) -> Boolean = { item ->
            applyFilter(unreadFilter) { item.unreadCount > 0 }
        }
        val filterFnStarted: (NovelLibraryItem) -> Boolean = { item ->
            applyFilter(startedFilter) { item.hasStarted }
        }
        val filterFnBookmarked: (NovelLibraryItem) -> Boolean = { item ->
            applyFilter(bookmarkedFilter) {
                (item as? NovelLibraryItem.Single)?.libraryNovel?.hasBookmarks == true
            }
        }
        val filterFnCompleted: (NovelLibraryItem) -> Boolean = { item ->
            applyFilter(completedFilter) {
                (item as? NovelLibraryItem.Single)?.libraryNovel?.novel?.status?.toInt() == SManga.COMPLETED
            }
        }
        val filterFnIntervalCustom: (NovelLibraryItem) -> Boolean = { item ->
            applyFilter(filterIntervalCustom) {
                (item as? NovelLibraryItem.Single)?.libraryNovel?.novel?.fetchInterval?.compareTo(0) == -1
            }
        }
        val filterFn: (NovelLibraryItem) -> Boolean = {
            filterFnDownloaded(it) &&
                filterFnUnread(it) &&
                filterFnStarted(it) &&
                filterFnBookmarked(it) &&
                filterFnCompleted(it) &&
                filterFnIntervalCustom(it)
        }
        return mapValues { (_, value) -> value.filter(filterFn) }
    }

    private fun NovelLibraryMap.applySort(
        sort: NovelLibrarySort,
        randomSortSeed: Int,
    ): NovelLibraryMap {
        return mapValues { (_, value) -> sortItems(value, sort, randomSortSeed) }
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
                mapOf(ungroupedCategory to items)
            }
            LibraryGroup.BY_STATUS -> {
                val statusCategories = mutableMapOf<Category, MutableList<NovelLibraryItem>>()
                items.forEach { item ->
                    val single = item as? NovelLibraryItem.Single
                    val status = single?.libraryNovel?.novel?.status ?: 0L
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
                    val category = statusCategories.keys.find { it.id == statusId } ?: Category(
                        id = statusId,
                        name = statusName,
                        order = statusId,
                        flags = sortFlags,
                        hidden = false,
                        hiddenFromHomeHub = false,
                    )
                    statusCategories.getOrPut(category) { mutableListOf() }.add(item)
                }
                statusCategories
            }
            LibraryGroup.BY_SOURCE -> {
                val sourceCategories = mutableMapOf<Category, MutableList<NovelLibraryItem>>()
                items.forEach { item ->
                    val single = item as? NovelLibraryItem.Single
                    val sourceId = single?.libraryNovel?.novel?.source ?: 0L
                    val sourceName = sourceManager.getOrStub(sourceId).name
                    val categoryId = -sourceId - 1000L
                    val category = sourceCategories.keys.find { it.id == categoryId } ?: Category(
                        id = categoryId,
                        name = sourceName,
                        order = categoryId,
                        flags = sortFlags,
                        hidden = false,
                        hiddenFromHomeHub = false,
                    )
                    sourceCategories.getOrPut(category) { mutableListOf() }.add(item)
                }
                sourceCategories
            }
            LibraryGroup.BY_TRACK_STATUS -> {
                val trackMapper = MapNovelTrackStatusToLibrary(trackerManager)
                val trackCategories = mutableMapOf<Category, MutableList<NovelLibraryItem>>()
                items.forEach { item ->
                    val single = item as? NovelLibraryItem.Single
                    val itemTracks = single?.libraryNovel?.novel?.id?.let { tracks[it] }.orEmpty()
                    if (itemTracks.isEmpty()) {
                        val categoryId = -2L
                        val category = trackCategories.keys.find { it.id == categoryId } ?: Category(
                            id = categoryId,
                            name = "Untracked",
                            order = categoryId,
                            flags = sortFlags,
                            hidden = false,
                            hiddenFromHomeHub = false,
                        )
                        trackCategories.getOrPut(category) { mutableListOf() }.add(item)
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
                            val category = trackCategories.keys.find { it.id == statusId } ?: Category(
                                id = statusId,
                                name = statusName,
                                order = statusId,
                                flags = sortFlags,
                                hidden = false,
                                hiddenFromHomeHub = false,
                            )
                            trackCategories.getOrPut(category) { mutableListOf() }.add(item)
                        }
                    }
                }
                trackCategories
            }
            else -> this
        }

        return grouped.entries
            .sortedBy { entry -> entry.key.id }
            .associate { entry -> entry.key to entry.value }
    }

    private fun getLibraryFlow(): Flow<NovelLibraryMap> {
        val libraryNovelsFlow = combine(
            getLibraryNovel.subscribe(),
            getLibraryNovelSeries.subscribe(),
            getNovelIdsInAnySeries.subscribe(),
        ) { novels, series, idsInSeries ->
            val singleItems = novels.filterNot {
                it.novel.id in idsInSeries
            }.map { NovelLibraryItem.Single(it) }
            val seriesItems = series.map { NovelLibraryItem.Series(it) }
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

            displayCategories.associateWith { libraryNovels[it.id].orEmpty() }
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

        val comparator = Comparator<NovelLibraryItem> { left, right ->
            when (sort.type) {
                NovelLibrarySort.Type.Alphabetical -> {
                    left.title.lowercase().compareToWithCollator(right.title.lowercase())
                }
                NovelLibrarySort.Type.LastRead -> left.lastRead.compareTo(right.lastRead)
                NovelLibrarySort.Type.LastUpdate -> {
                    val leftLastUpdate = (left as? NovelLibraryItem.Single)?.libraryNovel?.novel?.lastUpdate ?: 0L
                    val rightLastUpdate = (right as? NovelLibraryItem.Single)?.libraryNovel?.novel?.lastUpdate ?: 0L
                    leftLastUpdate.compareTo(rightLastUpdate)
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
                    val leftLatestUpload = (left as? NovelLibraryItem.Single)?.libraryNovel?.latestUpload ?: 0L
                    val rightLatestUpload = (right as? NovelLibraryItem.Single)?.libraryNovel?.latestUpload ?: 0L
                    leftLatestUpload.compareTo(rightLatestUpload)
                }
                NovelLibrarySort.Type.ChapterFetchDate -> {
                    val leftChapterFetchedAt =
                        (left as? NovelLibraryItem.Single)?.libraryNovel?.chapterFetchedAt ?: 0L
                    val rightChapterFetchedAt =
                        (right as? NovelLibraryItem.Single)?.libraryNovel?.chapterFetchedAt ?: 0L
                    leftChapterFetchedAt.compareTo(rightChapterFetchedAt)
                }
                NovelLibrarySort.Type.DateAdded -> left.dateAdded.compareTo(right.dateAdded)
                NovelLibrarySort.Type.TrackerMean -> 0
                NovelLibrarySort.Type.Random -> 0
            }
        }
            .let { if (sort.isAscending) it else it.reversed() }
            .thenComparator { left, right ->
                left.title.lowercase().compareToWithCollator(right.title.lowercase())
            }

        return items.sortPinnedSeriesFirst(
            isPinned = isPinned,
            isSeries = isSeries,
            comparator = comparator,
            randomSeed = if (sort.type == NovelLibrarySort.Type.Random) randomSortSeed else null,
        )
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
        val library: NovelLibraryMap = emptyMap(),
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
        val downloadedNovelIds: Set<Long> = emptySet(),
        val sort: NovelLibrarySort = NovelLibrarySort.default,
        val randomSortSeed: Int = 0,
        val dialog: Dialog? = null,
    ) {
        val items: List<NovelLibraryItem>
            get() = library.values.flatten()

        val rawItems: List<NovelLibraryItem>
            get() = library.values.flatten()

        val effectiveDownloadedFilter: TriState
            get() = if (downloadedOnly) TriState.ENABLED_IS else downloadedFilter

        private val libraryCount by lazy {
            library.values
                .flatten()
                .distinctBy {
                    when (it) {
                        is NovelLibraryItem.Single -> it.libraryNovel.novel.id
                        is NovelLibraryItem.Series -> it.librarySeries.id
                    }
                }
                .size
        }

        val isLibraryEmpty by lazy { libraryCount == 0 }

        val selectionMode = selection.isNotEmpty()

        val categories = library.keys.toList()

        fun getLibraryItemsByCategoryId(categoryId: Long): List<NovelLibraryItem>? {
            return library.entries.firstOrNull { it.key.id == categoryId }?.value
        }

        fun getLibraryItemsByPage(page: Int): List<NovelLibraryItem> {
            return library.values.toTypedArray().getOrNull(page).orEmpty()
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
    )

    private data class SortPreferences(
        val sortMode: NovelLibrarySort,
        val randomSortSeed: Int,
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

    suspend fun importEpub(uri: Uri) {
        withContext(Dispatchers.IO) {
            val context = Injekt.get<Application>()
            val storageManager = Injekt.get<StorageManager>()
            val localDir = storageManager.getLocalNovelSourceDirectory()
                ?: throw IOException("Local novel directory not configured. Set a storage location first.")
            val novelRepository = Injekt.get<NovelRepository>()
            val sourcePreferences = Injekt.get<SourcePreferences>()

            val displayName = getEpubDisplayName(context, uri)
            val sanitizedName = displayName.replace("[/\\\\:*?\"<>|]".toRegex(), "_")

            val targetFile = localDir.findFile(sanitizedName)
                ?: localDir.createFile(sanitizedName)
                ?: throw IOException("Cannot create file: $sanitizedName")

            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.openOutputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val addToLibrary = sourcePreferences.importEpubAddToLibrary().get()
            val novel = Novel.create().copy(
                source = LocalNovelSource.ID,
                url = sanitizedName,
                title = sanitizedName.removeSuffix(".epub").removeSuffix(".EPUB"),
                favorite = addToLibrary,
                dateAdded = if (addToLibrary) System.currentTimeMillis() else 0L,
                initialized = true,
            )

            novelRepository.insertNovel(novel)
        }
    }

    private fun getEpubDisplayName(context: Application, uri: Uri): String {
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

        screenModelScope.launchIO {
            val novelIds = selection.filterIsInstance<NovelLibraryItem.Single>().map { it.libraryNovel.novel.id }
            if (novelIds.isNotEmpty()) {
                createNovelSeries.await(name, 0L, novelIds)
            }
            clearSelection()
        }
    }

    fun addSelectionToSeries(series: NovelSeries) {
        val selection = state.value.selection
        if (selection.isEmpty()) return

        screenModelScope.launchIO {
            val novelIds = selection.filterIsInstance<NovelLibraryItem.Single>().map { it.libraryNovel.novel.id }
            if (novelIds.isNotEmpty()) {
                addNovelsToSeries.await(series.id, novelIds)
            }
            clearSelection()
        }
    }

    fun togglePinned(item: NovelLibraryItem) {
        setPinned(item, !item.pinned)
    }

    fun setPinned(item: NovelLibraryItem, pinned: Boolean) {
        screenModelScope.launchIO {
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
