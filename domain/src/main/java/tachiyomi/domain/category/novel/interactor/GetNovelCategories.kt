package tachiyomi.domain.category.novel.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.novel.model.NovelCategory
import tachiyomi.domain.category.novel.repository.NovelCategoryRepository

class GetNovelCategories(
    private val repository: NovelCategoryRepository,
) {
    fun subscribe(): Flow<List<NovelCategory>> = repository.getCategoriesAsFlow()

    suspend fun await(): List<NovelCategory> = repository.getCategories()
}
