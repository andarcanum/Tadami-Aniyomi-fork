package tachiyomi.data.category.novel

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.category.novel.model.NovelCategory
import tachiyomi.domain.category.novel.model.NovelCategoryUpdate
import tachiyomi.domain.category.novel.repository.NovelCategoryRepository
import tachiyomi.novel.data.NovelDatabase
import java.util.concurrent.atomic.AtomicBoolean

class NovelCategoryRepositoryImpl(
    private val handler: NovelDatabaseHandler,
) : NovelCategoryRepository {
    private val legacyCategoriesMigrationDone = AtomicBoolean(false)

    override suspend fun getCategory(id: Long): NovelCategory? {
        migrateLegacyCategoriesIfNeeded()
        return handler.awaitOneOrNull { novel_categoriesQueries.getCategory(id, ::mapCategory) }
    }

    override suspend fun getCategories(): List<NovelCategory> {
        migrateLegacyCategoriesIfNeeded()
        return handler.awaitList { novel_categoriesQueries.getCategories(::mapCategory) }
    }

    override suspend fun getVisibleCategories(): List<NovelCategory> {
        migrateLegacyCategoriesIfNeeded()
        return handler.awaitList { novel_categoriesQueries.getVisibleCategories(::mapCategory) }
    }

    override suspend fun getCategoriesByNovelId(novelId: Long): List<NovelCategory> {
        migrateLegacyCategoriesIfNeeded()
        return handler.awaitList {
            novel_categoriesQueries.getCategoriesByNovelId(novelId, ::mapCategory)
        }
    }

    override suspend fun getVisibleCategoriesByNovelId(novelId: Long): List<NovelCategory> {
        migrateLegacyCategoriesIfNeeded()
        return handler.awaitList {
            novel_categoriesQueries.getVisibleCategoriesByNovelId(novelId, ::mapCategory)
        }
    }

    override fun getCategoriesAsFlow(): Flow<List<NovelCategory>> {
        return flow {
            migrateLegacyCategoriesIfNeeded()
            emitAll(handler.subscribeToList { novel_categoriesQueries.getCategories(::mapCategory) })
        }
    }

    override fun getVisibleCategoriesAsFlow(): Flow<List<NovelCategory>> {
        return flow {
            migrateLegacyCategoriesIfNeeded()
            emitAll(handler.subscribeToList { novel_categoriesQueries.getVisibleCategories(::mapCategory) })
        }
    }

    override suspend fun insertCategory(category: NovelCategory): Long? {
        migrateLegacyCategoriesIfNeeded()
        return handler.awaitOneOrNullExecutable(inTransaction = true) {
            novel_categoriesQueries.insert(
                name = category.name,
                order = category.order,
                flags = category.flags,
            )
            novel_categoriesQueries.selectLastInsertedRowId()
        }
    }

    override suspend fun updatePartialCategory(update: NovelCategoryUpdate) {
        migrateLegacyCategoriesIfNeeded()
        handler.await {
            updatePartialBlocking(update)
        }
    }

    override suspend fun updateAllFlags(flags: Long) {
        migrateLegacyCategoriesIfNeeded()
        handler.await {
            novel_categoriesQueries.updateAllFlags(flags)
        }
    }

    override suspend fun deleteCategory(categoryId: Long) {
        migrateLegacyCategoriesIfNeeded()
        handler.await { novel_categoriesQueries.delete(categoryId) }
    }

    override suspend fun setNovelCategories(novelId: Long, categoryIds: List<Long>) {
        migrateLegacyCategoriesIfNeeded()
        handler.await(inTransaction = true) {
            novels_categoriesQueries.deleteNovelCategoryByNovelId(novelId)
            categoryIds.map { categoryId ->
                novels_categoriesQueries.insert(novelId, categoryId)
            }
        }
    }

    private suspend fun migrateLegacyCategoriesIfNeeded() {
        if (legacyCategoriesMigrationDone.get()) return
        handler.await(inTransaction = true) {
            val legacyCategories = categoriesQueries.getCategories(::mapCategory)
                .executeAsList()
                .filterNot { it.id == 0L }
            legacyCategories.forEach { legacy ->
                novel_categoriesQueries.insertOrIgnoreWithId(
                    id = legacy.id,
                    name = legacy.name,
                    order = legacy.order,
                    flags = legacy.flags,
                    hidden = if (legacy.hidden) 1L else 0L,
                )
            }
        }
        legacyCategoriesMigrationDone.set(true)
    }

    private fun NovelDatabase.updatePartialBlocking(update: NovelCategoryUpdate) {
        novel_categoriesQueries.update(
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
