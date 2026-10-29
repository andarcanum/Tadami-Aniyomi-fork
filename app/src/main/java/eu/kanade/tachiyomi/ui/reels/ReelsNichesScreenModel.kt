package eu.kanade.tachiyomi.ui.reels

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.animesource.AnimeFeedBrowseSource
import eu.kanade.tachiyomi.animesource.model.FeedCategory
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.reels.anime.model.ReelsAlbum
import tachiyomi.domain.reels.anime.repository.ReelsAlbumRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

/**
 * Category directory of one feed source (contract v20, AnimeFeedBrowseSource): paginated
 * rows the Niches screen renders as a preview grid. Pagination follows the v17 sticky
 * protocol on its own stream. A source without the capability (or a vanished source) yields
 * an empty terminal state, not an error.
 */
class ReelsNichesScreenModel(
    private val sourceId: Long,
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val albumRepository: ReelsAlbumRepository = Injekt.get(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StateScreenModel<ReelsNichesScreenModel.State>(ReelsNichesScreenModel.State()) {

    data class State(
        val categories: ImmutableList<FeedCategory> = persistentListOf(),
        val isLoading: Boolean = true,
        val error: String? = null,
        // Transient append failure while the grid is non-empty: kept surfable as a snackbar
        // instead of silently cutting pagination (the error state owns the empty grid only).
        val pageError: String? = null,
        val canLoadMore: Boolean = true,
        val nextPage: Int = 1,
        val nextCursor: String? = null,
        val cursorMode: Boolean = false,
        // Albums collection marks for the add-to-collection button on each category cell.
        val savedAlbumIds: Set<String> = emptySet(),
        // Local filter over the loaded directory (channel title or @username).
        val searchQuery: String = "",
    ) {
        /**
         * Categories matching [searchQuery]. The filter is local and instant: a feed source
         * serves its whole directory in one page (hasNextPage = false), so this searches the
         * full known catalog rather than the visible slice of the grid — and never falls back
         * to a feed search request. Leading "@" is ignored so "@durov" and "durov" match.
         */
        val visibleCategories: ImmutableList<FeedCategory>
            get() {
                val needle = searchQuery.trim().removePrefix("@").trim()
                if (needle.isEmpty()) return categories
                return categories
                    .filter { it.name.contains(needle, ignoreCase = true) || it.id.contains(needle, ignoreCase = true) }
                    .toImmutableList()
            }
    }

    // Monotonic generation token (the same pattern as ReelsFeedScreenModel): every retry and
    // every loadMore bumps it, and a superseded attempt must not append its page or clobber
    // the cursor of a fresh generation — closing the retry/loadMore race.
    private val loadGeneration = AtomicInteger(0)

    @Volatile
    private var loading = false
    private var loadJob: Job? = null

    // Saved-mark mutations are serialized and decided against the CURRENT DB state, so two
    // rapid taps cannot both insert (or both delete) from a stale snapshot.
    private val savedToggleMutex = Mutex()

    init {
        loadMore()
        // F05: marks follow the collection repository, including saves/removals made on the
        // albums or feed surfaces while this screen sits in the back stack.
        screenModelScope.launch {
            albumRepository.subscribeBySource(sourceId).collectLatest { albums ->
                mutableState.update { it.copy(savedAlbumIds = albums.map { a -> a.albumId }.toSet()) }
            }
        }
    }

    /** Adds/removes a category to/from the albums collection (device feature entry point). */
    fun toggleAlbumSaved(category: FeedCategory) {
        screenModelScope.launch(ioDispatcher) {
            savedToggleMutex.withLock {
                val saved = runCatching { albumRepository.getBySource(sourceId) }
                    .getOrDefault(emptyList())
                    .any { it.albumId == category.id }
                if (saved) {
                    albumRepository.delete(sourceId, category.id)
                } else {
                    albumRepository.insert(
                        ReelsAlbum(
                            sourceId = sourceId,
                            albumId = category.id,
                            name = category.name,
                            coverUrl = category.imageUrl,
                            addedAt = Date(),
                        ),
                    )
                }
            }
        }
    }

    /** Resets the stream and starts over (error state retry). */
    fun retry() {
        loadGeneration.incrementAndGet()
        loadJob?.cancel()
        loading = false
        mutableState.update { State(searchQuery = it.searchQuery) }
        loadMore()
    }

    /**
     * Filters the loaded directory by channel title or @username. Purely local: the source's
     * category page is already the whole catalog, so no extra request (and no feed search)
     * is issued while typing.
     */
    fun onSearchQueryChange(query: String) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun loadMore() {
        if (loading || !state.value.canLoadMore) return
        val src = sourceManager.get(sourceId) as? AnimeFeedBrowseSource
        if (src == null) {
            mutableState.update { it.copy(isLoading = false, canLoadMore = false) }
            return
        }
        loading = true
        val generation = loadGeneration.incrementAndGet()
        // Snapshot the pagination inputs on the calling (main) thread.
        val snapshot = state.value
        val page = snapshot.nextPage
        val cursor = if (snapshot.cursorMode) snapshot.nextCursor else null
        loadJob = screenModelScope.launch(ioDispatcher) {
            val result = runCatching { src.getBrowseCategories(page, cursor) }
            // Structured concurrency: a cancellation (screen disposal / superseding load)
            // must propagate, not be laundered into a "feed error" state write.
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            if (loadGeneration.get() == generation) loading = false
            mutableState.update { state ->
                // A retry() that raced this page must own the state: drop the stale write.
                if (loadGeneration.get() != generation) return@update state
                val pageData = result.getOrNull()
                if (pageData == null) {
                    val message = result.exceptionOrNull()?.localizedMessage
                    if (state.categories.isEmpty()) {
                        state.copy(isLoading = false, canLoadMore = false, error = message)
                    } else {
                        // Mid-feed failure: keep the grid usable; scrolling away and back to
                        // the bottom re-arms pagination, and the error is transient.
                        state.copy(isLoading = false, pageError = message, canLoadMore = true)
                    }
                } else {
                    val step = ReelsPagination(
                        nextPage = state.nextPage,
                        nextCursor = state.nextCursor,
                        cursorMode = state.cursorMode,
                        canLoadMore = state.canLoadMore,
                    ).advance(pageData.hasNextPage, pageData.nextCursor)
                    if (step.cursorLost) {
                        logcat(LogPriority.WARN) {
                            "Niches of $sourceId dropped their cursor mid-feed; stopping pagination (contract v17)."
                        }
                    }
                    state.copy(
                        isLoading = false,
                        error = null,
                        pageError = null,
                        // Duplicate ids would collide in the lazy-grid key space.
                        categories = (state.categories + pageData.categories).distinctBy { it.id }.toImmutableList(),
                        canLoadMore = step.pagination.canLoadMore,
                        nextPage = step.pagination.nextPage,
                        nextCursor = step.pagination.nextCursor,
                        cursorMode = step.pagination.cursorMode,
                    )
                }
            }
        }
    }

    fun onPageErrorShown() {
        mutableState.update { it.copy(pageError = null) }
    }
}
