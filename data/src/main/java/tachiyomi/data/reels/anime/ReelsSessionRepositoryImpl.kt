package tachiyomi.data.reels.anime

import android.database.sqlite.SQLiteException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.reels.anime.model.ReelsActiveCheckpoint
import tachiyomi.domain.reels.anime.model.ReelsCandidate
import tachiyomi.domain.reels.anime.model.ReelsQueuedCandidate
import tachiyomi.domain.reels.anime.model.ReelsSessionHandle
import tachiyomi.domain.reels.anime.model.ReelsSessionKey
import tachiyomi.domain.reels.anime.model.ReelsSessionStaleException
import tachiyomi.domain.reels.anime.model.ReelsSessionWindow
import tachiyomi.domain.reels.anime.repository.ReelsSessionRepository
import java.util.Date
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

class ReelsSessionRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : ReelsSessionRepository {

    override suspend fun open(
        key: ReelsSessionKey,
        ownerNonce: String,
        historyGeneration: Long,
        reset: Boolean,
    ): ReelsSessionHandle {
        return handler.await(inTransaction = true) { db ->
            val existing = db.reels_sessionQueries.getSession(key.accountScopeId, key.sourceId).executeAsOneOrNull()

            // Sanitize transport hints for this capable scope/source
            db.reels_candidateQueries.clearTransportHintsForScope(key.accountScopeId, key.sourceId)

            val canReuse = !reset && existing != null &&
                existing.context_key == key.contextKey &&
                existing.history_generation == historyGeneration

            if (canReuse && existing != null) {
                db.reels_sessionQueries.updateOwnerNonce(
                    ownerNonce = ownerNonce,
                    updatedAtMs = System.currentTimeMillis(),
                    scope = key.accountScopeId,
                    sourceId = key.sourceId,
                )
                ReelsSessionHandle(
                    key = key,
                    sessionId = existing.session_id,
                    ownerNonce = ownerNonce,
                    historyGeneration = historyGeneration,
                )
            } else {
                val newSessionId = UUID.randomUUID().toString()
                // Explicitly delete child queue rows to avoid orphans regardless of foreign_keys pragma
                db.reels_session_queueQueries.deleteForSource(key.accountScopeId, key.sourceId)

                val nowMs = System.currentTimeMillis()
                db.reels_sessionQueries.claimSession(
                    account_scope_id = key.accountScopeId,
                    source_id = key.sourceId,
                    context_key = key.contextKey,
                    session_id = newSessionId,
                    owner_nonce = ownerNonce,
                    history_generation = historyGeneration,
                    checkpoint_sequence = 0L,
                    active_item_id = null,
                    active_position_ms = 0L,
                    active_completed = 0L,
                    active_title = null,
                    active_author = null,
                    active_duration_sec = null,
                    active_has_audio = 0L,
                    active_tags_json = "[]",
                    active_web_url = null,
                    active_created_at_epoch_sec = null,
                    active_views_count = null,
                    active_likes_count = null,
                    active_is_author_verified = 0L,
                    active_metadata_schema_version = 2L,
                    updated_at_ms = nowMs,
                )

                ReelsSessionHandle(
                    key = key,
                    sessionId = newSessionId,
                    ownerNonce = ownerNonce,
                    historyGeneration = historyGeneration,
                )
            }
        }
    }

    override suspend fun active(handle: ReelsSessionHandle): ReelsActiveCheckpoint? {
        return handler.await { db ->
            val session = db.reels_sessionQueries.getSession(handle.key.accountScopeId, handle.key.sourceId)
                .executeAsOneOrNull() ?: return@await null

            if (session.session_id != handle.sessionId || session.history_generation != handle.historyGeneration) {
                return@await null
            }

            val activeItemId = session.active_item_id ?: return@await null

            // If marked completed, exclude it
            if (session.active_completed != 0L) {
                return@await null
            }

            // Exclude if skipped forward in consumption
            val consumption = db.reels_consumptionQueries.getByItem(
                handle.key.accountScopeId,
                handle.key.sourceId,
                activeItemId,
            )
                .executeAsOneOrNull()
            if (consumption?.skipped_forward_at != null) {
                return@await null
            }

            // Exclude if hidden
            val isHidden =
                db.reels_hiddenQueries.isHidden(handle.key.sourceId, activeItemId, session.active_author ?: "")
                    .executeAsOne() > 0
            if (isHidden) {
                return@await null
            }

            val candidate = ReelsCandidate(
                accountScopeId = handle.key.accountScopeId,
                sourceId = handle.key.sourceId,
                itemId = activeItemId,
                title = session.active_title,
                author = session.active_author,
                videoUrl = "",
                videoUrlHd = null,
                posterUrl = null,
                posterUrlVertical = null,
                webUrl = session.active_web_url,
                durationSec = session.active_duration_sec,
                hasAudio = session.active_has_audio != 0L,
                createdAtEpochSec = session.active_created_at_epoch_sec,
                discoveredAt = Date(session.updated_at_ms),
                lastValidatedAt = Date(session.updated_at_ms),
                availability = ReelsCandidate.AVAILABILITY_AVAILABLE,
                retryAfter = null,
                resumePositionMs = session.active_position_ms,
                metadataSchemaVersion = session.active_metadata_schema_version.toInt(),
                tagsJson = session.active_tags_json,
                viewsCount = session.active_views_count,
                likesCount = session.active_likes_count,
                isAuthorVerified = session.active_is_author_verified != 0L,
                capturedContextKey = session.context_key,
            )

            ReelsActiveCheckpoint(
                item = candidate,
                positionMs = session.active_position_ms,
                completed = false,
                sequence = session.checkpoint_sequence,
            )
        }
    }

