package tachiyomi.domain.source.interactor

import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.SavedSearchRepository

class GetSavedSearchById(
    private val savedSearchRepository: SavedSearchRepository,
) {
    suspend fun await(id: Long): SavedSearch? {
        return savedSearchRepository.getById(id)
    }
}
