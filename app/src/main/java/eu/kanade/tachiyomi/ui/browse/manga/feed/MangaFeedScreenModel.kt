package eu.kanade.tachiyomi.ui.browse.manga.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.manga.model.toDomainManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.source.interactor.CountFeedSavedSearchGlobal
import tachiyomi.domain.source.interactor.DeleteFeedSavedSearchById
import tachiyomi.domain.source.interactor.GetFeedSavedSearchGlobal
import tachiyomi.domain.source.interactor.GetSavedSearchById
import tachiyomi.domain.source.interactor.GetSavedSearchBySourceId
import tachiyomi.domain.source.interactor.InsertFeedSavedSearch
import tachiyomi.domain.source.interactor.ReorderFeed
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer

data class MangaFeedItemUI(
    val feed: FeedSavedSearch,
    val savedSearch: SavedSearch?,
    val source: CatalogueSource,
    val title: String,
    val subtitle: String,
    val results: List<Manga>?,
)

data class MangaFeedScreenState(
    val items: List<MangaFeedItemUI>? = null,
    val isReordering: Boolean = false,
    val dialog: MangaFeedScreenModel.Dialog? = null,
) {
    val isLoading get() = items == null
    val isEmpty get() = items.isNullOrEmpty()
    val isLoadingItems get() = items?.any { it.results == null } == true
}

