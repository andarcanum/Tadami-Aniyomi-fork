package eu.kanade.tachiyomi.ui.browse.novel.source.browse

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.filter
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.asState
import eu.kanade.domain.entries.novel.model.toDomainNovel
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.SNovel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.interactor.GetNovelByUrlAndSourceId
import tachiyomi.domain.source.novel.interactor.GetRemoteNovel
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BrowseNovelSourceScreenModel(
    private val sourceId: Long,
    listingQuery: String?,
    sourceManager: NovelSourceManager = Injekt.get(),
    getRemoteNovel: GetRemoteNovel = Injekt.get(),
    sourcePreferences: eu.kanade.domain.source.service.SourcePreferences = Injekt.get(),
    private val getNovelByUrlAndSourceId: GetNovelByUrlAndSourceId = Injekt.get(),
    private val networkToLocalNovel: NetworkToLocalNovel = Injekt.get(),
) : StateScreenModel<BrowseNovelSourceScreenModel.State>(State(Listing.valueOf(listingQuery))) {

    var displayMode by sourcePreferences.sourceDisplayMode().asState(screenModelScope)

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
                val loadedFilters = runCatching {
                    withContext(ioCoroutineScope.coroutineContext) {
                        source.getFilterList()
                    }
                }.getOrElse { NovelFilterList() }

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
    }

    private val hideInLibraryItems = sourcePreferences.hideInNovelLibraryItems().get()

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
                    pagingData.filter { novel ->
                        !hideInLibraryItems ||
                            getNovelByUrlAndSourceId.await(novel.url, sourceId)?.favorite != true
                    }
                }
                .cachedIn(ioCoroutineScope)
        }
        .stateIn(ioCoroutineScope, SharingStarted.Lazily, emptyFlow())

    fun resetFilters() {
        if (source !is NovelCatalogueSource) return

        screenModelScope.launch {
            val resetFilters = runCatching {
                withContext(ioCoroutineScope.coroutineContext) {
                    source.getFilterList()
                }
            }.getOrElse { NovelFilterList() }

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

        mutableState.update {
            it.copy(
                filters = filters,
            )
        }
    }

    fun search(query: String? = null, filters: NovelFilterList? = null) {
        if (source !is NovelCatalogueSource) return

        val currentState = state.value
        val updatedFilters = filters ?: currentState.filters
        val input = currentState.listing as? Listing.Search
            ?: Listing.Search(query = null, filters = updatedFilters)

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

    suspend fun openNovel(novel: SNovel): Long {
        val localNovel = networkToLocalNovel.await(novel.toDomainNovel(source.id))
        return localNovel.id
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
    }

    @Immutable
    data class State(
        val listing: Listing,
        val filters: NovelFilterList = NovelFilterList(),
        val toolbarQuery: String? = null,
        val dialog: Dialog? = null,
        val filterVersion: Int = 0,
    ) {
        val isUserQuery get() = listing is Listing.Search && !listing.query.isNullOrEmpty()
    }

    private data class PagingRequest(
        val query: String,
        val isSearch: Boolean,
        val filterVersion: Int,
        val filters: NovelFilterList,
    )
}
