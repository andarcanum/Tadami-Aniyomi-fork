package eu.kanade.domain.items.novelchapter.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository

class GetNovelScanlatorChapterCounts(
    private val repository: NovelChapterRepository,
) {

    suspend fun await(novelId: Long): Map<String, Int> {
        return repository.getChapterByNovelId(novelId, applyScanlatorFilter = false)
            .toScanlatorChapterCounts()
    }

    suspend fun subscribe(novelId: Long): Flow<Map<String, Int>> {
        return repository.getChapterByNovelIdAsFlow(novelId, applyScanlatorFilter = false)
            .map { chapters -> chapters.toScanlatorChapterCounts() }
    }

    private fun List<NovelChapter>.toScanlatorChapterCounts(): Map<String, Int> {
        return asSequence()
            .mapNotNull { chapter -> chapter.scanlator?.trim()?.takeIf(String::isNotEmpty) }
            .groupingBy { scanlator -> scanlator }
            .eachCount()
    }
}
