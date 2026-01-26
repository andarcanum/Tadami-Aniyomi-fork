package tachiyomi.data.history.anime

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.data.achievement.model.AchievementEvent
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.history.anime.model.AnimeHistory
import tachiyomi.domain.history.anime.model.AnimeHistoryUpdate
import tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.history.anime.repository.AnimeHistoryRepository

class AnimeHistoryRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
    private val eventBus: AchievementEventBus,
) : AnimeHistoryRepository {

    override fun getAnimeHistory(query: String): Flow<List<AnimeHistoryWithRelations>> {
        return handler.subscribeToList {
            animehistoryViewQueries.animehistory(query, AnimeHistoryMapper::mapAnimeHistoryWithRelations)
        }
    }

    override fun getRecentAnimeHistory(limit: Long): Flow<List<AnimeHistoryWithRelations>> {
        return handler.subscribeToList {
            animehistoryViewQueries.getRecentAnimeHistory(limit, AnimeHistoryMapper::mapAnimeHistoryWithRelations)
        }
    }

    override suspend fun getLastAnimeHistory(): AnimeHistoryWithRelations? {
        return handler.awaitOneOrNull {
            animehistoryViewQueries.getLatestAnimeHistory(AnimeHistoryMapper::mapAnimeHistoryWithRelations)
        }
    }

    override suspend fun getHistoryByAnimeId(animeId: Long): List<AnimeHistory> {
        return handler.awaitList {
            animehistoryQueries.getHistoryByAnimeId(
                animeId,
                AnimeHistoryMapper::mapAnimeHistory,
            )
        }
    }

    override suspend fun resetAnimeHistory(historyId: Long) {
        try {
            handler.await { animehistoryQueries.resetAnimeHistoryById(historyId) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun resetHistoryByAnimeId(animeId: Long) {
        try {
            handler.await { animehistoryQueries.resetHistoryByAnimeId(animeId) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun deleteAllAnimeHistory(): Boolean {
        return try {
            handler.await { animehistoryQueries.removeAllHistory() }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
            false
        }
    }

    override suspend fun upsertAnimeHistory(historyUpdate: AnimeHistoryUpdate) {
        try {
            handler.await {
                animehistoryQueries.upsert(
                    historyUpdate.episodeId,
                    historyUpdate.seenAt,
                )
            }

            // Publish achievement event after successful upsert
            // Only publish if this is an actual watch operation (has a valid seenAt date)
            if (historyUpdate.seenAt.time > 0) {
                try {
                    val episodeInfo = handler.awaitOneOrNull {
                        animehistoryQueries.getEpisodeInfo(historyUpdate.episodeId) { animeId, episodeNumber ->
                            Pair(animeId, episodeNumber)
                        }
                    }

                    if (episodeInfo != null) {
                        eventBus.tryEmit(
                            AchievementEvent.EpisodeWatched(
                                animeId = episodeInfo.first,
                                episodeNumber = episodeInfo.second.toInt(),
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Don't let event publishing errors affect history operations
                    logcat(LogPriority.WARN, throwable = e)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }
}
