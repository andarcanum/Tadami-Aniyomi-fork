package tachiyomi.domain.source.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearchUpdate

interface FeedSavedSearchRepository {

    suspend fun getGlobal(): List<FeedSavedSearch>

    fun getGlobalAsFlow(): Flow<List<FeedSavedSearch>>

    suspend fun countGlobal(): Long

    suspend fun delete(feedSavedSearchId: Long)

    suspend fun insert(feedSavedSearch: FeedSavedSearch): Long?

    suspend fun insertAll(feedSavedSearch: List<FeedSavedSearch>)

    suspend fun updatePartial(update: FeedSavedSearchUpdate)

    suspend fun updatePartial(updates: List<FeedSavedSearchUpdate>)
}
