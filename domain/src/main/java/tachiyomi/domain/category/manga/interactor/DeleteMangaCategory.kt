package tachiyomi.domain.category.manga.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.series.manga.repository.MangaSeriesRepository

class DeleteMangaCategory(
    private val categoryRepository: MangaCategoryRepository,
    private val seriesRepository: MangaSeriesRepository,
    private val libraryPreferences: LibraryPreferences,
    private val downloadPreferences: DownloadPreferences,
) {

    suspend fun await(categoryId: Long) = withNonCancellableContext {
        try {
            categoryRepository.deleteMangaCategory(categoryId)
            // manga_series.category_id has no FK: without this reset the series of the deleted
            // category keep a dangling id and disappear from every library page (their group is
            // not associated with any displayed category, and their volumes are suppressed as
            // series members) with no UI path to recover them.
            seriesRepository.moveSeriesFromCategoryToDefault(categoryId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }

        val categories = categoryRepository.getAllMangaCategories()
        val updates = categories.mapIndexed { index, category ->
            CategoryUpdate(
                id = category.id,
                order = index.toLong(),
            )
        }

        val defaultCategory = libraryPreferences.defaultMangaCategory().get()
        if (defaultCategory == categoryId.toInt()) {
            libraryPreferences.defaultMangaCategory().delete()
        }

        val categoryPreferences = listOf(
            libraryPreferences.mangaUpdateCategories(),
            // The second entry used to be a duplicate of mangaUpdateCategories(); the exclude
            // list (read by the update job) was never cleaned, keeping the deleted category's
            // id in "excluded" forever.
            libraryPreferences.mangaUpdateCategoriesExclude(),
            downloadPreferences.removeExcludeCategories(),
            downloadPreferences.downloadNewChapterCategories(),
            downloadPreferences.downloadNewChapterCategoriesExclude(),
        )
        val categoryIdString = categoryId.toString()
        categoryPreferences.forEach { preference ->
            val ids = preference.get()
            if (categoryIdString !in ids) return@forEach
            preference.set(ids.minus(categoryIdString))
        }

        try {
            categoryRepository.updatePartialMangaCategories(updates)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
