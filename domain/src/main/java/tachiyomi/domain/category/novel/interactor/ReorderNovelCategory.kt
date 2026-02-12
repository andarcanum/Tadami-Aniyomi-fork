package tachiyomi.domain.category.novel.interactor

import tachiyomi.domain.category.novel.model.NovelCategoryUpdate
import tachiyomi.domain.category.novel.repository.NovelCategoryRepository

class ReorderNovelCategory(
    private val repository: NovelCategoryRepository,
) {
    suspend fun await(categoryId: Long, order: Long) {
        repository.updatePartialCategory(
            NovelCategoryUpdate(
                id = categoryId,
                order = order,
            ),
        )
    }
}
