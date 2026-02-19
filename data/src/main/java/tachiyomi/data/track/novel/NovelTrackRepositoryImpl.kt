package tachiyomi.data.track.novel

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.domain.track.manga.model.MangaTrack
import tachiyomi.domain.track.manga.repository.MangaTrackRepository
import tachiyomi.domain.track.novel.model.NovelTrack
import tachiyomi.domain.track.novel.repository.NovelTrackRepository

class NovelTrackRepositoryImpl(
    private val mangaTrackRepository: MangaTrackRepository,
) : NovelTrackRepository {

    override suspend fun getTrackByNovelId(id: Long): NovelTrack? {
        return mangaTrackRepository.getTrackByMangaId(id)?.toNovelTrack()
    }

    override suspend fun getTracksByNovelId(novelId: Long): List<NovelTrack> {
        return mangaTrackRepository.getTracksByMangaId(novelId).map(MangaTrack::toNovelTrack)
    }

    override fun getNovelTracksAsFlow(): Flow<List<NovelTrack>> {
        return mangaTrackRepository.getMangaTracksAsFlow().map { tracks ->
            tracks.map(MangaTrack::toNovelTrack)
        }
    }

    override fun getTracksByNovelIdAsFlow(novelId: Long): Flow<List<NovelTrack>> {
        return mangaTrackRepository.getTracksByMangaIdAsFlow(novelId).map { tracks ->
            tracks.map(MangaTrack::toNovelTrack)
        }
    }

    override suspend fun delete(novelId: Long, trackerId: Long) {
        mangaTrackRepository.delete(novelId, trackerId)
    }

    override suspend fun insertNovel(track: NovelTrack) {
        mangaTrackRepository.insertManga(track.toMangaTrack())
    }

    override suspend fun insertAllNovel(tracks: List<NovelTrack>) {
        mangaTrackRepository.insertAllManga(tracks.map(NovelTrack::toMangaTrack))
    }
}

private fun MangaTrack.toNovelTrack(): NovelTrack {
    return NovelTrack(
        id = id,
        novelId = mangaId,
        trackerId = trackerId,
        remoteId = remoteId,
        libraryId = libraryId,
        title = title,
        lastChapterRead = lastChapterRead,
        totalChapters = totalChapters,
        status = status,
        score = score,
        remoteUrl = remoteUrl,
        startDate = startDate,
        finishDate = finishDate,
        private = private,
    )
}

private fun NovelTrack.toMangaTrack(): MangaTrack {
    return MangaTrack(
        id = id,
        mangaId = novelId,
        trackerId = trackerId,
        remoteId = remoteId,
        libraryId = libraryId,
        title = title,
        lastChapterRead = lastChapterRead,
        totalChapters = totalChapters,
        status = status,
        score = score,
        remoteUrl = remoteUrl,
        startDate = startDate,
        finishDate = finishDate,
        private = private,
    )
}
