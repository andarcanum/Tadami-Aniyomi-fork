package tachiyomi.domain.reels.anime.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinCommit
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinHandle
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinMutation
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinSnapshot
import tachiyomi.domain.reels.anime.model.ReelsSessionKey

/**
 * Durable host-owned round-robin state.
 *
 * Every mutation is fenced by the session handle, account/context and [expectedRevision] and
 * runs in one transaction. The scheduler itself is pure; this repository is the only place
 * that turns its immutable transitions into storage writes.
 *
 * Revision rule for [mutate]: [expectedRevision] must match the stored revision for every
 * mutation except a pump whose updates are exclusively `ReelsRoundRobinUpdate.Page`. Such a
 * page-only pump may rebase onto a fresher revision, but only when every page's
 * `fromCheckpoint` still matches that source's stored checkpoint. A pump carrying any
 * Deferred/Unavailable/ResetCursor/Revalidated update (or no updates at all) mutates source
 * state and is revision-fenced like every other mutation.
 *
 * Show-watched protection lifetime: candidate pruning (`pruneConsumedRemnants`) and direct
 * removal keep a consumed payload only while an existing round-robin state row has
 * `unseen_only = 0`, an active session owner, and the same `context_key` as the session.
 * Round-robin mode must claim its own dedicated context key (`storageKey + ':rr:1'`); legacy
 * or any other feed mode must never reuse that key, so its claim changes the session context
 * and drops the protection. History reset, account switch and mode switch must delete the
 * scoped round-robin rows (`reels_rr_item` -> `reels_rr_source` -> `reels_rr_state`) in the
 * same transaction that changes the generation or clears the inbox (owned by T4/T7);
 * [release] alone deliberately keeps the durable projection.
 */
interface ReelsRoundRobinRepository {

    suspend fun open(
        key: ReelsSessionKey,
        ownerNonce: String,
        historyGeneration: Long,
        unseenOnly: Boolean,
    ): ReelsRoundRobinHandle

    suspend fun read(handle: ReelsRoundRobinHandle): ReelsRoundRobinSnapshot

    suspend fun mutate(
        handle: ReelsRoundRobinHandle,
        expectedRevision: Long,
        change: ReelsRoundRobinMutation,
    ): ReelsRoundRobinCommit

    fun observeEligibility(scope: String, sourceId: Long): Flow<Unit>

    suspend fun release(handle: ReelsRoundRobinHandle)

    suspend fun clearScope(scope: String)

    /**
     * Drops only the round-robin projection of [sourceId] (`reels_rr_item` -> `reels_rr_source`
     * -> `reels_rr_state`). The session header/checkpoint and the candidate inbox/history are
     * kept, so a corrupt projection can be rebuilt from the catalog and a fresh scan without
     * side effects on the user's watched state.
     */
    suspend fun resetProjection(scope: String, sourceId: Long)
}
