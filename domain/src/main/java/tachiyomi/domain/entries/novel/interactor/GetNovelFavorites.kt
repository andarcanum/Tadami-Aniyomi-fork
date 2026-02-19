package tachiyomi.domain.entries.novel.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.repository.NovelRepository

class GetNovelFavorites(
    private val novelRepository: NovelRepository,
) {

    suspend fun await(): List<Novel> {
        return novelRepository.getNovelFavorites()
    }

    fun subscribe(sourceId: Long): Flow<List<Novel>> {
        return novelRepository.getNovelFavoritesBySourceId(sourceId)
    }
}
