package tachiyomi.domain.reels.anime.interactor

import tachiyomi.domain.reels.anime.model.ReelsCandidate
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinAnchor
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinCatalog
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinCatalogSource
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinCommit
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinEntry
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinFrame
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinNeed
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinSkip
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinSnapshot
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinSourceState
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinStop
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinTurn
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinValidation

private const val MAX_NEEDS_PER_SELECT = 4

/**
 * Pure host-side round-robin scheduler.
 *
 * The scheduler owns no IO and has no clock of its own: [select] receives `nowMs`, every
 * transition is an immutable copy, and the same snapshot/catalog pair always produces the
 * same result. At most one active frame and one prepared frame exist, so a pump can never
 * reserve an unbounded number of future rounds.
 */
object ReelsRoundRobinScheduler {

    /**
     * How long a source whose page was completely filtered waits before the next head check.
     * A fruitless scan must not be repeated in the same round; the scheduled head check is the
     * continuation that can discover newly published cards later.
     */
    const val FRUITLESS_RESCAN_MS = 10L * 60_000L

    fun reconcile(
        state: ReelsRoundRobinSnapshot,
        catalog: ReelsRoundRobinCatalog,
    ): ReelsRoundRobinSnapshot {
        val alias = aliasMap(catalog)
        val entries = normalizeEntries(state.entries, alias)
        val nextRound = nextRoundNumber(state.frames, state.anchor)
        val sources = mergeSources(state, catalog, alias, entries, nextRound)
        val anchor = state.anchor?.let { it.copy(sourceKey = alias.canonical(it.sourceKey)) }
        val frames = normalizeFrames(state.frames, alias)
        val oldParticipating = state.sources
            .asSequence()
            .filter { it.present && !it.excluded }
            .map { alias.canonical(it.key) }
            .toSet()
        val newParticipating = sources
            .asSequence()
            .filter { it.present && !it.excluded }
            .map { it.key }
            .toSet()
        val planned = planFrames(
            state = state,
            frames = frames,
            sources = sources,
            entries = entries,
            anchor = anchor,
            bootstrapComplete = state.bootstrapComplete || catalog.complete,
            membershipChanged = oldParticipating != newParticipating,
        )
        return state.copy(
            bootstrapComplete = state.bootstrapComplete || catalog.complete,
            catalogRevision = catalog.revision,
            sources = sources,
            frames = planned.frames,
            anchor = anchor,
            entries = planned.entries,
        )
    }

    fun select(
        state: ReelsRoundRobinSnapshot,
        eligibleIds: Set<String>,
        nowMs: Long,
        limit: Int,
    ): ReelsRoundRobinCommit {
        if (state.frames.isEmpty()) {
            return ReelsRoundRobinCommit(
                snapshot = state,
                page = emptyList(),
                needs = emptyList(),
                stop = ReelsRoundRobinStop.EMPTY,
            )
        }
        if (limit <= 0) {
            return ReelsRoundRobinCommit(
                snapshot = state,
                page = emptyList(),
                needs = emptyList(),
                stop = ReelsRoundRobinStop.NEED_INPUT,
            )
        }
        return Selection(state, eligibleIds, nowMs, limit).commit()
    }

    /**
     * Moves the frontier forward by one slot. The frontier is compared by (round, slot
     * position), never by item arrival; repeated and backward callbacks return the input
     * unchanged. Activating a prepared frame promotes it and drops the finished frame.
     */
    fun activate(
        state: ReelsRoundRobinSnapshot,
        itemId: String,
    ): ReelsRoundRobinSnapshot {
        if (state.frames.isEmpty()) return state
        val frames = sanitizeFrames(state.frames)
        val anchor = state.anchor
        val anchorFrameIndex = anchor?.let { current ->
            frames.indexOfFirst { it.number == current.round }
        } ?: -1
        val anchorSlot = if (anchor != null && anchorFrameIndex >= 0) {
            slotOf(frames[anchorFrameIndex], anchor)
        } else {
            -1
        }

        var targetFrameIndex = -1
        var targetSlot = -1
        for (index in frames.indices) {
            if (anchorFrameIndex >= 0 && index < anchorFrameIndex) continue
            val frame = frames[index]
            val slot = frame.members.indexOfFirst { member ->
                frame.turns[member]?.itemId == itemId
            }
            if (slot < 0) continue
            val forward = when {
                anchor == null -> true
                frame.number > anchor.round -> true
                frame.number < anchor.round -> false
                else -> slot > anchorSlot
            }
            if (forward) {
                targetFrameIndex = index
                targetSlot = slot
                break
            }
        }
        if (targetFrameIndex < 0) return state

        val target = frames[targetFrameIndex]
        val newAnchor = ReelsRoundRobinAnchor(
            round = target.number,
            sourceKey = target.members[targetSlot],
            itemId = itemId,
        )
        val newFrames = if (targetFrameIndex == 0) frames else frames.drop(targetFrameIndex)
        if (newAnchor == anchor && newFrames == state.frames) return state
        return state.copy(anchor = newAnchor, frames = newFrames)
    }

