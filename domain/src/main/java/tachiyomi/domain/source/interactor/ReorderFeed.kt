package tachiyomi.domain.source.interactor

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearchUpdate
import tachiyomi.domain.source.repository.FeedSavedSearchRepository

class ReorderFeed(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {
    private val mutex = Mutex()

    suspend fun changeOrder(feed: FeedSavedSearch, newIndex: Int): Result = mutex.withLock {
        try {
            val feeds = feedSavedSearchRepository.getGlobal().toMutableList()
            val currentIndex = feeds.indexOfFirst { it.id == feed.id }
            if (currentIndex == -1) return@withLock Result.Unchanged

            feeds.add(newIndex, feeds.removeAt(currentIndex))

            val updates = feeds.mapIndexed { index, f ->
                FeedSavedSearchUpdate(id = f.id, feedOrder = index.toLong())
            }
            feedSavedSearchRepository.updatePartial(updates)
            Result.Success
        } catch (e: Exception) {
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data object Unchanged : Result
        data class InternalError(val error: Throwable) : Result
    }
}
