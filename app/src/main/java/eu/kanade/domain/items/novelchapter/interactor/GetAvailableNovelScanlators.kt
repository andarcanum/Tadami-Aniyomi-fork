package eu.kanade.domain.items.novelchapter.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository

class GetAvailableNovelScanlators(
    private val repository: NovelChapterRepository,
) {

    private fun List<String>.cleanupAvailableScanlators(): Set<String> {
        return mapNotNull { scanlator ->
            scanlator.trim().ifBlank { null }
        }.toSet()
    }

    suspend fun await(novelId: Long): Set<String> {
        return repository.getScanlatorsByNovelId(novelId)
            .cleanupAvailableScanlators()
    }

    fun subscribe(novelId: Long): Flow<Set<String>> {
        return repository.getScanlatorsByNovelIdAsFlow(novelId)
            .map { it.cleanupAvailableScanlators() }
    }
}
