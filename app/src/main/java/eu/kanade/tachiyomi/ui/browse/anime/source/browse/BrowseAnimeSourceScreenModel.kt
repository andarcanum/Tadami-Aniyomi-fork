package eu.kanade.tachiyomi.ui.browse.anime.source.browse

import android.content.res.Configuration
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.asState
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.source.anime.interactor.GetAnimeIncognitoState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.ui.browse.search.SavedSearchFilterSerializer
import eu.kanade.tachiyomi.util.removeBackgrounds
import eu.kanade.tachiyomi.util.removeCovers
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.data.achievement.handler.AchievementHandler
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.anime.interactor.GetDuplicateLibraryAnime
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.toAnimeUpdate
import tachiyomi.domain.items.episode.interactor.SetAnimeDefaultEpisodeFlags
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.interactor.GetRemoteAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.interactor.DeleteSavedSearchById
import tachiyomi.domain.source.interactor.GetSavedSearchById
import tachiyomi.domain.source.interactor.GetSavedSearchBySourceId
import tachiyomi.domain.source.interactor.InsertSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.model.SourceType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import eu.kanade.tachiyomi.animesource.model.AnimeFilter as AnimeSourceModelFilter

class BrowseAnimeSourceScreenModel(
    private val sourceId: Long,
    listingQuery: String?,
    private val savedSearchId: Long? = null,
    sourceManager: AnimeSourceManager = Injekt.get(),
    sourcePreferences: SourcePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val coverCache: AnimeCoverCache = Injekt.get(),
    private val backgroundCache: AnimeBackgroundCache = Injekt.get(),
    private val getRemoteAnime: GetRemoteAnime = Injekt.get(),
    private val getDuplicateAnimelibAnime: GetDuplicateLibraryAnime = Injekt.get(),
    private val getCategories: GetAnimeCategories = Injekt.get(),
    private val setAnimeCategories: SetAnimeCategories = Injekt.get(),
    private val setAnimeDefaultEpisodeFlags: SetAnimeDefaultEpisodeFlags = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val addTracks: AddAnimeTracks = Injekt.get(),
    private val getIncognitoState: GetAnimeIncognitoState = Injekt.get(),
    private val achievementHandler: AchievementHandler = Injekt.get(),
    private val getSavedSearchById: GetSavedSearchById = Injekt.get(),
    private val getSavedSearchBySourceId: GetSavedSearchBySourceId = Injekt.get(),
    private val insertSavedSearch: InsertSavedSearch = Injekt.get(),
    private val deleteSavedSearchById: DeleteSavedSearchById = Injekt.get(),
    private val getAnimeFavorites: GetAnimeFavorites = Injekt.get(),
) : StateScreenModel<BrowseAnimeSourceScreenModel.State>(State(Listing.valueOf(listingQuery))) {

    var displayMode by sourcePreferences.sourceDisplayMode().asState(screenModelScope)

    val source = sourceManager.getOrStub(sourceId)
    private var defaultFiltersSerialized: String? = null

    init {
        if (source is AnimeCatalogueSource) {
            mutableState.update {
                it.copy(
                    toolbarQuery = (it.listing as? Listing.Search)?.query,
                )
            }
            loadFilters()
        }

        if (!getIncognitoState.await(source.id)) {
            sourcePreferences.lastUsedAnimeSource().set(source.id)
        }

        loadSavedSearches()

        if (savedSearchId != null && source is AnimeCatalogueSource) {
            screenModelScope.launch {
                val savedSearch = getSavedSearchById.await(savedSearchId) ?: return@launch
                val baseFilters = loadSourceFilters()
                val filtersJson = savedSearch.filtersJson
                if (filtersJson != null) {
                    SavedSearchFilterSerializer.deserialize(filtersJson, baseFilters)
                }
                mutableState.update {
                    it.copy(
                        listing = Listing.Search(savedSearch.sanitizedQuery(), baseFilters),
                        filters = baseFilters,
                        toolbarQuery = savedSearch.sanitizedQuery(),
                    )
                }
            }
        }
    }

    private fun loadFilters() {
        if (source !is AnimeCatalogueSource) return
        screenModelScope.launch {
            val loadedFilters = loadSourceFilters()
            mutableState.update { state ->
                val currentListing = state.listing
                val updatedListing = when {
                    currentListing is Listing.Search && currentListing.filters.isEmpty() -> {
                        val q = currentListing.query
                        if (!q.isNullOrBlank()) {
                            var genreFound = false
                            filter@ for (sourceFilter in loadedFilters) {
                                if (sourceFilter is AnimeSourceModelFilter.Group<*>) {
                                    for (filter in sourceFilter.state) {
                                        if (filter is AnimeSourceModelFilter<*> && filter.name.equals(q, true)) {
                                            when (filter) {
                                                is AnimeSourceModelFilter.TriState -> filter.state = 1
                                                is AnimeSourceModelFilter.CheckBox -> filter.state = true
                                                else -> {}
                                            }
                                            genreFound = true
                                            break@filter
                                        }
                                    }
                                } else if (sourceFilter is AnimeSourceModelFilter.Select<*>) {
                                    val idx = sourceFilter.values.filterIsInstance<String>()
                                        .indexOfFirst { it.equals(q, true) }
                                    if (idx != -1) {
                                        sourceFilter.state = idx
                                        genreFound = true
                                        break
                                    }
                                }
                            }
                            if (genreFound) {
                                currentListing.copy(query = null, filters = loadedFilters)
                            } else {
                                currentListing.copy(filters = loadedFilters)
                            }
                        } else {
                            currentListing.copy(filters = loadedFilters)
                        }
                    }
                    else -> currentListing
                }
                state.copy(
                    listing = updatedListing,
                    filters = if (state.filters.isEmpty()) loadedFilters else state.filters,
                    toolbarQuery = (updatedListing as? Listing.Search)?.query,
                )
            }
        }
    }

    private suspend fun loadSourceFilters(): AnimeFilterList {
        val filters = runCatching {
            withContext(ioCoroutineScope.coroutineContext) {
                (source as AnimeCatalogueSource).getFilterList()
            }
        }.getOrElse { AnimeFilterList() }
        defaultFiltersSerialized = serializeFilters(filters)
        return filters
    }

    private fun serializeFilters(filters: AnimeFilterList): String? {
        return runCatching { SavedSearchFilterSerializer.serialize(filters) }.getOrNull()
    }

    fun loadSavedSearches() {
        screenModelScope.launch {
            val searches = getSavedSearchBySourceId.await(sourceId, SourceType.ANIME)
            val currentActiveId = mutableState.value.savedSearches.find { it.second }?.first?.id
            mutableState.update {
                it.copy(
                    savedSearches = searches.map { search ->
                        search to (search.id == currentActiveId)
                    }.toImmutableList(),
                )
            }
        }
    }

    fun openSaveSearchDialog() {
        mutableState.update { it.copy(dialog = Dialog.CreateSavedSearch) }
    }

    fun saveSearch(name: String) {
        screenModelScope.launch {
            val state = mutableState.value
            val filtersJson = SavedSearchFilterSerializer.serialize(state.filters)
            val savedSearch = SavedSearch(
                id = -1,
                source = sourceId,
                sourceType = SourceType.ANIME,
                name = name,
                // BRA-3: never persist the Popular/Latest SENTINEL query strings (see the
                // manga SM comment) - null keeps the filters-only semantics.
                query = state.listing.query?.takeUnless { q ->
                    q == GetRemoteAnime.QUERY_POPULAR || q == GetRemoteAnime.QUERY_LATEST
                },
                filtersJson = filtersJson,
            )
            insertSavedSearch.await(savedSearch)
            dismissDialog()
            loadSavedSearches()
        }
    }

    /** BRA-3: legacy rows may still hold sentinel strings - map them to "no query". */
    private fun SavedSearch.sanitizedQuery(): String? = query?.takeUnless { q ->
        q == GetRemoteAnime.QUERY_POPULAR || q == GetRemoteAnime.QUERY_LATEST
    }

    fun deleteSearch(savedSearch: SavedSearch) {
        screenModelScope.launch {
            deleteSavedSearchById.await(savedSearch.id)
            loadSavedSearches()
            dismissDialog()
        }
    }

    fun openSavedSearch(savedSearch: SavedSearch) {
        if (source !is AnimeCatalogueSource) return
        screenModelScope.launch {
            val baseFilters = loadSourceFilters()
            val filtersJson = savedSearch.filtersJson
            if (filtersJson != null) {
                SavedSearchFilterSerializer.deserialize(filtersJson, baseFilters)
            }
            mutableState.update {
                it.copy(
                    listing = Listing.Search(savedSearch.sanitizedQuery(), baseFilters),
                    filters = baseFilters,
                    toolbarQuery = savedSearch.sanitizedQuery(),
                    savedSearches = it.savedSearches.map { (s, _) -> s to (s.id == savedSearch.id) }.toImmutableList(),
                )
            }
        }
    }

    fun dismissDialog() {
        setDialog(null)
    }

    // BRM-8: was subscribing to the WHOLE library and filtering in memory (manga etalon) -
    // source-scoped interactor instead.
    val favoriteAnimeUrls = getAnimeFavorites.subscribe(sourceId)
        .map { favorites -> favorites.map { it.url }.toSet() }
        .stateIn(screenModelScope, SharingStarted.Lazily, emptySet())

    /**
     * Flow of Pager flow tied to [State.listing]
     */
    val animePagerFlowFlow = state.map { it.listing }
        .distinctUntilChanged()
        .map { listing ->
            Pager(PagingConfig(pageSize = 25)) {
                getRemoteAnime.subscribe(sourceId, listing.query ?: "", listing.filters)
            }.flow
                .cachedIn(ioCoroutineScope)
                .combine(favoriteAnimeUrls) { pagingData, favorites ->
                    pagingData.map { anime ->
                        val isFavorite = anime.url in favorites
                        if (anime.favorite != isFavorite) {
                            anime.copy(favorite = isFavorite)
                        } else {
                            anime
                        }
                    }
                }
                .map { pagingData ->
                    // BRA-4 (РЕШ-10 port): the pref was snapshotted once in the constructor -
                    // toggling the setting did not affect the open browse screen. Read it live
                    // per emission (manga etalon).
                    val hideLibraryItems = sourcePreferences.hideInAnimeLibraryItems().get()
                    pagingData.filter { !hideLibraryItems || !it.favorite }
                }
                .cachedIn(ioCoroutineScope)
        }
        .stateIn(ioCoroutineScope, SharingStarted.Lazily, emptyFlow())

    fun getColumnsPreference(orientation: Int): GridCells {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val columns = if (isLandscape) {
            libraryPreferences.animeLandscapeColumns()
        } else {
            libraryPreferences.animePortraitColumns()
        }.get()
        return if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns)
    }

    // returns the number from the size slider
    fun getColumnsPreferenceForCurrentOrientation(orientation: Int): Int {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        return if (isLandscape) {
            libraryPreferences.animeLandscapeColumns()
        } else {
            libraryPreferences.animePortraitColumns()
        }.get()
    }

    fun resetFilters() {
        if (source !is AnimeCatalogueSource) return

        screenModelScope.launch {
            mutableState.update { it.copy(filters = loadSourceFilters()) }
        }
    }

    fun setListing(listing: Listing) {
        mutableState.update { it.copy(listing = listing, toolbarQuery = null) }
    }

    // РЕШ-9 (anime mirror): the filter sheet mutates the same FilterList instance the state
    // holds, so the self-comparison was always equal and browse never tracked FILTER.
    private var appliedFiltersSnapshot: String? = null

    fun setFilters(filters: AnimeFilterList) {
        if (source !is AnimeCatalogueSource) return

        val newSnapshot = runCatching { SavedSearchFilterSerializer.serialize(filters) }.getOrNull()
        val changed = newSnapshot != appliedFiltersSnapshot
        appliedFiltersSnapshot = newSnapshot

        mutableState.update {
            it.copy(
                filters = filters,
            )
        }
        if (changed) {
            achievementHandler.trackFeatureUsed(AchievementEvent.Feature.FILTER)
        }
    }

    fun search(query: String? = null, filters: AnimeFilterList? = null) {
        if (source !is AnimeCatalogueSource) return

        val currentState = state.value
        val input = currentState.listing as? Listing.Search
            ?: Listing.Search(query = null, filters = currentState.filters)

        val q = query ?: input.query
        if (!q.isNullOrBlank()) {
            val f = filters ?: input.filters
            val hasActiveFilters = serializeFilters(f)?.let { it != defaultFiltersSerialized } ?: f.isNotEmpty()
            if (hasActiveFilters) {
                achievementHandler.trackFeatureUsed(AchievementEvent.Feature.ADVANCED_SEARCH)
            } else {
                achievementHandler.trackFeatureUsed(AchievementEvent.Feature.SEARCH)
            }
        }

        mutableState.update {
            it.copy(
                listing = input.copy(
                    query = query ?: input.query,
                    filters = filters ?: input.filters,
                ),
                toolbarQuery = query ?: input.query,
            )
        }
    }

    fun searchGenre(genreName: String) {
        if (source !is AnimeCatalogueSource) return

        screenModelScope.launch {
            val defaultFilters = loadSourceFilters()
            var genreExists = false

            filter@ for (sourceFilter in defaultFilters) {
                if (sourceFilter is AnimeSourceModelFilter.Group<*>) {
                    for (filter in sourceFilter.state) {
                        if (filter is AnimeSourceModelFilter<*> && filter.name.equals(genreName, true)) {
                            when (filter) {
                                is AnimeSourceModelFilter.TriState -> filter.state = 1
                                is AnimeSourceModelFilter.CheckBox -> filter.state = true
                                else -> {}
                            }
                            genreExists = true
                            break@filter
                        }
                    }
                } else if (sourceFilter is AnimeSourceModelFilter.Select<*>) {
                    val index = sourceFilter.values.filterIsInstance<String>()
                        .indexOfFirst { it.equals(genreName, true) }

                    if (index != -1) {
                        sourceFilter.state = index
                        genreExists = true
                        break
                    }
                }
            }
            mutableState.update {
                val listing = if (genreExists) {
                    Listing.Search(query = null, filters = defaultFilters)
                } else {
                    Listing.Search(query = genreName, filters = defaultFilters)
                }
                it.copy(
                    filters = defaultFilters,
                    listing = listing,
                    toolbarQuery = listing.query,
                )
            }
        }
    }

    fun searchGenres(genres: List<String>) {
        if (source !is AnimeCatalogueSource || genres.isEmpty()) return

        screenModelScope.launch {
            val defaultFilters = loadSourceFilters()
            var anyExists = false

            for (genreName in genres) {
                filter@ for (sourceFilter in defaultFilters) {
                    if (sourceFilter is AnimeSourceModelFilter.Group<*>) {
                        for (filter in sourceFilter.state) {
                            if (filter is AnimeSourceModelFilter<*> && filter.name.equals(genreName, true)) {
                                when (filter) {
                                    is AnimeSourceModelFilter.TriState -> filter.state = 1
                                    is AnimeSourceModelFilter.CheckBox -> filter.state = true
                                    else -> {}
                                }
                                anyExists = true
                                break@filter
                            }
                        }
                    } else if (sourceFilter is AnimeSourceModelFilter.Select<*>) {
                        val idx = sourceFilter.values.filterIsInstance<String>()
                            .indexOfFirst { it.equals(genreName, true) }
                        if (idx != -1) {
                            sourceFilter.state = idx
                            anyExists = true
                            break@filter
                        }
                    }
                }
            }

            mutableState.update {
                val listing = if (anyExists) {
                    Listing.Search(query = null, filters = defaultFilters)
                } else {
                    Listing.Search(query = genres.firstOrNull(), filters = defaultFilters)
                }
                it.copy(
                    filters = defaultFilters,
                    listing = listing,
                    toolbarQuery = listing.query,
                )
            }
        }
    }

    /**
     *
     * @param anime the anime to update.
     */
    fun changeAnimeFavorite(anime: Anime) {
        screenModelScope.launch {
            var new = anime.copy(
                favorite = !anime.favorite,
                dateAdded = when (anime.favorite) {
                    true -> 0
                    false -> Instant.now().toEpochMilli()
                },
            )

            if (!new.favorite) {
                new = new.removeCovers(coverCache)
                new = new.removeBackgrounds(backgroundCache)
            } else {
                setAnimeDefaultEpisodeFlags.await(anime)
                addTracks.bindEnhancedTrackers(anime, source)
            }

            updateAnime.await(new.toAnimeUpdate())
        }
    }

    fun addFavorite(anime: Anime) {
        screenModelScope.launch {
            val categories = getCategories()
            val defaultCategoryId = libraryPreferences.defaultAnimeCategory().get()
            val defaultCategory = categories.find { it.id == defaultCategoryId.toLong() }

            when {
                // Default category set
                defaultCategory != null -> {
                    moveAnimeToCategories(anime, defaultCategory)

                    changeAnimeFavorite(anime)
                }
                // Automatic 'Default' or no categories
                defaultCategoryId == 0 || categories.isEmpty() -> {
                    moveAnimeToCategories(anime)

                    changeAnimeFavorite(anime)
                }

                // Choose a category
                else -> {
                    val preselectedIds = getCategories.await(anime.id).map { it.id }
                    setDialog(
                        Dialog.ChangeAnimeCategory(
                            anime,
                            categories.mapAsCheckboxState { it.id in preselectedIds }.toImmutableList(),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    suspend fun getCategories(): List<Category> {
        return getCategories.subscribe()
            .firstOrNull()
            ?.filterNot { it.isSystemCategory }
            .orEmpty()
    }

    suspend fun getDuplicateAnimelibAnime(anime: Anime): Anime? {
        return getDuplicateAnimelibAnime.await(anime).getOrNull(0)
    }

    private fun moveAnimeToCategories(anime: Anime, vararg categories: Category) {
        moveAnimeToCategories(anime, categories.filter { it.id != 0L }.map { it.id })
    }

    fun moveAnimeToCategories(anime: Anime, categoryIds: List<Long>) {
        screenModelScope.launchIO {
            setAnimeCategories.await(
                animeId = anime.id,
                categoryIds = categoryIds.toList(),
            )
        }
    }

    fun openFilterSheet() {
        setDialog(Dialog.Filter)
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    fun setToolbarQuery(query: String?) {
        mutableState.update { it.copy(toolbarQuery = query) }
    }

    sealed class Listing(open val query: String?, open val filters: AnimeFilterList) {
        data object Popular : Listing(
            query = GetRemoteAnime.QUERY_POPULAR,
            filters = AnimeFilterList(),
        )
        data object Latest : Listing(
            query = GetRemoteAnime.QUERY_LATEST,
            filters = AnimeFilterList(),
        )
        data class Search(override val query: String?, override val filters: AnimeFilterList) : Listing(
            query = query,
            filters = filters,
        )

        companion object {
            fun valueOf(query: String?): Listing {
                return when (query) {
                    null -> Popular
                    GetRemoteAnime.QUERY_POPULAR -> Popular
                    GetRemoteAnime.QUERY_LATEST -> Latest
                    else -> Search(query = query, filters = AnimeFilterList()) // filters are filled in later
                }
            }
        }
    }

    sealed interface Dialog {
        data object Filter : Dialog
        data object CreateSavedSearch : Dialog
        data class DeleteSavedSearch(val savedSearch: SavedSearch) : Dialog
        data class RemoveAnime(val anime: Anime) : Dialog
        data class AddDuplicateAnime(val anime: Anime, val duplicate: Anime) : Dialog
        data class ChangeAnimeCategory(
            val anime: Anime,
            val initialSelection: ImmutableList<CheckboxState.State<Category>>,
        ) : Dialog
        data class Migrate(val newAnime: Anime, val oldAnime: Anime) : Dialog
    }

    @Immutable
    data class State(
        val listing: Listing,
        val filters: AnimeFilterList = AnimeFilterList(),
        val toolbarQuery: String? = null,
        val dialog: Dialog? = null,
        val savedSearches: ImmutableList<Pair<SavedSearch, Boolean>> = persistentListOf(),
    ) {
        val isUserQuery get() = listing is Listing.Search && !listing.query.isNullOrEmpty()
        // РЕШ-B5: `filterable` removed - zero production readers.
    }
}
