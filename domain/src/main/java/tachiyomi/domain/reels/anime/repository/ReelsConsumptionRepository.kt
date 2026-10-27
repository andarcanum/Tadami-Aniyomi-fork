package tachiyomi.domain.reels.anime.repository

import tachiyomi.domain.reels.anime.model.ReelsConsumptionEntry
import java.util.Date

/**
 * Durable For You interaction summary (host-side, contract-independent).
 *
 * Every write is idempotent and fenced by [historyGeneration]: a writer that captured the
 * generation before a "reset history" must be dropped, otherwise a stale coroutine could
 * resurrect a consumed row after the user cleared the history.
 */
interface ReelsConsumptionRepository {

    /** Current history generation of [scope]; 0 when the scope has never been seen. */
    suspend fun generation(scope: String): Long

    /** Ensures the scope row exists and returns the (possibly fresh) generation. */
    suspend fun ensureScope(scope: String, policyVersion: Int): Long

    /**
     * Records a qualified watch. Returns false when the writer was fenced by a history reset
     * (its generation is stale) — the caller must treat that as "not persisted", never as a
     * success.
     */
    suspend fun markWatched(
        scope: String,
        sourceId: Long,
        itemId: String,
        at: Date,
        surface: String?,
        policyVersion: Int,
        generation: Long,
    ): Boolean

    /** Records a deliberate forward skip (idempotent, never clears a qualified watch). */
    suspend fun markSkipped(
        scope: String,
        sourceId: Long,
        itemId: String,
        at: Date,
        surface: String?,
        policyVersion: Int,
        generation: Long,
    ): Boolean

    /** Batch anti-join for one page: ids of [itemIds] that are already consumed. */
    suspend fun consumedIds(scope: String, sourceId: Long, itemIds: Collection<String>): Set<String>

    suspend fun entry(scope: String, sourceId: Long, itemId: String): ReelsConsumptionEntry?

    suspend fun countConsumed(scope: String, sourceId: Long): Long

    /**
     * Moves the summary rows of [fromScope] into [toScope] for one source.
     *
     * Needed when a source could not prove its account identity on the first generation (the
     * history was written under the fallback scope) and the real identity appeared later:
     * without the move, everything watched before that moment becomes invisible and returns
     * to the feed. Existing target rows win; the source rows are dropped afterwards.
     */
    suspend fun mergeScope(fromScope: String, toScope: String, sourceId: Long)

    /**
     * Transactionally deletes the scope's consumption rows and bumps its history generation.
     * Explicit hidden/blacklist entries live elsewhere and are deliberately untouched.
     *
     * @return the new generation.
     */
    suspend fun resetHistory(scope: String, policyVersion: Int): Long
}
