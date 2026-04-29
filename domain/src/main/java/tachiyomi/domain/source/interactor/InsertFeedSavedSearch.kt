package tachiyomi.domain.source.interactor

import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.repository.FeedSavedSearchRepository

class InsertFeedSavedSearch(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {
    suspend fun await(feedSavedSearch: FeedSavedSearch): Long? {
        return feedSavedSearchRepository.insert(feedSavedSearch)
    }

    suspend fun awaitAll(feedSavedSearch: List<FeedSavedSearch>) {
        feedSavedSearchRepository.insertAll(feedSavedSearch)
    }
}
