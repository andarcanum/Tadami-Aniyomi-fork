package eu.kanade.tachiyomi.ui.reels

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.domain.reels.anime.interactor.ReelsRoundRobinCleanup
import tachiyomi.domain.reels.anime.interactor.ReelsRoundRobinScheduler
import tachiyomi.domain.reels.anime.model.ReelsCandidate
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinAnchor
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinCatalog
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinCommit
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinEntry
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinFrame
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinHandle
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinMutation
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinSnapshot
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinSourceState
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinStop
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinUpdate
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinValidation
import tachiyomi.domain.reels.anime.model.ReelsSessionHandle
import tachiyomi.domain.reels.anime.model.ReelsSessionKey
import tachiyomi.domain.reels.anime.model.ReelsSessionStaleException
import tachiyomi.domain.reels.anime.repository.ReelsRoundRobinRepository
import java.util.UUID

/**
 * Ephemeral [ReelsRoundRobinRepository] for incognito mode.
 *
 * It runs the same pure [ReelsRoundRobinScheduler] as the durable adapter and implements the
 * same revision/fromCheckpoint fences, but it never touches the database, the session tables or
 * the persistent round-robin projection: frames, checkpoints and reservations live only in this
 * object and disappear with it. Eligibility reads (consumed ids, hidden rows) are performed by
 * the coordinator, not here, so incognito stays read-only on persistent state.
 */
