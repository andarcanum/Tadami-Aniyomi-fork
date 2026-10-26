package tachiyomi.domain.reels.anime.repository

import tachiyomi.domain.reels.anime.model.ReelsActiveCheckpoint
import tachiyomi.domain.reels.anime.model.ReelsCandidate
import tachiyomi.domain.reels.anime.model.ReelsSessionHandle
import tachiyomi.domain.reels.anime.model.ReelsSessionKey
import tachiyomi.domain.reels.anime.model.ReelsSessionWindow
import java.util.Date

interface ReelsSessionRepository {

    /**
     * Atomically claims or opens a session handle.
     * When [reset] is true, starts a new session generation with a fresh sessionId and cleared queue.
     */
    suspend fun open(
        key: ReelsSessionKey,
        ownerNonce: String,
        historyGeneration: Long,
        reset: Boolean,
    ): ReelsSessionHandle

    /**
     * Retrieves the active checkpoint for the session if valid.
     * Excludes skipped, hidden or completed items, but permits qualified-watch-only current items.
     */
    suspend fun active(handle: ReelsSessionHandle): ReelsActiveCheckpoint?

    /**
     * Scans raw ordered IDs starting after [afterOrdinal] up to [limit],
     * mapping to unconsumed and eligible candidates.
     */
    suspend fun window(
        handle: ReelsSessionHandle,
        afterOrdinal: Long,
        limit: Int,
    ): ReelsSessionWindow

    /**
     * Atomically appends candidates to the inbox and the session queue.
     * Validates capacity (all-or-nothing), candidate scope/source against handle,
     * and deduplicates input IDs.
     * Throws [tachiyomi.domain.reels.anime.model.ReelsInboxCapacityException] if capacity exceeded.
     */
    suspend fun appendPage(
        handle: ReelsSessionHandle,
        candidates: List<ReelsCandidate>,
    ): Int

    /**
     * Small coalesced write of the active checkpoint columns.
     * Strictly verifies owner nonce and sequence monotonicity.
     */
    suspend fun checkpoint(
        handle: ReelsSessionHandle,
        active: ReelsActiveCheckpoint,
    ): Boolean

    /**
     * Updates candidate availability and retry timestamp.
     */
    suspend fun setAvailability(
        handle: ReelsSessionHandle,
        itemId: String,
        availability: String,
        retryAfter: Date?,
    )

    /**
     * Invalidates the session owner, optionally clearing the checkpoint item.
     */
    suspend fun invalidate(
        handle: ReelsSessionHandle,
        dropCheckpoint: Boolean,
    )

    /**
     * Clears all session and queue rows for the given account scope.
     */
    suspend fun clearScope(scope: String)
}