class MangaFeedScreenModel(
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: MangaSourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getFeedSavedSearchGlobal: GetFeedSavedSearchGlobal = Injekt.get(),
    private val insertFeedSavedSearch: InsertFeedSavedSearch = Injekt.get(),
    private val deleteFeedSavedSearchById: DeleteFeedSavedSearchById = Injekt.get(),
    private val countFeedSavedSearchGlobal: CountFeedSavedSearchGlobal = Injekt.get(),
    private val reorderFeed: ReorderFeed = Injekt.get(),
    private val getSavedSearchBySourceId: GetSavedSearchBySourceId = Injekt.get(),
    private val getSavedSearchById: GetSavedSearchById = Injekt.get(),
    private val filterSerializer: FilterSerializer = Injekt.get(),
) : StateScreenModel<MangaFeedScreenState>(MangaFeedScreenState()) {

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    sealed interface Dialog {
        data class AddSource(val sources: List<CatalogueSource>) : Dialog
        data class AddSearch(val source: CatalogueSource, val savedSearches: List<SavedSearch>) : Dialog
        data class DeleteSource(val feed: FeedSavedSearch, val source: CatalogueSource) : Dialog
    }

    sealed interface Event {
        data object FailedFetchingSources : Event
    }

    init {
        getFeedSavedSearchGlobal.subscribe()
            .distinctUntilChanged()
            .onEach { feedEntries ->
                sourceManager.isInitialized.first { it }
                val items = resolveFeedItems(feedEntries)
                mutableState.update { it.copy(items = items) }
                loadFeed(items)
            }
            .catch { _events.send(Event.FailedFetchingSources) }
            .launchIn(screenModelScope)
    }

    private fun resolveFeedItems(feedEntries: List<FeedSavedSearch>): List<MangaFeedItemUI> {
        return feedEntries.mapNotNull { feed ->
            val source = sourceManager.get(feed.source) as? CatalogueSource ?: return@mapNotNull null
            val savedSearch = feed.savedSearch?.let { id ->
                // Not loading async here — will be filled in init continuation
                null
            }
            MangaFeedItemUI(
                feed = feed,
                savedSearch = null, // resolved lazily below
                source = source,
                title = source.name,
                subtitle = LocaleHelper.getLocalizedDisplayName(source.lang),
                results = null,
            )
        }
    }

    private fun loadFeed(items: List<MangaFeedItemUI>) {
        val hideInLibrary = sourcePreferences.hideInLibraryFeedItems().get()
        ioCoroutineScope.launch {
            val results = items.map { itemUI ->
                async {
                    try {
                        val mangas = if (itemUI.feed.savedSearch != null) {
                            val feed = itemUI.feed
                            val savedSearchId = feed.savedSearch
                            val ss = if (savedSearchId != null) getSavedSearchById.await(savedSearchId) else null
                            if (ss != null) {
                                val baseFilters = itemUI.source.getFilterList()
                                val filtersJson = ss.filtersJson
                                if (filtersJson != null) {
                                    filterSerializer.deserialize(
                                        baseFilters,
                                        Json.parseToJsonElement(filtersJson).jsonArray,
                                    )
                                }
                                itemUI.source.getSearchManga(1, ss.query ?: "", baseFilters).mangas
                            } else {
                                itemUI.source.getLatestUpdates(1).mangas
                            }
                        } else if (itemUI.source.supportsLatest) {
                            itemUI.source.getLatestUpdates(1).mangas
                        } else {
                            itemUI.source.getPopularManga(1).mangas
                        }
                        val converted = mangas.map { smanga ->
                            networkToLocalManga.await(smanga.toDomainManga(itemUI.source.id))
                        }.filter { !hideInLibrary || !it.favorite }
                        itemUI to converted
                    } catch (_: Exception) {
                        itemUI to emptyList<Manga>()
                    }
                }
            }.awaitAll()
            mutableState.update { state ->
                val updatedItems = state.items?.map { item ->
                    val pair = results.find { it.first.source.id == item.source.id }
                    if (pair != null) item.copy(results = pair.second) else item
                }
                state.copy(items = updatedItems)
            }
        }
    }

    fun refresh() {
        val currentItems = mutableState.value.items
        if (currentItems != null) {
            val resetItems = currentItems.map { it.copy(results = null) }
            mutableState.update { it.copy(items = resetItems) }
            loadFeed(resetItems)
        }
    }

    fun openAddSourceDialog() {
        val currentFeedIds = mutableState.value.items?.map { it.source.id }?.toSet() ?: emptySet()
        val enabledLanguages = sourcePreferences.enabledLanguages().get()
        val disabledSources = sourcePreferences.disabledMangaSources().get()
        val sources = sourceManager.getCatalogueSources()
            .distinctBy { it.id }
            .filter { "${it.id}" !in disabledSources }
            .filter { it.lang in enabledLanguages }
            .filter { it.id !in currentFeedIds }
            .sortedWith(compareBy { "${it.name.lowercase()} (${it.lang})" })
        mutableState.update { it.copy(dialog = Dialog.AddSource(sources)) }
    }

    fun onSourceSelected(source: CatalogueSource) {
        screenModelScope.launch {
            val savedSearches = getSavedSearchBySourceId.await(source.id)
            mutableState.update { it.copy(dialog = Dialog.AddSearch(source, savedSearches)) }
        }
    }

    fun addFeed(source: CatalogueSource, savedSearch: SavedSearch?) {
        val feed = FeedSavedSearch(
            id = -1,
            source = source.id,
            savedSearch = savedSearch?.id,
            global = true,
            feedOrder = 0,
        )
        screenModelScope.launch { insertFeedSavedSearch.await(feed) }
        dismissDialog()
    }

    fun openDeleteDialog(feed: FeedSavedSearch) {
        val source = sourceManager.get(feed.source) as? CatalogueSource ?: return
        mutableState.update { it.copy(dialog = Dialog.DeleteSource(feed = feed, source = source)) }
    }

    fun removeSource(feed: FeedSavedSearch) {
        screenModelScope.launch { deleteFeedSavedSearchById.await(feed.id) }
        dismissDialog()
    }

    fun toggleReordering() {
        mutableState.update { it.copy(isReordering = !it.isReordering) }
        if (!mutableState.value.isReordering) refresh()
    }

    fun reorderFeed(feed: FeedSavedSearch, newIndex: Int) {
        screenModelScope.launch { reorderFeed.changeOrder(feed, newIndex) }
    }

    @Composable
    fun getManga(initialManga: Manga): State<Manga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collectLatest { manga -> value = manga }
        }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }
}
