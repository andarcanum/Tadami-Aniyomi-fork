package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.AnimeFeedErrorClassifierSource
import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.FeedError
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/** Health and availability of one plugin inside the multi-source Mix feed. */
enum class MixSourceStatus {
    ACTIVE,
    NEEDS_AUTH,
    COOLDOWN,
    CIRCUIT_OPEN,
    EXHAUSTED,
    EXCLUDED,
}

/** One reel slot produced by [ReelsMultiSourceCoordinator], retaining its originating plugin. */
data class MixSlot(
    val sourceId: Long,
    val sourceName: String,
    val item: ShortVideoItem,
)

/** Result of pulling and interleaving one batch across all active Mix streams. */
data class MixBatchResult(
    val slots: List<MixSlot>,
    val hasMore: Boolean,
    val statuses: Map<Long, MixSourceStatus>,
    val consumedHiddenCount: Int = 0,
    val failedCount: Int = 0,
    val activeCount: Int = 0,
)

/** Filter decision supplied by the host before items enter a stream's buffer. */
data class MixFilterVerdict(
    val kept: List<ShortVideoItem>,
    val unseenFilteredCount: Int = 0,
)

/**
 * Multi-plugin Mix feed orchestrator implementing **Jittered Micro-Batch Round-Robin**.
 *
 * Each installed [AnimeFeedSource] keeps its own independent pagination state (contract v17 sticky
 * cursor + integer page counter), bounded lookahead buffer, composite deduplication set, and
 * fault isolation (timeouts, per-source cooldowns, session auth quarantine, and circuit breaker).
 */
