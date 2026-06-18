package eu.kanade.tachiyomi.ui.browse.novel.source.browse

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.asState
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.entries.novel.model.toDomainNovel
import eu.kanade.domain.entries.novel.model.toSNovel
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.extension.novel.runtime.hasVisiblePluginSettingsByDiscovery
import eu.kanade.tachiyomi.novelsource.ConfigurableNovelSource
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.ui.browse.search.SavedSearchFilterSerializer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
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
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.category.novel.interactor.SetNovelCategories
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.interactor.GetNovelByUrlAndSourceId
import tachiyomi.domain.entries.novel.interactor.GetNovelFavorites
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.items.novelchapter.interactor.SetNovelDefaultChapterFlags
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.interactor.DeleteSavedSearchById
import tachiyomi.domain.source.interactor.GetSavedSearchById
import tachiyomi.domain.source.interactor.GetSavedSearchBySourceId
import tachiyomi.domain.source.interactor.InsertSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.model.SourceType
import tachiyomi.domain.source.novel.interactor.GetRemoteNovel
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class BrowseNovelSourceScreenModel(
    private val sourceId: Long,
    listingQuery: String?,
    private val savedSearchId: Long? = null,
    sourceManager: NovelSourceManager = Injekt.get(),
    getRemoteNovel: GetRemoteNovel = Injekt.get(),
    sourcePreferences: eu.kanade.domain.source.service.SourcePreferences = Injekt.get(),
    private val getNovelByUrlAndSourceId: GetNovelByUrlAndSourceId = Injekt.get(),
    private val getNovelInteractor: GetNovel? = null,
    private val networkToLocalNovel: NetworkToLocalNovel = Injekt.get(),
    private val updateNovel: UpdateNovel? = null,
    private val libraryPrefs: LibraryPreferences? = null,
    private val getNovelFavoritesInteractor: GetNovelFavorites? = null,
    private val getNovelCategoriesInteractor: GetNovelCategories? = null,
    private val setNovelCategoriesInteractor: SetNovelCategories? = null,
    private val setNovelDefaultChapterFlagsInteractor: SetNovelDefaultChapterFlags? = null,
    private val getSavedSearchByIdInteractor: GetSavedSearchById? = null,
    private val getSavedSearchBySourceIdInteractor: GetSavedSearchBySourceId? = null,
    private val insertSavedSearchInteractor: InsertSavedSearch? = null,
    private val deleteSavedSearchByIdInteractor: DeleteSavedSearchById? = null,
    private val achievementHandler: AchievementHandler = Injekt.get(),
) : StateScreenModel<BrowseNovelSourceScreenModel.State>(State(Listing.valueOf(listingQuery))) {

    var displayMode by sourcePreferences.sourceDisplayMode().asState(screenModelScope)
    private var defaultFiltersSerialized: String? = null
    private val novelDetailsInFlight = ConcurrentHashMap.newKeySet<Long>()
    private val novelDetailsDispatcher = kotlinx.coroutines.Dispatchers.IO.limitedParallelism(3)
    private val browseNovelCoverUpdates = MutableStateFlow<Map<Long, BrowseNovelCoverUpdate>>(emptyMap())

    val source = sourceManager.getOrStub(sourceId)

    init {
        if (source is NovelCatalogueSource) {
            mutableState.update {
                var query: String? = null
                var listing = it.listing

                if (listing is Listing.Search) {
                    query = listing.query
                }

                it.copy(
                    listing = listing,
                    toolbarQuery = query,
                )
            }

            screenModelScope.launch {
                val loadedFilters = loadSourceFilters()

                mutableState.update { state ->
                    val hadNoFilters = state.filters.isEmpty()
                    val updatedListing = when (val listing = state.listing) {
                        is Listing.Search -> {
                            if (listing.filters.isEmpty()) {
                                listing.copy(filters = loadedFilters)
                            } else {
                                listing
                            }
                        }
                        else -> listing
                    }
                    val updatedFilters = if (state.filters.isEmpty()) loadedFilters else state.filters
                    state.copy(
                        listing = updatedListing,
                        filters = updatedFilters,
                        filterVersion = if (hadNoFilters && updatedFilters.isNotEmpty()) {
                            state.filterVersion + 1
                        } else {
                            state.filterVersion
                        },
                    )
                }
            }
        }

        sourcePreferences.lastUsedNovelSource().set(source.id)

        loadSavedSearches()

        screenModelScope.launch {
            val isConfigurable = withContext(ioCoroutineScope.coroutineContext) {
                source is ConfigurableNovelSource || source.hasVisiblePluginSettingsByDiscovery()
            }
            mutableState.update { it.copy(isSourceConfigurable = isConfigurable) }
        }

        if (savedSearchId != null && source is NovelCatalogueSource) {
            screenModelScope.launch {
                val getSavedSearchById = resolveGetSavedSearchById() ?: return@launch
                val savedSearch = getSavedSearchById.await(savedSearchId) ?: return@launch
                val baseFilters = loadSourceFilters()
                val filtersJson = savedSearch.filtersJson
                if (filtersJson != null) {
                    SavedSearchFilterSerializer.deserialize(filtersJson, baseFilters)
                }
                mutableState.update {
                    it.copy(
                        listing = Listing.Search(savedSearch.query, baseFilters),
                        filters = baseFilters,
                        toolbarQuery = savedSearch.query,
                        filterVersion = it.filterVersion + 1,
                    )
                }
            }
        }
    }

    private suspend fun loadSourceFilters(): NovelFilterList {
        val filters = runCatching {
            withContext(ioCoroutineScope.coroutineContext) {
                (source as NovelCatalogueSource).getFilterList()
            }
        }.getOrElse { NovelFilterList() }
        defaultFiltersSerialized = serializeFilters(filters)
        return filters
    }

    private fun serializeFilters(filters: NovelFilterList): String? {
        return runCatching { SavedSearchFilterSerializer.serialize(filters) }.getOrNull()
    }

    fun loadSavedSearches() {
        screenModelScope.launch {
            val getSavedSearchBySourceId = resolveGetSavedSearchBySourceId() ?: return@launch
            val searches = getSavedSearchBySourceId.await(sourceId, SourceType.NOVEL)
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
            val insertSavedSearch = resolveInsertSavedSearch() ?: return@launch
            val state = mutableState.value
            val filtersJson = SavedSearchFilterSerializer.serialize(state.filters)
            val savedSearch = SavedSearch(
                id = -1,
                source = sourceId,
                sourceType = SourceType.NOVEL,
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
            val deleteSavedSearchById = resolveDeleteSavedSearchById() ?: return@launch
            deleteSavedSearchById.await(savedSearch.id)
            loadSavedSearches()
            dismissDialog()
        }
    }

    fun openSavedSearch(savedSearch: SavedSearch) {
        if (source !is NovelCatalogueSource) return
        screenModelScope.launch {
            val baseFilters = loadSourceFilters()
            val filtersJson = savedSearch.filtersJson
            if (filtersJson != null) {
                SavedSearchFilterSerializer.deserialize(filtersJson, baseFilters)
            }
            mutableState.update {
                it.copy(
                    listing = Listing.Search(savedSearch.query, baseFilters),
                    filters = baseFilters,
                    toolbarQuery = savedSearch.query,
                    filterVersion = it.filterVersion + 1,
                    savedSearches = it.savedSearches.map { (s, _) -> s to (s.id == savedSearch.id) }.toImmutableList(),
                )
            }
        }
    }

    fun dismissDialog() {
        setDialog(null)
    }

    private val hideInLibraryItems = sourcePreferences.hideInNovelLibraryItems().get()

    private val autoFavoriteLocalNovels = sourcePreferences.importEpubAddToLibrary().get()

    val favoriteNovelUrls = resolveGetNovelFavorites()?.subscribe(sourceId)
        ?.map { list -> list.map { it.url }.toSet() }
        ?.stateIn(screenModelScope, SharingStarted.Lazily, emptySet())
        ?: MutableStateFlow(emptySet())

    val novelPagerFlowFlow = state
        .map { state ->
            val listing = state.listing
            PagingRequest(
                query = listing.query.orEmpty(),
                isSearch = listing is Listing.Search,
                filterVersion = state.filterVersion,
                filters = state.filters,
            )
        }
        .distinctUntilChanged { old, new ->
            old.query == new.query &&
                old.isSearch == new.isSearch &&
                old.filterVersion == new.filterVersion
        }
        .map { request ->
            Pager(PagingConfig(pageSize = 25)) {
                getRemoteNovel.subscribe(sourceId, request.query, request.filters)
            }.flow
                .map { pagingData ->
                    pagingData
                        .map { networkNovel ->
                            val autoFavorite = autoFavoriteLocalNovels && sourceId == 0L
                            val localNovel = networkToLocalNovel.await(
                                networkNovel.toDomainNovel(sourceId),
                                autoFavorite = autoFavorite,
                            )
                            maybeFetchMissingNovelDetails(localNovel)
                            localNovel
                        }
                        .filter { !hideInLibraryItems || !it.favorite }
                }
                .combine(browseNovelCoverUpdates) { pagingData, coverUpdates ->
                    if (coverUpdates.isEmpty()) {
                        pagingData
                    } else {
                        pagingData.map { novel -> coverUpdates[novel.id]?.applyTo(novel) ?: novel }
                    }
                }
                .cachedIn(ioCoroutineScope)
        }
        .stateIn(ioCoroutineScope, SharingStarted.Lazily, emptyFlow())

    private fun maybeFetchMissingNovelDetails(novel: Novel) {
        if (novel.id <= 0L) return
        if (novel.initialized) return
        if (!novel.thumbnailUrl.isNullOrBlank()) return
        if (source !is NovelCatalogueSource) return
        if (!novelDetailsInFlight.add(novel.id)) return

        screenModelScope.launch(novelDetailsDispatcher) {
            try {
                val networkNovel = source.getNovelDetails(novel.toSNovel())
                val resolvedThumbnailUrl = networkNovel.thumbnail_url?.takeIf { it.isNotBlank() }
                if (resolvedThumbnailUrl != null) {
                    browseNovelCoverUpdates.update { updates ->
                        updates + (novel.id to BrowseNovelCoverUpdate(thumbnailUrl = resolvedThumbnailUrl))
                    }
                }
                val updatePayload = NovelUpdate(
                    id = novel.id,
                    thumbnailUrl = resolvedThumbnailUrl ?: novel.thumbnailUrl,
                    initialized = true,
                )
                resolveUpdateNovel()?.await(updatePayload)
            } catch (_: Exception) {
                // Best-effort background enrichment; keep browse flow resilient.
            } finally {
                novelDetailsInFlight.remove(novel.id)
            }
        }
    }

    fun resetFilters() {
        if (source !is NovelCatalogueSource) return

        screenModelScope.launch {
            val resetFilters = loadSourceFilters()

            mutableState.update { state ->
                state.copy(filters = resetFilters)
            }
        }
    }

    fun setListing(listing: Listing) {
        mutableState.update { it.copy(listing = listing, toolbarQuery = null) }
    }

    fun setFilters(filters: NovelFilterList) {
        if (source !is NovelCatalogueSource) return

        val changed = try {
            SavedSearchFilterSerializer.serialize(filters) != SavedSearchFilterSerializer.serialize(state.value.filters)
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

    fun search(query: String? = null, filters: NovelFilterList? = null) {
        if (source !is NovelCatalogueSource) return

        val currentState = state.value
        val updatedFilters = filters ?: currentState.filters
        val input = currentState.listing as? Listing.Search
            ?: Listing.Search(query = null, filters = updatedFilters)

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
                    filters = updatedFilters,
                ),
                filters = updatedFilters,
                toolbarQuery = query ?: input.query,
            )
        }
    }

    fun applyFilters() {
        if (source !is NovelCatalogueSource) return
        mutableState.update { state ->
            val updatedListing = when (val listing = state.listing) {
                is Listing.Search -> listing.copy(filters = state.filters)
                else -> listing
            }
            val updatedToolbarQuery = when (updatedListing) {
                is Listing.Search -> updatedListing.query
                else -> null
            }
            state.copy(
                listing = updatedListing,
                toolbarQuery = updatedToolbarQuery,
                filterVersion = state.filterVersion + 1,
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

    suspend fun getLocalNovel(novel: SNovel): Novel {
        return networkToLocalNovel.await(novel.toDomainNovel(source.id))
    }

    suspend fun openNovel(novel: SNovel): Long {
        return getLocalNovel(novel).id
    }

    suspend fun addNovelToLibrary(novel: SNovel): Boolean {
        val localNovel = getLocalNovel(novel)
        if (localNovel.favorite) return false

        val updateNovelInteractor = resolveUpdateNovel()
            ?: return false

        return updateNovelInteractor.await(
            NovelUpdate(
                id = localNovel.id,
                favorite = true,
                dateAdded = Instant.now().toEpochMilli(),
            ),
        )
    }

    fun changeNovelFavorite(novel: Novel) {
        screenModelScope.launch {
            val updateNovelInteractor = resolveUpdateNovel() ?: return@launch

            val toggled = !novel.favorite
            val added = updateNovelInteractor.await(
                NovelUpdate(
                    id = novel.id,
                    favorite = toggled,
                    dateAdded = if (toggled) Instant.now().toEpochMilli() else 0L,
                ),
            )

            if (added && toggled) {
                resolveSetNovelDefaultChapterFlags()?.await(novel)
            }
        }
    }

    fun addFavorite(novel: Novel) {
        screenModelScope.launch {
            val prefs = resolveLibraryPreferences() ?: run {
                changeNovelFavorite(novel)
                return@launch
            }
            val categoriesInteractor = resolveGetNovelCategories() ?: run {
                changeNovelFavorite(novel)
                return@launch
            }

            val categories = getCategories(categoriesInteractor)
            val defaultCategoryId = prefs.defaultNovelCategory().get().toLong()
            val defaultCategory = categories.find { it.id == defaultCategoryId }

            when {
                defaultCategory != null -> {
                    moveNovelToCategories(novel, defaultCategory)
                    changeNovelFavorite(novel)
                }
                defaultCategoryId == 0L || categories.isEmpty() -> {
                    moveNovelToCategories(novel)
                    changeNovelFavorite(novel)
                }
                else -> {
                    val preselectedIds = categoriesInteractor.await(novel.id).map { it.id }
                    setDialog(
                        Dialog.ChangeNovelCategory(
                            novel = novel,
                            initialSelection = categories
                                .mapAsCheckboxState { it.id in preselectedIds }
                                .toImmutableList(),
                        ),
                    )
                }
            }
        }
    }

    suspend fun getDuplicateLibraryNovel(novel: Novel): Novel? {
        val favoritesInteractor = resolveGetNovelFavorites() ?: return null
        return favoritesInteractor.await()
            .firstOrNull { duplicate ->
                duplicate.id != novel.id &&
                    duplicate.title.equals(novel.title, ignoreCase = true)
            }
    }

    private suspend fun getCategories(
        interactor: GetNovelCategories,
    ): List<Category> {
        return interactor.await()
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

    private fun moveNovelToCategories(novel: Novel, vararg categories: Category) {
        moveNovelToCategories(novel, categories.filter { it.id != 0L }.map { it.id })
    }

    fun moveNovelToCategories(novel: Novel, categoryIds: List<Long>) {
        val setCategoriesInteractor = resolveSetNovelCategories() ?: return
        screenModelScope.launchIO {
            setCategoriesInteractor.await(
                novelId = novel.id,
                categoryIds = categoryIds.toList(),
            )
        }
    }

    private fun resolveUpdateNovel(): UpdateNovel? {
        return updateNovel
            ?: runCatching { Injekt.get<UpdateNovel>() }.getOrNull()
    }

    private fun resolveLibraryPreferences(): LibraryPreferences? {
        return libraryPrefs
            ?: runCatching { Injekt.get<LibraryPreferences>() }.getOrNull()
    }

    private fun resolveGetNovelFavorites(): GetNovelFavorites? {
        return getNovelFavoritesInteractor
            ?: runCatching { Injekt.get<GetNovelFavorites>() }.getOrNull()
    }

    private fun resolveGetNovelCategories(): GetNovelCategories? {
        return getNovelCategoriesInteractor
            ?: runCatching { Injekt.get<GetNovelCategories>() }.getOrNull()
    }

    private fun resolveSetNovelCategories(): SetNovelCategories? {
        return setNovelCategoriesInteractor
            ?: runCatching { Injekt.get<SetNovelCategories>() }.getOrNull()
    }

    private fun resolveSetNovelDefaultChapterFlags(): SetNovelDefaultChapterFlags? {
        return setNovelDefaultChapterFlagsInteractor
            ?: runCatching { Injekt.get<SetNovelDefaultChapterFlags>() }.getOrNull()
    }

    private fun resolveGetNovel(): GetNovel? {
        return getNovelInteractor
            ?: runCatching { Injekt.get<GetNovel>() }.getOrNull()
    }

    private fun resolveGetSavedSearchById(): GetSavedSearchById? {
        return getSavedSearchByIdInteractor
            ?: runCatching { Injekt.get<GetSavedSearchById>() }.getOrNull()
    }

    private fun resolveGetSavedSearchBySourceId(): GetSavedSearchBySourceId? {
        return getSavedSearchBySourceIdInteractor
            ?: runCatching { Injekt.get<GetSavedSearchBySourceId>() }.getOrNull()
    }

    private fun resolveInsertSavedSearch(): InsertSavedSearch? {
        return insertSavedSearchInteractor
            ?: runCatching { Injekt.get<InsertSavedSearch>() }.getOrNull()
    }

    private fun resolveDeleteSavedSearchById(): DeleteSavedSearchById? {
        return deleteSavedSearchByIdInteractor
            ?: runCatching { Injekt.get<DeleteSavedSearchById>() }.getOrNull()
    }

    sealed class Listing(open val query: String?, open val filters: NovelFilterList) {
        data object Popular : Listing(
            query = GetRemoteNovel.QUERY_POPULAR,
            filters = NovelFilterList(),
        )
        data object Latest : Listing(
            query = GetRemoteNovel.QUERY_LATEST,
            filters = NovelFilterList(),
        )
        data class Search(override val query: String?, override val filters: NovelFilterList) : Listing(
            query = query,
            filters = filters,
        )

        companion object {
            fun valueOf(query: String?): Listing {
                return when (query) {
                    null -> Popular
                    GetRemoteNovel.QUERY_POPULAR -> Popular
                    GetRemoteNovel.QUERY_LATEST -> Latest
                    else -> Search(query = query, filters = NovelFilterList())
                }
            }
        }
    }

    sealed interface Dialog {
        data object Filter : Dialog
        data object CreateSavedSearch : Dialog
        data class DeleteSavedSearch(val savedSearch: SavedSearch) : Dialog
        data class RemoveNovel(val novel: Novel) : Dialog
        data class AddDuplicateNovel(val novel: Novel, val duplicate: Novel) : Dialog
        data class Migrate(val newNovel: Novel, val oldNovel: Novel) : Dialog
        data class ChangeNovelCategory(
            val novel: Novel,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog
    }

    @Immutable
    data class State(
        val listing: Listing,
        val filters: NovelFilterList = NovelFilterList(),
        val toolbarQuery: String? = null,
        val dialog: Dialog? = null,
        val filterVersion: Int = 0,
        val savedSearches: ImmutableList<Pair<SavedSearch, Boolean>> = persistentListOf(),
        val isSourceConfigurable: Boolean = false,
    ) {
        val isUserQuery get() = listing is Listing.Search && !listing.query.isNullOrEmpty()
        val filterable get() = savedSearches.isNotEmpty()
    }

    private data class PagingRequest(
        val query: String,
        val isSearch: Boolean,
        val filterVersion: Int,
        val filters: NovelFilterList,
    )

    private data class BrowseNovelCoverUpdate(
        val thumbnailUrl: String,
    ) {
        fun applyTo(novel: Novel): Novel {
            return if (novel.thumbnailUrl == thumbnailUrl) {
                novel
            } else {
                novel.copy(thumbnailUrl = thumbnailUrl)
            }
        }
    }
}
