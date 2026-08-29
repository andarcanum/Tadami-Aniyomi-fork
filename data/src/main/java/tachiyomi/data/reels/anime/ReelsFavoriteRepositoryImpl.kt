package tachiyomi.data.reels.anime

import android.database.sqlite.SQLiteException
import dataanime.Reels_favorites
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.reels.anime.model.ReelsFavorite
import tachiyomi.domain.reels.anime.repository.ReelsFavoriteRepository
import tachiyomi.mi.data.AnimeDatabase as AnimeDb

class ReelsFavoriteRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : ReelsFavoriteRepository {

    override fun subscribeAll(): Flow<List<ReelsFavorite>> {
        return handler.subscribeToList { db ->
            db.reels_favoritesQueries.getAll()
        }.map { rows -> rows.map(::mapReelsFavorite) }
    }

    override suspend fun getAll(): List<ReelsFavorite> {
        return handler.awaitList { db ->
            db.reels_favoritesQueries.getAll()
        }.map(::mapReelsFavorite)
    }

    override suspend fun getBySource(sourceId: Long): List<ReelsFavorite> {
        return handler.awaitList { db ->
            db.reels_favoritesQueries.getBySource(sourceId)
        }.map(::mapReelsFavorite)
    }

    override suspend fun getIdsBySource(sourceId: Long): List<String> {
        return handler.awaitList { db ->
            db.reels_favoritesQueries.getIdsBySource(sourceId)
        }
    }

    override suspend fun insert(favorite: ReelsFavorite) {
        // Like/unlike taps are fire-and-forget: DB failures are logged, not surfaced, but a
        // cancellation must never be swallowed or the caller's scope keeps "running" after
        // being cancelled (broken structured concurrency).
        try {
            handler.await { db -> insert(db, favorite) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to save reels favorite ${favorite.videoId}" }
        }
    }

    override suspend fun insertAll(favorites: List<ReelsFavorite>) {
        if (favorites.isEmpty()) return
        try {
            // One transaction: backup restore of a large favorites list must not pay a
            // transaction per row.
            handler.await(inTransaction = true) { db ->
                favorites.forEach { favorite -> insert(db, favorite) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to save ${favorites.size} reels favorites" }
        }
    }

    override suspend fun delete(videoId: String, sourceId: Long) {
        try {
            handler.await { db ->
                db.reels_favoritesQueries.delete(videoId, sourceId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to delete reels favorite $videoId" }
        }
    }

    private fun insert(db: AnimeDb, favorite: ReelsFavorite) {
        db.reels_favoritesQueries.insert(
            video_id = favorite.videoId,
            source_id = favorite.sourceId,
            title = favorite.title,
            author = favorite.author,
            video_url = favorite.videoUrl,
            video_url_hd = favorite.videoUrlHd,
            poster_url = favorite.posterUrl,
            poster_url_vertical = favorite.posterUrlVertical,
            web_url = favorite.webUrl,
            duration_sec = favorite.durationSec,
            has_audio = if (favorite.hasAudio) 1L else 0L,
            added_at = favorite.addedAt,
        )
    }

    private fun mapReelsFavorite(row: Reels_favorites): ReelsFavorite {
        return ReelsFavorite(
            videoId = row.video_id,
            sourceId = row.source_id,
            title = row.title,
            author = row.author,
            videoUrl = row.video_url,
            videoUrlHd = row.video_url_hd,
            posterUrl = row.poster_url,
            posterUrlVertical = row.poster_url_vertical,
            webUrl = row.web_url,
            durationSec = row.duration_sec,
            hasAudio = row.has_audio != 0L,
            addedAt = row.added_at,
        )
    }
}