class ReelsMultiSourceCoordinator(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val random: Random = Random.Default,
    private val nowMonoMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val nowWallMs: () -> Long = { System.currentTimeMillis() },
    private val maxConcurrency: Int = DEFAULT_MAX_CONCURRENCY,
    private val sourceTimeoutMs: Long = DEFAULT_SOURCE_TIMEOUT_MS,
    private val startupGraceMs: Long = DEFAULT_STARTUP_GRACE_MS,
    private val progressiveMinSources: Int = DEFAULT_PROGRESSIVE_MIN_SOURCES,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val maxEmptyRefills: Int = DEFAULT_MAX_EMPTY_REFILLS,
    private val circuitBreakerThreshold: Int = DEFAULT_CIRCUIT_BREAKER_THRESHOLD,
    private val rateLimitCooldownMs: Long = DEFAULT_RATE_LIMIT_COOLDOWN_MS,
    private val transientCooldownMs: Long = DEFAULT_TRANSIENT_COOLDOWN_MS,
) {

    companion object {
        const val DEFAULT_MAX_CONCURRENCY = 4
        const val DEFAULT_SOURCE_TIMEOUT_MS = 7_000L
        const val DEFAULT_STARTUP_GRACE_MS = 2_000L
        const val DEFAULT_PROGRESSIVE_MIN_SOURCES = 2
        const val DEFAULT_BATCH_SIZE = 18
        const val DEFAULT_MAX_EMPTY_REFILLS = 6
        const val DEFAULT_CIRCUIT_BREAKER_THRESHOLD = 3
        const val DEFAULT_RATE_LIMIT_COOLDOWN_MS = 30_000L
        const val DEFAULT_TRANSIENT_COOLDOWN_MS = 5_000L
        const val MIN_BURST_SIZE = 1
        const val MAX_BURST_SIZE = 3

        private val HTTP_AUTH_REGEX = Regex("\\b(?:401|403)\\b")
        private val HTTP_RATE_LIMIT_REGEX = Regex("\\b429\\b")
        private val RETRY_AFTER_REGEX = Regex("(?i)retry[-_ ]?after[:= ]+(\\d{1,4})")
        private val URL_REDACT_REGEX = Regex("https?://\\S+")
        private val ELIGIBLE_ACTIVE_STATUSES = setOf(
            MixSourceStatus.ACTIVE,
            MixSourceStatus.COOLDOWN,
        )
        private val CONTINUABLE_STATUSES = setOf(
            MixSourceStatus.ACTIVE,
            MixSourceStatus.COOLDOWN,
        )
    }

    private class MixSourceStream(
        val source: AnimeFeedSource,
        var excluded: Boolean,
    ) {
        var filters: AnimeFilterList? = null
        var pagination: ReelsPagination = ReelsPagination()
        var exhausted: Boolean = false
        var status: MixSourceStatus = if (excluded) MixSourceStatus.EXCLUDED else MixSourceStatus.ACTIVE
        var cooldownUntilMonoMs: Long = 0L
        var consecutiveFailures: Int = 0
        var emptyRefillCount: Int = 0
        var fetching: Boolean = false
        var lastFetchedGeneration: Long = -1L
        val buffer: ArrayDeque<ShortVideoItem> = ArrayDeque()
        val seenIds: HashSet<String> = HashSet()
    }

    private val stateMutex = Mutex()
    private val streams = LinkedHashMap<Long, MixSourceStream>()
    private var generation: Long = 0L
    private var activeSessionToken: Long? = null
    private var minValidSessionToken: Long = 0L
    private var generationJob: Job? = null
    private var generationScope: CoroutineScope? = null
    private var lastEmittedSourceId: Long? = null
    private var burstSize: Int = 2
    private val excludedIds = LinkedHashSet<Long>()
    private val updateSeq = MutableStateFlow(0L)

    /** Initializes or resets a Mix generation across [sources]. */
    suspend fun reset(
        parentScope: CoroutineScope,
        sources: List<AnimeFeedSource>,
        excludedSourceIds: Set<Long>,
        burst: Int,
        sessionToken: Long? = null,
    ) {
        val logDetails = stateMutex.withLock {
            if (sessionToken != null && sessionToken < minValidSessionToken) {
                return@withLock "skipped_stale token=$sessionToken minValid=$minValidSessionToken"
            }
            activeSessionToken = sessionToken
            generationJob?.cancel()
            val parentJob = parentScope.coroutineContext[Job]
            val job = SupervisorJob(parentJob)
            generationJob = job
            generationScope = CoroutineScope(parentScope.coroutineContext + job + ioDispatcher)
            generation++
            lastEmittedSourceId = null
            burstSize = burst.coerceIn(MIN_BURST_SIZE, MAX_BURST_SIZE)
            excludedIds.clear()
            excludedIds.addAll(excludedSourceIds)
            streams.clear()
            for (source in sources) {
                streams[source.id] = MixSourceStream(
                    source = source,
                    excluded = source.id in excludedIds,
                )
            }
            updateSeq.value++
            val srcSummary = sources.joinToString { s ->
                "${s.id}(${s.name},excl=${s.id in excludedIds})"
            }
            "gen=$generation token=$sessionToken burst=$burstSize sources=${sources.size} [$srcSummary]"
        }
        ReelsMixDiagnosticLog.log("coord_reset", logDetails)
    }

    /** Stops any active background fetches for the current Mix generation. */
    suspend fun stop(sessionToken: Long? = null) {
        stateMutex.withLock {
            if (sessionToken != null) {
                if (sessionToken > minValidSessionToken) {
                    minValidSessionToken = sessionToken
                }
                val currentToken = activeSessionToken
                if (currentToken != null && currentToken >= sessionToken) {
                    return@withLock
                }
            }
            activeSessionToken = null
            generationJob?.cancel()
            generationJob = null
            generationScope = null
            generation++
            streams.values.forEach { it.fetching = false }
            updateSeq.value++
        }
    }

    /** Updates burst size (clamped to 1..3) without discarding buffered items or cursors. */
    suspend fun setBurstSize(burst: Int) {
        stateMutex.withLock {
            burstSize = burst.coerceIn(MIN_BURST_SIZE, MAX_BURST_SIZE)
        }
    }

    /** Updates the excluded source set dynamically, clearing queued buffers for excluded streams. */
    suspend fun updateExcludedSources(excludedSourceIds: Set<Long>) {
        stateMutex.withLock {
            excludedIds.clear()
            excludedIds.addAll(excludedSourceIds)
            for (stream in streams.values) {
                val nowExcluded = stream.source.id in excludedIds
                if (nowExcluded != stream.excluded) {
                    stream.excluded = nowExcluded
                    if (nowExcluded) {
                        stream.buffer.clear()
                        stream.status = MixSourceStatus.EXCLUDED
                    } else if (stream.status == MixSourceStatus.EXCLUDED) {
                        stream.status = if (stream.exhausted && stream.buffer.isEmpty()) {
                            MixSourceStatus.EXHAUSTED
                        } else {
                            MixSourceStatus.ACTIVE
                        }
                    }
                }
            }
            updateSeq.value++
        }
    }

    /** Synchronizes installed feed sources without resetting untouched active streams. */
    suspend fun syncSources(sources: List<AnimeFeedSource>) {
        stateMutex.withLock {
            val activeIds = sources.mapTo(HashSet()) { it.id }
            streams.keys.retainAll(activeIds)
            for (source in sources) {
                if (source.id !in streams) {
                    streams[source.id] = MixSourceStream(
                        source = source,
                        excluded = source.id in excludedIds,
                    )
                }
            }
            updateSeq.value++
        }
    }

    /** Removes any newly hidden video IDs or authors from still-buffered queues. */
    suspend fun purgeHidden(sourceId: Long, hiddenVideoIds: Set<String>, hiddenAuthors: Set<String>) {
        if (hiddenVideoIds.isEmpty() && hiddenAuthors.isEmpty()) return
        stateMutex.withLock {
            val stream = streams[sourceId] ?: return@withLock
            if (stream.buffer.isEmpty()) return@withLock
            val kept = stream.buffer.filterNot { item ->
                item.id in hiddenVideoIds || (item.author != null && item.author in hiddenAuthors)
            }
            if (kept.size != stream.buffer.size) {
                stream.buffer.clear()
                stream.buffer.addAll(kept)
            }
        }
    }

    /**
     * Pulls pages on demand (only for sources with `buffer.size < burstSize`) and interleaves the
     * next micro-batch of [MixSlot]s in shuffled round-robin source order.
     */
    suspend fun nextBatch(
        isInitial: Boolean,
        filterCandidates: suspend (Long, List<ShortVideoItem>) -> MixFilterVerdict,
    ): MixBatchResult {
        val semaphore = Semaphore(maxConcurrency.coerceAtLeast(1))
        val batchConsumedHidden = AtomicInteger(0)
        val batchFailures = AtomicInteger(0)

        val activeJobAndStreams: Triple<Long, CoroutineScope, List<MixSourceStream>>? = stateMutex.withLock {
            val gen = generation
            val scope = generationScope ?: return@withLock null
            val now = nowMonoMs()
            val toFetch = mutableListOf<MixSourceStream>()
            for (stream in streams.values) {
                if (stream.excluded) continue
                if (stream.status == MixSourceStatus.COOLDOWN && now >= stream.cooldownUntilMonoMs) {
                    stream.status = if (stream.exhausted) MixSourceStatus.EXHAUSTED else MixSourceStatus.ACTIVE
                }
                if (stream.status == MixSourceStatus.ACTIVE &&
                    !stream.exhausted &&
                    !stream.fetching &&
                    stream.buffer.size < burstSize
                ) {
                    stream.fetching = true
                    toFetch += stream
                }
            }
            Triple(gen, scope, toFetch)
        }

        if (activeJobAndStreams == null) {
            return MixBatchResult(
                slots = emptyList(),
                hasMore = false,
                statuses = currentStatuses(),
            )
        }

        val (currentGen, launchScope, streamsToFetch) = activeJobAndStreams

        val eligibleAtStart = stateMutex.withLock {
            streams.values.count {
                !it.excluded && it.status in ELIGIBLE_ACTIVE_STATUSES
            }
        }
        val fetchList = streamsToFetch.joinToString { "${it.source.id}:${it.source.name}" }
        ReelsMixDiagnosticLog.log(
            "coord_batch_start",
            "gen=$currentGen initial=$isInitial eligible=$eligibleAtStart " +
                "toFetch=${streamsToFetch.size}[$fetchList]",
        )

        streamsToFetch.forEach { stream ->
            launchScope.launch {
                try {
                    semaphore.withPermit {
                        fetchStreamUntilReady(
                            stream = stream,
                            expectedGeneration = currentGen,
                            launchScope = launchScope,
                            filterCandidates = filterCandidates,
                            onFiltered = { hiddenDelta -> batchConsumedHidden.addAndGet(hiddenDelta) },
                            onFailed = { batchFailures.incrementAndGet() },
                        )
                    }
                } catch (e: CancellationException) {
                    withContext(NonCancellable) {
                        stateMutex.withLock {
                            if (generation == currentGen) {
                                stream.fetching = false
                                updateSeq.value++
                            }
                        }
                    }
                    throw e
                }
            }
        }

        awaitBatchReadiness(
            expectedGeneration = currentGen,
            isInitial = isInitial,
            eligibleAtStart = eligibleAtStart,
        )

        return stateMutex.withLock {
            if (generation != currentGen) {
                ReelsMixDiagnosticLog.log(
                    "coord_batch_stale",
                    "expectedGen=$currentGen currentGen=$generation initial=$isInitial",
                )
                return@withLock MixBatchResult(
                    slots = emptyList(),
                    hasMore = false,
                    statuses = currentStatusesLocked(),
                )
            }
            val emitted = drainMicroBatchesLocked(batchSize)
            updateTerminalStatusesLocked()
            val statuses = currentStatusesLocked()
            val activeCount = statuses.values.count { it == MixSourceStatus.ACTIVE || it == MixSourceStatus.COOLDOWN }
            val hasMore = streams.values.any { stream ->
                !stream.excluded && (
                    stream.buffer.isNotEmpty() ||
                        stream.fetching ||
                        (!stream.exhausted && stream.status in CONTINUABLE_STATUSES)
                    )
            }
            val runsSummary = formatBatchRuns(emitted)
            val streamsSummary = formatStreamsSummaryLocked()
            ReelsMixDiagnosticLog.log(
                "coord_batch_done",
                "gen=$currentGen initial=$isInitial emitted=${emitted.size} " +
                    "runs=[$runsSummary] hasMore=$hasMore hidden=${batchConsumedHidden.get()} " +
                    "fail=${batchFailures.get()} streams=[$streamsSummary]",
            )
            MixBatchResult(
                slots = emitted,
                hasMore = hasMore,
                statuses = statuses,
                consumedHiddenCount = batchConsumedHidden.get(),
                failedCount = batchFailures.get(),
                activeCount = activeCount,
            )
        }
    }

    /** Current per-source availability snapshot. */
    suspend fun currentStatuses(): Map<Long, MixSourceStatus> = stateMutex.withLock {
        currentStatusesLocked()
    }

    /** Suspends until all in-flight background fetches for the current generation settle. */
    internal suspend fun awaitPendingFetches() {
        val children = stateMutex.withLock { generationJob?.children?.toList().orEmpty() }
        children.forEach { it.join() }
    }

    private suspend fun fetchStreamUntilReady(
        stream: MixSourceStream,
        expectedGeneration: Long,
        launchScope: CoroutineScope,
        filterCandidates: suspend (Long, List<ShortVideoItem>) -> MixFilterVerdict,
        onFiltered: (Int) -> Unit,
        onFailed: () -> Unit,
    ) {
        val attemptActive = AtomicBoolean(true)
        val outcomeDeferred = CompletableDeferred<Boolean>()
        var workerJob: Job? = null
        try {
            workerJob = launchScope.launch {
                val ok = try {
                    fetchPagesIntoBuffer(
                        stream = stream,
                        expectedGeneration = expectedGeneration,
                        isAttemptActive = { attemptActive.get() },
                        filterCandidates = filterCandidates,
                        onFiltered = onFiltered,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    if (attemptActive.get()) {
                        stateMutex.withLock {
                            if (generation == expectedGeneration && attemptActive.get()) {
                                applyFailureLocked(stream, t)
                            }
                        }
                    }
                    false
                }
                outcomeDeferred.complete(ok)
            }

            val outcome = withTimeoutOrNull(sourceTimeoutMs) {
                outcomeDeferred.await()
            }
            if (outcome == null) {
                // Fence off late blocking I/O returns first, then cancel cooperatively so the
                // semaphore permit is freed immediately without waiting for uninterruptible sockets.
                attemptActive.set(false)
                workerJob.cancel()
                ReelsMixDiagnosticLog.log(
                    "coord_timeout",
                    "gen=$expectedGeneration src=${stream.source.id}(${stream.source.name}) timeoutMs=$sourceTimeoutMs",
                )
                onFailed()
                stateMutex.withLock {
                    if (generation != expectedGeneration) return@withLock
                    applyFailureLocked(stream, error = null)
                }
            } else if (!outcome) {
                onFailed()
            }
        } catch (e: CancellationException) {
            attemptActive.set(false)
            workerJob?.cancel()
            throw e
        } finally {
            withContext(NonCancellable) {
                stateMutex.withLock {
                    if (generation == expectedGeneration) {
                        stream.fetching = false
                        updateSeq.value++
                    }
                }
            }
        }
    }

    /**
     * Fetches up to [maxEmptyRefills] pages for [stream] if filtered candidates come back empty
     * (all unseen/hidden), respecting contract v17 sticky cursor pagination. Returns `true` when
     * the fetch ended without error, or `false` if a source error occurred.
     */
    private suspend fun fetchPagesIntoBuffer(
        stream: MixSourceStream,
        expectedGeneration: Long,
        isAttemptActive: () -> Boolean,
        filterCandidates: suspend (Long, List<ShortVideoItem>) -> MixFilterVerdict,
        onFiltered: (Int) -> Unit,
    ): Boolean {
        while (true) {
            val (cachedFilters, pagination) = stateMutex.withLock {
                if (generation != expectedGeneration || !isAttemptActive() || stream.excluded || stream.exhausted) {
                    return true
                }
                stream.filters to stream.pagination
            }
            val filters = cachedFilters ?: run {
                val resolved = try {
                    stream.source.getFilterList()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    AnimeFilterList()
                }
                stateMutex.withLock {
                    if (generation != expectedGeneration || !isAttemptActive()) return true
                    stream.filters = resolved
                }
                resolved
            }

            val fetchStartMs = nowMonoMs()
            val reqPage = pagination.nextPage
            val reqCursorMode = pagination.cursorMode
            val hadCursor = !pagination.nextCursor.isNullOrBlank()
            val page = try {
                val cursor = if (reqCursorMode) pagination.nextCursor else null
                stream.source.getFeed(reqPage, cursor, filters)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                stateMutex.withLock {
                    if (generation != expectedGeneration || !isAttemptActive()) return false
                    applyFailureLocked(stream, t)
                }
                return false
            }
            val durMs = (nowMonoMs() - fetchStartMs).coerceAtLeast(0L)

            val deduplicatedInput = stateMutex.withLock {
                if (generation != expectedGeneration || !isAttemptActive()) return true
                page.videos.distinctBy { it.id }.filterNot { it.id in stream.seenIds }
            }

            val verdict = try {
                filterCandidates(stream.source.id, deduplicatedInput)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                MixFilterVerdict(kept = deduplicatedInput, unseenFilteredCount = 0)
            }

            if (!isAttemptActive()) return true
            if (verdict.unseenFilteredCount > 0) {
                onFiltered(verdict.unseenFilteredCount)
            }

            var logBuf = 0
            var logExhausted = false
            var logEmptyRefills = 0
            val shouldRefillEmpty = stateMutex.withLock {
                if (generation != expectedGeneration || !isAttemptActive()) return true
                stream.consecutiveFailures = 0
                stream.lastFetchedGeneration = expectedGeneration
                page.videos.forEach { stream.seenIds += it.id }

                val step = stream.pagination.advance(page.hasNextPage, page.nextCursor)
                stream.pagination = step.pagination
                val noMoreRemotePages = !step.pagination.canLoadMore

                if (verdict.kept.isNotEmpty()) {
                    stream.emptyRefillCount = 0
                    val accepted = if (stream.excluded) {
                        emptyList()
                    } else {
                        verdict.kept
                    }
                    stream.buffer.addAll(accepted)
                } else {
                    stream.emptyRefillCount++
                }

                val cappedEmpty = verdict.kept.isEmpty() && stream.emptyRefillCount >= maxEmptyRefills
                if (noMoreRemotePages || cappedEmpty) {
                    stream.exhausted = true
                }

                logBuf = stream.buffer.size
                logExhausted = stream.exhausted
                logEmptyRefills = stream.emptyRefillCount

                stream.buffer.isEmpty() && !stream.exhausted && !stream.excluded &&
                    stream.status == MixSourceStatus.ACTIVE
            }

            ReelsMixDiagnosticLog.log(
                "coord_page",
                "gen=$expectedGeneration src=${stream.source.id}(${stream.source.name}) " +
                    "page=$reqPage cursorMode=$reqCursorMode hadCursor=$hadCursor " +
                    "raw=${page.videos.size} afterMem=${deduplicatedInput.size} " +
                    "kept=${verdict.kept.size} unseenFilt=${verdict.unseenFilteredCount} " +
                    "durMs=$durMs buf=$logBuf hasNext=${page.hasNextPage} " +
                    "nextCursor=${!page.nextCursor.isNullOrBlank()} ex=$logExhausted " +
                    "emptyRefills=$logEmptyRefills refill=$shouldRefillEmpty",
            )

            if (!shouldRefillEmpty) {
                return true
            }
        }
    }

    private fun applyFailureLocked(stream: MixSourceStream, error: Throwable?) {
        val verdict = if (error != null && stream.source is AnimeFeedErrorClassifierSource) {
            runCatching { (stream.source as AnimeFeedErrorClassifierSource).classifyFeedError(error) }
                .getOrNull()
        } else {
            null
        }

        val message = error?.message.orEmpty()
        when (verdict) {
            FeedError.ExpiredCursor -> {
                if (stream.pagination.nextPage > 1) {
                    stream.exhausted = true
                    if (stream.buffer.isEmpty()) stream.status = MixSourceStatus.EXHAUSTED
                } else {
                    bumpCircuitBreakerLocked(stream)
                }
            }
            FeedError.AuthRequired -> {
                stream.status = MixSourceStatus.NEEDS_AUTH
                stream.exhausted = true
            }
            is FeedError.RateLimited -> {
                val delta = verdict.retryAtEpochMillis
                    ?.let { (it - nowWallMs()).coerceIn(5_000L, 120_000L) }
                    ?: rateLimitCooldownMs
                stream.status = MixSourceStatus.COOLDOWN
                stream.cooldownUntilMonoMs = nowMonoMs() + delta
            }
            FeedError.Terminal -> {
                stream.consecutiveFailures = circuitBreakerThreshold
                stream.status = MixSourceStatus.CIRCUIT_OPEN
                stream.exhausted = true
            }
            FeedError.Transient -> {
                bumpCircuitBreakerLocked(stream)
            }
            null -> {
                when {
                    error != null && HTTP_AUTH_REGEX.containsMatchIn(message) -> {
                        stream.status = MixSourceStatus.NEEDS_AUTH
                        stream.exhausted = true
                    }
                    error != null && HTTP_RATE_LIMIT_REGEX.containsMatchIn(message) -> {
                        val parsedSec = RETRY_AFTER_REGEX.find(message)
                            ?.groupValues
                            ?.getOrNull(1)
                            ?.toLongOrNull()
                            ?.coerceIn(5L, 120L)
                        val cooldown = parsedSec?.times(1_000L) ?: rateLimitCooldownMs
                        stream.status = MixSourceStatus.COOLDOWN
                        stream.cooldownUntilMonoMs = nowMonoMs() + cooldown
                    }
                    else -> bumpCircuitBreakerLocked(stream)
                }
            }
        }
        val errSummary = if (error == null) {
            "timeout"
        } else {
            val sanitized = error.message.orEmpty()
                .replace(URL_REDACT_REGEX, "<url>")
                .replace('\n', ' ')
                .take(120)
            "${error::class.simpleName}:$sanitized"
        }
        ReelsMixDiagnosticLog.log(
            "coord_error",
            "src=${stream.source.id}(${stream.source.name}) verdict=${verdict?.let {
                it::class.simpleName
            } ?: "null"} status=${stream.status} failures=${stream.consecutiveFailures} err=$errSummary",
        )
    }

    private fun bumpCircuitBreakerLocked(stream: MixSourceStream) {
        stream.consecutiveFailures++
        if (stream.consecutiveFailures >= circuitBreakerThreshold) {
            stream.status = MixSourceStatus.CIRCUIT_OPEN
            stream.exhausted = true
        } else {
            stream.status = MixSourceStatus.COOLDOWN
            stream.cooldownUntilMonoMs = nowMonoMs() + (transientCooldownMs * stream.consecutiveFailures)
        }
    }

    private suspend fun awaitBatchReadiness(
        expectedGeneration: Long,
        isInitial: Boolean,
        eligibleAtStart: Int,
    ) {
        val waitStart = nowMonoMs()
        val fastTarget = if (eligibleAtStart >= 2) {
            minOf(progressiveMinSources, eligibleAtStart)
        } else {
            1
        }
        var graceTimedOut = false

        while (true) {
            val check = stateMutex.withLock {
                if (generation != expectedGeneration) {
                    ReelsMixDiagnosticLog.log(
                        "coord_readiness_stale",
                        "expectedGen=$expectedGeneration currentGen=$generation initial=$isInitial",
                    )
                    return
                }
                val readyWithItems = streams.values.count {
                    !it.excluded && it.buffer.isNotEmpty()
                }
                val totalBuffered = streams.values
                    .filter { !it.excluded }
                    .sumOf { it.buffer.size }
                val inFlight = streams.values.count {
                    !it.excluded && it.status == MixSourceStatus.ACTIVE && it.fetching
                }
                val elapsed = (nowMonoMs() - waitStart).coerceAtLeast(0L)
                val graceReached = elapsed >= startupGraceMs || graceTimedOut
                val readyEnough = when {
                    inFlight == 0 -> true
                    isInitial -> {
                        val effectiveTarget = minOf(fastTarget, readyWithItems + inFlight)
                        (readyWithItems >= effectiveTarget && (readyWithItems >= fastTarget || graceReached)) ||
                            (readyWithItems >= 1 && graceReached)
                    }
                    else -> {
                        (totalBuffered >= batchSize && readyWithItems >= minOf(2, readyWithItems + inFlight)) ||
                            (readyWithItems >= 1 && graceReached)
                    }
                }
                if (readyEnough) {
                    ReelsMixDiagnosticLog.log(
                        "coord_readiness",
                        "gen=$expectedGeneration initial=$isInitial elapsedMs=$elapsed grace=$graceReached ready=$readyWithItems buffered=$totalBuffered inFlight=$inFlight",
                    )
                }
                Triple(
                    readyEnough,
                    if (readyWithItems >= 1 && !graceTimedOut) (startupGraceMs - elapsed).coerceAtLeast(1L) else null,
                    updateSeq.value,
                )
            }

            val (done, remainingGraceMs, seqBeforeWait) = check
            if (done) return

            if (remainingGraceMs != null) {
                val woken = withTimeoutOrNull(remainingGraceMs) {
                    updateSeq.first { it != seqBeforeWait }
                }
                if (woken == null) graceTimedOut = true
            } else {
                updateSeq.first { it != seqBeforeWait }
            }
        }
    }

    private fun drainMicroBatchesLocked(targetCount: Int): List<MixSlot> {
        val emitted = ArrayList<MixSlot>(targetCount)
        while (emitted.size < targetCount) {
            val cycle = streams.values.filter {
                !it.excluded && it.buffer.isNotEmpty()
            }.shuffled(random).toMutableList()

            if (cycle.isEmpty()) break

            // Never drain a subset of fast/warm sources repeatedly into one batch while peer
            // plugins are still fetching without items yet: emit at most one round so incoming
            // peers interleave right behind.
            if (emitted.isNotEmpty()) {
                val readyIds = cycle.map { it.source.id }.toSet()
                val fetchingPeers = streams.values.count {
                    !it.excluded && it.fetching && it.buffer.isEmpty() && it.source.id !in readyIds
                }
                if (fetchingPeers > 0) {
                    ReelsMixDiagnosticLog.log(
                        "coord_drain_stop_peers",
                        "readySources=${cycle.size} fetchingPeers=$fetchingPeers emittedSoFar=${emitted.size}",
                    )
                    break
                }
            }

            if (cycle.size > 1 && cycle.first().source.id == lastEmittedSourceId) {
                val swapIndex = 1 + random.nextInt(cycle.size - 1)
                val first = cycle[0]
                cycle[0] = cycle[swapIndex]
                cycle[swapIndex] = first
            }

            var progressed = false
            for (stream in cycle) {
                if (emitted.size >= targetCount) break
                if (stream.buffer.isEmpty()) continue
                val takeCount = minOf(burstSize, stream.buffer.size, targetCount - emitted.size)
                repeat(takeCount) {
                    val item = stream.buffer.removeFirst()
                    emitted += MixSlot(
                        sourceId = stream.source.id,
                        sourceName = stream.source.name,
                        item = item,
                    )
                    lastEmittedSourceId = stream.source.id
                    progressed = true
                }
            }
            if (!progressed) break
        }
        return emitted
    }

    private fun updateTerminalStatusesLocked() {
        for (stream in streams.values) {
            if (!stream.excluded &&
                stream.status == MixSourceStatus.ACTIVE &&
                stream.exhausted &&
                stream.buffer.isEmpty() &&
                !stream.fetching
            ) {
                stream.status = MixSourceStatus.EXHAUSTED
            }
        }
    }

    private fun currentStatusesLocked(): Map<Long, MixSourceStatus> =
        streams.mapValues { (_, stream) -> stream.status }

    private fun formatBatchRuns(slots: List<MixSlot>): String {
        if (slots.isEmpty()) return ""
        val parts = ArrayList<String>()
        var currentId = slots.first().sourceId
        var currentName = slots.first().sourceName
        var count = 0
        for (slot in slots) {
            if (slot.sourceId == currentId) {
                count++
            } else {
                parts += "$currentId($currentName)x$count"
                currentId = slot.sourceId
                currentName = slot.sourceName
                count = 1
            }
        }
        parts += "$currentId($currentName)x$count"
        return parts.joinToString()
    }

    private fun formatStreamsSummaryLocked(): String =
        streams.values.joinToString { s ->
            "${s.source.id}:buf=${s.buffer.size},fetch=${s.fetching},ex=${s.exhausted},excl=${s.excluded},st=${s.status}"
        }
}
