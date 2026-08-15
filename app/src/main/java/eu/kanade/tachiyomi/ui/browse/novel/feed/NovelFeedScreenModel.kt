package eu.kanade.tachiyomi.ui.browse.novel.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import eu.kanade.domain.entries.novel.model.toDomainNovel
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.ui.browse.feed.BaseFeedScreenModel
import eu.kanade.tachiyomi.ui.browse.feed.FeedScreenState
import eu.kanade.tachiyomi.ui.browse.search.SavedSearchFilterSerializer
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.source.model.FeedListingType
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SourceType
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

typealias NovelFeedScreenState = FeedScreenState<NovelFeedItemUI>

data class NovelFeedItemUI(
    override val feed: FeedSavedSearch,
    val source: NovelCatalogueSource,
    val title: String,
    val subtitle: String,
    override val results: List<Novel>?,
) : BaseFeedScreenModel.FeedItemUi

class NovelFeedScreenModel(
    override val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val networkToLocalNovel: NetworkToLocalNovel = Injekt.get(),
    private val getNovel: GetNovel = Injekt.get(),
) : BaseFeedScreenModel<NovelFeedScreenState, NovelFeedItemUI>(
    initialState = NovelFeedScreenState(),
    sourcePreferences = sourcePreferences,
) {

    override val sourceType = SourceType.NOVEL

    init {
        startFeedSubscription()
    }

    override suspend fun awaitSourcesInitialized() {
        sourceManager.isInitialized.first { it }
    }

    override fun itemsOf(state: NovelFeedScreenState): List<NovelFeedItemUI>? = state.items

    override fun withItems(state: NovelFeedScreenState, items: List<NovelFeedItemUI>?): NovelFeedScreenState =
        state.copy(items = items)

    override fun isReorderingOf(state: NovelFeedScreenState): Boolean = state.isReordering

    override fun withReordering(state: NovelFeedScreenState, reordering: Boolean): NovelFeedScreenState =
        state.copy(isReordering = reordering)

    override fun withDialog(
        state: NovelFeedScreenState,
        dialog: BaseFeedScreenModel.FeedDialog?,
    ): NovelFeedScreenState = state.copy(dialog = dialog)

    override suspend fun resolveFeedItems(entries: List<FeedSavedSearch>): List<NovelFeedItemUI> {
        return entries.mapNotNull { feed ->
            val source = sourceManager.get(feed.source) as? NovelCatalogueSource ?: return@mapNotNull null
            NovelFeedItemUI(
                feed = feed,
                source = source,
                title = source.name,
                subtitle = LocaleHelper.getLocalizedDisplayName(source.lang),
                results = null,
            )
        }
    }

    override fun loadFeed(items: List<NovelFeedItemUI>) {
        val hideInLibrary = sourcePreferences.hideInLibraryFeedItems().get()
        ioCoroutineScope.launch {
            val results = items.map { itemUI ->
                async {
                    try {
                        val savedSearchId = itemUI.feed.savedSearch
                        val novels = if (savedSearchId != null) {
                            // Legacy feeds stored a saved search without an explicit listing type.
                            val ss = getSavedSearchById.await(savedSearchId)
                            if (ss != null) {
                                val filtersJson = ss.filtersJson
                                val baseFilters = itemUI.source.getFilterList()
                                if (filtersJson != null) {
                                    SavedSearchFilterSerializer.deserialize(filtersJson, baseFilters)
                                }
                                itemUI.source.getSearchNovels(1, ss.query ?: "", baseFilters).novels
                            } else {
                                itemUI.source.getLatestUpdates(1).novels
                            }
                        } else {
                            when (itemUI.feed.listingType) {
                                FeedListingType.LATEST -> {
                                    if (itemUI.source.supportsLatest) {
                                        itemUI.source.getLatestUpdates(1).novels
                                    } else {
                                        itemUI.source.getPopularNovels(1).novels
                                    }
                                }
                                FeedListingType.POPULAR -> itemUI.source.getPopularNovels(1).novels
                                FeedListingType.SAVED_SEARCH -> itemUI.source.getLatestUpdates(1).novels
                            }
                        }
                        val converted = novels.map { snovel ->
                            networkToLocalNovel.await(snovel.toDomainNovel(itemUI.source.id))
                        }.filter { !hideInLibrary || !it.favorite }
                        itemUI to converted
                    } catch (_: Exception) {
                        itemUI to emptyList<Novel>()
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

    override fun clearResults(items: List<NovelFeedItemUI>): List<NovelFeedItemUI> =
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

    override fun disabledSourceIds(): Set<String> = sourcePreferences.disabledNovelSources().get()

    override fun candidateFor(id: Long): BaseFeedScreenModel.FeedSourceCandidate? =
        (sourceManager.get(id) as? NovelCatalogueSource)?.let {
            BaseFeedScreenModel.FeedSourceCandidate(
                id = it.id,
                lang = it.lang,
                name = it.name,
                supportsLatest = it.supportsLatest,
            )
        }

    @Composable
    fun getNovel(initialNovel: Novel): State<Novel> {
        return produceState(initialValue = initialNovel) {
            getNovel.subscribe(initialNovel.url, initialNovel.source)
                .filterNotNull()
                .collectLatest { novel -> value = novel }
        }
    }
}