    override suspend fun window(
        handle: ReelsSessionHandle,
        afterOrdinal: Long,
        limit: Int,
    ): ReelsSessionWindow {
        if (limit <= 0) {
            return ReelsSessionWindow(
                entries = emptyList(),
                nextOrdinal = afterOrdinal,
                hasMore = false,
                deferredCount = 0,
            )
        }

        return handler.await { db ->
            val rawRows = db.reels_session_queueQueries.rawQueueWindow(
                scope = handle.key.accountScopeId,
                sourceId = handle.key.sourceId,
                sessionId = handle.sessionId,
                afterOrdinal = afterOrdinal,
                limit = limit.toLong(),
            ).executeAsList()

            if (rawRows.isEmpty()) {
                return@await ReelsSessionWindow(
                    entries = emptyList(),
                    nextOrdinal = afterOrdinal,
                    hasMore = false,
                    deferredCount = 0,
                )
            }

            val nextOrdinal = rawRows.last().ordinal
            val hasMore = rawRows.size == limit

            var deferredCount = 0
            val entries = ArrayList<ReelsQueuedCandidate>(rawRows.size)

            for (raw in rawRows) {
                val candidateRow = db.reels_candidateQueries.getByItem(
                    scope = handle.key.accountScopeId,
                    sourceId = handle.key.sourceId,
                    itemId = raw.item_id,
                ).executeAsOneOrNull() ?: continue

                if (candidateRow.availability == ReelsCandidate.AVAILABILITY_DEFERRED) {
                    deferredCount++
                }

                // Check consumption (watched or skipped forward)
                val consumption = db.reels_consumptionQueries.getByItem(
                    handle.key.accountScopeId,
                    handle.key.sourceId,
                    raw.item_id,
                ).executeAsOneOrNull()

                if (consumption?.watched_qualified_at != null || consumption?.skipped_forward_at != null) {
                    continue
                }

                // Check hidden
                val isHidden = db.reels_hiddenQueries.isHidden(
                    sourceId = handle.key.sourceId,
                    itemId = raw.item_id,
                    author = candidateRow.author ?: "",
                ).executeAsOne() > 0
                if (isHidden) {
                    continue
                }

                val candidate = ReelsCandidateWriter.mapCandidate(candidateRow)
                entries.add(ReelsQueuedCandidate(ordinal = raw.ordinal, candidate = candidate))
            }

            ReelsSessionWindow(
                entries = entries,
                nextOrdinal = nextOrdinal,
                hasMore = hasMore,
                deferredCount = deferredCount,
            )
        }
    }

    override suspend fun appendPage(
        handle: ReelsSessionHandle,
        candidates: List<ReelsCandidate>,
    ): Int {
        if (candidates.isEmpty()) return 0

        return handler.await(inTransaction = true) { db ->
            val session = db.reels_sessionQueries.getSession(handle.key.accountScopeId, handle.key.sourceId)
                .executeAsOneOrNull()

            if (session == null || session.session_id != handle.sessionId ||
                session.history_generation != handle.historyGeneration
            ) {
                throw ReelsSessionStaleException()
            }
            if (session.owner_nonce != handle.ownerNonce) {
                throw ReelsSessionStaleException()
            }

            // Drop incoming items already consumed or explicitly hidden so the session queue
            // never resurrects them for this page (checked inside the append transaction).
            val distinct = candidates.distinctBy { it.itemId }
            val consumed = db.reels_consumptionQueries.consumedIds(
                scope = handle.key.accountScopeId,
                sourceId = handle.key.sourceId,
                itemIds = distinct.map { it.itemId },
            ).executeAsList().toHashSet()
            val hidden = db.reels_hiddenQueries.getBySource(handle.key.sourceId).executeAsList()
            val hiddenItems = hidden.filter { it.kind == "item" }.mapTo(HashSet()) { it.hidden_value }
            val hiddenAuthors = hidden.filter { it.kind == "author" }.mapTo(HashSet()) { it.hidden_value }
            val eligible = distinct.filter { candidate ->
                candidate.itemId !in consumed &&
                    candidate.itemId !in hiddenItems &&
                    (candidate.author ?: "") !in hiddenAuthors
            }
            if (eligible.isEmpty()) return@await 0

            // Write candidates with capacity check and remnant pruning
            val written = ReelsCandidateWriter.writeCandidates(
                db = db,
                scope = handle.key.accountScopeId,
                sourceId = handle.key.sourceId,
                candidates = eligible,
            )

            // Append to queue with contiguous ordinals
            var currentMax = db.reels_session_queueQueries.maxOrdinal(
                scope = handle.key.accountScopeId,
                sourceId = handle.key.sourceId,
                sessionId = handle.sessionId,
            ).executeAsOne().MAX ?: 0L

            for (c in eligible) {
                currentMax++
                db.reels_session_queueQueries.append(
                    account_scope_id = handle.key.accountScopeId,
                    source_id = handle.key.sourceId,
                    session_id = handle.sessionId,
                    item_id = c.itemId,
                    ordinal = currentMax,
                )
            }

            written
        }
    }

