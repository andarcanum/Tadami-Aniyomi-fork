package eu.kanade.tachiyomi.ui.browse.novel.source.browse

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.asState
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.presentation.util.ioCoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import eu.kanade.domain.entries.novel.model.toDomainNovel
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
                    listing = Listing.Search(query, source.getFilterList())
                }

                it.copy(
                    listing = listing,
                    filters = source.getFilterList(),
                    toolbarQuery = query,
                )
            }
        }

        sourcePreferences.lastUsedNovelSource().set(source.id)
    }

    val novelPagerFlowFlow = state.map { it.listing }
        .distinctUntilChanged()
        .map { listing ->
            Pager(PagingConfig(pageSize = 25)) {
                getRemoteNovel.subscribe(sourceId, listing.query ?: "", listing.filters)
            }.flow
                .cachedIn(ioCoroutineScope)
        }
        .stateIn(ioCoroutineScope, SharingStarted.Lazily, emptyFlow())

    fun resetFilters() {
        if (source !is NovelCatalogueSource) return

        mutableState.update { it.copy(filters = source.getFilterList()) }
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

        val input = state.value.listing as? Listing.Search
            ?: Listing.Search(query = null, filters = source.getFilterList())

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
    ) {
        val isUserQuery get() = listing is Listing.Search && !listing.query.isNullOrEmpty()
    }
}
