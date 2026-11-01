package tachiyomi.data.reels.anime

import tachiyomi.domain.reels.anime.model.ReelsSessionHandle
import tachiyomi.domain.reels.anime.model.ReelsSessionKey
import tachiyomi.domain.reels.anime.model.ReelsSessionStaleException
import tachiyomi.mi.data.AnimeDatabase
import java.util.UUID

/**
 * Transaction-body helpers shared by the legacy session repository and the round-robin
 * repository. None of these functions may perform suspend/network work: callers already own a
 * SQLDelight transaction.
 *
 * Protection lifetime contract (T3): the show-watched protection in
 * `reels_candidate.pruneConsumedRemnants` / `remove` is keyed to an existing `reels_rr_state`
 * row whose `context_key` matches the session row and whose owner is active. Round-robin mode
 * must claim its dedicated context key (`storageKey + ':rr:1'`); legacy or any other feed mode
 * must never claim that key. Claiming with a different [ReelsSessionKey.contextKey] rewrites
 * the session context and therefore drops the protection - it never inherits it. History
 * reset, account switch and mode switch must delete the scoped RR rows
 * (`reels_rr_item` -> `reels_rr_source` -> `reels_rr_state`) in the same transaction that
 * changes the generation or clears the inbox (owned by T4/T7).
 */
internal object ReelsRoundRobinStorage {

    /**
     * Claims or reuses the session header for [key]. With [preserveCheckpoint] a replacement
     * session keeps the previous active metadata/position under the new owner (migration/restore
     * only); otherwise a replacement starts empty, exactly like the legacy open path.
     */
    fun claimSession(
        db: AnimeDatabase,
        key: ReelsSessionKey,
        ownerNonce: String,
        historyGeneration: Long,
        reset: Boolean,
        preserveCheckpoint: Boolean,
        nowMs: Long,
    ): ReelsSessionHandle {
        val queries = db.reels_sessionQueries
        val existing = queries.getSession(key.accountScopeId, key.sourceId).executeAsOneOrNull()

        // Sanitize transport hints for this capable scope/source
        db.reels_candidateQueries.clearTransportHintsForScope(key.accountScopeId, key.sourceId)

        if (!reset && existing != null &&
            existing.context_key == key.contextKey &&
            existing.history_generation == historyGeneration
        ) {
            queries.updateOwnerNonce(
                ownerNonce = ownerNonce,
                updatedAtMs = nowMs,
                scope = key.accountScopeId,
                sourceId = key.sourceId,
            )
            return ReelsSessionHandle(
                key = key,
                sessionId = existing.session_id,
                ownerNonce = ownerNonce,
                historyGeneration = historyGeneration,
            )
        }

        val newSessionId = UUID.randomUUID().toString()
        // Explicitly delete child queue rows to avoid orphans regardless of foreign_keys pragma
        db.reels_session_queueQueries.deleteForSource(key.accountScopeId, key.sourceId)

        val preserved = if (preserveCheckpoint) existing else null
        queries.claimSession(
            account_scope_id = key.accountScopeId,
            source_id = key.sourceId,
            context_key = key.contextKey,
            session_id = newSessionId,
            owner_nonce = ownerNonce,
            history_generation = historyGeneration,
            checkpoint_sequence = 0L,
            active_item_id = preserved?.active_item_id,
            active_position_ms = preserved?.active_position_ms ?: 0L,
            active_completed = preserved?.active_completed ?: 0L,
            active_title = preserved?.active_title,
            active_author = preserved?.active_author,
            active_duration_sec = preserved?.active_duration_sec,
            active_has_audio = preserved?.active_has_audio ?: 0L,
            active_tags_json = preserved?.active_tags_json ?: "[]",
            active_web_url = preserved?.active_web_url,
            active_created_at_epoch_sec = preserved?.active_created_at_epoch_sec,
            active_views_count = preserved?.active_views_count,
            active_likes_count = preserved?.active_likes_count,
            active_is_author_verified = preserved?.active_is_author_verified ?: 0L,
            active_metadata_schema_version = preserved?.active_metadata_schema_version ?: 2L,
            updated_at_ms = nowMs,
        )

        return ReelsSessionHandle(
            key = key,
            sessionId = newSessionId,
            ownerNonce = ownerNonce,
            historyGeneration = historyGeneration,
        )
    }

    /** Fails with a stale handle unless session/owner/generation/context all still match. */
    fun requireOwner(db: AnimeDatabase, handle: ReelsSessionHandle) {
        val session = db.reels_sessionQueries.getSession(
            handle.key.accountScopeId,
            handle.key.sourceId,
        ).executeAsOneOrNull() ?: throw ReelsSessionStaleException()

        if (session.session_id != handle.sessionId ||
            session.owner_nonce != handle.ownerNonce ||
            session.history_generation != handle.historyGeneration ||
            session.context_key != handle.key.contextKey
        ) {
            throw ReelsSessionStaleException()
        }
    }

    fun clearScope(db: AnimeDatabase, scope: String) {
        db.reels_rr_itemQueries.deleteForScope(scope)
        db.reels_rr_sourceQueries.deleteForScope(scope)
        db.reels_rr_stateQueries.deleteForScope(scope)
        db.reels_session_queueQueries.deleteForScope(scope)
        db.reels_sessionQueries.deleteForScope(scope)
    }

    fun clearSource(db: AnimeDatabase, scope: String, sourceId: Long) {
        resetProjection(db, scope, sourceId)
        db.reels_session_queueQueries.deleteForSource(scope, sourceId)
        db.reels_sessionQueries.deleteForSource(scope, sourceId)
    }

    /**
     * Projection-only reset used by the corrupt-state rebuild. The session row (and therefore
     * the resume checkpoint) and the candidate inbox/history are deliberately untouched.
     */
    fun resetProjection(db: AnimeDatabase, scope: String, sourceId: Long) {
        db.reels_rr_itemQueries.deleteForSource(scope, sourceId)
        db.reels_rr_sourceQueries.deleteForSource(scope, sourceId)
        db.reels_rr_stateQueries.deleteForSource(scope, sourceId)
    }
}
