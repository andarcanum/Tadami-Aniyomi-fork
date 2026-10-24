package tachiyomi.data.reels.anime

import android.database.sqlite.SQLiteException
import dataanime.Reels_consumption
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.reels.anime.model.ReelsConsumptionEntry
import tachiyomi.domain.reels.anime.repository.ReelsConsumptionRepository
import java.util.Date
import tachiyomi.mi.data.AnimeDatabase as AnimeDb

/**
 * Durable For You interaction summary.
 *
 * Every mutation runs in a transaction that first re-reads the scope's history generation:
 * a writer which captured the generation before a "reset history" is dropped instead of
 * re-inserting the row the reset just deleted. DB failures are logged, not thrown into the
 * player — but a failed write is never reported as persisted (the caller gets `false`).
 */
class ReelsConsumptionRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : ReelsConsumptionRepository {

    override suspend fun generation(scope: String): Long {
        return handler.awaitOneOrNull { db ->
            db.reels_for_you_stateQueries.getGeneration(scope)
        } ?: 0L
    }

    override suspend fun ensureScope(scope: String, policyVersion: Int): Long {
        return try {
            handler.await(inTransaction = true) { db ->
                db.reels_for_you_stateQueries.ensureState(scope, policyVersion.toLong())
                db.reels_for_you_stateQueries.getGeneration(scope).executeAsOne()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to ensure reels For You scope $scope" }
            0L
        }
    }

    override suspend fun markWatched(
        scope: String,
        sourceId: Long,
        itemId: String,
        at: Date,
        surface: String?,
        policyVersion: Int,
        generation: Long,
    ): Boolean {
        return write(scope, generation, itemId) { db ->
            db.reels_consumptionQueries.markWatched(
                account_scope_id = scope,
                source_id = sourceId,
                item_id = itemId,
                watched_qualified_at = at,
                last_action_at = at,
                last_surface = surface,
                policy_version = policyVersion.toLong(),
                history_generation = generation,
            )
        }
    }

    override suspend fun markSkipped(
        scope: String,
        sourceId: Long,
        itemId: String,
        at: Date,
        surface: String?,
        policyVersion: Int,
        generation: Long,
    ): Boolean {
        return write(scope, generation, itemId) { db ->
            db.reels_consumptionQueries.markSkipped(
                account_scope_id = scope,
                source_id = sourceId,
                item_id = itemId,
                skipped_forward_at = at,
                last_action_at = at,
                last_surface = surface,
                policy_version = policyVersion.toLong(),
                history_generation = generation,
            )
        }
    }

    override suspend fun consumedIds(
        scope: String,
        sourceId: Long,
        itemIds: Collection<String>,
    ): Set<String> {
        if (itemIds.isEmpty()) return emptySet()
        return try {
            handler.awaitList { db ->
                db.reels_consumptionQueries.consumedIds(scope, sourceId, itemIds)
            }.toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            // A failed read must not silently mark everything as unseen AND must not crash the
            // feed: returning the empty set degrades to "no filtering this page".
            logcat(LogPriority.ERROR, throwable = e) { "Failed to read reels consumption for source $sourceId" }
            emptySet()
        }
    }

    override suspend fun entry(scope: String, sourceId: Long, itemId: String): ReelsConsumptionEntry? {
        return handler.awaitOneOrNull { db ->
            db.reels_consumptionQueries.getByItem(scope, sourceId, itemId)
        }?.let(::mapEntry)
    }

    override suspend fun countConsumed(scope: String, sourceId: Long): Long {
        return handler.awaitOne { db ->
            db.reels_consumptionQueries.countConsumed(scope, sourceId)
        }
    }

    override suspend fun resetHistory(scope: String, policyVersion: Int): Long {
        return try {
            handler.await(inTransaction = true) { db ->
                db.reels_consumptionQueries.deleteForScope(scope)
                db.reels_for_you_stateQueries.bumpGeneration(scope, policyVersion.toLong())
                db.reels_for_you_stateQueries.getGeneration(scope).executeAsOne()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to reset reels For You history for $scope" }
            generation(scope)
        }
    }

    /**
     * Generation-fenced write. Returns false on a stale generation or a DB failure, so the
     * caller can retry instead of pretending the interaction was recorded.
     */
    private suspend fun write(
        scope: String,
        generation: Long,
        itemId: String,
        block: (AnimeDb) -> Unit,
    ): Boolean {
        return try {
            handler.await(inTransaction = true) { db ->
                val current = db.reels_for_you_stateQueries.getGeneration(scope).executeAsOneOrNull()
                if (current != null && current != generation) {
                    false
                } else {
                    block(db)
                    true
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to record reels interaction for $itemId" }
            false
        }
    }

    private fun mapEntry(row: Reels_consumption): ReelsConsumptionEntry {
        return ReelsConsumptionEntry(
            accountScopeId = row.account_scope_id,
            sourceId = row.source_id,
            itemId = row.item_id,
            watchedQualifiedAt = row.watched_qualified_at,
            skippedForwardAt = row.skipped_forward_at,
            lastActionAt = row.last_action_at,
            lastSurface = row.last_surface,
            policyVersion = row.policy_version.toInt(),
            historyGeneration = row.history_generation,
        )
    }
}
