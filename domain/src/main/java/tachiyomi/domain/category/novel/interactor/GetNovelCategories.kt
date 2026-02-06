package tachiyomi.domain.category.novel.interactor

import tachiyomi.domain.category.novel.model.NovelCategory
import tachiyomi.domain.category.novel.repository.NovelCategoryRepository

class GetNovelCategories(
    private val repository: NovelCategoryRepository,
) {
    suspend fun await(): List<NovelCategory> = repository.getCategories()
}