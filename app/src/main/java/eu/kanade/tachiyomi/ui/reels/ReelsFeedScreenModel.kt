package eu.kanade.tachiyomi.ui.reels

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCategorizedSearchSource
import eu.kanade.tachiyomi.animesource.AnimeCategorySubscriptionSource
import eu.kanade.tachiyomi.animesource.AnimeContentPreferencesSource
import eu.kanade.tachiyomi.animesource.AnimeCreatorFeedSource
import eu.kanade.tachiyomi.animesource.AnimeCustomFeedSource
import eu.kanade.tachiyomi.animesource.AnimeFeedBrowseSource
import eu.kanade.tachiyomi.animesource.AnimeFeedLoginSource
import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.animesource.AnimeFeedWebLoginSource
import eu.kanade.tachiyomi.animesource.AnimeReelsFeedbackSource
import eu.kanade.tachiyomi.animesource.AnimeSearchHintsSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.ContentPreferenceOption
import eu.kanade.tachiyomi.animesource.model.CustomFeedRef
import eu.kanade.tachiyomi.animesource.model.FeedPage
import eu.kanade.tachiyomi.animesource.model.SearchSuggestions
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.reels.anime.model.ReelsFavorite
import tachiyomi.domain.reels.anime.model.ReelsFollow
import tachiyomi.domain.reels.anime.repository.ReelsFavoriteRepository
import tachiyomi.domain.reels.anime.repository.ReelsFollowRepository
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
    // Contract v18 creator modes (mutually exclusive, never combined with offline playlists):
    // [creator] serves one creator's feed via AnimeCreatorFeedSource; [followingFeed] merges
    // the latest videos of every followed creator of [initialSourceId].
    private val creator: String? = null,
    private val followingFeed: Boolean = false,
    // Contract v19 custom-feed mode: serves one custom feed via AnimeCustomFeedSource (never
    // combined with offline playlists or the creator modes above).
    private val customFeedId: String? = null,
    private val customFeedName: String? = null,
    // Contract v20 niche mode: serves one category feed via AnimeFeedBrowseSource (never
    // combined with offline playlists or the modes above).
    private val nicheId: String? = null,
    private val nicheName: String? = null,
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
    private val reelsFollowRepository: ReelsFollowRepository = Injekt.get(),
    private val sessionSound: ReelsSessionSoundState = sharedSessionSound,
) : StateScreenModel<ReelsFeedScreenModel.State>(
    State(
        currentSourceId = initialSourceId,
        customFeedName = customFeedName,
        nicheName = nicheName,
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

        // Fan-out protection for the FOLLOWING aggregation: a generation fetches one page
        // per followed creator, so the cap bounds concurrent requests. Enforced host-side
        // (the repository stores whatever it is told).
        const val MAX_FOLLOWS_PER_SOURCE = 100

        // Shown when a login returns false (rejected credentials); transport errors surface
        // their own message instead.
        const val LOGIN_FAILED_MESSAGE = "Login failed. Check email and password."
    }

    /** Which feed [loadFeed] generates. Fixed for the model's lifetime (per screen key). */
    enum class FeedMode { GLOBAL, CREATOR, FOLLOWING, CUSTOM, NICHE }

    private val mode: FeedMode = when {
        followingFeed -> FeedMode.FOLLOWING
        creator != null -> FeedMode.CREATOR
        customFeedId != null -> FeedMode.CUSTOM
        nicheId != null -> FeedMode.NICHE
        else -> FeedMode.GLOBAL
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
    private var baseSeenIds: ImmutableSet<String> = persistentSetOf()
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

    // Creators the user followed/unfollowed this session (main-confined): the same merge
    // protection as [decidedIds], applied to the follows snapshot. Cleared on source switch.
    private val decidedFollows = mutableSetOf<String>()

    // ---------------------------------------------------------------------------
    // FOLLOWING aggregation (contract v18)
    //
    // One stream per followed creator of the current source. Unfollows apply on the NEXT
    // generation (refresh / re-entry): splicing live streams would re-sort pages the user
    // has already seen — documented v1 limitation.
    // ---------------------------------------------------------------------------

    private class FollowingStream(val creator: String) {
        var pagesFetched: Int = 0
        var cursor: String? = null
        var cursorMode: Boolean = false
        var exhausted: Boolean = false
        val buffer = ArrayDeque<ShortVideoItem>()
    }

    // Replaced wholesale on a FOLLOWING reset generation; appends mutate stream objects in
    // place and only drop failed ones. Read/written from generation-guarded load jobs only.
    private var followingStreams: List<FollowingStream> = emptyList()

    init {
        if (initialFavorites.isNotEmpty()) {
            offlineSourceIds = initialFavorites.associate { it.videoId to it.sourceId }
            mutableState.update {
                it.copy(
                    isOffline = true,
                    items = initialFavorites.map { fav -> fav.toShortVideoItem() }.toImmutableList(),
                    seenIds = initialFavorites.map { it.videoId }.toImmutableSet(),
                    likedIds = initialFavorites.map { it.videoId }.toImmutableSet(),
                    isLoading = false,
                    feedGeneration = 1,
                    targetPageIndex = initialPage,
                    canLoadMore = false,
                )
            }
        } else {
            mutableState.update { it.copy(mode = mode, creator = creator) }
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
            val creatorCapable = rawSource is AnimeCreatorFeedSource
            val loginCapable = rawSource is AnimeFeedLoginSource
            val customFeedCapable = rawSource is AnimeCustomFeedSource
            val webLoginCapable = rawSource is AnimeFeedWebLoginSource
            val browseCapable = rawSource is AnimeFeedBrowseSource
            val contentPrefsCapable = rawSource is AnimeContentPreferencesSource
            val categorySubCapable = rawSource is AnimeCategorySubscriptionSource
            // Non-global modes need their matching capability (e.g. the plugin was downgraded
            // between sessions): refuse instead of silently serving the wrong feed.
            val missingCapability = when (mode) {
                FeedMode.CUSTOM -> !customFeedCapable
                FeedMode.NICHE -> !browseCapable
                FeedMode.GLOBAL -> false
                else -> !creatorCapable
            }
            if (missingCapability) {
                source = null
                mutableState.update {
                    it.copy(
                        error = when (mode) {
                            FeedMode.CUSTOM -> "Source does not support custom feeds"
                            FeedMode.NICHE -> "Source does not support category feeds"
                            else -> "Source does not support creator feeds"
                        },
                        isLoading = false,
                        isSourcePickerOpen = false,
                    )
                }
                return
            }
            source = rawSource
            persistUnlessIncognito { sourcePreferences.lastUsedReelsSource().set(newSourceId) }
            baseItems = persistentListOf()
            baseNextPageIndex = 1
            baseNextCursor = null
            baseCursorMode = false
            baseCanLoadMore = true
            basePosition = 0
            decidedIds.clear()
            decidedFollows.clear()
            followingStreams = emptyList()
            // Only the global feed restores a per-source browsing position; creator and
            // FOLLOWING pages always start at the top.
            pendingRestorePosition = if (mode == FeedMode.GLOBAL) {
                sourcePreferences.lastReelsPosition(newSourceId).get().coerceAtLeast(0)
            } else {
                0
            }
            restorePositionPending = mode == FeedMode.GLOBAL
            // The creator page and the FOLLOWING aggregation have no search/filter surface
            // and must never touch the saved global query or filters.
            val initialFilters = if (mode == FeedMode.GLOBAL) rawSource.getFilterList() else AnimeFilterList()
            val savedQuery = if (mode == FeedMode.GLOBAL) sourcePreferences.lastReelsQuery(newSourceId).get() else ""

            // Restore saved filter values
            if (mode == FeedMode.GLOBAL) {
                val savedFiltersSerialized = sourcePreferences.lastReelsFilter(newSourceId).get()
                restoreFilters(initialFilters, savedFiltersSerialized)
            }

            mutableState.update {
                it.copy(
                    currentSourceId = newSourceId,
                    sourceName = rawSource.name,
                    supportsTags = rawSource.supportsTags,
                    // Search hints (v19 addendum) are per source: drop the previous list;
                    // loadSearchHints below refills it when the capability exists.
                    searchHints = persistentListOf(),
                    searchQuery = savedQuery,
                    // Categorized-search tabs (v20) belong to the previous source's query.
                    searchSuggestions = null,
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
                    // Follows are per source too: drop the previous source's set before the
                    // new one is loaded by loadPersistedFollows below.
                    isCreatorCapable = creatorCapable,
                    followingCreators = persistentSetOf(),
                    // Login state is per source: snapshot the persisted session (non-suspend,
                    // so safe on the main thread) and drop the previous source's account.
                    isLoginCapable = loginCapable,
                    loggedInAccount = (rawSource as? AnimeFeedLoginSource)
                        ?.takeIf { it.isLoggedIn() }
                        ?.loggedInAccount(),
                    isLoggingIn = false,
                    loginError = null,
                    // Web login (v20): per source, reset on switch.
                    isWebLoginCapable = webLoginCapable,
                    isWebLoginDialogOpen = false,
                    webLoginHint = false,
                    webLoginStage2Attempt = 0,
                    // Custom feeds (v19): per source, reset on switch.
                    isCustomFeedCapable = customFeedCapable,
                    // Category browse (v20): per source, reset on switch.
                    isBrowseCapable = browseCapable,
                    // Category subscriptions (v20): per source, reset on switch.
                    isCategorySubscribable = categorySubCapable,
                    isCategoryFollowed = false,
                    // Content preferences (v20): per source, reset on switch.
                    isContentPreferencesCapable = contentPrefsCapable,
                    contentPreferences = null,
                    isContentPreferencesOpen = false,
                    isContentPreferencesLoading = false,
                    contentPreferencesError = null,
                    customFeeds = persistentListOf(),
                    isCustomFeedsOpen = false,
                    isCustomFeedsLoading = false,
                    customFeedsError = null,
                    isSourcePickerOpen = false,
                    error = null,
                )
            }
            loadPersistedFavorites(newSourceId)
            loadPersistedFollows(newSourceId)
            loadSearchHints()
            if (mode == FeedMode.NICHE) loadSubscribedCategoryState()
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
            if (mode == FeedMode.FOLLOWING) {
                loadFollowing(generation = generation, reset = reset, src = src)
                return@launch
            }
            try {
                val query = state.value.searchQuery
                val filters = state.value.filters
                val page = state.value.nextPageIndex
                // Contract v17: while locked into cursor mode the token is authoritative;
                // in page-int mode the source always receives a null cursor.
                val cursor = if (state.value.cursorMode) state.value.nextCursor else null
                // The creator page shares the sticky cursor protocol with the global feed,
                // just on its own per-stream token space (contract v18). switchSource has
                // already refused a non-capable source in this mode.
                val pageData = when {
                    mode == FeedMode.CREATOR -> (src as AnimeCreatorFeedSource)
                        .getCreatorFeed(creator.orEmpty(), page, cursor)
                    mode == FeedMode.CUSTOM -> (src as AnimeCustomFeedSource)
                        .getCustomFeed(customFeedId.orEmpty(), page, cursor)
                    mode == FeedMode.NICHE -> (src as? AnimeFeedBrowseSource)
                        ?.getCategoryFeed(nicheId.orEmpty(), page, cursor)
                        // Capability vanished between the switchSource guard and this load:
                        // serve an empty terminal page instead of crashing the pager.
                        ?: FeedPage(videos = emptyList(), hasNextPage = false)
                    query.isNotBlank() -> src.getSearchFeed(page, cursor, query, filters)
                    else -> src.getFeed(page, cursor, filters)
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
                    val incoming = pageData.videos.distinctBy { it.id }
                    val newItems = if (reset) {
                        incoming
                    } else {
                        incoming.filterNot { it.id in current.seenIds }
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
                        items = (if (reset) newItems else current.items + newItems).toImmutableList(),
                        seenIds = if (reset) {
                            newItems.map { it.id }.toImmutableSet()
                        } else {
                            (current.seenIds + newItems.map { it.id }).toImmutableSet()
                        },
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

    /**
     * FOLLOWING generation (contract v18): fan out one page per followed creator in
     * parallel, merge the buffers newest-first, and top up only the streams whose buffers
     * ran dry. A failing stream is dropped from this generation (its error joins
     * [State.pageError]); the feed survives as long as one stream is alive. All streams
     * failing on a reset generation is the only way to reach the full error state.
     */
    private suspend fun loadFollowing(generation: Int, reset: Boolean, src: AnimeFeedSource) {
        val capable = src as? AnimeCreatorFeedSource ?: run {
            if (loadGeneration.get() != generation) return
            mutableState.update { current ->
                current.copy(isLoading = false, error = "Source does not support creator feeds")
            }
            return
        }
        try {
            val requests: List<Triple<FollowingStream, Int, String?>> = if (reset) {
                val creators = reelsFollowRepository.getCreatorsBySource(state.value.currentSourceId)
                creators.map { Triple(FollowingStream(it), 1, null as String?) }
            } else {
                followingStreams.filter { !it.exhausted && it.buffer.isEmpty() }
                    .map { Triple(it, it.pagesFetched + 1, if (it.cursorMode) it.cursor else null) }
            }
            currentCoroutineContext().ensureActive()
            if (requests.isEmpty()) {
                if (reset) {
                    // Follows exist per source; an empty follow set is an empty feed, not an error.
                    followingStreams = emptyList()
                    mutableState.update { current ->
                        if (loadGeneration.get() != generation) return@update current
                        current.copy(
                            items = persistentListOf(),
                            seenIds = persistentSetOf(),
                            isLoading = false,
                            error = null,
                            pageError = null,
                            canLoadMore = false,
                            feedGeneration = current.feedGeneration + 1,
                            targetPageIndex = 0,
                            activeIndex = 0,
                        )
                    }
                } else {
                    mutableState.update { current ->
                        if (loadGeneration.get() != generation) return@update current
                        current.copy(isLoading = false, canLoadMore = false)
                    }
                }
                return
            }

            val outcomes: List<Result<FeedPage>> = coroutineScope {
                requests.map { (stream, page, cursor) ->
                    async { runCatching { capable.getCreatorFeed(stream.creator, page, cursor) } }
                }.map { it.await() }
            }
            currentCoroutineContext().ensureActive()
            if (loadGeneration.get() != generation) return

            val failures = mutableListOf<String>()
            val alive = mutableListOf<FollowingStream>()
            requests.forEachIndexed { index, (stream, page, _) ->
                val result = outcomes[index]
                val pageData = result.getOrNull()
                if (pageData == null) {
                    val t = result.exceptionOrNull()
                    logcat(LogPriority.ERROR, throwable = t) { "Following stream '${stream.creator}' failed" }
                    failures += "'${stream.creator}': ${t?.localizedMessage ?: "failed"}"
                    return@forEachIndexed
                }
                stream.buffer.addAll(pageData.videos)
                stream.pagesFetched = page
                // Per-stream v17 sticky cursor rules: the first non-null token locks the
                // stream; losing it mid-stream stops that stream only, not the feed.
                val newCursorMode = stream.cursorMode || pageData.nextCursor != null
                val cursorLost = newCursorMode && pageData.nextCursor == null && pageData.hasNextPage
                if (cursorLost) {
                    logcat(LogPriority.WARN) {
                        "Following stream '${stream.creator}' dropped its cursor mid-feed; stopping it (contract v18)."
                    }
                }
                stream.cursorMode = newCursorMode
                stream.cursor = pageData.nextCursor
                stream.exhausted = !pageData.hasNextPage || cursorLost
                alive += stream
            }
            val failedStreams = requests.filterIndexed { index, _ -> outcomes[index].isFailure }
                .map { it.first }
                .toSet()
            followingStreams = if (reset) alive else followingStreams.filterNot { it in failedStreams }
            val merged = mergeFollowingStreams(followingStreams)
            val failedText = failures.joinToString("; ")

            mutableState.update { current ->
                if (loadGeneration.get() != generation) return@update current
                val anyAlive = followingStreams.any { !it.exhausted }
                if (reset) {
                    val newItems = merged.distinctBy { it.id }
                    if (newItems.isEmpty() && failures.isNotEmpty() && alive.isEmpty()) {
                        // All-failed fan-out: nothing to show, surface the combined error.
                        current.copy(
                            isLoading = false,
                            error = failedText,
                            canLoadMore = false,
                            feedGeneration = current.feedGeneration + 1,
                            targetPageIndex = 0,
                            activeIndex = 0,
                        )
                    } else {
                        current.copy(
                            items = newItems.toImmutableList(),
                            seenIds = newItems.map { it.id }.toImmutableSet(),
                            isLoading = false,
                            error = null,
                            pageError = failedText.takeIf { it.isNotEmpty() },
                            canLoadMore = anyAlive,
                            feedGeneration = current.feedGeneration + 1,
                            targetPageIndex = 0,
                            activeIndex = 0,
                        )
                    }
                } else {
                    val fresh = merged.filterNot { it.id in current.seenIds }
                    current.copy(
                        items = (current.items + fresh).toImmutableList(),
                        seenIds = (current.seenIds + fresh.map { it.id }).toImmutableSet(),
                        isLoading = false,
                        pageError = failedText.takeIf { it.isNotEmpty() },
                        canLoadMore = anyAlive,
                    )
                }
            }
        } catch (e: CancellationException) {
            // Superseded by a newer reset/switch: keep whatever state the new load owns.
        } catch (t: Throwable) {
            logcat(LogPriority.ERROR, t) { "Failed to load following feed from source ${state.value.currentSourceId}" }
            if (loadGeneration.get() != generation) return
            mutableState.update { current ->
                if (current.items.isEmpty()) {
                    current.copy(isLoading = false, error = t.localizedMessage ?: "Failed to load feed")
                } else {
                    current.copy(isLoading = false, pageError = t.localizedMessage ?: "Failed to load feed")
                }
            }
        }
    }

    /**
     * k-way merge over the streams' buffer heads: newest first by
     * [ShortVideoItem.createdAtEpochSec]; heads without a timestamp keep their own stream
     * order and are pulled round-robin among themselves. Drains every buffer — the merged
     * result is the feed tail until the next top-up.
     */
    private fun mergeFollowingStreams(streams: List<FollowingStream>): List<ShortVideoItem> {
        val merged = mutableListOf<ShortVideoItem>()
        var roundRobin = 0
        while (true) {
            val withItems = streams.filter { it.buffer.isNotEmpty() }
            if (withItems.isEmpty()) return merged
            val anyTimed = withItems.any { it.buffer.first().createdAtEpochSec != null }
            val pick = if (anyTimed) {
                // maxBy returns the first maximal element: equal timestamps keep stream order.
                withItems.maxBy { it.buffer.first().createdAtEpochSec ?: Long.MIN_VALUE }
            } else {
                withItems[roundRobin++ % withItems.size]
            }
            merged += pick.buffer.removeFirst()
        }
    }

    /**
     * Follow/unfollow the given creator on the current source. Explicit user data: the
     * write persists regardless of incognito (same rule as favorite removal).
     *
     * @return false when the new-follow would cross [MAX_FOLLOWS_PER_SOURCE] (no state or
     * DB change happens; the caller surfaces the refusal, e.g. with a snackbar).
     */
    fun toggleFollow(creator: String): Boolean {
        val sourceId = state.value.currentSourceId
        val willFollow = creator !in state.value.followingCreators
        if (willFollow && state.value.followingCreators.size >= MAX_FOLLOWS_PER_SOURCE) return false
        decidedFollows += creator
        mutableState.update { state ->
            val newFollows = if (willFollow) {
                state.followingCreators + creator
            } else {
                state.followingCreators - creator
            }
            state.copy(followingCreators = newFollows.toImmutableSet())
        }
        // The tap is authoritative for this session; the DB write must survive screen
        // disposal (NonCancellable), and repository errors are logged, not surfaced.
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            if (willFollow) {
                reelsFollowRepository.insert(ReelsFollow(sourceId = sourceId, creator = creator, addedAt = Date()))
            } else {
                reelsFollowRepository.delete(sourceId, creator)
            }
        }
        return true
    }

    private fun loadPersistedFollows(sourceId: Long) {
        screenModelScope.launch(ioDispatcher) {
            val creators = reelsFollowRepository.getCreatorsBySource(sourceId)
            mutableState.update { current ->
                // The read raced a source switch: its result belongs to the old source.
                if (current.currentSourceId != sourceId) return@update current
                // Skip creators the user already toggled this session: a stale snapshot must
                // not resurrect an unfollow or drop a fresh follow.
                val persisted = creators.filterNot { it in decidedFollows }
                current.copy(followingCreators = (current.followingCreators + persisted).toImmutableSet())
            }
        }
    }

    fun search(query: String) {
        if (state.value.isOffline) return
        val trimmed = query.trim()
        // Snapshot the unfiltered feed the first time a query is applied, so clearing the
        // query can restore the browsing position instead of reloading from scratch.
        if (state.value.searchQuery.isBlank()) {
            baseItems = state.value.items
            baseSeenIds = state.value.seenIds
            baseNextPageIndex = state.value.nextPageIndex
            baseNextCursor = state.value.nextCursor
            baseCursorMode = state.value.cursorMode
            baseCanLoadMore = state.value.canLoadMore
            basePosition = state.value.activeIndex
        }
        persistUnlessIncognito { sourcePreferences.lastReelsQuery(state.value.currentSourceId).set(trimmed) }
        mutableState.update { it.copy(searchQuery = trimmed, isSearchBarOpen = false) }
        requestSearchSuggestions(trimmed)
        loadFeed(reset = true)
    }

    /**
     * Categorized search hits (contract v20) for the search-panel tabs — requested live while
     * the user types (site parity) and on submit. A throwing/absent capability leaves the tabs
     * hidden (null) and never touches the flat stream. A response that races a newer query is
     * dropped via [suggestionsQuery].
     */
    private var suggestionsQuery: String = ""

    fun requestSearchSuggestions(query: String) {
        val src = source as? AnimeCategorizedSearchSource
        if (src == null || query.isBlank()) {
            suggestionsQuery = ""
            mutableState.update { it.copy(searchSuggestions = null) }
            return
        }
        suggestionsQuery = query
        screenModelScope.launch(ioDispatcher) {
            val suggestions = runCatching { src.getCategorizedSearch(query) }.getOrNull()
            mutableState.update { current ->
                if (suggestionsQuery != query) return@update current
                current.copy(searchSuggestions = suggestions)
            }
        }
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
                    searchSuggestions = null,
                    items = baseItems,
                    seenIds = baseSeenIds,
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
            mutableState.update { it.copy(searchQuery = "", isSearchBarOpen = false, searchSuggestions = null) }
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
        if (open) loadSearchHints()
    }

    /**
     * Search hints (contract v19 addendum): pulls the source's tag list for the search-bar
     * chips. Optional capability — sources without it (or failing calls) leave the state
     * untouched, so the TopBar keeps showing its static fallback list. Same shape as
     * [loadCustomFeeds]: background load, source-switch race guard, no error surface.
     */
    private fun loadSearchHints() {
        val src = source as? AnimeSearchHintsSource ?: return
        val sourceId = state.value.currentSourceId
        screenModelScope.launch(ioDispatcher) {
            val hints = runCatching { src.getSearchHints() }.getOrNull() ?: return@launch
            mutableState.update { current ->
                // The read raced a source switch: drop it if the source changed.
                if (current.currentSourceId != sourceId) return@update current
                current.copy(searchHints = hints.toImmutableList())
            }
        }
    }

    fun toggleSourcePicker(open: Boolean) {
        mutableState.update { it.copy(isSourcePickerOpen = open) }
    }

    fun toggleLoginDialog(open: Boolean) {
        mutableState.update { it.copy(isLoginDialogOpen = open, loginError = if (open) null else it.loginError) }
    }

    /** Opens/closes the hosted-web-login WebView dialog (contract v20). */
    fun toggleWebLoginDialog(open: Boolean) {
        mutableState.update {
            it.copy(
                isWebLoginDialogOpen = open,
                webLoginHint = if (open) false else it.webLoginHint,
                webLoginStage2Attempt = if (open) 0 else it.webLoginStage2Attempt,
            )
        }
    }

    /** Login entry routing (contract v20): hosted web flow first, password dialog otherwise. */
    fun openLoginFlow() {
        if (state.value.isWebLoginCapable) toggleWebLoginDialog(true) else toggleLoginDialog(true)
    }

    /** Entry URL for the hosted web login of the current source; null when not capable. */
    fun webLoginUrl(): String? = (source as? AnimeFeedWebLoginSource)?.webLoginUrl()

    /** Stage-2 PKCE authorize URL (fresh verifier); null when not capable. */
    fun ownAuthorizeUrl(): String? = (source as? AnimeFeedWebLoginSource)?.ownAuthorizeUrl()

    /**
     * WebView session import (contract v20). A false answer keeps the dialog open (the host
     * retries on the next page-finished/Done press) and surfaces [State.webLoginHint]; true
     * closes the dialog, snapshots the account label and reloads the feed exactly like a
     * password-login success. Transport failures of the import count as "not yet".
     */
    fun tryImportWebSession(cookies: Map<String, String>, localStorage: Map<String, String>) {
        val webSource = source as? AnimeFeedWebLoginSource ?: return
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            val imported = runCatching { webSource.importWebSession(cookies, localStorage) }.getOrDefault(false)
            if (imported) applyWebLoginSuccess() else mutableState.update { it.copy(webLoginHint = true) }
        }
    }

    /** True when [url] is a redirect of the source's own in-flight authorize flow (stage 2). */
    fun isOwnWebLoginRedirect(url: String): Boolean =
        (source as? AnimeFeedWebLoginSource)?.isOwnLoginRedirect(url) == true

    /** Stage-2 code exchange (contract v20): same success/failure semantics as the SPA import. */
    fun tryImportWebRedirect(url: String, cookies: Map<String, String>) {
        val webSource = source as? AnimeFeedWebLoginSource ?: return
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            val imported = runCatching { webSource.importWebRedirect(url, cookies) }.getOrDefault(false)
            if (imported) applyWebLoginSuccess() else mutableState.update { it.copy(webLoginHint = true) }
        }
    }

    /**
     * Done-button path: try the SPA-session lift first; when the SPA keeps its tokens in
     * memory (nothing to lift — the RedGIFs case), bump the stage-2 counter so the dialog
     * loads the source's own PKCE authorize URL in the same WebView.
     */
    fun tryImportWebSessionStage2(cookies: Map<String, String>, localStorage: Map<String, String>) {
        val webSource = source as? AnimeFeedWebLoginSource ?: return
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            val imported = runCatching { webSource.importWebSession(cookies, localStorage) }.getOrDefault(false)
            if (imported) {
                applyWebLoginSuccess()
            } else {
                mutableState.update { current ->
                    current.copy(webLoginHint = true, webLoginStage2Attempt = current.webLoginStage2Attempt + 1)
                }
            }
        }
    }

    /** Shared post-web-login path: close the dialog, snapshot the account, reload the feed. */
    private fun applyWebLoginSuccess() {
        val loginSource = source as? AnimeFeedLoginSource
        mutableState.update { current ->
            current.copy(
                isWebLoginDialogOpen = false,
                webLoginHint = false,
                loggedInAccount = loginSource?.takeIf { it.isLoggedIn() }?.loggedInAccount(),
            )
        }
        loadFeed(reset = true)
    }

    // ---------------------------------------------------------------------------
    // Category subscriptions (contract v20)
    // ---------------------------------------------------------------------------

    /** Loads the followed-category membership for the current NICHE feed. */
    private fun loadSubscribedCategoryState() {
        val src = source as? AnimeCategorySubscriptionSource ?: return
        screenModelScope.launch(ioDispatcher) {
            val ids = runCatching { src.getSubscribedCategoryIds() }.getOrDefault(emptyList())
            val target = nicheId
            mutableState.update { current ->
                if (current.mode != FeedMode.NICHE || target == null) return@update current
                current.copy(isCategoryFollowed = target in ids)
            }
        }
    }

    /** Follows/unfollows the current niche (account-gated); the state flips only on success. */
    fun toggleCategoryFollow() {
        val src = source as? AnimeCategorySubscriptionSource ?: return
        val target = nicheId ?: return
        if (!state.value.isCategorySubscribable) return
        val desired = !state.value.isCategoryFollowed
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            val ok = runCatching { src.setCategorySubscription(target, desired) }.getOrDefault(false)
            if (ok) mutableState.update { it.copy(isCategoryFollowed = desired) }
        }
    }

    // ---------------------------------------------------------------------------
    // Content preferences (contract v20)
    // ---------------------------------------------------------------------------

    /** Opens/closes the content-preferences sheet; opening (re)loads the account toggles. */
    fun toggleContentPreferences(open: Boolean) {
        mutableState.update {
            it.copy(
                isContentPreferencesOpen = open,
                contentPreferencesError = if (open) null else it.contentPreferencesError,
            )
        }
        if (open) loadContentPreferences()
    }

    private fun loadContentPreferences() {
        val src = source as? AnimeContentPreferencesSource ?: return
        val sourceId = state.value.currentSourceId
        mutableState.update { it.copy(isContentPreferencesLoading = true, contentPreferencesError = null) }
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            val result = runCatching { src.getContentPreferences() }
            mutableState.update { current ->
                // The read may race a source switch: drop it if the source changed.
                if (current.currentSourceId != sourceId) {
                    return@update current.copy(isContentPreferencesLoading = false)
                }
                current.copy(
                    isContentPreferencesLoading = false,
                    contentPreferences = result.getOrNull()?.toImmutableList(),
                    contentPreferencesError = result.exceptionOrNull()?.localizedMessage,
                )
            }
        }
    }

    /** Persists the enabled-toggle set; reloads the sheet list on success. */
    fun saveContentPreferences(enabledIds: List<String>) {
        val src = source as? AnimeContentPreferencesSource ?: return
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            val ok = runCatching { src.setContentPreferences(enabledIds) }.getOrDefault(false)
            if (ok) {
                loadContentPreferences()
            } else {
                mutableState.update { it.copy(contentPreferencesError = "Save failed") }
            }
        }
    }

    /** Opens/closes the custom-feeds picker; opening also (re)loads the feed list. */
    fun toggleCustomFeeds(open: Boolean) {
        mutableState.update {
            it.copy(isCustomFeedsOpen = open, customFeedsError = if (open) null else it.customFeedsError)
        }
        if (open) loadCustomFeeds()
    }

    private fun loadCustomFeeds() {
        val src = source as? AnimeCustomFeedSource ?: return
        val sourceId = state.value.currentSourceId
        mutableState.update { it.copy(isCustomFeedsLoading = true, customFeedsError = null) }
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            val result = runCatching { src.getCustomFeeds() }
            mutableState.update { current ->
                // The read may race a source switch: drop it if the source changed.
                if (current.currentSourceId != sourceId) return@update current.copy(isCustomFeedsLoading = false)
                current.copy(
                    isCustomFeedsLoading = false,
                    customFeeds = result.getOrNull().orEmpty().toImmutableList(),
                    customFeedsError = result.exceptionOrNull()?.localizedMessage,
                )
            }
        }
    }

    /** Deletes a custom feed, then reloads the picker list. */
    fun deleteCustomFeed(id: String) {
        val src = source as? AnimeCustomFeedSource ?: return
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            runCatching { src.deleteCustomFeed(id) }
            loadCustomFeeds()
        }
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
            // Let a feedback-capable feed source adapt its recommendations to likes (opt-in,
            // fire-and-forget; the local favorite stays authoritative for the offline playlist).
            val feedback = source as? AnimeReelsFeedbackSource
            if (feedback != null) {
                runCatching { feedback.onVideoLiked(videoId, willLike) }
            }
        }
    }

    /** Reports playback of a reel to a feedback-capable source (drives remote personalization). */
    fun reportVideoView(itemId: String, secondsWatched: Float, duration: Float) {
        val feedback = source as? AnimeReelsFeedbackSource ?: return
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            runCatching { feedback.onVideoViewed(itemId, secondsWatched.toDouble(), duration.toDouble()) }
        }
    }

    /**
     * Authenticates against the current source (contract v19). On success the persisted
     * account is reflected in state and the feed reloads (a login can change the personalized
     * stream). A rejected credential set surfaces in [State.loginError]; transport errors log
     * and surface their message — never a crash.
     */
    fun login(email: String, password: String) {
        val loginSource = source as? AnimeFeedLoginSource ?: return
        if (state.value.isLoggingIn) return
        mutableState.update { it.copy(isLoggingIn = true, loginError = null) }
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            val ok = runCatching { loginSource.login(email, password) }.fold(
                onSuccess = { it },
                onFailure = { t ->
                    logcat(LogPriority.ERROR, t) { "Login failed for source ${state.value.currentSourceId}" }
                    mutableState.update {
                        it.copy(isLoggingIn = false, loginError = t.localizedMessage ?: LOGIN_FAILED_MESSAGE)
                    }
                    return@launch
                },
            )
            mutableState.update { current ->
                current.copy(
                    isLoggingIn = false,
                    loggedInAccount = if (ok) loginSource.loggedInAccount() else null,
                    loginError = if (ok) null else LOGIN_FAILED_MESSAGE,
                )
            }
            if (ok) loadFeed(reset = true)
        }
    }

    /** Clears the persisted login session of the current source and reloads the feed. */
    fun logout() {
        val loginSource = source as? AnimeFeedLoginSource ?: return
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            runCatching { loginSource.logout() }
            mutableState.update { it.copy(loggedInAccount = null, loginError = null) }
            loadFeed(reset = true)
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
        // Only the global feed has a per-source browsing position; creator and FOLLOWING
        // pages must not clobber it.
        if (!state.value.isOffline && mode == FeedMode.GLOBAL) {
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
        // Source-supplied tag hints (contract v19 addendum): the reels search-bar chips.
        // Empty => the TopBar falls back to its static popular list.
        val searchHints: ImmutableList<String> = persistentListOf(),
        // Feed generation mode (contract v18): drives the creator chrome in the TopBar and
        // which loadFeed pipeline runs.
        val mode: FeedMode = FeedMode.GLOBAL,
        // Creator name for [FeedMode.CREATOR]; null in every other mode.
        val creator: String? = null,
        // Custom-feed name for [FeedMode.CUSTOM] (its title); null in every other mode.
        val customFeedName: String? = null,
        // Category name for [FeedMode.NICHE] (its title); null in every other mode.
        val nicheName: String? = null,
        // The current source implements AnimeCreatorFeedSource: gates the author chip,
        // the follow action and the follows-screen entry.
        val isCreatorCapable: Boolean = false,
        // Followed creator names on the current source (follows are per source in v1).
        val followingCreators: ImmutableSet<String> = persistentSetOf(),
        // Account login (contract v19): the current source implements AnimeFeedLoginSource.
        val isLoginCapable: Boolean = false,
        // Persisted account label (e.g. the email used to log in), null when logged out.
        val loggedInAccount: String? = null,
        // Login in flight: the dialog disables its confirm button while true.
        val isLoggingIn: Boolean = false,
        // Last login failure reason; cleared on the next attempt/source switch.
        val loginError: String? = null,
        // Custom feeds (v19): the current source implements AnimeCustomFeedSource.
        val isCustomFeedCapable: Boolean = false,
        // Category browse (v20): the current source implements AnimeFeedBrowseSource;
        // gates the account-hub Niches row.
        val isBrowseCapable: Boolean = false,
        // Category subscriptions (v20): the current source implements
        // AnimeCategorySubscriptionSource; gates the follow toggle on NICHE feeds.
        val isCategorySubscribable: Boolean = false,
        val isCategoryFollowed: Boolean = false,
        // Content preferences (v20): the current source implements
        // AnimeContentPreferencesSource; gates the account-hub preferences row.
        val isContentPreferencesCapable: Boolean = false,
        val contentPreferences: ImmutableList<ContentPreferenceOption>? = null,
        val isContentPreferencesOpen: Boolean = false,
        val isContentPreferencesLoading: Boolean = false,
        val contentPreferencesError: String? = null,
        // Loaded list for the picker sheet.
        val customFeeds: ImmutableList<CustomFeedRef> = persistentListOf(),
        val isCustomFeedsOpen: Boolean = false,
        val isCustomFeedsLoading: Boolean = false,
        val customFeedsError: String? = null,
        val availableSources: ImmutableList<AnimeSource> = persistentListOf(),
        val sourceIcons: ImmutableMap<Long, ImageBitmap> = persistentHashMapOf(),
        val items: ImmutableList<ShortVideoItem> = persistentListOf(),
        // Ids already present in [items]: the append path filters incoming pages against this
        // set, keeping long sessions at O(page) instead of distinctBy's O(n²) per append.
        val seenIds: ImmutableSet<String> = persistentSetOf(),
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
        // Categorized search hits (contract v20) for the search-panel tabs; null when the
        // source lacks the capability or no query is active.
        val searchSuggestions: SearchSuggestions? = null,
        // Headers for the player's HTTP data source, captured from the current source.
        val sourceHeaders: ImmutableMap<String, String> = persistentHashMapOf(),
        val filters: AnimeFilterList = AnimeFilterList(),
        val isFilterDialogOpen: Boolean = false,
        val isSearchBarOpen: Boolean = false,
        val isSourcePickerOpen: Boolean = false,
        val isLoginDialogOpen: Boolean = false,
        // Web login (contract v20): the current source implements AnimeFeedWebLoginSource;
        // the account hub routes login to the WebView dialog instead of the password dialog.
        val isWebLoginCapable: Boolean = false,
        val isWebLoginDialogOpen: Boolean = false,
        // Transient hint inside the WebView dialog: the last import attempt found no session.
        val webLoginHint: Boolean = false,
        // Stage-2 counter (contract v20): bumped by the Done button when the SPA import found
        // no session; the dialog then loads the source's own PKCE authorize URL.
        val webLoginStage2Attempt: Int = 0,
        val error: String? = null,
        // Transient append failure while the feed is non-empty; surfaced as a snackbar.
        val pageError: String? = null,
        // Session-scoped "tap to unmute" pill: visible while the user has not decided sound.
        val showUnmuteHint: Boolean = false,
    )
}
