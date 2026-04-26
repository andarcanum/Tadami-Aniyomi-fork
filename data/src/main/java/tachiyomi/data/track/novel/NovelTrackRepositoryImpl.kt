package tachiyomi.data.track.novel

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.track.novel.model.NovelTrack
import tachiyomi.domain.track.novel.repository.NovelTrackRepository

class NovelTrackRepositoryImpl(
    private val handler: NovelDatabaseHandler,
) : NovelTrackRepository {

    override suspend fun getTrackByNovelId(id: Long): NovelTrack? {
        return handler.awaitOneOrNull { db -> db.novel_syncQueries.getTrackById(id, NovelTrackMapper::mapTrack) }
    }

    override suspend fun getTracksByNovelId(novelId: Long): List<NovelTrack> {
        return handler.awaitList { db ->
            db.novel_syncQueries.getTracksByNovelId(novelId, NovelTrackMapper::mapTrack)
        }
    }

    override fun getNovelTracksAsFlow(): Flow<List<NovelTrack>> {
        return handler.subscribeToList { db -> db.novel_syncQueries.getTracks(NovelTrackMapper::mapTrack) }
    }

    override fun getTracksByNovelIdAsFlow(novelId: Long): Flow<List<NovelTrack>> {
        return handler.subscribeToList { db ->
            db.novel_syncQueries.getTracksByNovelId(novelId, NovelTrackMapper::mapTrack)
        }
    }

    override suspend fun delete(novelId: Long, trackerId: Long) {
        handler.await { db ->
            db.novel_syncQueries.delete(
                novelId = novelId,
                syncId = trackerId,
            )
        }
    }

    override suspend fun insertNovel(track: NovelTrack) {
        insertValues(track)
    }

    override suspend fun insertAllNovel(tracks: List<NovelTrack>) {
        insertValues(*tracks.toTypedArray())
    }

    private suspend fun insertValues(vararg tracks: NovelTrack) {
        handler.await(inTransaction = true) { db ->
            tracks.forEach { novelTrack ->
                db.novel_syncQueries.insert(
                    novelId = novelTrack.novelId,
                    syncId = novelTrack.trackerId,
                    remoteId = novelTrack.remoteId,
                    libraryId = novelTrack.libraryId,
                    title = novelTrack.title,
                    lastChapterRead = novelTrack.lastChapterRead,
                    totalChapters = novelTrack.totalChapters,
                    status = novelTrack.status,
                    score = novelTrack.score,
                    remoteUrl = novelTrack.remoteUrl,
                    startDate = novelTrack.startDate,
                    finishDate = novelTrack.finishDate,
                    private = novelTrack.private,
                )
            }
        }
    }
}
