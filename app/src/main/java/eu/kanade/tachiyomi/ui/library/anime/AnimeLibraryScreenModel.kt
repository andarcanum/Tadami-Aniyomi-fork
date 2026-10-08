package eu.kanade.tachiyomi.ui.library.anime

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapNotNull
import cafe.adriel.voyager.core.model.StateScreenModel
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.core.preference.asState
import eu.kanade.core.util.fastFilterNot
import eu.kanade.core.util.fastPartition
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.items.episode.interactor.SetSeenStatus
import eu.kanade.domain.track.anime.MapAnimeTrackStatusToLibrary
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.library.LibrarySearchQuery
import eu.kanade.tachiyomi.ui.library.leadingDebounce
import eu.kanade.tachiyomi.ui.library.resolveLibraryRangeSelectionAdditions
import eu.kanade.tachiyomi.ui.library.sortPinnedSeriesFirst
import eu.kanade.tachiyomi.util.episode.getNextUnseen
import eu.kanade.tachiyomi.util.removeBackgrounds
import eu.kanade.tachiyomi.util.removeCovers
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
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
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.category.anime.interactor.GetVisibleAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.entries.applyFilter
import tachiyomi.domain.history.anime.interactor.GetNextEpisodes
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.anime.model.AnimeLibrarySort
import tachiyomi.domain.library.anime.model.sort
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibraryTrackStatus
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.series.anime.interactor.AddAnimesToSeries
import tachiyomi.domain.series.anime.interactor.CreateAnimeSeries
import tachiyomi.domain.series.anime.interactor.DeleteAnimeSeries
import tachiyomi.domain.series.anime.interactor.GetAnimeIdsInAnySeries
import tachiyomi.domain.series.anime.interactor.GetLibraryAnimeSeries
import tachiyomi.domain.series.anime.interactor.RemoveAnimeFromSeries
import tachiyomi.domain.series.anime.interactor.UpdateAnimeSeries
import tachiyomi.domain.series.anime.model.AnimeSeries
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetTracksPerAnime
import tachiyomi.domain.track.anime.model.AnimeTrack
import tachiyomi.source.local.entries.anime.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.random.Random

/**
 * Typealias for the library anime, using the category as keys, and list of anime as values.
 */
typealias AnimeLibraryMap = PersistentMap<Category, PersistentList<AnimeLibraryItem>>

