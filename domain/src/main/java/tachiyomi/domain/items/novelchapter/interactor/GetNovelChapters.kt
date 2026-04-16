package tachiyomi.domain.items.novelchapter.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository

class GetNovelChapters(
    private val repository: NovelChapterRepository,
) {
    suspend fun await(novelId: Long, applyScanlatorFilter: Boolean = false): List<NovelChapter> {
        return repository.getChapterByNovelId(novelId, applyScanlatorFilter)
    }

    suspend fun subscribe(novelId: Long, applyScanlatorFilter: Boolean = false): Flow<List<NovelChapter>> {
        return repository.getChapterByNovelIdAsFlow(novelId, applyScanlatorFilter)
    }
}
