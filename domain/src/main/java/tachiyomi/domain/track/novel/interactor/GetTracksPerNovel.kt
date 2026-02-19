package tachiyomi.domain.track.novel.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.domain.track.novel.model.NovelTrack
import tachiyomi.domain.track.novel.repository.NovelTrackRepository

class GetTracksPerNovel(
    private val trackRepository: NovelTrackRepository,
) {

    fun subscribe(): Flow<Map<Long, List<NovelTrack>>> {
        return trackRepository.getNovelTracksAsFlow().map { tracks -> tracks.groupBy { it.novelId } }
    }
}
