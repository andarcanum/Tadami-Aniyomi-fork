package tachiyomi.data.category.anime

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.mi.`data`.AnimeDatabase

class AnimeCategoryRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : AnimeCategoryRepository {

    override suspend fun getAnimeCategory(id: Long): Category? {
        return handler.awaitOneOrNull { db -> db.categoriesQueries.getCategory(id, ::mapCategory) }
    }

    override suspend fun getAllAnimeCategories(): List<Category> {
        return handler.awaitList { db -> db.categoriesQueries.getCategories(::mapCategory) }
    }

    override suspend fun getAllVisibleAnimeCategories(): List<Category> {
        return handler.awaitList { db -> db.categoriesQueries.getVisibleCategories(::mapCategory) }
    }

    override fun getAllAnimeCategoriesAsFlow(): Flow<List<Category>> {
        return handler.subscribeToList { db -> db.categoriesQueries.getCategories(::mapCategory) }
    }

    override fun getAllVisibleAnimeCategoriesAsFlow(): Flow<List<Category>> {
        return handler.subscribeToList { db -> db.categoriesQueries.getVisibleCategories(::mapCategory) }
    }

    override suspend fun getCategoriesByAnimeId(animeId: Long): List<Category> {
        return handler.awaitList { db ->
            db.categoriesQueries.getCategoriesByAnimeId(animeId, ::mapCategory)
        }
    }

    override suspend fun getVisibleCategoriesByAnimeId(animeId: Long): List<Category> {
        return handler.awaitList { db ->
            db.categoriesQueries.getVisibleCategoriesByAnimeId(animeId, ::mapCategory)
        }
    }

    override fun getCategoriesByAnimeIdAsFlow(animeId: Long): Flow<List<Category>> {
        return handler.subscribeToList { db ->
            db.categoriesQueries.getCategoriesByAnimeId(animeId, ::mapCategory)
        }
    }

    override fun getVisibleCategoriesByAnimeIdAsFlow(animeId: Long): Flow<List<Category>> {
        return handler.subscribeToList { db ->
            db.categoriesQueries.getVisibleCategoriesByAnimeId(animeId, ::mapCategory)
        }
    }

    override suspend fun insertAnimeCategory(category: Category) {
        handler.await { db ->
            db.categoriesQueries.insert(
                name = category.name,
                order = category.order,
                flags = category.flags,
            )
        }
    }

    override suspend fun updatePartialAnimeCategory(update: CategoryUpdate) {
        handler.await { db ->
            db.updatePartialBlocking(update)
        }
    }

    override suspend fun updatePartialAnimeCategories(updates: List<CategoryUpdate>) {
        handler.await(inTransaction = true) { db ->
            for (update in updates) {
                db.updatePartialBlocking(update)
            }
        }
    }

    private fun AnimeDatabase.updatePartialBlocking(update: CategoryUpdate) {
        categoriesQueries.update(
            name = update.name,
            order = update.order,
            flags = update.flags,
            hidden = update.hidden?.let { if (it) 1L else 0L },
            categoryId = update.id,
        )
    }

    override suspend fun updateAllAnimeCategoryFlags(flags: Long?) {
        handler.await { db ->
            db.categoriesQueries.updateAllFlags(flags)
        }
    }

    override suspend fun deleteAnimeCategory(categoryId: Long) {
        handler.await { db ->
            db.categoriesQueries.delete(
                categoryId = categoryId,
            )
        }
    }

    private fun mapCategory(
        id: Long,
        name: String,
        order: Long,
        flags: Long,
        hidden: Long,
    ): Category {
        return Category(
            id = id,
            name = name,
            order = order,
            flags = flags,
            hidden = hidden == 1L,
        )
    }
}
