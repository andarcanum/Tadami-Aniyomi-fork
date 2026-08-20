package eu.kanade.tachiyomi.ui.browse.anime.feed

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
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
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.model.FeedListingType
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.model.SourceType
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

typealias AnimeFeedScreenState = FeedScreenState<AnimeFeedItemUI>

data class AnimeFeedItemUI(
    override val feed: FeedSavedSearch,
    val savedSearch: SavedSearch?,
    val source: AnimeCatalogueSource,
    val title: String,
    val subtitle: String,
    override val results: List<Anime>?,
) : BaseFeedScreenModel.FeedItemUi

class AnimeFeedScreenModel(
    private val context: Context,
    override val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
) : BaseFeedScreenModel<AnimeFeedScreenState, AnimeFeedItemUI>(
    initialState = AnimeFeedScreenState(),
    sourcePreferences = sourcePreferences,
) {

    override val sourceType = SourceType.ANIME

    init {
        startFeedSubscription()
    }

    override suspend fun awaitSourcesInitialized() {
        sourceManager.isInitialized.first { it }
    }

    override fun itemsOf(state: AnimeFeedScreenState): List<AnimeFeedItemUI>? = state.items

    override fun withItems(state: AnimeFeedScreenState, items: List<AnimeFeedItemUI>?): AnimeFeedScreenState =
        state.copy(items = items)

    override fun isReorderingOf(state: AnimeFeedScreenState): Boolean = state.isReordering

    override fun withReordering(state: AnimeFeedScreenState, reordering: Boolean): AnimeFeedScreenState =
        state.copy(isReordering = reordering)

    override fun withDialog(
        state: AnimeFeedScreenState,
        dialog: BaseFeedScreenModel.FeedDialog?,
    ): AnimeFeedScreenState = state.copy(dialog = dialog)

    override suspend fun resolveFeedItems(entries: List<FeedSavedSearch>): List<AnimeFeedItemUI> {
        val latestLabel = context.stringResource(AYMR.strings.feed_latest)
        val popularLabel = context.stringResource(AYMR.strings.feed_popular)
        return entries.mapNotNull { feed ->
            val source = sourceManager.get(feed.source) as? AnimeCatalogueSource ?: return@mapNotNull null
            val savedSearchId = feed.savedSearch
            val savedSearch = if (feed.listingType == FeedListingType.SAVED_SEARCH && savedSearchId != null) {
                getSavedSearchById.await(savedSearchId)
            } else {
                null
            }
            AnimeFeedItemUI(
                feed = feed,
                savedSearch = savedSearch,
                source = source,
                title = source.name,
                subtitle = buildAnimeFeedSubtitle(
                    language = LocaleHelper.getLocalizedDisplayName(source.lang),
                    listingType = feed.listingType,
                    savedSearchName = savedSearch?.name,
                    latestLabel = latestLabel,
                    popularLabel = popularLabel,
                ),
                results = null,
            )
        }
    }

    override fun loadFeed(items: List<AnimeFeedItemUI>) {
        val hideInLibrary = sourcePreferences.hideInLibraryFeedItems().get()
        ioCoroutineScope.launch {
            val results = items.map { itemUI ->
                async {
                    try {
                        val animes = when (itemUI.feed.listingType) {
                            FeedListingType.SAVED_SEARCH -> {
                                val feed = itemUI.feed
                                val ss = itemUI.savedSearch
                                    ?: feed.savedSearch?.let { getSavedSearchById.await(it) }
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
                            }
                            FeedListingType.LATEST -> itemUI.source.getLatestUpdates(1).animes
                            FeedListingType.POPULAR -> itemUI.source.getPopularAnime(1).animes
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

    override fun clearResults(items: List<AnimeFeedItemUI>): List<AnimeFeedItemUI> =
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

    override fun disabledSourceIds(): Set<String> = sourcePreferences.disabledAnimeSources().get()

    override fun candidateFor(id: Long): BaseFeedScreenModel.FeedSourceCandidate? =
        (sourceManager.get(id) as? AnimeCatalogueSource)?.let {
            BaseFeedScreenModel.FeedSourceCandidate(
                id = it.id,
                lang = it.lang,
                name = it.name,
                supportsLatest = it.supportsLatest,
            )
        }

    @Composable
    fun getAnime(initialAnime: Anime): State<Anime> {
        return produceState(initialValue = initialAnime) {
            getAnime.subscribe(initialAnime.url, initialAnime.source)
                .filterNotNull()
                .collectLatest { anime -> value = anime }
        }
    }
}
