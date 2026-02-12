package tachiyomi.domain.category.novel.interactor

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.novel.model.NovelCategoryUpdate
import tachiyomi.domain.category.novel.repository.NovelCategoryRepository

class ReorderNovelCategory(
    private val repository: NovelCategoryRepository,
) {
    private val mutex = Mutex()

    suspend fun await(categoryId: Long, newIndex: Int) = withNonCancellableContext {
        mutex.withLock {
            val categories = repository.getCategories()
                .filterNot { it.id == 0L }
                .toMutableList()

            val currentIndex = categories.indexOfFirst { it.id == categoryId }
            if (currentIndex == -1) {
                return@withNonCancellableContext Result.Unchanged
            }

            try {
                categories.add(newIndex, categories.removeAt(currentIndex))
                categories.forEachIndexed { index, category ->
                    repository.updatePartialCategory(
                        NovelCategoryUpdate(
                            id = category.id,
                            order = index.toLong(),
                        ),
                    )
                }
                Result.Success
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                Result.InternalError(e)
            }
        }
    }

    sealed interface Result {
        data object Success : Result
        data object Unchanged : Result
        data class InternalError(val error: Throwable) : Result
    }
}
