package tachiyomi.domain.source.repository

import tachiyomi.domain.source.model.SavedSearch

interface SavedSearchRepository {
    suspend fun getById(savedSearchId: Long): SavedSearch?
    suspend fun getBySourceId(sourceId: Long): List<SavedSearch>
    suspend fun delete(savedSearchId: Long)
    suspend fun insert(savedSearch: SavedSearch): Long?
}
