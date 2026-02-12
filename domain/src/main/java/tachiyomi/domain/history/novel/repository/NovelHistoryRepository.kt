package tachiyomi.domain.history.novel.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.history.novel.model.NovelHistory
import tachiyomi.domain.history.novel.model.NovelHistoryUpdate
import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations

interface NovelHistoryRepository {

    fun getNovelHistory(query: String): Flow<List<NovelHistoryWithRelations>>

    suspend fun getLastNovelHistory(): NovelHistoryWithRelations?

    suspend fun getTotalReadDuration(): Long

    suspend fun getHistoryByNovelId(novelId: Long): List<NovelHistory>

    suspend fun resetNovelHistory(historyId: Long)

    suspend fun resetHistoryByNovelId(novelId: Long)

    suspend fun deleteAllNovelHistory(): Boolean

    suspend fun upsertNovelHistory(historyUpdate: NovelHistoryUpdate)
}
