package tachiyomi.data.category.novel

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.novel.data.NovelDatabase
import tachiyomi.domain.category.novel.model.NovelCategory
import tachiyomi.domain.category.novel.model.NovelCategoryUpdate
import tachiyomi.domain.category.novel.repository.NovelCategoryRepository

class NovelCategoryRepositoryImpl(
    private val handler: NovelDatabaseHandler,
) : NovelCategoryRepository {

    override suspend fun getCategory(id: Long): NovelCategory? {
        return handler.awaitOneOrNull { categoriesQueries.getCategory(id, ::mapCategory) }
    }

    override suspend fun getCategories(): List<NovelCategory> {
        return handler.awaitList { categoriesQueries.getCategories(::mapCategory) }
    }

    override suspend fun getVisibleCategories(): List<NovelCategory> {
        return handler.awaitList { categoriesQueries.getVisibleCategories(::mapCategory) }
    }

    override suspend fun getCategoriesByNovelId(novelId: Long): List<NovelCategory> {
        return handler.awaitList {
            categoriesQueries.getCategoriesByNovelId(novelId, ::mapCategory)
        }
    }

    override suspend fun getVisibleCategoriesByNovelId(novelId: Long): List<NovelCategory> {
        return handler.awaitList {
            categoriesQueries.getVisibleCategoriesByNovelId(novelId, ::mapCategory)
        }
    }

    override fun getCategoriesAsFlow(): Flow<List<NovelCategory>> {
        return handler.subscribeToList { categoriesQueries.getCategories(::mapCategory) }
    }

    override fun getVisibleCategoriesAsFlow(): Flow<List<NovelCategory>> {
        return handler.subscribeToList { categoriesQueries.getVisibleCategories(::mapCategory) }
    }

    override suspend fun insertCategory(category: NovelCategory): Long? {
        return handler.awaitOneOrNullExecutable(inTransaction = true) {
            categoriesQueries.insert(
                name = category.name,
                order = category.order,
                flags = category.flags,
            )
            categoriesQueries.selectLastInsertedRowId()
        }
    }

    override suspend fun updatePartialCategory(update: NovelCategoryUpdate) {
        handler.await {
            updatePartialBlocking(update)
        }
    }

    override suspend fun updateAllFlags(flags: Long) {
        handler.await {
            categoriesQueries.updateAllFlags(flags)
        }
    }

    override suspend fun deleteCategory(categoryId: Long) {
        handler.await { categoriesQueries.delete(categoryId) }
    }

    override suspend fun setNovelCategories(novelId: Long, categoryIds: List<Long>) {
        handler.await(inTransaction = true) {
            novels_categoriesQueries.deleteNovelCategoryByNovelId(novelId)
            categoryIds.map { categoryId ->
                novels_categoriesQueries.insert(novelId, categoryId)
            }
        }
    }

    private fun NovelDatabase.updatePartialBlocking(update: NovelCategoryUpdate) {
        categoriesQueries.update(
            name = update.name,
            order = update.order,
            flags = update.flags,
            hidden = update.hidden?.let { if (it) 1L else 0L },
            categoryId = update.id,
        )
    }

    private fun mapCategory(
        id: Long,
        name: String,
        order: Long,
        flags: Long,
        hidden: Long,
    ): NovelCategory = NovelCategory(
        id = id,
        name = name,
        order = order,
        flags = flags,
        hidden = hidden == 1L,
    )
}
