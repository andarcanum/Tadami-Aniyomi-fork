package tachiyomi.data.reels.anime

import android.database.sqlite.SQLiteException
import dataanime.Reels_candidate
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.reels.anime.model.ReelsCandidate
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
                val queries = db.reels_candidateQueries
                val known = queries
                    .existingIds(scope, sourceId, candidates.map { it.itemId })
                    .executeAsList()
                    .toHashSet()
                var room = (MAX_CANDIDATES_PER_SOURCE - queries.countForSource(scope, sourceId).executeAsOne())
                    .coerceAtLeast(0L)
                var written = 0
                var skipped = 0
                for (candidate in candidates) {
                    val isNew = candidate.itemId !in known
                    if (isNew && room <= 0L) {
                        skipped++
                        continue
                    }
                    if (isNew) room--
                    upsert(queries, candidate)
                    written++
                }
                if (skipped > 0) {
                    logcat(LogPriority.WARN) {
                        "Reels inbox for source $sourceId is at its $MAX_CANDIDATES_PER_SOURCE-row cap; " +
                            "$skipped new candidate(s) not stored (backpressure, nothing evicted)."
                    }
                }
                written
            }
        } catch (e: CancellationException) {
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
            }.map(::mapCandidate)
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

    private fun upsert(queries: dataanime.Reels_candidateQueries, candidate: ReelsCandidate) {
        queries.upsert(
            account_scope_id = candidate.accountScopeId,
            source_id = candidate.sourceId,
            item_id = candidate.itemId,
            title = candidate.title,
            author = candidate.author,
            video_url = candidate.videoUrl,
            video_url_hd = candidate.videoUrlHd,
            poster_url = candidate.posterUrl,
            poster_url_vertical = candidate.posterUrlVertical,
            web_url = candidate.webUrl,
            duration_sec = candidate.durationSec,
            has_audio = candidate.hasAudio,
            created_at_epoch_sec = candidate.createdAtEpochSec,
            discovered_at = candidate.discoveredAt,
            last_validated_at = candidate.lastValidatedAt ?: candidate.discoveredAt,
            availability = candidate.availability,
            retry_after = candidate.retryAfter,
            resume_position_ms = candidate.resumePositionMs,
            metadata_schema_version = candidate.metadataSchemaVersion.toLong(),
        )
    }

    private fun mapCandidate(row: Reels_candidate): ReelsCandidate {
        return ReelsCandidate(
            accountScopeId = row.account_scope_id,
            sourceId = row.source_id,
            itemId = row.item_id,
            title = row.title,
            author = row.author,
            videoUrl = row.video_url,
            videoUrlHd = row.video_url_hd,
            posterUrl = row.poster_url,
            posterUrlVertical = row.poster_url_vertical,
            webUrl = row.web_url,
            durationSec = row.duration_sec,
            hasAudio = row.has_audio,
            createdAtEpochSec = row.created_at_epoch_sec,
            discoveredAt = row.discovered_at,
            lastValidatedAt = row.last_validated_at,
            availability = row.availability,
            retryAfter = row.retry_after,
            resumePositionMs = row.resume_position_ms,
            metadataSchemaVersion = row.metadata_schema_version.toInt(),
        )
    }

    private companion object {
        /**
         * Per (account, source) inbox ceiling. Generous: it only exists so a pathological
         * source cannot grow the DB without bound; reaching it stops ingest, never evicts.
         */
        const val MAX_CANDIDATES_PER_SOURCE = 2_000L
    }
}
