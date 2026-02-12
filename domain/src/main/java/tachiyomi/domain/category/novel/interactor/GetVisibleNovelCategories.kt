package tachiyomi.domain.category.novel.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.novel.model.NovelCategory
import tachiyomi.domain.category.novel.repository.NovelCategoryRepository

class GetVisibleNovelCategories(
    private val repository: NovelCategoryRepository,
) {
    fun subscribe(): Flow<List<NovelCategory>> = repository.getVisibleCategoriesAsFlow()

    suspend fun await(): List<NovelCategory> = repository.getVisibleCategories()
}
