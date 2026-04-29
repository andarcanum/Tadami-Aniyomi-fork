package tachiyomi.domain.source.interactor

import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.SavedSearchRepository

class GetSavedSearchBySourceId(
    private val savedSearchRepository: SavedSearchRepository,
) {
    suspend fun await(sourceId: Long): List<SavedSearch> {
        return savedSearchRepository.getBySourceId(sourceId)
    }
}
