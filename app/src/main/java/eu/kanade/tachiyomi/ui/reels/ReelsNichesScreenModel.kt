package eu.kanade.tachiyomi.ui.reels

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.animesource.AnimeFeedBrowseSource
import eu.kanade.tachiyomi.animesource.model.FeedCategory
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Category directory of one feed source (contract v20, AnimeFeedBrowseSource): paginated
 * rows the Niches screen renders as a preview grid. Pagination follows the v17 sticky
 * protocol on its own stream. A source without the capability (or a vanished source) yields
 * an empty terminal state, not an error.
 */
class ReelsNichesScreenModel(
    private val sourceId: Long,
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StateScreenModel<ReelsNichesScreenModel.State>(ReelsNichesScreenModel.State()) {

    data class State(
        val categories: ImmutableList<FeedCategory> = persistentListOf(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val canLoadMore: Boolean = true,
        val nextPage: Int = 1,
        val nextCursor: String? = null,
        val cursorMode: Boolean = false,
    )

    @Volatile
    private var loading = false

    init {
        loadMore()
    }

    /** Resets the stream and starts over (error state retry). */
    fun retry() {
        loading = false
        mutableState.update { State() }
        loadMore()
    }

    fun loadMore() {
        if (loading || !state.value.canLoadMore) return
        val src = sourceManager.get(sourceId) as? AnimeFeedBrowseSource
        if (src == null) {
            mutableState.update { it.copy(isLoading = false, canLoadMore = false) }
            return
        }
        loading = true
        screenModelScope.launch(ioDispatcher) {
            val current = state.value
            val cursor = if (current.cursorMode) current.nextCursor else null
            val result = runCatching { src.getBrowseCategories(current.nextPage, cursor) }
            loading = false
            mutableState.update { state ->
                val page = result.getOrNull()
                if (page == null) {
                    state.copy(
                        isLoading = false,
                        canLoadMore = false,
                        error = result.exceptionOrNull()?.localizedMessage,
                    )
                } else {
                    state.copy(
                        isLoading = false,
                        error = null,
                        categories = (state.categories + page.categories).toImmutableList(),
                        canLoadMore = page.hasNextPage,
                        nextPage = state.nextPage + 1,
                        nextCursor = page.nextCursor,
                        cursorMode = state.cursorMode || page.nextCursor != null,
                    )
                }
            }
        }
    }
}
