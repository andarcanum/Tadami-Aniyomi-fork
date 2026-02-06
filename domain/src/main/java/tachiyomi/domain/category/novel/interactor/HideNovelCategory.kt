package tachiyomi.domain.category.novel.interactor

import tachiyomi.domain.category.novel.model.NovelCategoryUpdate
import tachiyomi.domain.category.novel.repository.NovelCategoryRepository

class HideNovelCategory(
    private val repository: NovelCategoryRepository,
) {
    suspend fun await(categoryId: Long, hidden: Boolean) {
        repository.updatePartialCategory(
            NovelCategoryUpdate(
                id = categoryId,
                hidden = hidden,
            ),
        )
    }
}