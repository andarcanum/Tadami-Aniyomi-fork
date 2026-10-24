package eu.kanade.tachiyomi.ui.reels

import androidx.compose.runtime.Immutable
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
 * Albums collection library of one source (device feature, sign-off prototype): the saved
 * albums (local storage, no network) plus the source's category catalog for adding new ones
 * (AnimeFeedBrowseSource.getBrowseCategories — the same data the niches screen shows).
 */
class ReelsAlbumsScreenModel(
    val sourceId: Long,
    private val albumRepository: ReelsAlbumRepository = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StateScreenModel<ReelsAlbumsScreenModel.State>(State()) {

    @Immutable
    data class State(
        val albums: ImmutableList<ReelsAlbum> = persistentListOf(),
        val catalog: ImmutableList<FeedCategory> = persistentListOf(),
        val isCatalogLoading: Boolean = false,
        // Append attempt while the catalog is already on screen keeps the list visible.
        val isCatalogPageLoading: Boolean = false,
        val catalogError: String? = null,
        // Append failure: the list stays usable and the footer offers a retry.
        val catalogPageError: String? = null,
        val catalogCanLoadMore: Boolean = true,
        val catalogNextPage: Int = 1,
        val catalogNextCursor: String? = null,
        val catalogCursorMode: Boolean = false,
    )

    // Same generation guard as the other Reels surfaces: a reset supersedes an in-flight
    // append, so a stale page can neither corrupt the cursor nor duplicate rows.
    private val catalogGeneration = AtomicInteger(0)

    @Volatile
    private var catalogLoading = false

    init {
        screenModelScope.launch {
            albumRepository.subscribeBySource(sourceId).collectLatest { albums ->
                mutableState.update { it.copy(albums = albums.toImmutableList()) }
            }
        }
        loadCatalog()
    }

    /** Resets and loads page 1 of the source's category directory for the add-sheet. */
    fun loadCatalog() {
        catalogGeneration.incrementAndGet()
        catalogLoading = false
        mutableState.update {
            it.copy(
                catalog = persistentListOf(),
                isCatalogLoading = true,
                isCatalogPageLoading = false,
                catalogError = null,
                catalogPageError = null,
                catalogCanLoadMore = true,
                catalogNextPage = 1,
                catalogNextCursor = null,
                catalogCursorMode = false,
            )
        }
        loadCatalogPage()
    }

    /** Appends the next catalog page; driven by the add-sheet footer when it becomes visible. */
    fun loadCatalogMore() {
        if (catalogLoading || !state.value.catalogCanLoadMore) return
        loadCatalogPage()
    }

    private fun loadCatalogPage() {
        if (catalogLoading) return
        val browse = sourceManager.get(sourceId) as? AnimeFeedBrowseSource
        if (browse == null) {
            mutableState.update {
                if (it.catalog.isEmpty()) {
                    it.copy(isCatalogLoading = false, catalogError = "Source unavailable", catalogCanLoadMore = false)
                } else {
                    it.copy(isCatalogPageLoading = false, catalogPageError = "Source unavailable")
                }
            }
            return
        }
        // Snapshot the pagination inputs on the calling (main) thread.
        val snapshot = state.value
        val page = snapshot.catalogNextPage
        val cursor = if (snapshot.catalogCursorMode) snapshot.catalogNextCursor else null
        catalogLoading = true
        val generation = catalogGeneration.incrementAndGet()
        if (snapshot.catalog.isNotEmpty()) {
            mutableState.update { it.copy(isCatalogPageLoading = true, catalogPageError = null) }
        }
        screenModelScope.launch(ioDispatcher) {
            val result = runCatching { browse.getBrowseCategories(page, cursor) }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            if (catalogGeneration.get() == generation) catalogLoading = false
            mutableState.update { state ->
                if (catalogGeneration.get() != generation) return@update state
                val pageData = result.getOrNull()
                if (pageData == null) {
                    val message = result.exceptionOrNull()?.localizedMessage.orEmpty()
                    logcat(LogPriority.ERROR, throwable = result.exceptionOrNull()) {
                        "Failed to load albums catalog of $sourceId"
                    }
                    if (state.catalog.isEmpty()) {
                        state.copy(
                            isCatalogLoading = false,
                            catalogError = message,
                            catalogCanLoadMore = false,
                        )
                    } else {
                        state.copy(
                            isCatalogPageLoading = false,
                            catalogPageError = message,
                            // Keep pagination armed: the footer retry resumes from the same cursor.
                            catalogCanLoadMore = true,
                        )
                    }
                } else {
                    val step = ReelsPagination(
                        nextPage = state.catalogNextPage,
                        nextCursor = state.catalogNextCursor,
                        cursorMode = state.catalogCursorMode,
                        canLoadMore = state.catalogCanLoadMore,
                    ).advance(pageData.hasNextPage, pageData.nextCursor)
                    if (step.cursorLost) {
                        logcat(LogPriority.WARN) {
                            "Albums catalog of $sourceId dropped its cursor mid-stream; stopping pagination (contract v17)."
                        }
                    }
                    state.copy(
                        // Duplicate ids would collide in the add-sheet's lazy key space.
                        catalog = (state.catalog + pageData.categories).distinctBy { it.id }.toImmutableList(),
                        isCatalogPageLoading = false,
                        catalogError = null,
                        catalogPageError = null,
                        catalogCanLoadMore = step.pagination.canLoadMore,
                        catalogNextPage = step.pagination.nextPage,
                        catalogNextCursor = step.pagination.nextCursor,
                        catalogCursorMode = step.pagination.cursorMode,
                    )
                }
            }
        }
    }

    fun addAlbum(category: FeedCategory) {
        screenModelScope.launch(ioDispatcher) {
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

    fun removeAlbum(album: ReelsAlbum) {
        screenModelScope.launch(ioDispatcher) {
            albumRepository.delete(sourceId, album.albumId)
        }
    }
}
