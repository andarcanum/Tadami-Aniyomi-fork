package tachiyomi.domain.entries.novel.interactor

import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.repository.NovelRepository

class GetNovelByUrlAndSourceId(
    private val novelRepository: NovelRepository,
) {
    suspend fun await(url: String, sourceId: Long): Novel? {
        return novelRepository.getNovelByUrlAndSourceId(url, sourceId)
    }
}
