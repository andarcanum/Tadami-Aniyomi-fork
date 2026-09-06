package tachiyomi.domain.source.interactor

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.domain.source.model.FeedSavedSearchUpdate
import tachiyomi.domain.source.model.SourceType
import tachiyomi.domain.source.repository.FeedSavedSearchRepository

class ReorderFeed(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {
    private val mutex = Mutex()

    /**
     * RESH-B15/BFEED-1: reorders the global feed so the RELATIVE order of [visibleOrder] ids
     * matches the given list. Entries not present there (rows whose source no longer resolves
     * and were filtered out of the UI) keep their current slots. The previous API took a raw
     * index from the FILTERED visible list and inserted into the FULL database list - with an
     * orphan row above the drop position every drag landed wrong and the drift accumulated.
     */
    suspend fun changeOrder(sourceType: SourceType, visibleOrder: List<Long>): Result = mutex.withLock {
        try {
            val feeds = feedSavedSearchRepository.getGlobal(sourceType)
            val byId = feeds.associateBy { it.id }
            if (feeds.none { it.id in visibleOrder }) return@withLock Result.Unchanged

            val newFeeds = feeds.toMutableList()
            var nextVisible = 0
            for (slot in newFeeds.indices) {
                if (newFeeds[slot].id !in visibleOrder) continue // orphan keeps its slot
                while (nextVisible < visibleOrder.size && visibleOrder[nextVisible] !in byId) {
                    nextVisible++
                }
                if (nextVisible >= visibleOrder.size) break
                newFeeds[slot] = byId.getValue(visibleOrder[nextVisible])
                nextVisible++
            }

            val updates = newFeeds.mapIndexed { index, f ->
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
