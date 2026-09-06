package eu.kanade.tachiyomi.ui.browse

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.paging.PagingSource
import cafe.adriel.voyager.core.model.rememberScreenModel
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.ui.browse.search.SavedSearchFilterSerializer
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreenModel
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreenModel
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreenModel
import kotlinx.serialization.json.jsonArray
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.source.anime.interactor.GetRemoteAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.interactor.GetRemoteManga
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.interactor.GetRemoteNovel
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Which title flavour a carousel page hosts.
 *
 * An enum on purpose: the screen is a Voyager [Screen] (java.io.Serializable) and is serialized
 * into the saved navigation state whenever the activity stops. Enum constants survive Java
 * serialization natively; the previous sealed interface with `data object`s did not implement
 * Serializable, so stopping the activity with the carousel open crashed with
 * `NotSerializableException: TitleCarouselType$Novel` inside `Parcel.writeSerializable`.
 */
internal enum class TitleCarouselType {
    Manga,
    Anime,
    Novel,
}

/**
 * Swipeable carousel over the titles of a source listing.
 *
 * Replaces the plain title screen when a title is opened from a source browser: horizontal swipes
 * move to the neighbouring titles of the listing the user came from. The carousel starts with the
 * snapshot of titles the browser had in memory and keeps fetching further pages of the listing on
 * demand; swiping back only walks the already loaded titles.
 *
 * Title screen models are hosted here (not inside the pager pages) and registered with the Voyager
 * ScreenModelStore per title (tag = titleId), so flipping between nearby titles never reloads
 * their chapter lists and everything is disposed when the carousel leaves the stack (BFEED-14).
 */
internal class TitleCarouselScreen(
    private val type: TitleCarouselType,
    private val sourceId: Long,
    private val initialTitleIds: List<Long>,
    private val initialIndex: Int,
    private val listingQuery: String?,
    private val filtersJson: String? = null,
    // CONTROL-7: was a bare Voyager Screen (key = class name) - two carousels in the stack
    // shared one ScreenModelStore holder key; presentation.util.Screen gives a uniqueScreenKey.
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val haptic = LocalHapticFeedback.current
        // BFEED-15: titleIds/paging progress survive configuration changes - previously the
        // list reset to initialTitleIds while rememberPagerState restored currentPage: the
        // restore effect then loaded page 1, every id was already known, appended=false set
        // endReached and the carousel stayed permanently truncated after a rotation.
        val titleIds = rememberSaveable(
            saver = listSaver(
                save = { it.toList() },
                restore = { it.toMutableStateList() },
            ),
        ) {
            mutableStateListOf<Long>().apply { addAll(initialTitleIds) }
        }
        val pagerState = rememberPagerState(
            initialPage = initialIndex.coerceIn(0, titleIds.size.coerceAtLeast(1) - 1),
        ) { titleIds.size.coerceAtLeast(1) }
        var savedNextPageKey by rememberSaveable { mutableStateOf(1L) }
        var savedExhausted by rememberSaveable { mutableStateOf(false) }
        val loader = remember(type, sourceId, listingQuery, filtersJson) {
            TitleCarouselListingLoader(type, sourceId, listingQuery, filtersJson).apply {
                nextPageKey = savedNextPageKey
                exhausted = savedExhausted
            }
        }
        var endReached by rememberSaveable { mutableStateOf(false) }
        var loadInFlight by remember { mutableStateOf(false) }

        // Fetch the next listing page when the reader approaches the end of the loaded window.
        // BFEED-16: keyed on loadInFlight/size too - a swipe DURING a load early-returned and
        // the effect never re-ran afterwards (key unchanged), stalling pagination until the
        // next page change; BRN-18: loadInFlight reset in finally (an exception left it true).
        LaunchedEffect(pagerState.currentPage, loadInFlight, titleIds.size) {
            if (endReached || loadInFlight) return@LaunchedEffect
            if (pagerState.currentPage >= titleIds.size - 3) {
                loadInFlight = true
                try {
                    val appended = loader.loadNextPage(titleIds)
                    savedNextPageKey = loader.nextPageKey
                    savedExhausted = loader.exhausted
                    if (!appended) {
                        endReached = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                } finally {
                    loadInFlight = false
                }
            }
        }

        // Boundary feedback: reaching the first title, or the last one of a finished listing.
        LaunchedEffect(pagerState.currentPage, endReached) {
            val atStart = pagerState.currentPage == 0
            val atEnd = endReached && pagerState.currentPage == titleIds.size - 1
            if (atStart || atEnd) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val titleId = titleIds.getOrNull(page) ?: return@HorizontalPager
            val isVisible = page == pagerState.currentPage || page == pagerState.targetPage
            if (!isVisible) return@HorizontalPager
            // BFEED-14 (РЕШ-B14): title screen models are registered with the Voyager
            // ScreenModelStore via rememberScreenModel(tag=titleId) instead of the manual LRU
            // cache. The cache bypassed the Store: evictions never disposed anything, the
            // unregistered SMs' screenModelScope dependencies landed under ScreenModelStore's
            // FOREIGN-key fallback (shared with - and cancellable by - unrelated screens, and
            // kept in the dependencies map forever). The Store now owns the lifecycle; all
            // tagged models are disposed when the carousel is popped. (The onDispose-on-evict
            // variant was rejected by control: ScreenModel.onDispose is a no-op by default and a
            // manual scope cancel could kill a foreign screen's scope.)
            when (type) {
                TitleCarouselType.Manga -> {
                    val model = rememberScreenModel(tag = titleId.toString()) {
                        MangaScreenModel(context, lifecycleOwner.lifecycle, titleId, isFromSource = true)
                    }
                    val screen = remember(titleId, model) {
                        MangaScreen(
                            mangaId = titleId,
                            fromSource = true,
                            externalScreenModel = model,
                        )
                    }
                    screen.Content()
                }
                TitleCarouselType.Anime -> {
                    val model = rememberScreenModel(tag = titleId.toString()) {
                        AnimeScreenModel(context, lifecycleOwner.lifecycle, titleId, isFromSource = true)
                    }
                    val screen = remember(titleId, model) {
                        AnimeScreen(
                            animeId = titleId,
                            fromSource = true,
                            externalScreenModel = model,
                        )
                    }
                    screen.Content()
                }
                TitleCarouselType.Novel -> {
                    val model = rememberScreenModel(tag = titleId.toString()) {
                        NovelScreenModel(lifecycleOwner.lifecycle, titleId)
                    }
                    val screen = remember(titleId, model) {
                        NovelScreen(
                            novelId = titleId,
                            fromSource = true,
                            externalScreenModel = model,
                        )
                    }
                    screen.Content()
                }
            }
        }
    }
}

