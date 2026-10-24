package tachiyomi.domain.reels.anime.repository

import tachiyomi.domain.reels.anime.model.ReelsCandidate

/**
 * Durable candidate inbox (host-side). Receiving a page is not a user interaction: rows here
 * are only "discovered", never "watched". The unconsumed backlog survives process death and
 * source-cursor loss, which is what makes the "20 delivered / 10 handled / restart" contract
 * hold even when the plugin's in-memory feed session is gone.
 */
interface ReelsCandidateRepository {

    /**
     * Upserts a page of candidates. Never resurrects a consumed item (the anti-join in
     * [unconsumed] is authoritative) and never evicts unconsumed rows: when the per-source
     * cap is reached the call stops growing the inbox (backpressure) instead of silently
     * dropping pending candidates.
     *
     * @return number of rows actually inserted/updated.
     */
    suspend fun ingest(scope: String, sourceId: Long, candidates: List<ReelsCandidate>): Int

    /** Unseen, still-available backlog, newest first. */
    suspend fun unconsumed(scope: String, sourceId: Long, limit: Int): List<ReelsCandidate>

    suspend fun saveResumePosition(scope: String, sourceId: Long, itemId: String, positionMs: Long)

    /** Drops rows whose consumption was durably committed (the summary is authoritative). */
    suspend fun remove(scope: String, sourceId: Long, itemIds: Collection<String>)

    suspend fun count(scope: String, sourceId: Long): Long

    suspend fun clear(scope: String)
}
