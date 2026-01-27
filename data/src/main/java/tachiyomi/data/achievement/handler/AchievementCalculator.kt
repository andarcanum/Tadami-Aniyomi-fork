package tachiyomi.data.achievement.handler

import kotlinx.coroutines.flow.first
import logcat.LogPriority
import logcat.logcat
import tachiyomi.data.achievement.database.AchievementsDatabase
import tachiyomi.data.achievement.handler.checkers.DiversityAchievementChecker
import tachiyomi.data.achievement.handler.checkers.StreakAchievementChecker
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.repository.AchievementRepository

class AchievementCalculator(
    private val repository: AchievementRepository,
    private val mangaHandler: MangaDatabaseHandler,
    private val animeHandler: AnimeDatabaseHandler,
    private val diversityChecker: DiversityAchievementChecker,
    private val streakChecker: StreakAchievementChecker,
    private val achievementsDatabase: AchievementsDatabase,
) {
    companion object {
        private const val BATCH_SIZE = 50
    }

    suspend fun calculateInitialProgress(): CalculationResult {
        val startTime = System.currentTimeMillis()
        var achievementsProcessed = 0
        var achievementsUnlocked = 0

        try {
            logcat(LogPriority.INFO) { "Starting initial achievement calculation..." }

            val allAchievements = repository.getAll().first()
            val achievementsById = allAchievements.associateBy { it.id }

            val (mangaChapters, animeEpisodes) = getTotalConsumed()
            logcat(LogPriority.INFO) { "Total chapters read: $mangaChapters, episodes watched: $animeEpisodes" }

            val firstAction = if (mangaChapters > 0 || animeEpisodes > 0) 1 else 0

            val genreCount = diversityChecker.getGenreDiversity()
            val sourceCount = diversityChecker.getSourceDiversity()
            val mangaGenreCount = diversityChecker.getMangaGenreDiversity()
            val animeGenreCount = diversityChecker.getAnimeGenreDiversity()
            val mangaSourceCount = diversityChecker.getMangaSourceDiversity()
            val animeSourceCount = diversityChecker.getAnimeSourceDiversity()
            logcat(LogPriority.INFO) {
                "Unique genres: $genreCount (M: $mangaGenreCount, A: $animeGenreCount), " +
                    "sources: $sourceCount (M: $mangaSourceCount, A: $animeSourceCount)"
            }

            val streak = streakChecker.getCurrentStreak()
            logcat(LogPriority.INFO) { "Current streak: $streak days" }

            val libraryCounts = getLibraryCounts()

            val nonMetaAchievements = allAchievements.filter { it.type != AchievementType.META }
            val progressUpdates = nonMetaAchievements.map { achievement ->
                val progress = when (achievement.type) {
                    AchievementType.QUANTITY -> calculateQuantityProgress(achievement, mangaChapters, animeEpisodes)
                    AchievementType.EVENT -> calculateEventProgress(achievement, firstAction)
                    AchievementType.DIVERSITY -> calculateDiversityProgress(
                        achievement,
                        genreCount,
                        sourceCount,
                        mangaGenreCount,
                        animeGenreCount,
                        mangaSourceCount,
                        animeSourceCount,
                    )
                    AchievementType.STREAK -> streak
                    AchievementType.LIBRARY -> calculateLibraryProgress(achievement, libraryCounts)
                    AchievementType.BALANCED -> calculateBalancedProgress(mangaChapters, animeEpisodes)
                    AchievementType.META -> 0
                    AchievementType.SECRET -> 0 // Secret achievements handled by AchievementHandler.checkSecretAchievements()
                }

                buildProgress(achievement, progress)
            }

            val unlockedCountExcludingMeta = progressUpdates.count { it.isUnlocked }
            val metaAchievements = allAchievements.filter { it.type == AchievementType.META }
            val metaProgressUpdates = metaAchievements.map { achievement ->
                buildProgress(achievement, unlockedCountExcludingMeta)
            }

            val allProgressUpdates = progressUpdates + metaProgressUpdates

            allProgressUpdates.chunked(BATCH_SIZE).forEach { batch ->
                batch.forEach { progress ->
                    repository.insertOrUpdateProgress(progress)
                    achievementsProcessed++
                    if (progress.isUnlocked) achievementsUnlocked++
                }
            }

            val totalPoints = allProgressUpdates
                .filter { it.isUnlocked }
                .sumOf { achievementsById[it.achievementId]?.points ?: 0 }

            achievementsDatabase.achievementProgressQueries.setTotalPoints(totalPoints.toLong())
            achievementsDatabase.achievementProgressQueries.setUnlockedCount(achievementsUnlocked.toLong())

            populateActivityLog()

            val duration = System.currentTimeMillis() - startTime
            logcat(LogPriority.INFO) {
                "Achievement calculation completed in ${duration}ms. Processed: $achievementsProcessed, Unlocked: $achievementsUnlocked"
            }

            return CalculationResult(
                success = true,
                achievementsProcessed = achievementsProcessed,
                achievementsUnlocked = achievementsUnlocked,
                duration = duration,
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Achievement calculation failed: ${e.message}" }
            return CalculationResult(
                success = false,
                error = e.message ?: "Unknown error",
            )
        }
    }

    private suspend fun getTotalConsumed(): Pair<Long, Long> {
        val mangaCount = mangaHandler.awaitOneOrNull {
            historyQueries.getTotalChaptersRead()
        } ?: 0L

        val animeCount = animeHandler.awaitOneOrNull {
            animehistoryQueries.getTotalEpisodesWatched()
        } ?: 0L

        return Pair(mangaCount, animeCount)
    }

    private suspend fun getLibraryCounts(): Pair<Long, Long> {
        val mangaCount = mangaHandler.awaitOneOrNull { mangasQueries.getLibraryCount() } ?: 0L
        val animeCount = animeHandler.awaitOneOrNull { animesQueries.getLibraryCount() } ?: 0L
        return Pair(mangaCount, animeCount)
    }

    private fun calculateQuantityProgress(
        achievement: Achievement,
        mangaChapters: Long,
        animeEpisodes: Long,
    ): Int {
        return when (achievement.category) {
            AchievementCategory.MANGA -> mangaChapters.toInt()
            AchievementCategory.ANIME -> animeEpisodes.toInt()
            AchievementCategory.BOTH -> (mangaChapters + animeEpisodes).toInt()
            else -> 0
        }.coerceAtLeast(0)
    }

    private fun calculateEventProgress(achievement: Achievement, firstAction: Int): Int {
        return when {
            achievement.id.contains("first", ignoreCase = true) && firstAction > 0 -> 1
            else -> 0
        }
    }

    private fun calculateDiversityProgress(
        achievement: Achievement,
        genreCount: Int,
        sourceCount: Int,
        mangaGenreCount: Int,
        animeGenreCount: Int,
        mangaSourceCount: Int,
        animeSourceCount: Int,
    ): Int {
        return when {
            achievement.id.contains("genre", ignoreCase = true) -> {
                when {
                    achievement.id.contains("manga", ignoreCase = true) -> mangaGenreCount
                    achievement.id.contains("anime", ignoreCase = true) -> animeGenreCount
                    else -> genreCount
                }
            }
            achievement.id.contains("source", ignoreCase = true) -> {
                when {
                    achievement.id.contains("manga", ignoreCase = true) -> mangaSourceCount
                    achievement.id.contains("anime", ignoreCase = true) -> animeSourceCount
                    else -> sourceCount
                }
            }
            else -> 0
        }
    }

    private fun calculateLibraryProgress(
        achievement: Achievement,
        libraryCounts: Pair<Long, Long>,
    ): Int {
        val (mangaCount, animeCount) = libraryCounts
        return when (achievement.category) {
            AchievementCategory.MANGA -> mangaCount.toInt()
            AchievementCategory.ANIME -> animeCount.toInt()
            AchievementCategory.BOTH, AchievementCategory.SECRET -> (mangaCount + animeCount).toInt()
        }
    }

    private fun calculateBalancedProgress(
        mangaChapters: Long,
        animeEpisodes: Long,
    ): Int {
        return minOf(mangaChapters, animeEpisodes).toInt().coerceAtLeast(0)
    }

    private fun buildProgress(achievement: Achievement, progress: Int): AchievementProgress {
        val threshold = achievement.threshold ?: 1
        val isUnlocked = progress >= threshold
        return AchievementProgress(
            achievementId = achievement.id,
            progress = progress,
            maxProgress = threshold,
            isUnlocked = isUnlocked,
            unlockedAt = if (isUnlocked) System.currentTimeMillis() else null,
            lastUpdated = System.currentTimeMillis(),
        )
    }

    private suspend fun populateActivityLog() {
        logcat(LogPriority.INFO) { "Activity log population not yet implemented - streaks will build from first use" }
    }

    data class CalculationResult(
        val success: Boolean,
        val achievementsProcessed: Int = 0,
        val achievementsUnlocked: Int = 0,
        val duration: Long = 0,
        val error: String? = null,
    )
}