/**
 * Page-by-page loader over the same source listing the browser showed.
 *
 * Reuses the paging sources of the browse screen models ([GetRemoteNovel]/[GetRemoteManga]/
 * [GetRemoteAnime]) so the carousel continuation matches the listing (popular/latest/search with
 * the same filters) the user was looking at.
 */
private class TitleCarouselListingLoader(
    private val type: TitleCarouselType,
    private val sourceId: Long,
    private val listingQuery: String?,
    private val filtersJson: String?,
) {
    // BFEED-15: restored from / synced back to rememberSaveable state across config changes.
    var nextPageKey: Long = 1L
    var exhausted = false

    suspend fun loadNextPage(knownIds: MutableList<Long>): Boolean {
        if (exhausted) return false
        val result = runCatching {
            pagingSource().load(
                PagingSource.LoadParams.Append(
                    key = nextPageKey,
                    loadSize = PAGE_SIZE,
                    placeholdersEnabled = false,
                ),
            )
        }.getOrNull()
        val page = result as? PagingSource.LoadResult.Page<*, *> ?: run {
            exhausted = true
            return false
        }
        nextPageKey = (page.nextKey as? Long) ?: 0L
        if (nextPageKey <= 0L) exhausted = true
        val ids = pageIds(page.data)
        if (ids.isEmpty()) {
            exhausted = true
            return false
        }
        val known = knownIds.toHashSet()
        var appended = false
        for (id in ids) {
            if (known.add(id)) {
                knownIds.add(id)
                appended = true
            }
        }
        return appended
    }

    private fun pageIds(data: List<*>): List<Long> = when (type) {
        TitleCarouselType.Novel -> data.filterIsInstance<Novel>().map { it.id }
        TitleCarouselType.Manga -> data.filterIsInstance<Manga>().map { it.id }
        TitleCarouselType.Anime -> data.filterIsInstance<Anime>().map { it.id }
    }

    private suspend fun pagingSource() = when (type) {
        TitleCarouselType.Novel -> Injekt.get<GetRemoteNovel>().subscribe(
            sourceId,
            listingQuery.orEmpty(),
            novelFilters(),
        )
        TitleCarouselType.Manga -> Injekt.get<GetRemoteManga>().subscribe(
            sourceId,
            listingQuery.orEmpty(),
            mangaFilters(),
        )
        TitleCarouselType.Anime -> Injekt.get<GetRemoteAnime>().subscribe(
            sourceId,
            listingQuery.orEmpty(),
            animeFilters(),
        )
    }

    private suspend fun novelFilters(): NovelFilterList {
        val json = filtersJson ?: return NovelFilterList()
        // BRN-6: deserialized into an EMPTY NovelFilterList - iterating an empty list makes the
        // restore a total no-op, so anime/novel carousel continuations requested an UNFILTERED
        // listing diverging from the browse screen they continue. Load the source's base list
        // first (the manga path below is the etalon).
        val source = runCatching {
            Injekt.get<NovelSourceManager>().getOrStub(sourceId) as? NovelCatalogueSource
        }.getOrNull() ?: return NovelFilterList()
        val base = runCatching { source.getFilterList() }.getOrElse { NovelFilterList() }
        runCatching { SavedSearchFilterSerializer.deserialize(json, base) }
        return base
    }

    private suspend fun animeFilters(): AnimeFilterList {
        val json = filtersJson ?: return AnimeFilterList()
        // BRN-6: see novelFilters.
        val source = runCatching {
            Injekt.get<AnimeSourceManager>().getOrStub(sourceId) as? AnimeCatalogueSource
        }.getOrNull() ?: return AnimeFilterList()
        val base = runCatching { source.getFilterList() }.getOrElse { AnimeFilterList() }
        runCatching { SavedSearchFilterSerializer.deserialize(json, base) }
        return base
    }

    private suspend fun mangaFilters(): FilterList {
        val json = filtersJson ?: return FilterList()
        val source = runCatching {
            Injekt.get<MangaSourceManager>().getOrStub(sourceId) as? CatalogueSource
        }.getOrNull() ?: return FilterList()
        val base = runCatching { source.getFilterList() }.getOrElse { FilterList() }
        return runCatching {
            val serializer = Injekt.get<xyz.nulldev.ts.api.http.serializer.FilterSerializer>()
            serializer.deserialize(
                base,
                kotlinx.serialization.json.Json.parseToJsonElement(json).jsonArray,
            )
            base
        }.getOrElse { base }
    }

    private companion object {
        const val PAGE_SIZE = 25
    }
}
