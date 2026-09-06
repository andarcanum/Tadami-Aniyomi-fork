package eu.kanade.tachiyomi.ui.browse.feed

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
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
import kotlin.coroutines.cancellation.CancellationException

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

        /** BFEED-5: non-null when the last load of this row FAILED (vs. genuinely empty). */
        val loadError: String?
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
        data object ReorderFailed : FeedEvent
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

    /**
     * Loads page-1 results for [items]. BFEED-2: suspend (runs INSIDE the cancellable load job
     * managed by [startLoad]) - subclasses used to launch their own detached coroutine per
     * call, so concurrent loads raced and a stale one could overwrite a newer one's results.
     */
    protected abstract suspend fun loadFeed(items: List<ITEM>)

    protected abstract fun clearResults(items: List<ITEM>): List<ITEM>
    protected abstract fun allSourceCandidates(): List<FeedSourceCandidate>
    protected abstract fun disabledSourceIds(): Set<String>
    protected abstract fun candidateFor(id: Long): FeedSourceCandidate?

    private var loadJob: Job? = null

    /** BFEED-13: set when a drag actually changed the order (skip the exit refresh otherwise). */
    private var orderDirty = false

    /** BFEED-3 layer 2: single-flight guard for [addFeed] (see there). */
    @Volatile
    private var addInFlight = false

    /** Starts observing the feed entries; call from the subclass init. */
    protected fun startFeedSubscription() {
        getFeedSavedSearchGlobal.subscribe(sourceType)
            .distinctUntilChanged()
            // BFEED-2: every emission (including EVERY reorder drag step, each writing the DB)
            // used to trigger a full loadFeed - N drag steps = N parallel full page-1 network
            // fan-outs to every source. conflate() drops intermediate emissions while one is
            // being processed, and reorder steps skip the load entirely (toggleReordering
            // refreshes once on exit).
            .conflate()
            .onEach { feedEntries ->
                awaitSourcesInitialized()
                val items = resolveFeedItems(feedEntries)
                mutableState.update { withItems(it, items) }
                if (!isReorderingOf(mutableState.value)) {
                    startLoad(items)
                }
            }
            .catch { _events.send(FeedEvent.FailedFetchingSources) }
            .launchIn(screenModelScope)
    }

    private fun startLoad(items: List<ITEM>) {
        // BFEED-2: a new load cancels the previous one - no stale completion can overwrite
        // fresher results anymore.
        loadJob?.cancel()
        loadJob = screenModelScope.launch {
            try {
                loadFeed(items)
            } finally {
                if (loadJob === currentCoroutineContext()[Job]) loadJob = null
            }
        }
    }

    fun refresh() {
        val currentItems = itemsOf(mutableState.value)
        if (currentItems != null) {
            val resetItems = clearResults(currentItems)
            mutableState.update { withItems(it, resetItems) }
            startLoad(resetItems)
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
        // BFEED-3 layer 2: single-flight + fresh-state dedup. The candidate filter in
        // openAddSourceDialog reads STATE, which lags the async insert by a flow emission:
        // a double tap in one frame (or reopening the dialog before the state caught up)
        // inserted the same feed row twice - duplicate rows corrupted ordering and (before
        // the feed.id keys) crash-looped the tab. The unique index (layer 3) backstops this.
        if (addInFlight) {
            dismissDialog()
            return
        }
        val alreadyInFeed = itemsOf(mutableState.value)?.any {
            it.feed.source == candidate.id &&
                it.feed.listingType == listingType &&
                it.feed.savedSearch == savedSearch?.id
        } == true
        if (alreadyInFeed) {
            dismissDialog()
            return
        }
        addInFlight = true
        val feed = FeedSavedSearch(
            id = -1,
            source = candidate.id,
            sourceType = sourceType,
            listingType = listingType,
            savedSearch = savedSearch?.id,
            global = true,
            feedOrder = 0,
        )
        screenModelScope.launch {
            try {
                insertFeedSavedSearch.await(feed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The unique index rejects a racing duplicate; surface it as a fetch failure
                // instead of crashing the handler-less screenModelScope.
                _events.send(FeedEvent.FailedFetchingSources)
            } finally {
                addInFlight = false
            }
        }
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
        val wasReordering = isReorderingOf(mutableState.value)
        mutableState.update { withReordering(it, !wasReordering) }
        // BFEED-13: refresh on exit only when the order actually changed - every toggle used
        // to re-fan the full page-1 network load even when nothing moved.
        if (wasReordering && orderDirty) {
            orderDirty = false
            refresh()
        }
    }

    fun reorderFeed(feed: FeedSavedSearch, newIndex: Int) {
        orderDirty = true
        val visible = itemsOf(mutableState.value)?.map { it.feed } ?: return
        val currentIndex = visible.indexOfFirst { it.id == feed.id }
        if (currentIndex == -1) return
        val reordered = visible.toMutableList().apply {
            add(newIndex.coerceIn(0, size - 1), removeAt(currentIndex))
        }
        screenModelScope.launch {
            // RESH-B15/BFEED-1: hand the interactor the full VISIBLE order after the move; it
            // maps it onto the full DB list (rows whose sources no longer resolve keep their
            // slots). The raw newIndex from the filtered UI used to be inserted into the DB
            // list directly - wrong landing position with orphan rows above the drop point.
            // BFEED-12: an InternalError used to vanish silently (order snapped back on the
            // next emission with no explanation).
            when (reorderFeed.changeOrder(sourceType, reordered.map { it.id })) {
                is ReorderFeed.Result.InternalError -> _events.send(FeedEvent.ReorderFailed)
                else -> Unit
            }
        }
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
