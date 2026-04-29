package tachiyomi.domain.source.interactor

import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.SavedSearchRepository

class InsertSavedSearch(
    private val savedSearchRepository: SavedSearchRepository,
) {
    suspend fun await(savedSearch: SavedSearch): Long? {
        return savedSearchRepository.insert(savedSearch)
    }
}
