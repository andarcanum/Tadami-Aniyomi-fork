package eu.kanade.tachiyomi.ui.reels

import android.net.Uri
import android.os.SystemClock
import android.webkit.CookieManager
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeAlbumSearchSource
import eu.kanade.tachiyomi.animesource.AnimeBlockedTagsSource
import eu.kanade.tachiyomi.animesource.AnimeCategorizedSearchSource
import eu.kanade.tachiyomi.animesource.AnimeCategoryFeedOrderSource
import eu.kanade.tachiyomi.animesource.AnimeCategorySubscriptionSource
import eu.kanade.tachiyomi.animesource.AnimeContentPreferencesSource
import eu.kanade.tachiyomi.animesource.AnimeCreatorFeedSource
import eu.kanade.tachiyomi.animesource.AnimeCustomFeedSource
import eu.kanade.tachiyomi.animesource.AnimeFeedAccountIdentitySource
import eu.kanade.tachiyomi.animesource.AnimeFeedBrowseSource
import eu.kanade.tachiyomi.animesource.AnimeFeedErrorClassifierSource
import eu.kanade.tachiyomi.animesource.AnimeFeedLoginInstrumentationSource
import eu.kanade.tachiyomi.animesource.AnimeFeedLoginSource
import eu.kanade.tachiyomi.animesource.AnimeFeedMediaDescriptorSource
import eu.kanade.tachiyomi.animesource.AnimeFeedOriginScopedSessionSource
import eu.kanade.tachiyomi.animesource.AnimeFeedReplaySource
import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.animesource.AnimeFeedVideoResolverSource
import eu.kanade.tachiyomi.animesource.AnimeFeedWebLoginSource
import eu.kanade.tachiyomi.animesource.AnimeReelsFeedbackSource
import eu.kanade.tachiyomi.animesource.AnimeSearchHintsSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.ContentPreferenceOption
import eu.kanade.tachiyomi.animesource.model.CustomFeedRef
import eu.kanade.tachiyomi.animesource.model.FeedCategory
import eu.kanade.tachiyomi.animesource.model.FeedError
import eu.kanade.tachiyomi.animesource.model.FeedMediaDescriptor
import eu.kanade.tachiyomi.animesource.model.FeedPage
import eu.kanade.tachiyomi.animesource.model.FeedReplayContext
import eu.kanade.tachiyomi.animesource.model.FeedReplayItemResult
import eu.kanade.tachiyomi.animesource.model.SearchSuggestions
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import eu.kanade.tachiyomi.animesource.model.WebLoginCookie
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.ui.reels.components.legacyCookieMap
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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.reels.anime.model.ReelsAlbum
import tachiyomi.domain.reels.anime.model.ReelsCandidate
import tachiyomi.domain.reels.anime.model.ReelsFavorite
import tachiyomi.domain.reels.anime.model.ReelsFollow
import tachiyomi.domain.reels.anime.model.ReelsHiddenEntry
import tachiyomi.domain.reels.anime.model.ReelsSessionKey
import tachiyomi.domain.reels.anime.model.ReelsWatchEntry
import tachiyomi.domain.reels.anime.repository.ReelsAlbumRepository
import tachiyomi.domain.reels.anime.repository.ReelsCandidateRepository
import tachiyomi.domain.reels.anime.repository.ReelsConsumptionRepository
import tachiyomi.domain.reels.anime.repository.ReelsFavoriteRepository
import tachiyomi.domain.reels.anime.repository.ReelsFollowRepository
import tachiyomi.domain.reels.anime.repository.ReelsHiddenRepository
import tachiyomi.domain.reels.anime.repository.ReelsSessionRepository
import tachiyomi.domain.reels.anime.repository.ReelsWatchRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

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
    // Offline playlist mode (opened from the Favorites screen): the playlist is reloaded
    // from the favorites DB; [playlistSort] mirrors the Favorites screen order and
    // [initialVideoId] seeks to the tapped video.
    private val offlinePlaylist: Boolean = false,
    private val playlistSort: FavoritesSort = FavoritesSort.DateDesc,
    private val initialVideoId: String? = null,
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
    // B3.1: when set, the fresh feed resumes this clip at the stored history position (best
    // effort — the clip must appear among the loaded pages).
    private val resumeVideoId: String? = null,
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    // Injectable for tests; resolves the reels-only incognito toggle by default. Product
    // decision: the global incognito switch, the NSFW auto policy and the per-extension
    // incognito set do NOT apply inside the feed — the reels toggle in the More menu is the
    // single source of truth here (the player/readers keep respecting the global switch).
    private val isIncognito: (Long) -> Boolean = { _ ->
        Injekt.get<SourcePreferences>().reelsIncognitoMode().get()
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
    private val reelsWatchRepository: ReelsWatchRepository = Injekt.get(),
    private val reelsHiddenRepository: ReelsHiddenRepository = Injekt.get(),
    private val reelsAlbumRepository: ReelsAlbumRepository = Injekt.get(),
    private val reelsConsumptionRepository: ReelsConsumptionRepository = Injekt.get(),
    private val reelsCandidateRepository: ReelsCandidateRepository = Injekt.get(),
    private val reelsSessionRepository: ReelsSessionRepository? = null,
    // Injectable monotonic clock (the watch accumulator must never use wall time) and wall
    // clock (the durable summary stores real timestamps).
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallClock: () -> Date = { Date() },
    // Pure watch-qualification state machine; one per screen so a source switch starts clean.
    private val interactionTracker: ReelsInteractionTracker = ReelsInteractionTracker(),
    private val offlineStore: ReelsOfflineStore = Injekt.get(),
    private val sessionSound: ReelsSessionSoundState = sharedSessionSound,
    // Adaptive pager runway; injectable so tests can drive a fake mono clock.
    private val runway: ReelsFeedRunway = ReelsFeedRunway(),
) : StateScreenModel<ReelsFeedScreenModel.State>(
    State(
        currentSourceId = initialSourceId,
        customFeedName = customFeedName,
        nicheName = nicheName,
        resumeVideoId = resumeVideoId,
        isAutoAdvance = sourcePreferences.autoAdvanceReels().get(),
        isCropMode = sourcePreferences.reelsCropMode().get(),
        // Undecided session: always start muted (with the unmute hint); afterwards the
        // persisted preference wins.
        isMuted = if (sessionSound.decided) sourcePreferences.reelsMuted().get() else true,
        isHdQuality = sourcePreferences.reelsHdQuality().get(),
        dataSaverMetered = sourcePreferences.reelsDataSaverMetered().get(),
        preloadEnabled = sourcePreferences.reelsPreloadEnabled().get(),
        preloadWifiOnly = sourcePreferences.reelsPreloadWifiOnly().get(),
        isPipEnabled = sourcePreferences.reelsPipEnabled().get(),
        unseenOnly = sourcePreferences.reelsUnseenOnly().get(),
        showUnmuteHint = !sessionSound.decided,
    ),
) {

    companion object {
        val sharedSessionSound = ReelsSessionSoundState()

        // Fan-out protection for the FOLLOWING aggregation: one page per followed creator,
        // fetched in bounded-concurrency chunks (the follow count itself is uncapped).
        // Audit B1: 6 instead of 12 — every request hits the SAME source, and a 12-wide burst
        // is a self-inflicted 429 on rate-limited backends.
        const val FOLLOWING_FETCH_CONCURRENCY = 6

        // B1 rate-limit backoff: 429 verdicts are parsed from the plugin error text (plugins
        // shape their own messages, so this is best-effort), Retry-After is honored when present.
        const val RATE_LIMIT_MAX_RETRIES = 3
        const val RATE_LIMIT_DEFAULT_RETRY_SEC = 30

        // Debounce window for the categorized-search tabs (contract v20): the request is a
        // network call per query, so the tabs refresh once typing pauses, not per keystroke.
        const val SEARCH_SUGGESTIONS_DEBOUNCE_MS = 300L

        // Ceiling on offscreen Cloudflare/session bootstrap WebView mounts per source entry:
        // a permanently broken 401/403 source must not loop silent reloads without bound.
        const val CF_BOOTSTRAP_MAX_ATTEMPTS = 3

        // Bounded fan-out for the v22 resolver pass on playlist open (see resolveExpiredUrls).
        const val RESOLVE_URL_CONCURRENCY = 6

        // F07: ceiling on the SILENT deep-link seek (a missing target must not drain an
        // endless feed); manual pagination past the budget stays available.
        const val RESUME_SEEK_MAX_PAGES = 10

        // Audit H6: word-boundary status match — a reel id or message that merely CONTAINS
        // "403"/"401" must not mount the bootstrap WebView.
        private val HTTP_SESSION_ERROR_REGEX = Regex("\\b(?:401|403)\\b")

        // B1: rate-limit detection in plugin error text + optional Retry-After seconds.
        private val HTTP_TOO_MANY_REQUESTS_REGEX = Regex("\\b429\\b")
        private val RETRY_AFTER_REGEX = Regex("(?i)retry[-_ ]?after[:= ]+(\\d{1,4})")
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
    // @Volatile: loadFeed is entered from main (search/pagination) AND from IO (login /
    // web-login success) — without it the cancel-then-replace sequence can lose visibility
    // and leave an orphan job running (state stays safe via loadGeneration, but the orphan
    // would burn network for nothing).
    @Volatile
    private var loadJob: Job? = null

    // B1 rate-limit backoff: consecutive auto-retries (capped), reset on any successful page.
    private var rateLimitRetryJob: Job? = null
    private var rateLimitRetries = 0

    // Monotonic token: every loadFeed() call invalidates previously running load jobs,
    // closing the window between ensureActive() and the state write (no suspension there).
    // The pagination cursor (nextPageIndex/canLoadMore) lives in State, so every cursor
    // write goes through the same generation-checked CAS update as the items — a stale job
    // can no longer corrupt the newer feed's cursor.
    private val loadGeneration = AtomicInteger(0)

    // Latest pager position, including transient pages crossed during a fling. The settled
    // page is not enough: a 10-reel fling must already have the next page in flight.
    @Volatile
    private var lastPagerPage = 0

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

    // B3.1 one-shot resume navigation: entering from the watch history must land on the
    // tapped clip, not on the saved source position. Consumed by the first reset load;
    // best effort — the clip has to appear on the first page (fresh feeds are shuffled, so
    // often it won't; then the normal position restore wins and the seek stays armed for
    // whenever the clip does show up).
    private var resumeNavPending = resumeVideoId != null

    // Pages appended by the SILENT seek so far (main-confined). The budget bounds the
    // background fan-out; reaching it leaves the already-loaded feed untouched.
    private var resumeSeekPages = 0

    // sourceId per offline playlist SLOT (index-aligned with the items list): a videoId is only
    // unique PER SOURCE, so a videoId-keyed map would misattribute likes of same-id favorites
    // from different sources. Written from the IO load job, read from main-thread like handlers.
    @Volatile
    private var offlineSourceIds: List<Long> = emptyList()

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

    // B3.2 "not interested" (host-side): per-source hidden video ids / authors loaded on source
    // switch, mutated by the hide actions. Future pages/generations filter them out; already
    // loaded pages stay until the next swipe (standard shorts behavior). An immutable snapshot
    // behind @Volatile keeps the IO-side feed loads race-free.
    @Volatile
    private var hiddenSnapshot: Pair<Set<String>, Set<String>> = emptySet<String>() to emptySet<String>()
    private var lastHide: Triple<String, String, Long>? = null

    // Serializes the hide insert / undo delete DB writes: launched independently on the IO
    // dispatcher, a fast hide→undo could otherwise execute DELETE before INSERT and leave a
    // stale row that re-hides the video after restart.
    private val hiddenWriteMutex = Mutex()

    /** Future pages never serve hidden reels / authors. */
    private fun List<ShortVideoItem>.filterHidden(): List<ShortVideoItem> {
        val (videos, authors) = hiddenSnapshot
        return filterNot { it.id in videos || (it.author != null && it.author in authors) }
    }

    // ---------------------------------------------------------------------------
    // For You consumption history (durable, host-side)
    //
    // "Received", "inserted into the pager", "prefetched" and "decoded" are transport facts
    // and never create history: only a qualified watch or a committed MANUAL forward swipe
    // does. The scope is the source's stable account identity (never a display name or
    // @handle), so a relogin keeps the history while a different account gets its own.
    // ---------------------------------------------------------------------------

    @Volatile
    private var forYouScope: String? = null

    @Volatile
    private var forYouGeneration: Long = 0L

    // Ids whose interaction was already committed in THIS session: duplicate player callbacks
    // and repeated transitions must not issue duplicate writes (the DB upsert is idempotent
    // as well — this only keeps the writer quiet).
    private val watchedCommitted = mutableSetOf<String>()
    private val skippedCommitted = mutableSetOf<String>()

    // Pages fetched by the unseen refill loop in the current feed generation (bounded).
    private var unseenRefillPages = 0

    @Volatile
    private var replayKey: ReelsSessionKey? = null
    private var remoteTailJob: Job? = null

    @Volatile
    private var observedReplayKey: ReelsSessionKey? = null

    private val sessionContinuity by lazy {
        ReelsSessionContinuity(
            scope = screenModelScope,
            ioDispatcher = ioDispatcher,
            repository = reelsSessionRepository ?: Injekt.get(),
            nowMs = nowMs,
            wallClock = wallClock,
            isCurrentKey = { key ->
                replayKey == key && state.value.currentSourceId == key.sourceId &&
                    !isIncognito(key.sourceId) && filtersUnseen(state.value.searchQuery)
            },
            isObservedKey = { key -> observedReplayKey == key && state.value.currentSourceId == key.sourceId },
            onContextInvalidated = { key, changed ->
                screenModelScope.launch(Dispatchers.Main.immediate) {
                    if (observedReplayKey != key || state.value.currentSourceId != key.sourceId) return@launch
                    replayKey = null
                    remoteTailJob?.cancel()
                    loadJob?.cancel()
                    loadGeneration.incrementAndGet()
                    mutableState.update {
                        it.copy(
                            items = persistentListOf(),
                            seenIds = persistentSetOf(),
                            isPlaying = false,
                            canLoadMore = false,
                            replayLocalHasMore = false,
                            isLoading = false,
                            pendingSeekItemId = null,
                            pendingSeekPositionMs = null,
                        )
                    }
                    if (changed != null) source?.let { buildFiltersAndStartFeed(it, key.sourceId) }
                }
            },
        )
    }

    private fun isReplaySurface(): Boolean = source is AnimeFeedReplaySource &&
        filtersUnseen(state.value.searchQuery) && !isIncognito(state.value.currentSourceId)

    private fun invalidateReplay(dropCheckpoint: Boolean = false) {
        if (observedReplayKey == null && replayKey == null) return
        observedReplayKey = null
        replayKey = null
        remoteTailJob?.cancel()
        remoteTailJob = null
        val handle = sessionContinuity.detach()
        if (handle != null) {
            screenModelScope.launch(ioDispatcher) {
                (reelsSessionRepository ?: Injekt.get()).invalidate(handle, dropCheckpoint)
            }
        }
        mutableState.update {
            it.copy(replayLocalHasMore = false, pendingSeekItemId = null, pendingSeekPositionMs = null)
        }
    }

    // Pinned account scope per source. The identity can arrive AFTER the first feed request
    // (TDLib is still authorizing), and a scope that silently follows it would strand every
    // watched/skipped row written a second earlier under the fallback scope — exactly the
    // reels that then come back. The first PROVEN identity wins for this model's lifetime.
    private var pinnedScope: Pair<Long, String>? = null

    private fun accountScopeFor(sourceId: Long): String {
        // A proven identity, once seen, outranks a later `null` (TDLib restarting, a web
        // session losing its uid): dropping back to the fallback scope would hide the
        // history that is already stored under the account.
        pinnedScope?.let { (pinnedSourceId, scope) -> if (pinnedSourceId == sourceId) return scope }
        // A capability lookup on plugin bytecode: never let a broken extension break history.
        val identity = runCatching {
            (sourceManager.get(sourceId) as? AnimeFeedAccountIdentitySource)?.accountScopeId()
        }.getOrNull()
        val proven = identity?.trim()?.takeIf { it.isNotEmpty() }
            ?: return ReelsForYou.accountScope(sourceId, null)
        val scope = ReelsForYou.accountScope(sourceId, proven)
        pinnedScope = sourceId to scope
        return scope
    }

    /** Scope + history generation of the current account, cached until the scope changes. */
    private suspend fun ensureForYouScope(sourceId: Long): Pair<String, Long> {
        val scope = accountScopeFor(sourceId)
        if (forYouScope == scope) return scope to forYouGeneration
        adoptFallbackHistory(sourceId, scope)
        val generation = reelsConsumptionRepository.ensureScope(scope, ReelsForYou.POLICY_VERSION)
        forYouScope = scope
        forYouGeneration = generation
        return scope to generation
    }

    /**
     * The first generation of a cold start may run before the source can prove an account
     * identity, so its history lands in the fallback `src:` scope. When the real identity
     * appears, that history has to follow the user instead of vanishing: one transactional
     * copy into the account scope, then the fallback rows are dropped.
     *
     * Idempotent and cheap after the first run (the source scope is empty by then), so it is
     * safe to attempt on every scope resolution.
     */
    private suspend fun adoptFallbackHistory(sourceId: Long, scope: String) {
        val fallback = ReelsForYou.fallbackScope(sourceId)
        if (scope == fallback) return
        val previous = forYouScope
        if (previous != null && previous != fallback) return
        runCatching { reelsConsumptionRepository.mergeScope(fallback, scope, sourceId) }
            .onFailure { logcat(LogPriority.WARN, it) { "Reels history merge $fallback -> $scope failed" } }
    }

    /** Diagnostic surface stored with the summary; never used to decide filtering. */
    private fun currentSurface(): ReelsSurface {
        if (state.value.isOffline) return ReelsSurface.PLAYLIST
        if (state.value.searchQuery.isNotBlank()) return ReelsSurface.SEARCH
        return when (mode) {
            FeedMode.GLOBAL -> ReelsSurface.FOR_YOU
            FeedMode.CREATOR -> ReelsSurface.CREATOR
            FeedMode.FOLLOWING -> ReelsSurface.FOLLOWING
            FeedMode.CUSTOM -> ReelsSurface.CUSTOM
            FeedMode.NICHE -> ReelsSurface.NICHE
        }
    }

    /**
     * Only the automatic For You feed hides consumed reels. Explicit surfaces (creator, niche,
     * search, custom feeds, favorites, history) must never go empty because of this history —
     * they may grow an "unwatched only" switch later.
     */
    private fun filtersUnseen(query: String): Boolean {
        return mode == FeedMode.GLOBAL &&
            query.isBlank() &&
            !state.value.isOffline &&
            state.value.unseenOnly &&
            // Entering from the watch history is an explicit "play THIS clip" request: hiding
            // the target would break the resume promise.
            resumeVideoId == null
    }

    /** A page became the active card: a fresh visit starts, the previous one is dropped. */
    fun onVisitStarted(item: ShortVideoItem) {
        if (state.value.isOffline) return
        interactionTracker.startVisit(item.id, item.durationSec?.let { (it * 1000f).toLong() }, nowMs())
        if (isReplaySurface()) sessionContinuity.visitStarted(item, persistEligible = true)
    }

    /** The player presented its first frame for this visit: time may start counting. */
    fun onFirstFramePresented(itemId: String) {
        interactionTracker.onFirstFrame(itemId)
    }

    /**
     * One sample of the active page's player state.
     *
     * [eligible] folds together every condition the product contract requires: the card is the
     * settled one, the screen is in the foreground, the player is really playing (not
     * READY/paused/buffering) and a frame was presented. The summary is committed the moment
     * the threshold is crossed — not on `onStop`, which a process kill can preempt.
     */
    fun onPlaybackSample(itemId: String, positionMs: Long, eligible: Boolean) {
        if (state.value.isOffline) return
        if (isReplaySurface()) sessionContinuity.sample(itemId, positionMs, foreground = eligible)
        val event = interactionTracker.onSample(itemId, nowMs(), positionMs, eligible) ?: return
        commitWatched(event.itemId)
    }

    /** Foreground loss: persist the latest position of the active visit before it can be lost. */
    fun onForegroundLost() {
        if (isReplaySurface()) sessionContinuity.flushActiveVisit()
    }

    fun onVideoCompleted(itemId: String) {
        if (isReplaySurface()) sessionContinuity.videoCompleted(itemId)
    }

    fun onSeekPositionConsumed(itemId: String) {
        if (state.value.pendingSeekItemId == itemId) {
            mutableState.update { it.copy(pendingSeekItemId = null, pendingSeekPositionMs = null) }
        }
    }

    /**
     * The active card left the screen. Persists the resume position and the legacy history
     * row; guarantees consumption is committed if not already done.
     */
    fun onVisitEnded(item: ShortVideoItem, positionMs: Long, positionFraction: Float, durationSec: Float) {
        if (isReplaySurface()) sessionContinuity.positionBoundary(item.id, positionMs)
        val snapshot = interactionTracker.endVisit(item.id, nowMs(), positionMs)
        if (snapshot == null) return
        // Remote personalization (opt-in capability) reports the REAL foreground playback the
        // tracker measured, not the wall time the card spent on screen.
        reportVideoView(item.id, snapshot.watchedMs / 1000f, durationSec)
        recordWatchHistory(item, positionFraction, durationSec)
        if (state.value.isOffline) return
        val sourceId = state.value.currentSourceId
        if (isIncognito(sourceId)) return

        // Guarantee consumption on visit end: if real playback reached the threshold count as watched,
        // otherwise count as skipped (e.g. swiped away or screen closed before threshold).
        if (snapshot.qualified || snapshot.watchedMs >= ReelsForYou.WATCH_THRESHOLD_MS) {
            commitWatched(item.id)
        } else {
            commitSkipped(item.id)
        }

        if (positionMs <= 0L) return
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            val (scope, _) = ensureForYouScope(sourceId)
            reelsCandidateRepository.saveResumePosition(scope, sourceId, item.id, positionMs)
        }
    }

    private fun commitWatched(itemId: String) {
        val sourceId = state.value.currentSourceId
        // Incognito means "do not persist what I watch" — the For You history included.
        if (isIncognito(sourceId)) return
        if (!watchedCommitted.add(itemId)) return
        val surface = currentSurface().key
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            val (scope, generation) = ensureForYouScope(sourceId)
            val at = wallClock()
            var ok = reelsConsumptionRepository.markWatched(
                scope,
                sourceId,
                itemId,
                at,
                surface,
                ReelsForYou.POLICY_VERSION,
                generation,
            )
            // A transient DB failure gets one retry; a stale generation (history reset while
            // the write was in flight) must NOT be retried — that is exactly the row the
            // reset deleted.
            if (!ok && reelsConsumptionRepository.generation(scope) == generation) {
                ok = reelsConsumptionRepository.markWatched(
                    scope,
                    sourceId,
                    itemId,
                    at,
                    surface,
                    ReelsForYou.POLICY_VERSION,
                    generation,
                )
            }
            if (ok) {
                reelsCandidateRepository.remove(scope, sourceId, listOf(itemId))
            } else {
                logcat(LogPriority.WARN) { "Reels watch for $itemId was not persisted (generation $generation)" }
            }
        }
    }

    private fun commitSkipped(itemId: String) {
        if (state.value.isOffline) return
        val sourceId = state.value.currentSourceId
        if (isIncognito(sourceId)) return
        if (watchedCommitted.contains(itemId)) return
        if (!skippedCommitted.add(itemId)) return
        val surface = currentSurface().key
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            val (scope, generation) = ensureForYouScope(sourceId)
            val ok = reelsConsumptionRepository.markSkipped(
                scope,
                sourceId,
                itemId,
                wallClock(),
                surface,
                ReelsForYou.POLICY_VERSION,
                generation,
            )
            if (ok) {
                reelsCandidateRepository.remove(scope, sourceId, listOf(itemId))
            } else {
                logcat(LogPriority.WARN) { "Reels skip for $itemId was not persisted (generation $generation)" }
            }
        }
    }

    /** "Only unwatched in For You": OFF is the explicit show-watched mode, not a history wipe. */
    fun setUnseenOnly(enabled: Boolean) {
        if (state.value.unseenOnly == enabled) return
        persistUnlessIncognito(state.value.currentSourceId) {
            sourcePreferences.reelsUnseenOnly().set(enabled)
        }
        watchedCommitted.clear()
        skippedCommitted.clear()
        mutableState.update { it.copy(unseenOnly = enabled) }
        loadFeed(reset = true)
    }

    /**
     * "Reset For You history" — bumps the history generation (fencing writers that are still
     * in flight) and drops the summary. Explicit hidden/blacklist entries live in their own
     * table and survive by design.
     */
    fun resetForYouHistory() {
        invalidateReplay(dropCheckpoint = true)
        val sourceId = state.value.currentSourceId
        watchedCommitted.clear()
        skippedCommitted.clear()
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            val scope = accountScopeFor(sourceId)
            val generation = reelsConsumptionRepository.resetHistory(scope, ReelsForYou.POLICY_VERSION)
            forYouScope = scope
            forYouGeneration = generation
            withContext(Dispatchers.Main.immediate) { loadFeed(reset = true) }
        }
    }

    private suspend fun persistForYouPage(scope: String, sourceId: Long, items: List<ShortVideoItem>) {
        if (items.isEmpty() || isIncognito(sourceId)) return
        val now = wallClock()
        val candidates = items.map { it.toCandidate(scope, sourceId, now) }
        if (isReplaySurface() && sessionContinuity.activeHandle?.key?.accountScopeId == scope) {
            val contextKey = sessionContinuity.activeHandle?.key?.contextKey
            sessionContinuity.persistPage(
                candidates.map {
                    it.copy(
                        videoUrl = "",
                        videoUrlHd = null,
                        posterUrl = "",
                        posterUrlVertical = null,
                        capturedContextKey = contextKey,
                    )
                },
            )
        } else {
            reelsCandidateRepository.ingest(scope, sourceId, candidates)
        }
    }

    private fun ShortVideoItem.toCandidate(scope: String, sourceId: Long, now: Date): ReelsCandidate {
        return ReelsCandidate(
            accountScopeId = scope,
            sourceId = sourceId,
            itemId = id,
            title = title,
            author = author,
            videoUrl = videoUrl,
            videoUrlHd = videoUrlHd,
            posterUrl = posterUrl,
            posterUrlVertical = posterUrlVertical,
            webUrl = webUrl,
            durationSec = durationSec?.toDouble(),
            hasAudio = hasAudio,
            createdAtEpochSec = createdAtEpochSec,
            discoveredAt = now,
            lastValidatedAt = now,
            availability = ReelsCandidate.AVAILABILITY_AVAILABLE,
            retryAfter = null,
            resumePositionMs = null,
            metadataSchemaVersion = ReelsForYou.METADATA_SCHEMA_VERSION,
            tagsJson = Json.encodeToString(tags),
            viewsCount = viewsCount, likesCount = likesCount, isAuthorVerified = isAuthorVerified,
        )
    }

    private fun ReelsCandidate.toShortVideoItem(): ShortVideoItem {
        return ShortVideoItem(
            id = itemId,
            title = title,
            author = author,
            videoUrl = videoUrl,
            videoUrlHd = videoUrlHd,
            posterUrl = posterUrl.orEmpty(),
            posterUrlVertical = posterUrlVertical,
            durationSec = durationSec?.toFloat(),
            hasAudio = hasAudio,
            tags = runCatching { Json.decodeFromString<List<String>>(tagsJson) }.getOrDefault(emptyList()),
            viewsCount = viewsCount, likesCount = likesCount, isAuthorVerified = isAuthorVerified,
            webUrl = webUrl,
            createdAtEpochSec = createdAtEpochSec,
        )
    }

    init {
        // Reactive albums-collection marks (report fix): saves from any screen (search cards,
        // album screen, niche row) update the saved set everywhere without manual reloads.
        startSavedAlbumsSubscription()
        if (offlinePlaylist) {
            mutableState.update { it.copy(isOffline = true, isLoading = true) }
            screenModelScope.launch(ioDispatcher) {
                val all = reelsFavoriteRepository.getAll()
                val favorites = when (playlistSort) {
                    FavoritesSort.DateDesc -> all.sortedByDescending { it.addedAt }
                    FavoritesSort.DateAsc -> all.sortedBy { it.addedAt }
                    FavoritesSort.Source -> all.sortedBy { it.sourceId }
                }
                val hiddenVideosBySource = all.map { it.sourceId }.distinct().associateWith { sourceId ->
                    runCatching { reelsHiddenRepository.getBySource(sourceId) }
                        .getOrDefault(emptyList())
                        .filter { it.kind == ReelsHiddenEntry.KIND_VIDEO }
                        .map { it.value }
                        .toSet()
                }
                val playable = favorites.filterNot { fav ->
                    // Favorites are EXPLICIT likes: only a per-VIDEO hide removes them from the
                    // playlist. An author-hide is a feed-discovery decision — silently emptying
                    // the user's own collection (and skipping the tapped card) would be a trap.
                    fav.videoId in (hiddenVideosBySource[fav.sourceId] ?: emptySet())
                }
                // B3.3: locally stored copies win over network URLs; the pair set feeds the
                // per-item download badge and the toggle action.
                val stored = offlineStore.storedPairs()
                val storedPlayable = playable.map { fav ->
                    if (fav.sourceId to fav.videoId in stored) {
                        val local = offlineStore.localUrl(fav.sourceId, fav.videoId)
                        if (local != null) fav.copy(videoUrl = local, videoUrlHd = null) else fav
                    } else {
                        fav
                    }
                }
                // B4 (audit H10): a watch-history tap seeds the playlist with the tapped clip
                // itself (favorite or not) — the resume promise must actually play THAT reel.
                val localPlayable = ReelsPlaybackSeed.consume()?.let { seed ->
                    listOf(seed) + storedPlayable.filterNot {
                        it.videoId == seed.videoId && it.sourceId == seed.sourceId
                    }
                } ?: storedPlayable
                offlineSourceIds = localPlayable.map { it.sourceId }
                // The tapped favorite is identified by BOTH its source and video id: the same
                // videoId may exist in several sources of the merged playlist.
                val requestedIndex = localPlayable.indexOfFirst {
                    it.videoId == initialVideoId && it.sourceId == initialSourceId
                }.takeIf { it >= 0 } ?: if (initialVideoId == null) {
                    -1
                } else {
                    localPlayable.indexOfFirst { it.videoId == initialVideoId }
                }
                if (requestedIndex < 0) {
                    // The tapped video vanished between screens (unliked elsewhere / DB edit):
                    // start the playlist at the top instead of at an arbitrary position.
                    logcat(LogPriority.WARN) { "Offline playlist video $initialVideoId not found; starting at top" }
                }
                val startIndex = requestedIndex.coerceAtLeast(0)
                // The playlist is published IMMEDIATELY with the stored URLs: the contract-v22
                // resolver pass is a network fan-out and must never gate the first frame.
                mutableState.update {
                    it.copy(
                        isOffline = true,
                        items = localPlayable.map { fav -> fav.toShortVideoItem() }.toImmutableList(),
                        offlineSourceIds = offlineSourceIds.toImmutableList(),
                        offlineStored = stored.map { "${it.first}:${it.second}" }.toImmutableSet(),
                        // Append/dedupe bookkeeping: the offline playlist never appends.
                        seenIds = localPlayable.map { it.videoId }.toImmutableSet(),
                        // Liked state is per (sourceId, videoId) here: a raw videoId would make
                        // same-id favorites from two sources share one heart.
                        likedIds = localPlayable.map { "${it.sourceId}:${it.videoId}" }.toImmutableSet(),
                        isLoading = false,
                        feedGeneration = 1,
                        targetPageIndex = startIndex,
                        canLoadMore = false,
                    )
                }
                // Contract v22 (background): refresh stored CDN links that may have expired.
                // Locally stored copies need no resolution. The clip at the swap-time active
                // index keeps its URL (swapping would rebuild its player mid-playback); a stale
                // URL there surfaces as a playback error, whose retry path re-resolves on demand
                // via [refreshOfflineUrl].
                val needsResolve = localPlayable.filterNot { fav -> fav.sourceId to fav.videoId in stored }
                if (needsResolve.isNotEmpty()) {
                    val resolved = resolveExpiredUrls(needsResolve)
                    val byKey = resolved.associateBy { it.sourceId to it.videoId }
                    mutableState.update { current ->
                        if (!current.isOffline) return@update current
                        current.copy(
                            items = current.items.mapIndexed { index, item ->
                                if (index == current.activeIndex) return@mapIndexed item
                                val fav = byKey[offlineSourceIds.getOrNull(index) to item.id]
                                    ?: return@mapIndexed item
                                if (fav.videoUrl == item.videoUrl) {
                                    item
                                } else {
                                    item.copy(videoUrl = fav.videoUrl, videoUrlHd = fav.videoUrlHd)
                                }
                            }.toImmutableList(),
                        )
                    }
                }
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
            if (resumeVideoId != null) {
                launchResumeLookup(resumeVideoId, initialSourceId)
            }
        }
    }

    // Browsing history of reels (last source, queries, filters) must not be persisted while
    // incognito is on for the source (global mode, NSFW policy or the per-extension set).
    private inline fun persistUnlessIncognito(sourceId: Long, block: () -> Unit) {
        if (!isIncognito(sourceId)) block()
    }

    fun switchSource(newSourceId: Long) {
        if (state.value.isOffline) return
        invalidateReplay()
        val previousSourceId = state.value.currentSourceId
        val rawSource = sourceManager.get(newSourceId)
        if (rawSource is AnimeFeedSource) {
            val creatorCapable = rawSource is AnimeCreatorFeedSource
            val loginCapable = rawSource is AnimeFeedLoginSource
            val customFeedCapable = rawSource is AnimeCustomFeedSource
            val webLoginCapable = rawSource is AnimeFeedWebLoginSource
            val browseCapable = rawSource is AnimeFeedBrowseSource
            val contentPrefsCapable = rawSource is AnimeContentPreferencesSource
            val categoryOrderCapable = rawSource is AnimeCategoryFeedOrderSource
            val blockedTagsCapable = rawSource is AnimeBlockedTagsSource
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
                        errorRes = when (mode) {
                            FeedMode.CUSTOM -> MR.strings.reels_source_missing_custom_feeds
                            FeedMode.NICHE -> MR.strings.reels_source_missing_category_feeds
                            else -> MR.strings.reels_source_missing_creator_feeds
                        },
                        // A stale transport error must not outlive the capability verdict.
                        error = null,
                        isLoading = false,
                        isSourcePickerOpen = false,
                    )
                }
                return
            }
            source = rawSource
            rateLimitRetryJob?.cancel()
            rateLimitRetries = 0
            persistUnlessIncognito(newSourceId) { sourcePreferences.lastUsedReelsSource().set(newSourceId) }
            // Device report: under an incognito policy the disk write above is skipped and the
            // entry point fell back to the first installed source. The session holder keeps the
            // choice for re-entries within this process without a disk trace.
            ReelsSessionSource.lastSourceId = newSourceId
            baseItems = persistentListOf()
            baseNextPageIndex = 1
            baseNextCursor = null
            baseCursorMode = false
            baseCanLoadMore = true
            basePosition = 0
            decidedIds.clear()
            // Interaction ids are per (source, item): a source switch must not suppress the
            // same id in a different source's history.
            watchedCommitted.clear()
            skippedCommitted.clear()
            unseenRefillPages = 0
            // A manual switch to a DIFFERENT source voids any pending deep-link seek from the
            // entry point; the entry switch itself (same id) keeps it.
            if (newSourceId != previousSourceId) resumeNavPending = false
            decidedFollows.clear()
            followingStreams = emptyList()
            hiddenSnapshot = emptySet<String>() to emptySet<String>()
            lastHide = null
            // Every feed mode restores its own last browsing position (B3.1): GLOBAL keeps the
            // plain per-source key, the other modes get a mode-scoped suffix.
            pendingRestorePosition =
                sourcePreferences.lastReelsPosition(newSourceId, positionSuffix).get().coerceAtLeast(0)
            restorePositionPending = true
            // The creator page and the FOLLOWING aggregation have no search/filter surface
            // and must never touch the saved global query or filters.
            val savedQuery = if (mode == FeedMode.GLOBAL) sourcePreferences.lastReelsQuery(newSourceId).get() else ""

            // The filter list is built and restored off the main thread by
            // buildFiltersAndStartFeed (arbitrary plugin code behind getFilterList); the
            // state carries a placeholder list until the real one replaces it.

            mutableState.update {
                it.copy(
                    currentSourceId = newSourceId,
                    sourceName = rawSource.name,
                    supportsTags = rawSource.supportsTags,
                    // Search hints (v19 addendum) are per source: drop the previous list;
                    // loadSearchHints below refills it when the capability exists.
                    searchHints = persistentListOf(),
                    searchQuery = savedQuery,
                    albumSearchCapable = rawSource is AnimeAlbumSearchSource,
                    albumSearchResults = persistentListOf(),
                    albumSearchNextPage = 1,
                    albumSearchNextCursor = null,
                    albumSearchCursorMode = false,
                    albumSearchCanLoadMore = true,
                    previewAlbumId = null,
                    previewItems = persistentListOf(),
                    previewLoading = false,
                    // Categorized-search tabs (v20) belong to the previous source's query.
                    searchSuggestions = null,
                    // Placeholder: the real filter list replaces it from buildFiltersAndStartFeed.
                    filters = AnimeFilterList(),
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
                    loggedInAccount = null,
                    isLoggingIn = false,
                    loginError = null,
                    loginRejected = false,
                    // Web login (v20): per source, reset on switch.
                    isWebLoginCapable = webLoginCapable,
                    isWebLoginDialogOpen = false,
                    webLoginHint = false,
                    webLoginStage2Attempt = 0,
                    // Bootstrap budget is per source entry: switching sources re-arms it.
                    cfBootstrapAttempt = 0,
                    cfBootstrapExhausted = false,
                    webLoginPendingClose = false,
                    // Custom feeds (v19): per source, reset on switch.
                    isCustomFeedCapable = customFeedCapable,
                    // Category browse (v20): per source, reset on switch.
                    isBrowseCapable = browseCapable,
                    // Category feed ordering (v21): enables the filter sheet in NICHE mode.
                    isCategoryOrderCapable = categoryOrderCapable,
                    // Category subscriptions (v20): per source, reset on switch.
                    isCategorySubscribable = categorySubCapable,
                    isCategoryFollowed = false,
                    // Content preferences (v20): per source, reset on switch.
                    isContentPreferencesCapable = contentPrefsCapable,
                    contentPreferences = null,
                    isContentPreferencesOpen = false,
                    isContentPreferencesLoading = false,
                    contentPreferencesError = null,
                    contentPreferencesSaveFailed = false,
                    // Blocked tags (v20): per source, reset on switch.
                    isBlockedTagsCapable = blockedTagsCapable,
                    blockedTags = null,
                    isBlockedTagsOpen = false,
                    isBlockedTagsLoading = false,
                    blockedTagsError = null,
                    blockedTagsSaveFailed = false,
                    customFeeds = persistentListOf(),
                    isCustomFeedsOpen = false,
                    isCustomFeedsLoading = false,
                    customFeedsError = null,
                    isSourcePickerOpen = false,
                    error = null,
                    errorRes = null,
                    errorCounts = null,
                    retryAfterSec = 0,
                )
            }
            loadPersistedFavorites(newSourceId)
            loadPersistedFollows(newSourceId)
            loadPersistedHidden(newSourceId)
            loadSearchHints()
            if (mode == FeedMode.NICHE) loadSubscribedCategoryState()
            buildFiltersAndStartFeed(rawSource, newSourceId)
        } else {
            mutableState.update {
                it.copy(
                    error = null,
                    errorRes = MR.strings.reels_source_not_feed,
                    isLoading = false,
                    isSourcePickerOpen = false,
                )
            }
        }
    }

    /**
     * Builds and restores the initial filter list off the main thread (arbitrary plugin code
     * behind [AnimeFeedSource.getFilterList] must not run there), then starts the first feed
     * generation back on the main dispatcher. Skipped when the user already switched away.
     */
    private fun buildFiltersAndStartFeed(rawSource: AnimeFeedSource, sourceId: Long, resetReplay: Boolean = false) {
        invalidateReplay()
        loadJob?.cancel()
        val entryGeneration = loadGeneration.incrementAndGet()
        loadJob = screenModelScope.launch(ioDispatcher) {
            val account = (rawSource as? AnimeFeedLoginSource)
                ?.takeIf { it.isLoggedIn() }
                ?.loggedInAccount()

            val filters = when (mode) {
                FeedMode.GLOBAL -> rawSource.getFilterList()
                FeedMode.NICHE -> (rawSource as? AnimeCategoryFeedOrderSource)?.categoryFilters()
                    ?: AnimeFilterList()
                else -> AnimeFilterList()
            }
            if (mode == FeedMode.GLOBAL) {
                restoreFilters(filters, sourcePreferences.lastReelsFilter(sourceId).get())
            }

            var restoredSession: RestoredReelsSession? = null
            if (mode == FeedMode.GLOBAL && rawSource is AnimeFeedReplaySource &&
                filtersUnseen(state.value.searchQuery) && !isIncognito(sourceId)
            ) {
                val replayContext = rawSource.prepareReplay()
                if (replayContext != null) {
                    val frozen = ReelsSessionContext.freezeFilters(rawSource, filters)
                    val contextKey = ReelsSessionContext.computeContextKey(
                        sourceId = sourceId,
                        route = "main",
                        query = "",
                        unseenOnly = filtersUnseen(""),
                        filterFingerprint = frozen.fingerprint,
                        continuityKey = replayContext.continuityKey,
                    )
                    val (accountScope, historyGen) = ensureForYouScope(sourceId)
                    val key = ReelsSessionKey(
                        accountScopeId = accountScope,
                        sourceId = sourceId,
                        contextKey = contextKey,
                    )
                    ensureActive()
                    if (loadGeneration.get() != entryGeneration ||
                        state.value.currentSourceId != sourceId
                    ) {
                        return@launch
                    }
                    observedReplayKey = key
                    replayKey = key
                    restoredSession = sessionContinuity.restore(
                        source = rawSource,
                        key = key,
                        context = replayContext,
                        filters = frozen.filters,
                        historyGeneration = historyGen,
                        reset = resetReplay,
                    )?.let { restored ->
                        // Last line of defence before the queue becomes visible content.
                        // The stored window already anti-joins the consumption summary, but
                        // the summary can move (a scope merge, a write that landed after the
                        // read) — and a restored reel the user already watched or swiped past
                        // must never be drawn again. Cheap: one batch query per restore.
                        val ids = restored.initialItems.map { it.id }
                        val consumed = if (ids.isEmpty()) {
                            emptySet()
                        } else {
                            reelsConsumptionRepository.consumedIds(accountScope, sourceId, ids)
                        }
                        if (consumed.isEmpty()) {
                            restored
                        } else {
                            val kept = restored.initialItems.filterNot { it.id in consumed }
                            val seekId = restored.seekItemId?.takeIf { it !in consumed }
                            restored.copy(
                                initialItems = kept,
                                seekItemId = seekId,
                                seekPositionMs = if (seekId == null) null else restored.seekPositionMs,
                            )
                        }
                    }
                }
            }

            withContext(Dispatchers.Main.immediate) {
                if (state.value.currentSourceId != sourceId ||
                    loadGeneration.get() != entryGeneration
                ) {
                    return@withContext
                }
                mutableState.update {
                    it.copy(
                        filters = filters,
                        loggedInAccount = account,
                    )
                }
                if (restoredSession != null && restoredSession.initialItems.isNotEmpty()) {
                    applyRestoredSession(restoredSession)
                } else {
                    loadFeedInternal(reset = true)
                }
            }
        }
    }

    private fun applyRestoredSession(restored: RestoredReelsSession) {
        val initialItems = restored.initialItems
        val seekTargetIndex = if (restored.seekItemId != null) {
            initialItems.indexOfFirst { it.id == restored.seekItemId }.coerceAtLeast(0)
        } else {
            0
        }

        mutableState.update { current ->
            current.copy(
                items = initialItems.toImmutableList(),
                seenIds = initialItems.map { it.id }.toImmutableSet(),
                isLoading = false,
                canLoadMore = true,
                feedGeneration = current.feedGeneration + 1,
                targetPageIndex = seekTargetIndex,
                activeIndex = seekTargetIndex,
                pendingSeekItemId = restored.seekItemId,
                pendingSeekPositionMs = restored.seekPositionMs,
                replayLocalHasMore = restored.localHasMore,
            )
        }

        initialItems.getOrNull(seekTargetIndex)?.let {
            sessionContinuity.visitStarted(it, persistEligible = true)
        }

        val preparedKey = replayKey
        remoteTailJob?.cancel()
        remoteTailJob = screenModelScope.launch(ioDispatcher) {
            delay(1000L)
            if (preparedKey != null && replayKey == preparedKey) loadRemoteTail(reset = true)
        }
    }

    private fun loadRemoteTail(reset: Boolean) {
        if (state.value.isOffline || !isReplaySurface()) return
        val requestedKey = replayKey ?: return
        val src = source ?: return
        val snapshot = state.value
        val sourceIdAtStart = snapshot.currentSourceId
        val filters = sessionContinuity.frozenFilters() ?: return
        val page = if (reset) 1 else snapshot.nextPageIndex
        val cursor = if (reset) null else (if (snapshot.cursorMode) snapshot.nextCursor else null)

        remoteTailJob = screenModelScope.launch(ioDispatcher) {
            try {
                val pageData = src.getFeed(page, cursor, filters)
                ensureActive()
                if (replayKey != requestedKey || !isReplaySurface()) return@launch

                val rawIncoming = pageData.videos.distinctBy { it.id }.filterHidden()
                val (scope, _) = ensureForYouScope(sourceIdAtStart)
                val consumed = reelsConsumptionRepository.consumedIds(scope, sourceIdAtStart, rawIncoming.map { it.id })
                val incoming = if (consumed.isEmpty()) rawIncoming else rawIncoming.filterNot { it.id in consumed }

                if (incoming.isNotEmpty()) {
                    persistForYouPage(scope, sourceIdAtStart, incoming)
                }

                val step = ReelsPagination(
                    nextPage = page,
                    nextCursor = cursor,
                    cursorMode = snapshot.cursorMode,
                    canLoadMore = snapshot.canLoadMore,
                ).advance(pageData.hasNextPage, pageData.nextCursor)

                withContext(Dispatchers.Main.immediate) {
                    if (state.value.currentSourceId != sourceIdAtStart || replayKey != requestedKey) return@withContext
                    mutableState.update { current ->
                        current.copy(
                            canLoadMore = step.pagination.canLoadMore,
                            nextPageIndex = step.pagination.nextPage,
                            nextCursor = step.pagination.nextCursor,
                            cursorMode = step.pagination.cursorMode,
                            replayLocalHasMore = current.replayLocalHasMore || incoming.isNotEmpty(),
                        )
                    }
                }
            } catch (e: CancellationException) {
                // Cancelled
            } catch (t: Throwable) {
                logcat(LogPriority.WARN, t) { "Background tail load failed for source $sourceIdAtStart" }
            }
        }
    }

    private fun loadLocalReplayWindow(): Boolean {
        val snapshot = state.value
        if (!isReplaySurface() || !snapshot.replayLocalHasMore || snapshot.isLoading) return false
        val generation = loadGeneration.incrementAndGet()
        loadJob?.cancel()
        mutableState.update { it.copy(isLoading = true) }
        loadJob = screenModelScope.launch(ioDispatcher) {
            try {
                val window = sessionContinuity.loadNextWindow()
                ensureActive()
                withContext(Dispatchers.Main.immediate) {
                    if (loadGeneration.get() != generation ||
                        state.value.currentSourceId != snapshot.currentSourceId
                    ) {
                        return@withContext
                    }
                    mutableState.update { current ->
                        val added = window.entries.map { it.candidate.toShortVideoItem() }.filterHidden()
                            .filterNot { it.id in current.seenIds }
                        current.copy(
                            items = (current.items + added).toImmutableList(),
                            seenIds = (current.seenIds + added.map { it.id }).toImmutableSet(),
                            replayLocalHasMore = window.hasMore,
                            isLoading = false,
                            pageError = null,
                        )
                    }
                    if (window.entries.isEmpty() && window.hasMore) loadLocalReplayWindow()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (loadGeneration.get() == generation) {
                    mutableState.update {
                        it.copy(isLoading = false, pageError = t.localizedMessage.orEmpty())
                    }
                }
            }
        }
        return true
    }

    fun loadFeed(reset: Boolean = false) {
        if (reset) {
            val replaySource = source
            if (replaySource != null && isReplaySurface()) {
                buildFiltersAndStartFeed(replaySource, state.value.currentSourceId, resetReplay = true)
                return
            }
            invalidateReplay()
        }
        loadFeedInternal(reset)
    }

    private fun loadFeedInternal(reset: Boolean = false) {
        if (state.value.isOffline) return
        if (!reset && loadLocalReplayWindow()) return
        val src = source ?: return
        if (!reset && state.value.isLoading) return
        // Album-search pagination lives on its own cursor/cap flag (report fix): the feed's
        // canLoadMore must not gate album pages and vice versa.
        val albumSearchMode = state.value.albumSearchCapable && state.value.searchQuery.isNotBlank()
        if (!reset && albumSearchMode && !state.value.albumSearchCanLoadMore) return
        if (!reset && !albumSearchMode && !state.value.canLoadMore) return

        loadJob?.cancel()
        val generation = loadGeneration.incrementAndGet()
        if (reset) {
            runway.reset()
            lastPagerPage = 0
            unseenRefillPages = 0
        }

        // Set synchronously before the coroutine is dispatched: two rapid
        // loadNextPageIfNeeded() calls must not both pass the isLoading guard while the
        // first job is still queued on the IO dispatcher. The reset also clears the cursor
        // here so the stale (now-cancelled) job can never see it.
        mutableState.update { current ->
            if (reset) {
                current.copy(
                    isLoading = true,
                    error = null,
                    errorRes = null,
                    pageError = null,
                    nextPageIndex = 1,
                    nextCursor = null,
                    cursorMode = false,
                    canLoadMore = true,
                    albumSearchResults = persistentListOf(),
                    albumSearchNextPage = 1,
                    albumSearchNextCursor = null,
                    albumSearchCursorMode = false,
                    albumSearchCanLoadMore = true,
                    unseenHidden = 0,
                    unseenScanStopped = false,
                )
            } else {
                current.copy(isLoading = true, error = null, errorRes = null, errorCounts = null)
            }
        }
        // Snapshot the per-request inputs on the calling (main) thread: the IO job below
        // must not read snapshot state after its input sections were reset by a newer load.
        val snapshot = state.value
        val query = snapshot.searchQuery
        val filters = if (replayKey != null) sessionContinuity.frozenFilters() ?: snapshot.filters else snapshot.filters
        val page = snapshot.nextPageIndex
        val sourceIdAtStart = snapshot.currentSourceId
        // Decided once per request: a preference flip mid-flight must not half-filter a page.
        val unseenFilter = filtersUnseen(query)
        // Contract v17: while locked into cursor mode the token is authoritative;
        // in page-int mode the source always receives a null cursor.
        val cursor = if (snapshot.cursorMode) snapshot.nextCursor else null

        // Contract v23: album-oriented sources answer search with a paginated album directory;
        // the screen then renders album cards (open / save / long-press preview) instead of
        // the flat video pager for such queries.
        // Report fix: album search works from ANY mode (niche/album feeds included) whenever
        // the source exposes the v23 capability and a query is active.
        if (query.isNotBlank() && src is AnimeAlbumSearchSource) {
            loadJob = screenModelScope.launch(ioDispatcher) {
                loadAlbumSearch(
                    generation = generation,
                    reset = reset,
                    src = src,
                    query = query,
                )
            }
            return
        }

        val pageAtLoadStart = lastPagerPage
        loadJob = screenModelScope.launch(ioDispatcher) {
            if (mode == FeedMode.FOLLOWING) {
                loadFollowing(generation = generation, reset = reset, src = src)
                return@launch
            }
            runway.onLoadStarted()
            try {
                // The creator page shares the sticky cursor protocol with the global feed,
                // just on its own per-stream token space (contract v18). switchSource has
                // already refused a non-capable source in this mode.
                val pageData = when {
                    mode == FeedMode.CREATOR -> (src as AnimeCreatorFeedSource)
                        .getCreatorFeed(creator.orEmpty(), page, cursor)
                    mode == FeedMode.CUSTOM -> (src as AnimeCustomFeedSource)
                        .getCustomFeed(customFeedId.orEmpty(), page, cursor)
                    mode == FeedMode.NICHE -> {
                        val browse = src as? AnimeFeedBrowseSource
                        // v21: order-capable sources get the NICHE-mode filter sheet's order.
                        when (browse) {
                            is AnimeCategoryFeedOrderSource -> browse.getCategoryFeed(
                                nicheId.orEmpty(),
                                page,
                                cursor,
                                state.value.filters,
                            )
                            else -> browse?.getCategoryFeed(nicheId.orEmpty(), page, cursor)
                        }
                            // Capability vanished between the switchSource guard and this load:
                            // serve an empty terminal page instead of crashing the pager.
                            ?: FeedPage(videos = emptyList(), hasNextPage = false)
                    }
                    query.isNotBlank() -> src.getSearchFeed(page, cursor, query, filters)
                    else -> src.getFeed(page, cursor, filters)
                }
                // Re-check cancellation: the suspend calls above may have completed right
                // before this job was superseded by a reset.
                ensureActive()
                // Generation guard: a newer loadFeed() started after this job's network call
                // returned; writing now would corrupt the newer feed's cursor/items.
                if (loadGeneration.get() != generation) return@launch
                // Any successful page resets the rate-limit retry budget (B1).
                rateLimitRetries = 0

                // Consume the one-shot position restore OUTSIDE the CAS: update lambdas may
                // re-run under contention and must stay side-effect-free.
                val restorePosition = if (reset && restorePositionPending) {
                    restorePositionPending = false
                    pendingRestorePosition
                } else {
                    0
                }
                // One-shot resume navigation (watch-history entry, album grid tap): the tapped
                // clip outranks the saved position. Deep albums keep the clip beyond page 1,
                // so the seek survives across appended pages until found (bounded).
                val resumeTarget = if (resumeNavPending) {
                    val pool = if (reset) {
                        pageData.videos.distinctBy { it.id }.filterHidden()
                    } else {
                        state.value.items + pageData.videos.distinctBy { it.id }.filterHidden()
                    }
                    val hit = pool.indexOfFirst { it.id == resumeVideoId }
                    if (hit >= 0) resumeNavPending = false
                    hit
                } else {
                    -1
                }

                // Stall detection for the seek loop, evaluated BEFORE the CAS below updates
                // seenIds: an append page that contributes nothing new ends the seek instead
                // of looping forever on a broken source.
                val seekStalled = !reset && resumeNavPending &&
                    pageData.videos.distinctBy { it.id }.filterHidden()
                        .all { it.id in state.value.seenIds }

                val itemsBeforeAppend = state.value.items.size

                // --- For You: filter BEFORE the page is published, never after ---
                // Receiving, decoding and prefetching are transport facts: they never create
                // a consumption row. Only an already recorded watch/skip removes a reel here.
                val rawIncoming = pageData.videos.distinctBy { it.id }.filterHidden()
                val forYou = if (unseenFilter) ensureForYouScope(sourceIdAtStart) else null
                val consumed = if (forYou != null && rawIncoming.isNotEmpty()) {
                    reelsConsumptionRepository.consumedIds(
                        forYou.first,
                        sourceIdAtStart,
                        rawIncoming.map { it.id },
                    )
                } else {
                    emptySet()
                }
                val incoming = if (consumed.isEmpty()) rawIncoming else rawIncoming.filterNot { it.id in consumed }
                // Durable inbox: persisted BEFORE the UI publish, so a crash between the two
                // still leaves the received candidates recoverable after reopening.
                if (forYou != null && incoming.isNotEmpty()) {
                    persistForYouPage(forYou.first, sourceIdAtStart, incoming)
                }
                // Restart/rescan recovery: the durable unseen backlog seeds a fresh generation,
                // so reels the user never reached survive a lost plugin cursor. Fresh items
                // keep the head of the feed; the backlog follows them.
                val backlog = if (reset && forYou != null) {
                    reelsCandidateRepository
                        .unconsumed(forYou.first, sourceIdAtStart, ReelsForYou.BACKLOG_SEED_LIMIT)
                        .map { it.toShortVideoItem() }
                } else {
                    emptyList()
                }

                mutableState.update { current ->
                    // Re-check inside the CAS: a reset may have landed between the outer guard
                    // and this update; writing a stale page would corrupt the newer feed.
                    if (loadGeneration.get() != generation) return@update current
                    val newItems = if (reset) {
                        incoming
                    } else {
                        incoming.filterNot { it.id in current.seenIds }
                    }
                    val merged = when {
                        !reset -> current.items + newItems
                        backlog.isEmpty() -> newItems
                        else -> (newItems + backlog).distinctBy { it.id }
                    }
                    // Sticky cursor mode (contract v17), shared with every other host surface:
                    // the first non-null token locks the generation, and losing the token while
                    // more pages are claimed is a protocol violation that stops pagination.
                    val step = ReelsPagination(
                        nextPage = current.nextPageIndex,
                        nextCursor = current.nextCursor,
                        cursorMode = current.cursorMode,
                        canLoadMore = current.canLoadMore,
                    ).advance(pageData.hasNextPage, pageData.nextCursor)
                    if (step.cursorLost) {
                        logcat(LogPriority.WARN) {
                            "Feed ${current.currentSourceId} dropped its cursor mid-feed; stopping pagination (contract v17)."
                        }
                    }
                    current.copy(
                        items = merged.toImmutableList(),
                        seenIds = if (reset) {
                            merged.map { it.id }.toImmutableSet()
                        } else {
                            (current.seenIds + newItems.map { it.id }).toImmutableSet()
                        },
                        isLoading = false,
                        canLoadMore = step.pagination.canLoadMore,
                        nextPageIndex = step.pagination.nextPage,
                        nextCursor = step.pagination.nextCursor,
                        cursorMode = step.pagination.cursorMode,
                        feedGeneration = if (reset || resumeTarget >= 0) {
                            current.feedGeneration + 1
                        } else {
                            current.feedGeneration
                        },
                        // A fresh feed starts at the saved position on source entry, at the top
                        // on search/filter resets; a resume/deep-link target outranks both with
                        // a direct navigation to the tapped clip (found on this page or an
                        // appended seek page); only the clearSearch restore path sets a
                        // non-zero targetPageIndex otherwise.
                        targetPageIndex = if (resumeTarget >= 0) {
                            resumeTarget
                        } else if (reset) {
                            restorePosition
                        } else {
                            current.targetPageIndex
                        },
                        activeIndex = if (reset) 0 else current.activeIndex,
                        // A recovered append must not leave a stale transient error.
                        pageError = null,
                        unseenHidden = if (consumed.isEmpty()) {
                            current.unseenHidden
                        } else {
                            current.unseenHidden + consumed.size
                        },
                    )
                }
                runway.onLoadFinished()
                val itemsAfterAppend = state.value.items.size
                val appended = if (reset) itemsAfterAppend else itemsAfterAppend - itemsBeforeAppend
                // Deep-link seek continuation: the tapped clip was not on this page — append
                // the next page until it surfaces, bounded by RESUME_SEEK_MAX_PAGES so a
                // missing target cannot drain an endless feed. Natural stops: end of feed,
                // a stall page that adds nothing new, or the page budget.
                if (resumeNavPending && state.value.canLoadMore) {
                    if (seekStalled || resumeSeekPages >= RESUME_SEEK_MAX_PAGES) {
                        if (!seekStalled) {
                            logcat(LogPriority.WARN) {
                                "Deep-link target $resumeVideoId not found within " +
                                    "$RESUME_SEEK_MAX_PAGES pages; stopping the silent seek."
                            }
                        }
                        resumeNavPending = false
                    } else {
                        resumeSeekPages++
                        loadFeed(reset = false)
                    }
                } else if (unseenFilter && incoming.isEmpty() && state.value.canLoadMore) {
                    // The whole page was already consumed. Keep scanning — bounded — so a deep
                    // watch history cannot turn For You into a permanent empty screen. Running
                    // out of budget is NOT end-of-feed: the screen says "still scanning".
                    if (unseenRefillPages < ReelsForYou.UNSEEN_REFILL_MAX_PAGES) {
                        unseenRefillPages++
                        loadFeed(reset = false)
                    } else {
                        mutableState.update { it.copy(unseenScanStopped = true) }
                    }
                } else if (appended > 0 && lastPagerPage > pageAtLoadStart) {
                    // The fling crossed at least one page while this page was in flight:
                    // re-evaluate the runway so the next page is already on its way.
                    maybeLoadMoreForRunway()
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
                runway.onLoadAborted()
                val msg = t.localizedMessage.orEmpty()
                // Contract v25: a source-supplied verdict replaces the text heuristics. On an
                // append, an expired cursor only ends this feed session: the usable feed stays,
                // the dead cursor is never retried, and a refresh starts a fresh generation.
                val verdict = classifyFeedError(t)
                if (verdict == FeedError.ExpiredCursor && !reset && state.value.items.isNotEmpty()) {
                    logcat(LogPriority.WARN) {
                        "Feed ${state.value.currentSourceId} cursor expired; stopping pagination (contract v25)."
                    }
                    mutableState.update { it.copy(isLoading = false, canLoadMore = false, pageError = null) }
                    return@launch
                }
                mutableState.update { current ->
                    if (current.items.isEmpty()) {
                        current.copy(isLoading = false, error = t.localizedMessage.orEmpty())
                    } else {
                        // Mid-feed failure: the feed stays usable, the error is surfaced as a
                        // transient snackbar instead of replacing the whole screen.
                        current.copy(isLoading = false, pageError = t.localizedMessage.orEmpty())
                    }
                }
                handleFeedErrorVerdict(verdict, msg, reset)
            }
        }
    }

    /**
     * Samples the live pager position (including transient pages crossed during a fling)
     * and asks the adaptive runway whether the next page should already be in flight.
     */
    fun onPagerPositionChanged(page: Int) {
        if (page < 0) return
        lastPagerPage = page
        runway.onPage(page)
        maybeLoadMoreForRunway()
    }

    fun loadNextPageIfNeeded(visibleIndex: Int) {
        if (mode == FeedMode.FOLLOWING) {
            // Following feeds top up per-creator streams and keep their own merged-tail rule.
            if (visibleIndex >= state.value.items.size - 2 &&
                state.value.canLoadMore &&
                !state.value.isLoading
            ) {
                loadFeed(reset = false)
            }
            return
        }
        if (visibleIndex >= 0) {
            lastPagerPage = visibleIndex
            runway.onPage(visibleIndex)
        }
        maybeLoadMoreForRunway()
    }

    private fun maybeLoadMoreForRunway() {
        val current = state.value
        if (mode == FeedMode.FOLLOWING) return
        if (current.isOffline || current.isLoading || (!current.canLoadMore && !current.replayLocalHasMore) ||
            current.items.isEmpty()
        ) {
            return
        }
        // Album search paginates on its own cursor/cap flag; the reels runway does not apply.
        if (current.albumSearchCapable && current.searchQuery.isNotBlank()) return

        val page = lastPagerPage.coerceIn(0, current.items.lastIndex)
        val decision = runway.evaluate(
            page = page,
            total = current.items.size,
            isLoading = current.isLoading,
            canLoadMore = current.canLoadMore || current.replayLocalHasMore,
        )
        if (!decision.shouldLoad) return

        logcat(LogPriority.DEBUG) {
            "Reels runway: page=$page size=${current.items.size} " +
                "remaining=${decision.pagesRemaining} rate=${"%.2f".format(decision.pagesPerSecond)}/s " +
                "eta=${decision.etaMs}ms"
        }
        loadFeed(reset = false)
    }

    /**
     * Error-state retry (audit H5). With a live source this is a plain reset reload. With a
     * rejected source (non-feed / missing capability → source=null) loadFeed would silently
     * no-op: GLOBAL opens the source picker instead, fixed modes answer false so the screen
     * can fall back to a back-navigation.
     */
    fun retryFromError(): Boolean {
        if (source != null) {
            loadFeed(reset = true)
            return true
        }
        if (mode == FeedMode.GLOBAL) {
            mutableState.update { it.copy(isSourcePickerOpen = true) }
            return true
        }
        return false
    }

    /** Contract v23 album-search stream: accumulates FeedCategory pages on its own cursor. */
    private suspend fun loadAlbumSearch(generation: Int, reset: Boolean, src: AnimeAlbumSearchSource, query: String) {
        try {
            val snapshot = state.value
            // Same sticky reducer as the video feed: the album stream owns its own cursor
            // space, and a null token after the lock is a violation, not a page-int restart.
            val pagination = if (reset) {
                ReelsPagination()
            } else {
                ReelsPagination(
                    nextPage = snapshot.albumSearchNextPage,
                    nextCursor = snapshot.albumSearchNextCursor,
                    cursorMode = snapshot.albumSearchCursorMode,
                    canLoadMore = snapshot.albumSearchCanLoadMore,
                )
            }
            val page = pagination.nextPage
            val cursor = if (pagination.cursorMode) pagination.nextCursor else null
            val pageData = src.searchAlbums(query, page, cursor)
            currentCoroutineContext().ensureActive()
            if (loadGeneration.get() != generation) return
            rateLimitRetries = 0
            mutableState.update { current ->
                // Re-check inside the CAS: a newer query/reset may have landed between the
                // outer guard and this update; a stale album page must not touch its state.
                if (loadGeneration.get() != generation) return@update current
                val step = ReelsPagination(
                    nextPage = current.albumSearchNextPage,
                    nextCursor = current.albumSearchNextCursor,
                    cursorMode = current.albumSearchCursorMode,
                    canLoadMore = current.albumSearchCanLoadMore,
                ).advance(pageData.hasNextPage, pageData.nextCursor)
                if (step.cursorLost) {
                    logcat(LogPriority.WARN) {
                        "Album search for \"$query\" dropped its cursor mid-stream; stopping pagination (contract v17)."
                    }
                }
                val base = if (reset) persistentListOf<FeedCategory>() else current.albumSearchResults
                current.copy(
                    // Duplicate ids would collide in the lazy-grid key space.
                    albumSearchResults = (base + pageData.categories).distinctBy { it.id }.toImmutableList(),
                    albumSearchNextPage = step.pagination.nextPage,
                    albumSearchNextCursor = step.pagination.nextCursor,
                    albumSearchCursorMode = step.pagination.cursorMode,
                    albumSearchCanLoadMore = step.pagination.canLoadMore,
                    isLoading = false,
                    error = null,
                    errorRes = null,
                    errorCounts = null,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            logcat(LogPriority.ERROR, throwable = t) { "Album search failed for query $query" }
            if (loadGeneration.get() != generation) return
            val msg = t.localizedMessage.orEmpty()
            val verdict = classifyFeedError(t)
            if (verdict == FeedError.ExpiredCursor && !reset && state.value.albumSearchResults.isNotEmpty()) {
                // The album cursor expired mid-stream: stop its pagination silently, keeping
                // the albums already on screen (contract v25).
                logcat(LogPriority.WARN) {
                    "Album search for \"$query\" cursor expired; stopping pagination (contract v25)."
                }
                mutableState.update { current ->
                    if (loadGeneration.get() != generation) return@update current
                    current.copy(isLoading = false, albumSearchCanLoadMore = false)
                }
                return
            }
            mutableState.update { current ->
                if (loadGeneration.get() != generation) return@update current
                current.copy(isLoading = false, error = msg)
            }
            handleFeedErrorVerdict(verdict, msg, reset)
        }
    }

    /** Albums-collection marks of the current source (save buttons / niche save row). */
    private fun startSavedAlbumsSubscription() {
        screenModelScope.launch {
            mutableState.map { it.currentSourceId }
                .distinctUntilChanged()
                .flatMapLatest { sourceId -> reelsAlbumRepository.subscribeBySource(sourceId) }
                .collectLatest { albums ->
                    mutableState.update { it.copy(savedAlbumIds = albums.map { a -> a.albumId }.toSet()) }
                }
        }
    }

    /** Adds/removes an album to/from the collection (search cards, niche save row). */
    fun toggleAlbumSaved(category: FeedCategory) {
        val sourceId = state.value.currentSourceId
        screenModelScope.launch(ioDispatcher) {
            if (category.id in state.value.savedAlbumIds) {
                reelsAlbumRepository.delete(sourceId, category.id)
            } else {
                reelsAlbumRepository.insert(
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
    }

    /** Save-row action for NICHE mode: the currently open album/category. */
    fun toggleCurrentNicheAlbum() {
        val id = nicheId ?: return
        // Report fix: keep a cover for the collection row (first reel's poster).
        toggleAlbumSaved(
            FeedCategory(
                id = id,
                name = nicheName.orEmpty(),
                imageUrl = state.value.items.firstOrNull()?.posterUrl,
                itemCount = null,
            ),
        )
    }

    /** Long-press preview of an album's content (first page thumbnails) for search cards. */
    fun loadAlbumPreview(albumId: String) {
        mutableState.update {
            it.copy(previewAlbumId = albumId, previewLoading = true, previewItems = persistentListOf())
        }
        screenModelScope.launch(ioDispatcher) {
            val browse = source as? AnimeFeedBrowseSource
            val items = runCatching { browse?.getCategoryFeed(albumId, 1, null)?.videos.orEmpty() }
                .getOrDefault(emptyList())
                .take(8)
            mutableState.update { current ->
                if (current.previewAlbumId == albumId) {
                    current.copy(previewItems = items.toImmutableList(), previewLoading = false)
                } else {
                    current
                }
            }
        }
    }

    fun closeAlbumPreview() {
        mutableState.update {
            it.copy(previewAlbumId = null, previewItems = persistentListOf(), previewLoading = false)
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
                current.copy(isLoading = false, error = null, errorRes = MR.strings.reels_source_missing_creator_feeds)
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
                            errorRes = null,
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
                // Bounded concurrency: chunked fan-out keeps a huge follow set from opening
                // one socket per creator at once (order of outcomes is preserved).
                requests.chunked(FOLLOWING_FETCH_CONCURRENCY).flatMap { chunk ->
                    chunk.map { (stream, page, cursor) ->
                        async { runCatching { capable.getCreatorFeed(stream.creator, page, cursor) } }
                    }.map { it.await() }
                }
            }
            currentCoroutineContext().ensureActive()
            if (loadGeneration.get() != generation) return

            val failures = mutableListOf<String>()
            val unclassifiedFailures = mutableListOf<String>()
            val alive = mutableListOf<FollowingStream>()
            val failedStreams = mutableSetOf<FollowingStream>()
            var authRequired = false
            var rateLimited = false
            var rateLimitRetryAt: Long? = null
            requests.forEachIndexed { index, (stream, page, _) ->
                val result = outcomes[index]
                val pageData = result.getOrNull()
                if (pageData == null) {
                    val t = result.exceptionOrNull()
                    val verdict = t?.let(::classifyFeedError)
                    if (verdict == FeedError.ExpiredCursor) {
                        // One creator's cursor went stale: end that stream silently while the
                        // aggregate feed keeps its other streams (contract v25). A reset
                        // generation rebuilds the stream from cursor = null.
                        logcat(LogPriority.WARN) {
                            "Following stream '${stream.creator}' cursor expired; stopping it (contract v25)."
                        }
                        stream.exhausted = true
                        alive += stream
                        return@forEachIndexed
                    }
                    logcat(LogPriority.ERROR, throwable = t) { "Following stream '${stream.creator}' failed" }
                    failedStreams += stream
                    val text = "'${stream.creator}': ${t?.localizedMessage ?: "failed"}"
                    failures += text
                    when (verdict) {
                        FeedError.AuthRequired -> authRequired = true
                        is FeedError.RateLimited -> {
                            rateLimited = true
                            val at = verdict.retryAtEpochMillis
                            if (at != null) rateLimitRetryAt = minOf(rateLimitRetryAt ?: at, at)
                        }
                        FeedError.Terminal, FeedError.Transient -> Unit
                        FeedError.ExpiredCursor, null -> unclassifiedFailures += text
                    }
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
            followingStreams = if (reset) alive else followingStreams.filterNot { it in failedStreams }
            val merged = mergeFollowingStreams(followingStreams)
            val failedText = failures.joinToString("; ")
            // Only unclassified failures fall through to the legacy text heuristics; a typed
            // Terminal verdict must not bootstrap or back off just because its message
            // happens to contain a status-like number.
            val legacyText = unclassifiedFailures.joinToString("; ")
            // Same self-healing as loadFeed: a Cloudflare/dead-session failure inside the
            // creator streams re-lifts the web session through the bootstrap WebView.
            maybeBumpBootstrap(authRequired || HTTP_SESSION_ERROR_REGEX.containsMatchIn(legacyText))
            // B1: a rate-limited shutdown (every stream 429'd) schedules the capped auto-retry;
            // a fully clean fan-out resets the budget.
            if (alive.isEmpty() && failures.isNotEmpty()) {
                if (rateLimited) {
                    val seconds = rateLimitRetryAt
                        ?.let { ((it - System.currentTimeMillis() + 999) / 1000L).coerceIn(1L, 120L).toInt() }
                        ?: RATE_LIMIT_DEFAULT_RETRY_SEC
                    scheduleRateLimitRetry(seconds, reset)
                } else {
                    maybeScheduleRateLimitRetry(legacyText, reset)
                }
            } else if (failures.isEmpty()) {
                rateLimitRetries = 0
            }

            mutableState.update { current ->
                if (loadGeneration.get() != generation) return@update current
                val anyAlive = followingStreams.any { !it.exhausted }
                if (reset) {
                    val newItems = shuffleFollowingBatch(
                        merged
                            .filterNot { (_, item) ->
                                val (hVideos, hAuthors) = hiddenSnapshot
                                item.id in hVideos || (item.author != null && item.author in hAuthors)
                            }
                            .distinctBy { it.second.id },
                        prevTailAuthor = null,
                    ).map { it.second }
                    if (newItems.isEmpty() && failures.isNotEmpty() && alive.isEmpty()) {
                        // All-failed fan-out: nothing to show, surface a localized count summary
                        // (the raw per-creator detail stays in `error` for logs).
                        current.copy(
                            isLoading = false,
                            error = failedText,
                            errorCounts = failures.size to requests.size,
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
                            errorRes = null,
                            errorCounts = null,
                            pageError = failedText.takeIf { it.isNotEmpty() },
                            canLoadMore = anyAlive,
                            feedGeneration = current.feedGeneration + 1,
                            targetPageIndex = 0,
                            activeIndex = 0,
                        )
                    }
                } else {
                    val fresh = shuffleFollowingBatch(
                        merged.filterNot { (_, item) ->
                            val (hVideos, hAuthors) = hiddenSnapshot
                            item.id in current.seenIds ||
                                item.id in hVideos ||
                                (item.author != null && item.author in hAuthors)
                        },
                        prevTailAuthor = current.items.lastOrNull()?.author,
                    ).map { it.second }
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
            val msg = t.localizedMessage.orEmpty()
            val verdict = classifyFeedError(t)
            mutableState.update { current ->
                if (current.items.isEmpty()) {
                    current.copy(isLoading = false, error = t.localizedMessage.orEmpty())
                } else {
                    current.copy(isLoading = false, pageError = t.localizedMessage.orEmpty())
                }
            }
            handleFeedErrorVerdict(verdict, msg, reset)
        }
    }

    /**
     * k-way merge over the streams' buffer heads: newest first by
     * [ShortVideoItem.createdAtEpochSec]; heads without a timestamp keep their own stream
     * order and are pulled round-robin among themselves. Drains every buffer — the merged
     * result is the feed tail until the next top-up. Items come back paired with their
     * stream creator so [shuffleFollowingBatch] can separate same-author runs.
     */
    private fun mergeFollowingStreams(streams: List<FollowingStream>): List<Pair<String, ShortVideoItem>> {
        val merged = mutableListOf<Pair<String, ShortVideoItem>>()
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
            merged += pick.creator to pick.buffer.removeFirst()
        }
    }

    /**
     * Follow/unfollow the given creator on the current source. A follow INSERT is suppressed
     * in incognito (same rule as likes: an incognito session leaves no persisted traces); a
     * REMOVAL always persists so a stale follow cannot resurface (same rule as favorite
     * removal). Uncapped — the FOLLOWING fan-out is bounded at fetch time, not at the follow set.
     */
    fun toggleFollow(creator: String) {
        val sourceId = state.value.currentSourceId
        val willFollow = creator !in state.value.followingCreators
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
            if (willFollow && !isIncognito(sourceId)) {
                reelsFollowRepository.insert(ReelsFollow(sourceId = sourceId, creator = creator, addedAt = Date()))
            } else if (!willFollow) {
                // A removal must always reach the DB so a stale persisted follow doesn't
                // "resurrect" after restart — even in incognito.
                reelsFollowRepository.delete(sourceId, creator)
            }
        }
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
        applySearchQuery(query, closeBar = true)
    }

    /**
     * Applies a search query from the IME submit ([search]) or live from typing on
     * album-capable sources ([requestSearchSuggestions]): snapshots the base feed once,
     * persists the query, and reloads. Live typing keeps the search bar open.
     */
    private fun applySearchQuery(query: String, closeBar: Boolean) {
        val trimmed = query.trim()
        // A new search voids any pending deep-link seek from the entry point.
        resumeNavPending = false
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
        persistUnlessIncognito(state.value.currentSourceId) {
            sourcePreferences.lastReelsQuery(state.value.currentSourceId).set(trimmed)
        }
        mutableState.update { current ->
            current.copy(
                searchQuery = trimmed,
                isSearchBarOpen = if (closeBar) false else current.isSearchBarOpen,
            )
        }
        // Album-capable sources render cards (live via requestSearchSuggestions); the
        // categorized panel is only for non-album sources.
        if (!state.value.albumSearchCapable) requestSearchSuggestions(trimmed)
        loadFeed(reset = true)
    }

    /**
     * Categorized search hits (contract v20) for the search-panel tabs — requested live while
     * the user types (site parity) and on submit. A throwing/absent capability leaves the tabs
     * hidden (null) and never touches the flat stream. A response that races a newer query is
     * dropped via [suggestionsQuery].
     */
    private var suggestionsQuery: String = ""
    private var suggestionsJob: Job? = null

    fun requestSearchSuggestions(query: String) {
        suggestionsQuery = query
        // A newer keystroke (or a cleared query) supersedes the in-flight attempt: the
        // canceled job must never repopulate the tabs afterwards.
        suggestionsJob?.cancel()
        // Device UX fix: for album-capable sources the paginated album cards ARE the
        // suggestions (with save "+" and long-press preview, no 100-chip cap) — typing
        // runs the album search live instead of the one-shot categorized panel.
        if (state.value.albumSearchCapable) {
            if (query.isBlank()) {
                mutableState.update { it.copy(searchSuggestions = null) }
                return
            }
            suggestionsJob = screenModelScope.launch(ioDispatcher) {
                delay(SEARCH_SUGGESTIONS_DEBOUNCE_MS)
                if (suggestionsQuery != query) return@launch
                if (state.value.searchQuery == query.trim()) return@launch
                applySearchQuery(query, closeBar = false)
            }
            return
        }
        val src = source as? AnimeCategorizedSearchSource
        if (src == null || query.isBlank()) {
            mutableState.update { it.copy(searchSuggestions = null) }
            return
        }
        // Debounced: the tabs refresh once the user pauses typing, not per keystroke.
        suggestionsJob = screenModelScope.launch(ioDispatcher) {
            delay(SEARCH_SUGGESTIONS_DEBOUNCE_MS)
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
        persistUnlessIncognito(state.value.currentSourceId) {
            sourcePreferences.lastReelsQuery(state.value.currentSourceId).set("")
        }
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
                    errorRes = null,
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
        persistUnlessIncognito(state.value.currentSourceId) {
            sourcePreferences.lastReelsFilter(state.value.currentSourceId).set(serialized)
        }
        mutableState.update { it.copy(isFilterDialogOpen = false) }
        loadFeed(reset = true)
    }

    fun resetFilters() {
        if (state.value.isOffline) return
        val src = source ?: return
        val sourceId = state.value.currentSourceId
        persistUnlessIncognito(sourceId) {
            sourcePreferences.lastReelsQuery(sourceId).set("")
            sourcePreferences.lastReelsFilter(sourceId).set("")
        }
        // The fresh list is rebuilt off the main thread (plugin code); the placeholder closes
        // the dialog and the reset generation waits for the real list.
        mutableState.update {
            it.copy(
                filters = AnimeFilterList(),
                searchQuery = "",
                isFilterDialogOpen = false,
                isSearchBarOpen = false,
            )
        }
        screenModelScope.launch(ioDispatcher) {
            val freshFilters = src.getFilterList()
            withContext(Dispatchers.Main.immediate) {
                if (state.value.currentSourceId != sourceId) return@withContext
                mutableState.update { it.copy(filters = freshFilters) }
                loadFeed(reset = true)
            }
        }
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
        mutableState.update {
            it.copy(
                isLoginDialogOpen = open,
                loginError = if (open) null else it.loginError,
                loginRejected = if (open) false else it.loginRejected,
            )
        }
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

    /**
     * Contract v22: the source's own session instrumentation snippet for the login WebView.
     * Null/empty = the host falls back to the generic cookie/localStorage dump.
     */
    fun sessionInstrumentationJs(): String? =
        (source as? AnimeFeedLoginInstrumentationSource)?.sessionInstrumentationJs()

    /** Contract v22: extra cookie origins the source wants inside the session dump. */
    fun extraSessionCookieOrigins(): List<String> =
        (source as? AnimeFeedLoginInstrumentationSource)?.extraSessionCookieOrigins().orEmpty()

    /** Stage-2 PKCE authorize URL (fresh verifier); null when not capable. */
    fun ownAuthorizeUrl(): String? = (source as? AnimeFeedWebLoginSource)?.ownAuthorizeUrl()

    /**
     * WebView session import (contract v20). A false answer keeps the dialog open (the host
     * retries on the next page-finished/Done press) and surfaces [State.webLoginHint]; true
     * closes the dialog, snapshots the account label and reloads the feed exactly like a
     * password-login success. Transport failures of the import count as "not yet".
     *
     * Contract v24: [cookies] are origin-scoped records. A source implementing
     * [AnimeFeedOriginScopedSessionSource] receives them as-is; legacy sources get the old
     * last-wins name → value merge.
     */
    fun tryImportWebSession(cookies: List<WebLoginCookie>, localStorage: Map<String, String>) {
        val webSource = source as? AnimeFeedWebLoginSource ?: return
        val scopedSource = webSource as? AnimeFeedOriginScopedSessionSource
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            val imported = runCatching {
                if (scopedSource != null) {
                    scopedSource.importWebSessionScoped(cookies, localStorage)
                } else {
                    webSource.importWebSession(legacyCookieMap(cookies), localStorage)
                }
            }.getOrDefault(false)
            if (imported) onWebSessionImported() else mutableState.update { it.copy(webLoginHint = true) }
        }
    }

    /** True when [url] is a redirect of the source's own in-flight authorize flow (stage 2). */
    fun isOwnWebLoginRedirect(url: String): Boolean =
        (source as? AnimeFeedWebLoginSource)?.isOwnLoginRedirect(url) == true

    /** Stage-2 code exchange (contract v20): same success/failure semantics as the SPA import. */
    fun tryImportWebRedirect(url: String, cookies: List<WebLoginCookie>) {
        val webSource = source as? AnimeFeedWebLoginSource ?: return
        val scopedSource = webSource as? AnimeFeedOriginScopedSessionSource
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            val imported = runCatching {
                if (scopedSource != null) {
                    scopedSource.importWebRedirectScoped(url, cookies)
                } else {
                    webSource.importWebRedirect(url, legacyCookieMap(cookies))
                }
            }.getOrDefault(false)
            if (imported) applyWebLoginSuccess() else mutableState.update { it.copy(webLoginHint = true) }
        }
    }

    /**
     * Done-button path: try the SPA-session lift first; when the SPA keeps its tokens in
     * memory (nothing to lift — the RedGIFs case), bump the stage-2 counter so the dialog
     * loads the source's own PKCE authorize URL in the same WebView.
     */
    fun tryImportWebSessionStage2(cookies: List<WebLoginCookie>, localStorage: Map<String, String>) {
        val webSource = source as? AnimeFeedWebLoginSource ?: return
        val scopedSource = webSource as? AnimeFeedOriginScopedSessionSource
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            val imported = runCatching {
                if (scopedSource != null) {
                    scopedSource.importWebSessionScoped(cookies, localStorage)
                } else {
                    webSource.importWebSession(legacyCookieMap(cookies), localStorage)
                }
            }.getOrDefault(false)
            if (imported) {
                onWebSessionImported()
            } else {
                mutableState.update { current ->
                    current.copy(webLoginHint = true, webLoginStage2Attempt = current.webLoginStage2Attempt + 1)
                }
            }
        }
    }

    /**
     * SPA import succeeded: the first time, upgrade to a refreshable session instead of
     * closing — bump the stage-2 counter so the dialog loads the source's own PKCE authorize
     * URL (with the auth2 cookie it auto-completes and the intercepted code yields a
     * refresh_token). The second import (or a source without stage 2) closes the dialog.
     */
    private fun onWebSessionImported() {
        if (!state.value.webLoginPendingClose) {
            mutableState.update { current ->
                current.copy(
                    webLoginPendingClose = true,
                    webLoginHint = false,
                    webLoginStage2Attempt = current.webLoginStage2Attempt + 1,
                )
            }
        } else {
            applyWebLoginSuccess()
        }
    }

    /** Shared post-web-login path: close the dialog, snapshot the account, reload the feed. */
    private fun applyWebLoginSuccess() {
        val loginSource = source as? AnimeFeedLoginSource
        mutableState.update { current ->
            current.copy(
                isWebLoginDialogOpen = false,
                webLoginHint = false,
                webLoginPendingClose = false,
                cfBootstrapAttempt = 0,
                cfBootstrapExhausted = false,
                loggedInAccount = loginSource?.takeIf { it.isLoggedIn() }?.loggedInAccount(),
            )
        }
        // A re-lifted session must refresh whatever account sheet is open right now.
        if (state.value.isContentPreferencesOpen) loadContentPreferences()
        if (state.value.isBlockedTagsOpen) loadBlockedTags()
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
        mutableState.update {
            it.copy(
                isContentPreferencesLoading = true,
                contentPreferencesError = null,
                contentPreferencesSaveFailed = false,
            )
        }
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
                    contentPreferencesSaveFailed = false,
                )
            }
            // Account session expired (short-lived Kinde bearer, no refresh token): silently
            // re-lift a fresh one through the bootstrap WebView; the sheet reloads on success.
            // Audit H6: only an AUTH failure is session-death evidence — a legitimately empty
            // preference list (or a timeout) must not mount the bootstrap WebView.
            maybeBumpBootstrap(
                result.isFailure &&
                    HTTP_SESSION_ERROR_REGEX.containsMatchIn(result.exceptionOrNull()?.localizedMessage.orEmpty()),
            )
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
                mutableState.update { it.copy(contentPreferencesError = null, contentPreferencesSaveFailed = true) }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Blocked tags (contract v20)
    // ---------------------------------------------------------------------------

    /** Opens/closes the blocked-tags editor; opening (re)loads the account list. */
    fun toggleBlockedTags(open: Boolean) {
        mutableState.update {
            it.copy(
                isBlockedTagsOpen = open,
                blockedTagsError = if (open) null else it.blockedTagsError,
            )
        }
        if (open) loadBlockedTags()
    }

    private fun loadBlockedTags() {
        val src = source as? AnimeBlockedTagsSource ?: return
        val sourceId = state.value.currentSourceId
        mutableState.update {
            it.copy(isBlockedTagsLoading = true, blockedTagsError = null, blockedTagsSaveFailed = false)
        }
        screenModelScope.launch(ioDispatcher) {
            val result = runCatching { src.getBlockedTags() }
            mutableState.update { current ->
                if (current.currentSourceId != sourceId) {
                    return@update current.copy(isBlockedTagsLoading = false)
                }
                current.copy(
                    isBlockedTagsLoading = false,
                    blockedTags = result.getOrNull()?.toImmutableList(),
                    blockedTagsError = result.exceptionOrNull()?.localizedMessage,
                    blockedTagsSaveFailed = false,
                )
            }
        }
    }

    /** Persists the replaced blocked-tag set; reloads the sheet list on success. */
    fun saveBlockedTags(tags: List<String>) {
        val src = source as? AnimeBlockedTagsSource ?: return
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            val ok = runCatching { src.setBlockedTags(tags) }.getOrDefault(false)
            if (ok) {
                loadBlockedTags()
            } else {
                mutableState.update { it.copy(blockedTagsError = null, blockedTagsSaveFailed = true) }
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

    fun loadCustomFeeds() {
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

    /** B3.5: explicit picture-in-picture toggle (off by default). */
    fun togglePip() {
        val next = !state.value.isPipEnabled
        sourcePreferences.reelsPipEnabled().set(next)
        mutableState.update { it.copy(isPipEnabled = next) }
    }

    /**
     * Reels-only incognito (product decision): the global incognito switch, the NSFW auto
     * policy and the per-extension set do not apply inside the feed — this toggle is the
     * single source of truth for history/position/last-source writes here. The badge and the
     * write gates react through the preference's change flow / live reads.
     */
    fun toggleReelsIncognito() {
        invalidateReplay()
        val next = !sourcePreferences.reelsIncognitoMode().get()
        sourcePreferences.reelsIncognitoMode().set(next)
    }

    // --- B2 sleep timer ---

    enum class SleepTimerOption { OFF, END_OF_VIDEO, M15, M30, M60 }

    private var sleepTimerJob: Job? = null

    /**
     * Arms/disarms the feed sleep timer (YouTube pattern): a countdown that pauses the feed
     * on expiry, or "after this video" consumed at the next completion boundary. Expiry is a
     * STICKY pause (userPaused), so auto-advance and swipes do not undo it.
     */
    fun setSleepTimer(option: SleepTimerOption) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        when (option) {
            SleepTimerOption.OFF -> mutableState.update {
                it.copy(sleepTimerRemainingSec = 0, sleepAtVideoEnd = false)
            }
            SleepTimerOption.END_OF_VIDEO -> mutableState.update {
                it.copy(sleepTimerRemainingSec = 0, sleepAtVideoEnd = true)
            }
            else -> {
                val minutes = when (option) {
                    SleepTimerOption.M15 -> 15
                    SleepTimerOption.M30 -> 30
                    else -> 60
                }
                mutableState.update {
                    it.copy(sleepTimerRemainingSec = minutes * 60, sleepAtVideoEnd = false)
                }
                sleepTimerJob = screenModelScope.launch {
                    while (true) {
                        delay(1000)
                        var expired = false
                        mutableState.update { current ->
                            val remaining = current.sleepTimerRemainingSec - 1
                            if (remaining <= 0) {
                                expired = true
                                current.copy(sleepTimerRemainingSec = 0)
                            } else {
                                current.copy(sleepTimerRemainingSec = remaining)
                            }
                        }
                        if (expired) {
                            triggerSleep()
                            break
                        }
                    }
                }
            }
        }
    }

    /** Timer expiry / end-of-video consumption: sticky pause, playback stops for good. */
    private fun triggerSleep() {
        sleepTimerJob = null
        mutableState.update {
            it.copy(isPlaying = false, userPaused = true, sleepAtVideoEnd = false, sleepTimerRemainingSec = 0)
        }
    }

    /**
     * Video-boundary hook (screen's onVideoCompleted): true when the "after this video" timer
     * was armed — the feed pauses instead of advancing and the flag is consumed.
     */
    fun consumeSleepAtVideoEnd(): Boolean {
        if (!state.value.sleepAtVideoEnd) return false
        triggerSleep()
        return true
    }

    fun toggleLike(item: ShortVideoItem, itemIndex: Int = -1) {
        setLike(item, liked = !state.value.isLiked(item.id, itemIndex), itemIndex = itemIndex)
    }

    /**
     * Idempotent like write. Gestures and accessibility actions can deliver the same intent
     * twice (a double-tap replayed after the page was rebound, a repeated action) — a call
     * whose target equals the current state must not flip the like back or duplicate the
     * insert / remote feedback.
     */
    fun setLike(item: ShortVideoItem, liked: Boolean, itemIndex: Int = -1) {
        val videoId = item.id
        val likeKey = state.value.likeKey(videoId, itemIndex)
        if (liked == (likeKey in state.value.likedIds)) return
        // The item comes from the caller (the page rendering it): re-finding it in state
        // would silently skip the insert when a feed refresh displaced the video between
        // the tap and the write. The write must survive screen disposal (NonCancellable).
        // Offline playlists resolve the sourceId by the item's SLOT (same videoId can exist
        // for several sources — a videoId-keyed lookup would misattribute likes).
        val sourceId = if (state.value.isOffline) {
            offlineSourceIds.getOrNull(itemIndex) ?: state.value.currentSourceId
        } else {
            state.value.currentSourceId
        }
        // Snapshot the feedback capability synchronously: reading the live `source` field
        // inside the NonCancellable launch below could deliver the like signal to a DIFFERENT
        // source's plugin if a source switch lands between the tap and the write.
        val feedback = source as? AnimeReelsFeedbackSource
        decidedIds += videoId
        mutableState.update { state ->
            val newLikes = if (liked) state.likedIds + likeKey else state.likedIds - likeKey
            state.copy(likedIds = newLikes.toImmutableSet())
        }
        // Incognito blocks persisting a NEW like, but a removal must always reach the DB so a
        // previously saved like doesn't "resurrect" after restart.
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            if (liked) {
                if (!isIncognito(sourceId)) {
                    reelsFavoriteRepository.insert(item.toReelsFavorite(sourceId))
                }
            } else {
                reelsFavoriteRepository.delete(videoId, sourceId)
            }
            // Let a feedback-capable feed source adapt its recommendations to likes (opt-in,
            // fire-and-forget; the local favorite stays authoritative for the offline playlist).
            // Incognito suppresses the REMOTE signal too: privacy applies end-to-end, not only
            // to the local DB.
            if (feedback != null && !isIncognito(sourceId)) {
                runCatching { feedback.onVideoLiked(videoId, liked) }
            }
        }
    }

    /** Reports playback of a reel to a feedback-capable source (drives remote personalization). */
    fun reportVideoView(itemId: String, secondsWatched: Float, duration: Float) {
        val feedback = source as? AnimeReelsFeedbackSource ?: return
        // Incognito: no remote view signal either (privacy end-to-end).
        if (isIncognito(state.value.currentSourceId)) return
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            runCatching { feedback.onVideoViewed(itemId, secondsWatched.toDouble(), duration.toDouble()) }
        }
    }

    /**
     * Local watch history (B3.1): fires when a reel is left, storing the playback fraction for
     * the resume-seek. Browsing data — suppressed in incognito; the offline favorites playlist
     * is not recorded (it replays local rows, not browsing).
     */
    fun recordWatchHistory(item: ShortVideoItem, positionFraction: Float, durationSec: Float) {
        if (state.value.isOffline) return
        if (isIncognito(state.value.currentSourceId)) return
        val sourceId = state.value.currentSourceId
        val positionMs = (positionFraction.coerceIn(0f, 1f) * durationSec.coerceAtLeast(0f) * 1000f).toLong()
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            reelsWatchRepository.upsert(
                ReelsWatchEntry(
                    videoId = item.id,
                    sourceId = sourceId,
                    title = item.title,
                    author = item.author,
                    posterUrl = item.posterUrlVertical ?: item.posterUrl,
                    webUrl = item.webUrl,
                    videoUrl = item.videoUrl,
                    // The PLAYER-reported duration wins: feed metadata durationSec is null on
                    // several sources (e.g. KinkGrid), and launchResumeLookup needs a duration
                    // to convert positionMs back into a seek fraction — without it the resume
                    // feature is dead for those sources even though positions are recorded.
                    durationSec = durationSec.takeIf { it > 0f }?.toDouble() ?: item.durationSec?.toDouble(),
                    positionMs = positionMs,
                    watchedAt = Date(),
                ),
            )
        }
    }

    /** Loads the stored resume position for [resumeVideoId] once (best effort, silent). */
    private fun launchResumeLookup(videoId: String, sourceId: Long) {
        screenModelScope.launch(ioDispatcher) {
            val entry = reelsWatchRepository.getByVideo(videoId, sourceId) ?: return@launch
            val duration = entry.durationSec ?: return@launch
            if (duration <= 0.0 || entry.positionMs <= 0) return@launch
            val fraction = (entry.positionMs / (duration * 1000.0)).toFloat().coerceIn(0f, 0.97f)
            mutableState.update { current ->
                if (current.currentSourceId != sourceId) return@update current
                current.copy(resumeFraction = fraction)
            }
        }
    }

    /**
     * The resume position is consumed by the first applied seek: clear it so re-entering the
     * same clip later in this session (page disposed and recomposed) starts from the top
     * instead of jumping back to the stale stored fraction.
     */
    fun onInitialSeekConsumed() {
        mutableState.update { if (it.resumeFraction == null) it else it.copy(resumeFraction = null) }
    }

    /** Per-mode position suffix: every feed mode restores its own last position (B3.1). */
    private val positionSuffix: String
        get() = when (mode) {
            FeedMode.GLOBAL -> ""
            FeedMode.CREATOR -> "_creator_${creator.orEmpty().hashCode()}"
            FeedMode.CUSTOM -> "_custom_${customFeedId.orEmpty().hashCode()}"
            FeedMode.NICHE -> "_niche_${nicheId.orEmpty().hashCode()}"
            FeedMode.FOLLOWING -> "_following"
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
        mutableState.update { it.copy(isLoggingIn = true, loginError = null, loginRejected = false) }
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            val ok = runCatching { loginSource.login(email, password) }.fold(
                onSuccess = { it },
                onFailure = { t ->
                    logcat(LogPriority.ERROR, t) { "Login failed for source ${state.value.currentSourceId}" }
                    mutableState.update {
                        it.copy(isLoggingIn = false, loginError = t.localizedMessage.orEmpty())
                    }
                    return@launch
                },
            )
            mutableState.update { current ->
                current.copy(
                    isLoggingIn = false,
                    loggedInAccount = if (ok) loginSource.loggedInAccount() else null,
                    loginError = null,
                    loginRejected = !ok,
                )
            }
            // A login can change the underlying account: the For You scope must be re-resolved
            // instead of reusing the previous account's generation (or its pinned identity).
            forYouScope = null
            pinnedScope = null
            watchedCommitted.clear()
            skippedCommitted.clear()
            if (ok) loadFeed(reset = true)
        }
    }

    /** Clears the persisted login session of the current source and reloads the feed. */
    fun logout() {
        val loginSource = source as? AnimeFeedLoginSource ?: return
        val webLoginSource = source as? AnimeFeedWebLoginSource
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            runCatching { loginSource.logout() }
            if (webLoginSource != null) {
                // Contract v22: the source names the origins to purge; the generic same-site
                // heuristic is the fallback for sources without the capability.
                val purgeOrigins = (source as? AnimeFeedLoginInstrumentationSource)
                    ?.logoutCookieOrigins()
                    .orEmpty()
                runCatching { clearWebLoginCookies(webLoginSource.webLoginUrl(), purgeOrigins) }
            }
            mutableState.update { it.copy(loggedInAccount = null, loginError = null, loginRejected = false) }
            // Logout fences the account scope: the next load re-resolves it, so the previous
            // account's history is never reused for a different session.
            forYouScope = null
            pinnedScope = null
            watchedCommitted.clear()
            skippedCommitted.clear()
            loadFeed(reset = true)
        }
    }

    /**
     * Contract v22: refresh the stored CDN links of favorites whose source supports
     * [AnimeFeedVideoResolverSource]; a null/failure answer keeps the stored URL and resolver
     * errors are never surfaced (best effort for the offline playlist).
     *
     * Bounded parallelism: the playlist spinner covers this whole pass, so a big favorites
     * list must not serialize N resolver round-trips before the first frame.
     */
    private suspend fun resolveExpiredUrls(favorites: List<ReelsFavorite>): List<ReelsFavorite> {
        return favorites.chunked(RESOLVE_URL_CONCURRENCY).flatMap { chunk ->
            coroutineScope {
                chunk.map { favorite -> async { resolveExpiredUrl(favorite) } }.awaitAll()
            }
        }
    }

    private suspend fun resolveExpiredUrl(favorite: ReelsFavorite): ReelsFavorite {
        val resolver = sourceManager.get(favorite.sourceId) as? AnimeFeedVideoResolverSource
            ?: return favorite
        val base = runCatching { resolver.resolveVideoUrl(favorite.videoId, false) }.getOrNull()
            ?: return favorite
        val hd = runCatching { resolver.resolveVideoUrl(favorite.videoId, true) }.getOrNull()
            ?.takeIf { it != base }
        return favorite.copy(videoUrl = base, videoUrlHd = hd ?: favorite.videoUrlHd)
    }

    private fun clearWebLoginCookies(startUrl: String, purgeOrigins: List<String>) {
        val origins = mutableSetOf<String>()
        if (purgeOrigins.isNotEmpty()) {
            // Source-supplied purge set (contract v22): no host-side domain guessing.
            purgeOrigins.forEach { if (it.isNotBlank()) origins += it }
        } else {
            val uri = runCatching { Uri.parse(startUrl) }.getOrNull() ?: return
            val host = uri.host ?: return
            val scheme = uri.scheme ?: "https"
            origins += "$scheme://$host"
            val labels = host.split('.')
            val root = if (labels.size >= 2) labels.takeLast(2).joinToString(".") else host
            if (labels.size >= 2) {
                origins += "$scheme://$root"
                origins += "$scheme://auth2.$root"
                origins += "$scheme://api.$root"
            }
        }
        val cm = CookieManager.getInstance()
        val fallbackRoot = runCatching { Uri.parse(startUrl).host }.getOrNull().orEmpty()
        origins.forEach { origin ->
            val cookieStr = cm.getCookie(origin) ?: return@forEach
            cookieStr.split(';').forEach { pair ->
                val key = pair.substringBefore('=').trim()
                if (key.isNotEmpty()) {
                    cm.setCookie(origin, "$key=; Max-Age=0; Path=/")
                    if (fallbackRoot.isNotEmpty()) {
                        cm.setCookie(origin, "$key=; Domain=.$fallbackRoot; Max-Age=0; Path=/")
                    }
                    runCatching { Uri.parse(origin).host }.getOrNull()?.let { originHost ->
                        cm.setCookie(origin, "$key=; Domain=$originHost; Max-Age=0; Path=/")
                    }
                }
            }
        }
        cm.flush()
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

    /** B3.2: loads the per-source hidden set; a raced source switch drops the answer. */
    private fun loadPersistedHidden(sourceId: Long) {
        screenModelScope.launch(ioDispatcher) {
            val entries = reelsHiddenRepository.getBySource(sourceId)
            val videos = entries.filter { it.kind == ReelsHiddenEntry.KIND_VIDEO }.map { it.value }.toSet()
            val authors = entries.filter { it.kind == ReelsHiddenEntry.KIND_AUTHOR }.map { it.value }.toSet()
            // The snapshot merge is main-confined: a hide tapped while this DB read was in
            // flight must not be lost to a read-modify-write race.
            withContext(Dispatchers.Main.immediate) {
                if (state.value.currentSourceId != sourceId) return@withContext
                hiddenSnapshot = hiddenSnapshot.first + videos to hiddenSnapshot.second + authors
            }
        }
    }

    // --- B3.2 "not interested" actions ---

    /** Hides one reel from future pages; undo until the next hide via [undoHide]. */
    fun hideVideo(item: ShortVideoItem) {
        if (state.value.isOffline || item.id.isBlank()) return
        // The manager screen shows the captured title; the raw videoId is the fallback.
        applyHide(ReelsHiddenEntry.KIND_VIDEO, item.id, state.value.currentSourceId, item.title)
    }

    /** Hides an author's reels from future pages; undo until the next hide via [undoHide]. */
    fun hideAuthor(author: String) {
        if (state.value.isOffline || author.isBlank()) return
        applyHide(ReelsHiddenEntry.KIND_AUTHOR, author, state.value.currentSourceId)
    }

    /** Token of the most recent hide; capture it when showing the undo snackbar. */
    fun lastHideToken(): Triple<String, String, Long>? = lastHide

    /**
     * Undo of a specific hide (video or author), local and persisted. No-op when a newer hide
     * superseded [token] — an older snackbar's undo must not roll back the newer decision.
     */
    fun undoHide(token: Triple<String, String, Long>?) {
        if (token == null || lastHide != token) return
        val (kind, value, sourceId) = token
        lastHide = null
        val (videos, authors) = hiddenSnapshot
        hiddenSnapshot = when (kind) {
            ReelsHiddenEntry.KIND_VIDEO -> (videos - value) to authors
            else -> videos to (authors - value)
        }
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            hiddenWriteMutex.withLock {
                reelsHiddenRepository.delete(sourceId, kind, value)
            }
        }
    }

    private fun applyHide(kind: String, value: String, sourceId: Long, label: String? = null) {
        val (videos, authors) = hiddenSnapshot
        hiddenSnapshot = when (kind) {
            ReelsHiddenEntry.KIND_VIDEO -> (videos + value) to authors
            else -> videos to (authors + value)
        }
        lastHide = Triple(kind, value, sourceId)
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            hiddenWriteMutex.withLock {
                reelsHiddenRepository.insert(
                    ReelsHiddenEntry(sourceId, kind, value, Date(), label?.takeIf { it.isNotBlank() }),
                )
            }
        }
    }

    // --- B3.3 offline copies ---

    enum class OfflineCopyResult { SAVED, REMOVED, FAILED_QUOTA, FAILED_NETWORK, FAILED_UNSUPPORTED }

    /**
     * Contract v25: the source's own media verdict for [item], or null when the source does
     * not implement the capability (the host then keeps the legacy URL-based rules).
     * [sourceId] names the item's owning source when it differs from the live feed source
     * (the offline playlist mixes sources; its slots carry their own id).
     */
    fun mediaDescriptor(item: ShortVideoItem, sourceId: Long? = null): FeedMediaDescriptor? {
        val owner = sourceId?.let(sourceManager::get) ?: source
        val descriptorSource = owner as? AnimeFeedMediaDescriptorSource ?: return null
        return try {
            descriptorSource.mediaDescriptor(item)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            logcat(LogPriority.WARN, t) { "mediaDescriptor failed for item ${item.id}; using legacy rules" }
            null
        }
    }

    /**
     * Just-in-time stream signing (contract v22): sources may serve items with a blank
     * videoUrl (balbums v6+ defers signing to playback time); the player and the offline
     * downloader resolve through the resolver capability instead of batch-resolving pages.
     * v25: a failed HD resolution may retry the base variant unless the descriptor opts out.
     */
    suspend fun resolvePlaybackUrl(
        item: ShortVideoItem,
        hd: Boolean,
        sourceId: Long? = null,
    ): String? {
        val owner = sourceId?.let(sourceManager::get) ?: source
        if (owner === source && isReplaySurface() && replayKey != null) {
            val key = replayKey
            val result = sessionContinuity.resolveItem(item.id)
            if (replayKey != key) return null
            if (result is FeedReplayItemResult.Available && result.item.id == item.id) {
                val fresh = result.item
                withContext(Dispatchers.Main.immediate) {
                    if (replayKey == key) {
                        mutableState.update { current ->
                            current.copy(
                                items = current.items.map { old ->
                                    if (old.id == item.id && old.videoUrl.isBlank()) fresh else old
                                }.toImmutableList(),
                            )
                        }
                    }
                }
                return if (hd) fresh.videoUrlHd?.takeIf { it.isNotBlank() } ?: fresh.videoUrl else fresh.videoUrl
            }
            return null
        }
        val resolver = owner as? AnimeFeedVideoResolverSource ?: return null
        val hdFallback = mediaDescriptor(item, sourceId)?.hdFallback ?: true
        return try {
            resolver.resolveVideoUrl(item.id, hd)?.takeIf { it.isNotBlank() }
                ?: if (hd && hdFallback) {
                    resolver.resolveVideoUrl(item.id, false)?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            logcat(LogPriority.WARN, t) { "Video resolver failed for item ${item.id}" }
            null
        }
    }

    /**
     * Downloads a real offline copy of the current playlist reel (or removes it). On success
     * the item's URL flips to the local file and the player rebuilds automatically; quota and
     * transport failures keep the network URL and report distinct reasons for the snackbar.
     */
    suspend fun toggleOfflineCopy(item: ShortVideoItem, itemIndex: Int): OfflineCopyResult {
        if (!state.value.isOffline) return OfflineCopyResult.FAILED_NETWORK
        val sourceId = offlineSourceIds.getOrNull(itemIndex) ?: return OfflineCopyResult.FAILED_NETWORK
        val key = "$sourceId:${item.id}"
        if (key in state.value.offlineStored) {
            offlineStore.delete(sourceId, item.id)
            // The playlist item currently plays the local file: flip it back to the favorite's
            // network URL, otherwise the deleted copy leaves a broken player URL.
            val networkUrl = reelsFavoriteRepository.getAll()
                .firstOrNull { it.videoId == item.id && it.sourceId == sourceId }
                ?.videoUrl
            mutableState.update { current ->
                current.copy(
                    offlineStored = (current.offlineStored - key).toImmutableSet(),
                    items = current.items.mapIndexed { index, storedItem ->
                        if (index == itemIndex && networkUrl != null) {
                            storedItem.copy(videoUrl = networkUrl, videoUrlHd = null)
                        } else {
                            storedItem
                        }
                    }.toImmutableList(),
                )
            }
            return OfflineCopyResult.REMOVED
        }
        // Transport exceptions are a NETWORK verdict, never a crash in the UI scope.
        // Lazy-signed items (blank url) are resolved just-in-time before the download.
        // v25: a source can declare an item non-downloadable; removing a stored copy above
        // stays available regardless.
        if (mediaDescriptor(item, sourceId)?.offlineSupported == false) {
            return OfflineCopyResult.FAILED_UNSUPPORTED
        }
        val downloadItem = if (item.videoUrl.isBlank()) {
            val resolved = resolvePlaybackUrl(item, state.value.isHdQuality, sourceId)
                ?: return OfflineCopyResult.FAILED_NETWORK
            item.copy(videoUrl = resolved)
        } else {
            item
        }
        val result = try {
            offlineStore.download(downloadItem, sourceId)
        } catch (e: CancellationException) {
            // A cancelled screen must not report the download as a network failure.
            throw e
        } catch (_: Exception) {
            ReelsOfflineStore.DownloadResult.NETWORK
        }
        if (result != ReelsOfflineStore.DownloadResult.SAVED) {
            return when (result) {
                ReelsOfflineStore.DownloadResult.QUOTA -> OfflineCopyResult.FAILED_QUOTA
                ReelsOfflineStore.DownloadResult.UNSUPPORTED -> OfflineCopyResult.FAILED_UNSUPPORTED
                else -> OfflineCopyResult.FAILED_NETWORK
            }
        }
        val local = offlineStore.localUrl(sourceId, item.id)
        mutableState.update { current ->
            current.copy(
                offlineStored = (current.offlineStored + key).toImmutableSet(),
                items = current.items.mapIndexed { index, storedItem ->
                    if (index == itemIndex && local != null) {
                        storedItem.copy(videoUrl = local, videoUrlHd = null)
                    } else {
                        storedItem
                    }
                }.toImmutableList(),
            )
        }
        return OfflineCopyResult.SAVED
    }

    /** Bytes occupied by offline copies (quota visibility in the More menu, audit H8). */
    suspend fun offlineUsedBytes(): Long = withContext(ioDispatcher) { offlineStore.usedBytes() }

    /** User-initiated removal of ALL offline copies; returns the freed bytes. */
    suspend fun clearOfflineStorage(): Long = offlineStore.clearAll()

    /**
     * Playback-error retry path (offline playlist): re-resolves the stored CDN link of ONE clip
     * on demand. The background resolver pass skips the ACTIVE clip (a URL swap would rebuild
     * its player mid-playback), so a stale link on the clip being watched is refreshed here —
     * the player is already failed, the rebuild is the recovery. Local file copies are skipped.
     */
    fun refreshOfflineUrl(itemIndex: Int) {
        if (!state.value.isOffline) return
        val sourceId = offlineSourceIds.getOrNull(itemIndex) ?: return
        val item = state.value.items.getOrNull(itemIndex) ?: return
        if (item.videoUrl.startsWith("file://")) return
        screenModelScope.launch(ioDispatcher) {
            val favorite = reelsFavoriteRepository.getAll()
                .firstOrNull { it.videoId == item.id && it.sourceId == sourceId }
                ?: return@launch
            val refreshed = resolveExpiredUrl(favorite)
            if (refreshed.videoUrl == item.videoUrl) return@launch
            mutableState.update { current ->
                if (!current.isOffline) return@update current
                current.copy(
                    items = current.items.mapIndexed { index, currentItem ->
                        if (index == itemIndex) {
                            currentItem.copy(videoUrl = refreshed.videoUrl, videoUrlHd = refreshed.videoUrlHd)
                        } else {
                            currentItem
                        }
                    }.toImmutableList(),
                )
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
        val next = !state.value.isPlaying
        // A tap is the ONLY playback decision: it sets or clears the sticky pause intent
        // that survives swipes (auto-advance must not undo a deliberate pause).
        mutableState.update { it.copy(isPlaying = next, userPaused = !next) }
    }

    // Device UX: the open ⋮ settings block holds playback; closing resumes it.
    private var settingsHoldActive = false

    /**
     * Temporary playback hold for the open ⋮ settings block (device UX): pause WITHOUT
     * touching [State.userPaused] — the sticky pause stays the user's own decision — and on
     * release resume only when that sticky pause is not set (a user who paused before opening
     * the menu must stay paused after closing it).
     */
    fun setSettingsHold(hold: Boolean) {
        if (hold == settingsHoldActive) return
        settingsHoldActive = hold
        mutableState.update { current ->
            if (hold) {
                if (current.isPlaying) current.copy(isPlaying = false) else current
            } else {
                if (!current.userPaused) current.copy(isPlaying = true) else current
            }
        }
    }

    /**
     * Mirrors the active player's real state (buffering, audio-focus, lifecycle pauses and
     * seek can stop ExoPlayer without the model's play intent changing). Drives
     * FLAG_KEEP_SCREEN_ON, PiP eligibility and chrome auto-hide via [State.isActuallyPlaying].
     */
    fun setPlaybackRunning(running: Boolean) {
        mutableState.update { current ->
            if (current.isActuallyPlaying == running) current else current.copy(isActuallyPlaying = running)
        }
    }

    /**
     * Contract v25: the source's typed verdict for a failed request, or null when it has no
     * opinion (or the classifier itself failed) — the host then keeps its legacy error-text
     * heuristics. A broken classifier must never mask the original feed error.
     */
    private fun classifyFeedError(error: Throwable): FeedError? {
        val classifier = source as? AnimeFeedErrorClassifierSource ?: return null
        return try {
            classifier.classifyFeedError(error)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            logcat(LogPriority.WARN, t) { "Feed error classifier failed; using legacy heuristics" }
            null
        }
    }

    /**
     * Applies a typed verdict (contract v25). A verdict is authoritative: Terminal suppresses
     * the 401/403/429 text heuristics entirely, AuthRequired bootstraps even without status
     * text, and RateLimited honors the source's retry window. Null = legacy behavior.
     */
    private fun handleFeedErrorVerdict(verdict: FeedError?, message: String, reset: Boolean) {
        when (verdict) {
            // Expired cursors are handled by the caller (silent append stop); a reset load
            // has no cursor to expire and is surfaced as a regular terminal error.
            FeedError.ExpiredCursor -> Unit
            FeedError.AuthRequired -> maybeBumpBootstrap(true)
            is FeedError.RateLimited -> scheduleRateLimitRetry(
                seconds = verdict.retryAtEpochMillis
                    ?.let { ((it - System.currentTimeMillis() + 999) / 1000L).coerceIn(1L, 120L).toInt() }
                    ?: RATE_LIMIT_DEFAULT_RETRY_SEC,
                reset = reset,
            )
            FeedError.Transient, FeedError.Terminal -> Unit
            null -> {
                // Cloudflare managed challenge (403) or a dead account bearer (401) on
                // web-login-capable sources: bootstrap via an offscreen WebView without user
                // interaction; the import verification reloads the feed on success.
                maybeBumpBootstrap(HTTP_SESSION_ERROR_REGEX.containsMatchIn(message))
                maybeScheduleRateLimitRetry(message, reset)
            }
        }
    }

    /**
     * B1: a 429 verdict surfaces the wait ([State.retryAfterSec] drives the snackbar) and
     * schedules ONE automatic retry, capped at [RATE_LIMIT_MAX_RETRIES] consecutive attempts
     * so a hard-blocked source cannot spin forever. Any successful page resets the budget.
     * Plugin error texts are self-shaped, so the detection is best-effort by pattern.
     */
    private fun maybeScheduleRateLimitRetry(message: String, reset: Boolean) {
        if (!HTTP_TOO_MANY_REQUESTS_REGEX.containsMatchIn(message)) return
        val seconds = RETRY_AFTER_REGEX.find(message)?.groupValues?.get(1)?.toIntOrNull()
            ?.coerceIn(1, 120)
            ?: RATE_LIMIT_DEFAULT_RETRY_SEC
        scheduleRateLimitRetry(seconds, reset)
    }

    private fun scheduleRateLimitRetry(seconds: Int, reset: Boolean) {
        if (rateLimitRetries >= RATE_LIMIT_MAX_RETRIES) return
        val sourceId = state.value.currentSourceId
        rateLimitRetries++
        mutableState.update { it.copy(retryAfterSec = seconds) }
        rateLimitRetryJob?.cancel()
        rateLimitRetryJob = screenModelScope.launch(ioDispatcher) {
            delay(seconds * 1000L)
            // A source switch during the wait invalidates the scheduled retry.
            if (state.value.currentSourceId != sourceId) return@launch
            mutableState.update { it.copy(retryAfterSec = 0) }
            loadFeed(reset = reset)
        }
    }

    /**
     * Session-healing trigger (web-login-capable sources): mounts the offscreen bootstrap
     * WebView at most [CF_BOOTSTRAP_MAX_ATTEMPTS] times per source entry, so a permanently
     * broken 401/403 source cannot loop silent reloads without bound. Exhausting the budget
     * UNMOUNTS the WebView (idle challenge pages hold native threads for the whole screen
     * session otherwise); manual web login stays reachable. The budget resets on a source
     * switch and on a successful import.
     */
    private fun maybeBumpBootstrap(shouldTrigger: Boolean) {
        if (shouldTrigger && source is AnimeFeedWebLoginSource) {
            mutableState.update { current ->
                if (current.cfBootstrapExhausted) return@update current
                if (current.cfBootstrapAttempt >= CF_BOOTSTRAP_MAX_ATTEMPTS) {
                    return@update current.copy(cfBootstrapAttempt = 0, cfBootstrapExhausted = true)
                }
                current.copy(cfBootstrapAttempt = current.cfBootstrapAttempt + 1)
            }
        }
    }

    fun onPageChanged(index: Int, cause: ReelsTransitionCause = ReelsTransitionCause.PROGRAMMATIC) {
        val previousIndex = state.value.activeIndex
        val previousItem = state.value.items.getOrNull(previousIndex)
        // A skip exists only for a COMMITTED MANUAL forward move away from the card that was
        // actually settled. Cancelled drags never reach this collector (settledPage does not
        // change), auto-advance/restore/error-fallback arrive as PROGRAMMATIC and a backward
        // move is USER_BACKWARD.
        //
        // EVERY card the user moved past is a skip, not only the origin: a fling settles one
        // page further but means "I did not want any of these". Recording only the origin left
        // the flown-over reels unconsumed, so they came back in the next generation.
        if (cause == ReelsTransitionCause.USER_FORWARD && index > previousIndex && previousItem != null) {
            val items = state.value.items
            for (position in previousIndex until minOf(index, items.size)) {
                items.getOrNull(position)?.let { commitSkipped(it.id) }
            }
        }
        mutableState.update {
            it.copy(
                activeIndex = index,
                // Swiping resumes playback only when the user has not deliberately paused;
                // the sticky pause keeps every subsequently swiped page paused until resumed.
                isPlaying = !it.userPaused,
                // P03: actual playback belongs to the settled page's player. The new page
                // starts paused/preloaded, so claiming "running" here would keep the screen
                // awake and hide the chrome while it is still buffering.
                isActuallyPlaying = false,
            )
        }
        // Every feed mode persists its own browsing position (B3.1); the offline playlist has no
        // live feed position to remember.
        if (!state.value.isOffline) {
            persistUnlessIncognito(state.value.currentSourceId) {
                sourcePreferences.lastReelsPosition(state.value.currentSourceId, positionSuffix).set(index)
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
        // B3.3 offline playlist: per-slot source ids and the stored-copy keys ("sourceId:videoId").
        val offlineSourceIds: ImmutableList<Long> = persistentListOf(),
        val offlineStored: ImmutableSet<String> = persistentSetOf(),
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
        // Category feed ordering (v21): the source implements AnimeCategoryFeedOrderSource;
        // enables the filter sheet in NICHE mode.
        val isCategoryOrderCapable: Boolean = false,
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
        // Blocked tags (v20): the current source implements AnimeBlockedTagsSource; gates the
        // account-hub blocked-tags row.
        val isBlockedTagsCapable: Boolean = false,
        val blockedTags: ImmutableList<String>? = null,
        val isBlockedTagsOpen: Boolean = false,
        val isBlockedTagsLoading: Boolean = false,
        val blockedTagsError: String? = null,
        // Silent Cloudflare bootstrap counter (web-login-capable sources): >0 mounts an
        // offscreen WebView that solves the managed challenge and lifts the cookies.
        val cfBootstrapAttempt: Int = 0,
        // Set when the bootstrap budget is exhausted: the WebView is unmounted (an idle
        // challenge page with live native threads must not linger for the screen session)
        // and stays unmounted until a source switch or a successful import re-arms it.
        val cfBootstrapExhausted: Boolean = false,
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
        val replayLocalHasMore: Boolean = false,
        val isMuted: Boolean = false,
        val isHdQuality: Boolean = true,
        // Force SD while on metered (non-Wi-Fi) networks; applied when a page activates.
        val dataSaverMetered: Boolean = true,
        val isAutoAdvance: Boolean = true,
        val isCropMode: Boolean = false,
        val preloadEnabled: Boolean = true,
        val preloadWifiOnly: Boolean = false,
        // B3.5: explicit picture-in-picture (off by default; the feed survives to the PiP window).
        val isPipEnabled: Boolean = false,
        val likedIds: ImmutableSet<String> = persistentSetOf(),
        val activeIndex: Int = 0,
        // B3.1 resume-seek: when set, the matching clip seeks to [resumeFraction] once.
        val resumeVideoId: String? = null,
        val resumeFraction: Float? = null,
        val pendingSeekItemId: String? = null,
        val pendingSeekPositionMs: Long? = null,
        val isPlaying: Boolean = true,
        // Sticky user pause: set by an explicit pause tap, survives swipes (auto-advance must
        // not undo a deliberate pause) and is cleared only by an explicit resume tap.
        val userPaused: Boolean = false,
        // Actual playback state as reported by the active page's player: buffering, audio-focus
        // loss, lifecycle pauses and the media end stop ExoPlayer without the play intent
        // changing. Starts false — nothing is confirmed playing before the player says so.
        // Gates FLAG_KEEP_SCREEN_ON, PiP eligibility and chrome auto-hide.
        val isActuallyPlaying: Boolean = false,
        // Bumped every time the feed content is replaced (search/filter/source/reset) so the
        // pager can reliably scroll back to the first video.
        val feedGeneration: Int = 0,
        // Page the pager should scroll to after a feedGeneration bump: 0 on fresh loads,
        // the remembered position when a cleared search restores the base feed.
        val targetPageIndex: Int = 0,
        val searchQuery: String = "",
        // Contract v23 album search: the source answers search with a paginated album
        // directory; the screen renders album cards instead of the video pager.
        val albumSearchCapable: Boolean = false,
        val albumSearchResults: ImmutableList<FeedCategory> = persistentListOf(),
        val albumSearchNextPage: Int = 1,
        val albumSearchNextCursor: String? = null,
        // Sticky cursor lock of the album-search stream (contract v17): a null token after a
        // token was seen is a violation, not a restart of the head page.
        val albumSearchCursorMode: Boolean = false,
        val albumSearchCanLoadMore: Boolean = true,
        val savedAlbumIds: Set<String> = emptySet(),
        val previewAlbumId: String? = null,
        val previewItems: ImmutableList<ShortVideoItem> = persistentListOf(),
        val previewLoading: Boolean = false,
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
        // Stage-2 orchestration: first SPA import success arms the upgrade, the second closes.
        val webLoginPendingClose: Boolean = false,
        val error: String? = null,
        // Static, localizable feed failure chosen by the model (capability missing etc.);
        // the UI resolves it to its MR string. Transport failures stay in [error] (raw text).
        val errorRes: StringResource? = null,
        // FOLLOWING all-failed fan-out: (failed, total) creator-stream counts so the screen can
        // show a localized summary instead of the raw "'creator': msg; …" concatenation.
        val errorCounts: Pair<Int, Int>? = null,
        // B1: seconds until the automatic rate-limit retry (0 = none); drives the snackbar.
        val retryAfterSec: Int = 0,
        // B2 sleep timer: countdown seconds remaining (0 = off) / "pause after this video" flag.
        val sleepTimerRemainingSec: Int = 0,
        val sleepAtVideoEnd: Boolean = false,
        // Transient append failure while the feed is non-empty; surfaced as a snackbar.
        val pageError: String? = null,
        // Login returned false (rejected credentials) — distinct from a transport error so
        // the dialog can show the localized rejection message.
        val loginRejected: Boolean = false,
        // Save actions of the content-preference / blocked-tag sheets failed (localized text).
        val contentPreferencesSaveFailed: Boolean = false,
        val blockedTagsSaveFailed: Boolean = false,
        // Session-scoped "tap to unmute" pill: visible while the user has not decided sound.
        val showUnmuteHint: Boolean = false,
        // For You unseen policy (durable consumption history). [unseenOnly] mirrors the user
        // preference ("show watched" is its OFF state, not a history wipe); [unseenHidden]
        // counts reels dropped because they were already watched/skipped in this generation;
        // [unseenScanStopped] means the bounded refill budget ran out — which is NOT the same
        // as "everything has been seen".
        val unseenOnly: Boolean = true,
        val unseenHidden: Int = 0,
        val unseenScanStopped: Boolean = false,
    ) {
        /**
         * Like identity for the playlist slot: the offline playlist merges several sources,
         * where a raw videoId is ambiguous; single-source feeds keep the plain id.
         */
        fun likeKey(videoId: String, itemIndex: Int): String =
            if (isOffline && itemIndex >= 0) {
                "${offlineSourceIds.getOrNull(itemIndex) ?: currentSourceId}:$videoId"
            } else {
                videoId
            }

        fun isLiked(videoId: String, itemIndex: Int): Boolean =
            likeKey(videoId, itemIndex) in likedIds
    }
}

/**
 * Constrained shuffle of one FOLLOWING batch: uniform-random order with no two videos of
 * the same creator back to back whenever a valid arrangement exists (the largest creator
 * holds at most ceil(n/2) items); degenerate batches keep the unavoidable minimum of runs.
 * The batch head also avoids [prevTailAuthor] so an appended batch does not extend the
 * creator run at the feed's tail. Keys on the stream creator (always non-null), never on
 * [ShortVideoItem.author]. Pure and retry-safe: [entries] is not mutated.
 */
internal fun shuffleFollowingBatch(
    entries: List<Pair<String, ShortVideoItem>>,
    prevTailAuthor: String?,
    random: Random = Random.Default,
): List<Pair<String, ShortVideoItem>> {
    if (entries.size < 2) return entries
    val groups = LinkedHashMap<String, ArrayDeque<Pair<String, ShortVideoItem>>>()
    for (entry in entries.shuffled(random)) {
        groups.getOrPut(entry.first) { ArrayDeque() } += entry
    }
    val result = ArrayList<Pair<String, ShortVideoItem>>(entries.size)
    var prev = prevTailAuthor
    while (result.size < entries.size) {
        val candidates = groups.entries.filter { (creator, queue) -> creator != prev && queue.isNotEmpty() }
        val pick = if (candidates.isEmpty()) {
            // Only the previous author's queue is left: the run is unavoidable.
            groups.entries.first { (_, queue) -> queue.isNotEmpty() }
        } else {
            // Most-frequent-first keeps the arrangement feasible whenever one exists;
            // ties break at random so every reset lands on a different order.
            val maxSize = candidates.maxOf { (_, queue) -> queue.size }
            val top = candidates.filter { (_, queue) -> queue.size == maxSize }
            top[random.nextInt(top.size)]
        }
        result += pick.value.removeFirst()
        prev = pick.key
    }
    return result
}