    override suspend fun checkpoint(
        handle: ReelsSessionHandle,
        active: ReelsActiveCheckpoint,
    ): Boolean {
        return try {
            handler.await(inTransaction = true) { db ->
                val previous = db.reels_sessionQueries.getSession(
                    handle.key.accountScopeId,
                    handle.key.sourceId,
                ).executeAsOneOrNull() ?: return@await false
                if (previous.session_id != handle.sessionId ||
                    previous.owner_nonce != handle.ownerNonce ||
                    previous.history_generation != handle.historyGeneration ||
                    previous.checkpoint_sequence >= active.sequence ||
                    active.item.accountScopeId != handle.key.accountScopeId ||
                    active.item.sourceId != handle.key.sourceId
                ) {
                    return@await false
                }
                val nowMs = System.currentTimeMillis()
                db.reels_sessionQueries.checkpoint(
                    sequence = active.sequence,
                    itemId = active.item.itemId,
                    positionMs = active.positionMs,
                    completed = if (active.completed) 1L else 0L,
                    title = active.item.title,
                    author = active.item.author,
                    durationSec = active.item.durationSec,
                    hasAudio = if (active.item.hasAudio) 1L else 0L,
                    tagsJson = active.item.tagsJson,
                    webUrl = active.item.webUrl,
                    createdAtEpochSec = active.item.createdAtEpochSec,
                    viewsCount = active.item.viewsCount,
                    likesCount = active.item.likesCount,
                    isAuthorVerified = if (active.item.isAuthorVerified) 1L else 0L,
                    metadataSchemaVersion = active.item.metadataSchemaVersion.toLong(),
                    updatedAtMs = nowMs,
                    scope = handle.key.accountScopeId,
                    sourceId = handle.key.sourceId,
                    ownerNonce = handle.ownerNonce,
                )
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to write reels session checkpoint" }
            false
        }
    }

    override suspend fun setAvailability(
        handle: ReelsSessionHandle,
        itemId: String,
        availability: String,
        retryAfter: Date?,
    ) {
        try {
            handler.await(inTransaction = true) { db ->
                val session = db.reels_sessionQueries.getSession(
                    handle.key.accountScopeId,
                    handle.key.sourceId,
                ).executeAsOneOrNull()
                if (session == null || session.session_id != handle.sessionId ||
                    session.owner_nonce != handle.ownerNonce ||
                    session.history_generation != handle.historyGeneration
                ) {
                    throw ReelsSessionStaleException()
                }
                db.reels_candidateQueries.updateAvailability(
                    availability = availability,
                    retryAfter = retryAfter,
                    scope = handle.key.accountScopeId,
                    sourceId = handle.key.sourceId,
                    itemId = itemId,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to update availability for $itemId" }
        }
    }

    override suspend fun invalidate(
        handle: ReelsSessionHandle,
        dropCheckpoint: Boolean,
    ) {
        try {
            handler.await(inTransaction = true) { db ->
                val current = db.reels_sessionQueries.getSession(
                    handle.key.accountScopeId,
                    handle.key.sourceId,
                ).executeAsOneOrNull()
                if (current?.session_id != handle.sessionId || current.owner_nonce != handle.ownerNonce ||
                    current.history_generation != handle.historyGeneration
                ) {
                    return@await
                }
                db.reels_sessionQueries.invalidate(
                    dropCheckpoint = dropCheckpoint,
                    scope = handle.key.accountScopeId,
                    sourceId = handle.key.sourceId,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to invalidate session for ${handle.key.accountScopeId}" }
        }
    }

    override suspend fun clearScope(scope: String) {
        try {
            handler.await(inTransaction = true) { db ->
                db.reels_session_queueQueries.deleteForScope(scope)
                db.reels_sessionQueries.deleteForScope(scope)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to clear reels session scope for $scope" }
        }
    }
}
