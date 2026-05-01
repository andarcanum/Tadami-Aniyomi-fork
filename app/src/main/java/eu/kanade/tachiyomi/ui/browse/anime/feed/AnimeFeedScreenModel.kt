package eu.kanade.tachiyomi.ui.browse.anime.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.ui.browse.search.SavedSearchFilterSerializer
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
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.interactor.CountFeedSavedSearchGlobal
import tachiyomi.domain.source.interactor.DeleteFeedSavedSearchById
import tachiyomi.domain.source.interactor.GetFeedSavedSearchGlobal
import tachiyomi.domain.source.interactor.GetSavedSearchById
import tachiyomi.domain.source.interactor.GetSavedSearchBySourceId
import tachiyomi.domain.source.interactor.InsertFeedSavedSearch
import tachiyomi.domain.source.interactor.ReorderFeed
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.model.SourceType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class AnimeFeedItemUI(
    val feed: FeedSavedSearch,
    val savedSearch: SavedSearch?,
    val source: AnimeCatalogueSource,
    val title: String,
    val subtitle: String,
    val results: List<Anime>?,
)

data class AnimeFeedScreenState(
    val items: List<AnimeFeedItemUI>? = null,
    val isReordering: Boolean = false,
    val dialog: AnimeFeedScreenModel.Dialog? = null,
) {
    val isLoading get() = items == null
    val isEmpty get() = items.isNullOrEmpty()
    val isLoadingItems get() = items?.any { it.results == null } == true
}

class AnimeFeedScreenModel(
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val getFeedSavedSearchGlobal: GetFeedSavedSearchGlobal = Injekt.get(),
    private val insertFeedSavedSearch: InsertFeedSavedSearch = Injekt.get(),
    private val deleteFeedSavedSearchById: DeleteFeedSavedSearchById = Injekt.get(),
    private val countFeedSavedSearchGlobal: CountFeedSavedSearchGlobal = Injekt.get(),
    private val reorderFeed: ReorderFeed = Injekt.get(),
    private val getSavedSearchBySourceId: GetSavedSearchBySourceId = Injekt.get(),
    private val getSavedSearchById: GetSavedSearchById = Injekt.get(),
) : StateScreenModel<AnimeFeedScreenState>(AnimeFeedScreenState()) {

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    sealed interface Dialog {
        data class AddSource(val sources: List<AnimeCatalogueSource>) : Dialog
        data class AddSearch(val source: AnimeCatalogueSource, val savedSearches: List<SavedSearch>) : Dialog
        data class DeleteSource(val feed: FeedSavedSearch, val source: AnimeCatalogueSource) : Dialog
    }

    sealed interface Event {
        data object FailedFetchingSources : Event
    }

    init {
        getFeedSavedSearchGlobal.subscribe(SourceType.ANIME)
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

    private fun resolveFeedItems(feedEntries: List<FeedSavedSearch>): List<AnimeFeedItemUI> {
        return feedEntries.mapNotNull { feed ->
            val source = sourceManager.get(feed.source) as? AnimeCatalogueSource ?: return@mapNotNull null
            AnimeFeedItemUI(
                feed = feed,
                savedSearch = null,
                source = source,
                title = source.name,
                subtitle = LocaleHelper.getLocalizedDisplayName(source.lang),
                results = null,
            )
        }
    }

    private fun loadFeed(items: List<AnimeFeedItemUI>) {
        val hideInLibrary = sourcePreferences.hideInLibraryFeedItems().get()
        ioCoroutineScope.launch {
            val results = items.map { itemUI ->
                async {
                    try {
                        val animes = if (itemUI.feed.savedSearch != null) {
                            val feed = itemUI.feed
                            val savedSearchId = feed.savedSearch
                            val ss = if (savedSearchId != null) getSavedSearchById.await(savedSearchId) else null
                            if (ss != null) {
                                val filtersJson = ss.filtersJson
                                val baseFilters = itemUI.source.getFilterList()
                                if (filtersJson != null) {
                                    SavedSearchFilterSerializer.deserialize(filtersJson, baseFilters)
                                }
                                itemUI.source.getSearchAnime(1, ss.query ?: "", baseFilters).animes
                            } else {
                                itemUI.source.getLatestUpdates(1).animes
                            }
                        } else if (itemUI.source.supportsLatest) {
                            itemUI.source.getLatestUpdates(1).animes
                        } else {
                            itemUI.source.getPopularAnime(1).animes
                        }
                        val converted = animes.map { sanime ->
                            networkToLocalAnime.await(sanime.toDomainAnime(itemUI.source.id))
                        }.filter { !hideInLibrary || !it.favorite }
                        itemUI to converted
                    } catch (_: Exception) {
                        itemUI to emptyList<Anime>()
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
        val disabledSources = sourcePreferences.disabledAnimeSources().get()
        val sources = sourceManager.getCatalogueSources()
            .distinctBy { it.id }
            .filter { "${it.id}" !in disabledSources }
            .filter { it.lang in enabledLanguages }
            .filter { it.id !in currentFeedIds }
            .sortedWith(compareBy { "${it.name.lowercase()} (${it.lang})" })
        mutableState.update { it.copy(dialog = Dialog.AddSource(sources)) }
    }

    fun onSourceSelected(source: AnimeCatalogueSource) {
        screenModelScope.launch {
            val savedSearches = getSavedSearchBySourceId.await(source.id, SourceType.ANIME)
            mutableState.update { it.copy(dialog = Dialog.AddSearch(source, savedSearches)) }
        }
    }

    fun addFeed(source: AnimeCatalogueSource, savedSearch: SavedSearch?) {
        val feed = FeedSavedSearch(
            id = -1,
            source = source.id,
            sourceType = SourceType.ANIME,
            savedSearch = savedSearch?.id,
            global = true,
            feedOrder = 0,
        )
        screenModelScope.launch { insertFeedSavedSearch.await(feed) }
        dismissDialog()
    }

    fun openDeleteDialog(feed: FeedSavedSearch) {
        val source = sourceManager.get(feed.source) as? AnimeCatalogueSource ?: return
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
    fun getAnime(initialAnime: Anime): State<Anime> {
        return produceState(initialValue = initialAnime) {
            getAnime.subscribe(initialAnime.url, initialAnime.source)
                .filterNotNull()
                .collectLatest { anime -> value = anime }
        }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }
}
