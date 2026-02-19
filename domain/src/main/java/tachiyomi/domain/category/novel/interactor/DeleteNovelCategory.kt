package tachiyomi.domain.category.novel.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.novel.model.NovelCategoryUpdate
import tachiyomi.domain.category.novel.repository.NovelCategoryRepository
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences

class DeleteNovelCategory(
    private val repository: NovelCategoryRepository,
    private val libraryPreferences: LibraryPreferences,
    private val downloadPreferences: DownloadPreferences,
) {
    suspend fun await(categoryId: Long) = withNonCancellableContext {
        try {
            repository.deleteCategory(categoryId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }

        val categories = repository.getCategories()
        categories.forEachIndexed { index, category ->
            repository.updatePartialCategory(
                NovelCategoryUpdate(
                    id = category.id,
                    order = index.toLong(),
                ),
            )
        }

        val defaultCategory = libraryPreferences.defaultNovelCategory().get()
        if (defaultCategory == categoryId.toInt()) {
            libraryPreferences.defaultNovelCategory().delete()
        }

        val categoryPreferences = listOf(
            libraryPreferences.novelUpdateCategories(),
            libraryPreferences.novelUpdateCategoriesExclude(),
            downloadPreferences.removeExcludeNovelCategories(),
            downloadPreferences.downloadNewNovelChapterCategories(),
            downloadPreferences.downloadNewNovelChapterCategoriesExclude(),
        )
        val categoryIdString = categoryId.toString()
        categoryPreferences.forEach { preference ->
            val ids = preference.get()
            if (categoryIdString !in ids) return@forEach
            preference.set(ids.minus(categoryIdString))
        }

        Result.Success
    }

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
