package tachiyomi.domain.entries.novel.interactor

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.repository.NovelRepository

class GetNovel(
    private val novelRepository: NovelRepository,
) {

    suspend fun await(id: Long): Novel? {
        return try {
            novelRepository.getNovelById(id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    suspend fun subscribe(id: Long): Flow<Novel> {
        return novelRepository.getNovelByIdAsFlow(id)
    }

    fun subscribe(url: String, sourceId: Long): Flow<Novel?> {
        return novelRepository.getNovelByUrlAndSourceIdAsFlow(url, sourceId)
    }
}