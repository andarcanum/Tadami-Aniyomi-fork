package eu.kanade.tachiyomi.ui.reels

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.reels.anime.model.ReelsFavorite
import tachiyomi.domain.reels.anime.repository.ReelsFavoriteRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

/**
 * Session-scoped "user has not decided sound yet" flag. Process-wide by default: every
 * fresh app launch starts the feed muted (Play-policy friendly), and once the user toggles
 * sound ON/OFF the persisted preference applies for the rest of the session. Injectable so
 * tests get a hermetic instance.
 */
class ReelsSessionSoundState {
    var decided = false
}

class ReelsFeedScreenModel(
    val initialSourceId: Long,
    // Non-empty when opened as an offline playlist (e.g. from the Favorites screen).
    private val initialFavorites: List<ReelsFavorite> = emptyList(),
    private val initialPage: Int = 0,
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    // Injectable for tests; resolves the real incognito preference by default.
    private val isIncognito: () -> Boolean = {
        Injekt.get<eu.kanade.domain.base.BasePreferences>().incognitoMode().get()
    },
    // Injectable for tests; resolves extension icons by default.
    private val sourceIconProvider: (Long) -> ImageBitmap? = { sourceId ->
        Injekt.get<eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager>()
            .getAppIconForSource(sourceId)
            ?.toBitmap()
            ?.asImageBitmap()
    },
    private val reelsFavoriteRepository: ReelsFavoriteRepository = Injekt.get(),
    private val sessionSound: ReelsSessionSoundState = sharedSessionSound,
) : StateScreenModel<ReelsFeedScreenModel.State>(
    State(
        currentSourceId = initialSourceId,
        isAutoAdvance = sourcePreferences.autoAdvanceReels().get(),
        isCropMode = sourcePreferences.reelsCropMode().get(),
        // Undecided session: always start muted (with the unmute hint); afterwards the
        // persisted preference wins.
        isMuted = if (sessionSound.decided) sourcePreferences.reelsMuted().get() else true,
        isHdQuality = sourcePreferences.reelsHdQuality().get(),
        dataSaverMetered = sourcePreferences.reelsDataSaverMetered().get(),
        preloadEnabled = sourcePreferences.reelsPreloadEnabled().get(),
        preloadWifiOnly = sourcePreferences.reelsPreloadWifiOnly().get(),
        showUnmuteHint = !sessionSound.decided,
    ),
) {

    companion object {
        val sharedSessionSound = ReelsSessionSoundState()
    }

    private var source: AnimeFeedSource? = null

    // Guards against a stale in-flight load completing after a reset/source switch and
    // appending old videos (or overwriting the cursor) on top of the freshly reset feed.
    private var loadJob: Job? = null

    // Monotonic token: every loadFeed() call invalidates previously running load jobs,
    // closing the window between ensureActive() and the state write (no suspension there).
    // The pagination cursor (nextPageIndex/canLoadMore) lives in State, so every cursor
    // write goes through the same generation-checked CAS update as the items — a stale job
    // can no longer corrupt the newer feed's cursor.
    private val loadGeneration = AtomicInteger(0)

    // Snapshot of the unfiltered feed, used to restore position when a search is cleared.
    // Written only from main-thread entry points (search/clearSearch/switchSource).
    private var baseItems: ImmutableList<ShortVideoItem> = persistentListOf()
    private var baseNextPageIndex = 1
    private var baseNextCursor: String? = null
    private var baseCursorMode = false
    private var baseCanLoadMore = true
    private var basePosition = 0

    // One-shot feed-position restore: snapshotted on source switch, consumed by the first
    // successful load after it, so re-entry lands on the video the user left off at.
    private var pendingRestorePosition = 0
    private var restorePositionPending = false

    // videoId -> sourceId for offline playlists, so likes persist against the right source.
    private var offlineSourceIds: Map<String, Long> = emptyMap()

    // videoIds the user liked/unliked this session (main-confined). Their DB state is already
    // authoritative, so a concurrently-loaded persisted favorites snapshot must never
    // resurrect an unlike or override a fresh like. Cleared on source switch.
    private val decidedIds = mutableSetOf<String>()

    init {
        if (initialFavorites.isNotEmpty()) {
            offlineSourceIds = initialFavorites.associate { it.videoId to it.sourceId }
            mutableState.update {
                it.copy(
                    isOffline = true,
                    items = initialFavorites.map { fav -> fav.toShortVideoItem() }.toImmutableList(),
                    likedIds = initialFavorites.map { it.videoId }.toImmutableSet(),
                    isLoading = false,
                    feedGeneration = 1,
                    targetPageIndex = initialPage,
                    canLoadMore = false,
                )
            }
        } else {
            // Collect installed feed sources (with their extension icons) for quick switching.
            // Sources disabled in Browse are excluded, consistent with the sources list.
            screenModelScope.launch(ioDispatcher) {
                combine(
                    sourceManager.sources,
                    sourcePreferences.disabledAnimeSources().changes(),
                ) { sources, disabled ->
                    sources.filterIsInstance<AnimeFeedSource>()
                        .filterNot { it.id.toString() in disabled }
                }.collectLatest { feedSources ->
                    val icons = feedSources.mapNotNull { source ->
                        sourceIconProvider(source.id)?.let { source.id to it }
                    }.toMap().toImmutableMap()
                    // The lambda is blocking (bitmap decode) and ignores cancellation; drop a
                    // superseded emission so it cannot overwrite a fresher source/icon set.
                    ensureActive()
                    mutableState.update {
                        it.copy(availableSources = feedSources.toImmutableList(), sourceIcons = icons)
                    }
                }
            }

            switchSource(initialSourceId)
        }
    }

    // Browsing history of reels (last source, queries, filters) must not be persisted
    // while the app-wide incognito mode is on.
    private inline fun persistUnlessIncognito(block: () -> Unit) {
        if (!isIncognito()) block()
    }

    fun switchSource(newSourceId: Long) {
        if (state.value.isOffline) return
        val rawSource = sourceManager.get(newSourceId)
        if (rawSource is AnimeFeedSource) {
            source = rawSource
            persistUnlessIncognito { sourcePreferences.lastUsedReelsSource().set(newSourceId) }
            baseItems = persistentListOf()
            baseNextPageIndex = 1
            baseNextCursor = null
            baseCursorMode = false
            baseCanLoadMore = true
            basePosition = 0
            decidedIds.clear()
            pendingRestorePosition = sourcePreferences.lastReelsPosition(newSourceId).get().coerceAtLeast(0)
            restorePositionPending = true
            val savedQuery = sourcePreferences.lastReelsQuery(newSourceId).get()
            val initialFilters = rawSource.getFilterList()

            // Restore saved filter values
            val savedFiltersSerialized = sourcePreferences.lastReelsFilter(newSourceId).get()
            restoreFilters(initialFilters, savedFiltersSerialized)

            mutableState.update {
                it.copy(
                    currentSourceId = newSourceId,
                    sourceName = rawSource.name,
                    supportsTags = rawSource.supportsTags,
                    searchQuery = savedQuery,
                    filters = initialFilters,
                    // Player request headers come from the (ABI-stable) AnimeHttpSource.headers —
                    // adding fields to ShortVideoItem would break linkage for extensions compiled
                    // against an older source-api.
                    sourceHeaders = (rawSource as? AnimeHttpSource)?.headers
                        ?.associate { it.first to it.second }
                        .orEmpty()
                        .toPersistentHashMap(),
                    // Likes are stored per (videoId, sourceId) in the DB; likedIds is only the
                    // current source's set. Drop the previous source's likes so a colliding
                    // videoId in the new source doesn't show a phantom heart; the new source's
                    // likes are loaded by loadPersistedFavorites below.
                    likedIds = persistentSetOf(),
                    isSourcePickerOpen = false,
                    error = null,
                )
            }
            loadPersistedFavorites(newSourceId)
            loadFeed(reset = true)
        } else {
            mutableState.update {
                it.copy(
                    error = "Source is not a video feed source",
                    isLoading = false,
                    isSourcePickerOpen = false,
                )
            }
        }
    }

    fun loadFeed(reset: Boolean = false) {
        if (state.value.isOffline) return
        val src = source ?: return
        if (!reset && state.value.isLoading) return
        if (!reset && !state.value.canLoadMore) return

        loadJob?.cancel()
        val generation = loadGeneration.incrementAndGet()

        // Set synchronously before the coroutine is dispatched: two rapid
        // loadNextPageIfNeeded() calls must not both pass the isLoading guard while the
        // first job is still queued on the IO dispatcher. The reset also clears the cursor
        // here so the stale (now-cancelled) job can never see it.
        mutableState.update { current ->
            if (reset) {
                current.copy(
                    isLoading = true,
                    error = null,
                    pageError = null,
                    nextPageIndex = 1,
                    nextCursor = null,
                    cursorMode = false,
                    canLoadMore = true,
                )
            } else {
                current.copy(isLoading = true, error = null)
            }
        }
        loadJob = screenModelScope.launch(ioDispatcher) {
            try {
                val query = state.value.searchQuery
                val filters = state.value.filters
                val page = state.value.nextPageIndex
                // Contract v17: while locked into cursor mode the token is authoritative;
                // in page-int mode the source always receives a null cursor.
                val cursor = if (state.value.cursorMode) state.value.nextCursor else null
                val pageData = if (query.isNotBlank()) {
                    src.getSearchFeed(page, cursor, query, filters)
                } else {
                    src.getFeed(page, cursor, filters)
                }
                // Re-check cancellation: the suspend calls above may have completed right
                // before this job was superseded by a reset.
                ensureActive()
                // Generation guard: a newer loadFeed() started after this job's network call
                // returned; writing now would corrupt the newer feed's cursor/items.
                if (loadGeneration.get() != generation) return@launch

                // Consume the one-shot position restore OUTSIDE the CAS: update lambdas may
                // re-run under contention and must stay side-effect-free.
                val restorePosition = if (reset && restorePositionPending) {
                    restorePositionPending = false
                    pendingRestorePosition
                } else {
                    0
                }

                mutableState.update { current ->
                    // Re-check inside the CAS: a reset may have landed between the outer guard
                    // and this update; writing a stale page would corrupt the newer feed.
                    if (loadGeneration.get() != generation) return@update current
                    val newItems = if (reset) {
                        pageData.videos
                    } else {
                        current.items + pageData.videos
                    }
                    // Sticky cursor mode (contract v17): the first non-null cursor locks the
                    // whole generation into cursor mode.
                    val newCursorMode = current.cursorMode || pageData.nextCursor != null
                    // Cursor lost mid-feed while more pages are claimed is a protocol
                    // violation: stop pagination, keep the feed usable.
                    val cursorLost = newCursorMode && pageData.nextCursor == null && pageData.hasNextPage
                    if (cursorLost) {
                        logcat(LogPriority.WARN) {
                            "Feed ${current.currentSourceId} dropped its cursor mid-feed; stopping pagination (contract v17)."
                        }
                    }
                    current.copy(
                        items = newItems.distinctBy { it.id }.toImmutableList(),
                        isLoading = false,
                        canLoadMore = pageData.hasNextPage && !cursorLost,
                        nextPageIndex = page + 1,
                        nextCursor = pageData.nextCursor,
                        cursorMode = newCursorMode,
                        feedGeneration = if (reset) current.feedGeneration + 1 else current.feedGeneration,
                        // A fresh feed starts at the saved position on source entry, at the top
                        // on search/filter resets; only the clearSearch restore path sets a
                        // non-zero targetPageIndex otherwise.
                        targetPageIndex = if (reset) restorePosition else current.targetPageIndex,
                        activeIndex = if (reset) 0 else current.activeIndex,
                        // A recovered append must not leave a stale transient error.
                        pageError = null,
                    )
                }
            } catch (e: CancellationException) {
                // Superseded by a newer reset/switch: keep whatever state the new load owns.
            } catch (t: Throwable) {
                // Throwable, not just Exception: extension bytecode can fail linkage
                // (NoSuchMethodError/AbstractMethodError on source-api drift) and an Error
                // escaping here kills the whole process.
                logcat(LogPriority.ERROR, t) { "Failed to load video feed from source ${state.value.currentSourceId}" }
                // A load that lost the generation race must not stamp its error onto the new feed.
                if (loadGeneration.get() != generation) return@launch
                mutableState.update { current ->
                    if (current.items.isEmpty()) {
                        current.copy(isLoading = false, error = t.localizedMessage ?: "Failed to load feed")
                    } else {
                        // Mid-feed failure: the feed stays usable, the error is surfaced as a
                        // transient snackbar instead of replacing the whole screen.
                        current.copy(isLoading = false, pageError = t.localizedMessage ?: "Failed to load feed")
                    }
                }
            }
        }
    }

    fun loadNextPageIfNeeded(visibleIndex: Int) {
        if (visibleIndex >= state.value.items.size - 2 && state.value.canLoadMore && !state.value.isLoading) {
            loadFeed(reset = false)
        }
    }

    fun search(query: String) {
        if (state.value.isOffline) return
        val trimmed = query.trim()
        // Snapshot the unfiltered feed the first time a query is applied, so clearing the
        // query can restore the browsing position instead of reloading from scratch.
        if (state.value.searchQuery.isBlank()) {
            baseItems = state.value.items
            baseNextPageIndex = state.value.nextPageIndex
            baseNextCursor = state.value.nextCursor
            baseCursorMode = state.value.cursorMode
            baseCanLoadMore = state.value.canLoadMore
            basePosition = state.value.activeIndex
        }
        persistUnlessIncognito { sourcePreferences.lastReelsQuery(state.value.currentSourceId).set(trimmed) }
        mutableState.update { it.copy(searchQuery = trimmed, isSearchBarOpen = false) }
        loadFeed(reset = true)
    }

    fun clearSearch() {
        if (state.value.isOffline) return
        // Invalidate any in-flight search so it cannot overwrite the restored base feed.
        loadJob?.cancel()
        loadGeneration.incrementAndGet()
        persistUnlessIncognito { sourcePreferences.lastReelsQuery(state.value.currentSourceId).set("") }
        if (baseItems.isNotEmpty()) {
            // Restore the pre-search feed and scroll back to where the user was.
            mutableState.update { current ->
                current.copy(
                    searchQuery = "",
                    isSearchBarOpen = false,
                    items = baseItems,
                    isLoading = false,
                    error = null,
                    canLoadMore = baseCanLoadMore,
                    nextPageIndex = baseNextPageIndex,
                    nextCursor = baseNextCursor,
                    cursorMode = baseCursorMode,
                    feedGeneration = current.feedGeneration + 1,
                    targetPageIndex = basePosition.coerceIn(0, (baseItems.size - 1).coerceAtLeast(0)),
                )
            }
        } else {
            mutableState.update { it.copy(searchQuery = "", isSearchBarOpen = false) }
            loadFeed(reset = true)
        }
    }

    fun setFilters(filters: AnimeFilterList) {
        // AnimeFilterList.equals() is always false by design, so a fresh wrapper guarantees
        // Compose observes the change while the dialog keeps mutating the same filter objects.
        mutableState.update { it.copy(filters = AnimeFilterList(filters.list)) }
    }

    fun applyFilters() {
        if (state.value.isOffline) return
        val serialized = serializeFilters(state.value.filters)
        persistUnlessIncognito { sourcePreferences.lastReelsFilter(state.value.currentSourceId).set(serialized) }
        mutableState.update { it.copy(isFilterDialogOpen = false) }
        loadFeed(reset = true)
    }

    fun resetFilters() {
        if (state.value.isOffline) return
        val src = source ?: return
        val freshFilters = src.getFilterList()
        persistUnlessIncognito {
            sourcePreferences.lastReelsQuery(state.value.currentSourceId).set("")
            sourcePreferences.lastReelsFilter(state.value.currentSourceId).set("")
        }
        mutableState.update {
            it.copy(
                filters = freshFilters,
                searchQuery = "",
                isFilterDialogOpen = false,
                isSearchBarOpen = false,
            )
        }
        loadFeed(reset = true)
    }

    fun toggleFilterDialog(open: Boolean) {
        mutableState.update { it.copy(isFilterDialogOpen = open) }
    }

    fun toggleSearchBar(open: Boolean) {
        mutableState.update { it.copy(isSearchBarOpen = open) }
    }

    fun toggleSourcePicker(open: Boolean) {
        mutableState.update { it.copy(isSourcePickerOpen = open) }
    }

    fun toggleAutoAdvance() {
        val next = !state.value.isAutoAdvance
        sourcePreferences.autoAdvanceReels().set(next)
        mutableState.update { it.copy(isAutoAdvance = next) }
    }

    fun toggleCropMode() {
        val next = !state.value.isCropMode
        sourcePreferences.reelsCropMode().set(next)
        mutableState.update { it.copy(isCropMode = next) }
    }

    fun togglePreload() {
        val next = !state.value.preloadEnabled
        sourcePreferences.reelsPreloadEnabled().set(next)
        mutableState.update { it.copy(preloadEnabled = next) }
    }

    fun togglePreloadWifiOnly() {
        val next = !state.value.preloadWifiOnly
        sourcePreferences.reelsPreloadWifiOnly().set(next)
        mutableState.update { it.copy(preloadWifiOnly = next) }
    }

    fun toggleLike(item: ShortVideoItem) {
        val videoId = item.id
        val willLike = videoId !in state.value.likedIds
        // The item comes from the caller (the page rendering it): re-finding it in state
        // would silently skip the insert when a feed refresh displaced the video between
        // the tap and the write. The write must survive screen disposal (NonCancellable).
        val sourceId = offlineSourceIds[videoId] ?: state.value.currentSourceId
        decidedIds += videoId
        mutableState.update { state ->
            val newLikes = if (videoId in state.likedIds) {
                state.likedIds - videoId
            } else {
                state.likedIds + videoId
            }
            state.copy(likedIds = newLikes.toImmutableSet())
        }
        // Incognito blocks persisting a NEW like, but a removal must always reach the DB so a
        // previously saved like doesn't "resurrect" after restart.
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            if (willLike) {
                if (!isIncognito()) {
                    reelsFavoriteRepository.insert(item.toReelsFavorite(sourceId))
                }
            } else {
                reelsFavoriteRepository.delete(videoId, sourceId)
            }
        }
    }

    private fun loadPersistedFavorites(sourceId: Long) {
        screenModelScope.launch(ioDispatcher) {
            val favoriteIds = reelsFavoriteRepository.getIdsBySource(sourceId)
            mutableState.update { current ->
                // The read raced a source switch: its result belongs to the old source.
                if (current.currentSourceId != sourceId) return@update current
                // Merge instead of blind overwrite, but skip ids the user already decided
                // this session: the (older) persisted snapshot must not resurrect an unlike
                // or erase a like that happened while the DB read was in flight.
                val persisted = favoriteIds.filterNot { it in decidedIds }
                current.copy(likedIds = (persisted.toSet() + current.likedIds).toImmutableSet())
            }
        }
    }

    private fun ShortVideoItem.toReelsFavorite(sourceId: Long) = ReelsFavorite(
        videoId = id,
        sourceId = sourceId,
        title = title,
        author = author,
        videoUrl = videoUrl,
        videoUrlHd = videoUrlHd,
        posterUrl = posterUrl,
        posterUrlVertical = posterUrlVertical,
        webUrl = webUrl,
        durationSec = durationSec?.toDouble(),
        hasAudio = hasAudio,
        addedAt = Date(),
    )

    private fun ReelsFavorite.toShortVideoItem() = ShortVideoItem(
        id = videoId,
        title = title,
        author = author,
        videoUrl = videoUrl,
        videoUrlHd = videoUrlHd,
        posterUrl = posterUrl,
        posterUrlVertical = posterUrlVertical,
        durationSec = durationSec?.toFloat(),
        hasAudio = hasAudio,
        webUrl = webUrl,
    )

    fun toggleMute() {
        val next = !state.value.isMuted
        // Any manual toggle ends the "undecided" part of the session: from now on the
        // persisted preference applies on re-entry.
        sessionSound.decided = true
        sourcePreferences.reelsMuted().set(next)
        mutableState.update { it.copy(isMuted = next, showUnmuteHint = false) }
    }

    fun dismissUnmuteHint() {
        // Timeout dismissal is NOT a decision: a later re-entry shows the hint again while
        // the session stays undecided.
        mutableState.update { it.copy(showUnmuteHint = false) }
    }

    fun toggleDataSaver() {
        val next = !state.value.dataSaverMetered
        sourcePreferences.reelsDataSaverMetered().set(next)
        mutableState.update { it.copy(dataSaverMetered = next) }
    }

    fun toggleQuality() {
        val next = !state.value.isHdQuality
        sourcePreferences.reelsHdQuality().set(next)
        mutableState.update { it.copy(isHdQuality = next) }
    }

    fun togglePlayPause() {
        mutableState.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun onPageChanged(index: Int) {
        mutableState.update { it.copy(activeIndex = index, isPlaying = true) }
        if (!state.value.isOffline) {
            persistUnlessIncognito {
                sourcePreferences.lastReelsPosition(state.value.currentSourceId).set(index)
            }
        }
        loadNextPageIfNeeded(index)
    }

    fun onPageErrorShown() {
        mutableState.update { it.copy(pageError = null) }
    }

    private fun serializeFilters(filters: AnimeFilterList): String {
        return buildString {
            filters.forEachIndexed { index, filter ->
                if (index > 0) append(";")
                append(index).append("=")
                when (filter) {
                    is AnimeFilter.Select<*> -> append(filter.state)
                    is AnimeFilter.CheckBox -> append(filter.state)
                    is AnimeFilter.Text -> append(filter.state)
                    is AnimeFilter.Sort -> append("${filter.state?.index ?: -1}:${filter.state?.ascending ?: true}")
                    else -> append(filter.state.toString())
                }
            }
        }
    }

    private fun restoreFilters(filters: AnimeFilterList, serialized: String) {
        if (serialized.isBlank()) return
        val entries = serialized.split(";").associate {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) parts[0].toIntOrNull() to parts[1] else null to null
        }
        filters.forEachIndexed { index, filter ->
            val rawValue = entries[index] ?: return@forEachIndexed
            try {
                when (filter) {
                    is AnimeFilter.Select<*> -> rawValue.toIntOrNull()?.let { filter.state = it }
                    is AnimeFilter.CheckBox -> rawValue.toBooleanStrictOrNull()?.let { filter.state = it }
                    is AnimeFilter.Text -> filter.state = rawValue
                    is AnimeFilter.Sort -> {
                        val sortParts = rawValue.split(":")
                        if (sortParts.size == 2) {
                            val sortIdx = sortParts[0].toIntOrNull() ?: -1
                            val asc = sortParts[1].toBooleanStrictOrNull() ?: true
                            if (sortIdx >= 0) {
                                filter.state = AnimeFilter.Sort.Selection(sortIdx, asc)
                            }
                        }
                    }
                    else -> {}
                }
            } catch (_: Exception) {}
        }
    }

    @Immutable
    data class State(
        val currentSourceId: Long,
        val sourceName: String = "",
        val isOffline: Boolean = false,
        val supportsTags: Boolean = true,
        val availableSources: ImmutableList<AnimeSource> = persistentListOf(),
        val sourceIcons: ImmutableMap<Long, ImageBitmap> = persistentHashMapOf(),
        val items: ImmutableList<ShortVideoItem> = persistentListOf(),
        val isLoading: Boolean = true,
        // Pagination cursor: index of the next feed page to fetch and whether the source
        // reported more pages. Lives in State so stale loads cannot corrupt it.
        val nextPageIndex: Int = 1,
        // Continuation token for cursor mode (contract v17 sticky protocol): set from every
        // successful FeedPage.nextCursor; null while in page-int mode.
        val nextCursor: String? = null,
        // Sticky per generation: locked true on the first response with a non-null cursor.
        val cursorMode: Boolean = false,
        val canLoadMore: Boolean = true,
        val isMuted: Boolean = false,
        val isHdQuality: Boolean = true,
        // Force SD while on metered (non-Wi-Fi) networks; applied when a page activates.
        val dataSaverMetered: Boolean = true,
        val isAutoAdvance: Boolean = true,
        val isCropMode: Boolean = false,
        val preloadEnabled: Boolean = true,
        val preloadWifiOnly: Boolean = false,
        val likedIds: ImmutableSet<String> = persistentSetOf(),
        val activeIndex: Int = 0,
        val isPlaying: Boolean = true,
        // Bumped every time the feed content is replaced (search/filter/source/reset) so the
        // pager can reliably scroll back to the first video.
        val feedGeneration: Int = 0,
        // Page the pager should scroll to after a feedGeneration bump: 0 on fresh loads,
        // the remembered position when a cleared search restores the base feed.
        val targetPageIndex: Int = 0,
        val searchQuery: String = "",
        // Headers for the player's HTTP data source, captured from the current source.
        val sourceHeaders: ImmutableMap<String, String> = persistentHashMapOf(),
        val filters: AnimeFilterList = AnimeFilterList(),
        val isFilterDialogOpen: Boolean = false,
        val isSearchBarOpen: Boolean = false,
        val isSourcePickerOpen: Boolean = false,
        val error: String? = null,
        // Transient append failure while the feed is non-empty; surfaced as a snackbar.
        val pageError: String? = null,
        // Session-scoped "tap to unmute" pill: visible while the user has not decided sound.
        val showUnmuteHint: Boolean = false,
    )
}
