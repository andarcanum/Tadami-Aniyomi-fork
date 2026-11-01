package tachiyomi.data.reels.anime

import dataanime.Reels_rr_item
import dataanime.Reels_rr_state
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
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
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinTurn
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinUpdate
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinValidation
import tachiyomi.domain.reels.anime.model.ReelsSessionHandle
import tachiyomi.domain.reels.anime.model.ReelsSessionKey
import tachiyomi.domain.reels.anime.model.ReelsSessionStaleException
import tachiyomi.domain.reels.anime.repository.ReelsRoundRobinRepository
import tachiyomi.mi.data.AnimeDatabase

/** Corrupt/unreadable persisted RR projection. Never treated as an empty feed. */
class ReelsRoundRobinStateCorruptException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/**
 * Transactional round-robin storage.
 *
 * The repository is the only writer of the RR tables; the scheduler stays pure. Every mutation
 * checks the session/owner/context/history-generation fence, applies its source checkpoints and
 * then commits the projection and the session queue projection under one revision CAS.
 *
 * A missing candidate payload is a durable need, not a reason to forget the slot: the frame turn
 * and its `unmappedItemIds` entry survive persisting pumps, so the scheduler keeps returning
 * `Need.Revalidate(itemId)` until the card is resolved. With FK ON the deleted payload cascades
 * the `reels_rr_item` association away, so `Revalidated` alone cannot satisfy the need (it has no
 * orderKey to recreate the row); the slot is released by [ReelsRoundRobinMutation.Repair] and
 * re-created by `Import`/scan with a verified identity. `Revalidated` still applies to rows that
 * continue to exist.
 */
class ReelsRoundRobinRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : ReelsRoundRobinRepository {

    override suspend fun open(
        key: ReelsSessionKey,
        ownerNonce: String,
        historyGeneration: Long,
        unseenOnly: Boolean,
    ): ReelsRoundRobinHandle {
        return handler.await(inTransaction = true) { db ->
            val existing = db.reels_rr_stateQueries.get(key.accountScopeId, key.sourceId)
                .executeAsOneOrNull()
            val sameContext = existing != null &&
                existing.context_key == key.contextKey &&
                existing.unseen_only == unseenOnly.toLong()
            val existingSession = db.reels_sessionQueries.getSession(
                key.accountScopeId,
                key.sourceId,
            ).executeAsOneOrNull()
            val sameGeneration = existingSession?.history_generation == historyGeneration
            val sameSessionContext = existingSession?.context_key == key.contextKey
            // First round-robin open over a same-generation legacy session: carry its active
            // card metadata over and remember the exact head. Nothing is inferred from titles
            // or authors; a different generation means the card was reset and is not a head.
            val legacyHeadItemId = if (
                existing == null &&
                existingSession != null &&
                !sameSessionContext &&
                sameGeneration &&
                existingSession.active_completed == 0L
            ) {
                existingSession.active_item_id
            } else {
                null
            }

            val session = ReelsRoundRobinStorage.claimSession(
                db = db,
                key = key,
                ownerNonce = ownerNonce,
                historyGeneration = historyGeneration,
                reset = existing != null && !sameContext,
                preserveCheckpoint = if (existing == null) {
                    legacyHeadItemId != null
                } else {
                    sameContext && sameGeneration && sameSessionContext
                },
                nowMs = System.currentTimeMillis(),
            )

            if (existing == null) {
                db.reels_rr_stateQueries.upsert(
                    account_scope_id = key.accountScopeId,
                    source_id = key.sourceId,
                    context_key = key.contextKey,
                    state_version = STATE_VERSION,
                    revision = 0L,
                    unseen_only = unseenOnly.toLong(),
                    bootstrap_complete = 0L,
                    catalog_revision = null,
                    frames_json = "[]",
                    anchor_round = null,
                    anchor_source_key = null,
                    anchor_item_id = null,
                    legacy_head_id = legacyHeadItemId,
                    updated_at_ms = System.currentTimeMillis(),
                )
            } else if (!sameContext) {
                recontext(db, existing, key, unseenOnly)
            }

            rebuildQueue(db, session, loadLight(db, key)?.snapshot)
            ReelsRoundRobinHandle(session)
        }
    }

    override suspend fun read(handle: ReelsRoundRobinHandle): ReelsRoundRobinSnapshot {
        return handler.await(inTransaction = true) { db ->
            ReelsRoundRobinStorage.requireOwner(db, handle.session)
            load(db, handle.session.key)?.snapshot ?: emptySnapshot(handle.session.key, unseenOnly = false)
        }
    }

    override suspend fun mutate(
        handle: ReelsRoundRobinHandle,
        expectedRevision: Long,
        change: ReelsRoundRobinMutation,
    ): ReelsRoundRobinCommit {
        return handler.await(inTransaction = true) { db ->
            ReelsRoundRobinStorage.requireOwner(db, handle.session)
            val loaded = load(db, handle.session.key) ?: throw ReelsSessionStaleException()

            if (change is ReelsRoundRobinMutation.Pump) {
                val pages = change.updates.filterIsInstance<ReelsRoundRobinUpdate.Page>()
                require(pages.map { it.sourceKey }.distinct().size == pages.size) {
                    "Duplicate source responses inside one round-robin pump"
                }
                // Page-only pumps are the single rebase exception: a page response may arrive
                // out of order, and it is accepted only while its fromCheckpoint still matches.
                // Any other update (Deferred/Unavailable/ResetCursor/Revalidated) mutates state
                // and is revision-fenced exactly like every non-pump mutation.
                val pageOnly = pages.isNotEmpty() && pages.size == change.updates.size
                if (!pageOnly && loaded.snapshot.revision != expectedRevision) {
                    throw ReelsSessionStaleException()
                }
                val byKey = loaded.snapshot.sources.associateBy { it.key }
                pages.forEach { page ->
                    val source = byKey[page.sourceKey]
                        ?: throw IllegalArgumentException("Unknown round-robin source ${page.sourceKey}")
                    if (source.checkpoint != page.fromCheckpoint) throw ReelsSessionStaleException()
                }
            } else if (loaded.snapshot.revision != expectedRevision) {
                throw ReelsSessionStaleException()
            }

            when (change) {
                is ReelsRoundRobinMutation.Catalog -> catalog(db, handle, loaded, change)
                is ReelsRoundRobinMutation.Import -> import(db, handle, loaded, change)
                is ReelsRoundRobinMutation.Pump -> pump(db, handle, loaded, change)
                is ReelsRoundRobinMutation.Activate -> activate(db, handle, loaded, change)
                is ReelsRoundRobinMutation.Recheck -> recheck(db, handle, loaded, change)
                is ReelsRoundRobinMutation.Repair -> repair(db, handle, loaded, change)
            }
        }
    }

