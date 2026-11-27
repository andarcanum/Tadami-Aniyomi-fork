package tachiyomi.data.history.anime

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.service.DiscoveryMutationFence
import tachiyomi.domain.history.anime.model.AnimeHistory
import tachiyomi.domain.history.anime.model.AnimeHistoryUpdate
import tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.history.anime.repository.AnimeHistoryRepository

class AnimeHistoryRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : AnimeHistoryRepository {

    override fun getAnimeHistory(query: String): Flow<List<AnimeHistoryWithRelations>> {
        return handler.subscribeToList { db ->
            db.animehistoryViewQueries.animehistory(query, AnimeHistoryMapper::mapAnimeHistoryWithRelations)
        }
    }

    override fun getRecentAnimeHistory(limit: Long): Flow<List<AnimeHistoryWithRelations>> {
        return handler.subscribeToList { db ->
            db.animehistoryViewQueries.getRecentAnimeHistory(limit, AnimeHistoryMapper::mapAnimeHistoryWithRelations)
        }
    }

    override suspend fun getLastAnimeHistory(): AnimeHistoryWithRelations? {
        return handler.awaitOneOrNull { db ->
            db.animehistoryViewQueries.getLatestAnimeHistory(AnimeHistoryMapper::mapAnimeHistoryWithRelations)
        }
    }

    override suspend fun getHistoryByAnimeId(animeId: Long): List<AnimeHistory> {
        return handler.awaitList { db ->
            db.animehistoryQueries.getHistoryByAnimeId(
                animeId,
                AnimeHistoryMapper::mapAnimeHistory,
            )
        }
    }

    override suspend fun resetAnimeHistory(historyId: Long) {
        try {
            fenced { handler.await { db -> db.animehistoryQueries.resetAnimeHistoryById(historyId) } }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun resetHistoryByAnimeId(animeId: Long) {
        try {
            fenced { handler.await { db -> db.animehistoryQueries.resetHistoryByAnimeId(animeId) } }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun deleteAllAnimeHistory(): Boolean {
        return try {
            fenced { handler.await { db -> db.animehistoryQueries.removeAllHistory() } }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
            false
        }
    }

    override suspend fun upsertAnimeHistory(historyUpdate: AnimeHistoryUpdate) {
        try {
            fenced {
                handler.await { db ->
                    db.animehistoryQueries.upsert(
                        historyUpdate.episodeId,
                        historyUpdate.seenAt,
                    )
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    private suspend fun <T> fenced(block: suspend () -> T): T =
        DiscoveryMutationFence.withMutation(DiscoveryMediaType.ANIME, block)
}