    /**
     * Releases future invalid candidates back into the same source's unresolved turn. Played
     * slots (at or before the anchor) are never touched. Later reservations of a repaired source
     * are released as well, so the next selection re-plans them instead of keeping future slots
     * the repaired source can no longer justify.
     */
    fun repair(
        state: ReelsRoundRobinSnapshot,
        invalidIds: Set<String>,
    ): ReelsRoundRobinSnapshot {
        if (invalidIds.isEmpty() || state.frames.isEmpty()) return state
        val frames = sanitizeFrames(state.frames)
        val updatedFrames = frames.toMutableList()
        var entries = state.entries
        val rePlanSources = mutableMapOf<Int, MutableSet<String>>()
        var changed = false

        for (frameIndex in frames.indices) {
            val frame = frames[frameIndex]
            val playedLimit = playedSlotLimit(frame, state.anchor)
            var turns: LinkedHashMap<String, ReelsRoundRobinTurn>? = null
            for (index in frame.members.indices) {
                if (index <= playedLimit) continue
                val member = frame.members[index]
                val itemId = frame.turns[member]?.itemId ?: continue
                if (itemId !in invalidIds) continue
                if (turns == null) turns = LinkedHashMap(frame.turns)
                turns.remove(member)
                entries = entries.map { entry ->
                    if (entry.candidate.itemId == itemId) {
                        entry.copy(
                            round = null,
                            ordinal = null,
                            validation = ReelsRoundRobinValidation.UNKNOWN,
                        )
                    } else {
                        entry
                    }
                }
                rePlanSources.getOrPut(frameIndex) { mutableSetOf() } += member
                changed = true
            }
            turns?.let { updatedFrames[frameIndex] = frame.copy(turns = orderedTurns(frame.members, it)) }
        }
        if (!changed) return state

        rePlanSources.forEach { (repairedIndex, repairedSources) ->
            for (laterIndex in repairedIndex + 1 until updatedFrames.size) {
                val later = updatedFrames[laterIndex]
                var turns: LinkedHashMap<String, ReelsRoundRobinTurn>? = null
                repairedSources.forEach { sourceKey ->
                    val itemId = later.turns[sourceKey]?.itemId ?: return@forEach
                    if (turns == null) turns = LinkedHashMap(later.turns)
                    turns.remove(sourceKey)
                    entries = entries.map { entry ->
                        if (entry.candidate.itemId == itemId) {
                            entry.copy(round = null, ordinal = null)
                        } else {
                            entry
                        }
                    }
                }
                turns?.let { updatedFrames[laterIndex] = later.copy(turns = orderedTurns(later.members, it)) }
            }
        }
        return state.copy(frames = updatedFrames, entries = entries)
    }
}