    override fun observeEligibility(scope: String, sourceId: Long): Flow<Unit> {
        // Cold SQLDelight query over consumption + hidden only. Deliberately no StateFlow and no
        // distinctUntilChanged: an UPDATE that keeps the same row count must still recheck.
        return handler.subscribeToOne { db ->
            db.reels_rr_stateQueries.eligibilityStamp(scope, sourceId)
        }.map { }
    }

    override suspend fun release(handle: ReelsRoundRobinHandle) {
        handler.await(inTransaction = true) { db ->
            val session = handle.session
            val current = db.reels_sessionQueries.getSession(
                session.key.accountScopeId,
                session.key.sourceId,
            ).executeAsOneOrNull() ?: return@await
            if (current.session_id != session.sessionId ||
                current.owner_nonce != session.ownerNonce ||
                current.history_generation != session.historyGeneration ||
                current.context_key != session.key.contextKey
            ) {
                return@await
            }
            // Owner-fenced invalidate, never a scope wipe: the durable RR projection survives.
            db.reels_sessionQueries.revokeOwner(
                ownerNonce = session.ownerNonce,
                scope = session.key.accountScopeId,
                sourceId = session.key.sourceId,
            )
        }
    }

    override suspend fun clearScope(scope: String) {
        handler.await(inTransaction = true) { db ->
            ReelsRoundRobinStorage.clearScope(db, scope)
        }
    }

    /**
     * Drops only the RR projection of one source. The session header, its clock/checkpoint and
     * the candidate inbox are untouched, so the next open rebuilds frames from the catalog and
     * a fresh scan without losing the account history or the resume position.
     */
    override suspend fun resetProjection(scope: String, sourceId: Long) {
        handler.await(inTransaction = true) { db ->
            ReelsRoundRobinStorage.resetProjection(db, scope, sourceId)
        }
    }

