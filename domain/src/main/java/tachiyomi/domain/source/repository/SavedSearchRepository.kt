package tachiyomi.domain.source.repository

import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.model.SourceType

interface SavedSearchRepository {
    suspend fun getById(savedSearchId: Long): SavedSearch?
    suspend fun getBySourceId(sourceId: Long, sourceType: SourceType): List<SavedSearch>
    suspend fun delete(savedSearchId: Long)
    suspend fun insert(savedSearch: SavedSearch): Long?
}
