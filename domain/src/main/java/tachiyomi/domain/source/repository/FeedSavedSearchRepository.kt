package tachiyomi.domain.source.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearchUpdate
import tachiyomi.domain.source.model.SourceType

interface FeedSavedSearchRepository {

    suspend fun getGlobal(sourceType: SourceType): List<FeedSavedSearch>

    fun getGlobalAsFlow(sourceType: SourceType): Flow<List<FeedSavedSearch>>

    suspend fun countGlobal(sourceType: SourceType): Long

    suspend fun delete(feedSavedSearchId: Long)

    suspend fun insert(feedSavedSearch: FeedSavedSearch): Long?

    suspend fun insertAll(feedSavedSearch: List<FeedSavedSearch>)

    suspend fun updatePartial(update: FeedSavedSearchUpdate)

    suspend fun updatePartial(updates: List<FeedSavedSearchUpdate>)
}
