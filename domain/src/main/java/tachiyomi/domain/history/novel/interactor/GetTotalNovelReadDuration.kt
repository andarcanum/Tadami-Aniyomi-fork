package tachiyomi.domain.history.novel.interactor

import tachiyomi.domain.history.novel.repository.NovelHistoryRepository

class GetTotalNovelReadDuration(
    private val repository: NovelHistoryRepository,
) {
    suspend fun await(): Long {
        return repository.getTotalReadDuration()
    }
}
