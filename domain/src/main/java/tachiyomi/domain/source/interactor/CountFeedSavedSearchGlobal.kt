package tachiyomi.domain.source.interactor

import tachiyomi.domain.source.model.SourceType
import tachiyomi.domain.source.repository.FeedSavedSearchRepository

class CountFeedSavedSearchGlobal(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {
    suspend fun await(sourceType: SourceType): Long = feedSavedSearchRepository.countGlobal(sourceType)
}