    private fun recontext(
        db: AnimeDatabase,
        row: Reels_rr_state,
        key: ReelsSessionKey,
        unseenOnly: Boolean,
    ) {
        if (row.state_version != STATE_VERSION) throw corruptState("Unsupported state version ${row.state_version}")
        val sources = loadSources(db, key.accountScopeId, key.sourceId)
        val frames = parseFrames(row.frames_json)
        val anchor = parseAnchor(row)
        val participants = sources
            .filter { it.present && !it.excluded }
            .sortedWith(compareBy({ it.order }, { it.key }))
            .map { it.key }
        val oldRound = anchor?.round ?: frames.minOfOrNull { it.number } ?: 0L
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
        val turns = emptyMap<String, ReelsRoundRobinTurn>()
        val newFrames = buildList {
            if (rotated.isNotEmpty()) {
                add(ReelsRoundRobinFrame(newRound, rotated, turns))
                if (row.bootstrap_complete != 0L) {
                    add(ReelsRoundRobinFrame(newRound + 1L, rotated, turns))
                }
            }
        }
        val newSources = sources.map { source ->
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

        db.reels_rr_itemQueries.invalidateAll(key.accountScopeId, key.sourceId)
        newSources.forEach { writeSource(db, key, it) }
        val updated = ReelsRoundRobinSnapshot(
            key = key,
            revision = row.revision + 1L,
            unseenOnly = unseenOnly,
            bootstrapComplete = row.bootstrap_complete != 0L,
            catalogRevision = row.catalog_revision,
            sources = newSources,
            frames = newFrames,
            anchor = null,
            entries = emptyList(),
            unmappedItemIds = emptyList(),
        )
        db.reels_rr_stateQueries.updateCas(
            context_key = key.contextKey,
            state_version = STATE_VERSION,
            revision = updated.revision,
            unseen_only = unseenOnly.toLong(),
            bootstrap_complete = updated.bootstrapComplete.toLong(),
            catalog_revision = updated.catalogRevision,
            frames_json = framesJson(newFrames),
            anchor_round = null,
            anchor_source_key = null,
            anchor_item_id = null,
            updated_at_ms = System.currentTimeMillis(),
            scope = key.accountScopeId,
            sourceId = key.sourceId,
            expectedRevision = row.revision,
        )
        if (db.reels_rr_stateQueries.changes().executeAsOne() != 1L) {
            throw ReelsSessionStaleException()
        }
    }

    private fun catalog(
        db: AnimeDatabase,
        handle: ReelsRoundRobinHandle,
        loaded: Loaded,
        change: ReelsRoundRobinMutation.Catalog,
    ): ReelsRoundRobinCommit {
        validateCatalog(change.value)
        val reconciled = ReelsRoundRobinScheduler.reconcile(loaded.snapshot, change.value)
        if (reconciled == loaded.snapshot) {
            return ReelsRoundRobinCommit(loaded.snapshot, emptyList(), emptyList(), ReelsRoundRobinStop.EMPTY)
        }
        return persist(
            db = db,
            handle = handle,
            loaded = loaded,
            target = reconciled,
            candidates = emptyList(),
            nowMs = change.nowMs,
            page = emptyList(),
            needs = emptyList(),
            stop = ReelsRoundRobinStop.EMPTY,
        )
    }

    private fun import(
        db: AnimeDatabase,
        handle: ReelsRoundRobinHandle,
        loaded: Loaded,
        change: ReelsRoundRobinMutation.Import,
    ): ReelsRoundRobinCommit {
        if (change.identities.isEmpty()) {
            return ReelsRoundRobinCommit(loaded.snapshot, emptyList(), emptyList(), ReelsRoundRobinStop.EMPTY)
        }
        val key = handle.session.key
        val sources = loaded.snapshot.sources.associateBy { it.key }
        val entries = loaded.snapshot.entries.toMutableList()
        val candidates = candidatePayloads(db, key, change.identities.keys)

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
                val candidate = candidates[itemId] ?: return@forEach
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

        // The legacy active card becomes the anchor exactly once, before any real card turn
        // exists. A skip-only frame (a source was removed/hidden before the import) does not mean
        // the feed has started: only a turn carrying an itemId claims that. A card whose identity
        // is unknown or whose chat is absent/excluded stays only as a payload; it is never
        // guessed into a slot. An entry that was already associated by a previous import (or a
        // recontext) is not re-anchored.
        val headItemId = loaded.row.legacy_head_id
        val anchor = loaded.snapshot.anchor ?: run {
            if (headItemId == null) return@run null
            if (loaded.snapshot.entries.any { it.candidate.itemId == headItemId }) return@run null
            if (loaded.snapshot.frames.any { frame -> frame.turns.values.any { it.itemId != null } }) {
                return@run null
            }
            val candidate = candidates[headItemId] ?: return@run null
            val identity = change.identities[headItemId] ?: return@run null
            val source = sources[identity.sourceKey] ?: return@run null
            if (!source.present || source.excluded) return@run null
            val round = loaded.snapshot.frames
                .firstOrNull { frame -> identity.sourceKey in frame.members }
                ?.number
                ?: 0L
            ReelsRoundRobinAnchor(round = round, sourceKey = identity.sourceKey, itemId = candidate.itemId)
        }

        val target = loaded.snapshot.copy(entries = entries.toList(), anchor = anchor)
        if (target == loaded.snapshot) {
            return ReelsRoundRobinCommit(loaded.snapshot, emptyList(), emptyList(), ReelsRoundRobinStop.EMPTY)
        }
        return persist(
            db = db,
            handle = handle,
            loaded = loaded,
            target = target,
            candidates = emptyList(),
            nowMs = change.nowMs,
            page = emptyList(),
            needs = emptyList(),
            stop = ReelsRoundRobinStop.EMPTY,
        )
    }

    private fun pump(
        db: AnimeDatabase,
        handle: ReelsRoundRobinHandle,
        loaded: Loaded,
        change: ReelsRoundRobinMutation.Pump,
    ): ReelsRoundRobinCommit {
        val key = handle.session.key
        val unseenOnly = loaded.snapshot.unseenOnly
        val currentContext = key.contextKey
        val sources = loaded.snapshot.sources.associateByTo(LinkedHashMap()) { it.key }
        var entries = loaded.snapshot.entries
        val accepted = LinkedHashMap<String, ReelsCandidate>()
        val invalid = LinkedHashSet<String>()

        val pageUpdates = change.updates.filterIsInstance<ReelsRoundRobinUpdate.Page>()
        val itemIds = (
            pageUpdates.flatMap { page -> page.entries.map { it.candidate.itemId } } +
                entries.map { it.candidate.itemId }
            )
            .distinct()
        val consumed = consumedIds(db, key, itemIds)
        val hidden = db.reels_hiddenQueries.getBySource(key.sourceId).executeAsList()
        val hiddenItems = hidden.filter { it.kind == "video" || it.kind == "item" }
            .mapTo(HashSet()) { it.hidden_value }
        val hiddenAuthors = hidden.filter { it.kind == "author" }
            .mapTo(HashSet()) { it.hidden_value }

        fun locallyEligible(candidate: ReelsCandidate): Boolean {
            if (unseenOnly && candidate.itemId in consumed) return false
            if (candidate.itemId in hiddenItems) return false
            if ((candidate.author ?: "") in hiddenAuthors) return false
            return true
        }

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
                    val normalized = update.entries
                        .map { entry ->
                            if (locallyEligible(entry.candidate)) {
                                entry.copy(
                                    checkedContext = if (entry.validation == ReelsRoundRobinValidation.ELIGIBLE) {
                                        currentContext
                                    } else {
                                        entry.checkedContext
                                    },
                                    round = null,
                                    ordinal = null,
                                )
                            } else {
                                // The payload was seen in this context; only the local verdict is
                                // FILTERED, so a later recheck can restore it without a resolve.
                                entry.copy(
                                    checkedContext = currentContext,
                                    validation = ReelsRoundRobinValidation.FILTERED,
                                    round = null,
                                    ordinal = null,
                                )
                            }
                        }
                    normalized.forEach { entry ->
                        if (entry.validation == ReelsRoundRobinValidation.FILTERED &&
                            !locallyEligible(entry.candidate)
                        ) {
                            invalid += entry.candidate.itemId
                            val index = entries.indexOfFirst { it.candidate.itemId == entry.candidate.itemId }
                            if (index >= 0) {
                                entries = entries.toMutableList().also {
                                    it[index] = it[index].copy(
                                        validation = ReelsRoundRobinValidation.FILTERED,
                                        checkedContext = currentContext,
                                        round = null,
                                        ordinal = null,
                                    )
                                }
                            }
                        } else {
                            accepted[entry.candidate.itemId] = entry.candidate
                            if (locallyEligible(entry.candidate)) pageAccepted = true
                            val index = entries.indexOfFirst { it.candidate.itemId == entry.candidate.itemId }
                            if (index >= 0) {
                                val existing = entries[index]
                                entries = entries.toMutableList().also {
                                    it[index] = entry.copy(round = existing.round, ordinal = existing.ordinal)
                                }
                            } else {
                                entries = entries + entry
                            }
                        }
                    }
                    // A page that returned cards but staged none is a budgeted filter result,
                    // not an invitation to scan the same source again in this round.
                    val fruitless = update.fruitless ||
                        (update.entries.isNotEmpty() && !pageAccepted)
                    sources[update.sourceKey] = source.copy(
                        checkpoint = update.nextCheckpoint,
                        checkpointContext = currentContext,
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
                            fruitlessScan = false,
                        )
                    }
                }