class InMemoryReelsRoundRobinRepository(
    /**
     * Optional payload lookup used by [ReelsRoundRobinMutation.Import]. The durable adapter
     * reads the same payloads from `reels_candidate`; incognito mode does not import a legacy
     * backlog, so the default lookup is null.
     */
    private val resolvePayload: ((scope: String, sourceId: Long, itemId: String) -> ReelsCandidate?)? = null,
    /**
     * Live local eligibility (hidden/consumed). The coordinator hands the incognito adapter the
     * same predicate the durable adapter applies from its SQL rows, so both schedulers see
     * locally ineligible entries as `FILTERED` instead of reserving them.
     */
    @Volatile
    var locallyEligible: ((ReelsCandidate) -> Boolean)? = null,
) : ReelsRoundRobinRepository {

    private val mutex = Mutex()
    private val states = LinkedHashMap<StateId, MemoryState>()

    override suspend fun open(
        key: ReelsSessionKey,
        ownerNonce: String,
        historyGeneration: Long,
        unseenOnly: Boolean,
    ): ReelsRoundRobinHandle = mutex.withLock {
        val id = StateId(key.accountScopeId, key.sourceId)
        var state = states[id]
        if (state == null || state.historyGeneration != historyGeneration) {
            state = emptyState(key.contextKey, unseenOnly, historyGeneration)
            states[id] = state
        } else if (state.contextKey != key.contextKey || state.unseenOnly != unseenOnly) {
            recontext(state, key.contextKey, unseenOnly)
        }
        state.ownerNonce = ownerNonce
        val handle = ReelsSessionHandle(
            key = ReelsSessionKey(key.accountScopeId, key.sourceId, state.contextKey),
            sessionId = UUID.randomUUID().toString(),
            ownerNonce = ownerNonce,
            historyGeneration = historyGeneration,
        )
        ReelsRoundRobinHandle(handle)
    }

    override suspend fun read(handle: ReelsRoundRobinHandle): ReelsRoundRobinSnapshot = mutex.withLock {
        val state = requireState(handle)
        snapshot(handle.session.key, state)
    }

    override suspend fun mutate(
        handle: ReelsRoundRobinHandle,
        expectedRevision: Long,
        change: ReelsRoundRobinMutation,
    ): ReelsRoundRobinCommit = mutex.withLock {
        val state = requireState(handle)
        if (change is ReelsRoundRobinMutation.Pump) {
            val pages = change.updates.filterIsInstance<ReelsRoundRobinUpdate.Page>()
            require(pages.map { it.sourceKey }.distinct().size == pages.size) {
                "Duplicate source responses inside one round-robin pump"
            }
            val pageOnly = pages.isNotEmpty() && pages.size == change.updates.size
            if (!pageOnly && state.revision != expectedRevision) throw ReelsSessionStaleException()
            val sources = state.sources.associateBy { it.key }
            pages.forEach { page ->
                val source = sources[page.sourceKey]
                    ?: throw IllegalArgumentException("Unknown round-robin source ${page.sourceKey}")
                if (source.checkpoint != page.fromCheckpoint) throw ReelsSessionStaleException()
            }
        } else if (state.revision != expectedRevision) {
            throw ReelsSessionStaleException()
        }

        when (change) {
            is ReelsRoundRobinMutation.Catalog -> catalog(handle.session.key, state, change)
            is ReelsRoundRobinMutation.Import -> import(handle.session.key, state, change)
            is ReelsRoundRobinMutation.Pump -> pump(handle.session.key, state, change)
            is ReelsRoundRobinMutation.Activate -> activate(handle.session.key, state, change)
            is ReelsRoundRobinMutation.Recheck -> recheck(handle.session.key, state, change)
            is ReelsRoundRobinMutation.Repair -> repair(handle.session.key, state, change)
        }
    }

    override fun observeEligibility(scope: String, sourceId: Long): Flow<Unit> = emptyFlow()

    override suspend fun release(handle: ReelsRoundRobinHandle) = mutex.withLock {
        val state = states[StateId(handle.session.key.accountScopeId, handle.session.key.sourceId)]
            ?: return@withLock
        if (state.ownerNonce == handle.session.ownerNonce) {
            state.ownerNonce = null
        }
    }

    override suspend fun clearScope(scope: String) {
        mutex.withLock {
            states.keys.removeAll { it.scope == scope }
        }
    }

    override suspend fun resetProjection(scope: String, sourceId: Long) {
        mutex.withLock {
            states.remove(StateId(scope, sourceId))
        }
    }

    /**
     * Test seam mirroring a durable payload prune: the reserved frame turn survives while the
     * payload and its `reels_rr_item` association disappear. Used by coordinator tests that
     * exercise the entry-less `Need.Revalidate` repair path.
     */
    internal suspend fun forgetPayloads(itemIds: Collection<String>) {
        if (itemIds.isEmpty()) return
        mutex.withLock {
            val ids = itemIds.toSet()
            states.values.forEach { state ->
                state.entries = state.entries.filterNot { it.candidate.itemId in ids }
                ids.forEach(state.payloads::remove)
            }
        }
    }

    private fun requireState(handle: ReelsRoundRobinHandle): MemoryState {
        val key = handle.session.key
        val state = states[StateId(key.accountScopeId, key.sourceId)]
            ?: throw ReelsSessionStaleException()
        if (state.ownerNonce != handle.session.ownerNonce ||
            state.contextKey != key.contextKey ||
            state.historyGeneration != handle.session.historyGeneration
        ) {
            throw ReelsSessionStaleException()
        }
        return state
    }

    private fun catalog(
        key: ReelsSessionKey,
        state: MemoryState,
        change: ReelsRoundRobinMutation.Catalog,
    ): ReelsRoundRobinCommit {
        val current = snapshot(key, state)
        val reconciled = ReelsRoundRobinScheduler.reconcile(current, change.value)
        if (reconciled == current) {
            return ReelsRoundRobinCommit(current, emptyList(), emptyList(), ReelsRoundRobinStop.EMPTY)
        }
        val persisted = persist(key, state, reconciled)
        return ReelsRoundRobinCommit(persisted, emptyList(), emptyList(), ReelsRoundRobinStop.EMPTY)
    }

    private fun import(
        key: ReelsSessionKey,
        state: MemoryState,
        change: ReelsRoundRobinMutation.Import,
    ): ReelsRoundRobinCommit {
        if (change.identities.isEmpty()) {
            return ReelsRoundRobinCommit(snapshot(key, state), emptyList(), emptyList(), ReelsRoundRobinStop.EMPTY)
        }
        val sources = state.sources.associateBy { it.key }
        val entries = state.entries.toMutableList()
        change.identities.forEach { (itemId, identity) ->
            if (identity.sourceKey !in sources) return@forEach
            val existingIndex = entries.indexOfFirst { it.candidate.itemId == itemId }
            if (existingIndex >= 0) {
                val existing = entries[existingIndex]
                if (existing.sourceKey != identity.sourceKey || existing.orderKey != identity.orderKey) {
                    entries[existingIndex] = existing.copy(
                        sourceKey = identity.sourceKey,
                        orderKey = identity.orderKey,
                    )
                }
            } else {
                val candidate = resolvePayload?.invoke(key.accountScopeId, key.sourceId, itemId)
                    ?: state.payloads[itemId]
                    ?: return@forEach
                state.payloads[itemId] = candidate
                entries += ReelsRoundRobinEntry(
                    candidate = candidate,
                    sourceKey = identity.sourceKey,
                    orderKey = identity.orderKey,
                    contentKey = null,
                    checkedContext = null,
                    validation = ReelsRoundRobinValidation.UNKNOWN,
                    round = null,
                    ordinal = null,
                )
            }
        }
        val target = snapshot(key, state).copy(entries = entries.toList())
        if (target.entries == state.entries) {
            return ReelsRoundRobinCommit(snapshot(key, state), emptyList(), emptyList(), ReelsRoundRobinStop.EMPTY)
        }
        val persisted = persist(key, state, target)
        return ReelsRoundRobinCommit(persisted, emptyList(), emptyList(), ReelsRoundRobinStop.EMPTY)
    }

    private fun repair(
        key: ReelsSessionKey,
        state: MemoryState,
        change: ReelsRoundRobinMutation.Repair,
    ): ReelsRoundRobinCommit {
        val current = snapshot(key, state)
        val repaired = ReelsRoundRobinScheduler.repair(current, change.invalidItemIds)
        if (repaired == current) {
            return ReelsRoundRobinCommit(current, emptyList(), emptyList(), ReelsRoundRobinStop.EMPTY)
        }
        return ReelsRoundRobinCommit(
            persist(key, state, repaired),
            emptyList(),
            emptyList(),
            ReelsRoundRobinStop.EMPTY,
        )
    }

    private fun pump(
        key: ReelsSessionKey,
        state: MemoryState,
        change: ReelsRoundRobinMutation.Pump,
    ): ReelsRoundRobinCommit {
        val context = key.contextKey
        val sources = state.sources.associateByTo(LinkedHashMap()) { it.key }
        var entries = state.entries
        val invalid = LinkedHashSet<String>()

        change.updates.forEach { update ->
            when (update) {
                is ReelsRoundRobinUpdate.Page -> {
                    val source = sources[update.sourceKey]
                        ?: throw IllegalArgumentException("Unknown round-robin source ${update.sourceKey}")
                    require(update.nextCheckpoint.isNotBlank()) {
                        "Round-robin page for ${update.sourceKey} has a blank checkpoint"
                    }
                    update.entries.forEach { entry ->
                        require(
                            entry.sourceKey == update.sourceKey &&
                                entry.candidate.accountScopeId == key.accountScopeId &&
                                entry.candidate.sourceId == key.sourceId &&
                                entry.candidate.itemId.isNotBlank() &&
                                entry.orderKey > 0L,
                        ) {
                            "Round-robin entry identity does not match source ${update.sourceKey}"
                        }
                    }
                    var pageAccepted = false
                    update.entries.forEach { entry ->
                        val localAllowed = locallyEligible?.invoke(entry.candidate) != false
                        if (localAllowed) pageAccepted = true
                        val normalized = if (!localAllowed) {
                            // The payload was seen in this context; only the local verdict is
                            // FILTERED, so a later recheck can restore it without a resolve.
                            entry.copy(
                                checkedContext = context,
                                validation = ReelsRoundRobinValidation.FILTERED,
                                round = null,
                                ordinal = null,
                            )
                        } else {
                            entry.copy(
                                checkedContext = if (entry.validation == ReelsRoundRobinValidation.ELIGIBLE) {
                                    context
                                } else {
                                    entry.checkedContext
                                },
                                round = null,
                                ordinal = null,
                            )
                        }
                        val index = entries.indexOfFirst {
                            it.candidate.itemId == entry.candidate.itemId
                        }
                        entries = if (index >= 0) {
                            entries.toMutableList().also { it[index] = normalized }
                        } else {
                            entries + normalized
                        }
                        if (normalized.validation != ReelsRoundRobinValidation.ELIGIBLE) {
                            invalid += normalized.candidate.itemId
                        }
                    }
                    val fruitless = update.fruitless ||
                        (update.entries.isNotEmpty() && !pageAccepted)
                    sources[update.sourceKey] = source.copy(
                        checkpoint = update.nextCheckpoint,
                        checkpointContext = context,
                        historyExhausted = update.historyExhausted,
                        nextHeadCheckAt = update.nextHeadCheckAt
                            ?: if (fruitless) {
                                change.nowMs + ReelsRoundRobinScheduler.FRUITLESS_RESCAN_MS
                            } else {
                                null
                            },
                        retryAt = null,
                        retryReason = null,
                        fruitlessScan = fruitless,
                    )
                }

                is ReelsRoundRobinUpdate.Deferred -> {
                    sources[update.sourceKey]?.let { source ->
                        sources[update.sourceKey] = source.copy(
                            retryAt = update.retryAt,
                            retryReason = update.reason,
                        )
                    }
                }

                is ReelsRoundRobinUpdate.Unavailable -> {
                    sources[update.sourceKey]?.let { source ->
                        sources[update.sourceKey] = source.copy(
                            present = false,
                            retryAt = null,
                            retryReason = null,
                        )
                        entries.asSequence()
                            .filter { it.sourceKey == update.sourceKey && it.round != null }
                            .mapTo(invalid) { it.candidate.itemId }
                    }
                }

                is ReelsRoundRobinUpdate.ResetCursor -> {
                    sources[update.sourceKey]?.let { source ->
                        sources[update.sourceKey] = source.copy(
                            checkpoint = null,
                            checkpointContext = null,
                            historyExhausted = false,
                            nextHeadCheckAt = null,
                            retryAt = null,
                            retryReason = null,
                            // Parity with the durable adapter: a reset rewinds the source, so a
                            // stale fruitless budget must not keep skipping it as FILTER_BUDGET.
                            fruitlessScan = false,
                        )
                    }
                }

                is ReelsRoundRobinUpdate.Revalidated -> {
                    val candidate = update.candidate?.takeIf { it.itemId == update.itemId }
                    if (update.candidate != null && candidate == null) {
                        invalid += update.itemId
                        return@forEach
                    }
                    val existingIndex = entries.indexOfFirst { it.candidate.itemId == update.itemId }
                    if (existingIndex >= 0) {
                        val existing = entries[existingIndex]
                        val resolved = candidate ?: existing.candidate
                        val localAllowed = locallyEligible?.invoke(resolved) != false
                        val validation = when {
                            update.validation == ReelsRoundRobinValidation.ELIGIBLE && localAllowed ->
                                ReelsRoundRobinValidation.ELIGIBLE
                            update.validation == ReelsRoundRobinValidation.ELIGIBLE ->
                                ReelsRoundRobinValidation.FILTERED
                            else -> update.validation
                        }
                        // A source-side FilteredOut has no candidate; it is not a local filter
                        // that an eligibility recheck may undo.
                        val contentFiltered = update.validation == ReelsRoundRobinValidation.FILTERED &&
                            candidate == null
                        val updated = existing.copy(
                            candidate = resolved,
                            checkedContext = if (contentFiltered) null else context,
                            validation = validation,
                            round = null,
                            ordinal = null,
                        )
                        entries = entries.toMutableList().also { it[existingIndex] = updated }
                        if (validation != ReelsRoundRobinValidation.ELIGIBLE) invalid += update.itemId
                    } else {
                        // No entry can be re-created here (the in-memory adapter has no
                        // identity row); release the turn so the coordinator can repair it.
                        invalid += update.itemId
                    }
                    val sourceKey = entries.firstOrNull { it.candidate.itemId == update.itemId }?.sourceKey
                    if (sourceKey != null && update.retryAt != null) {
                        sources[sourceKey]?.let { source ->
                            sources[sourceKey] = source.copy(
                                retryAt = update.retryAt,
                                retryReason = "deferred",
                            )
                        }
                    }
                }
            }
        }

        entries.asSequence()
            .filter { it.round != null && it.validation != ReelsRoundRobinValidation.ELIGIBLE }
            .mapTo(invalid) { it.candidate.itemId }

        var current = snapshot(key, state).copy(
            sources = sources.values.sortedWith(compareBy({ it.order }, { it.key })),
            entries = entries.distinctBy { it.candidate.itemId },
        )
        current = ReelsRoundRobinScheduler.repair(current, invalid)

        // Same rule as the durable adapter: a locally hidden/consumed entry is not selectable,
        // even when the scheduler itself has no reason to reject it.
        val locallyUnusable = current.entries
            .filter { locallyEligible?.invoke(it.candidate) == false }
            .mapTo(HashSet()) { it.candidate.itemId }
        if (locallyUnusable.isNotEmpty()) {
            current = current.copy(
                entries = current.entries.map { entry ->
                    if (entry.candidate.itemId in locallyUnusable) {
                        // Keep the context proof: hiding/consuming is a local verdict, and the
                        // next recheck may re-admit the same validated payload.
                        entry.copy(
                            validation = ReelsRoundRobinValidation.FILTERED,
                            round = null,
                            ordinal = null,
                        )
                    } else {
                        entry
                    }
                },
            )
        }

        val sourcesByKey = current.sources.associateBy { it.key }
        val eligibleIds = current.entries
            .asSequence()
            .filter {
                it.validation == ReelsRoundRobinValidation.ELIGIBLE &&
                    it.checkedContext == context &&
                    sourcesByKey[it.sourceKey]?.let { source -> source.present && !source.excluded } == true &&
                    locallyEligible?.invoke(it.candidate) != false
            }
            .mapTo(HashSet()) { it.candidate.itemId }

        val selection = ReelsRoundRobinScheduler.select(
            state = current,
            eligibleIds = eligibleIds,
            nowMs = change.nowMs,
            limit = change.limit,
        )
        if (selection.snapshot == state.toSnapshot(key) && selection.page.isEmpty()) {
            return selection
        }
        val persisted = persist(key, state, selection.snapshot)
        return selection.copy(snapshot = persisted)
    }

    private fun activate(
        key: ReelsSessionKey,
        state: MemoryState,
        change: ReelsRoundRobinMutation.Activate,
    ): ReelsRoundRobinCommit {
        val current = snapshot(key, state)
        val activated = ReelsRoundRobinScheduler.activate(current, change.itemId)
        if (activated == current) {
            return ReelsRoundRobinCommit(current, emptyList(), emptyList(), ReelsRoundRobinStop.PAUSED)
        }
        return ReelsRoundRobinCommit(
            persist(key, state, activated),
            emptyList(),
            emptyList(),
            ReelsRoundRobinStop.PAUSED,
        )
    }

    private fun recheck(
        key: ReelsSessionKey,
        state: MemoryState,
        change: ReelsRoundRobinMutation.Recheck,
    ): ReelsRoundRobinCommit {
        if (change.rewindSources.isEmpty()) {
            return ReelsRoundRobinCommit(snapshot(key, state), emptyList(), emptyList(), ReelsRoundRobinStop.PAUSED)
        }
        val current = snapshot(key, state)
        val context = key.contextKey
        val played = ReelsRoundRobinCleanup.playedItemIds(
            frames = current.frames,
            anchor = current.anchor,
        )
        val activeRound = current.anchor?.round ?: current.frames.minOfOrNull { it.number }
        val released = LinkedHashSet<String>()
        val sources = current.sources.map { source ->
            if (source.key !in change.rewindSources) return@map source
            source.copy(retryAt = null, retryReason = null, fruitlessScan = false)
        }
        val entries = current.entries.map { entry ->
            if (entry.sourceKey !in change.rewindSources) return@map entry
            val itemId = entry.candidate.itemId
            val allowed = locallyEligible?.invoke(entry.candidate) != false
            when {
                !allowed -> {
                    // Never re-admit a hidden/consumed card; release only its future turn.
                    released += itemId
                    entry.copy(
                        validation = ReelsRoundRobinValidation.FILTERED,
                        round = null,
                        ordinal = null,
                    )
                }
                itemId in played -> {
                    // The played prefix keeps its turns; only the lazy payload validation is
                    // reset, so a later render can revalidate it without moving the frontier.
                    entry.copy(
                        validation = ReelsRoundRobinValidation.UNKNOWN,
                        checkedContext = null,
                    )
                }
                entry.validation == ReelsRoundRobinValidation.ELIGIBLE &&
                    activeRound != null && entry.round == activeRound -> {
                    // Re-decide only the current round's unresolved tail. A later frame keeps
                    // its reservations, so filtering one card does not rewind the whole source.
                    released += itemId
                    entry.copy(
                        validation = ReelsRoundRobinValidation.UNKNOWN,
                        checkedContext = null,
                        round = null,
                        ordinal = null,
                    )
                }
                entry.validation == ReelsRoundRobinValidation.ELIGIBLE -> entry
                entry.validation == ReelsRoundRobinValidation.FILTERED &&
                    entry.checkedContext == context -> {
                    // Locally filtered before and eligible again: the payload is still valid for
                    // this context, so no resolve round trip is needed.
                    entry.copy(validation = ReelsRoundRobinValidation.ELIGIBLE)
                }
                else -> {
                    // Staged unchecked or content-filtered: leave the verdict, only drop a stale
                    // reservation so the scheduler can re-decide the slot.
                    released += itemId
                    entry.copy(round = null, ordinal = null)
                }
            }
        }
        val updated = current.copy(
            sources = sources,
            frames = ReelsRoundRobinCleanup.removeFutureTurns(
                frames = ReelsRoundRobinCleanup.clearFutureSkips(
                    frames = current.frames,
                    anchor = current.anchor,
                    sources = change.rewindSources,
                ),
                anchor = current.anchor,
                itemIds = released,
            ),
            entries = entries,
        )
        if (updated == current) {
            return ReelsRoundRobinCommit(current, emptyList(), emptyList(), ReelsRoundRobinStop.PAUSED)
        }
        return ReelsRoundRobinCommit(
            persist(key, state, updated),
            emptyList(),
            emptyList(),
            ReelsRoundRobinStop.PAUSED,
        )
    }

    private fun persist(
        key: ReelsSessionKey,
        state: MemoryState,
        target: ReelsRoundRobinSnapshot,
    ): ReelsRoundRobinSnapshot {
        val normalized = normalizeReservations(target)
        val revision = state.revision + 1L
        val persisted = normalized.copy(revision = revision)
        state.revision = revision
        state.bootstrapComplete = persisted.bootstrapComplete
        state.catalogRevision = persisted.catalogRevision
        state.sources = persisted.sources
        state.frames = persisted.frames
        state.anchor = persisted.anchor
        state.entries = persisted.entries
        state.payloads.putAll(persisted.entries.associate { it.candidate.itemId to it.candidate })
        return snapshot(key, state)
    }

    private fun normalizeReservations(snapshot: ReelsRoundRobinSnapshot): ReelsRoundRobinSnapshot {
        val reservations = HashMap<String, Pair<Long, Long>>()
        snapshot.frames.forEach { frame ->
            val limit = playedLimit(frame, snapshot.anchor)
            frame.members.forEachIndexed { index, member ->
                if (index <= limit) return@forEachIndexed
                frame.turns[member]?.itemId?.let { itemId ->
                    reservations[itemId] = frame.number to index.toLong()
                }
            }
        }
        return snapshot.copy(
            entries = snapshot.entries.map { entry ->
                val reservation = reservations[entry.candidate.itemId]
                entry.copy(round = reservation?.first, ordinal = reservation?.second)
            },
        )
    }

    private fun playedLimit(frame: ReelsRoundRobinFrame, anchor: ReelsRoundRobinAnchor?): Int {
        if (anchor == null) return -1
        if (frame.number < anchor.round) return frame.members.lastIndex
        if (frame.number > anchor.round) return -1
        val byKey = frame.members.indexOf(anchor.sourceKey)
        if (byKey >= 0) return byKey
        return frame.members.indexOfFirst { frame.turns[it]?.itemId == anchor.itemId }
    }

    private fun snapshot(key: ReelsSessionKey, state: MemoryState): ReelsRoundRobinSnapshot {
        val snapshot = state.toSnapshot(key)
        return snapshot.copy(
            unmappedItemIds = futureItemIds(snapshot).filter { it !in state.payloads }.distinct(),
        )
    }

    private fun MemoryState.toSnapshot(key: ReelsSessionKey): ReelsRoundRobinSnapshot {
        return ReelsRoundRobinSnapshot(
            key = ReelsSessionKey(key.accountScopeId, key.sourceId, contextKey),
            revision = revision,
            unseenOnly = unseenOnly,
            bootstrapComplete = bootstrapComplete,
            catalogRevision = catalogRevision,
            sources = sources,
            frames = frames,
            anchor = anchor,
            entries = entries,
            unmappedItemIds = emptyList(),
        )
    }

    private fun futureItemIds(snapshot: ReelsRoundRobinSnapshot): List<String> {
        val ids = mutableListOf<String>()
        snapshot.frames.forEach { frame ->
            val limit = playedLimit(frame, snapshot.anchor)
            frame.members.forEachIndexed { index, member ->
                if (index > limit) {
                    frame.turns[member]?.itemId?.let(ids::add)
                }
            }
        }
        return ids
    }

    private fun recontext(state: MemoryState, contextKey: String, unseenOnly: Boolean) {
        val participants = state.sources
            .filter { it.present && !it.excluded }
            .sortedWith(compareBy({ it.order }, { it.key }))
            .map { it.key }
        val oldRound = state.anchor?.round ?: state.frames.minOfOrNull { it.number } ?: 0L
        val anchor = state.anchor
        val startIndex = if (anchor != null && participants.isNotEmpty()) {
            val index = participants.indexOf(anchor.sourceKey)
            if (index >= 0) (index + 1) % participants.size else 0
        } else {
            0
        }
        val newRound = if (participants.isEmpty()) 0L else oldRound + 1L
        val rotated = if (participants.isEmpty()) {
            emptyList()
        } else {
            participants.drop(startIndex) + participants.take(startIndex)
        }
        val frames = buildList {
            if (rotated.isNotEmpty()) {
                add(ReelsRoundRobinFrame(newRound, rotated, emptyMap()))
                if (state.bootstrapComplete) {
                    add(ReelsRoundRobinFrame(newRound + 1L, rotated, emptyMap()))
                }
            }
        }
        state.contextKey = contextKey
        state.unseenOnly = unseenOnly
        state.sources = state.sources.map { source ->
            source.copy(
                checkpoint = null,
                checkpointContext = null,
                historyExhausted = false,
                retryAt = null,
                retryReason = null,
                nextHeadCheckAt = null,
                fruitlessScan = false,
                joinRound = newRound,
            )
        }
        state.frames = frames
        state.anchor = null
        state.entries = emptyList()
        state.revision += 1L
    }

    private fun emptyState(contextKey: String, unseenOnly: Boolean, historyGeneration: Long): MemoryState =
        MemoryState(
            contextKey = contextKey,
            unseenOnly = unseenOnly,
            revision = 0L,
            bootstrapComplete = false,
            catalogRevision = null,
            sources = emptyList(),
            frames = emptyList(),
            anchor = null,
            entries = emptyList(),
            payloads = HashMap(),
            ownerNonce = null,
            historyGeneration = historyGeneration,
        )

    private data class StateId(val scope: String, val sourceId: Long)

    private data class MemoryState(
        var contextKey: String,
        var unseenOnly: Boolean,
        var revision: Long,
        var bootstrapComplete: Boolean,
        var catalogRevision: String?,
        var sources: List<ReelsRoundRobinSourceState>,
        var frames: List<ReelsRoundRobinFrame>,
        var anchor: ReelsRoundRobinAnchor?,
        var entries: List<ReelsRoundRobinEntry>,
        var payloads: HashMap<String, ReelsCandidate>,
        var ownerNonce: String?,
        var historyGeneration: Long,
    )
}
