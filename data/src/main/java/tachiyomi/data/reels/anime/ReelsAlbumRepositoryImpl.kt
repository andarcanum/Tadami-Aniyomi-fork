package tachiyomi.data.reels.anime

import android.database.sqlite.SQLiteException
import dataanime.Reels_albums
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.reels.anime.model.ReelsAlbum
import tachiyomi.domain.reels.anime.repository.ReelsAlbumRepository
import java.util.Date
import tachiyomi.mi.data.AnimeDatabase as AnimeDb

class ReelsAlbumRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : ReelsAlbumRepository {

    override fun subscribeAll(): Flow<List<ReelsAlbum>> {
        return handler.subscribeToList { db ->
            db.reels_albumsQueries.getAllAlbums()
        }.map { rows -> rows.map(::mapAlbum) }
    }

    override suspend fun getAll(): List<ReelsAlbum> {
        return handler.awaitList { db ->
            db.reels_albumsQueries.getAllAlbums()
        }.map(::mapAlbum)
    }

    override fun subscribeBySource(sourceId: Long): Flow<List<ReelsAlbum>> {
        return handler.subscribeToList { db ->
            db.reels_albumsQueries.getAlbumsBySource(sourceId)
        }.map { rows -> rows.map(::mapAlbum) }
    }

    override suspend fun getBySource(sourceId: Long): List<ReelsAlbum> {
        return handler.awaitList { db ->
            db.reels_albumsQueries.getAlbumsBySource(sourceId)
        }.map(::mapAlbum)
    }

    override suspend fun insert(album: ReelsAlbum) {
        try {
            handler.await { db -> insert(db, album) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to save reels album ${album.albumId}" }
        }
    }

    override suspend fun delete(sourceId: Long, albumId: String) {
        try {
            handler.await { db ->
                // Watched marks stay: they are keyed by (source, video) without an album ref,
                // so re-adding the album later restores its marks; deleteBySource cleans them.
                db.reels_albumsQueries.deleteAlbum(sourceId, albumId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to delete reels album $albumId" }
        }
    }

    override suspend fun deleteBySource(sourceId: Long) {
        try {
            handler.await { db ->
                db.reels_albumsQueries.deleteAlbumsBySource(sourceId)
                db.reels_albumsQueries.deleteWatchedBySource(sourceId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to delete reels albums of source $sourceId" }
        }
    }

    override fun subscribeWatched(sourceId: Long): Flow<Set<String>> {
        return handler.subscribeToList { db ->
            db.reels_albumsQueries.getWatchedBySource(sourceId)
        }.map { rows -> rows.map { it.video_id }.toSet() }
    }

    override suspend fun markWatched(sourceId: Long, videoId: String) {
        try {
            handler.await { db ->
                db.reels_albumsQueries.insertWatched(sourceId, videoId, Date())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to mark reels album item $videoId watched" }
        }
    }

    override suspend fun unmarkWatched(sourceId: Long, videoId: String) {
        try {
            handler.await { db ->
                db.reels_albumsQueries.deleteWatched(sourceId, videoId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to unmark reels album item $videoId" }
        }
    }

    override suspend fun deleteWatchedBySource(sourceId: Long) {
        try {
            handler.await { db ->
                db.reels_albumsQueries.deleteWatchedBySource(sourceId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to clear watched marks of source $sourceId" }
        }
    }

    private fun insert(db: AnimeDb, album: ReelsAlbum) {
        db.reels_albumsQueries.insertAlbum(
            source_id = album.sourceId,
            album_id = album.albumId,
            name = album.name,
            cover_url = album.coverUrl,
            added_at = album.addedAt,
        )
    }

    private fun mapAlbum(row: Reels_albums): ReelsAlbum {
        return ReelsAlbum(
            sourceId = row.source_id,
            albumId = row.album_id,
            name = row.name,
            coverUrl = row.cover_url,
            addedAt = row.added_at,
        )
    }
}
