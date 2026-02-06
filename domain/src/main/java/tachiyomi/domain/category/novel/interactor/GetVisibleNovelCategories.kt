package tachiyomi.domain.category.novel.interactor

import tachiyomi.domain.category.novel.model.NovelCategory
import tachiyomi.domain.category.novel.repository.NovelCategoryRepository

class GetVisibleNovelCategories(
    private val repository: NovelCategoryRepository,
) {
    suspend fun await(): List<NovelCategory> = repository.getVisibleCategories()
}