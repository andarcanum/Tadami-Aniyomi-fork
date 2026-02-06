package tachiyomi.domain.updates.novel.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.updates.novel.model.NovelUpdatesWithRelations

interface NovelUpdatesRepository {

    suspend fun awaitWithRead(read: Boolean, after: Long, limit: Long): List<NovelUpdatesWithRelations>

    fun subscribeAllNovelUpdates(after: Long, limit: Long): Flow<List<NovelUpdatesWithRelations>>

    fun subscribeWithRead(read: Boolean, after: Long, limit: Long): Flow<List<NovelUpdatesWithRelations>>
}
