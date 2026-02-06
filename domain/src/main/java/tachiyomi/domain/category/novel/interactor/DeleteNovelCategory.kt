package tachiyomi.domain.category.novel.interactor

import tachiyomi.domain.category.novel.repository.NovelCategoryRepository

class DeleteNovelCategory(
    private val repository: NovelCategoryRepository,
) {
    suspend fun await(categoryId: Long) {
        repository.deleteCategory(categoryId)
    }
}