private class Selection(
    private val state: ReelsRoundRobinSnapshot,
    private val eligibleIds: Set<String>,
    private val nowMs: Long,
    private val limit: Int,
) {
    private val sources = state.sources.associateBy { it.key }
    private val frames: MutableList<ReelsRoundRobinFrame>
    private var entries = state.entries
    private val entriesById = state.entries.associateBy { it.candidate.itemId }
    private val needs = mutableListOf<ReelsRoundRobinNeed>()
    private val page = mutableListOf<ReelsCandidate>()
    private val claimedContentKeys = mutableSetOf<String>()
    private val reservedItemIds = mutableSetOf<String>()
    private var blocked = false
    private var limitReached = false
    private var retryWait = false
    private val startSlot: Int

    init {
        val sanitized = sanitizeFrames(state.frames)
        val anchorIndex = state.anchor?.let { anchor ->
            sanitized.indexOfFirst { it.number == anchor.round }
        } ?: -1
        val activeFrames = if (anchorIndex > 0) sanitized.drop(anchorIndex) else sanitized
        frames = activeFrames.take(2).toMutableList()
        startSlot = if (frames.isEmpty()) 0 else frontierSlot(frames.first(), state.anchor)
        // A migrated head may be anchored before its frame turn is materialized. It is the
        // current card, not a staged tail candidate, so it must never be selected again.
        state.anchor?.itemId?.let(reservedItemIds::add)
        // Include played/pruned frames in reservedItemIds so consumed historical cards cannot look
        // like unreserved FILTERED candidates and trigger FILTER_BUDGET on the next round.
        for (frame in sanitized) {
            for (member in frame.members) {
                frame.turns[member]?.itemId?.let(reservedItemIds::add)
            }
        }
        for (frame in frames) {
            for (member in frame.members) {
                val turn = frame.turns[member] ?: continue
                val itemId = turn.itemId ?: continue
                val entry = entriesById[itemId] ?: continue
                if (entry.sourceKey == member &&
                    entry.validation == ReelsRoundRobinValidation.ELIGIBLE &&
                    itemId in eligibleIds
                ) {
                    entry.contentKey?.let(claimedContentKeys::add)
                }
            }
        }
    }

    fun commit(): ReelsRoundRobinCommit {
        var frameIndex = 0
        var firstFrame = true
        while (frameIndex < frames.size) {
            val frame = frames[frameIndex]
            val from = if (firstFrame) startSlot else 0
            for (index in from until frame.members.size) {
                if (page.size >= limit) {
                    limitReached = true
                    break
                }
                process(frameIndex, index)
                if (blocked) {
                    for (later in index + 1 until frame.members.size) {
                        if (needs.size >= MAX_NEEDS_PER_SELECT) break
                        collectNeed(frame, later)
                    }
                    break
                }
            }
            if (limitReached || blocked) break
            if (!limitReached &&
                page.size < limit &&
                frameIndex == frames.lastIndex &&
                frames.size == 1 &&
                state.bootstrapComplete
            ) {
                val nextMembers = participatingSources(state.sources, frame.number + 1)
                if (nextMembers.isNotEmpty()) {
                    frames += ReelsRoundRobinFrame(
                        number = frame.number + 1,
                        members = nextMembers,
                        turns = emptyMap(),
                    )
                }
            }
            firstFrame = false
            frameIndex++
        }

        return ReelsRoundRobinCommit(
            snapshot = state.copy(
                frames = frames.map { it.copy(turns = orderedTurns(it.members, it.turns)) },
                entries = entries,
            ),
            page = page.toList(),
            needs = needs.toList(),
            stop = stop(),
        )
    }

    private fun process(frameIndex: Int, index: Int) {
        val frame = frames[frameIndex]
        val member = frame.members[index]
        val source = sources[member]
        val turn = frame.turns[member]

        if (turn != null) {
            val itemId = turn.itemId
            if (itemId == null) {
                // A budgeted skip is not final: once its source head check is due the slot is
                // re-decided (usually into a refresh-head scan) instead of staying filtered
                // forever in the prepared frame.
                val staleBudget = turn.skip == ReelsRoundRobinSkip.FILTER_BUDGET &&
                    sources[member]?.let { it.fruitlessScan && headCheckDue(it) } == true
                if (!staleBudget) {
                    if (turn.skip == ReelsRoundRobinSkip.RETRY) retryWait = true
                    return
                }
                // Drop the expired budget skip so the slot is decided again below.
                frames[frameIndex] = frame.copy(
                    turns = orderedTurns(frame.members, frame.turns - member),
                )
            } else {
                val entry = entriesById[itemId]
                if (entry != null &&
                    entry.sourceKey == member &&
                    entry.validation == ReelsRoundRobinValidation.ELIGIBLE &&
                    itemId in eligibleIds
                ) {
                    page += entry.candidate
                } else {
                    blocked = true
                    addNeed(ReelsRoundRobinNeed.Revalidate(itemId))
                }
                return
            }
        }

        if (source == null || !source.present) {
            setSkip(frameIndex, member, ReelsRoundRobinSkip.REMOVED)
            return
        }
        if (source.excluded) {
            setSkip(frameIndex, member, ReelsRoundRobinSkip.HIDDEN)
            return
        }

        val candidate = pickCandidate(member)
        if (candidate != null) {
            reserve(frameIndex, member, index, candidate)
            return
        }

        val userFacingFrame = frameIndex == 0
        when {
            source.retryAt != null && source.retryAt > nowMs -> {
                if (userFacingFrame) {
                    setSkip(frameIndex, member, ReelsRoundRobinSkip.RETRY)
                } else {
                    blocked = true
                    retryWait = true
                }
            }
            source.fruitlessScan && !headCheckDue(source) -> {
                // The last scan advanced the checkpoint but staged nothing (every card was
                // hidden/consumed). Re-scanning the same page would starve the ring, so this
                // turn is skipped for the current round and the source waits for its head
                // check. A due head check falls through to a regular scan below, ahead of the
                // history-exhausted decision, because that scan may still find new cards.
                setSkip(frameIndex, member, ReelsRoundRobinSkip.FILTER_BUDGET)
            }
            source.historyExhausted && !headCheckDue(source) -> {
                // A scheduled head check may still find content, so that turn waits for it.
                // Otherwise the empty round decision is recorded, also in the prepared frame,
                // so a fully played active frame cannot stall forever.
                if (!userFacingFrame && source.nextHeadCheckAt != null) {
                    blocked = true
                } else {
                    setSkip(frameIndex, member, ReelsRoundRobinSkip.EMPTY)
                }
            }
            hasFilteredEntry(member) -> {
                if (source.checkpoint == null || headCheckDue(source)) {
                    // Budgeted filtering only sees already staged content; without a usable
                    // checkpoint or with a due head check new content must be requested.
                    blocked = true
                    addNeed(scanNeed(source))
                } else {
                    setSkip(frameIndex, member, ReelsRoundRobinSkip.FILTER_BUDGET)
                }
            }
            else -> {
                val unchecked = newestStagedUnchecked(member)
                blocked = true
                if (unchecked != null) {
                    addNeed(ReelsRoundRobinNeed.Revalidate(unchecked.candidate.itemId))
                } else {
                    addNeed(scanNeed(source))
                }
            }
        }
    }

    private fun collectNeed(frame: ReelsRoundRobinFrame, index: Int) {
        if (needs.size >= MAX_NEEDS_PER_SELECT) return
        val member = frame.members[index]
        val source = sources[member] ?: return
        if (!source.present || source.excluded) return
        val turn = frame.turns[member]
        if (turn != null) {
            val itemId = turn.itemId ?: return
            val entry = entriesById[itemId]
            val usable = entry != null &&
                entry.sourceKey == member &&
                entry.validation == ReelsRoundRobinValidation.ELIGIBLE &&
                itemId in eligibleIds
            if (!usable) addNeed(ReelsRoundRobinNeed.Revalidate(itemId))
            return
        }
        if (pickCandidate(member) != null) return
        if (source.retryAt != null && source.retryAt > nowMs) return
        if (source.fruitlessScan && !headCheckDue(source)) return
        if (source.historyExhausted && !headCheckDue(source)) return
        if (hasFilteredEntry(member)) {
            if (source.checkpoint == null || headCheckDue(source)) addNeed(scanNeed(source))
            return
        }
        val unchecked = newestStagedUnchecked(member)
        if (unchecked != null) {
            addNeed(ReelsRoundRobinNeed.Revalidate(unchecked.candidate.itemId))
        } else {
            addNeed(scanNeed(source))
        }
    }

    private fun reserve(
        frameIndex: Int,
        member: String,
        index: Int,
        entry: ReelsRoundRobinEntry,
    ) {
        val frame = frames[frameIndex]
        val itemId = entry.candidate.itemId
        val turns = LinkedHashMap(frame.turns)
        turns[member] = ReelsRoundRobinTurn(itemId = itemId, skip = null)
        frames[frameIndex] = frame.copy(turns = orderedTurns(frame.members, turns))
        entries = entries.map { current ->
            if (current === entry) {
                current.copy(round = frame.number, ordinal = index.toLong())
            } else {
                current
            }
        }
        reservedItemIds += itemId
        entry.contentKey?.let(claimedContentKeys::add)
        page += entry.candidate
    }

    private fun setSkip(frameIndex: Int, member: String, skip: ReelsRoundRobinSkip) {
        val frame = frames[frameIndex]
        val turns = LinkedHashMap(frame.turns)
        turns[member] = ReelsRoundRobinTurn(itemId = null, skip = skip)
        frames[frameIndex] = frame.copy(turns = orderedTurns(frame.members, turns))
        if (skip == ReelsRoundRobinSkip.RETRY) retryWait = true
    }

    private fun pickCandidate(sourceKey: String): ReelsRoundRobinEntry? =
        entries.asSequence()
            .filter {
                it.sourceKey == sourceKey &&
                    it.round == null &&
                    it.ordinal == null &&
                    it.validation == ReelsRoundRobinValidation.ELIGIBLE &&
                    it.candidate.itemId in eligibleIds &&
                    it.candidate.itemId !in reservedItemIds &&
                    (it.contentKey == null || it.contentKey !in claimedContentKeys)
            }
            .sortedWith(
                compareByDescending<ReelsRoundRobinEntry> { it.orderKey }
                    .thenBy { it.candidate.itemId },
            )
            .firstOrNull()

    private fun newestStagedUnchecked(sourceKey: String): ReelsRoundRobinEntry? =
        entries.asSequence()
            .filter {
                it.sourceKey == sourceKey &&
                    it.round == null &&
                    it.ordinal == null &&
                    it.candidate.itemId !in reservedItemIds &&
                    (
                        it.validation == ReelsRoundRobinValidation.UNKNOWN ||
                            it.validation == ReelsRoundRobinValidation.DEFERRED
                        )
            }
            .sortedWith(
                compareByDescending<ReelsRoundRobinEntry> { it.orderKey }
                    .thenBy { it.candidate.itemId },
            )
            .firstOrNull()

    private fun hasFilteredEntry(sourceKey: String): Boolean =
        entries.any {
            it.sourceKey == sourceKey &&
                it.round == null &&
                it.ordinal == null &&
                it.candidate.itemId !in reservedItemIds &&
                it.validation == ReelsRoundRobinValidation.FILTERED
        }

    private fun scanNeed(source: ReelsRoundRobinSourceState): ReelsRoundRobinNeed.Scan =
        ReelsRoundRobinNeed.Scan(
            sourceKey = source.key,
            checkpoint = source.checkpoint,
            refreshHead = source.checkpoint == null || headCheckDue(source),
            maxCandidates = limit,
        )

    private fun headCheckDue(source: ReelsRoundRobinSourceState): Boolean {
        val dueAt = source.nextHeadCheckAt ?: return false
        return nowMs >= dueAt
    }

    private fun addNeed(need: ReelsRoundRobinNeed) {
        if (needs.size >= MAX_NEEDS_PER_SELECT) return
        when (need) {
            is ReelsRoundRobinNeed.Scan -> {
                if (needs.none { it is ReelsRoundRobinNeed.Scan && it.sourceKey == need.sourceKey }) {
                    needs += need
                }
            }
            is ReelsRoundRobinNeed.Revalidate -> {
                if (needs.none { it is ReelsRoundRobinNeed.Revalidate && it.itemId == need.itemId }) {
                    needs += need
                }
            }
        }
    }

    private fun stop(): ReelsRoundRobinStop {
        val allResolved = frames.isNotEmpty() &&
            frames.all { frame -> frame.members.all { frame.turns[it] != null } }
        val horizonReached = allResolved && (frames.size == 2 || !state.bootstrapComplete)
        return when {
            page.isNotEmpty() && horizonReached -> ReelsRoundRobinStop.RESERVATION_HORIZON
            page.isNotEmpty() -> ReelsRoundRobinStop.PAGE_READY
            needs.isNotEmpty() -> ReelsRoundRobinStop.NEED_INPUT
            retryWait -> ReelsRoundRobinStop.WAIT_RETRY
            allResolved && frames.any { frame -> frame.turns.values.any { it.itemId != null } } ->
                ReelsRoundRobinStop.RESERVATION_HORIZON
            else -> ReelsRoundRobinStop.EMPTY
        }
    }
}

