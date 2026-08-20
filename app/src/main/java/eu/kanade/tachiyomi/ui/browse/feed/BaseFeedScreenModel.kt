package eu.kanade.tachiyomi.ui.browse.feed

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.source.interactor.DeleteFeedSavedSearchById
import tachiyomi.domain.source.interactor.GetFeedSavedSearchGlobal
import tachiyomi.domain.source.interactor.GetSavedSearchById
import tachiyomi.domain.source.interactor.GetSavedSearchBySourceId
import tachiyomi.domain.source.interactor.InsertFeedSavedSearch
import tachiyomi.domain.source.interactor.ReorderFeed
import tachiyomi.domain.source.model.FeedListingType
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.model.SourceType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Shared state machine for the anime/manga/novel feed tabs.
 *
 * The feed tabs used to be three near-identical per-media copies (one per source
 * type). Everything that does not depend on the concrete media type — observing
 * the global feed entries, dialog orchestration, add/delete/reorder flows and
 * refresh — lives here. Subclasses only resolve their typed sources and fetch
 * the typed results.
 *
 * @param STATE per-media feed state (see [FeedScreenState])
 * @param ITEM per-media feed item UI model
 */
abstract class BaseFeedScreenModel<STATE, ITEM : BaseFeedScreenModel.FeedItemUi>(
    initialState: STATE,
    protected open val sourcePreferences: SourcePreferences = Injekt.get(),
    private val getFeedSavedSearchGlobal: GetFeedSavedSearchGlobal = Injekt.get(),
    private val insertFeedSavedSearch: InsertFeedSavedSearch = Injekt.get(),
    private val deleteFeedSavedSearchById: DeleteFeedSavedSearchById = Injekt.get(),
    private val reorderFeed: ReorderFeed = Injekt.get(),
    private val getSavedSearchBySourceId: GetSavedSearchBySourceId = Injekt.get(),
    protected val getSavedSearchById: GetSavedSearchById = Injekt.get(),
) : StateScreenModel<STATE>(initialState) {

    /** Media type managed by this feed (anime, manga or novel). */
    abstract val sourceType: SourceType

    /** Common supertype of the per-media feed items used by the shared flows. */
    interface FeedItemUi {
        val feed: FeedSavedSearch
        val results: List<*>?
    }

    /** Typed-source-free source descriptor handed to the shared management UI. */
    data class FeedSourceCandidate(
        val id: Long,
        val lang: String,
        val name: String,
        val supportsLatest: Boolean,
    )

    sealed interface FeedDialog {
        data class AddSource(val sources: List<FeedSourceCandidate>) : FeedDialog
        data class AddSearch(val source: FeedSourceCandidate, val savedSearches: List<SavedSearch>) : FeedDialog
        data class DeleteSource(val feed: FeedSavedSearch, val source: FeedSourceCandidate) : FeedDialog
    }

    sealed interface FeedEvent {
        data object FailedFetchingSources : FeedEvent
    }

    private val _events = Channel<FeedEvent>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    // Abstract hooks implemented by each media type.

    protected abstract suspend fun awaitSourcesInitialized()
    protected abstract fun itemsOf(state: STATE): List<ITEM>?
    protected abstract fun withItems(state: STATE, items: List<ITEM>?): STATE
    protected abstract fun isReorderingOf(state: STATE): Boolean
    protected abstract fun withReordering(state: STATE, reordering: Boolean): STATE
    protected abstract fun withDialog(state: STATE, dialog: FeedDialog?): STATE
    protected abstract suspend fun resolveFeedItems(entries: List<FeedSavedSearch>): List<ITEM>
    protected abstract fun loadFeed(items: List<ITEM>)
    protected abstract fun clearResults(items: List<ITEM>): List<ITEM>
    protected abstract fun allSourceCandidates(): List<FeedSourceCandidate>
    protected abstract fun disabledSourceIds(): Set<String>
    protected abstract fun candidateFor(id: Long): FeedSourceCandidate?

    /** Starts observing the feed entries; call from the subclass init. */
    protected fun startFeedSubscription() {
        getFeedSavedSearchGlobal.subscribe(sourceType)
            .distinctUntilChanged()
            .onEach { feedEntries ->
                awaitSourcesInitialized()
                val items = resolveFeedItems(feedEntries)
                mutableState.update { withItems(it, items) }
                loadFeed(items)
            }
            .catch { _events.send(FeedEvent.FailedFetchingSources) }
            .launchIn(screenModelScope)
    }

    fun refresh() {
        val currentItems = itemsOf(mutableState.value)
        if (currentItems != null) {
            val resetItems = clearResults(currentItems)
            mutableState.update { withItems(it, resetItems) }
            loadFeed(resetItems)
        }
    }

    fun openAddSourceDialog() {
        val currentFeedIds = itemsOf(mutableState.value)?.map { it.feed.source }?.toSet() ?: emptySet()
        val enabledLanguages = sourcePreferences.enabledLanguages().get()
        val sources = allSourceCandidates()
            .distinctBy { it.id }
            .filter { "${it.id}" !in disabledSourceIds() }
            .filter { it.lang in enabledLanguages }
            .filter { it.id !in currentFeedIds }
            .sortedWith(compareBy { "${it.name.lowercase()} (${it.lang})" })
        mutableState.update { withDialog(it, FeedDialog.AddSource(sources)) }
    }

    fun onSourceSelected(candidate: FeedSourceCandidate) {
        screenModelScope.launch {
            val savedSearches = getSavedSearchBySourceId.await(candidate.id, sourceType)
            mutableState.update { withDialog(it, FeedDialog.AddSearch(candidate, savedSearches)) }
        }
    }

    fun addFeed(candidate: FeedSourceCandidate, listingType: FeedListingType, savedSearch: SavedSearch?) {
        val feed = FeedSavedSearch(
            id = -1,
            source = candidate.id,
            sourceType = sourceType,
            listingType = listingType,
            savedSearch = savedSearch?.id,
            global = true,
            feedOrder = 0,
        )
        screenModelScope.launch { insertFeedSavedSearch.await(feed) }
        dismissDialog()
    }

    fun openDeleteDialog(feed: FeedSavedSearch) {
        val candidate = candidateFor(feed.source) ?: return
        mutableState.update { withDialog(it, FeedDialog.DeleteSource(feed = feed, source = candidate)) }
    }

    fun removeSource(feed: FeedSavedSearch) {
        screenModelScope.launch { deleteFeedSavedSearchById.await(feed.id) }
        dismissDialog()
    }

    fun toggleReordering() {
        mutableState.update { withReordering(it, !isReorderingOf(it)) }
        if (!isReorderingOf(mutableState.value)) refresh()
    }

    fun reorderFeed(feed: FeedSavedSearch, newIndex: Int) {
        screenModelScope.launch { reorderFeed.changeOrder(feed, newIndex) }
    }

    fun dismissDialog() {
        mutableState.update { withDialog(it, null) }
    }
}

/** Shared immutable state for every per-media feed tab. */
data class FeedScreenState<ITEM : BaseFeedScreenModel.FeedItemUi>(
    val items: List<ITEM>? = null,
    val isReordering: Boolean = false,
    val dialog: BaseFeedScreenModel.FeedDialog? = null,
) {
    val isLoading get() = items == null
    val isEmpty get() = items.isNullOrEmpty()
    val isLoadingItems get() = items?.any { it.results == null } == true
}
