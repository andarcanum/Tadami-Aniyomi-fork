package tachiyomi.data.reels.anime

import android.database.sqlite.SQLiteException
import dataanime.Reels_candidate
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.reels.anime.model.ReelsCandidate
import tachiyomi.domain.reels.anime.model.ReelsInboxCapacityException
import tachiyomi.domain.reels.anime.repository.ReelsCandidateRepository

/**
 * Durable candidate inbox.
 *
 * Ingest is deliberately "grow only": when the per-source cap is reached the inbox stops
 * accepting NEW items instead of evicting pending ones. Silently dropping an unseen candidate
 * because the queue got big is exactly the data loss this table exists to prevent.
 *
 * [ingest] also deliberately **throws** on a storage failure instead of returning 0. The caller
 * publishes the page and advances its cursor only after this call returns, so swallowing the
 * error here would advance the feed past candidates that were never written down — the exact
 * "checkpoint without queue" loss the inbox exists to prevent. A failed ingest must fail the
 * page (which is retried with the same page/cursor), never degrade into a silent gap.
 */
class ReelsCandidateRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : ReelsCandidateRepository {

    override suspend fun ingest(scope: String, sourceId: Long, candidates: List<ReelsCandidate>): Int {
        if (candidates.isEmpty()) return 0
        return try {
            handler.await(inTransaction = true) { db ->
                ReelsCandidateWriter.writeCandidates(db, scope, sourceId, candidates)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ReelsInboxCapacityException) {
            logcat(LogPriority.WARN) {
                "Reels inbox for source $sourceId reached capacity; rejecting ingest."
            }
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to ingest reels candidates for source $sourceId" }
            throw e
        }
    }

    override suspend fun unconsumed(scope: String, sourceId: Long, limit: Int): List<ReelsCandidate> {
        if (limit <= 0) return emptyList()
        return try {
            handler.awaitList { db ->
                db.reels_candidateQueries.unconsumed(scope, sourceId, limit.toLong())
            }.map(ReelsCandidateWriter::mapCandidate)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to read reels inbox for source $sourceId" }
            emptyList()
        }
    }

    override suspend fun saveResumePosition(scope: String, sourceId: Long, itemId: String, positionMs: Long) {
        try {
            handler.await { db ->
                db.reels_candidateQueries.saveResumePosition(
                    positionMs = positionMs,
                    scope = scope,
                    sourceId = sourceId,
                    itemId = itemId,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to save reels resume position for $itemId" }
        }
    }

    override suspend fun remove(scope: String, sourceId: Long, itemIds: Collection<String>) {
        if (itemIds.isEmpty()) return
        try {
            handler.await { db ->
                db.reels_candidateQueries.remove(scope, sourceId, itemIds)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to remove consumed reels candidates" }
        }
    }

    override suspend fun count(scope: String, sourceId: Long): Long {
        return handler.awaitOne { db ->
            db.reels_candidateQueries.countForSource(scope, sourceId)
        }
    }

    override suspend fun clear(scope: String) {
        try {
            handler.await { db ->
                db.reels_candidateQueries.deleteForScope(scope)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to clear the reels inbox for $scope" }
        }
    }
}
