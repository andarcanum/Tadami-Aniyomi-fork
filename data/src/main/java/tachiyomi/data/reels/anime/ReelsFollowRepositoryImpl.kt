package tachiyomi.data.reels.anime

import android.database.sqlite.SQLiteException
import dataanime.Reels_follows
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.reels.anime.model.ReelsFollow
import tachiyomi.domain.reels.anime.repository.ReelsFollowRepository
import tachiyomi.mi.data.AnimeDatabase as AnimeDb

class ReelsFollowRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : ReelsFollowRepository {

    override fun subscribeAll(): Flow<List<ReelsFollow>> {
        return handler.subscribeToList { db ->
            db.reels_followsQueries.getAll()
        }.map { rows -> rows.map(::mapReelsFollow) }
    }

    override suspend fun getAll(): List<ReelsFollow> {
        return handler.awaitList { db ->
            db.reels_followsQueries.getAll()
        }.map(::mapReelsFollow)
    }

    override suspend fun getBySource(sourceId: Long): List<ReelsFollow> {
        return handler.awaitList { db ->
            db.reels_followsQueries.getBySource(sourceId)
        }.map(::mapReelsFollow)
    }

    override suspend fun getCreatorsBySource(sourceId: Long): List<String> {
        return handler.awaitList { db ->
            db.reels_followsQueries.getCreatorsBySource(sourceId)
        }
    }

    override suspend fun insert(follow: ReelsFollow) {
        // Follow taps are fire-and-forget: DB failures are logged, not surfaced, but a
        // cancellation must never be swallowed (structured concurrency).
        try {
            handler.await { db -> insert(db, follow) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to save reels follow ${follow.creator}" }
        }
    }

    private fun insert(db: AnimeDb, follow: ReelsFollow) {
        db.reels_followsQueries.insert(
            source_id = follow.sourceId,
            creator = follow.creator,
            added_at = follow.addedAt,
        )
    }

    override suspend fun delete(sourceId: Long, creator: String) {
        try {
            handler.await { db ->
                db.reels_followsQueries.delete(sourceId, creator)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to delete reels follow $creator" }
        }
    }

    private fun mapReelsFollow(row: Reels_follows): ReelsFollow {
        return ReelsFollow(
            sourceId = row.source_id,
            creator = row.creator,
            addedAt = row.added_at,
        )
    }
}
