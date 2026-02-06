package tachiyomi.domain.category.novel.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.novel.model.NovelCategory
import tachiyomi.domain.category.novel.model.NovelCategoryUpdate

interface NovelCategoryRepository {

    suspend fun getCategory(id: Long): NovelCategory?

    suspend fun getCategories(): List<NovelCategory>

    suspend fun getVisibleCategories(): List<NovelCategory>

    suspend fun getCategoriesByNovelId(novelId: Long): List<NovelCategory>

    suspend fun getVisibleCategoriesByNovelId(novelId: Long): List<NovelCategory>

    fun getCategoriesAsFlow(): Flow<List<NovelCategory>>

    fun getVisibleCategoriesAsFlow(): Flow<List<NovelCategory>>

    suspend fun insertCategory(category: NovelCategory): Long?

    suspend fun updatePartialCategory(update: NovelCategoryUpdate)

    suspend fun updateAllFlags(flags: Long)

    suspend fun deleteCategory(categoryId: Long)

    suspend fun setNovelCategories(novelId: Long, categoryIds: List<Long>)
}