private data class PlannedFrames(
    val frames: List<ReelsRoundRobinFrame>,
    val entries: List<ReelsRoundRobinEntry>,
)

private fun aliasMap(catalog: ReelsRoundRobinCatalog): Map<String, String> {
    val aliases = LinkedHashMap<String, String>()
    catalog.sources.forEach { source -> aliases[source.key] = source.key }
    catalog.sources.forEach { source ->
        source.previousKeys.forEach { previous ->
            if (previous !in aliases) aliases[previous] = source.key
        }
    }
    return aliases
}

private fun Map<String, String>.canonical(key: String): String = this[key] ?: key

private fun nextRoundNumber(
    frames: List<ReelsRoundRobinFrame>,
    anchor: ReelsRoundRobinAnchor?,
): Long {
    if (frames.isEmpty()) return 0
    val sorted = frames.sortedBy { it.number }
    val active = anchor?.let { current -> sorted.firstOrNull { it.number == current.round } } ?: sorted.first()
    val prepared = sorted.firstOrNull { it.number > active.number }
    return prepared?.number ?: (active.number + 1)
}

private fun mergeSources(
    state: ReelsRoundRobinSnapshot,
    catalog: ReelsRoundRobinCatalog,
    alias: Map<String, String>,
    entries: List<ReelsRoundRobinEntry>,
    nextRound: Long,
): List<ReelsRoundRobinSourceState> {
    val authorsBySource = entries.mapNotNull { entry ->
        entry.candidate.author?.let { author -> alias.canonical(entry.sourceKey) to author }
    }.groupBy({ it.first }, { it.second })

    val merged = LinkedHashMap<String, ReelsRoundRobinSourceState>()
    state.sources.groupBy { alias.canonical(it.key) }.forEach { (canonical, group) ->
        val ordered = group.sortedWith(compareBy({ it.key != canonical }, { it.order }, { it.key }))
        var accumulator = ordered.first().forCanonical(canonical)
        ordered.drop(1).forEach { source ->
            accumulator = mergeStates(accumulator, source.forCanonical(canonical))
        }
        merged[canonical] = accumulator
    }

    var nextOrder = (merged.values.maxOfOrNull { it.order } ?: -1L) + 1L
    catalog.sources
        .distinctBy { it.key }
        .filter { it.key !in merged }
        .sortedWith(compareBy({ it.title }, { it.key }))
        .forEach { source ->
            merged[source.key] = newSource(
                source = source,
                order = nextOrder++,
                joinRound = if (state.bootstrapComplete) nextRound else 0,
                knownAuthors = authorsBySource[source.key].orEmpty().toSet(),
            )
        }

    val catalogByKey = catalog.sources.associateBy { it.key }
    return merged.values
        .map { source ->
            val catalogSource = catalogByKey[source.key]
            val knownAuthors = source.knownAuthors + authorsBySource[source.key].orEmpty()
            if (catalogSource == null) {
                // A snapshot that does not claim to be authoritative may omit sources
                // transiently (partial pagination, a failed chat lookup). Only a complete
                // catalog may remove membership; additions and exclusions still apply.
                source.copy(
                    present = if (catalog.complete) false else source.present,
                    knownAuthors = knownAuthors,
                )
            } else {
                source.copy(
                    title = catalogSource.title,
                    author = catalogSource.author,
                    previousKeys = source.previousKeys + catalogSource.previousKeys,
                    present = true,
                    excluded = catalogSource.excluded,
                    joinRound = if (source.present) {
                        source.joinRound
                    } else if (state.bootstrapComplete) {
                        nextRound
                    } else {
                        0
                    },
                    contentRevision = catalogSource.contentRevision,
                    knownAuthors = knownAuthors,
                )
            }
        }
        .sortedWith(compareBy({ it.order }, { it.key }))
}

