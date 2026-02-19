package tachiyomi.domain.category.novel.interactor

import tachiyomi.domain.category.novel.model.NovelCategoryUpdate
import tachiyomi.domain.category.novel.repository.NovelCategoryRepository

class UpdateNovelCategory(
    private val repository: NovelCategoryRepository,
) {
    suspend fun await(update: NovelCategoryUpdate) {
        repository.updatePartialCategory(update)
    }
}