class AnimeLibraryScreenModel(
    private val getLibraryAnime: GetLibraryAnime = Injekt.get(),
    private val getLibraryAnimeSeries: GetLibraryAnimeSeries = Injekt.get(),
    private val getAnimeIdsInAnySeries: GetAnimeIdsInAnySeries = Injekt.get(),
    private val getCategories: GetVisibleAnimeCategories = Injekt.get(),
    private val getTracksPerAnime: GetTracksPerAnime = Injekt.get(),
    private val getNextEpisodes: GetNextEpisodes = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val setSeenStatus: SetSeenStatus = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val setAnimeCategories: SetAnimeCategories = Injekt.get(),
    private val createAnimeSeries: CreateAnimeSeries = Injekt.get(),
    private val addAnimesToSeries: AddAnimesToSeries = Injekt.get(),
    private val updateAnimeSeries: UpdateAnimeSeries = Injekt.get(),
    private val deleteAnimeSeries: DeleteAnimeSeries = Injekt.get(),
    private val removeAnimeFromSeries: RemoveAnimeFromSeries = Injekt.get(),
    private val preferences: BasePreferences = Injekt.get(),
    val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val coverCache: AnimeCoverCache = Injekt.get(),
    private val backgroundCache: AnimeBackgroundCache = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val downloadManager: AnimeDownloadManager = Injekt.get(),
    private val downloadCache: AnimeDownloadCache = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    private val startActive: Boolean = true,
    private val libraryDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : StateScreenModel<AnimeLibraryScreenModel.State>(
    State(
        groupType = if (libraryPreferences.globalGroupLibrary().get()) {
            libraryPreferences.globalGroupLibraryBy().get()
        } else {
            libraryPreferences.animeGroupLibraryBy().get()
        },
    ),
) {

    /**
     * Owned process-lifetime scope - deliberately NOT Voyager's screenModelScope store
     * dependency. This model is held by tab data objects for the whole process (J1), but
     * ScreenModelStore keys the scope of an UNREGISTERED model under lastScreenModelKey -
     * whichever screen model was remembered last app-wide (e.g. a pushed MangaScreen). When
     * that screen pops, the store's prefix sweep cancels the borrowed scope: every pipeline
     * and preference collector of this model dies silently, later gate re-writes are no-ops
     * and the library section is stuck on LoadingScreen until a process restart (the v0.62.8
     * "Manga section spins forever after finishing a manhwa" report). This member shadows the
     * imported extension for the whole class; same pattern as ReaderSettingsScreenModel.
     * Regression net: LibrarySharedModelScopeTest.
     */
    private val screenModelScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineName("AnimeLibraryScreenModel"),
    )

    override fun onDispose() {
        super.onDispose()
        screenModelScope.cancel()
    }

    var activeCategoryIndex: Int by libraryPreferences.lastUsedAnimeCategory().asState(
        screenModelScope,
    )

    // F1: one shared instance - the raw factory was subscribed 4+ times inside the same
    // pipeline, and each copy emitted independently into the top-level combine, so a single
    // preference toggle re-ran the full O(N log N) library recompute up to 4 times.
    private val sharedItemPreferencesFlow: Flow<ItemPreferences> =
        getAnimelibItemPreferencesFlow()
            .distinctUntilChanged()
            .shareIn(screenModelScope, SharingStarted.Eagerly, replay = 1)

    private val libraryPipelineActive = MutableStateFlow(startActive)

    /** I21: visibility gate - the tab stops the pipeline when it leaves composition. */
    fun setLibraryPipelineActive(active: Boolean) {
        libraryPipelineActive.value = active
    }

    init {
        screenModelScope.launch {
            // I21: visibility-gated pipeline (manga/novel mirror this structure) - with the
            // tab-held models (J1) the screen model outlives composition, so the full library
            // recompute must stop when the library tab is not composed.
            libraryPipelineActive
                .flatMapLatest { active ->
                    if (!active) {
                        emptyFlow<Triple<AnimeLibraryMap, List<String>, Int>>()
                    } else {
                        val baseLibraryFlow = combine(
                            getLibraryFlow(),
                            getTracksPerAnime.subscribe(),
                            getTrackingFilterFlow(),
                            state.map { it.groupType }.distinctUntilChanged(),
                            getDownloadFilterInvalidationFlow(),
                            sharedItemPreferencesFlow,
                        ) { flowsArray ->
                            @Suppress("UNCHECKED_CAST")
                            val library = flowsArray[0] as AnimeLibraryMap

                            @Suppress("UNCHECKED_CAST")
                            val tracks = flowsArray[1] as Map<Long, List<AnimeTrack>>

                            @Suppress("UNCHECKED_CAST")
                            val trackingFilter = flowsArray[2] as Map<Long, TriState>
                            val groupType = flowsArray[3] as Int
                            val itemPreferences = flowsArray[5] as ItemPreferences
                            val hasActiveFilters = itemPreferences.hasActiveFilters(trackingFilter)
                            val sourceCategories = library.keys.toList()

                            val languageCache = HashMap<Long, String>()
                            // F4: collect the language set in place - the whole-library flatten+map ran on
                            // every emission and allocated an item-sized list just to derive a tiny set.
                            val languageSet = HashSet<String>()
                            library.values.forEach { items ->
                                items.forEach { item ->
                                    val source = (item as? AnimeLibraryItem.Single)?.libraryAnime?.anime?.source
                                        ?: (item as? AnimeLibraryItem.Series)?.coverAnime?.source
                                    source?.let {
                                        languageSet += languageCache.getOrPut(it) {
                                            sourceManager.getOrStub(it).lang
                                        }
                                    }
                                }
                            }
                            val libraryLanguages = languageSet.sorted()

                            AnimeBaseLibraryResult(
                                groupType = groupType,
                                hasActiveFilters = hasActiveFilters,
                                libraryLanguages = libraryLanguages,
                                library = library
                                    .applyFilters(itemPreferences, tracks, trackingFilter)
                                    // E1: grouping BEFORE sorting - applySort sorts each map key's list, and
                                    // the pseudo-categories produced by applyGrouping carry the effective
                                    // global sort in their flags (SetSortModeForAnimeCategory persists it
                                    // when grouping is active). The old order sorted only within real
                                    // categories, so grouped views showed a concatenation of per-category
                                    // runs and the sort selection was silently ignored.
                                    .applyGrouping(groupType, tracks)
                                    .applySort(tracks, trackingFilter.keys)
                                    .withFilteredEmptyPlaceholder(sourceCategories, hasActiveFilters),
                            )
                        }

                        combine(
                            baseLibraryFlow,
                            state.map { it.searchQuery }.distinctUntilChanged()
                                .leadingDebounce(SEARCH_DEBOUNCE_MILLIS),
                        ) { baseLibrary, searchQuery ->
                            val librarySearchQuery = searchQuery?.let(::LibrarySearchQuery)
                            // F4: with no active query the per-category lists are unchanged - rebuilding
                            // the whole PersistentMap on every emission was pure allocation churn.
                            val searchedMap = if (librarySearchQuery == null) {
                                baseLibrary.library
                            } else {
                                baseLibrary.library
                                    .mapValues { (_, value) ->
                                        value.filter { it.matches(librarySearchQuery, sourceManager) }
                                            .toPersistentList()
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
                            // F2: deduped entry count computed here (background dispatcher), once per
                            // emission - the per-State lazy recomputed flatten+distinctBy over the WHOLE
                            // library on MAIN for every selection/keystroke state copy.
                            val libraryCount = filteredMap.values
                                .flatten()
                                .fastDistinctBy {
                                    when (it) {
                                        is AnimeLibraryItem.Single -> it.libraryAnime.anime.id
                                        is AnimeLibraryItem.Series -> it.librarySeries.id
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
            .onEach { (showCategoryTabs, showAnimeCount, showAnimeContinueButton) ->
                mutableState.update { state ->
                    state.copy(
                        showCategoryTabs = showCategoryTabs,
                        showAnimeCount = showAnimeCount,
                        showAnimeContinueButton = showAnimeContinueButton,
                    )
                }
            }
            .launchIn(screenModelScope)

        combine(
            sharedItemPreferencesFlow,
            getTrackingFilterFlow(),
        ) { prefs, trackFilter ->
            prefs.hasActiveFilters(trackFilter)
        }
            .distinctUntilChanged()
            .onEach {
                mutableState.update { state ->
                    state.copy(hasActiveFilters = it)
                }
            }
            .launchIn(screenModelScope)

        sharedItemPreferencesFlow
            .onEach { prefs ->
                mutableState.update { state ->
                    state.copy(languageFilter = prefs.filterLanguages)
                }
            }
            .launchIn(screenModelScope)

        libraryPreferences.globalGroupLibrary().changes()
            .combine(libraryPreferences.globalGroupLibraryBy().changes()) { isGlobal, globalType ->
                isGlobal to globalType
            }
            .combine(libraryPreferences.animeGroupLibraryBy().changes()) { (isGlobal, globalType), mediaType ->
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

    private fun getDownloadFilterInvalidationFlow(): Flow<Unit> {
        return sharedItemPreferencesFlow
            // F1: gate on the CONDITION, not the whole prefs object - any unrelated preference
            // change used to restart this flow and re-emit into the pipeline.
            .map { it.globalFilterDownloaded || it.filterDownloaded != TriState.DISABLED }
            .distinctUntilChanged()
            .flatMapLatest { enabled ->
                if (enabled) {
                    downloadCache.changes.conflate()
                } else {
                    flowOf(Unit)
                }
            }
    }

    private fun getDownloadBadgeInvalidationFlow(): Flow<Unit> {
        return sharedItemPreferencesFlow
            .map { it.downloadBadge } // F1: see getDownloadFilterInvalidationFlow
            .distinctUntilChanged()
            .flatMapLatest { enabled ->
                if (enabled) {
                    downloadCache.changes.conflate()
                } else {
                    flowOf(Unit)
                }
            }
    }

    private suspend fun AnimeLibraryMap.applyFilters(
        prefs: ItemPreferences,
        trackMap: Map<Long, List<AnimeTrack>>,
        trackingFilter: Map<Long, TriState>,
    ): AnimeLibraryMap {
        val downloadedOnly = prefs.globalFilterDownloaded
        val skipOutsideReleasePeriod = prefs.skipOutsideReleasePeriod
        val filterDownloaded = if (downloadedOnly) TriState.ENABLED_IS else prefs.filterDownloaded
        val filterUnseen = prefs.filterUnseen
        val filterStarted = prefs.filterStarted
        val filterBookmarked = prefs.filterBookmarked
        val filterCompleted = prefs.filterCompleted
        val filterIntervalCustom = prefs.filterIntervalCustom
        val filterLanguages = prefs.filterLanguages

        val languageBySourceId = HashMap<Long, String>()
        fun AnimeLibraryItem.sourceLang(): String {
            val sourceId = (this as? AnimeLibraryItem.Single)?.libraryAnime?.anime?.source
                ?: (this as? AnimeLibraryItem.Series)?.coverAnime?.source
                ?: return ""
            return languageBySourceId.getOrPut(sourceId) { sourceManager.getOrStub(sourceId).lang }
        }
        val filterFnLanguage: (AnimeLibraryItem) -> Boolean = { item ->
            filterLanguages.isEmpty() || item.sourceLang() in filterLanguages
        }

        val isNotLoggedInAnyTrack = trackingFilter.isEmpty()

        val excludedTracks = trackingFilter.mapNotNullTo(HashSet()) {
            if (it.value ==
                TriState.ENABLED_NOT
            ) {
                it.key
            } else {
                null
            }
        }
        val includedTracks = trackingFilter.mapNotNullTo(HashSet()) {
            if (it.value ==
                TriState.ENABLED_IS
            ) {
                it.key
            } else {
                null
            }
        }
        val trackFiltersIsIgnored = includedTracks.isEmpty() && excludedTracks.isEmpty()
        val trackIdsByAnimeId = if (trackFiltersIsIgnored) {
            emptyMap()
        } else {
            trackMap.mapValues { entry -> entry.value.mapTo(HashSet(entry.value.size)) { it.trackerId } }
        }

        val filterFnDownloaded: (AnimeLibraryItem) -> Boolean = { item ->
            applyFilter(filterDownloaded) {
                when (item) {
                    is AnimeLibraryItem.Single ->
                        item.libraryAnime.anime.isLocal() ||
                            item.downloadCount > 0 ||
                            downloadManager.getDownloadCount(item.libraryAnime.anime) > 0
                    is AnimeLibraryItem.Series ->
                        item.librarySeries.entries.any {
                            it.anime.isLocal() || downloadManager.getDownloadCount(it.anime) > 0
                        }
                }
            }
        }

        val filterFnUnseen: (AnimeLibraryItem) -> Boolean = { item ->
            applyFilter(filterUnseen) { item.unseenCount > 0 }
        }

        val filterFnStarted: (AnimeLibraryItem) -> Boolean = { item ->
            applyFilter(filterStarted) { item.hasStarted }
        }

        val filterFnBookmarked: (AnimeLibraryItem) -> Boolean = { item ->
            applyFilter(filterBookmarked) { item.hasBookmarks }
        }

        val filterFnCompleted: (AnimeLibraryItem) -> Boolean = { item ->
            applyFilter(filterCompleted) {
                when (item) {
                    is AnimeLibraryItem.Single -> item.libraryAnime.anime.status.toInt() == SAnime.COMPLETED
                    is AnimeLibraryItem.Series -> item.librarySeries.entries.isNotEmpty() &&
                        item.librarySeries.entries.all { it.anime.status.toInt() == SAnime.COMPLETED }
                }
            }
        }

        val filterFnIntervalCustom: (AnimeLibraryItem) -> Boolean = { item ->
            if (skipOutsideReleasePeriod) {
                applyFilter(filterIntervalCustom) {
                    when (item) {
                        is AnimeLibraryItem.Single -> item.libraryAnime.anime.fetchInterval < 0
                        is AnimeLibraryItem.Series -> item.librarySeries.entries.any { it.anime.fetchInterval < 0 }
                    }
                }
            } else {
                true
            }
        }

        val filterFnTracking: (AnimeLibraryItem) -> Boolean = tracking@{ item ->
            if (isNotLoggedInAnyTrack || trackFiltersIsIgnored) return@tracking true

            val animeTracks = when (item) {
                is AnimeLibraryItem.Single -> trackIdsByAnimeId[item.libraryAnime.id].orEmpty()
                is AnimeLibraryItem.Series -> item.librarySeries.entries.flatMap {
                    trackIdsByAnimeId[it.id].orEmpty()
                }.toSet()
            }

            val isExcluded = excludedTracks.isNotEmpty() && animeTracks.any { it in excludedTracks }
            val isIncluded = includedTracks.isEmpty() || animeTracks.any { it in includedTracks }

            !isExcluded && isIncluded
        }

        val filterFn: (AnimeLibraryItem) -> Boolean = {
            filterFnDownloaded(it) &&
                filterFnUnseen(it) &&
                filterFnStarted(it) &&
                filterFnBookmarked(it) &&
                filterFnCompleted(it) &&
                filterFnIntervalCustom(it) &&
                filterFnLanguage(it) &&
                filterFnTracking(it)
        }

        return mapValues { (_, value) -> value.fastFilter(filterFn).toPersistentList() }
            .toPersistentMap()
    }

    private fun AnimeLibraryMap.applySort(
        trackMap: Map<Long, List<AnimeTrack>>,
        loggedInTrackerIds: Set<Long>,
    ): AnimeLibraryMap {
        val alphabeticSortKeys = HashMap<Long, String>()
        fun AnimeLibraryItem.alphabeticSortKey(): String {
            return alphabeticSortKeys.getOrPut(id) {
                title.lowercase()
            }
        }
        val sortAlphabetically: (AnimeLibraryItem, AnimeLibraryItem) -> Int = { i1, i2 ->
            i1.alphabeticSortKey().compareToWithCollator(i2.alphabeticSortKey())
        }
        val isPinned: (AnimeLibraryItem) -> Boolean = {
            when (it) {
                is AnimeLibraryItem.Single -> it.libraryAnime.pinned
                is AnimeLibraryItem.Series -> it.librarySeries.pinned
            }
        }
        val isSeries: (AnimeLibraryItem) -> Boolean = { it is AnimeLibraryItem.Series }

        val defaultTrackerScoreSortValue = -1.0
        val trackerScores by lazy {
            val trackerMap = trackerManager.getAll(loggedInTrackerIds).associateBy { e -> e.id }
            trackMap.mapValues { entry ->
                when {
                    entry.value.isEmpty() -> null
                    else ->
                        entry.value
                            .mapNotNull { trackerMap[it.trackerId]?.animeService?.get10PointScore(it) }
                            .average()
                }
            }
        }

        fun AnimeLibraryItem.lastUpdateSortValue(): Long {
            return when (this) {
                is AnimeLibraryItem.Single -> libraryAnime.anime.lastUpdate
                is AnimeLibraryItem.Series -> librarySeries.entries.maxOfOrNull { it.anime.lastUpdate } ?: 0L
            }
        }

        fun AnimeLibraryItem.latestUploadSortValue(): Long {
            return when (this) {
                is AnimeLibraryItem.Single -> libraryAnime.latestUpload
                is AnimeLibraryItem.Series -> librarySeries.latestUpload
            }
        }

        fun AnimeLibraryItem.episodeFetchDateSortValue(): Long {
            return when (this) {
                is AnimeLibraryItem.Single -> libraryAnime.episodeFetchedAt
                is AnimeLibraryItem.Series -> librarySeries.entries.maxOfOrNull { it.episodeFetchedAt } ?: 0L
            }
        }

        fun AnimeLibraryItem.airingTimeSortValue(): Long {
            return when (this) {
                is AnimeLibraryItem.Single -> libraryAnime.anime.nextEpisodeAiringAt
                is AnimeLibraryItem.Series -> librarySeries.entries.minOfOrNull {
                    if (it.anime.nextEpisodeAiringAt > 0L) it.anime.nextEpisodeAiringAt else Long.MAX_VALUE
                }?.takeIf { it != Long.MAX_VALUE } ?: 0L
            }
        }

        fun AnimeLibraryItem.trackerMeanSortValue(): Double {
            return when (this) {
                is AnimeLibraryItem.Single -> trackerScores[libraryAnime.id] ?: defaultTrackerScoreSortValue
                is AnimeLibraryItem.Series -> librarySeries.entries.mapNotNull { trackerScores[it.id] }
                    .takeIf { it.isNotEmpty() }?.average() ?: defaultTrackerScoreSortValue
            }
        }

        fun AnimeLibrarySort.comparator(): Comparator<AnimeLibraryItem> = Comparator { i1, i2 ->
            when (this.type) {
                AnimeLibrarySort.Type.Alphabetical -> {
                    sortAlphabetically(i1, i2)
                }
                AnimeLibrarySort.Type.LastSeen -> {
                    i1.lastSeen.compareTo(i2.lastSeen)
                }
                AnimeLibrarySort.Type.LastUpdate -> {
                    i1.lastUpdateSortValue().compareTo(i2.lastUpdateSortValue())
                }
                AnimeLibrarySort.Type.UnseenCount -> when {
                    // Ensure unseen content comes first
                    i1.unseenCount == i2.unseenCount -> 0
                    i1.unseenCount == 0L -> if (this.isAscending) 1 else -1
                    i2.unseenCount == 0L -> if (this.isAscending) -1 else 1
                    else -> i1.unseenCount.compareTo(i2.unseenCount)
                }
                AnimeLibrarySort.Type.TotalEpisodes -> {
                    i1.totalEpisodes.compareTo(i2.totalEpisodes)
                }
                AnimeLibrarySort.Type.LatestEpisode -> {
                    i1.latestUploadSortValue().compareTo(i2.latestUploadSortValue())
                }
                AnimeLibrarySort.Type.EpisodeFetchDate -> {
                    i1.episodeFetchDateSortValue().compareTo(i2.episodeFetchDateSortValue())
                }
                AnimeLibrarySort.Type.DateAdded -> {
                    i1.dateAdded.compareTo(i2.dateAdded)
                }
                AnimeLibrarySort.Type.TrackerMean -> {
                    i1.trackerMeanSortValue().compareTo(i2.trackerMeanSortValue())
                }
                AnimeLibrarySort.Type.AiringTime -> {
                    val a1 = i1.airingTimeSortValue()
                    val a2 = i2.airingTimeSortValue()
                    when {
                        i1.unseenCount != i2.unseenCount -> i1.unseenCount.compareTo(i2.unseenCount)
                        a1 == a2 -> 0
                        a1 == 0L -> if (this.isAscending) 1 else -1
                        a2 == 0L -> if (this.isAscending) -1 else 1
                        else -> a1.compareTo(a2)
                    }
                }
                AnimeLibrarySort.Type.Random -> {
                    error("Why Are We Still Here? Just To Suffer?")
                }
            }
        }

        return mapValues { (key, value) ->
            if (key.sort.type == AnimeLibrarySort.Type.Random) {
                return@mapValues value.sortPinnedSeriesFirst(
                    isPinned = isPinned,
                    isSeries = isSeries,
                    comparator = sortAlphabetically,
                    randomSeed = libraryPreferences.randomAnimeSortSeed().get(),
                ).toPersistentList()
            }

            val comparator = key.sort.comparator()
                .let { if (key.sort.isAscending) it else it.reversed() }
                .thenComparator(sortAlphabetically)

            value.sortPinnedSeriesFirst(
                isPinned = isPinned,
                isSeries = isSeries,
                comparator = comparator,
            ).toPersistentList()
        }.toPersistentMap()
    }

    private fun AnimeLibraryMap.applyGrouping(
        groupType: Int,
        tracks: Map<Long, List<AnimeTrack>>,
    ): AnimeLibraryMap {
        if (groupType == LibraryGroup.BY_DEFAULT) return this

        val items = this.values.flatten().distinctBy { it.id }
        val sortFlags = libraryPreferences.animeSortingMode().get().flag

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
                val statusCategories = LinkedHashMap<Long, Pair<Category, MutableList<AnimeLibraryItem>>>()
                items.forEach { item ->
                    val status = when (item) {
                        is AnimeLibraryItem.Single -> item.libraryAnime.anime.status
                        is AnimeLibraryItem.Series -> item.latestAnime.anime.status
                    }
                    val statusInt = status.toInt()
                    val (statusName, statusId) = when (statusInt) {
                        SAnime.ONGOING -> "Ongoing" to -21L
                        SAnime.COMPLETED -> "Completed" to -22L
                        SAnime.LICENSED -> "Licensed" to -23L
                        SAnime.PUBLISHING_FINISHED -> "Publishing Finished" to -24L
                        SAnime.CANCELLED -> "Cancelled" to -25L
                        SAnime.ON_HIATUS -> "On Hiatus" to -26L
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
                val sourceCategories = LinkedHashMap<Long, Pair<Category, MutableList<AnimeLibraryItem>>>()
                items.forEach { item ->
                    val sourceId = when (item) {
                        is AnimeLibraryItem.Single -> item.libraryAnime.anime.source
                        is AnimeLibraryItem.Series -> item.latestAnime.anime.source
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
                val trackMapper = MapAnimeTrackStatusToLibrary(trackerManager)
                val trackCategories = LinkedHashMap<Long, Pair<Category, MutableList<AnimeLibraryItem>>>()
                items.forEach { item ->
                    val representativeAnimeId = when (item) {
                        is AnimeLibraryItem.Single -> item.libraryAnime.anime.id
                        is AnimeLibraryItem.Series -> item.latestAnime.anime.id
                    }
                    val itemTracks = tracks[representativeAnimeId].orEmpty()
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
                                LibraryTrackStatus.READING -> "Watching"
                                LibraryTrackStatus.REPEATING -> "Rewatching"
                                LibraryTrackStatus.COMPLETED -> "Completed"
                                LibraryTrackStatus.ON_HOLD -> "On Hold"
                                LibraryTrackStatus.DROPPED -> "Dropped"
                                LibraryTrackStatus.PLAN_TO_READ -> "Plan to watch"
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

    private fun AnimeLibraryMap.withFilteredEmptyPlaceholder(
        sourceCategories: List<Category>,
        hasActiveFilters: Boolean,
    ): AnimeLibraryMap {
        if (isNotEmpty() || !hasActiveFilters) return this
        val fallbackCategory = sourceCategories.firstOrNull() ?: return this
        return persistentMapOf(fallbackCategory to persistentListOf())
    }

    private fun ItemPreferences.hasActiveFilters(trackingFilter: Map<Long, TriState>): Boolean {
        return listOf(
            filterDownloaded,
            filterUnseen,
            filterStarted,
            filterBookmarked,
            filterCompleted,
            filterIntervalCustom,
        ).any { it != TriState.DISABLED } ||
            filterLanguages.isNotEmpty() ||
            trackingFilter.values.any { it != TriState.DISABLED }
    }

    private fun getAnimelibItemPreferencesFlow(): Flow<ItemPreferences> {
        return combine(
            libraryPreferences.downloadBadge().changes(),
            libraryPreferences.unreadBadge().changes(),
            libraryPreferences.localBadge().changes(),
            libraryPreferences.languageBadge().changes(),
            libraryPreferences.autoUpdateItemRestrictions().changes(),

            preferences.downloadedOnly().changes(),
            libraryPreferences.filterDownloadedAnime().changes(),
            libraryPreferences.filterUnseen().changes(),
            libraryPreferences.filterStartedAnime().changes(),
            libraryPreferences.filterBookmarkedAnime().changes(),
            libraryPreferences.filterCompletedAnime().changes(),
            libraryPreferences.filterIntervalCustom().changes(),
            libraryPreferences.filterAnimeLanguages().changes(),
            transform = {
                @Suppress("UNCHECKED_CAST")
                val filterLanguages = it[12] as Set<String>
                ItemPreferences(
                    downloadBadge = it[0] as Boolean,
                    unseenBadge = it[1] as Boolean,
                    localBadge = it[2] as Boolean,
                    languageBadge = it[3] as Boolean,
                    skipOutsideReleasePeriod = LibraryPreferences.ENTRY_OUTSIDE_RELEASE_PERIOD in (it[4] as Set<*>),
                    globalFilterDownloaded = it[5] as Boolean,
                    filterDownloaded = it[6] as TriState,
                    filterUnseen = it[7] as TriState,
                    filterStarted = it[8] as TriState,
                    filterBookmarked = it[9] as TriState,
                    filterCompleted = it[10] as TriState,
                    filterIntervalCustom = it[11] as TriState,
                    filterLanguages = filterLanguages,
                )
            },
        )
    }

    /**
     * Get the categories and all its anime from the database.
     */
    private fun getLibraryFlow(): Flow<AnimeLibraryMap> {
        val animelibAnimesFlow = combine(
            getLibraryAnime.subscribe(),
            getLibraryAnimeSeries.subscribe(),
            sharedItemPreferencesFlow,
            getDownloadBadgeInvalidationFlow(),
        ) { animelibAnimeList, librarySeriesList, prefs, _ ->
            val idsInSeries = librarySeriesList.flatMapTo(HashSet()) { librarySeries ->
                librarySeries.entries.map { it.anime.id }
            }
            val singleItems = animelibAnimeList
                .filterNot { it.anime.id in idsInSeries }
                .map { animelibAnime ->
                    AnimeLibraryItem.Single(
                        libraryAnime = animelibAnime,
                        downloadCount = if (prefs.downloadBadge) {
                            downloadManager.getDownloadCount(animelibAnime.anime).toLong()
                        } else {
                            0
                        },
                        unseenCount = if (prefs.unseenBadge) animelibAnime.unseenCount else 0,
                        isLocal = if (prefs.localBadge) animelibAnime.anime.isLocal() else false,
                        sourceLanguage = if (prefs.languageBadge) {
                            sourceManager.getOrStub(animelibAnime.anime.source).lang
                        } else {
                            ""
                        },
                    )
                }
            val seriesItems = librarySeriesList
                .filter { it.entries.isNotEmpty() }
                .map { librarySeries ->
                    AnimeLibraryItem.Series(
                        librarySeries = librarySeries,
                        downloadCount = if (prefs.downloadBadge) {
                            librarySeries.entries.sumOf { downloadManager.getDownloadCount(it.anime).toLong() }
                        } else {
                            0
                        },
                        isLocal = if (prefs.localBadge) {
                            librarySeries.entries.fastAny { it.anime.isLocal() }
                        } else {
                            false
                        },
                        sourceLanguage = if (prefs.languageBadge) {
                            librarySeries.entries.firstOrNull()?.anime?.source?.let { source ->
                                sourceManager.getOrStub(source).lang
                            } ?: ""
                        } else {
                            ""
                        },
                    )
                }
            (singleItems + seriesItems).groupBy { it.category }
        }

        return combine(getCategories.subscribe(), animelibAnimesFlow) { categories, animelibAnime ->
            val displayCategories = if (animelibAnime.isNotEmpty() && !animelibAnime.containsKey(0)) {
                categories.fastFilterNot { it.isSystemCategory }
            } else {
                categories
            }

            val visibleIds = displayCategories.map { it.id }.toHashSet()
            val regrouped = animelibAnime.entries
                .flatMap { (categoryId, items) -> items.map { categoryId to it } }
                .groupBy({ (categoryId, item) ->
                    if (item is AnimeLibraryItem.Series && categoryId !in visibleIds) {
                        item.librarySeries.entries
                            .map { it.category }
                            .firstOrNull { it in visibleIds }
                            ?: categoryId
                    } else {
                        categoryId
                    }
                }) { it.second }

            displayCategories
                .associateWith { regrouped[it.id].orEmpty().toPersistentList() }
                .toPersistentMap()
        }
    }

    /**
     * Flow of tracking filter preferences
     *
     * @return map of track id with the filter value
     */
    private fun getTrackingFilterFlow(): Flow<Map<Long, TriState>> {
        return trackerManager.loggedInTrackersFlow().flatMapLatest { loggedInTrackers ->
            if (loggedInTrackers.isEmpty()) return@flatMapLatest flowOf(emptyMap())

            val prefFlows = loggedInTrackers.map { tracker ->
                libraryPreferences.filterTrackedAnime(tracker.id.toInt()).changes()
            }
            combine(prefFlows) {
                loggedInTrackers
                    .mapIndexed { index, tracker -> tracker.id to it[index] }
                    .toMap()
            }
        }
    }

    /**
     * Returns the common categories for the given list of anime.
     *
     * @param animes the list of anime.
     */
    private suspend fun getCommonCategories(animes: List<Anime>): Collection<Category> {
        if (animes.isEmpty()) return emptyList()
        return animes
            .map { getCategories.await(it.id).toSet() }
            .reduce { set1, set2 -> set1.intersect(set2) }
    }

    suspend fun getNextUnseenEpisode(anime: Anime): Episode? {
        return getEpisodesByAnimeId.await(anime.id).getNextUnseen(
            anime = anime,
            downloadManager = downloadManager,
            downloadedOnly = preferences.downloadedOnly().get(),
        )
    }

    suspend fun getNextUnseenEpisode(item: AnimeLibraryItem): Episode? {
        return when (item) {
            is AnimeLibraryItem.Single -> getNextUnseenEpisode(item.libraryAnime.anime)
            is AnimeLibraryItem.Series -> {
                for (anime in item.librarySeries.entries) {
                    val episode = getNextUnseenEpisode(anime.anime)
                    if (episode != null) return episode
                }
                null
            }
        }
    }

    /**
     * Returns the mix (non-common) categories for the given list of anime.
     *
     * @param animes the list of anime.
     */
    private suspend fun getMixCategories(animes: List<Anime>): Collection<Category> {
        if (animes.isEmpty()) return emptyList()
        val nimeCategories = animes.map { getCategories.await(it.id).toSet() }
        val common = nimeCategories.reduce { set1, set2 -> set1.intersect(set2) }
        return nimeCategories.flatten().distinct().subtract(common)
    }

    fun runDownloadActionSelection(action: DownloadAction) {
        val selection = state.value.selection
        val animes = selection.selectedAnimes()
        when (action) {
            DownloadAction.NEXT_1_ITEM -> downloadUnseenEpisodes(animes, 1)
            DownloadAction.NEXT_5_ITEMS -> downloadUnseenEpisodes(animes, 5)
            DownloadAction.NEXT_10_ITEMS -> downloadUnseenEpisodes(animes, 10)
            DownloadAction.NEXT_25_ITEMS -> downloadUnseenEpisodes(animes, 25)
            DownloadAction.UNVIEWED_ITEMS -> downloadUnseenEpisodes(animes, null)
        }
        clearSelection()
    }

    /**
     * Queues the amount specified of unseen episodes from the list of animes given.
     *
     * @param animes the list of anime.
     * @param amount the amount to queue or null to queue all
     */
    private fun downloadUnseenEpisodes(animes: List<Anime>, amount: Int?) {
        screenModelScope.launchNonCancellable {
            animes.forEach { anime ->
                val episodes = getNextEpisodes.await(anime.id)
                    .fastFilterNot { episode ->
                        downloadManager.getQueuedDownloadOrNull(episode.id) != null ||
                            downloadManager.isEpisodeDownloaded(
                                episode.name,
                                episode.scanlator,
                                anime.title,
                                anime.source,
                            )
                    }
                    .let { if (amount != null) it.take(amount) else it }

                downloadManager.downloadEpisodes(anime, episodes)
            }
        }
    }

    /**
     * Marks animes' episodes seen status.
     */
    fun markSeenSelection(seen: Boolean) {
        val animes = state.value.selection.selectedAnimes()
        screenModelScope.launchNonCancellable {
            animes.forEach { anime ->
                setSeenStatus.await(
                    anime = anime,
                    seen = seen,
                )
            }
        }
        clearSelection()
    }

    /**
     * Remove the selected anime.
     *
     * @param animeList the list of anime to delete.
     * @param deleteFromLibrary whether to delete anime from library.
     * @param deleteEpisodes whether to delete downloaded episodes.
     * @param seriesIds the list of series ids to delete.
     */
    fun removeAnimes(
        animeList: List<Anime>,
        deleteFromLibrary: Boolean,
        deleteEpisodes: Boolean,
        seriesIds: List<Long> = emptyList(),
    ) {
        screenModelScope.launchNonCancellable {
            seriesIds.forEach { seriesId ->
                deleteAnimeSeries.await(seriesId)
            }
            val animeToDelete = animeList.distinctBy { it.id }

            if (deleteFromLibrary) {
                val toDelete = animeToDelete.map {
                    it.removeCovers(coverCache)
                    it.removeBackgrounds(backgroundCache)
                    AnimeUpdate(
                        favorite = false,
                        id = it.id,
                    )
                }
                updateAnime.awaitAll(toDelete)
            }

            if (deleteEpisodes) {
                animeToDelete.forEach { anime ->
                    val source = sourceManager.get(anime.source) as? AnimeHttpSource
                    if (source != null) {
                        downloadManager.deleteAnime(anime, source)
                    }
                }
            }
        }
    }

    fun togglePinned(item: AnimeLibraryItem) {
        setPinned(item, !item.pinned)
    }

    fun setPinned(item: AnimeLibraryItem, pinned: Boolean) {
        screenModelScope.launchNonCancellable {
            setPinnedInternal(item, pinned)
        }
    }

    fun togglePinned(anime: LibraryAnime) {
        setPinned(anime, !anime.pinned)
    }

    fun setPinned(anime: LibraryAnime, pinned: Boolean) {
        screenModelScope.launchNonCancellable {
            val item = state.value.library.values
                .flatten()
                .firstOrNull { libraryItem ->
                    when (libraryItem) {
                        is AnimeLibraryItem.Single -> libraryItem.libraryAnime.id == anime.id
                        is AnimeLibraryItem.Series -> libraryItem.librarySeries.entries.any { it.id == anime.id }
                    }
                } ?: return@launchNonCancellable
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

    private suspend fun setPinnedInternal(item: AnimeLibraryItem, pinned: Boolean) {
        when (item) {
            is AnimeLibraryItem.Single -> updateAnime.await(
                AnimeUpdate(
                    id = item.libraryAnime.id,
                    pinned = pinned,
                ),
            )
            is AnimeLibraryItem.Series -> updateAnimeSeries.await(
                item.librarySeries.series.copy(
                    pinned = pinned,
                ),
            )
        }
    }

    /**
     * Bulk update categories of anime using old and new common categories.
     *
     * @param animeList the list of anime to move.
     * @param addCategories the categories to add for all animes.
     * @param removeCategories the categories to remove in all animes.
     */
    fun setAnimeCategories(
        animeList: List<Anime>,
        addCategories: List<Long>,
        removeCategories: List<Long>,
    ) {
        screenModelScope.launchNonCancellable {
            animeList.forEach { anime ->
                val categoryIds = getCategories.await(anime.id)
                    .map { it.id }
                    .subtract(removeCategories.toSet())
                    .plus(addCategories)
                    .toList()

                setAnimeCategories.await(anime.id, categoryIds)
            }
        }
    }

    // G1: cache the preference-backed states per model - every asState() call registers a
    // permanent collector in screenModelScope, and callers (pager pages, dialogs) used to
    // create fresh instances per recomposition (unbounded leak).
    private val separateDisplayModeState by lazy {
        libraryPreferences.animeDisplayMode().asState(screenModelScope)
    }
    private val sharedDisplayModeState by lazy {
        libraryPreferences.displayMode().asState(screenModelScope)
    }
    private val portraitColumnsState by lazy {
        libraryPreferences.animePortraitColumns().asState(screenModelScope)
    }
    private val landscapeColumnsState by lazy {
        libraryPreferences.animeLandscapeColumns().asState(screenModelScope)
    }

    fun getDisplayMode(useSeparateDisplayModePerMedia: Boolean): PreferenceMutableState<LibraryDisplayMode> {
        return if (useSeparateDisplayModePerMedia) separateDisplayModeState else sharedDisplayModeState
    }

    fun getColumnsPreferenceForCurrentOrientation(isLandscape: Boolean): PreferenceMutableState<Int> {
        return if (isLandscape) landscapeColumnsState else portraitColumnsState
    }

    suspend fun getRandomAnimelibItemForCurrentCategory(): AnimeLibraryItem? {
        return withIOContext {
            // D-M8 (anime port): activeCategoryIndex is a persistent pref that can go stale when
            // categories shrink - indexed access crashed with IOOB. The single snapshot also
            // removes the TOCTOU of re-reading state.value across the suspension.
            val snapshot = state.value
            snapshot.categories
                .getOrNull(activeCategoryIndex)
                ?.let { snapshot.getAnimelibItemsByCategoryId(it.id) }
                ?.randomOrNull()
        }
    }

    fun showSettingsDialog() {
        mutableState.update { it.copy(dialog = Dialog.SettingsSheet) }
    }

    fun clearSelection() {
        mutableState.update { it.copy(selection = persistentListOf()) }
    }

    fun toggleSelection(item: AnimeLibraryItem) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                if (list.fastAny { it.id == item.id }) {
                    list.removeAll { it.id == item.id }
                } else {
                    list.add(item)
                }
            }
            state.copy(selection = newSelection)
        }
    }

    /**
     * Selects all anime between and including the given anime and the last pressed anime from the
     * same visible library group.
     */
    fun toggleRangeSelection(item: AnimeLibraryItem) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                // F5: only the group that actually contains the target can contribute - copying
                // EVERY visible group per long-press-drag event was an O(library) allocation
                // on the main thread.
                val targetGroup = state.library.values
                    .firstOrNull { items -> items.fastAny { it.id == item.id } }
                val visibleGroups = targetGroup
                    ?.let { items -> listOf(items) }
                    .orEmpty()
                val newSelections = resolveLibraryRangeSelectionAdditions(
                    selectedItems = list,
                    targetItem = item,
                    visibleGroups = visibleGroups,
                    itemId = { it.id },
                )
                list.addAll(newSelections)
            }
            state.copy(selection = newSelection)
        }
    }

    fun selectAll(index: Int) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val categoryId = state.categories.getOrNull(index)?.id ?: -1
                val selectedIds = state.selectedIds
                state.getAnimelibItemsByCategoryId(categoryId)
                    ?.fastFilterNot { it.id in selectedIds }
                    ?.let { list.addAll(it) }
            }
            state.copy(selection = newSelection)
        }
    }

    fun invertSelection(index: Int) {
        mutableState.update { state ->
            // D-M8 (anime port): guard the stale index (selectAll already uses getOrNull; this
            // sibling did not) - invert from the toolbar crashed with IOOB after categories shrank.
            val categoryId = state.categories.getOrNull(index)?.id ?: return@update state
            val newSelection = state.selection.mutate { list ->
                val items = state.getAnimelibItemsByCategoryId(categoryId).orEmpty()
                val selectedIds = state.selectedIds
                val (toRemove, toAdd) = items.fastPartition { it.id in selectedIds }
                val toRemoveIds = toRemove.mapTo(HashSet(toRemove.size)) { it.id }
                list.removeAll { it.id in toRemoveIds }
                list.addAll(toAdd)
            }
            state.copy(selection = newSelection)
        }
    }

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun toggleLanguage(language: String) {
        val current = libraryPreferences.filterAnimeLanguages().get()
        libraryPreferences.filterAnimeLanguages().set(
            if (language in current) current - language else current + language,
        )
    }

    fun setAllLanguages(languages: Set<String>) {
        libraryPreferences.filterAnimeLanguages().set(languages)
    }

    fun clearLanguageFilter() {
        libraryPreferences.filterAnimeLanguages().set(emptySet())
    }

    fun openChangeCategoryDialog() {
        screenModelScope.launchIO {
            // Create a copy of selected anime
            val animeList = state.value.selection.selectedAnimes()

            // Hide the default category because it has a different behavior than the ones from db.
            val categories = state.value.categories.filter { it.id != 0L }

            // Get indexes of the common categories to preselect.
            val common = getCommonCategories(animeList)
            // Get indexes of the mix categories to preselect.
            val mix = getMixCategories(animeList)
            val preselected = categories
                .map {
                    when (it) {
                        in common -> CheckboxState.State.Checked(it)
                        in mix -> CheckboxState.TriState.Exclude(it)
                        else -> CheckboxState.State.None(it)
                    }
                }
                .toImmutableList()
            mutableState.update { it.copy(dialog = Dialog.ChangeCategory(animeList, preselected)) }
        }
    }

    fun openDeleteAnimeDialog() {
        val animeList = state.value.selection.selectedAnimes()
        val seriesIds = state.value.selection
            .filterIsInstance<AnimeLibraryItem.Series>()
            .map { it.librarySeries.series.id }
        mutableState.update { it.copy(dialog = Dialog.DeleteAnime(animeList, seriesIds)) }
    }

    fun openCreateSeries() {
        mutableState.update { it.copy(dialog = Dialog.CreateSeries) }
    }

    fun openAddToSeries() {
        screenModelScope.launchIO {
            val allSeries = getLibraryAnimeSeries.subscribe().first()
            val series = allSeries.filter { it.entries.isNotEmpty() }.map { it.series }
            mutableState.update { it.copy(dialog = Dialog.AddToSeries(series)) }
        }
    }

    fun createSeries(name: String) {
        val selection = state.value.selection
        if (selection.isEmpty()) return

        // D3: capture the ids and clear the selection synchronously
        val animeIds = selection.selectedAnimeEntries().map { it.anime.id }
        clearSelection()
        if (animeIds.isEmpty()) return
        screenModelScope.launchNonCancellable {
            createAnimeSeries.await(name, 0L, animeIds)
        }
    }

    fun addSelectionToSeries(series: AnimeSeries) {
        val selection = state.value.selection
        if (selection.isEmpty()) return

        val animeIds = selection.selectedAnimeEntries().map { it.anime.id }
        clearSelection()
        if (animeIds.isEmpty()) return
        screenModelScope.launchNonCancellable {
            addAnimesToSeries.await(series.id, animeIds)
        }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed interface Dialog {
        data object SettingsSheet : Dialog
        data class ChangeCategory(
            val anime: List<Anime>,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog
        data class DeleteAnime(val anime: List<Anime>, val seriesIds: List<Long> = emptyList()) : Dialog
        data object CreateSeries : Dialog
        data class AddToSeries(val series: List<AnimeSeries>) : Dialog
    }

    private data class AnimeBaseLibraryResult(
        val groupType: Int,
        val hasActiveFilters: Boolean,
        val libraryLanguages: List<String>,
        val library: AnimeLibraryMap,
    )

    @Immutable
    private data class ItemPreferences(
        val downloadBadge: Boolean,
        val unseenBadge: Boolean,
        val localBadge: Boolean,
        val languageBadge: Boolean,
        val skipOutsideReleasePeriod: Boolean,

        val globalFilterDownloaded: Boolean,
        val filterDownloaded: TriState,
        val filterUnseen: TriState,
        val filterStarted: TriState,
        val filterBookmarked: TriState,
        val filterCompleted: TriState,
        val filterIntervalCustom: TriState,
        val filterLanguages: Set<String> = emptySet(),
    )

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val library: AnimeLibraryMap = persistentMapOf(),
        val searchQuery: String? = null,
        val selection: PersistentList<AnimeLibraryItem> = persistentListOf(),
        val hasActiveFilters: Boolean = false,
        val languageFilter: Set<String> = emptySet(),
        val libraryLanguages: List<String> = emptyList(),
        val libraryCount: Int = 0,
        val showCategoryTabs: Boolean = false,
        val showAnimeCount: Boolean = false,
        val showAnimeContinueButton: Boolean = false,
        val groupType: Int = LibraryGroup.BY_DEFAULT,
        val dialog: Dialog? = null,
    ) {
        val categories = library.keys.toList()
        private val pages = library.values.toList()

        val items: List<AnimeLibraryItem> by lazy { pages.flatten() }

        val rawItems: List<AnimeLibraryItem>
            get() = items

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

        fun getAnimelibItemsByCategoryId(categoryId: Long): List<AnimeLibraryItem>? {
            val index = categories.indexOfFirst { it.id == categoryId }
            return pages.getOrNull(index)
        }

        fun getAnimelibItemsByPage(page: Int): List<AnimeLibraryItem> {
            return pages.getOrNull(page).orEmpty()
        }

        fun getAnimeCountForCategory(category: Category): Int? {
            return if (showAnimeCount || !searchQuery.isNullOrEmpty()) library[category]?.size else null
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
                !showAnimeCount -> null
                !showCategoryTabs -> getAnimeCountForCategory(category)
                // Whole library count
                else -> libraryCount
            }

            return LibraryToolbarTitle(title, count)
        }
    }
}

private fun List<AnimeLibraryItem>.selectedAnimeEntries(): List<LibraryAnime> {
    return asSequence()
        .flatMap { item ->
            when (item) {
                is AnimeLibraryItem.Single -> sequenceOf(item.libraryAnime)
                is AnimeLibraryItem.Series -> item.librarySeries.entries.asSequence()
            }
        }
        .distinctBy { it.id }
        .toList()
}

private fun List<AnimeLibraryItem>.selectedAnimes(): List<Anime> {
    return selectedAnimeEntries().map { it.anime }
}