private fun newSource(
    source: ReelsRoundRobinCatalogSource,
    order: Long,
    joinRound: Long,
    knownAuthors: Set<String>,
): ReelsRoundRobinSourceState = ReelsRoundRobinSourceState(
    key = source.key,
    order = order,
    title = source.title,
    author = source.author,
    knownAuthors = knownAuthors,
    previousKeys = source.previousKeys.toSet(),
    present = true,
    excluded = source.excluded,
    joinRound = joinRound,
    contentRevision = source.contentRevision,
    checkpoint = null,
    checkpointContext = null,
    historyExhausted = false,
    retryAt = null,
    retryReason = null,
    nextHeadCheckAt = null,
)

private fun ReelsRoundRobinSourceState.forCanonical(
    canonical: String,
): ReelsRoundRobinSourceState = if (key == canonical) {
    this
} else {
    copy(
        key = canonical,
        previousKeys = previousKeys + key,
        checkpoint = null,
        checkpointContext = null,
        historyExhausted = false,
        retryAt = null,
        retryReason = null,
        nextHeadCheckAt = null,
        fruitlessScan = false,
    )
}

private fun mergeStates(
    primary: ReelsRoundRobinSourceState,
    secondary: ReelsRoundRobinSourceState,
): ReelsRoundRobinSourceState {
    val anyIncluded =
        (primary.present && !primary.excluded) || (secondary.present && !secondary.excluded)
    return primary.copy(
        order = minOf(primary.order, secondary.order),
        author = primary.author ?: secondary.author,
        knownAuthors = primary.knownAuthors + secondary.knownAuthors,
        previousKeys = primary.previousKeys + secondary.previousKeys,
        present = primary.present || secondary.present,
        excluded = !anyIncluded,
        joinRound = minOf(primary.joinRound, secondary.joinRound),
        contentRevision = primary.contentRevision ?: secondary.contentRevision,
        fruitlessScan = primary.fruitlessScan || secondary.fruitlessScan,
    )
}

