package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.AnimeFeedReplaySource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.FeedReplayContext
import eu.kanade.tachiyomi.animesource.model.FeedReplayItemResult
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.domain.reels.anime.model.ReelsActiveCheckpoint
import tachiyomi.domain.reels.anime.model.ReelsCandidate
import tachiyomi.domain.reels.anime.model.ReelsSessionHandle
import tachiyomi.domain.reels.anime.model.ReelsSessionKey
import tachiyomi.domain.reels.anime.model.ReelsSessionWindow
import tachiyomi.domain.reels.anime.repository.ReelsSessionRepository
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Result of a successful session restore.
 */
data class RestoredReelsSession(
    val handle: ReelsSessionHandle,
    val initialItems: List<ShortVideoItem>,
    val seekItemId: String?,
    val seekPositionMs: Long?,
    val localHasMore: Boolean,
    val deferredCount: Int,
)

/**
 * Session coordinator for Telegram Reels continuity (feed contract v27).
 * Owns session handle, local window ordinal, coalesced checkpoint writes,
 * and bounded rehydration cache.
 */
class ReelsSessionContinuity(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val repository: ReelsSessionRepository,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val wallClock: () -> Date = { Date() },
    private val isCurrentKey: (ReelsSessionKey) -> Boolean = { true },
    private val onContextInvalidated: (ReelsSessionKey, FeedReplayContext?) -> Unit = { _, _ -> },
    private val isObservedKey: (ReelsSessionKey) -> Boolean = isCurrentKey,
) {

    private companion object {
        /** Upper bound of network resolves during cache-first restore; the rest stay lazy. */
        const val MAX_EAGER_RESTORE_RESOLVES = 6
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val resolveSemaphore = Semaphore(2)
    private val mediaCache = ConcurrentHashMap<String, ShortVideoItem>()
    private val inFlightResolves = ConcurrentHashMap<String, Deferred<FeedReplayItemResult>>()

    @Volatile
    var activeHandle: ReelsSessionHandle? = null
        private set

    @Volatile
    private var activeSessionKey: ReelsSessionKey? = null

    @Volatile
    private var currentSource: AnimeFeedReplaySource? = null

    @Volatile
    private var currentContext: FeedReplayContext? = null

    @Volatile
    private var currentFilters: AnimeFilterList? = null

    @Volatile
    private var activeItem: ShortVideoItem? = null

    @Volatile
    private var activePositionMs: Long = 0L

    @Volatile
    private var activePersistEligible: Boolean = false

    @Volatile
    private var isCompletedLatch: Boolean = false

    @Volatile
    private var lastCheckpointWriteMs: Long = 0L

    private val currentSequence = AtomicLong(1L)
    private var lastOrdinal: Long = 0L
    private var contextObserver: Job? = null

    suspend fun restore(
        source: AnimeFeedReplaySource,
        key: ReelsSessionKey,
        context: FeedReplayContext,
        filters: AnimeFilterList,
        historyGeneration: Long,
        reset: Boolean = false,
    ): RestoredReelsSession? = withContext(ioDispatcher) {
        contextObserver?.cancel()
        inFlightResolves.values.forEach { it.cancel() }
        inFlightResolves.clear()
        mediaCache.clear()
        currentSource = source
        currentContext = context
        currentFilters = filters
        activeSessionKey = key

        val ownerNonce = UUID.randomUUID().toString()
        val handle = repository.open(key, ownerNonce, historyGeneration, reset = reset)
        activeHandle = handle
        contextObserver = scope.launch(ioDispatcher) {
            var lastObserved: FeedReplayContext? = context
            source.replayContextChanges.collect { changed ->
                if (changed != lastObserved && isObservedKey(key)) {
                    lastObserved = changed
                    val revoked = activeHandle
                    activeHandle = null
                    activeItem = null
                    activePersistEligible = false
                    mediaCache.clear()
                    inFlightResolves.values.forEach { it.cancel() }
                    inFlightResolves.clear()
                    onContextInvalidated(key, changed)
                    if (revoked != null) repository.invalidate(revoked, dropCheckpoint = false)
                }
            }
        }

        if (!isCurrentKey(key)) return@withContext null

        val items = mutableListOf<ShortVideoItem>()
        var seekItemId: String? = null
        var seekPositionMs: Long? = null

        // 1. Check active checkpoint (one qualified-watch exception). A deferred resolve must
        // not drop the resume target: the stored metadata card still leads the feed and the
        // player re-resolves its transport on activation.
        var eagerAttempts = 0
        val active = repository.active(handle)
        if (active != null) {
            eagerAttempts++
            when (val resolveResult = resolveItem(active.item.itemId)) {
                is FeedReplayItemResult.Available -> {
                    items.add(resolveResult.item)
                    seekItemId = active.item.itemId
                    seekPositionMs = active.positionMs
                }
                is FeedReplayItemResult.Deferred -> {
                    items.add(active.item.toShortVideoItem())
                    seekItemId = active.item.itemId
                    seekPositionMs = active.positionMs
                }
                else -> Unit
            }
        }

        // 2. Fetch first local window (skipping forward past consumed entries if needed)
        var window = repository.window(handle, afterOrdinal = 0L, limit = 40)
        lastOrdinal = window.nextOrdinal

        while (true) {
            // Rehydrate items in window. Eager network attempts are bounded; the rest keep their
            // stored metadata and resolve just-in-time when they become active.
            for (candidate in window.entries.map { it.candidate }) {
                if (candidate.itemId == seekItemId) continue // Already added from active
                if (eagerAttempts >= MAX_EAGER_RESTORE_RESOLVES) {
                    items.add(candidate.toShortVideoItem())
                    continue
                }
                eagerAttempts++
                when (val res = resolveItem(candidate.itemId)) {
                    is FeedReplayItemResult.Available -> items.add(res.item)
                    is FeedReplayItemResult.Deferred -> items.add(candidate.toShortVideoItem())
                    else -> Unit
                }
            }

            if (items.isNotEmpty() || !window.hasMore) break
            window = repository.window(handle, afterOrdinal = lastOrdinal, limit = 40)
            lastOrdinal = window.nextOrdinal
        }

        if (items.isEmpty() && !window.hasMore) {
            return@withContext null
        }

        RestoredReelsSession(
            handle = handle,
            initialItems = items,
            seekItemId = seekItemId,
            seekPositionMs = seekPositionMs,
            localHasMore = window.hasMore,
            deferredCount = window.deferredCount,
        )
    }

    suspend fun persistPage(candidates: List<ReelsCandidate>): Int = withContext(ioDispatcher) {
        val handle = activeHandle ?: return@withContext 0
        repository.appendPage(handle, candidates)
    }

    suspend fun loadNextWindow(): ReelsSessionWindow = withContext(ioDispatcher) {
        val handle = activeHandle ?: return@withContext ReelsSessionWindow(emptyList(), lastOrdinal, false, 0)
        val window = repository.window(handle, afterOrdinal = lastOrdinal, limit = 40)
        lastOrdinal = window.nextOrdinal
        window
    }

    suspend fun resolveItem(itemId: String): FeedReplayItemResult = withContext(ioDispatcher) {
        val requestedKey = activeSessionKey ?: return@withContext FeedReplayItemResult.ContextChanged
        if (!isCurrentKey(requestedKey)) return@withContext FeedReplayItemResult.ContextChanged
        mediaCache[itemId]?.let { return@withContext FeedReplayItemResult.Available(it) }

        val source = currentSource ?: return@withContext FeedReplayItemResult.Deferred(null)
        val context = currentContext ?: return@withContext FeedReplayItemResult.ContextChanged
        val filters = currentFilters ?: AnimeFilterList()

        val deferred = inFlightResolves.computeIfAbsent(itemId) {
            scope.async(ioDispatcher) {
                resolveSemaphore.withPermit {
                    try {
                        val result = source.resolveReplayItem(itemId, context, filters)
                        if (activeSessionKey != requestedKey || currentContext != context ||
                            !isCurrentKey(requestedKey) || source.accountScopeId() != context.accountId
                        ) {
                            return@withPermit FeedReplayItemResult.ContextChanged
                        }
                        if (result is FeedReplayItemResult.Available && result.item.id != itemId) {
                            return@withPermit FeedReplayItemResult.Deferred(null)
                        }
                        when (result) {
                            is FeedReplayItemResult.Available -> {
                                if (mediaCache.size >= 8) {
                                    val firstKey = mediaCache.keys.firstOrNull()
                                    if (firstKey != null) mediaCache.remove(firstKey)
                                }
                                mediaCache[itemId] = result.item
                            }
                            is FeedReplayItemResult.Unavailable -> {
                                activeHandle?.let { h ->
                                    repository.setAvailability(h, itemId, "unavailable", null)
                                }
                            }
                            is FeedReplayItemResult.Deferred -> {
                                activeHandle?.let { h ->
                                    repository.setAvailability(
                                        h,
                                        itemId,
                                        "deferred",
                                        result.retryAtEpochMillis?.let { Date(it) },
                                    )
                                }
                            }
                            else -> Unit
                        }
                        result
                    } finally {
                        inFlightResolves.remove(itemId)
                    }
                }
            }
        }

        try {
            deferred.await()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            FeedReplayItemResult.Deferred(null)
        }
    }

    fun visitStarted(item: ShortVideoItem, persistEligible: Boolean) {
        val prevActive = activeItem
        if (prevActive?.id == item.id) return
        if (prevActive != null) {
            flushCheckpoint(completed = isCompletedLatch)
        }
        activeItem = item
        activePersistEligible = persistEligible
        activePositionMs = 0L
        isCompletedLatch = false
        lastCheckpointWriteMs = nowMs()
        currentSequence.incrementAndGet()
    }

    fun sample(itemId: String, positionMs: Long, foreground: Boolean) {
        if (!foreground) return
        val currentActive = activeItem ?: return
        if (currentActive.id != itemId) return

        activePositionMs = positionMs
        val now = nowMs()
        if (now - lastCheckpointWriteMs >= 2000L) {
            lastCheckpointWriteMs = now
            flushCheckpoint(completed = isCompletedLatch)
        }
    }

    fun positionBoundary(itemId: String, positionMs: Long) {
        val currentActive = activeItem ?: return
        if (currentActive.id != itemId) return
        activePositionMs = positionMs
        flushCheckpoint(completed = isCompletedLatch)
    }

    fun videoCompleted(itemId: String) {
        val currentActive = activeItem ?: return
        if (currentActive.id != itemId) return
        isCompletedLatch = true
        flushCheckpoint(completed = true)
    }

    fun attachHandle(handle: ReelsSessionHandle) {
        activeHandle = handle
    }

    fun attachedHandle(): ReelsSessionHandle? = activeHandle

    fun onVisitStarted(item: ShortVideoItem, persistEligible: Boolean = true) {
        visitStarted(item, persistEligible)
    }

    fun onPlaybackSample(itemId: String, positionMs: Long, foreground: Boolean = true) {
        sample(itemId, positionMs, foreground)
    }

    fun onVisitEnded(itemId: String, positionMs: Long) {
        positionBoundary(itemId, positionMs)
    }

    fun onVideoCompleted(itemId: String) {
        videoCompleted(itemId)
    }

    fun flushActiveVisit() {
        flushCheckpoint(completed = isCompletedLatch)
    }

    fun detach(): ReelsSessionHandle? {
        contextObserver?.cancel()
        contextObserver = null
        val handle = activeHandle
        activeHandle = null
        activeSessionKey = null
        activeItem = null
        activePersistEligible = false
        activePositionMs = 0L
        currentSource = null
        currentContext = null
        currentFilters = null
        mediaCache.clear()
        inFlightResolves.values.forEach { it.cancel() }
        inFlightResolves.clear()
        return handle
    }

    suspend fun invalidate(dropCheckpoint: Boolean) {
        val handle = detach() ?: return
        withContext(ioDispatcher) { repository.invalidate(handle, dropCheckpoint) }
    }

    fun frozenFilters(): AnimeFilterList? = currentFilters

    private fun flushCheckpoint(completed: Boolean) {
        if (!activePersistEligible) return
        val handle = activeHandle ?: return
        if (!isCurrentKey(handle.key)) return
        val item = activeItem ?: return
        val pos = activePositionMs
        val seq = currentSequence.incrementAndGet()
        val candidate = item.toReelsCandidate(
            scope = handle.key.accountScopeId,
            sourceId = handle.key.sourceId,
            now = wallClock(),
            resumePositionMs = pos,
        )
        val checkpoint = ReelsActiveCheckpoint(
            item = candidate,
            positionMs = pos,
            completed = completed,
            sequence = seq,
        )
        scope.launch(ioDispatcher) {
            if (activeHandle == handle && activePersistEligible && isCurrentKey(handle.key)) {
                repository.checkpoint(handle, checkpoint)
            }
        }
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
            tags = runCatching {
                json.decodeFromString<List<String>>(tagsJson)
            }.getOrDefault(emptyList()),
            webUrl = webUrl,
            viewsCount = viewsCount,
            likesCount = likesCount,
            isAuthorVerified = isAuthorVerified,
            createdAtEpochSec = createdAtEpochSec,
        )
    }

    private fun ShortVideoItem.toReelsCandidate(
        scope: String,
        sourceId: Long,
        now: Date,
        resumePositionMs: Long = 0L,
    ): ReelsCandidate {
        return ReelsCandidate(
            accountScopeId = scope,
            sourceId = sourceId,
            itemId = id,
            discoveredAt = now,
            resumePositionMs = resumePositionMs,
            videoUrl = "",
            videoUrlHd = null,
            posterUrl = "",
            posterUrlVertical = null,
            title = title,
            author = author,
            durationSec = durationSec?.toDouble(),
            hasAudio = hasAudio,
            tagsJson = json.encodeToString(tags),
            webUrl = webUrl,
            createdAtEpochSec = createdAtEpochSec,
            availability = "available",
            lastValidatedAt = now,
            metadataSchemaVersion = ReelsSessionContext.METADATA_SCHEMA_VERSION,
            retryAfter = null,
            viewsCount = viewsCount,
            likesCount = likesCount,
            isAuthorVerified = isAuthorVerified,
            capturedContextKey = null,
        )
    }
}
