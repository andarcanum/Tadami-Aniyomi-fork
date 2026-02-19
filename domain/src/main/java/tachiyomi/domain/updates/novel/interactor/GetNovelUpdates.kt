package tachiyomi.domain.updates.novel.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.updates.novel.model.NovelUpdatesWithRelations
import tachiyomi.domain.updates.novel.repository.NovelUpdatesRepository
import java.time.Instant

class GetNovelUpdates(
    private val repository: NovelUpdatesRepository,
) {

    suspend fun await(read: Boolean, after: Long): List<NovelUpdatesWithRelations> {
        return repository.awaitWithRead(read, after, limit = 500)
    }

    fun subscribe(instant: Instant): Flow<List<NovelUpdatesWithRelations>> {
        return repository.subscribeAllNovelUpdates(instant.toEpochMilli(), limit = 500)
    }

    fun subscribe(read: Boolean, after: Long): Flow<List<NovelUpdatesWithRelations>> {
        return repository.subscribeWithRead(read, after, limit = 500)
    }
}