private fun normalizeEntries(
    entries: List<ReelsRoundRobinEntry>,
    alias: Map<String, String>,
): List<ReelsRoundRobinEntry> =
    entries.map { it.copy(sourceKey = alias.canonical(it.sourceKey)) }
        .distinctBy { it.candidate.itemId }

private fun normalizeFrames(
    frames: List<ReelsRoundRobinFrame>,
    alias: Map<String, String>,
): List<ReelsRoundRobinFrame> =
    frames.sortedBy { it.number }.map { frame ->
        val members = mutableListOf<String>()
        val turns = LinkedHashMap<String, ReelsRoundRobinTurn>()
        frame.members.forEach { rawMember ->
            val member = alias.canonical(rawMember)
            val turn = sanitizeTurn(frame.turns[rawMember])
            if (member !in members) {
                members += member
                if (turn != null) turns[member] = turn
            } else if (turn != null && turns[member] == null) {
                turns[member] = turn
            }
        }
        frame.copy(members = members, turns = orderedTurns(members, turns))
    }

private fun planFrames(
    state: ReelsRoundRobinSnapshot,
    frames: List<ReelsRoundRobinFrame>,
    sources: List<ReelsRoundRobinSourceState>,
    entries: List<ReelsRoundRobinEntry>,
    anchor: ReelsRoundRobinAnchor?,
    bootstrapComplete: Boolean,
    membershipChanged: Boolean,
): PlannedFrames {
    var planned = frames.toMutableList()
    var plannedEntries = entries
    val sourcesByKey = sources.associateBy { it.key }

    fun unreserveRound(round: Long) {
        plannedEntries = plannedEntries.map {
            if (it.round == round) it.copy(round = null, ordinal = null) else it
        }
    }

    fun unreserveItem(itemId: String) {
        plannedEntries = plannedEntries.map {
            if (it.candidate.itemId == itemId) it.copy(round = null, ordinal = null) else it
        }
    }

    anchor?.let { current ->
        val anchorIndex = planned.indexOfFirst { it.number == current.round }
        if (anchorIndex > 0) {
            planned = planned.drop(anchorIndex).toMutableList()
        }
    }

    if (planned.isEmpty()) {
        val members = participatingSources(sources, 0)
        if (members.isNotEmpty()) {
            planned += ReelsRoundRobinFrame(number = 0, members = members, turns = emptyMap())
            if (bootstrapComplete) {
                val nextMembers = participatingSources(sources, 1)
                if (nextMembers.isNotEmpty()) {
                    planned += ReelsRoundRobinFrame(number = 1, members = nextMembers, turns = emptyMap())
                }
            }
        }
        return finalizePlanned(planned, plannedEntries)
    }

    var active = planned.first()
    if (!state.bootstrapComplete && active.number == 0L) {
        val additions = participatingSources(sources, active.number).filter { it !in active.members }
        if (additions.isNotEmpty()) {
            val members = active.members + additions
            active = active.copy(members = members, turns = orderedTurns(members, active.turns))
        }
    }

    val playedLimit = playedSlotLimit(active, anchor)
    val activeTurns = LinkedHashMap(active.turns)
    active.members.forEachIndexed { index, member ->
        if (index <= playedLimit) return@forEachIndexed
        val source = sourcesByKey[member]
        if (source != null && source.present && !source.excluded) return@forEachIndexed
        val turn = activeTurns[member]
        if (turn?.skip != null) return@forEachIndexed
        turn?.itemId?.let { unreserveItem(it) }
        val skip = if (source != null && source.present && source.excluded) {
            ReelsRoundRobinSkip.HIDDEN
        } else {
            ReelsRoundRobinSkip.REMOVED
        }
        activeTurns[member] = ReelsRoundRobinTurn(itemId = null, skip = skip)
    }
    active = active.copy(turns = orderedTurns(active.members, activeTurns))
    planned[0] = active

    val preparedIndex = planned.indexOfFirst { it.number > active.number }
    if (!bootstrapComplete) {
        if (preparedIndex >= 0) {
            planned.drop(preparedIndex).forEach { unreserveRound(it.number) }
            planned = mutableListOf(active)
        }
        return finalizePlanned(planned, plannedEntries)
    }

    if (preparedIndex >= 0) {
        val prepared = planned[preparedIndex]
        val expectedNumber = active.number + 1
        val stale = membershipChanged ||
            prepared.number != expectedNumber ||
            prepared.members.any { member ->
                val source = sourcesByKey[member]
                source == null ||
                    !source.present ||
                    source.excluded ||
                    source.joinRound > prepared.number
            }
        if (stale) {
            unreserveRound(prepared.number)
            if (prepared.number != expectedNumber) unreserveRound(expectedNumber)
            val members = participatingSources(sources, expectedNumber)
            planned = mutableListOf(active)
            if (members.isNotEmpty()) {
                planned += ReelsRoundRobinFrame(expectedNumber, members, emptyMap())
            }
        } else {
            planned = mutableListOf(active, prepared)
        }
    } else {
        val expectedNumber = active.number + 1
        val members = participatingSources(sources, expectedNumber)
        if (members.isNotEmpty()) {
            planned += ReelsRoundRobinFrame(expectedNumber, members, emptyMap())
        }
    }
    return finalizePlanned(planned, plannedEntries)
}

