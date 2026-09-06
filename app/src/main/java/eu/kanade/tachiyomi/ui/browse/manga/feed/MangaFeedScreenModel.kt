package eu.kanade.tachiyomi.ui.browse.manga.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import eu.kanade.domain.entries.manga.model.toDomainManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.browse.feed.BaseFeedScreenModel
import eu.kanade.tachiyomi.ui.browse.feed.FeedScreenState
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.model.FeedListingType
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SourceType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer
import kotlin.coroutines.cancellation.CancellationException

typealias MangaFeedScreenState = FeedScreenState<MangaFeedItemUI>

data class MangaFeedItemUI(
    override val feed: FeedSavedSearch,
    val source: CatalogueSource,
    val title: String,
    val subtitle: String,
    override val results: List<Manga>?,
    override val loadError: String? = null,
) : BaseFeedScreenModel.FeedItemUi

class MangaFeedScreenModel(
    override val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: MangaSourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val filterSerializer: FilterSerializer = Injekt.get(),
) : BaseFeedScreenModel<MangaFeedScreenState, MangaFeedItemUI>(
    initialState = MangaFeedScreenState(),
    sourcePreferences = sourcePreferences,
) {

    override val sourceType = SourceType.MANGA

    init {
        startFeedSubscription()
    }

    override suspend fun awaitSourcesInitialized() {
        sourceManager.isInitialized.first { it }
    }

    override fun itemsOf(state: MangaFeedScreenState): List<MangaFeedItemUI>? = state.items

    override fun withItems(state: MangaFeedScreenState, items: List<MangaFeedItemUI>?): MangaFeedScreenState =
        state.copy(items = items)

    override fun isReorderingOf(state: MangaFeedScreenState): Boolean = state.isReordering

    override fun withReordering(state: MangaFeedScreenState, reordering: Boolean): MangaFeedScreenState =
        state.copy(isReordering = reordering)

    override fun withDialog(
        state: MangaFeedScreenState,
        dialog: BaseFeedScreenModel.FeedDialog?,
    ): MangaFeedScreenState = state.copy(dialog = dialog)

    override suspend fun resolveFeedItems(entries: List<FeedSavedSearch>): List<MangaFeedItemUI> {
        return entries.mapNotNull { feed ->
            val source = sourceManager.get(feed.source) as? CatalogueSource ?: return@mapNotNull null
            MangaFeedItemUI(
                feed = feed,
                source = source,
                title = source.name,
                subtitle = LocaleHelper.getLocalizedDisplayName(source.lang),
                results = null,
            )
        }
    }

    // BFEED-2: suspend + runs inside the base SM's cancellable load job (was a detached
    // ioCoroutineScope.launch per call - concurrent loads raced, stale ones overwrote fresh).
    override suspend fun loadFeed(items: List<MangaFeedItemUI>) = withContext(ioCoroutineScope.coroutineContext) {
        val hideInLibrary = sourcePreferences.hideInLibraryFeedItems().get()
        val results = items.map { itemUI ->
            async {
                // BFEED-5: cancellation is rethrown (the catch-all Exception swallowed it -
                // CancellationException IS an Exception - breaking load-job cancellation), and a
                // source failure is recorded as loadError instead of an empty list that the UI
                // could not distinguish from "no results".
                val loaded = try {
                    val savedSearchId = itemUI.feed.savedSearch
                    val mangas = if (savedSearchId != null) {
                        // Legacy feeds stored a saved search without an explicit listing type.
                        val ss = getSavedSearchById.await(savedSearchId)
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
                    } else {
                        when (itemUI.feed.listingType) {
                            FeedListingType.LATEST -> {
                                if (itemUI.source.supportsLatest) {
                                    itemUI.source.getLatestUpdates(1).mangas
                                } else {
                                    itemUI.source.getPopularManga(1).mangas
                                }
                            }
                            FeedListingType.POPULAR -> itemUI.source.getPopularManga(1).mangas
                            FeedListingType.SAVED_SEARCH -> itemUI.source.getLatestUpdates(1).mangas
                        }
                    }
                    val converted = mangas.map { smanga ->
                        networkToLocalManga.await(smanga.toDomainManga(itemUI.source.id))
                    }.filter { !hideInLibrary || !it.favorite }
                    Result.success(converted)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure<List<Manga>>(e)
                }
                itemUI to loaded
            }
        }.awaitAll()
        mutableState.update { state ->
            val updatedItems = state.items?.map { item ->
                // BFEED-4: match by feed.id - source.id matching cross-wired two rows of the
                // same source (both got the first row's results).
                val pair = results.find { it.first.feed.id == item.feed.id }
                if (pair != null) {
                    pair.second.fold(
                        onSuccess = { item.copy(results = it, loadError = null) },
                        onFailure = {
                            item.copy(results = emptyList(), loadError = it.message ?: it.javaClass.simpleName)
                        },
                    )
                } else {
                    item
                }
            }
            state.copy(items = updatedItems)
        }
    }

    override fun clearResults(items: List<MangaFeedItemUI>): List<MangaFeedItemUI> =
        items.map { it.copy(results = null) }

    override fun allSourceCandidates(): List<BaseFeedScreenModel.FeedSourceCandidate> =
        sourceManager.getCatalogueSources().map {
            BaseFeedScreenModel.FeedSourceCandidate(
                id = it.id,
                lang = it.lang,
                name = it.name,
                supportsLatest = it.supportsLatest,
            )
        }

    override fun disabledSourceIds(): Set<String> = sourcePreferences.disabledMangaSources().get()

    override fun candidateFor(id: Long): BaseFeedScreenModel.FeedSourceCandidate? =
        (sourceManager.get(id) as? CatalogueSource)?.let {
            BaseFeedScreenModel.FeedSourceCandidate(
                id = it.id,
                lang = it.lang,
                name = it.name,
                supportsLatest = it.supportsLatest,
            )
        }

    @Composable
    fun getManga(initialManga: Manga): State<Manga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collectLatest { manga -> value = manga }
        }
    }
}
