package eu.kanade.tachiyomi.ui.library.novel

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.library.novel.model.NovelLibrarySort
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.novel.LibraryNovel
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.random.Random

class NovelLibraryScreenModel(
    private val getLibraryNovel: GetLibraryNovel = Injekt.get(),
    private val chapterRepository: NovelChapterRepository = Injekt.get(),
    private val basePreferences: BasePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val hasDownloadedChapters: (tachiyomi.domain.entries.novel.model.Novel) -> Boolean = {
        NovelDownloadManager().hasAnyDownloadedChapter(it)
    },
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

    init {
        screenModelScope.launch {
            getLibraryNovel.subscribe()
                .collectLatest { novels ->
                    mutableState.update { current ->
                        val downloadedNovelIds = if (current.effectiveDownloadedFilter != TriState.DISABLED) {
                            resolveDownloadedNovelIds(novels)
                        } else {
                            emptySet()
                        }
                        current.copy(
                            isLoading = false,
                            rawItems = novels,
                            downloadedNovelIds = downloadedNovelIds,
                            items = filterItems(
                                novels = novels,
                                query = current.searchQuery,
                                downloadedFilter = current.effectiveDownloadedFilter,
                                downloadedNovelIds = downloadedNovelIds,
                                unreadFilter = current.unreadFilter,
                                startedFilter = current.startedFilter,
                                bookmarkedFilter = current.bookmarkedFilter,
                                completedFilter = current.completedFilter,
                                filterIntervalCustom = current.filterIntervalCustom,
                                sort = current.sort,
                                randomSortSeed = current.randomSortSeed,
                            ),
                        )
                    }
                }
        }

        screenModelScope.launch {
            combine(
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
                .collectLatest { prefs ->
                    mutableState.update { current ->
                        val effectiveDownloadedFilter = if (prefs.downloadedOnly) {
                            TriState.ENABLED_IS
                        } else {
                            prefs.downloadedFilter
                        }
                        val downloadedNovelIds = if (effectiveDownloadedFilter != TriState.DISABLED) {
                            resolveDownloadedNovelIds(current.rawItems)
                        } else {
                            emptySet()
                        }
                        current.copy(
                            downloadedOnly = prefs.downloadedOnly,
                            downloadedFilter = prefs.downloadedFilter,
                            unreadFilter = prefs.unreadFilter,
                            startedFilter = prefs.startedFilter,
                            bookmarkedFilter = prefs.bookmarkedFilter,
                            completedFilter = prefs.completedFilter,
                            filterIntervalCustom = prefs.filterIntervalCustom,
                            downloadedNovelIds = downloadedNovelIds,
                            items = filterItems(
                                novels = current.rawItems,
                                query = current.searchQuery,
                                downloadedFilter = effectiveDownloadedFilter,
                                downloadedNovelIds = downloadedNovelIds,
                                unreadFilter = prefs.unreadFilter,
                                startedFilter = prefs.startedFilter,
                                bookmarkedFilter = prefs.bookmarkedFilter,
                                completedFilter = prefs.completedFilter,
                                filterIntervalCustom = prefs.filterIntervalCustom,
                                sort = current.sort,
                                randomSortSeed = current.randomSortSeed,
                            ),
                        )
                    }
                }
        }

        screenModelScope.launch {
            combine(
                libraryPreferences.novelSortingMode().changes(),
                libraryPreferences.randomNovelSortSeed().changes(),
            ) { sort, randomSortSeed ->
                sort to randomSortSeed
            }
                .collectLatest { (sort, randomSortSeed) ->
                    mutableState.update { current ->
                        current.copy(
                            sort = sort,
                            randomSortSeed = randomSortSeed,
                            items = filterItems(
                                novels = current.rawItems,
                                query = current.searchQuery,
                                downloadedFilter = current.effectiveDownloadedFilter,
                                downloadedNovelIds = current.downloadedNovelIds,
                                unreadFilter = current.unreadFilter,
                                startedFilter = current.startedFilter,
                                bookmarkedFilter = current.bookmarkedFilter,
                                completedFilter = current.completedFilter,
                                filterIntervalCustom = current.filterIntervalCustom,
                                sort = sort,
                                randomSortSeed = randomSortSeed,
                            ),
                        )
                    }
                }
        }
    }

    fun search(query: String?) {
        mutableState.update { current ->
            val trimmed = query?.trim().orEmpty().ifBlank { null }
            current.copy(
                searchQuery = trimmed,
                items = filterItems(
                    novels = current.rawItems,
                    query = trimmed,
                    downloadedFilter = current.effectiveDownloadedFilter,
                    downloadedNovelIds = current.downloadedNovelIds,
                    unreadFilter = current.unreadFilter,
                    startedFilter = current.startedFilter,
                    bookmarkedFilter = current.bookmarkedFilter,
                    completedFilter = current.completedFilter,
                    filterIntervalCustom = current.filterIntervalCustom,
                    sort = current.sort,
                    randomSortSeed = current.randomSortSeed,
                ),
            )
        }
    }

    fun showSettingsDialog() {
        mutableState.update { it.copy(dialog = Dialog.Settings) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
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
        ).sortedBy { it.sourceOrder }
        if (chapters.isEmpty()) return null

        // Keep reading from an in-progress chapter first.
        chapters.firstOrNull { it.lastPageRead > 0L && !it.read }?.let { return it }

        // Continue from the next unread chapter in reader navigation order.
        val lastReadIndex = chapters.indexOfLast { it.read || it.lastPageRead > 0L }
        if (lastReadIndex >= 0) {
            chapters.drop(lastReadIndex + 1).firstOrNull { !it.read }?.let { return it }
        }

        // Fallback for "nothing read yet".
        return chapters.firstOrNull { !it.read }
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

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val rawItems: List<LibraryNovel> = emptyList(),
        val items: List<LibraryNovel> = emptyList(),
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

        val hasActiveFilters: Boolean
            get() = effectiveDownloadedFilter != TriState.DISABLED ||
                unreadFilter != TriState.DISABLED ||
                startedFilter != TriState.DISABLED ||
                bookmarkedFilter != TriState.DISABLED ||
                completedFilter != TriState.DISABLED ||
                filterIntervalCustom != TriState.DISABLED
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

    sealed interface Dialog {
        data object Settings : Dialog
    }
}