private fun finalizePlanned(
    frames: List<ReelsRoundRobinFrame>,
    entries: List<ReelsRoundRobinEntry>,
): PlannedFrames {
    val keptFrames = frames.take(2)
    val itemsByRound = keptFrames.associate { frame ->
        frame.number to frame.turns.values.mapNotNullTo(mutableSetOf()) { it.itemId }
    }
    val normalizedEntries = entries.map { entry ->
        val round = entry.round ?: return@map entry
        val reservedItems = itemsByRound[round] ?: return@map entry
        if (entry.candidate.itemId in reservedItems) entry else entry.copy(round = null, ordinal = null)
    }
    return PlannedFrames(keptFrames, normalizedEntries)
}

private fun participatingSources(
    sources: List<ReelsRoundRobinSourceState>,
    round: Long,
): List<String> = sources
    .asSequence()
    .filter { it.present && !it.excluded && it.joinRound <= round }
    .sortedWith(compareBy({ it.order }, { it.key }))
    .map { it.key }
    .toList()

private fun sanitizeFrames(frames: List<ReelsRoundRobinFrame>): List<ReelsRoundRobinFrame> =
    frames.sortedBy { it.number }.map { frame ->
        val turns = LinkedHashMap<String, ReelsRoundRobinTurn>()
        frame.members.forEach { member ->
            sanitizeTurn(frame.turns[member])?.let { turns[member] = it }
        }
        frame.copy(turns = orderedTurns(frame.members, turns))
    }

