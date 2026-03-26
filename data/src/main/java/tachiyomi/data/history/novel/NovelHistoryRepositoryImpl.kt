package tachiyomi.data.history.novel

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.history.novel.model.NovelHistory
import tachiyomi.domain.history.novel.model.NovelHistoryUpdate
import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository

class NovelHistoryRepositoryImpl(
    private val handler: NovelDatabaseHandler,
) : NovelHistoryRepository {

    override fun getNovelHistory(query: String): Flow<List<NovelHistoryWithRelations>> {
        return handler.subscribeToList { db ->
            db.novelhistoryViewQueries.history(query, NovelHistoryMapper::mapNovelHistoryWithRelations)
        }
    }

    override suspend fun getLastNovelHistory(): NovelHistoryWithRelations? {
        return handler.awaitOneOrNull { db ->
            db.novelhistoryViewQueries.getLatestHistory(NovelHistoryMapper::mapNovelHistoryWithRelations)
        }
    }

    override suspend fun getTotalReadDuration(): Long {
        return handler.awaitOne { db -> db.novel_historyQueries.getReadDuration() }
    }

    override suspend fun getHistoryByNovelId(novelId: Long): List<NovelHistory> {
        return handler.awaitList { db ->
            db.novel_historyQueries.getHistoryByNovelId(novelId, NovelHistoryMapper::mapNovelHistory)
        }
    }

    override suspend fun resetNovelHistory(historyId: Long) {
        try {
            handler.await { db -> db.novel_historyQueries.resetHistoryById(historyId) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun resetHistoryByNovelId(novelId: Long) {
        try {
            handler.await { db -> db.novel_historyQueries.resetHistoryByNovelId(novelId) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun deleteAllNovelHistory(): Boolean {
        return try {
            handler.await { db -> db.novel_historyQueries.removeAllHistory() }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
            false
        }
    }

    override suspend fun upsertNovelHistory(historyUpdate: NovelHistoryUpdate) {
        try {
            handler.await { db ->
                db.novel_historyQueries.upsert(
                    historyUpdate.chapterId,
                    historyUpdate.readAt,
                    historyUpdate.sessionReadDuration,
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }
}
