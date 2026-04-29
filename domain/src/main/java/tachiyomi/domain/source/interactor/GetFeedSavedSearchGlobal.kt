package tachiyomi.domain.source.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SourceType
import tachiyomi.domain.source.repository.FeedSavedSearchRepository

class GetFeedSavedSearchGlobal(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {
    suspend fun await(sourceType: SourceType): List<FeedSavedSearch> {
        return feedSavedSearchRepository.getGlobal(sourceType)
    }

    fun subscribe(sourceType: SourceType): Flow<List<FeedSavedSearch>> {
        return feedSavedSearchRepository.getGlobalAsFlow(sourceType)
    }
}