private fun sanitizeTurn(turn: ReelsRoundRobinTurn?): ReelsRoundRobinTurn? {
    if (turn == null) return null
    return if ((turn.itemId != null) != (turn.skip != null)) turn else null
}

private fun orderedTurns(
    members: List<String>,
    turns: Map<String, ReelsRoundRobinTurn>,
): Map<String, ReelsRoundRobinTurn> {
    if (turns.isEmpty()) return emptyMap()
    val ordered = LinkedHashMap<String, ReelsRoundRobinTurn>(turns.size)
    members.forEach { member ->
        turns[member]?.let { ordered[member] = it }
    }
    return ordered
}

private fun slotOf(
    frame: ReelsRoundRobinFrame,
    anchor: ReelsRoundRobinAnchor,
): Int {
    val byKey = frame.members.indexOf(anchor.sourceKey)
    if (byKey >= 0) return byKey
    return frame.members.indexOfFirst { frame.turns[it]?.itemId == anchor.itemId }
}

private fun frontierSlot(
    frame: ReelsRoundRobinFrame,
    anchor: ReelsRoundRobinAnchor?,
): Int {
    if (anchor == null || anchor.round != frame.number) return 0
    val slot = slotOf(frame, anchor)
    return if (slot >= 0) (slot + 1).coerceAtMost(frame.members.size) else 0
}

private fun playedSlotLimit(
    frame: ReelsRoundRobinFrame,
    anchor: ReelsRoundRobinAnchor?,
): Int {
    if (anchor == null) return -1
    if (frame.number < anchor.round) return frame.members.lastIndex
    if (frame.number > anchor.round) return -1
    return slotOf(frame, anchor)
}