                is ReelsRoundRobinUpdate.Revalidated -> {
                    val revalidatedCandidate = update.candidate
                        ?.takeIf { it.itemId == update.itemId }
                    if (update.candidate != null && revalidatedCandidate == null) {
                        // A candidate for a different item is not a validation of this slot:
                        // reject it and release the turn instead of attaching a foreign payload.
                        invalid += update.itemId
                        return@forEach
                    }
                    val row = loaded.itemRows.firstOrNull { it.item_id == update.itemId }
                    val sourceKey = row?.source_key
                        ?: entries.firstOrNull { it.candidate.itemId == update.itemId }?.sourceKey
                    if (sourceKey != null && update.retryAt != null) {
                        sources[sourceKey]?.let { source ->
                            sources[sourceKey] = source.copy(
                                retryAt = update.retryAt,
                                retryReason = "deferred",
                            )
                        }
                    }
                    revalidatedCandidate?.let { accepted[it.itemId] = it }
                    val existingIndex = entries.indexOfFirst { it.candidate.itemId == update.itemId }
                    if (existingIndex >= 0) {
                        val existing = entries[existingIndex]
                        val candidate = revalidatedCandidate ?: existing.candidate
                        val eligible = update.validation == ReelsRoundRobinValidation.ELIGIBLE &&
                            locallyEligible(candidate)
                        val validation = if (eligible) {
                            ReelsRoundRobinValidation.ELIGIBLE
                        } else if (update.validation == ReelsRoundRobinValidation.ELIGIBLE) {
                            ReelsRoundRobinValidation.FILTERED
                        } else {
                            update.validation
                        }
                        // A source-side FilteredOut has no candidate; it is not a local filter
                        // that an eligibility recheck may undo.
                        val contentFiltered = update.validation == ReelsRoundRobinValidation.FILTERED &&
                            revalidatedCandidate == null
                        entries = entries.toMutableList().also {
                            it[existingIndex] = existing.copy(
                                candidate = candidate,
                                checkedContext = if (contentFiltered) null else currentContext,
                                validation = validation,
                                round = null,
                                ordinal = null,
                            )
                        }
                        if (!eligible) invalid += update.itemId
                    } else if (row != null && revalidatedCandidate != null) {
                        val eligible = update.validation == ReelsRoundRobinValidation.ELIGIBLE &&
                            locallyEligible(revalidatedCandidate)
                        entries = entries + ReelsRoundRobinEntry(
                            candidate = revalidatedCandidate,
                            sourceKey = row.source_key,
                            orderKey = row.order_key,
                            contentKey = row.content_key,
                            checkedContext = currentContext,
                            validation = if (eligible) {
                                ReelsRoundRobinValidation.ELIGIBLE
                            } else {
                                update.validation
                            },
                            round = null,
                            ordinal = null,
                        )
                        if (!eligible) invalid += update.itemId
                    } else {
                        // No association row and no entry to update. Even an ELIGIBLE verdict
                        // cannot recreate the slot without its identity/order key, so the turn
                        // is released for a scan/import instead of being silently ignored.
                        invalid += update.itemId
                    }
                }
            }
        }

        entries.asSequence()
            .filter { it.round != null && it.validation != ReelsRoundRobinValidation.ELIGIBLE }
            .mapTo(invalid) { it.candidate.itemId }

        var state = loaded.snapshot.copy(
            sources = sources.values.sortedWith(compareBy({ it.order }, { it.key })),
            entries = entries.distinctBy { it.candidate.itemId },
        )
        state = ReelsRoundRobinScheduler.repair(state, invalid)

        val locallyUnusable = state.entries
            .filter { (unseenOnly && it.candidate.itemId in consumed) || !locallyEligible(it.candidate) }
            .mapTo(HashSet()) { it.candidate.itemId }
        if (locallyUnusable.isNotEmpty()) {
            state = state.copy(
                entries = state.entries.map { entry ->
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

        val sourceByKey = state.sources.associateBy { it.key }
        val eligibleIds = state.entries
            .asSequence()
            .filter {
                it.validation == ReelsRoundRobinValidation.ELIGIBLE &&
                    it.checkedContext == currentContext &&
                    sourceByKey[it.sourceKey]?.let { source -> source.present && !source.excluded } == true &&
                    locallyEligible(it.candidate)
            }
            .mapTo(HashSet()) { it.candidate.itemId }

        val commit = ReelsRoundRobinScheduler.select(
            state = state,
            eligibleIds = eligibleIds,
            nowMs = change.nowMs,
            limit = change.limit,
        )
        if (commit.snapshot == loaded.snapshot && accepted.isEmpty()) {
            return commit
        }
        return persist(
            db = db,
            handle = handle,
            loaded = loaded,
            target = commit.snapshot,
            candidates = accepted.values.toList(),
            nowMs = change.nowMs,
            page = commit.page,
            needs = commit.needs,
            stop = commit.stop,
        )
    }

    private fun activate(
        db: AnimeDatabase,
        handle: ReelsRoundRobinHandle,
        loaded: Loaded,
        change: ReelsRoundRobinMutation.Activate,
    ): ReelsRoundRobinCommit {
        val activated = ReelsRoundRobinScheduler.activate(loaded.snapshot, change.itemId)
        if (activated == loaded.snapshot) {
            return ReelsRoundRobinCommit(loaded.snapshot, emptyList(), emptyList(), ReelsRoundRobinStop.PAUSED)
        }
        return persist(
            db = db,
            handle = handle,
            loaded = loaded,
            target = activated,
            candidates = emptyList(),
            nowMs = change.nowMs,
            page = emptyList(),
            needs = emptyList(),
            stop = ReelsRoundRobinStop.PAUSED,
        )
    }

    private fun recheck(
        db: AnimeDatabase,
        handle: ReelsRoundRobinHandle,
        loaded: Loaded,
        change: ReelsRoundRobinMutation.Recheck,
    ): ReelsRoundRobinCommit {
        if (change.rewindSources.isEmpty()) {
            return ReelsRoundRobinCommit(loaded.snapshot, emptyList(), emptyList(), ReelsRoundRobinStop.PAUSED)
        }
        val key = handle.session.key
        val context = key.contextKey
        val unseenOnly = loaded.snapshot.unseenOnly
        val consumed = consumedIds(db, key, loaded.snapshot.entries.map { it.candidate.itemId })
        val hidden = db.reels_hiddenQueries.getBySource(key.sourceId).executeAsList()
        val hiddenItems = hidden.filter { it.kind == "video" || it.kind == "item" }
            .mapTo(HashSet()) { it.hidden_value }
        val hiddenAuthors = hidden.filter { it.kind == "author" }
            .mapTo(HashSet()) { it.hidden_value }
        val played = ReelsRoundRobinCleanup.playedItemIds(
            frames = loaded.snapshot.frames,
            anchor = loaded.snapshot.anchor,
        )
        val activeRound = loaded.snapshot.anchor?.round
            ?: loaded.snapshot.frames.minOfOrNull { it.number }
        val released = LinkedHashSet<String>()

        val sources = loaded.snapshot.sources.map { source ->
            if (source.key !in change.rewindSources) return@map source
            // A rewind re-decides the source now, so a previous fruitless scan no longer
            // applies: the next selection may request the scan immediately.
            source.copy(retryAt = null, retryReason = null, fruitlessScan = false)
        }
        val entries = loaded.snapshot.entries.map { entry ->
            if (entry.sourceKey !in change.rewindSources) return@map entry
            val itemId = entry.candidate.itemId
            val locallyEligible = itemId !in hiddenItems &&
                (entry.candidate.author ?: "") !in hiddenAuthors &&
                !(unseenOnly && itemId in consumed)
            when {
                !locallyEligible -> {
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
        val state = loaded.snapshot.copy(
            sources = sources.sortedWith(compareBy({ it.order }, { it.key })),
            frames = ReelsRoundRobinCleanup.removeFutureTurns(
                frames = ReelsRoundRobinCleanup.clearFutureSkips(
                    frames = loaded.snapshot.frames,
                    anchor = loaded.snapshot.anchor,
                    sources = change.rewindSources,
                ),
                anchor = loaded.snapshot.anchor,
                itemIds = released,
            ),
            entries = entries,
        )
        if (state == loaded.snapshot) {
            return ReelsRoundRobinCommit(loaded.snapshot, emptyList(), emptyList(), ReelsRoundRobinStop.PAUSED)
        }
        return persist(
            db = db,
            handle = handle,
            loaded = loaded,
            target = state,
            candidates = emptyList(),
            nowMs = change.nowMs,
            page = emptyList(),
            needs = emptyList(),
            stop = ReelsRoundRobinStop.PAUSED,
        )
    }

    /**
     * Releases future turns that can never be fulfilled because their payload row is gone. The
     * scheduler keeps the played prefix/anchor untouched, so this is the durable answer to an
     * entry-less `Need.Revalidate`; the next selection asks for a scan instead of repeating the
     * dead need forever.
     */
    private fun repair(
        db: AnimeDatabase,
        handle: ReelsRoundRobinHandle,
        loaded: Loaded,
        change: ReelsRoundRobinMutation.Repair,
    ): ReelsRoundRobinCommit {
        val repaired = ReelsRoundRobinScheduler.repair(loaded.snapshot, change.invalidItemIds)
        if (repaired == loaded.snapshot) {
            return ReelsRoundRobinCommit(loaded.snapshot, emptyList(), emptyList(), ReelsRoundRobinStop.EMPTY)
        }
        return persist(
            db = db,
            handle = handle,
            loaded = loaded,
            target = repaired,
            candidates = emptyList(),
            nowMs = change.nowMs,
            page = emptyList(),
            needs = emptyList(),
            stop = ReelsRoundRobinStop.EMPTY,
        )
    }

    /**
     * A rewound source must be re-decided, not only re-validated: an earlier pump may have
     * written an explicit future skip (EMPTY/FILTER_BUDGET/HIDDEN) for its slot, and such a
     * skip carries no item id, so [ReelsRoundRobinScheduler.repair] cannot release it. The
     * shared [ReelsRoundRobinCleanup] drops those turns; played slots (at or before the anchor)
     * are never touched.
     */
    private fun persist(
        db: AnimeDatabase,
        handle: ReelsRoundRobinHandle,
        loaded: Loaded,
        target: ReelsRoundRobinSnapshot,
        candidates: List<ReelsCandidate>,
        nowMs: Long,
        page: List<ReelsCandidate>,
        needs: List<tachiyomi.domain.reels.anime.model.ReelsRoundRobinNeed>,
        stop: ReelsRoundRobinStop,
    ): ReelsRoundRobinCommit {
        val normalized = normalizeReservations(materializeAnchorTurn(target))
        val revision = loaded.row.revision + 1L
        var snapshot = normalized.copy(revision = revision)

        // Revision CAS first; every later write stays in the same transaction, so a failure or a
        // lost CAS rolls the whole pump back (including the advanced checkpoint).
        writeState(db, snapshot, loaded.row.revision, nowMs)

        if (candidates.isNotEmpty()) {
            // Capacity/prune runs inside the transaction and throws before anything can commit.
            ReelsCandidateWriter.writeCandidates(
                db = db,
                scope = snapshot.key.accountScopeId,
                sourceId = snapshot.key.sourceId,
                candidates = candidates,
            )
        }

        // The writer may have pruned a consumed payload whose reservation was released earlier
        // (played past, owner released). Reconcile with the rows that still exist so a deleted
        // payload is never re-associated, kept in a frame or returned as a shown card.
        val reconciled = reconcilePayloads(db, snapshot, page)
        if (reconciled.snapshot != snapshot) {
            snapshot = reconciled.snapshot
            writeState(db, snapshot, revision, nowMs)
        }
        // FK OFF does not cascade the association delete; keep both pragma modes orphan-free.
        db.reels_rr_itemQueries.deleteOrphans(snapshot.key.accountScopeId, snapshot.key.sourceId)
        writeSources(db, snapshot.key, loaded.snapshot.sources, snapshot.sources)
        snapshot.entries.forEach { entry ->
            db.reels_rr_itemQueries.upsert(
                account_scope_id = snapshot.key.accountScopeId,
                source_id = snapshot.key.sourceId,
                item_id = entry.candidate.itemId,
                source_key = entry.sourceKey,
                order_key = entry.orderKey,
                content_key = entry.contentKey,
                checked_context = entry.checkedContext,
                validation = entry.validation.name,
                reserved_round = entry.round,
                ordinal = entry.ordinal,
            )
        }
        rebuildQueue(db, handle.session, snapshot)
        return ReelsRoundRobinCommit(snapshot, reconciled.page, needs, stop)
    }

    private fun writeState(
        db: AnimeDatabase,
        snapshot: ReelsRoundRobinSnapshot,
        expectedRevision: Long,
        nowMs: Long,
    ) {
        db.reels_rr_stateQueries.updateCas(
            context_key = snapshot.key.contextKey,
            state_version = STATE_VERSION,
            revision = snapshot.revision,
            unseen_only = snapshot.unseenOnly.toLong(),
            bootstrap_complete = snapshot.bootstrapComplete.toLong(),
            catalog_revision = snapshot.catalogRevision,
            frames_json = framesJson(snapshot.frames),
            anchor_round = snapshot.anchor?.round,
            anchor_source_key = snapshot.anchor?.sourceKey,
            anchor_item_id = snapshot.anchor?.itemId,
            updated_at_ms = nowMs,
            scope = snapshot.key.accountScopeId,
            sourceId = snapshot.key.sourceId,
            expectedRevision = expectedRevision,
        )
        if (db.reels_rr_stateQueries.changes().executeAsOne() != 1L) {
            throw ReelsSessionStaleException()
        }
    }

    /**
     * Cleans projection references that cannot be persisted while keeping a missing payload a
     * durable need.
     *
     * The selected page must never return a card whose `reels_candidate` row disappeared, and an
     * entry cannot be written without its FK-backed payload, so both are dropped here. The frame
     * turn, the anchor and the `unmappedItemIds` entry are the need ledger instead: dropping them
     * on the next persisting pump would silently forget a future card, so they survive and the
     * scheduler keeps returning `Need.Revalidate`. The stale `reels_rr_item` row is already gone
     * with FK ON (cascade) and is removed by `reels_rr_itemQueries.deleteOrphans` with FK OFF.
     */
    private fun reconcilePayloads(
        db: AnimeDatabase,
        snapshot: ReelsRoundRobinSnapshot,
        page: List<ReelsCandidate>,
    ): PayloadReconciliation {
        val key = snapshot.key
        val ids = (
            snapshot.entries.map { it.candidate.itemId } +
                snapshot.frames.flatMap { frame -> frame.turns.values.mapNotNull { it.itemId } } +
                page.map { it.itemId }
            )
            .distinct()
        val existing = if (ids.isEmpty()) {
            emptySet()
        } else {
            ids.chunked(SQL_BATCH_SIZE)
                .flatMap { chunk ->
                    db.reels_candidateQueries.getByItems(key.accountScopeId, key.sourceId, chunk)
                        .executeAsList()
                }
                .mapTo(HashSet()) { it.item_id }
        }
        val missing = ids.filterTo(HashSet()) { it !in existing }
        if (missing.isEmpty()) return PayloadReconciliation(snapshot, page)

        return PayloadReconciliation(
            snapshot = snapshot.copy(
                entries = snapshot.entries.filter { it.candidate.itemId !in missing },
            ),
            page = page.filter { it.itemId !in missing },
        )
    }

    private fun writeSources(
        db: AnimeDatabase,
        key: ReelsSessionKey,
        previous: List<ReelsRoundRobinSourceState>,
        target: List<ReelsRoundRobinSourceState>,
    ) {
        require(target.map { it.key }.distinct().size == target.size) {
            "Duplicate round-robin source key"
        }
        require(target.map { it.order }.distinct().size == target.size) {
            "Duplicate round-robin source order"
        }
        val targetKeys = target.mapTo(LinkedHashSet()) { it.key }
        val aliases = LinkedHashMap<String, String>()
        val existingKeys = previous.mapTo(LinkedHashSet()) { it.key }
        target.forEach { source ->
            source.previousKeys.forEach { previousKey ->
                if (previousKey == source.key || previousKey in targetKeys) return@forEach
                if (previousKey !in existingKeys) return@forEach
                val existing = aliases.put(previousKey, source.key)
                require(existing == null || existing == source.key) {
                    "Conflicting round-robin source alias $previousKey"
                }
            }
        }

        val existingRows = db.reels_rr_sourceQueries.all(key.accountScopeId, key.sourceId)
            .executeAsList()
            .associateBy { it.source_key }
        val needsFreeze = target.any { source ->
            existingRows[source.key]?.source_order != source.order
        }
        if (needsFreeze) {
            db.reels_rr_sourceQueries.freezeOrders(key.accountScopeId, key.sourceId)
        }
        target.forEach { writeSource(db, key, it) }

        val affected = targetKeys + aliases.keys
        if (affected.isNotEmpty()) {
            db.reels_rr_itemQueries.unreserveForSources(
                key.accountScopeId,
                key.sourceId,
                affected.toList(),
            )
        }
        aliases.forEach { (oldKey, canonicalKey) ->
            db.reels_rr_itemQueries.reassignSourceKey(
                toKey = canonicalKey,
                scope = key.accountScopeId,
                sourceId = key.sourceId,
                fromKey = oldKey,
            )
        }
        previous.map { it.key }.filter { it !in targetKeys }.forEach { oldKey ->
            if (oldKey !in aliases) {
                db.reels_rr_itemQueries.deleteBySourceKey(key.accountScopeId, key.sourceId, oldKey)
            }
            db.reels_rr_sourceQueries.deleteForKey(key.accountScopeId, key.sourceId, oldKey)
        }
    }

    private fun writeSource(db: AnimeDatabase, key: ReelsSessionKey, source: ReelsRoundRobinSourceState) {
        db.reels_rr_sourceQueries.upsert(
            account_scope_id = key.accountScopeId,
            source_id = key.sourceId,
            source_key = source.key,
            source_order = source.order,
            present = source.present.toLong(),
            state_json = json.encodeToString(ReelsRoundRobinSourceState.serializer(), source),
        )
    }

    private fun rebuildQueue(
        db: AnimeDatabase,
        session: ReelsSessionHandle,
        snapshot: ReelsRoundRobinSnapshot?,
    ) {
        val scope = session.key.accountScopeId
        val sourceId = session.key.sourceId
        db.reels_session_queueQueries.deleteForSession(scope, sourceId, session.sessionId)
        if (snapshot == null) return
        var ordinal = 0L
        forwardItemIds(snapshot).forEach { itemId ->
            ordinal++
            db.reels_session_queueQueries.append(
                account_scope_id = scope,
                source_id = sourceId,
                session_id = session.sessionId,
                item_id = itemId,
                ordinal = ordinal,
            )
        }
    }

    private fun forwardItemIds(snapshot: ReelsRoundRobinSnapshot): List<String> {
        val frames = snapshot.frames.sortedBy { it.number }
        if (frames.isEmpty()) return emptyList()
        val anchor = snapshot.anchor
        val startFrame = if (anchor != null) {
            frames.indexOfFirst { it.number == anchor.round }.coerceAtLeast(0)
        } else {
            0
        }
        val itemIds = mutableListOf<String>()
        for (index in startFrame until frames.size) {
            val frame = frames[index]
            var slot = 0
            if (index == startFrame && anchor != null && frame.number == anchor.round) {
                val anchorSlot = frame.members.indexOf(anchor.sourceKey).let {
                    if (it >= 0) {
                        it
                    } else {
                        frame.members.indexOfFirst { member ->
                            frame.turns[member]?.itemId == anchor.itemId
                        }
                    }
                }
                slot = if (anchorSlot >= 0) anchorSlot + 1 else 0
            }
            for (memberIndex in slot until frame.members.size) {
                frame.turns[frame.members[memberIndex]]?.itemId?.let(itemIds::add)
            }
        }
        return itemIds.distinct()
    }

    private fun loadLight(db: AnimeDatabase, key: ReelsSessionKey): Loaded? {
        return load(db, key, withEntries = false)
    }

    private fun load(
        db: AnimeDatabase,
        key: ReelsSessionKey,
        withEntries: Boolean = true,
    ): Loaded? {
        val row = db.reels_rr_stateQueries.get(key.accountScopeId, key.sourceId)
            .executeAsOneOrNull() ?: return null
        if (row.state_version != STATE_VERSION) {
            throw corruptState("Unsupported RR state version ${row.state_version}")
        }
        val sources = loadSources(db, key.accountScopeId, key.sourceId)
        val frames = parseFrames(row.frames_json)
        val anchor = parseAnchor(row)
        if (!withEntries) {
            val snapshot = ReelsRoundRobinSnapshot(
                key = key,
                revision = row.revision,
                unseenOnly = row.unseen_only != 0L,
                bootstrapComplete = row.bootstrap_complete != 0L,
                catalogRevision = row.catalog_revision,
                sources = sources,
                frames = frames,
                anchor = anchor,
                entries = emptyList(),
                unmappedItemIds = emptyList(),
            )
            return Loaded(row, snapshot, emptyList())
        }

        val itemRows = db.reels_rr_itemQueries.all(key.accountScopeId, key.sourceId).executeAsList()
        val candidates = candidatePayloads(db, key, itemRows.map { it.item_id })
        val entries = itemRows.mapNotNull { item ->
            val candidate = candidates[item.item_id] ?: return@mapNotNull null
            ReelsRoundRobinEntry(
                candidate = candidate,
                sourceKey = item.source_key,
                orderKey = item.order_key,
                contentKey = item.content_key,
                checkedContext = item.checked_context,
                validation = parseValidation(item.validation),
                round = item.reserved_round,
                ordinal = item.ordinal,
            )
        }
        val futureIds = futureItemIds(frames, anchor)
        val unmapped = futureIds.filter { it !in candidates }.distinct()
        val snapshot = ReelsRoundRobinSnapshot(
            key = key,
            revision = row.revision,
            unseenOnly = row.unseen_only != 0L,
            bootstrapComplete = row.bootstrap_complete != 0L,
            catalogRevision = row.catalog_revision,
            sources = sources,
            frames = frames,
            anchor = anchor,
            entries = entries,
            unmappedItemIds = unmapped,
        )
        return Loaded(row, snapshot, itemRows)
    }

    private fun loadSources(
        db: AnimeDatabase,
        scope: String,
        sourceId: Long,
    ): List<ReelsRoundRobinSourceState> {
        val rows = db.reels_rr_sourceQueries.all(scope, sourceId).executeAsList()
        val keys = HashSet<String>()
        val orders = HashSet<Long>()
        return rows.map { row ->
            val state = try {
                json.decodeFromString(ReelsRoundRobinSourceState.serializer(), row.state_json)
            } catch (e: IllegalArgumentException) {
                throw corruptState("Corrupt RR source state for ${row.source_key}", e)
            }
            if (state.key != row.source_key ||
                state.order != row.source_order ||
                state.present != (row.present != 0L) ||
                !keys.add(state.key) ||
                !orders.add(state.order)
            ) {
                throw corruptState("RR source state does not match its indexed row ${row.source_key}")
            }
            state
        }
    }

    private fun parseFrames(value: String): List<ReelsRoundRobinFrame> {
        val frames = try {
            json.decodeFromString(FRAMES_SERIALIZER, value)
        } catch (e: IllegalArgumentException) {
            throw corruptState("Corrupt RR frames JSON", e)
        }
        val numbers = HashSet<Long>()
        val itemIds = HashSet<String>()
        frames.forEach { frame ->
            if (!numbers.add(frame.number)) {
                throw corruptState("Duplicate RR frame number ${frame.number}")
            }
            if (frame.members.distinct().size != frame.members.size) {
                throw corruptState("Duplicate RR frame member")
            }
            frame.turns.forEach { (member, turn) ->
                if (member !in frame.members) {
                    throw corruptState("RR turn for unknown member $member")
                }
                if ((turn.itemId != null) == (turn.skip != null)) {
                    throw corruptState("RR turn must contain exactly one of itemId/skip")
                }
                turn.itemId?.let {
                    if (it.isEmpty() || !itemIds.add(it)) {
                        throw corruptState("Duplicate or empty RR item id $it")
                    }
                }
            }
        }
        return frames
    }

    private fun parseAnchor(row: Reels_rr_state): ReelsRoundRobinAnchor? {
        val round = row.anchor_round
        val sourceKey = row.anchor_source_key
        val itemId = row.anchor_item_id
        if (round == null && sourceKey == null && itemId == null) return null
        if (round == null || sourceKey == null || itemId == null) {
            throw corruptState("Partial RR anchor row")
        }
        return ReelsRoundRobinAnchor(round, sourceKey, itemId)
    }

    private fun parseValidation(value: String): ReelsRoundRobinValidation {
        return try {
            ReelsRoundRobinValidation.valueOf(value)
        } catch (e: IllegalArgumentException) {
            throw corruptState("Unknown RR validation $value", e)
        }
    }

    /**
     * A migrated legacy head is anchored by ID before any turn exists. Materialize its turn in
     * the anchor frame (when the catalog has created one) so it stays reserved, is skipped by
     * the frontier and cannot be selected a second time as a staged tail item.
     */
    private fun materializeAnchorTurn(snapshot: ReelsRoundRobinSnapshot): ReelsRoundRobinSnapshot {
        val anchor = snapshot.anchor ?: return snapshot
        val frameIndex = snapshot.frames.indexOfFirst { it.number == anchor.round }
        if (frameIndex < 0) return snapshot
        val frame = snapshot.frames[frameIndex]
        if (frame.turns.values.any { it.itemId == anchor.itemId }) return snapshot
        if (anchor.sourceKey !in frame.members) return snapshot
        // A skip (REMOVED/HIDDEN/RETRY) is not a card and does not own the anchor's slot, so the
        // migrated anchor replaces it. A different real card turn still wins the slot.
        val slotTurn = frame.turns[anchor.sourceKey]
        if (slotTurn?.itemId != null) return snapshot
        val source = snapshot.sources.firstOrNull { it.key == anchor.sourceKey }
        if (source != null && (!source.present || source.excluded)) return snapshot
        val turns = LinkedHashMap<String, ReelsRoundRobinTurn>()
        frame.members.forEach { member ->
            if (member == anchor.sourceKey) {
                turns[member] = ReelsRoundRobinTurn(itemId = anchor.itemId, skip = null)
            } else {
                frame.turns[member]?.let { turns[member] = it }
            }
        }
        return snapshot.copy(
            frames = snapshot.frames.toMutableList().also { it[frameIndex] = frame.copy(turns = turns) },
        )
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

    private fun futureItemIds(
        frames: List<ReelsRoundRobinFrame>,
        anchor: ReelsRoundRobinAnchor?,
    ): List<String> {
        val ids = mutableListOf<String>()
        frames.forEach { frame ->
            val limit = playedLimit(frame, anchor)
            frame.members.forEachIndexed { index, member ->
                if (index > limit) {
                    frame.turns[member]?.itemId?.let(ids::add)
                }
            }
        }
        return ids
    }

    private fun candidatePayloads(
        db: AnimeDatabase,
        key: ReelsSessionKey,
        itemIds: Collection<String>,
    ): Map<String, ReelsCandidate> {
        if (itemIds.isEmpty()) return emptyMap()
        return itemIds.distinct()
            .chunked(SQL_BATCH_SIZE)
            .flatMap { chunk ->
                db.reels_candidateQueries.getByItems(key.accountScopeId, key.sourceId, chunk)
                    .executeAsList()
            }
            .associateBy { it.item_id }
            .mapValues { (_, row) -> ReelsCandidateWriter.mapCandidate(row) }
    }

    private fun consumedIds(
        db: AnimeDatabase,
        key: ReelsSessionKey,
        itemIds: Collection<String>,
    ): Set<String> {
        if (itemIds.isEmpty()) return emptySet()
        return itemIds.distinct()
            .chunked(SQL_BATCH_SIZE)
            .flatMap { chunk ->
                db.reels_consumptionQueries.consumedIds(key.accountScopeId, key.sourceId, chunk)
                    .executeAsList()
            }
            .toSet()
    }

    private fun validateCatalog(catalog: ReelsRoundRobinCatalog) {
        val keys = catalog.sources.map { it.key }
        require(keys.distinct().size == keys.size) { "Duplicate round-robin catalog source key" }
        val canonicalKeys = keys.toHashSet()
        val claims = HashMap<String, String>()
        catalog.sources.forEach { source ->
            require(source.key.isNotBlank()) { "Blank round-robin catalog source key" }
            source.previousKeys.forEach { previous ->
                require(previous.isNotBlank() && previous != source.key) {
                    "Invalid round-robin previous key $previous"
                }
                require(previous !in canonicalKeys) {
                    "Conflicting round-robin previous key $previous"
                }
                val existing = claims.put(previous, source.key)
                require(existing == null || existing == source.key) {
                    "Conflicting round-robin previous key $previous"
                }
            }
        }
    }

    private fun framesJson(frames: List<ReelsRoundRobinFrame>): String {
        return json.encodeToString(FRAMES_SERIALIZER, frames)
    }

    private fun emptySnapshot(key: ReelsSessionKey, unseenOnly: Boolean): ReelsRoundRobinSnapshot {
        return ReelsRoundRobinSnapshot(
            key = key,
            revision = 0L,
            unseenOnly = unseenOnly,
            bootstrapComplete = false,
            catalogRevision = null,
            sources = emptyList(),
            frames = emptyList(),
            anchor = null,
            entries = emptyList(),
            unmappedItemIds = emptyList(),
        )
    }

    private fun corruptState(message: String, cause: Throwable? = null): ReelsRoundRobinStateCorruptException {
        return ReelsRoundRobinStateCorruptException(message, cause)
    }

    private fun Boolean.toLong(): Long = if (this) 1L else 0L

    private data class Loaded(
        val row: Reels_rr_state,
        val snapshot: ReelsRoundRobinSnapshot,
        val itemRows: List<Reels_rr_item>,
    )

    private data class PayloadReconciliation(
        val snapshot: ReelsRoundRobinSnapshot,
        val page: List<ReelsCandidate>,
    )

    private companion object {
        const val STATE_VERSION = 1L
        const val SQL_BATCH_SIZE = 500

        val json = Json { encodeDefaults = true }
        val FRAMES_SERIALIZER = ListSerializer(ReelsRoundRobinFrame.serializer())
    }
}
