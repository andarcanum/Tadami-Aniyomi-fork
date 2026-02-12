package eu.kanade.domain.items.chapter.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.repository.ChapterRepository

class GetScanlatorChapterCounts(
    private val repository: ChapterRepository,
) {

    suspend fun await(mangaId: Long): Map<String, Int> {
        return repository.getChapterByMangaId(mangaId, applyScanlatorFilter = false)
            .toScanlatorChapterCounts()
    }

    suspend fun subscribe(mangaId: Long): Flow<Map<String, Int>> {
        return repository.getChapterByMangaIdAsFlow(mangaId, applyScanlatorFilter = false)
            .map { chapters -> chapters.toScanlatorChapterCounts() }
    }

    private fun List<Chapter>.toScanlatorChapterCounts(): Map<String, Int> {
        return asSequence()
            .mapNotNull { chapter -> chapter.scanlator?.trim()?.takeIf(String::isNotEmpty) }
            .groupingBy { scanlator -> scanlator }
            .eachCount()
    }
}
