package eu.kanade.tachiyomi.ui.library.novel

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadCache
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadQueueManager
import eu.kanade.tachiyomi.data.download.novel.NovelTranslatedDownloadFormat
import eu.kanade.tachiyomi.data.download.novel.NovelTranslatedDownloadManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.entries.novel.NovelDownloadAction
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreenModel
import eu.kanade.tachiyomi.ui.novel.resolveNovelResumeChapter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.category.novel.interactor.SetNovelCategories
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.items.novelchapter.service.getNovelChapterSort
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.library.novel.model.NovelLibrarySort
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.random.Random

class NovelLibraryScreenModel(
    private val getLibraryNovel: GetLibraryNovel = Injekt.get(),
    private val getNovelCategories: GetNovelCategories = Injekt.get(),
    private val setNovelCategories: SetNovelCategories = Injekt.get(),
    private val updateNovel: UpdateNovel = Injekt.get(),
    private val chapterRepository: NovelChapterRepository = Injekt.get(),
    private val basePreferences: BasePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val novelDownloadManager: NovelDownloadManager = NovelDownloadManager(),
    private val novelTranslatedDownloadManager: NovelTranslatedDownloadManager = NovelTranslatedDownloadManager(),
    private val downloadCacheChanges: Flow<Unit> = Injekt.get<NovelDownloadCache>().changes,
    private val hasDownloadedChapters: (tachiyomi.domain.entries.novel.model.Novel) -> Boolean = {
        Injekt.get<NovelDownloadCache>().hasAnyDownloadedChapter(it)
    },
    private val downloadedIdsDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val searchDebounceMillis: Long = SEARCH_DEBOUNCE_MILLIS,
) : StateScreenModel<NovelLibraryScreenModel.State>(
    State(
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
    var activeCategoryIndex: Int by mutableStateOf(0)

    init {
        screenModelScope.launch {
            combine(
                flow = state
                    .map { screenState -> screenState.searchQuery }
                    .debounce { query: String? ->
                        if (query.isNullOrBlank()) 0L else searchDebounceMillis
                    }
                    .distinctUntilChanged(),
                flow2 = getLibraryNovel.subscribe(),
                flow3 = getFilterPreferencesFlow(),
                flow4 = getSortPreferencesFlow(),
                flow5 = downloadCacheChanges.onStart { emit(Unit) },
                transform = {
                        query: String?,
                        novels: List<LibraryNovel>,
                        filterPrefs: FilterPreferences,
                        sortPrefs: SortPreferences,
                        _: Unit,
                    ->
                    RecomputeInput(
                        query = query,
                        novels = novels,
                        filterPreferences = filterPrefs,
                        sortPreferences = sortPrefs,
                    )
                },
            )
                .collectLatest { input ->
                    val effectiveDownloadedFilter = if (input.filterPreferences.downloadedOnly) {
                        TriState.ENABLED_IS
                    } else {
                        input.filterPreferences.downloadedFilter
                    }
                    val downloadedNovelIds = resolveDownloadedNovelIdsForFilter(
                        novels = input.novels,
                        shouldResolve = effectiveDownloadedFilter != TriState.DISABLED,
                    )
                    val recomputed = RecomputedState(
                        items = filterItems(
                            novels = input.novels,
                            query = input.query,
                            downloadedFilter = effectiveDownloadedFilter,
                            downloadedNovelIds = downloadedNovelIds,
                            unreadFilter = input.filterPreferences.unreadFilter,
                            startedFilter = input.filterPreferences.startedFilter,
                            bookmarkedFilter = input.filterPreferences.bookmarkedFilter,
                            completedFilter = input.filterPreferences.completedFilter,
                            filterIntervalCustom = input.filterPreferences.filterIntervalCustom,
                            sort = input.sortPreferences.sort,
                            randomSortSeed = input.sortPreferences.randomSortSeed,
                        ),
                        downloadedNovelIds = downloadedNovelIds,
                    )
                    mutableState.update { current ->
                        current.copy(
                            isLoading = false,
                            rawItems = input.novels,
                            items = recomputed.items,
                            searchQuery = input.query,
                            downloadedOnly = input.filterPreferences.downloadedOnly,
                            downloadedFilter = input.filterPreferences.downloadedFilter,
                            unreadFilter = input.filterPreferences.unreadFilter,
                            startedFilter = input.filterPreferences.startedFilter,
                            bookmarkedFilter = input.filterPreferences.bookmarkedFilter,
                            completedFilter = input.filterPreferences.completedFilter,
                            filterIntervalCustom = input.filterPreferences.filterIntervalCustom,
                            downloadedNovelIds = recomputed.downloadedNovelIds,
                            sort = input.sortPreferences.sort,
                            randomSortSeed = input.sortPreferences.randomSortSeed,
                        )
                    }
                }
        }
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

    fun toggleSelection(novel: LibraryNovel) {
        mutableState.update { current ->
            val mutable = current.selection.toMutableList()
            val existingIndex = mutable.indexOfFirst { it.id == novel.id }
            if (existingIndex >= 0) {
                mutable.removeAt(existingIndex)
            } else {
                mutable.add(novel)
            }
            current.copy(selection = persistentListOf<LibraryNovel>().addAll(mutable))
        }
    }

    fun toggleRangeSelection(novel: LibraryNovel) {
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
                return@update current.copy(selection = persistentListOf<LibraryNovel>().addAll(mutable))
            }

            val items = current.items.filter { it.category == novel.category }
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
            current.copy(selection = persistentListOf<LibraryNovel>().addAll(mutable))
        }
    }

    fun selectAll(index: Int) {
        mutableState.update { current ->
            val targetCategoryId = current.visibleCategoryIds().getOrNull(index)
            val scopeItems = if (targetCategoryId == null) {
                current.items
            } else {
                current.items.filter { it.category == targetCategoryId }
            }
            val selectedIds = current.selection.map { it.id }.toSet()
            val mutable = current.selection.toMutableList()
            mutable.addAll(scopeItems.filterNot { it.id in selectedIds })
            current.copy(selection = persistentListOf<LibraryNovel>().addAll(mutable))
        }
    }

    fun invertSelection(index: Int) {
        mutableState.update { current ->
            val targetCategoryId = current.visibleCategoryIds().getOrNull(index)
            val scopeItems = if (targetCategoryId == null) {
                current.items
            } else {
                current.items.filter { it.category == targetCategoryId }
            }
            val selectedIds = current.selection.map { it.id }.toSet()
            val toRemoveIds = scopeItems.filter { it.id in selectedIds }.map { it.id }.toSet()
            val mutable = current.selection.filterNot { it.id in toRemoveIds }.toMutableList()
            mutable.addAll(scopeItems.filterNot { it.id in selectedIds })
            current.copy(selection = persistentListOf<LibraryNovel>().addAll(mutable))
        }
    }

    fun openChangeCategoryDialog() {
        val novels = state.value.selection.map { it.novel }.distinctBy { it.id }
        openChangeCategoryDialog(novels)
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
        val novels = state.value.selection.map { it.novel }.distinctBy { it.id }
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
        val selected = state.value.selection.map { it.novel }.distinctBy { it.id }
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
        val selected = state.value.selection.map { it.novel }.distinctBy { it.id }
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
        val selected = state.value.selection.map { it.novel }.distinctBy { it.id }
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
        val novel = state.value.selection.singleOrNull()?.novel ?: return emptyList()
        val chapters = getSortedNovelChapters(novel)
        if (!onlyNotDownloaded) return chapters
        return chapters.filterNot { chapter ->
            novelDownloadManager.isChapterDownloaded(novel, chapter.id)
        }
    }

    suspend fun runDownloadForSingleSelectionChapterIds(chapterIds: Set<Long>): Int {
        if (chapterIds.isEmpty()) return 0
        val novel = state.value.selection.singleOrNull()?.novel ?: return 0
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
        val novel = state.value.selection.singleOrNull()?.novel ?: return emptyList()
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
        val novel = state.value.selection.singleOrNull()?.novel ?: return 0
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
                )
            }
            .filterNot(Category::isSystemCategory)
    }

    private fun filterItems(
        novels: List<LibraryNovel>,
        query: String?,
        downloadedFilter: TriState,
        downloadedNovelIds: Set<Long>,
        unreadFilter: TriState,
        startedFilter: TriState,
        bookmarkedFilter: TriState,
        completedFilter: TriState,
        filterIntervalCustom: TriState,
        sort: NovelLibrarySort,
        randomSortSeed: Int,
    ): List<LibraryNovel> {
        var filtered = novels
        if (!query.isNullOrBlank()) {
            filtered = filtered.filter { it.novel.title.contains(query, ignoreCase = true) }
        }
        filtered = applyFilter(filtered, downloadedFilter) { it.novel.id in downloadedNovelIds }
        filtered = applyFilter(filtered, unreadFilter) { it.unreadCount > 0 }
        filtered = applyFilter(filtered, startedFilter) { it.hasStarted }
        filtered = applyFilter(filtered, bookmarkedFilter) { it.hasBookmarks }
        filtered = applyFilter(filtered, completedFilter) {
            it.novel.status.toInt() == SManga.COMPLETED
        }
        filtered = applyFilter(filtered, filterIntervalCustom) { it.novel.fetchInterval < 0 }

        return sortItems(filtered, sort, randomSortSeed)
    }

    private fun applyFilter(
        items: List<LibraryNovel>,
        filter: TriState,
        predicate: (LibraryNovel) -> Boolean,
    ): List<LibraryNovel> {
        return when (filter) {
            TriState.DISABLED -> items
            TriState.ENABLED_IS -> items.filter(predicate)
            TriState.ENABLED_NOT -> items.filterNot(predicate)
        }
    }

    private fun resolveDownloadedNovelIds(novels: List<LibraryNovel>): Set<Long> {
        return novels.asSequence()
            .mapNotNull { libraryNovel ->
                libraryNovel.novel.id.takeIf { hasDownloadedChapters(libraryNovel.novel) }
            }
            .toSet()
    }

    private suspend fun resolveDownloadedNovelIdsForFilter(
        novels: List<LibraryNovel>,
        shouldResolve: Boolean,
    ): Set<Long> {
        if (!shouldResolve || novels.isEmpty()) return emptySet()
        return withContext(downloadedIdsDispatcher) {
            resolveDownloadedNovelIds(novels)
        }
    }

    private fun sortItems(
        items: List<LibraryNovel>,
        sort: NovelLibrarySort,
        randomSortSeed: Int,
    ): List<LibraryNovel> {
        if (items.isEmpty()) return items
        if (sort.type == NovelLibrarySort.Type.Random) {
            return items.shuffled(Random(randomSortSeed))
        }

        val sorted = items.sortedWith(
            Comparator<LibraryNovel> { left, right ->
                when (sort.type) {
                    NovelLibrarySort.Type.Alphabetical -> {
                        left.novel.title.lowercase().compareToWithCollator(right.novel.title.lowercase())
                    }
                    NovelLibrarySort.Type.LastRead -> left.lastRead.compareTo(right.lastRead)
                    NovelLibrarySort.Type.LastUpdate -> left.novel.lastUpdate.compareTo(right.novel.lastUpdate)
                    NovelLibrarySort.Type.UnreadCount -> {
                        when {
                            left.unreadCount == right.unreadCount -> 0
                            left.unreadCount == 0L -> if (sort.isAscending) 1 else -1
                            right.unreadCount == 0L -> if (sort.isAscending) -1 else 1
                            else -> left.unreadCount.compareTo(right.unreadCount)
                        }
                    }
                    NovelLibrarySort.Type.TotalChapters -> left.totalChapters.compareTo(right.totalChapters)
                    NovelLibrarySort.Type.LatestChapter -> left.latestUpload.compareTo(right.latestUpload)
                    NovelLibrarySort.Type.ChapterFetchDate -> left.chapterFetchedAt.compareTo(right.chapterFetchedAt)
                    NovelLibrarySort.Type.DateAdded -> left.novel.dateAdded.compareTo(right.novel.dateAdded)
                    NovelLibrarySort.Type.TrackerMean -> 0
                    NovelLibrarySort.Type.Random -> 0
                }
            }
                .let { if (sort.isAscending) it else it.reversed() }
                .thenComparator { left, right ->
                    left.novel.title.lowercase().compareToWithCollator(right.novel.title.lowercase())
                },
        )

        return sorted
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

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val rawItems: List<LibraryNovel> = emptyList(),
        val items: List<LibraryNovel> = emptyList(),
        val selection: PersistentList<LibraryNovel> = persistentListOf(),
        val searchQuery: String? = null,
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
        val effectiveDownloadedFilter: TriState
            get() = if (downloadedOnly) TriState.ENABLED_IS else downloadedFilter

        val isLibraryEmpty: Boolean
            get() = rawItems.isEmpty()

        val selectionMode: Boolean
            get() = selection.isNotEmpty()

        val hasActiveFilters: Boolean
            get() = effectiveDownloadedFilter != TriState.DISABLED ||
                unreadFilter != TriState.DISABLED ||
                startedFilter != TriState.DISABLED ||
                bookmarkedFilter != TriState.DISABLED ||
                completedFilter != TriState.DISABLED ||
                filterIntervalCustom != TriState.DISABLED

        fun visibleCategoryIds(): List<Long> {
            return items.map { it.category }.distinct()
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
        val sort: NovelLibrarySort,
        val randomSortSeed: Int,
    )

    private data class RecomputeInput(
        val query: String?,
        val novels: List<LibraryNovel>,
        val filterPreferences: FilterPreferences,
        val sortPreferences: SortPreferences,
    )

    private data class RecomputedState(
        val items: List<LibraryNovel>,
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
                sort = sort,
                randomSortSeed = randomSortSeed,
            )
        }.distinctUntilChanged()
    }

    sealed interface Dialog {
        data object Settings : Dialog
        data class ChangeCategory(
            val novels: List<Novel>,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog
        data class DeleteNovels(val novels: List<Novel>) : Dialog
    }
}
