package tachiyomi.data.achievement.handler

/**
 * Калькулятор прогресса достижений.
 *
 * Агрегирует данные из различных источников для вычисления прогресса достижений.
 * Используется при первичной инициализации системы и для пересчета прогресса.
 *
 * Поддерживаемые типы расчетов:
 * - Quantity: Количество прочитанных глав/просмотренных серий
 * - Event: Одноразовые события (первые действия)
 * - Diversity: Разнообразие (жанры, источники)
 * - Streak: Серии активности (дни подряд)
 *
 * @param repository Репозиторий достижений для сохранения прогресса
 * @param mangaHandler Обработчик БД манги для получения статистики
 * @param animeHandler Обработчик БД аниме для получения статистики
 * @param diversityChecker Проверщик разнообразия для вычисления diversity-достижений
 * @param streakChecker Проверщик серий для вычисления streak-достижений
 *
 * @see Achievement
 * @see AchievementProgress
 */
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import logcat.LogPriority
import logcat.logcat
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
) {
    companion object {
        // Batch size for progress updates to avoid memory issues
        private const val BATCH_SIZE = 50

        // Query limit for large library operations
        private const val QUERY_LIMIT = 1000
    }

    suspend fun calculateInitialProgress(): CalculationResult {
        val startTime = System.currentTimeMillis()
        var achievementsProcessed = 0
        var achievementsUnlocked = 0

        try {
            logcat(LogPriority.INFO) { "Starting initial achievement calculation..." }

            // Get all achievements
            val allAchievements = repository.getAll().first()

            // 1. Quantity achievements (total chapters/episodes consumed)
            val (mangaChapters, animeEpisodes) = getTotalConsumed()
            logcat(LogPriority.INFO) { "Total chapters read: $mangaChapters, episodes watched: $animeEpisodes" }

            // 2. Event achievements (first actions)
            val firstAction = if (mangaChapters > 0 || animeEpisodes > 0) 1 else 0

            // 3. Diversity achievements (now with caching)
            val genreCount = diversityChecker.getGenreDiversity()
            val sourceCount = diversityChecker.getSourceDiversity()
            logcat(LogPriority.INFO) { "Unique genres: $genreCount, sources: $sourceCount" }

            // 4. Streak achievements
            val streak = streakChecker.getCurrentStreak()
            logcat(LogPriority.INFO) { "Current streak: $streak days" }

            // Calculate all progress updates first
            val progressUpdates = allAchievements.map { achievement ->
                val progress = when (achievement.type) {
                    AchievementType.QUANTITY -> calculateQuantityProgress(achievement, mangaChapters, animeEpisodes)
                    AchievementType.EVENT -> calculateEventProgress(achievement, firstAction)
                    AchievementType.DIVERSITY -> calculateDiversityProgress(achievement, genreCount, sourceCount)
                    AchievementType.STREAK -> streak
                }

                val threshold = achievement.threshold ?: 1
                val isUnlocked = progress >= threshold

                AchievementProgress(
                    achievementId = achievement.id,
                    progress = progress,
                    maxProgress = threshold,
                    isUnlocked = isUnlocked,
                    unlockedAt = if (isUnlocked) System.currentTimeMillis() else null,
                    lastUpdated = System.currentTimeMillis(),
                )
            }

            // Batch insert progress updates in chunks
            progressUpdates.chunked(BATCH_SIZE).forEach { batch ->
                batch.forEach { progress ->
                    repository.insertOrUpdateProgress(progress)
                    achievementsProcessed++
                    if (progress.isUnlocked) achievementsUnlocked++
                }
            }

            // Populate activity log for streak calculation
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
            logcat(LogPriority.ERROR, e) { "Achievement calculation failed: ${e.message}" }
            return CalculationResult(
                success = false,
                error = e.message,
            )
        }
    }

    private suspend fun getTotalConsumed(): Pair<Long, Long> {
        // Get total chapters read from manga history
        val mangaCount = mangaHandler.awaitOneOrNull {
            historyQueries.getTotalChaptersRead { count -> count ?: 0L }
        } ?: 0L

        // Get total episodes watched from anime history
        val animeCount = animeHandler.awaitOneOrNull {
            animehistoryQueries.getTotalEpisodesWatched { count -> count ?: 0L }
        } ?: 0L

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
        // For event achievements check specific conditions
        return when {
            achievement.id.contains("first", ignoreCase = true) && firstAction > 0 -> 1
            else -> 0
        }
    }

    private fun calculateDiversityProgress(
        achievement: Achievement,
        genreCount: Int,
        sourceCount: Int,
    ): Int {
        return when {
            achievement.id.contains("genre", ignoreCase = true) -> {
                when {
                    achievement.id.contains("manga", ignoreCase = true) -> diversityChecker.getMangaGenreDiversity()
                    achievement.id.contains("anime", ignoreCase = true) -> diversityChecker.getAnimeGenreDiversity()
                    else -> genreCount
                }
            }
            achievement.id.contains("source", ignoreCase = true) -> {
                when {
                    achievement.id.contains("manga", ignoreCase = true) -> diversityChecker.getMangaSourceDiversity()
                    achievement.id.contains("anime", ignoreCase = true) -> diversityChecker.getAnimeSourceDiversity()
                    else -> sourceCount
                }
            }
            else -> 0
        }
    }

    private suspend fun populateActivityLog() {
        // TODO: Populate achievement_activity_log based on history
        // This will allow proper streak calculation for existing users
        // For now, users will start building streak from first use
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
