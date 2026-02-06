package tachiyomi.domain.category.novel.interactor

import tachiyomi.domain.category.novel.repository.NovelCategoryRepository

class SetNovelCategories(
    private val repository: NovelCategoryRepository,
) {
    suspend fun await(novelId: Long, categoryIds: List<Long>) {
        repository.setNovelCategories(novelId, categoryIds)
    }
}