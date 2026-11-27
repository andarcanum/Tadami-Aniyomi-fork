package tachiyomi.domain.reels.anime.model

import kotlinx.serialization.Serializable

/**
 * One host-owned round-robin projection over a fixed source roster.
 *
 * Frames and turns are persisted as JSON, so they are deliberately limited to primitives and
 * collections. Candidate payloads live in [ReelsRoundRobinEntry] and are never copied into a
 * frame.
 */
@Serializable
enum class ReelsRoundRobinSkip { FILTER_BUDGET, EMPTY, RETRY, UNAVAILABLE, REMOVED, HIDDEN }

/**
 * One member's turn in a round. A resolved turn holds exactly one of [itemId] / [skip];
 * a missing map entry means the turn has not been attempted yet.
 */
@Serializable
data class ReelsRoundRobinTurn(val itemId: String?, val skip: ReelsRoundRobinSkip?)

@Serializable
data class ReelsRoundRobinFrame(
    val number: Long,
    val members: List<String>,
    val turns: Map<String, ReelsRoundRobinTurn>,
)

data class ReelsRoundRobinAnchor(val round: Long, val sourceKey: String, val itemId: String)

@Serializable
data class ReelsRoundRobinSourceState(
    val key: String,
    val order: Long,
    val title: String,
    val author: String?,
    val knownAuthors: Set<String>,
    val previousKeys: Set<String>,
    val present: Boolean,
    val excluded: Boolean,
    val joinRound: Long,
    val contentRevision: String?,
    val checkpoint: String?,
    val checkpointContext: String?,
    val historyExhausted: Boolean,
    val retryAt: Long?,
    val retryReason: String?,
    val nextHeadCheckAt: Long?,
    /**
     * The last scan advanced the checkpoint but every card on it was locally hidden/consumed,
     * so nothing can be staged for this source until a later head check. It turns the next
     * slot into a budgeted FILTER_BUDGET skip instead of an immediate re-scan of the same page.
     */
    val fruitlessScan: Boolean = false,
)

enum class ReelsRoundRobinValidation { UNKNOWN, ELIGIBLE, FILTERED, DEFERRED, UNAVAILABLE }

data class ReelsRoundRobinEntry(
    val candidate: ReelsCandidate,
    val sourceKey: String,
    val orderKey: Long,
    val contentKey: String?,
    val checkedContext: String?,
    val validation: ReelsRoundRobinValidation,
    val round: Long?,
    val ordinal: Long?,
)

data class ReelsRoundRobinSnapshot(
    val key: ReelsSessionKey,
    val revision: Long,
    val unseenOnly: Boolean,
    val bootstrapComplete: Boolean,
    val catalogRevision: String?,
    val sources: List<ReelsRoundRobinSourceState>,
    val frames: List<ReelsRoundRobinFrame>,
    val anchor: ReelsRoundRobinAnchor?,
    val entries: List<ReelsRoundRobinEntry>,
    val unmappedItemIds: List<String>,
)

data class ReelsRoundRobinCatalogSource(
    val key: String,
    val title: String,
    val author: String?,
    val contentRevision: String?,
    val excluded: Boolean,
    val previousKeys: List<String>,
)

data class ReelsRoundRobinCatalog(
    val revision: String,
    val complete: Boolean,
    val sources: List<ReelsRoundRobinCatalogSource>,
)

data class ReelsRoundRobinIdentity(val sourceKey: String, val orderKey: Long)

sealed interface ReelsRoundRobinNeed {
    data class Scan(
        val sourceKey: String,
        val checkpoint: String?,
        val refreshHead: Boolean,
        val maxCandidates: Int,
    ) : ReelsRoundRobinNeed

    data class Revalidate(val itemId: String) : ReelsRoundRobinNeed
}

enum class ReelsRoundRobinStop { PAGE_READY, NEED_INPUT, RESERVATION_HORIZON, PAUSED, WAIT_RETRY, EMPTY }

data class ReelsRoundRobinCommit(
    val snapshot: ReelsRoundRobinSnapshot,
    val page: List<ReelsCandidate>,
    val needs: List<ReelsRoundRobinNeed>,
    val stop: ReelsRoundRobinStop,
)

data class ReelsRoundRobinHandle(val session: ReelsSessionHandle)

/** Updates applied by the repository before the scheduler re-plans the projection. */
sealed interface ReelsRoundRobinUpdate {
    data class Page(
        val sourceKey: String,
        val fromCheckpoint: String?,
        val nextCheckpoint: String,
        val entries: List<ReelsRoundRobinEntry>,
        val historyExhausted: Boolean,
        val nextHeadCheckAt: Long?,
        /**
         * The scan returned cards but every one of them was locally ineligible, so [entries]
         * is empty. The source records a fruitless scan and a later head check instead of
         * being scanned again immediately in the same round.
         */
        val fruitless: Boolean = false,
    ) : ReelsRoundRobinUpdate

    data class Deferred(val sourceKey: String, val reason: String, val retryAt: Long) : ReelsRoundRobinUpdate

    data class Unavailable(val sourceKey: String) : ReelsRoundRobinUpdate

    data class ResetCursor(val sourceKey: String) : ReelsRoundRobinUpdate

    data class Revalidated(
        val itemId: String,
        val candidate: ReelsCandidate?,
        val validation: ReelsRoundRobinValidation,
        val retryAt: Long?,
    ) : ReelsRoundRobinUpdate
}

/** One transactional mutation requested from the repository. */
sealed interface ReelsRoundRobinMutation {
    data class Catalog(val value: ReelsRoundRobinCatalog, val nowMs: Long) : ReelsRoundRobinMutation

    data class Import(
        val identities: Map<String, ReelsRoundRobinIdentity>,
        val nowMs: Long,
    ) : ReelsRoundRobinMutation

    data class Pump(
        val updates: List<ReelsRoundRobinUpdate>,
        val limit: Int,
        val nowMs: Long,
    ) : ReelsRoundRobinMutation

    data class Activate(val itemId: String, val nowMs: Long) : ReelsRoundRobinMutation

    data class Recheck(
        val rewindSources: Set<String>,
        val nowMs: Long,
    ) : ReelsRoundRobinMutation

    /**
     * Releases future turns whose payload no longer exists. [ReelsRoundRobinScheduler.repair]
     * keeps the played prefix and the anchor untouched and re-plans later reservations of the
     * affected sources, so the next selection can request a fresh scan instead of repeating an
     * unsatisfiable `Need.Revalidate` forever.
     */
    data class Repair(
        val invalidItemIds: Set<String>,
        val nowMs: Long,
    ) : ReelsRoundRobinMutation
}
