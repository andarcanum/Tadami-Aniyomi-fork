package eu.kanade.tachiyomi.ui.browse.manga.source.browse

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
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.entries.manga.model.toDomainManga
import eu.kanade.domain.source.manga.interactor.GetMangaIncognitoState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.manga.interactor.AddMangaTracks
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.util.removeCovers
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.data.achievement.handler.AchievementHandler
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.manga.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.toMangaUpdate
import tachiyomi.domain.items.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.interactor.DeleteSavedSearchById
import tachiyomi.domain.source.interactor.GetSavedSearchById
import tachiyomi.domain.source.interactor.GetSavedSearchBySourceId
import tachiyomi.domain.source.interactor.InsertSavedSearch
import tachiyomi.domain.source.manga.interactor.GetRemoteManga
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.model.SourceType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer
import java.time.Instant
import eu.kanade.tachiyomi.source.model.Filter as SourceModelFilter

class BrowseMangaSourceScreenModel(
    private val sourceId: Long,
    listingQuery: String?,
    private val savedSearchId: Long? = null,
    sourceManager: MangaSourceManager = Injekt.get(),
    sourcePreferences: SourcePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val coverCache: MangaCoverCache = Injekt.get(),
    private val getRemoteManga: GetRemoteManga = Injekt.get(),
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = Injekt.get(),
    private val getCategories: GetMangaCategories = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val addTracks: AddMangaTracks = Injekt.get(),
    private val getIncognitoState: GetMangaIncognitoState = Injekt.get(),
    private val achievementHandler: AchievementHandler = Injekt.get(),
    private val getSavedSearchById: GetSavedSearchById = Injekt.get(),
    private val getSavedSearchBySourceId: GetSavedSearchBySourceId = Injekt.get(),
    private val insertSavedSearch: InsertSavedSearch = Injekt.get(),
    private val deleteSavedSearchById: DeleteSavedSearchById = Injekt.get(),
    private val filterSerializer: FilterSerializer = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
) : StateScreenModel<BrowseMangaSourceScreenModel.State>(State(Listing.valueOf(listingQuery))) {

    var displayMode by sourcePreferences.sourceDisplayMode().asState(screenModelScope)

    val source = sourceManager.getOrStub(sourceId)
    private var defaultFiltersSerialized: String? = null

    init {
        if (source is CatalogueSource) {
            mutableState.update {
                it.copy(
                    toolbarQuery = (it.listing as? Listing.Search)?.query,
                )
            }
            loadFilters()
        }

        if (!getIncognitoState.await(source.id)) {
            sourcePreferences.lastUsedMangaSource().set(source.id)
        }

        loadSavedSearches()

        if (savedSearchId != null && source is CatalogueSource) {
            screenModelScope.launch {
                val savedSearch = getSavedSearchById.await(savedSearchId) ?: return@launch
                val baseFilters = loadSourceFilters()
                val filtersJson = savedSearch.filtersJson
                if (filtersJson != null) {
                    filterSerializer.deserialize(baseFilters, Json.parseToJsonElement(filtersJson).jsonArray)
                }
                mutableState.update {
                    it.copy(
                        listing = Listing.Search(savedSearch.query, baseFilters),
                        filters = baseFilters,
                        toolbarQuery = savedSearch.query,
                    )
                }
            }
        }
    }

    private fun loadFilters() {
        if (source !is CatalogueSource) return
        screenModelScope.launch {
            val loadedFilters = loadSourceFilters()
            mutableState.update { state ->
                val updatedListing = when (val listing = state.listing) {
                    is Listing.Search -> if (listing.filters.isEmpty()) {
                        listing.copy(
                            filters = loadedFilters,
                        )
                    } else {
                        listing
                    }
                    else -> listing
                }
                state.copy(
                    listing = updatedListing,
                    filters = if (state.filters.isEmpty()) loadedFilters else state.filters,
                )
            }
        }
    }

    private suspend fun loadSourceFilters(): FilterList {
        val filters = runCatching {
            withContext(ioCoroutineScope.coroutineContext) {
                (source as CatalogueSource).getFilterList()
            }
        }.getOrElse { FilterList() }
        defaultFiltersSerialized = serializeFilters(filters)
        return filters
    }

    private fun serializeFilters(filters: FilterList): String? {
        return runCatching {
            Json.encodeToString(filterSerializer.serialize(filters))
        }.getOrNull()
    }

    fun loadSavedSearches() {
        screenModelScope.launch {
            val searches = getSavedSearchBySourceId.await(sourceId, SourceType.MANGA)
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
            val filtersJson = kotlinx.serialization.json.Json.encodeToString(filterSerializer.serialize(state.filters))
            val savedSearch = SavedSearch(
                id = -1,
                source = sourceId,
                sourceType = SourceType.MANGA,
                name = name,
                query = state.listing.query,
                filtersJson = filtersJson,
            )
            insertSavedSearch.await(savedSearch)
            dismissDialog()
            loadSavedSearches()
        }
    }

    fun deleteSearch(savedSearch: SavedSearch) {
        screenModelScope.launch {
            deleteSavedSearchById.await(savedSearch.id)
            loadSavedSearches()
            dismissDialog()
        }
    }

    fun openSavedSearch(savedSearch: SavedSearch) {
        if (source !is CatalogueSource) return
        screenModelScope.launch {
            val filtersJsonStr = savedSearch.filtersJson
            val jsonArray = if (filtersJsonStr != null) {
                Json.parseToJsonElement(filtersJsonStr).jsonArray
            } else {
                buildJsonArray { }
            }
            val baseFilters = loadSourceFilters()
            filterSerializer.deserialize(baseFilters, jsonArray)
            mutableState.update {
                it.copy(
                    listing = Listing.Search(savedSearch.query, baseFilters),
                    filters = baseFilters,
                    toolbarQuery = savedSearch.query,
                    savedSearches = it.savedSearches.map { (s, _) -> s to (s.id == savedSearch.id) }.toImmutableList(),
                )
            }
        }
    }

    fun dismissDialog() {
        setDialog(null)
    }

    val favoriteMangaUrls = getLibraryManga.subscribe()
        .map { libraryMangaList ->
            libraryMangaList
                .filter { it.manga.source == sourceId }
                .map { it.manga.url }
                .toSet()
        }
        .stateIn(screenModelScope, SharingStarted.Lazily, emptySet())

    /**
     * Flow of Pager flow tied to [State.listing]
     */
    private val hideInLibraryItems = sourcePreferences.hideInMangaLibraryItems().get()
    val mangaPagerFlowFlow = state.map { it.listing }
        .distinctUntilChanged()
        .map { listing ->
            Pager(PagingConfig(pageSize = 25)) {
                getRemoteManga.subscribe(sourceId, listing.query ?: "", listing.filters)
            }.flow.map { pagingData ->
                pagingData.map {
                    networkToLocalManga.await(it.toDomainManga(sourceId))
                }
                    .filter { !hideInLibraryItems || !it.favorite }
            }
                .cachedIn(ioCoroutineScope)
        }
        .stateIn(ioCoroutineScope, SharingStarted.Lazily, emptyFlow())

    fun getColumnsPreference(orientation: Int): GridCells {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val columns = if (isLandscape) {
            libraryPreferences.mangaLandscapeColumns()
        } else {
            libraryPreferences.mangaPortraitColumns()
        }.get()
        return if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns)
    }

    // returns the number from the size slider
    fun getColumnsPreferenceForCurrentOrientation(orientation: Int): Int {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        return if (isLandscape) {
            libraryPreferences.mangaLandscapeColumns()
        } else {
            libraryPreferences.mangaPortraitColumns()
        }.get()
    }

    fun resetFilters() {
        if (source !is CatalogueSource) return

        screenModelScope.launch {
            mutableState.update { it.copy(filters = loadSourceFilters()) }
        }
    }

    fun setListing(listing: Listing) {
        mutableState.update { it.copy(listing = listing, toolbarQuery = null) }
    }

    fun setFilters(filters: FilterList) {
        if (source !is CatalogueSource) return

        val changed = try {
            kotlinx.serialization.json.Json.encodeToString(filterSerializer.serialize(filters)) !=
                kotlinx.serialization.json.Json.encodeToString(filterSerializer.serialize(state.value.filters))
        } catch (e: Exception) {
            true
        }

        mutableState.update {
            it.copy(
                filters = filters,
            )
        }
        if (changed) {
            achievementHandler.trackFeatureUsed(AchievementEvent.Feature.FILTER)
        }
    }

    fun search(query: String? = null, filters: FilterList? = null) {
        if (source !is CatalogueSource) return

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
        if (source !is CatalogueSource) return

        screenModelScope.launch {
            val defaultFilters = loadSourceFilters()
            var genreExists = false

            filter@ for (sourceFilter in defaultFilters) {
                if (sourceFilter is SourceModelFilter.Group<*>) {
                    for (filter in sourceFilter.state) {
                        if (filter is SourceModelFilter<*> && filter.name.equals(genreName, true)) {
                            when (filter) {
                                is SourceModelFilter.TriState -> filter.state = 1
                                is SourceModelFilter.CheckBox -> filter.state = true
                                else -> {}
                            }
                            genreExists = true
                            break@filter
                        }
                    }
                } else if (sourceFilter is SourceModelFilter.Select<*>) {
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

    /**
     * Adds or removes a manga from the library.
     *
     * @param manga the manga to update.
     */
    fun changeMangaFavorite(manga: Manga) {
        screenModelScope.launch {
            var new = manga.copy(
                favorite = !manga.favorite,
                dateAdded = when (manga.favorite) {
                    true -> 0
                    false -> Instant.now().toEpochMilli()
                },
            )

            if (!new.favorite) {
                new = new.removeCovers(coverCache)
            } else {
                setMangaDefaultChapterFlags.await(manga)
                addTracks.bindEnhancedTrackers(manga, source)
            }

            updateManga.await(new.toMangaUpdate())
        }
    }

    fun addFavorite(manga: Manga) {
        screenModelScope.launch {
            val categories = getCategories()
            val defaultCategoryId = libraryPreferences.defaultMangaCategory().get()
            val defaultCategory = categories.find { it.id == defaultCategoryId.toLong() }

            when {
                // Default category set
                defaultCategory != null -> {
                    moveMangaToCategories(manga, defaultCategory)

                    changeMangaFavorite(manga)
                }

                // Automatic 'Default' or no categories
                defaultCategoryId == 0 || categories.isEmpty() -> {
                    moveMangaToCategories(manga)

                    changeMangaFavorite(manga)
                }

                // Choose a category
                else -> {
                    val preselectedIds = getCategories.await(manga.id).map { it.id }
                    setDialog(
                        Dialog.ChangeMangaCategory(
                            manga,
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

    suspend fun getDuplicateLibraryManga(manga: Manga): Manga? {
        return getDuplicateLibraryManga.await(manga).getOrNull(0)
    }

    private fun moveMangaToCategories(manga: Manga, vararg categories: Category) {
        moveMangaToCategories(manga, categories.filter { it.id != 0L }.map { it.id })
    }

    fun moveMangaToCategories(manga: Manga, categoryIds: List<Long>) {
        screenModelScope.launchIO {
            setMangaCategories.await(
                mangaId = manga.id,
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

    sealed class Listing(open val query: String?, open val filters: FilterList) {
        data object Popular : Listing(query = GetRemoteManga.QUERY_POPULAR, filters = FilterList())
        data object Latest : Listing(query = GetRemoteManga.QUERY_LATEST, filters = FilterList())
        data class Search(override val query: String?, override val filters: FilterList) : Listing(
            query = query,
            filters = filters,
        )

        companion object {
            fun valueOf(query: String?): Listing {
                return when (query) {
                    null -> Popular
                    GetRemoteManga.QUERY_POPULAR -> Popular
                    GetRemoteManga.QUERY_LATEST -> Latest
                    else -> Search(query = query, filters = FilterList()) // filters are filled in later
                }
            }
        }
    }

    sealed interface Dialog {
        data object Filter : Dialog
        data object CreateSavedSearch : Dialog
        data class DeleteSavedSearch(val savedSearch: SavedSearch) : Dialog
        data class RemoveManga(val manga: Manga) : Dialog
        data class AddDuplicateManga(val manga: Manga, val duplicate: Manga) : Dialog
        data class ChangeMangaCategory(
            val manga: Manga,
            val initialSelection: ImmutableList<CheckboxState.State<Category>>,
        ) : Dialog
        data class Migrate(val newManga: Manga, val oldManga: Manga) : Dialog
    }

    @Immutable
    data class State(
        val listing: Listing,
        val filters: FilterList = FilterList(),
        val toolbarQuery: String? = null,
        val dialog: Dialog? = null,
        val savedSearches: ImmutableList<Pair<SavedSearch, Boolean>> = persistentListOf(),
    ) {
        val isUserQuery get() = listing is Listing.Search && !listing.query.isNullOrEmpty()
        val filterable get() = savedSearches.isNotEmpty()
    }
}
