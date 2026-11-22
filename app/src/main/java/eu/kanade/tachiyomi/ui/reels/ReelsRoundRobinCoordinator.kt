package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.AnimeFeedRoundRobinSource
import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.FeedReplayContext
import eu.kanade.tachiyomi.animesource.model.FeedReplayItemResult
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinCatalogResult
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinDeferral
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinIdentity
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinReadiness
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinScanRequest
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinScanResult
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.data.reels.anime.ReelsRoundRobinStateCorruptException
import tachiyomi.domain.reels.anime.model.ReelsActiveCheckpoint
import tachiyomi.domain.reels.anime.model.ReelsCandidate
import tachiyomi.domain.reels.anime.model.ReelsHiddenEntry
import tachiyomi.domain.reels.anime.model.ReelsInboxCapacityException
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinAnchor
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinCatalog
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinCatalogSource
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinCommit
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinEntry
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinFrame
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinHandle
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinIdentity
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinMutation
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinNeed
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinSnapshot
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinStop
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinUpdate
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinValidation
import tachiyomi.domain.reels.anime.model.ReelsSessionKey
import tachiyomi.domain.reels.anime.model.ReelsSessionStaleException
import tachiyomi.domain.reels.anime.repository.ReelsCandidateRepository
import tachiyomi.domain.reels.anime.repository.ReelsConsumptionRepository
import tachiyomi.domain.reels.anime.repository.ReelsHiddenRepository
import tachiyomi.domain.reels.anime.repository.ReelsRoundRobinRepository
import tachiyomi.domain.reels.anime.repository.ReelsSessionRepository
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Immutable identity of one published round-robin generation.
 *
 * The token is an opaque app-local value (never an access token). It stays stable across append
 * and refresh operations and changes only when the account, the eligibility context or the
 * session is replaced.
 */
data class ReelsRoundRobinRenderOwner(
    val token: String,
    val accountScope: String,
    val sourceId: Long,
    val historyGeneration: Long,
    val persistEligible: Boolean,
)

/**
 * One atomic publication of the round-robin feed.
 *
 * [suffix] is the full forward projection after the monotonic [frontierItemId], not a delta.
 * The host keeps its own prefix through the frontier item and replaces only the suffix; with a
 * null frontier the publication is an initial replace.
 */
data class ReelsRoundRobinUiPage(
    val suffix: List<ShortVideoItem>,
    val frontierItemId: String?,
    val playbackItemId: String?,
    val pendingSeekPositionMs: Long?,
    val loading: Boolean,
    val stop: ReelsRoundRobinStop,
    val error: Throwable?,
    val owner: ReelsRoundRobinRenderOwner?,
)

/** The source is signed out or still resolving its identity; the host must not use the web feed. */
class ReelsRoundRobinNotReadyException(val readiness: FeedRoundRobinReadiness) :
    IllegalStateException("Round-robin feed is not ready: $readiness")

/**
 * App-local orchestrator for the v28 round-robin For You feed.
 *
 * All state changes go through one actor and repository calls are serialized by [writeMutex].
 * Scan tasks are bounded to [MAX_PARALLEL_TASKS], each captures its frozen replay context,
 * filters, checkpoint and attempt, and a result is committed only after its session/account/
 * generation fence still matches. The coordinator never writes consumption, watches or hidden
 * rows itself; eligibility is read-only, and all round-robin storage side effects go through
 * [ReelsRoundRobinRepository].
 */
class ReelsRoundRobinCoordinator(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val repository: ReelsRoundRobinRepository,
    private val sessionRepository: ReelsSessionRepository,
    private val consumptionRepository: ReelsConsumptionRepository,
    private val hiddenRepository: ReelsHiddenRepository,
    private val candidateRepository: ReelsCandidateRepository,
    private val publish: (ReelsRoundRobinUiPage) -> Unit,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val wallClock: () -> Date = { Date(nowMs()) },
    private val isCurrentGeneration: (ReelsRoundRobinRenderOwner) -> Boolean = { true },
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val memoryRepositoryFactory: ((ReelsCandidate) -> Boolean) -> ReelsRoundRobinRepository =
        { locallyEligible ->
            InMemoryReelsRoundRobinRepository(locallyEligible = locallyEligible)
        },
) {

    private companion object {
        const val PAGE_LIMIT = 12
        const val MAX_PARALLEL_TASKS = 4
        const val MAX_TURNS_PER_SLICE = 48
        const val SLICE_BUDGET_MS = 8_000L
        const val DEFAULT_RETRY_MS = 5_000L
        const val REPEATED_RETRY_MS = 60_000L
        const val MAX_RETRY_MS = 30L * 60_000L
        const val CATALOG_RETRY_MS = 60_000L
        const val CATALOG_REFRESH_MS = 60_000L
        const val CATALOG_WARM_RECONCILE_MS = 2_000L
        const val NEED_INPUT_WAKE_MS = 250L
        const val EMPTY_AUTO_INTERVAL_MS = 30_000L
        const val BACKLOG_SEED_LIMIT = ReelsForYou.BACKLOG_SEED_LIMIT
        const val DEBUG_EVENT_LIMIT = 512

        val RECOVERABLE_STOPS = setOf(
            ReelsRoundRobinStop.PAGE_READY,
            ReelsRoundRobinStop.RESERVATION_HORIZON,
        )
    }

    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val writeMutex = Mutex()
    private val operationMutex = Mutex()
    private val operationEpoch = AtomicLong(0L)
    private val operationCounter = AtomicLong(0L)
    private val attachEpoch = AtomicLong(0L)

    @Volatile
    private var activeSession: Session? = null

    private var activeOperation: ActiveOperation? = null
    private val queuedOperations = ArrayDeque<OperationRequest>()

    private var wakeJob: Job? = null
    private var eligibilityJob: Job? = null
    private var replayContextJob: Job? = null

    private val checkpointWriter = ReelsSessionContinuity(
        scope = scope,
        ioDispatcher = ioDispatcher,
        repository = sessionRepository,
        nowMs = nowMs,
        wallClock = wallClock,
        isCurrentKey = { key ->
            val session = activeSession
            session != null && !session.stopped && session.handle.session.key == key
        },
    )

    init {
        scope.launch(ioDispatcher) {
            for (command in commands) {
                try {
                    handle(command)
                } catch (e: CancellationException) {
                    // A cancelled child (priority operation, superseded attach) is not an
                    // actor failure: only a cancelled coordinator scope may stop the loop.
                    currentCoroutineContext().ensureActive()
                } catch (t: Throwable) {
                    publishCommandFailure(t)
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Public API (§7)
    // ---------------------------------------------------------------------------------------

    /**
     * [accountScope] is the host's pinned RR account scope. When present it is used verbatim
     * for the session key, the owner and the consumption generation, so the host's checkpoint
     * read and this opened session cannot diverge on a transiently unreadable identity.
     */
    suspend fun attach(
        source: AnimeFeedRoundRobinSource,
        sourceId: Long,
        filters: AnimeFilterList,
        unseenOnly: Boolean,
        incognito: Boolean,
        accountScope: String? = null,
    ) {
        val done = CompletableDeferred<Unit>()
        commands.send(
            Command.Attach(
                AttachRequest(source, sourceId, filters, unseenOnly, incognito, accountScope),
                done,
            ),
        )
        done.await()
    }

    fun loadNext() {
        commands.trySend(Command.LoadNext)
    }

    fun refresh() {
        commands.trySend(Command.Refresh)
    }

    fun activate(itemId: String) {
        commands.trySend(Command.Activate(itemId))
    }

    fun sample(itemId: String, positionMs: Long, foreground: Boolean) {
        val session = activeSession ?: return
        if (session.stopped) return
        if (session.playbackItemId != itemId && !ensurePlaybackItem(session, itemId)) return
        if (foreground) {
            session.samplePositions[itemId] = positionMs
        }
        if (!session.incognito) {
            checkpointWriter.sample(itemId, positionMs, foreground)
        }
    }

    fun positionBoundary(itemId: String, positionMs: Long) {
        val session = activeSession ?: return
        if (session.stopped) return
        if (session.playbackItemId != itemId && !ensurePlaybackItem(session, itemId)) return
        session.samplePositions[itemId] = positionMs
        if (!session.incognito) {
            checkpointWriter.positionBoundary(itemId, positionMs)
        }
    }

    fun videoCompleted(itemId: String) {
        val session = activeSession ?: return
        if (session.stopped) return
        if (session.playbackItemId != itemId && !ensurePlaybackItem(session, itemId)) return
        if (!session.incognito) {
            checkpointWriter.videoCompleted(itemId)
        }
    }

    fun foregroundLost() {
        val session = activeSession ?: return
        if (session.stopped || session.incognito) return
        checkpointWriter.flushActiveVisit()
    }

    fun stop() {
        commands.trySend(Command.Stop)
    }

    internal fun debugWakeAt(): Long? = activeSession?.scheduledWakeAt

    internal fun debugLastStop(): ReelsRoundRobinStop? = activeSession?.lastStop

    internal val debugEvents = mutableListOf<String>()

    /** Keeps the diagnostic trail bounded: an attached session wakes forever. */
    private fun debug(event: String) {
        synchronized(debugEvents) {
            debugEvents += event
            if (debugEvents.size > DEBUG_EVENT_LIMIT) {
                debugEvents.subList(0, debugEvents.size - DEBUG_EVENT_LIMIT).clear()
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Actor
    // ---------------------------------------------------------------------------------------

    private sealed interface Command {
        data class Attach(val request: AttachRequest, val done: CompletableDeferred<Unit>) : Command
        data object LoadNext : Command
        data object Refresh : Command
        data class Activate(val itemId: String) : Command
        data object Stop : Command
        data class EligibilityChanged(val session: Session) : Command
        data class ReplayContextChanged(val session: Session, val context: FeedReplayContext?) : Command
        data class Wake(val session: Session, val scheduledAt: Long) : Command
        data class OperationFinished(val operationId: Long) : Command
    }

    private class AttachRequest(
        val source: AnimeFeedRoundRobinSource,
        val sourceId: Long,
        val filters: AnimeFilterList,
        val unseenOnly: Boolean,
        val incognito: Boolean,
        val accountScope: String? = null,
    )

    private data class OperationRequest(
        val initial: Boolean = false,
        val refreshEligibility: Boolean = false,
        val forceCatalog: Boolean = false,
        val activateItemId: String? = null,
        val publishLoading: Boolean = true,
        val refreshDueRetries: Boolean = false,
    )

    private data class ActiveOperation(val id: Long, val job: Job)

    private suspend fun handle(command: Command) {
        when (command) {
            is Command.Attach -> {
                try {
                    attachInternal(command.request)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    publishStandalone(attachEpoch.get(), t)
                } finally {
                    command.done.complete(Unit)
                }
            }
            Command.LoadNext -> {
                // A user paging gesture is not the automatic rescan the horizon forbids: it
                // must still pump once so a pruned future payload is discovered and repaired.
                if (activeSession == null) return
                startOperation(OperationRequest(), priority = false)
            }
            Command.Refresh -> startOperation(
                OperationRequest(refreshEligibility = true, forceCatalog = true),
                priority = true,
            )
            is Command.Activate -> {
                activeSession?.let { it.emptyAutoUsed = false }
                startOperation(
                    OperationRequest(activateItemId = command.itemId, publishLoading = false),
                    priority = true,
                )
            }
            Command.Stop -> stopInternal()
            is Command.EligibilityChanged -> {
                if (command.session === activeSession) {
                    startOperation(
                        OperationRequest(refreshEligibility = true, publishLoading = false),
                        priority = true,
                    )
                }
            }
            is Command.ReplayContextChanged -> {
                if (command.session === activeSession) {
                    handleReplayContextChanged(command.session)
                }
            }
            is Command.Wake -> {
                debug(
                    "cmd:${command.scheduledAt}:current=${command.session.scheduledWakeAt}:" +
                        "stop=${command.session.lastStop}:active=${command.session === activeSession}",
                )
                if (command.session === activeSession &&
                    command.session.scheduledWakeAt == command.scheduledAt
                ) {
                    // The periodic catalog reconciliation may fire while the feed is parked at
                    // the reservation horizon; a retry/head-check wake still may not.
                    val catalogTick = command.session.catalogWakeAt == command.scheduledAt
                    if (catalogTick) command.session.catalogWakeAt = null
                    // A deferred catalog must be re-fetched, not skipped by the "already
                    // applied" shortcut: the wake armed at catalogRetryAt carries that intent.
                    val catalogRetry = command.session.catalogRetryForced &&
                        command.session.catalogRetryAt?.let { it <= command.scheduledAt } == true
                    if (command.session.lastStop != ReelsRoundRobinStop.RESERVATION_HORIZON ||
                        catalogTick || catalogRetry
                    ) {
                        command.session.scheduledWakeAt = null
                        startOperation(
                            OperationRequest(
                                refreshDueRetries = true,
                                forceCatalog = catalogTick || catalogRetry,
                                // A background reconciliation must not pulse the loader.
                                publishLoading = !catalogTick && !catalogRetry,
                            ),
                            priority = false,
                        )
                    }
                }
            }
            is Command.OperationFinished -> {
                if (activeOperation?.id == command.operationId) {
                    activeOperation = null
                    val next = queuedOperations.removeFirstOrNull()
                    if (next != null) launchOperation(next)
                }
            }
        }
    }

    /** Reports a command failure without letting it escape the actor loop. */
    private suspend fun publishCommandFailure(t: Throwable) {
        try {
            val session = activeSession
            if (session != null && !session.stopped) {
                publishControl(
                    session = session,
                    snapshot = session.lastSnapshot,
                    loading = false,
                    stop = ReelsRoundRobinStop.PAUSED,
                    error = t,
                )
            } else {
                publishStandalone(attachEpoch.get(), t)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // A broken publish callback must not kill the actor either.
        }
    }

    private fun startOperation(request: OperationRequest, priority: Boolean) {
        val session = activeSession ?: return
        if (session.stopped) return
        val running = activeOperation
        if (running == null) {
            launchOperation(request)
            return
        }
        if (priority) {
            operationEpoch.incrementAndGet()
            running.job.cancel()
            activeOperation = null
            queuedOperations.clear()
            launchOperation(request)
        } else {
            enqueue(request)
        }
    }

    private fun enqueue(request: OperationRequest) {
        val last = queuedOperations.removeLastOrNull()
        if (last == null) {
            queuedOperations.addLast(request)
            return
        }
        queuedOperations.addLast(
            OperationRequest(
                initial = last.initial && request.initial,
                refreshEligibility = last.refreshEligibility || request.refreshEligibility,
                forceCatalog = last.forceCatalog || request.forceCatalog,
                activateItemId = request.activateItemId ?: last.activateItemId,
                publishLoading = last.publishLoading || request.publishLoading,
                refreshDueRetries = last.refreshDueRetries || request.refreshDueRetries,
            ),
        )
    }

    private fun launchOperation(request: OperationRequest) {
        val session = activeSession ?: return
        if (session.stopped) return
        wakeJob?.cancel()
        wakeJob = null
        session.scheduledWakeAt = null
        val id = operationCounter.incrementAndGet()
        val epoch = operationEpoch.get()
        debug("launch:$id@$epoch:$request")
        val job = scope.launch(ioDispatcher) {
            try {
                operationMutex.withLock {
                    runOperation(session, request, epoch)
                }
            } finally {
                commands.trySend(Command.OperationFinished(id))
            }
        }
        activeOperation = ActiveOperation(id, job)
    }

    // ---------------------------------------------------------------------------------------
    // Attach / detach
    // ---------------------------------------------------------------------------------------

    private suspend fun attachInternal(request: AttachRequest, preserveOnFailure: Boolean = false) {
        val myEpoch = attachEpoch.incrementAndGet()
        if (preserveOnFailure) {
            // A replay-context reattach may fail (for example a transient open error). Cancel
            // the running work but keep the current session usable for later commands.
            cancelActiveWork()
        } else {
            tearDownActiveSession()
        }
        val previous = activeSession

        val prepared = try {
            prepareSession(request, myEpoch)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            if (previous != null && activeSession === previous && !previous.stopped) {
                startObservers(previous)
                publishControl(
                    session = previous,
                    snapshot = previous.lastSnapshot,
                    loading = false,
                    stop = ReelsRoundRobinStop.PAUSED,
                    error = t,
                )
            } else {
                publishStandalone(myEpoch, t)
            }
            return
        } ?: return

        if (attachEpoch.get() != myEpoch || activeSession !== previous) {
            releaseQuietly(prepared.session.repository, prepared.session.handle)
            return
        }
        if (previous != null) tearDownActiveSession()
        val session = prepared.session
        activeSession = session
        seedPlayback(session, prepared.snapshot, prepared.restoredActive)
        startObservers(session)
        publishControl(
            session = session,
            snapshot = prepared.snapshot,
            loading = true,
            stop = ReelsRoundRobinStop.PAUSED,
            error = null,
        )
        startOperation(OperationRequest(initial = true), priority = true)
    }

    private suspend fun prepareSession(request: AttachRequest, myEpoch: Long): PreparedSession? {
        val frozen = withContext(ioDispatcher) {
            val feedSource = request.source as? AnimeFeedSource
            if (feedSource != null) {
                ReelsSessionContext.freezeFilters(feedSource, request.filters)
            } else {
                FrozenReelsFilters(
                    filters = request.filters,
                    fingerprint = ReelsSessionContext.serializeFilters(request.filters),
                )
            }
        }
        val readiness = withContext(ioDispatcher) { request.source.prepareRoundRobin() }
        if (attachEpoch.get() != myEpoch) return null
        if (readiness !is FeedRoundRobinReadiness.Ready || readiness.storageKey.isBlank()) {
            throw ReelsRoundRobinNotReadyException(readiness)
        }

        val scopeId = request.accountScope?.takeIf { it.isNotBlank() }
            ?: ReelsForYou.accountScope(
                request.sourceId,
                runCatching { request.source.accountScopeId() }.getOrNull(),
            )
        val generation = withContext(ioDispatcher) {
            if (request.incognito) {
                consumptionRepository.generation(scopeId)
            } else {
                consumptionRepository.ensureScope(scopeId, ReelsForYou.POLICY_VERSION)
            }
        }
        if (attachEpoch.get() != myEpoch) return null

        val contextKey = ReelsSessionContext.computeContextKey(
            sourceId = request.sourceId,
            route = "main",
            query = "",
            unseenOnly = request.unseenOnly,
            filterFingerprint = frozen.fingerprint,
            continuityKey = readiness.storageKey + ":rr:1",
        )
        val key = ReelsSessionKey(scopeId, request.sourceId, contextKey)
        val owner = ReelsRoundRobinRenderOwner(
            token = UUID.randomUUID().toString(),
            accountScope = scopeId,
            sourceId = request.sourceId,
            historyGeneration = generation,
            persistEligible = !request.incognito,
        )
        val eligibilityGate = LocalEligibilityGate()
        val selectedRepository = if (request.incognito) {
            memoryRepositoryFactory(eligibilityGate::allows)
        } else {
            repository
        }
        // A corrupt persisted projection is rebuildable: drop only the RR tables of this
        // source (the session/checkpoint and the candidate inbox stay) and reopen. The initial
        // operation then re-fetches the catalog and rebuilds frames from a fresh scan.
        var handle = try {
            writeMutex.withLock {
                selectedRepository.open(key, owner.token, generation, request.unseenOnly)
            }
        } catch (e: ReelsRoundRobinStateCorruptException) {
            writeMutex.withLock { selectedRepository.resetProjection(scopeId, request.sourceId) }
            writeMutex.withLock {
                selectedRepository.open(key, owner.token, generation, request.unseenOnly)
            }
        }
        if (attachEpoch.get() != myEpoch) {
            releaseQuietly(selectedRepository, handle)
            return null
        }
        val snapshot = try {
            writeMutex.withLock { selectedRepository.read(handle) }
        } catch (e: ReelsRoundRobinStateCorruptException) {
            releaseQuietly(selectedRepository, handle)
            writeMutex.withLock { selectedRepository.resetProjection(scopeId, request.sourceId) }
            handle = writeMutex.withLock {
                selectedRepository.open(key, owner.token, generation, request.unseenOnly)
            }
            if (attachEpoch.get() != myEpoch) {
                releaseQuietly(selectedRepository, handle)
                return null
            }
            writeMutex.withLock { selectedRepository.read(handle) }
        }

        val restoredActive = if (request.incognito) {
            null
        } else {
            optional(null) { sessionRepository.active(handle.session) }
        }
        val hidden = optional(emptyList()) { hiddenRepository.getBySource(request.sourceId) }
        val knownIds = buildSet {
            snapshot.entries.forEach { add(it.candidate.itemId) }
            snapshot.frames.forEach { frame ->
                frame.turns.values.forEach { turn -> turn.itemId?.let { add(it) } }
            }
            restoredActive?.item?.itemId?.let { add(it) }
        }
        val consumed = if (request.unseenOnly && knownIds.isNotEmpty()) {
            optional(emptySet()) {
                consumptionRepository.consumedIds(scopeId, request.sourceId, knownIds)
            }
        } else {
            emptySet()
        }
        val session = Session(
            source = request.source,
            sourceId = request.sourceId,
            accountScope = scopeId,
            historyGeneration = generation,
            unseenOnly = request.unseenOnly,
            incognito = request.incognito,
            filters = frozen,
            storageKey = readiness.storageKey,
            key = key,
            owner = owner,
            repository = selectedRepository,
            handle = handle,
            expectedContext = readiness.context,
            eligibility = Eligibility.from(hidden, consumed),
            playbackItemId = null,
            pendingSeekPositionMs = null,
        )
        session.lastSnapshot = snapshot
        val sameWarmContext = snapshot.sources.any { it.checkpointContext == readiness.context.continuityKey }
        if (snapshot.sources.isNotEmpty() && snapshot.catalogRevision != null && sameWarmContext) {
            session.catalogRevisionApplied = snapshot.catalogRevision
            session.catalogWakeAt = nowMs() + CATALOG_WARM_RECONCILE_MS
        }
        eligibilityGate.session = session
        return PreparedSession(session, snapshot, restoredActive)
    }

    private fun seedPlayback(
        session: Session,
        snapshot: ReelsRoundRobinSnapshot,
        restoredActive: ReelsActiveCheckpoint?,
    ) {
        if (restoredActive != null) {
            val item = with(ReelsCandidateMapper) { restoredActive.item.toShortVideoItem() }
            session.liveItems[item.id] = item
            session.playbackItemId = item.id
            session.pendingSeekPositionMs = restoredActive.positionMs
            session.samplePositions[item.id] = restoredActive.positionMs
            if (!session.incognito) {
                checkpointWriter.attachHandle(session.handle.session)
                checkpointWriter.visitStarted(item, session.owner.persistEligible)
                checkpointWriter.sample(item.id, restoredActive.positionMs, foreground = true)
            }
            return
        }
        val anchorId = snapshot.anchor?.itemId
        if (anchorId != null) {
            session.playbackItemId = anchorId
            val candidate = snapshot.entries.firstOrNull { it.candidate.itemId == anchorId }?.candidate
            if (candidate != null) {
                val item = with(ReelsCandidateMapper) { candidate.toShortVideoItem() }
                session.liveItems[anchorId] = item
                if (!session.incognito) {
                    checkpointWriter.attachHandle(session.handle.session)
                    checkpointWriter.visitStarted(item, session.owner.persistEligible)
                }
            }
        }
        if (!session.incognito && checkpointWriter.attachedHandle() == null) {
            checkpointWriter.attachHandle(session.handle.session)
        }
    }

    private fun startObservers(session: Session) {
        eligibilityJob = scope.launch(ioDispatcher) {
            try {
                // Incognito keeps the ephemeral feed but still follows the persistent
                // hidden/consumption stamps read-only, so local decisions apply live.
                val source = if (session.incognito) repository else session.repository
                source.observeEligibility(session.accountScope, session.sourceId).collect {
                    commands.trySend(Command.EligibilityChanged(session))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Eligibility is advisory: a broken observer must not kill the feed.
            }
        }
        replayContextJob = scope.launch(ioDispatcher) {
            try {
                session.source.replayContextChanges.collect { changed ->
                    commands.trySend(Command.ReplayContextChanged(session, changed))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // A broken context stream is handled by the next attach/refresh.
            }
        }
    }

    private fun cancelActiveWork() {
        operationEpoch.incrementAndGet()
        activeOperation?.job?.cancel()
        activeOperation = null
        queuedOperations.clear()
        wakeJob?.cancel()
        wakeJob = null
        eligibilityJob?.cancel()
        eligibilityJob = null
        replayContextJob?.cancel()
        replayContextJob = null
        activeSession?.scheduledWakeAt = null
    }

    private suspend fun tearDownActiveSession() {
        cancelActiveWork()
        val session = activeSession
        if (session != null) {
            // Flush the coalesced checkpoint while the handle/key fence is still valid, then
            // detach. Without the explicit flush the last sampled position would be lost.
            if (!session.incognito) checkpointWriter.flushActiveVisitNow()
            synchronized(session.publishLock) {
                session.stopped = true
            }
            checkpointWriter.detach()
            releaseQuietly(session.repository, session.handle)
        }
        activeSession = null
    }

    private suspend fun stopInternal() {
        attachEpoch.incrementAndGet()
        tearDownActiveSession()
    }

    private suspend fun handleReplayContextChanged(session: Session) {
        if (session !== activeSession || session.stopped) return
        val readiness = withContext(ioDispatcher) {
            try {
                session.source.prepareRoundRobin()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
        }
        if (session !== activeSession || session.stopped) return
        when (readiness) {
            is FeedRoundRobinReadiness.Ready -> {
                val sameAccount = readiness.context.accountId == session.expectedContext.accountId
                val sameStorage = readiness.storageKey == session.storageKey
                if (sameAccount && sameStorage) {
                    session.expectedContext = readiness.context
                    startOperation(
                        OperationRequest(forceCatalog = true, publishLoading = false),
                        priority = true,
                    )
                } else {
                    // A readiness context that proves a different account belongs to a new
                    // stream: the pinned scope must not follow it, so prepareSession derives
                    // the scope from the source's current proven identity. A blank id or a
                    // transient identity loss proves nothing and keeps the pin. A fallback
                    // `src:` pin is upgraded once the readiness context proves an account.
                    val accountChanged =
                        readiness.context.accountId != session.expectedContext.accountId
                    val fallbackPinned =
                        session.accountScope == ReelsForYou.fallbackScope(session.sourceId)
                    val provenDifferentAccount = readiness.context.accountId.isNotBlank() &&
                        (accountChanged || fallbackPinned)
                    attachInternal(
                        AttachRequest(
                            source = session.source,
                            sourceId = session.sourceId,
                            filters = session.filters.filters,
                            unseenOnly = session.unseenOnly,
                            incognito = session.incognito,
                            // A proven different account (or a fallback pin that this context
                            // upgrades) re-derives the scope from the source; a blank or
                            // unchanged identity keeps the session's pin. The published owner
                            // carries the resolved scope, and the host adopts it for its later
                            // restore/reset/attach.
                            accountScope = if (provenDifferentAccount) null else session.accountScope,
                        ),
                        preserveOnFailure = true,
                    )
                }
            }
            else -> {
                operationEpoch.incrementAndGet()
                activeOperation?.job?.cancel()
                activeOperation = null
                queuedOperations.clear()
                wakeJob?.cancel()
                wakeJob = null
                session.scheduledWakeAt = null
                publishControl(
                    session = session,
                    snapshot = session.lastSnapshot,
                    loading = false,
                    stop = ReelsRoundRobinStop.PAUSED,
                    error = ReelsRoundRobinNotReadyException(
                        readiness ?: FeedRoundRobinReadiness.SignedOut,
                    ),
                )
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Operation
    // ---------------------------------------------------------------------------------------

    private suspend fun runOperation(
        session: Session,
        request: OperationRequest,
        epoch: Long,
    ) {
        val initialSnapshot = readSnapshot(session)
        if (initialSnapshot == null) {
            if (isCurrent(session, epoch)) {
                session.lastStop = ReelsRoundRobinStop.PAUSED
                publishControl(
                    session = session,
                    snapshot = session.lastSnapshot,
                    loading = false,
                    stop = ReelsRoundRobinStop.PAUSED,
                    error = IllegalStateException("Round-robin state is unavailable"),
                )
            }
            return
        }
        if (!isCurrent(session, epoch)) return
        var snapshot: ReelsRoundRobinSnapshot = initialSnapshot
        var stop = session.lastStop
        var error: Throwable? = null
        var recoveryUsed = false
        var explicitWakeAt: Long? = null
        var turns = 0
        var needs: List<ReelsRoundRobinNeed> = emptyList()
        var updates: List<ReelsRoundRobinUpdate> = emptyList()
        var shouldPump = true
        var catalogFenced = false
        // One repair per dead slot per operation: a payload that cannot be re-created would
        // otherwise request the same repair on every loop iteration until the turn budget ends.
        val repairedIds = mutableSetOf<String>()

        try {
            if (request.refreshEligibility) {
                snapshot = refreshEligibility(session, snapshot)
            }
            if (request.refreshDueRetries) {
                snapshot = refreshDueRetries(session, snapshot)
            }
            if (request.publishLoading && !request.initial) {
                publishControl(
                    session = session,
                    snapshot = snapshot,
                    loading = true,
                    stop = ReelsRoundRobinStop.PAUSED,
                    error = null,
                )
            }
            if (request.activateItemId != null) {
                session.emptyAutoUsed = false
                prepareActivation(session, request.activateItemId)
                snapshot = mutate(
                    session,
                    snapshot,
                    ReelsRoundRobinMutation.Activate(request.activateItemId, nowMs()),
                ).snapshot
                shouldPump = true
            }
            when (val gate = ensureCatalog(session, snapshot, request)) {
                is CatalogGate.Applied -> snapshot = gate.snapshot
                CatalogGate.Deferred -> stop = ReelsRoundRobinStop.PAUSED
                CatalogGate.Fenced -> {
                    stop = ReelsRoundRobinStop.PAUSED
                    error = IllegalStateException("Round-robin catalog context changed")
                    catalogFenced = true
                }
                CatalogGate.None -> Unit
            }
            if (!catalogFenced) {
                snapshot = importUnmapped(session, snapshot)
                val deadline = nowMs() + SLICE_BUDGET_MS
                while (true) {
                    currentCoroutineContext().ensureActive()
                    if (!isCurrent(session, epoch)) return
                    if (shouldPump) {
                        val commit = mutate(
                            session,
                            snapshot,
                            ReelsRoundRobinMutation.Pump(updates, PAGE_LIMIT, nowMs()),
                        )
                        snapshot = commit.snapshot
                        updates = emptyList()
                        shouldPump = false
                        stop = commit.stop
                        if (session.catalogRevisionApplied == null &&
                            stop == ReelsRoundRobinStop.EMPTY
                        ) {
                            stop = ReelsRoundRobinStop.PAUSED
                        }
                        val items = if (commit.page.isEmpty()) {
                            emptyList()
                        } else {
                            buildSuffix(session, snapshot)
                        }
                        if (items.isNotEmpty()) {
                            session.emptyAutoUsed = false
                            publishItems(
                                session = session,
                                snapshot = snapshot,
                                items = items,
                                loading = false,
                                stop = stop,
                                error = error,
                            )
                            needs = commit.needs
                            if (needs.isEmpty()) break
                        } else {
                            val anchorEmpty = stop == ReelsRoundRobinStop.EMPTY && snapshot.anchor != null
                            val recoverable = stop in RECOVERABLE_STOPS || anchorEmpty
                            if (recoverable && commit.needs.isEmpty()) {
                                val plan = recoveryPlan(
                                    session = session,
                                    snapshot = snapshot,
                                    now = nowMs(),
                                    respectCooldown = request.activateItemId != null,
                                )
                                if (plan != null) {
                                    if (plan.wakeAt != null) {
                                        explicitWakeAt = listOfNotNull(
                                            explicitWakeAt,
                                            plan.wakeAt,
                                        ).minOrNull()
                                    }
                                    if (plan.sources.isNotEmpty() && !recoveryUsed) {
                                        recoveryUsed = true
                                        snapshot = recover(session, snapshot, plan.sources)
                                        stop = ReelsRoundRobinStop.PAUSED
                                        shouldPump = true
                                        continue
                                    }
                                    stop = ReelsRoundRobinStop.PAUSED
                                    break
                                }
                            }
                            if (stop in RECOVERABLE_STOPS && commit.needs.isEmpty()) {
                                stop = ReelsRoundRobinStop.PAUSED
                                break
                            }
                            needs = commit.needs
                            if (needs.isEmpty()) break
                        }
                    }
                    if (nowMs() >= deadline || turns >= MAX_TURNS_PER_SLICE) break
                    val dispatched = dispatch(
                        session = session,
                        needs = needs,
                        deadline = deadline,
                        maxTurns = (MAX_TURNS_PER_SLICE - turns).coerceAtLeast(0),
                        alreadyRepaired = repairedIds,
                    )
                    if (dispatched.repairs.isNotEmpty()) {
                        repairedIds += dispatched.repairs.keys
                        snapshot = mutate(
                            session,
                            snapshot,
                            ReelsRoundRobinMutation.Repair(dispatched.repairs.keys, nowMs()),
                        ).snapshot
                        updates = emptyList()
                        needs = dispatched.repairs.values.distinct().mapNotNull { sourceKey ->
                            snapshot.sources.firstOrNull { it.key == sourceKey }?.let { source ->
                                ReelsRoundRobinNeed.Scan(
                                    sourceKey = source.key,
                                    checkpoint = source.checkpoint,
                                    refreshHead = source.checkpoint == null,
                                    maxCandidates = PAGE_LIMIT,
                                )
                            }
                        }
                        // Dispatch the rescan before the pump: a pump first would decide the
                        // released slot from the old (history-exhausted) source state and could
                        // record a skip that keeps the re-created payload unreserved.
                        shouldPump = false
                        continue
                    }
                    if (dispatched.outcomes.isEmpty()) {
                        if (dispatched.deferredAt != null) {
                            stop = ReelsRoundRobinStop.WAIT_RETRY
                            explicitWakeAt = listOfNotNull(
                                explicitWakeAt,
                                dispatched.deferredAt,
                            ).minOrNull()
                        }
                        break
                    }
                    turns += dispatched.outcomes.size
                    if (dispatched.outcomes.any { it.fenced }) {
                        stop = ReelsRoundRobinStop.PAUSED
                        error = IllegalStateException("Round-robin source context changed")
                        break
                    }
                    updates = dispatched.outcomes.flatMap { it.updates }
                    shouldPump = true
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ReelsInboxCapacityException) {
            stop = ReelsRoundRobinStop.PAUSED
            error = e
        } catch (e: ReelsSessionStaleException) {
            // A superseded response: never commit it, never surface it as an end of feed.
            stop = ReelsRoundRobinStop.PAUSED
        } catch (t: Throwable) {
            stop = ReelsRoundRobinStop.PAUSED
            error = t
        } finally {
            debug("finish:id=${session.owner.token.take(4)}:current=${isCurrent(session, epoch)}:stop=$stop")
            if (isCurrent(session, epoch)) {
                val finalSnapshot = session.lastSnapshot ?: snapshot
                session.lastStop = stop
                session.lastError = error
                publishControl(
                    session = session,
                    snapshot = finalSnapshot,
                    loading = false,
                    stop = stop,
                    error = error,
                )
                scheduleWake(session, finalSnapshot, stop, explicitWakeAt)
            }
        }
    }

    private suspend fun ensureCatalog(
        session: Session,
        snapshot: ReelsRoundRobinSnapshot,
        request: OperationRequest,
    ): CatalogGate {
        val hasCatalog = session.catalogRevisionApplied != null
        if (hasCatalog && !request.forceCatalog) return CatalogGate.None
        val now = nowMs()
        val providerRetry = session.providerRetryAt
        if (providerRetry != null && now < providerRetry) return CatalogGate.None
        val catalogRetry = session.catalogRetryAt
        if (catalogRetry != null && now < catalogRetry &&
            (session.catalogRetryExplicit || !request.forceCatalog)
        ) {
            return CatalogGate.None
        }
        val result = withContext(ioDispatcher) {
            session.source.getRoundRobinCatalog(session.expectedContext, request.forceCatalog)
        }
        return when (result) {
            is FeedRoundRobinCatalogResult.Snapshot -> {
                val catalog = result.catalog
                if (catalog.context.accountId != session.expectedContext.accountId) {
                    CatalogGate.Fenced
                } else {
                    val mapped = ReelsRoundRobinCatalog(
                        revision = catalog.revision,
                        complete = catalog.complete,
                        sources = catalog.sources.map { source ->
                            ReelsRoundRobinCatalogSource(
                                key = source.key,
                                title = source.title,
                                author = source.author,
                                contentRevision = source.contentRevision,
                                excluded = source.excluded,
                                previousKeys = source.previousKeys,
                            )
                        },
                    )
                    val commit = mutate(
                        session,
                        snapshot,
                        ReelsRoundRobinMutation.Catalog(mapped, now),
                    )
                    session.catalogRevisionApplied = mapped.revision
                    session.catalogRetryAt = catalog.retryAtEpochMillis
                    session.catalogRetryExplicit = catalog.retryAtEpochMillis != null
                    session.catalogRetryForced = false
                    // Chat membership changes only arrive through this snapshot. Reconcile at
                    // most once per interval while the feed stays open; a manual refresh runs
                    // an operation immediately and re-arms the same interval.
                    session.catalogWakeAt = now + CATALOG_REFRESH_MS
                    CatalogGate.Applied(commit.snapshot)
                }
            }
            is FeedRoundRobinCatalogResult.Deferred -> {
                val retry = result.retryAtEpochMillis
                session.catalogRetryAt = retry ?: (now + CATALOG_RETRY_MS)
                session.catalogRetryExplicit = retry != null
                session.catalogRetryForced = true
                CatalogGate.Deferred
            }
            FeedRoundRobinCatalogResult.ContextChanged -> {
                session.catalogRetryForced = false
                CatalogGate.Fenced
            }
        }
    }

    private suspend fun importUnmapped(
        session: Session,
        snapshot: ReelsRoundRobinSnapshot,
    ): ReelsRoundRobinSnapshot {
        if (session.catalogRevisionApplied == null) return snapshot
        val entryIds = snapshot.entries.mapTo(HashSet()) { it.candidate.itemId }
        val candidates = LinkedHashSet<String>()
        snapshot.frames.forEach { frame ->
            frame.turns.values.mapNotNull { it.itemId }.forEach { itemId ->
                if (itemId !in entryIds) candidates += itemId
            }
        }
        snapshot.anchor?.itemId?.let { if (it !in entryIds) candidates += it }
        session.playbackItemId?.let { if (it !in entryIds) candidates += it }

        val backlog = if (!session.legacyImported && !session.incognito) {
            optional(emptyList()) {
                candidateRepository.unconsumed(
                    session.accountScope,
                    session.sourceId,
                    BACKLOG_SEED_LIMIT,
                )
            }
        } else {
            emptyList()
        }
        session.legacyImported = true
        val backlogById = backlog.associateBy { it.itemId }
        if (backlogById.isNotEmpty()) {
            if (session.unseenOnly) {
                val consumed = optional(emptySet()) {
                    consumptionRepository.consumedIds(
                        session.accountScope,
                        session.sourceId,
                        backlogById.keys,
                    )
                }
                session.mergeConsumed(consumed)
            }
            backlogById.values.forEach { candidate ->
                if (session.eligibility.allows(candidate.author, candidate.itemId, session.unseenOnly)) {
                    candidates += candidate.itemId
                }
            }
        }
        candidates.removeIf { itemId ->
            !session.eligibility.allowsId(itemId, session.unseenOnly)
        }
        if (candidates.isEmpty()) return snapshot

        val identities = LinkedHashMap<String, ReelsRoundRobinIdentity>()
        candidates.forEach { itemId ->
            val identity = runCatching { session.source.identifyRoundRobinItem(itemId) }.getOrNull()
            if (identity != null) {
                identities[itemId] = ReelsRoundRobinIdentity(identity.sourceKey, identity.orderKey)
            }
        }
        if (identities.isEmpty()) return snapshot
        return mutate(
            session,
            snapshot,
            ReelsRoundRobinMutation.Import(identities, nowMs()),
        ).snapshot
    }

    private suspend fun refreshEligibility(
        session: Session,
        snapshot: ReelsRoundRobinSnapshot,
    ): ReelsRoundRobinSnapshot {
        // A failed read is not "nothing is hidden/consumed": keep the previous snapshot and
        // skip the diff so a transient IO error cannot unhide the whole feed.
        val hidden = optionalRead { hiddenRepository.getBySource(session.sourceId) }
            ?: return snapshot
        val knownIds = buildSet {
            snapshot.entries.forEach { add(it.candidate.itemId) }
            snapshot.frames.forEach { frame ->
                frame.turns.values.forEach { turn -> turn.itemId?.let { add(it) } }
            }
            snapshot.anchor?.itemId?.let { add(it) }
            session.playbackItemId?.let { add(it) }
        }
        val consumed = if (session.unseenOnly && knownIds.isNotEmpty()) {
            optionalRead {
                consumptionRepository.consumedIds(
                    session.accountScope,
                    session.sourceId,
                    knownIds,
                )
            } ?: return snapshot
        } else {
            emptySet()
        }
        val fresh = Eligibility.from(hidden, consumed)
        val old = session.eligibility
        synchronized(session.eligibilityLock) {
            session.eligibility = fresh
        }

        val entriesById = snapshot.entries.associateBy { it.candidate.itemId }
        val sourceByItem = HashMap<String, String>()
        snapshot.frames.forEach { frame ->
            frame.members.forEach { member ->
                frame.turns[member]?.itemId?.let { sourceByItem[it] = member }
            }
        }
        fun sourceFor(itemId: String): String? =
            entriesById[itemId]?.sourceKey ?: sourceByItem[itemId] ?: snapshot.anchor
                ?.takeIf { it.itemId == itemId }?.sourceKey

        val affected = LinkedHashSet<String>()
        val unhidden = LinkedHashSet<String>()

        (fresh.hiddenVideos - old.hiddenVideos).forEach { itemId ->
            sourceFor(itemId)?.let(affected::add)
        }
        (old.hiddenVideos - fresh.hiddenVideos).forEach { itemId ->
            sourceFor(itemId)?.let {
                affected += it
                unhidden += it
            }
        }
        (fresh.consumed - old.consumed).forEach { itemId ->
            if (itemId != session.playbackItemId) sourceFor(itemId)?.let(affected::add)
        }
        (old.consumed - fresh.consumed).forEach { itemId ->
            sourceFor(itemId)?.let(affected::add)
        }

        fun sourcesForAuthor(author: String): Set<String> {
            val result = LinkedHashSet<String>()
            snapshot.entries.forEach { entry ->
                if (entry.candidate.author == author) result += entry.sourceKey
            }
            snapshot.sources.forEach { source ->
                if (author in source.knownAuthors) result += source.key
            }
            return result
        }
        (fresh.hiddenAuthors - old.hiddenAuthors).forEach { author ->
            affected += sourcesForAuthor(author)
        }
        (old.hiddenAuthors - fresh.hiddenAuthors).forEach { author ->
            val sources = sourcesForAuthor(author)
            affected += sources
            unhidden += sources
        }

        snapshot.sources.forEach { source ->
            if (source.retryAt != null &&
                source.retryReason != FeedRoundRobinDeferral.RATE_LIMIT.name
            ) {
                affected += source.key
            }
        }

        // Eligibility diffs must never lift a rate limit. Such a source keeps its backoff and
        // is re-decided after the retry time, not scanned now.
        val rateLimited = snapshot.sources
            .filter { it.retryReason == FeedRoundRobinDeferral.RATE_LIMIT.name }
            .mapTo(HashSet()) { it.key }
        affected.removeAll(rateLimited)
        unhidden.removeAll(rateLimited)

        val now = nowMs()
        val headCheckPending = snapshot.sources
            .filter { it.nextHeadCheckAt?.let { due -> due > now } == true }
            .mapTo(HashSet()) { it.key }
        unhidden.removeAll(headCheckPending)

        if (affected.isEmpty()) return snapshot
        var result = mutate(
            session,
            snapshot,
            ReelsRoundRobinMutation.Recheck(affected, nowMs()),
        ).snapshot
        if (unhidden.isNotEmpty()) {
            val reset = unhidden.map { ReelsRoundRobinUpdate.ResetCursor(it) }
            result = mutate(
                session,
                result,
                ReelsRoundRobinMutation.Pump(reset, PAGE_LIMIT, nowMs()),
            ).snapshot
        }
        return result
    }

    /**
     * Plans the empty-page recovery. Only sources whose future slots are explicit skips may be
     * rewound, and never while a head check or a retry (especially a rate limit) is still due;
     * those source wake up at their due time instead of being scanned early.
     */
    private fun recoveryPlan(
        session: Session,
        snapshot: ReelsRoundRobinSnapshot,
        now: Long,
        respectCooldown: Boolean,
    ): RecoveryPlan? {
        val candidates = snapshot.sources.filter { it.present && !it.excluded }
        if (candidates.isEmpty()) return null
        val resettable = LinkedHashSet<String>()
        var wakeAt: Long? = null
        candidates.forEach { source ->
            val headCheckAt = source.nextHeadCheckAt?.takeIf { it > now }
            val retry = source.retryAt
            val retryAt = retry?.takeIf { it > now }
            val rateLimitActive = source.retryReason == FeedRoundRobinDeferral.RATE_LIMIT.name &&
                (retry == null || retry > now)
            if (headCheckAt != null || retryAt != null ||
                rateLimitActive
            ) {
                wakeAt = listOfNotNull(wakeAt, headCheckAt, retryAt).minOrNull()
                return@forEach
            }
            if (!allFutureTurnsAreSkips(snapshot, source.key)) return@forEach
            resettable += source.key
        }
        val cooldownOver = !respectCooldown || session.lastRecoveryAt?.let {
            now - it >= EMPTY_AUTO_INTERVAL_MS
        } ?: true
        return RecoveryPlan(
            sources = if (cooldownOver) resettable else emptySet(),
            wakeAt = wakeAt,
        )
    }

    private fun allFutureTurnsAreSkips(
        snapshot: ReelsRoundRobinSnapshot,
        sourceKey: String,
    ): Boolean {
        snapshot.frames.forEach { frame ->
            val limit = playedLimit(frame, snapshot.anchor)
            frame.members.forEachIndexed { index, member ->
                if (member != sourceKey || index <= limit) return@forEachIndexed
                val turn = frame.turns[member] ?: return false
                if (turn.skip == null) return false
            }
        }
        return true
    }

    private fun playedLimit(frame: ReelsRoundRobinFrame, anchor: ReelsRoundRobinAnchor?): Int {
        if (anchor == null) return -1
        if (frame.number < anchor.round) return frame.members.lastIndex
        if (frame.number > anchor.round) return -1
        val byKey = frame.members.indexOf(anchor.sourceKey)
        if (byKey >= 0) return byKey
        return frame.members.indexOfFirst { frame.turns[it]?.itemId == anchor.itemId }
    }

    /**
     * A scheduled wake resumed a head check or a retry that is now due. Clearing the resolved
     * skip lets the scheduler issue the pending scan from the preserved checkpoint; the active
     * rate limit itself is only ever lifted at its own due time.
     */
    private suspend fun refreshDueRetries(
        session: Session,
        snapshot: ReelsRoundRobinSnapshot,
    ): ReelsRoundRobinSnapshot {
        val now = nowMs()
        val due = snapshot.sources
            .filter { source ->
                source.retryAt?.let { it <= now } == true ||
                    source.nextHeadCheckAt?.let { it <= now } == true
            }
            .mapTo(LinkedHashSet()) { it.key }
        if (due.isEmpty()) return snapshot
        return mutate(
            session,
            snapshot,
            ReelsRoundRobinMutation.Recheck(due, nowMs()),
        ).snapshot
    }

    private suspend fun recover(
        session: Session,
        snapshot: ReelsRoundRobinSnapshot,
        sources: Set<String>,
    ): ReelsRoundRobinSnapshot {
        if (sources.isEmpty()) return snapshot
        var result = mutate(
            session,
            snapshot,
            ReelsRoundRobinMutation.Recheck(sources, nowMs()),
        ).snapshot
        val updates = sources.map { ReelsRoundRobinUpdate.ResetCursor(it) }
        result = mutate(
            session,
            result,
            ReelsRoundRobinMutation.Pump(updates, PAGE_LIMIT, nowMs()),
        ).snapshot
        session.lastRecoveryAt = nowMs()
        return result
    }

    // ---------------------------------------------------------------------------------------
    // Scan / revalidate tasks
    // ---------------------------------------------------------------------------------------

    private suspend fun dispatch(
        session: Session,
        needs: List<ReelsRoundRobinNeed>,
        deadline: Long,
        maxTurns: Int,
        alreadyRepaired: Set<String>,
    ): DispatchResult {
        val executable = mutableListOf<ReelsRoundRobinNeed>()
        val seen = HashSet<String>()
        val repairs = LinkedHashMap<String, String>()
        var deferredAt: Long? = null
        val now = nowMs()
        val snapshot = session.lastSnapshot
        fun deferUntil(at: Long?) {
            if (at != null) deferredAt = listOfNotNull(deferredAt, at).minOrNull()
        }
        for (need in needs) {
            if (executable.size >= MAX_PARALLEL_TASKS || executable.size >= maxTurns) break
            val key = when (need) {
                is ReelsRoundRobinNeed.Scan -> {
                    val source = snapshot?.sources?.firstOrNull { it.key == need.sourceKey }
                    val retry = source?.retryAt
                    if (retry != null && retry > now) {
                        deferUntil(retry)
                        continue
                    }
                    if (session.providerRetryAt?.let { now < it } == true) {
                        deferUntil(session.providerRetryAt)
                        continue
                    }
                    "scan:${need.sourceKey}"
                }
                is ReelsRoundRobinNeed.Revalidate -> {
                    val entry = snapshot?.entries?.firstOrNull { it.candidate.itemId == need.itemId }
                    if (entry == null) {
                        // The payload row is gone, so no resolve can ever satisfy this need.
                        // Derive the owning slot from the frame and release it instead of
                        // dropping the need (which would leave the frame blocked forever).
                        if (need.itemId !in alreadyRepaired) {
                            val member = snapshot?.frames?.firstNotNullOfOrNull { frame ->
                                frame.members.firstOrNull { member ->
                                    frame.turns[member]?.itemId == need.itemId
                                }
                            }
                            if (member != null) repairs[need.itemId] = member
                        }
                        continue
                    }
                    val source = snapshot.sources.firstOrNull { it.key == entry.sourceKey }
                    val retry = source?.retryAt
                    if (retry != null && retry > now) {
                        // A deferred revalidation is due at the source retry time; retrying
                        // earlier spins on the same payload and ignores the backoff.
                        deferUntil(retry)
                        continue
                    }
                    if (session.providerRetryAt?.let { now < it } == true) {
                        deferUntil(session.providerRetryAt)
                        continue
                    }
                    "revalidate:${need.itemId}"
                }
            }
            if (seen.add(key)) executable += need
        }
        if (executable.isEmpty()) return DispatchResult(emptyList(), deferredAt, repairs)

        val channel = Channel<Outcome>(Channel.UNLIMITED)
        val outcomes = supervisorScope {
            val jobs = executable.map { need ->
                launch(ioDispatcher) {
                    val outcome = try {
                        when (need) {
                            is ReelsRoundRobinNeed.Scan -> executeScan(session, need)
                            is ReelsRoundRobinNeed.Revalidate -> executeRevalidate(session, need)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        failureOutcome(session, need)
                    }
                    channel.send(outcome)
                }
            }
            val received = mutableListOf<Outcome>()
            try {
                var remaining = executable.size
                while (remaining > 0) {
                    val budget = deadline - nowMs()
                    if (budget <= 0L) break
                    val outcome = withTimeoutOrNull(budget) { channel.receive() } ?: break
                    received += outcome
                    remaining--
                    if (outcome.fenced) break
                }
            } finally {
                jobs.forEach { it.cancel() }
                channel.close()
            }
            received
        }
        return DispatchResult(outcomes, deferredAt)
    }

    private suspend fun executeScan(
        session: Session,
        need: ReelsRoundRobinNeed.Scan,
    ): Outcome {
        val request = FeedRoundRobinScanRequest(
            sourceKey = need.sourceKey,
            expectedContext = session.expectedContext,
            filters = session.filters.filters,
            checkpoint = need.checkpoint,
            refreshHead = need.refreshHead,
            maxCandidates = need.maxCandidates.coerceIn(1, 2),
        )
        val result = session.source.scanRoundRobinSource(request)
        return when (result) {
            is FeedRoundRobinScanResult.Page -> {
                if (!isValidPage(result, request)) {
                    return Outcome(
                        listOf(
                            ReelsRoundRobinUpdate.Deferred(
                                sourceKey = need.sourceKey,
                                reason = FeedRoundRobinDeferral.NETWORK.name,
                                retryAt = defaultRetryAt(session, need.sourceKey),
                            ),
                        ),
                    )
                }
                val consumed = if (session.unseenOnly && result.candidates.isNotEmpty()) {
                    optionalRead {
                        consumptionRepository.consumedIds(
                            session.accountScope,
                            session.sourceId,
                            result.candidates.map { it.item.id },
                        )
                    } ?: return Outcome(
                        listOf(
                            ReelsRoundRobinUpdate.Deferred(
                                sourceKey = need.sourceKey,
                                reason = FeedRoundRobinDeferral.NETWORK.name,
                                retryAt = defaultRetryAt(session, need.sourceKey),
                            ),
                        ),
                    )
                } else {
                    emptySet()
                }
                session.mergeConsumed(consumed)
                val now = Date(nowMs())
                val entries = result.candidates.mapNotNull { candidate ->
                    val identity = candidate.identity
                    if (identity.sourceKey != request.sourceKey ||
                        candidate.item.id.isBlank() ||
                        identity.orderKey <= 0L
                    ) {
                        return@mapNotNull null
                    }
                    if (!session.eligibility.allows(
                            candidate.item.author,
                            candidate.item.id,
                            session.unseenOnly,
                        )
                    ) {
                        return@mapNotNull null
                    }
                    session.liveItems[candidate.item.id] = candidate.item
                    val stored = with(ReelsCandidateMapper) {
                        candidate.item.toCandidate(session.accountScope, session.sourceId, now)
                    }.copy(
                        videoUrl = "",
                        videoUrlHd = null,
                        posterUrl = "",
                        posterUrlVertical = null,
                        capturedContextKey = session.key.contextKey,
                    )
                    ReelsRoundRobinEntry(
                        candidate = stored,
                        sourceKey = request.sourceKey,
                        orderKey = identity.orderKey,
                        contentKey = candidate.contentKey,
                        checkedContext = null,
                        validation = ReelsRoundRobinValidation.ELIGIBLE,
                        round = null,
                        ordinal = null,
                    )
                }
                session.retryCounts.remove(need.sourceKey)
                Outcome(
                    listOf(
                        ReelsRoundRobinUpdate.Page(
                            sourceKey = request.sourceKey,
                            fromCheckpoint = request.checkpoint,
                            nextCheckpoint = result.checkpoint,
                            entries = entries,
                            historyExhausted = result.historyExhausted,
                            nextHeadCheckAt = result.nextHeadCheckAtEpochMillis,
                            // Every returned card was locally ineligible, so staging nothing
                            // is a budgeted filter result: the source records a fruitless scan
                            // and waits for its head check instead of being rescanned in-round.
                            fruitless = result.candidates.isNotEmpty() && entries.isEmpty(),
                        ),
                    ),
                )
            }
            is FeedRoundRobinScanResult.Deferred -> {
                val retryAt = result.retryAtEpochMillis ?: defaultRetryAt(session, need.sourceKey)
                if (result.providerWide) {
                    session.providerRetryAt = retryAt
                }
                Outcome(
                    listOf(
                        ReelsRoundRobinUpdate.Deferred(
                            sourceKey = need.sourceKey,
                            reason = result.reason.name,
                            retryAt = retryAt,
                        ),
                    ),
                )
            }
            FeedRoundRobinScanResult.Unavailable -> Outcome(
                listOf(ReelsRoundRobinUpdate.Unavailable(need.sourceKey)),
            )
            FeedRoundRobinScanResult.ContextChanged -> Outcome(emptyList(), fenced = true)
            FeedRoundRobinScanResult.InvalidCheckpoint -> Outcome(
                listOf(ReelsRoundRobinUpdate.ResetCursor(need.sourceKey)),
            )
        }
    }

    private suspend fun executeRevalidate(
        session: Session,
        need: ReelsRoundRobinNeed.Revalidate,
    ): Outcome {
        val entry = session.lastSnapshot?.entries
            ?.firstOrNull { it.candidate.itemId == need.itemId }
            ?: return Outcome(emptyList())
        val result = session.source.resolveReplayItem(
            need.itemId,
            session.expectedContext,
            session.filters.filters,
        )
        return when (result) {
            is FeedReplayItemResult.Available -> {
                val item = result.item
                if (item.id != need.itemId) {
                    return Outcome(
                        listOf(
                            ReelsRoundRobinUpdate.Revalidated(
                                itemId = need.itemId,
                                candidate = null,
                                validation = ReelsRoundRobinValidation.DEFERRED,
                                retryAt = defaultRetryAt(session, entry.sourceKey),
                            ),
                        ),
                    )
                }
                val consumed = if (session.unseenOnly) {
                    optionalRead {
                        consumptionRepository.consumedIds(
                            session.accountScope,
                            session.sourceId,
                            listOf(need.itemId),
                        )
                    } ?: return Outcome(
                        listOf(
                            ReelsRoundRobinUpdate.Revalidated(
                                itemId = need.itemId,
                                candidate = null,
                                validation = ReelsRoundRobinValidation.DEFERRED,
                                retryAt = defaultRetryAt(session, entry.sourceKey),
                            ),
                        ),
                    )
                } else {
                    emptySet()
                }
                session.mergeConsumed(consumed)
                val allowed = session.eligibility.allows(
                    item.author,
                    item.id,
                    session.unseenOnly,
                )
                if (allowed) {
                    session.liveItems[item.id] = item
                }
                val stored = with(ReelsCandidateMapper) {
                    item.toCandidate(session.accountScope, session.sourceId, Date(nowMs()))
                }.copy(
                    videoUrl = "",
                    videoUrlHd = null,
                    posterUrl = "",
                    posterUrlVertical = null,
                    capturedContextKey = session.key.contextKey,
                )
                if (allowed) session.retryCounts.remove(entry.sourceKey)
                Outcome(
                    listOf(
                        ReelsRoundRobinUpdate.Revalidated(
                            itemId = need.itemId,
                            candidate = stored,
                            validation = if (allowed) {
                                ReelsRoundRobinValidation.ELIGIBLE
                            } else {
                                ReelsRoundRobinValidation.FILTERED
                            },
                            retryAt = null,
                        ),
                    ),
                )
            }
            FeedReplayItemResult.FilteredOut -> Outcome(
                listOf(
                    ReelsRoundRobinUpdate.Revalidated(
                        itemId = need.itemId,
                        candidate = null,
                        validation = ReelsRoundRobinValidation.FILTERED,
                        retryAt = null,
                    ),
                ),
            )
            FeedReplayItemResult.Unavailable -> Outcome(
                listOf(
                    ReelsRoundRobinUpdate.Revalidated(
                        itemId = need.itemId,
                        candidate = null,
                        validation = ReelsRoundRobinValidation.UNAVAILABLE,
                        retryAt = null,
                    ),
                ),
            )
            is FeedReplayItemResult.Deferred -> Outcome(
                listOf(
                    ReelsRoundRobinUpdate.Revalidated(
                        itemId = need.itemId,
                        candidate = null,
                        validation = ReelsRoundRobinValidation.DEFERRED,
                        retryAt = result.retryAtEpochMillis ?: defaultRetryAt(session, entry.sourceKey),
                    ),
                ),
            )
            FeedReplayItemResult.ContextChanged -> Outcome(emptyList(), fenced = true)
        }
    }

    private fun failureOutcome(
        session: Session,
        need: ReelsRoundRobinNeed,
    ): Outcome {
        return when (need) {
            is ReelsRoundRobinNeed.Scan -> Outcome(
                listOf(
                    ReelsRoundRobinUpdate.Deferred(
                        sourceKey = need.sourceKey,
                        reason = FeedRoundRobinDeferral.NETWORK.name,
                        retryAt = defaultRetryAt(session, need.sourceKey),
                    ),
                ),
            )
            is ReelsRoundRobinNeed.Revalidate -> {
                val sourceKey = session.lastSnapshot?.entries
                    ?.firstOrNull { it.candidate.itemId == need.itemId }
                    ?.sourceKey
                val retryAt = sourceKey?.let { defaultRetryAt(session, it) }
                Outcome(
                    listOf(
                        ReelsRoundRobinUpdate.Revalidated(
                            itemId = need.itemId,
                            candidate = null,
                            validation = ReelsRoundRobinValidation.DEFERRED,
                            retryAt = retryAt,
                        ),
                    ),
                )
            }
        }
    }

    private fun isValidPage(
        page: FeedRoundRobinScanResult.Page,
        request: FeedRoundRobinScanRequest,
    ): Boolean {
        if (page.checkpoint.isBlank()) return false
        if (page.candidates.size > request.maxCandidates) return false
        return page.candidates.all { candidate ->
            candidate.identity.sourceKey == request.sourceKey &&
                candidate.item.id.isNotBlank() &&
                candidate.identity.orderKey > 0L
        }
    }

    private fun defaultRetryAt(session: Session, sourceKey: String): Long {
        val count = synchronized(session.retryLock) {
            val next = (session.retryCounts[sourceKey] ?: 0) + 1
            session.retryCounts[sourceKey] = next
            next
        }
        val delay = if (count <= 1) {
            DEFAULT_RETRY_MS
        } else {
            val factor = 1L shl (count - 2).coerceAtMost(5)
            (REPEATED_RETRY_MS * factor).coerceAtMost(MAX_RETRY_MS)
        }
        return nowMs() + delay
    }

    // ---------------------------------------------------------------------------------------
    // Publishing
    // ---------------------------------------------------------------------------------------

    private suspend fun publishItems(
        session: Session,
        snapshot: ReelsRoundRobinSnapshot,
        items: List<ShortVideoItem>,
        loading: Boolean,
        stop: ReelsRoundRobinStop,
        error: Throwable?,
    ) {
        publishPage(
            session = session,
            snapshot = snapshot,
            page = ReelsRoundRobinUiPage(
                suffix = items,
                frontierItemId = snapshot.anchor?.itemId,
                playbackItemId = session.playbackItemId,
                pendingSeekPositionMs = session.pendingSeekPositionMs,
                loading = loading,
                stop = stop,
                error = error,
                owner = session.owner,
            ),
        )
    }

    private suspend fun publishControl(
        session: Session,
        snapshot: ReelsRoundRobinSnapshot?,
        loading: Boolean,
        stop: ReelsRoundRobinStop,
        error: Throwable?,
    ) {
        val effective = snapshot ?: session.lastSnapshot
        val items = effective?.let { buildSuffix(session, it) } ?: session.lastSuffix
        publishPage(
            session = session,
            snapshot = effective,
            page = ReelsRoundRobinUiPage(
                suffix = items,
                frontierItemId = effective?.anchor?.itemId,
                playbackItemId = session.playbackItemId,
                pendingSeekPositionMs = session.pendingSeekPositionMs,
                loading = loading,
                stop = stop,
                error = error,
                owner = session.owner,
            ),
        )
    }

    private suspend fun publishPage(
        session: Session,
        snapshot: ReelsRoundRobinSnapshot?,
        page: ReelsRoundRobinUiPage,
    ) {
        withContext(mainDispatcher) {
            synchronized(session.publishLock) {
                if (session.stopped || activeSession !== session) return@withContext
                if (!runCatching { isCurrentGeneration(session.owner) }.getOrDefault(false)) {
                    return@withContext
                }
                session.lastSuffix = page.suffix
                session.lastStop = page.stop
                session.lastError = page.error
                if (snapshot != null) session.lastSnapshot = snapshot
                retainLiveItems(session, snapshot)
                if (session.lastPublishedPage == page) return@withContext
                session.lastPublishedPage = page
                publish(page)
                // A resume seek is a one-shot instruction: later pages must not replay it.
                session.pendingSeekPositionMs = null
            }
        }
    }

    private suspend fun publishStandalone(epoch: Long, error: Throwable) {
        withContext(mainDispatcher) {
            if (attachEpoch.get() != epoch) return@withContext
            publish(
                ReelsRoundRobinUiPage(
                    suffix = emptyList(),
                    frontierItemId = null,
                    playbackItemId = null,
                    pendingSeekPositionMs = null,
                    loading = false,
                    stop = ReelsRoundRobinStop.PAUSED,
                    error = error,
                    owner = null,
                ),
            )
        }
    }

    private fun buildSuffix(session: Session, snapshot: ReelsRoundRobinSnapshot): List<ShortVideoItem> {
        val frames = snapshot.frames.sortedBy { it.number }
        if (frames.isEmpty()) return emptyList()
        val anchor = snapshot.anchor
        val startIndex = if (anchor != null) {
            frames.indexOfFirst { it.number == anchor.round }.coerceAtLeast(0)
        } else {
            0
        }
        val entries = snapshot.entries.associateBy { it.candidate.itemId }
        val result = mutableListOf<ShortVideoItem>()
        val seen = HashSet<String>()
        for (frameIndex in startIndex until frames.size) {
            val frame = frames[frameIndex]
            var slot = 0
            if (frameIndex == startIndex && anchor != null && frame.number == anchor.round) {
                val byKey = frame.members.indexOf(anchor.sourceKey)
                if (byKey >= 0) {
                    slot = byKey + 1
                } else {
                    val byItem = frame.members.indexOfFirst {
                        frame.turns[it]?.itemId == anchor.itemId
                    }
                    slot = if (byItem >= 0) byItem + 1 else 0
                }
            }
            for (memberIndex in slot until frame.members.size) {
                val member = frame.members[memberIndex]
                val itemId = frame.turns[member]?.itemId ?: continue
                if (!seen.add(itemId)) continue
                val entry = entries[itemId] ?: continue
                if (entry.sourceKey != member) continue
                if (entry.validation != ReelsRoundRobinValidation.ELIGIBLE) continue
                val item = session.liveItems[itemId]
                    ?: with(ReelsCandidateMapper) { entry.candidate.toShortVideoItem() }
                if (!session.eligibility.allows(item.author, item.id, session.unseenOnly)) continue
                result += item
                if (result.size >= PAGE_LIMIT) return result
            }
        }
        return result
    }

    private fun retainLiveItems(session: Session, snapshot: ReelsRoundRobinSnapshot?) {
        if (snapshot == null) return
        val keep = HashSet<String>()
        snapshot.entries.forEach { keep += it.candidate.itemId }
        snapshot.frames.forEach { frame ->
            frame.turns.values.forEach { turn -> turn.itemId?.let(keep::add) }
        }
        session.lastSuffix.forEach { keep += it.id }
        session.playbackItemId?.let(keep::add)
        session.liveItems.keys.toList().forEach { itemId ->
            if (itemId !in keep) session.liveItems.remove(itemId)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Scheduling / small helpers
    // ---------------------------------------------------------------------------------------

    private fun ensurePlaybackItem(session: Session, itemId: String): Boolean {
        if (session.playbackItemId != null) return session.playbackItemId == itemId
        val item = session.liveItems[itemId]
            ?: session.lastSuffix.firstOrNull { it.id == itemId }
            ?: return false
        session.playbackItemId = itemId
        if (!session.incognito) {
            checkpointWriter.visitStarted(item, session.owner.persistEligible)
        }
        return true
    }

    private fun prepareActivation(session: Session, itemId: String) {
        session.playbackItemId = itemId
        // A seek hint is a one-shot resume instruction, never a live position echo: only a
        // stored positive position re-activates as a seek, and an activation without one
        // clears any hint left by the previous card.
        session.pendingSeekPositionMs = session.samplePositions[itemId]?.takeIf { it > 0L }
        val item = session.liveItems[itemId]
            ?: session.lastSuffix.firstOrNull { it.id == itemId }
        if (item != null) {
            session.liveItems[itemId] = item
            if (!session.incognito) {
                checkpointWriter.visitStarted(item, session.owner.persistEligible)
            }
        }
    }

    /** The earliest source retry, head check, provider or catalog backoff still in the future. */
    private fun earliestPendingWake(
        session: Session,
        snapshot: ReelsRoundRobinSnapshot?,
        now: Long,
    ): Long? {
        val times = mutableListOf<Long>()
        snapshot?.sources?.forEach { source ->
            source.retryAt?.takeIf { it > now }?.let(times::add)
            source.nextHeadCheckAt?.takeIf { it > now }?.let(times::add)
        }
        session.providerRetryAt?.takeIf { it > now }?.let(times::add)
        session.catalogRetryAt?.takeIf { it > now }?.let(times::add)
        return times.minOrNull()
    }

    /** Only explicit backoffs, never a future head check: that one is a scan, not a retry. */
    private fun earliestPendingRetry(
        session: Session,
        snapshot: ReelsRoundRobinSnapshot?,
        now: Long,
    ): Long? {
        val times = mutableListOf<Long>()
        snapshot?.sources?.forEach { source ->
            source.retryAt?.takeIf { it > now }?.let(times::add)
        }
        session.providerRetryAt?.takeIf { it > now }?.let(times::add)
        session.catalogRetryAt?.takeIf { it > now }?.let(times::add)
        return times.minOrNull()
    }

    private fun scheduleWake(
        session: Session,
        snapshot: ReelsRoundRobinSnapshot?,
        stop: ReelsRoundRobinStop,
        wakeAt: Long? = null,
    ) {
        wakeJob?.cancel()
        wakeJob = null
        session.scheduledWakeAt = null
        if (session.stopped || activeSession !== session) return
        val now = nowMs()
        val periodicAt = session.catalogWakeAt?.takeIf { it > now }
        // A deferred catalog (FLOOD_WAIT or a partial window) must keep a wake of its own: a
        // healthy projection stops at PAGE_READY/EMPTY or the reservation horizon, where the
        // PAUSED branch would otherwise arm nothing.
        val catalogRetryAt = if (session.catalogRetryForced) {
            session.catalogRetryAt?.takeIf { it > now } ?: (now + CATALOG_RETRY_MS)
        } else {
            null
        }
        val stopAt: Long? = when (stop) {
            ReelsRoundRobinStop.WAIT_RETRY -> {
                earliestPendingWake(session, snapshot, now) ?: (now + DEFAULT_RETRY_MS)
            }
            ReelsRoundRobinStop.PAGE_READY -> {
                // A page can still be publishable while one source keeps a retry backoff. Wake
                // at that due time so its slot is re-decided instead of being reset eagerly.
                // Future head checks are deliberately excluded: they are resumed by the
                // activation/recovery path, not by every publishable page.
                earliestPendingRetry(session, snapshot, now)
            }
            ReelsRoundRobinStop.EMPTY -> {
                if (session.emptyAutoUsed) {
                    null
                } else {
                    session.emptyAutoUsed = true
                    now + EMPTY_AUTO_INTERVAL_MS
                }
            }
            ReelsRoundRobinStop.PAUSED -> {
                if (session.catalogRetryForced || session.catalogRevisionApplied == null) {
                    session.catalogRetryAt?.takeIf { it > now } ?: (now + CATALOG_RETRY_MS)
                } else {
                    null
                }
            }
            ReelsRoundRobinStop.NEED_INPUT -> {
                if (snapshot?.sources?.isNotEmpty() == true) {
                    now + NEED_INPUT_WAKE_MS
                } else {
                    null
                }
            }
            else -> null
        }
        val at = listOfNotNull(wakeAt, stopAt, periodicAt, catalogRetryAt).minOrNull() ?: return
        session.scheduledWakeAt = at
        debug("schedule:$at@$now stop=$stop")
        wakeJob = scope.launch(ioDispatcher) {
            delay((at - now).coerceAtLeast(0L))
            debug("fire:$at")
            commands.trySend(Command.Wake(session, at))
        }
    }

    private fun isCurrent(session: Session, epoch: Long): Boolean =
        activeSession === session && !session.stopped && operationEpoch.get() == epoch

    private suspend fun readSnapshot(session: Session): ReelsRoundRobinSnapshot? {
        return try {
            writeMutex.withLock { session.repository.read(session.handle) }.also {
                session.lastSnapshot = it
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun releaseQuietly(
        repository: ReelsRoundRobinRepository,
        handle: ReelsRoundRobinHandle,
    ) {
        try {
            writeMutex.withLock { repository.release(handle) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // The durable projection outlives the handle; a failed release is not fatal.
        }
    }

    private suspend fun mutate(
        session: Session,
        snapshot: ReelsRoundRobinSnapshot,
        change: ReelsRoundRobinMutation,
    ): ReelsRoundRobinCommit {
        val commit = writeMutex.withLock {
            session.repository.mutate(session.handle, snapshot.revision, change)
        }
        session.lastSnapshot = commit.snapshot
        return commit
    }

    /**
     * Read-only optional dependency access. Cancellation and fatal errors are never swallowed.
     */
    private suspend fun <T> optional(default: T, block: suspend () -> T): T {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            if (t is Error) throw t
            default
        }
    }

    /** Like [optional], but a failed read stays distinguishable from an empty result. */
    private suspend fun <T> optionalRead(block: suspend () -> T): T? {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            if (t is Error) throw t
            null
        }
    }

    private sealed interface CatalogGate {
        data class Applied(val snapshot: ReelsRoundRobinSnapshot) : CatalogGate
        data object Deferred : CatalogGate
        data object Fenced : CatalogGate
        data object None : CatalogGate
    }

    private data class RecoveryPlan(
        val sources: Set<String>,
        val wakeAt: Long?,
    )

    private data class DispatchResult(
        val outcomes: List<Outcome>,
        val deferredAt: Long?,
        /** itemId -> owning source slot for entry-less needs that must be released. */
        val repairs: Map<String, String> = emptyMap(),
    )

    private data class PreparedSession(
        val session: Session,
        val snapshot: ReelsRoundRobinSnapshot,
        val restoredActive: ReelsActiveCheckpoint?,
    )

    /** Snapshot-safe eligibility predicate handed to the incognito memory adapter. */
    private class LocalEligibilityGate {
        @Volatile
        var session: Session? = null

        fun allows(candidate: ReelsCandidate): Boolean {
            val current = session ?: return true
            return current.eligibility.allows(
                candidate.author,
                candidate.itemId,
                current.unseenOnly,
            )
        }
    }

    private data class Outcome(
        val updates: List<ReelsRoundRobinUpdate>,
        val fenced: Boolean = false,
    )

    private data class Eligibility(
        val hiddenVideos: Set<String>,
        val hiddenAuthors: Set<String>,
        val consumed: Set<String>,
    ) {
        fun allows(author: String?, itemId: String, unseenOnly: Boolean): Boolean =
            itemId !in hiddenVideos &&
                (author == null || author !in hiddenAuthors) &&
                (!unseenOnly || itemId !in consumed)

        fun allowsId(itemId: String, unseenOnly: Boolean): Boolean =
            itemId !in hiddenVideos && (!unseenOnly || itemId !in consumed)

        companion object {
            fun from(hidden: List<ReelsHiddenEntry>, consumed: Set<String>): Eligibility {
                val videos = HashSet<String>()
                val authors = HashSet<String>()
                hidden.forEach { entry ->
                    when (entry.kind) {
                        "video", "item" -> videos += entry.value
                        ReelsHiddenEntry.KIND_AUTHOR -> authors += entry.value
                    }
                }
                return Eligibility(videos, authors, consumed)
            }
        }
    }

    private class Session(
        val source: AnimeFeedRoundRobinSource,
        val sourceId: Long,
        val accountScope: String,
        val historyGeneration: Long,
        val unseenOnly: Boolean,
        val incognito: Boolean,
        val filters: FrozenReelsFilters,
        val storageKey: String,
        val key: ReelsSessionKey,
        val owner: ReelsRoundRobinRenderOwner,
        val repository: ReelsRoundRobinRepository,
        val handle: ReelsRoundRobinHandle,
        @Volatile var expectedContext: FeedReplayContext,
        @Volatile var eligibility: Eligibility,
        @Volatile var playbackItemId: String?,
        @Volatile var pendingSeekPositionMs: Long?,
    ) {
        val publishLock = Any()
        val retryLock = Any()
        val eligibilityLock = Any()
        val liveItems = ConcurrentHashMap<String, ShortVideoItem>()
        val samplePositions = ConcurrentHashMap<String, Long>()
        val retryCounts = ConcurrentHashMap<String, Int>()

        @Volatile
        var stopped: Boolean = false

        @Volatile
        var lastSuffix: List<ShortVideoItem> = emptyList()

        @Volatile
        var lastStop: ReelsRoundRobinStop = ReelsRoundRobinStop.PAUSED

        @Volatile
        var lastError: Throwable? = null

        @Volatile
        var lastSnapshot: ReelsRoundRobinSnapshot? = null

        @Volatile
        var catalogRevisionApplied: String? = null

        @Volatile
        var catalogRetryAt: Long? = null

        @Volatile
        var catalogRetryExplicit: Boolean = false

        /** The last catalog fetch was deferred, so [catalogRetryAt] must force a re-fetch. */
        @Volatile
        var catalogRetryForced: Boolean = false

        /** Next periodic catalog reconciliation while the feed stays attached. */
        @Volatile
        var catalogWakeAt: Long? = null

        @Volatile
        var providerRetryAt: Long? = null

        @Volatile
        var legacyImported: Boolean = false

        @Volatile
        var emptyAutoUsed: Boolean = false

        @Volatile
        var scheduledWakeAt: Long? = null

        @Volatile
        var lastRecoveryAt: Long? = null

        @Volatile
        var lastPublishedPage: ReelsRoundRobinUiPage? = null

        fun mergeConsumed(ids: Set<String>) {
            if (ids.isEmpty()) return
            synchronized(eligibilityLock) {
                eligibility = eligibility.copy(consumed = eligibility.consumed + ids)
            }
        }
    }
}
