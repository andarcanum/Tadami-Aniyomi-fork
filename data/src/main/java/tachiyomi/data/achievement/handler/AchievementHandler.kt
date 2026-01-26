package tachiyomi.data.achievement.handler

/**
 * Главный обработчик системы достижений.
 *
 * Координирует процесс проверки и разблокировки достижений.
 * Подписывается на события из EventBus и делегирует проверку соответствующим checker'ам.
 *
 * Поток обработки:
 * 1. Получает событие из EventBus
 * 2. Определяет тип события и релевантные достижения
 * 3. Вычисляет прогресс через checker'ы
 * 4. Обновляет прогресс в базе данных
 * 5. Если разблокировано - добавляет очки и разблокирует награды
 * 6. Уведомляет через callback
 *
 * @param eventBus Шина событий для подписки на действия пользователя
 * @param repository Репозиторий для управления достижениями и прогрессом
 * @param diversityChecker Проверщик достижений разнообразия (жанры, источники)
 * @param streakChecker Проверчик серий активности (дни подряд)
 * @param pointsManager Менеджер для управления очками пользователя
 * @param unlockableManager Менеджер для разблокировки наград (темы, значки)
 *
 * @see AchievementEvent
 * @see AchievementEventBus
 * @see DiversityAchievementChecker
 * @see StreakAchievementChecker
 */

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import tachiyomi.data.achievement.UnlockableManager
import tachiyomi.data.achievement.handler.checkers.DiversityAchievementChecker
import tachiyomi.data.achievement.handler.checkers.StreakAchievementChecker
import tachiyomi.data.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.repository.AchievementRepository

class AchievementHandler(
    private val eventBus: AchievementEventBus,
    private val repository: AchievementRepository,
    private val diversityChecker: DiversityAchievementChecker,
    private val streakChecker: StreakAchievementChecker,
    private val pointsManager: PointsManager,
    private val unlockableManager: UnlockableManager,
) {

    interface AchievementUnlockCallback {
        fun onAchievementUnlocked(achievement: Achievement)
    }

    var unlockCallback: AchievementUnlockCallback? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        scope.launch {
            eventBus.events
                .catch { e ->
                    logcat(LogPriority.ERROR, e) { "Error in achievement event stream" }
                }
                .collect { event ->
                    try {
                        processEvent(event)
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Error processing achievement event: $event" }
                    }
                }
        }
    }

    private suspend fun processEvent(event: AchievementEvent) {
        when (event) {
            is AchievementEvent.ChapterRead -> handleChapterRead(event)
            is AchievementEvent.EpisodeWatched -> handleEpisodeWatched(event)
            is AchievementEvent.LibraryAdded -> handleLibraryAdded(event)
            is AchievementEvent.LibraryRemoved -> handleLibraryRemoved(event)
            is AchievementEvent.MangaCompleted -> handleMangaCompleted(event)
            is AchievementEvent.AnimeCompleted -> handleAnimeCompleted(event)
        }
    }

    private suspend fun handleChapterRead(event: AchievementEvent.ChapterRead) {
        // Log activity for streak tracking
        streakChecker.logChapterRead()

        val achievements = repository.getByCategory(AchievementCategory.MANGA)
            .first()

        val relevantAchievements = achievements.filter {
            it.type == AchievementType.QUANTITY || it.type == AchievementType.EVENT
        }

        relevantAchievements.forEach { achievement ->
            checkAndUpdateProgress(achievement, event)
        }
    }

    private suspend fun handleEpisodeWatched(event: AchievementEvent.EpisodeWatched) {
        // Log activity for streak tracking
        streakChecker.logEpisodeWatched()

        val achievements = repository.getByCategory(AchievementCategory.ANIME)
            .first()

        val relevantAchievements = achievements.filter {
            it.type == AchievementType.QUANTITY || it.type == AchievementType.EVENT
        }

        relevantAchievements.forEach { achievement ->
            checkAndUpdateProgress(achievement, event)
        }
    }

    private suspend fun handleLibraryAdded(event: AchievementEvent.LibraryAdded) {
        val achievements = repository.getByCategory(event.type)
            .first()
            .filter { it.type == AchievementType.EVENT }

        achievements.forEach { achievement ->
            checkAndUpdateProgress(achievement, event)
        }
    }

    private suspend fun handleLibraryRemoved(event: AchievementEvent.LibraryRemoved) {
        // Library removal events typically don't affect achievement progress
        // Add logic here if needed in the future
    }

    private suspend fun handleMangaCompleted(event: AchievementEvent.MangaCompleted) {
        val achievements = repository.getByCategory(AchievementCategory.MANGA)
            .first()
            .filter { it.type == AchievementType.EVENT }

        achievements.forEach { achievement ->
            checkAndUpdateProgress(achievement, event)
        }
    }

    private suspend fun handleAnimeCompleted(event: AchievementEvent.AnimeCompleted) {
        val achievements = repository.getByCategory(AchievementCategory.ANIME)
            .first()
            .filter { it.type == AchievementType.EVENT }

        achievements.forEach { achievement ->
            checkAndUpdateProgress(achievement, event)
        }
    }

    private suspend fun checkAndUpdateProgress(
        achievement: Achievement,
        event: AchievementEvent,
    ) {
        val currentProgress = repository.getProgress(achievement.id).first()
        val newProgress = calculateProgress(achievement, event, currentProgress)
        val threshold = achievement.threshold ?: 1

        if (currentProgress == null) {
            // Create new progress entry
            repository.insertOrUpdateProgress(
                AchievementProgress(
                    achievementId = achievement.id,
                    progress = newProgress,
                    maxProgress = threshold,
                    isUnlocked = newProgress >= threshold,
                    unlockedAt = if (newProgress >= threshold) System.currentTimeMillis() else null,
                    lastUpdated = System.currentTimeMillis(),
                ),
            )

            if (newProgress >= threshold) {
                onAchievementUnlocked(achievement)
            }
        } else if (!currentProgress.isUnlocked) {
            // Update existing progress
            val shouldUnlock = newProgress >= threshold
            repository.insertOrUpdateProgress(
                currentProgress.copy(
                    progress = newProgress,
                    isUnlocked = shouldUnlock,
                    unlockedAt = if (shouldUnlock) System.currentTimeMillis() else currentProgress.unlockedAt,
                    lastUpdated = System.currentTimeMillis(),
                ),
            )

            if (shouldUnlock) {
                onAchievementUnlocked(achievement)
            }
        }
    }

    private fun calculateProgress(
        achievement: Achievement,
        event: AchievementEvent,
        currentProgress: AchievementProgress?,
    ): Int {
        return when (achievement.type) {
            AchievementType.EVENT -> {
                // Event-based achievements: 0 or 1
                if (currentProgress == null || currentProgress.progress == 0) 1 else currentProgress.progress
            }
            AchievementType.QUANTITY -> {
                // Quantity-based achievements: increment
                val current = currentProgress?.progress ?: 0
                when (event) {
                    is AchievementEvent.ChapterRead -> current + 1
                    is AchievementEvent.EpisodeWatched -> current + 1
                    else -> current
                }
            }
            AchievementType.DIVERSITY -> {
                // Diversity achievements: calculate current diversity count
                when {
                    achievement.id.contains("genre", ignoreCase = true) -> {
                        when {
                            achievement.id.contains(
                                "manga",
                                ignoreCase = true,
                            ) -> diversityChecker.getMangaGenreDiversity()
                            achievement.id.contains(
                                "anime",
                                ignoreCase = true,
                            ) -> diversityChecker.getAnimeGenreDiversity()
                            else -> diversityChecker.getGenreDiversity()
                        }
                    }
                    achievement.id.contains("source", ignoreCase = true) -> {
                        when {
                            achievement.id.contains(
                                "manga",
                                ignoreCase = true,
                            ) -> diversityChecker.getMangaSourceDiversity()
                            achievement.id.contains(
                                "anime",
                                ignoreCase = true,
                            ) -> diversityChecker.getAnimeSourceDiversity()
                            else -> diversityChecker.getSourceDiversity()
                        }
                    }
                    else -> currentProgress?.progress ?: 0
                }
            }
            AchievementType.STREAK -> {
                // Streak achievements: calculate current streak
                streakChecker.getCurrentStreak()
            }
        }
    }

    private fun onAchievementUnlocked(achievement: Achievement) {
        logcat(LogPriority.INFO) { "Achievement unlocked: ${achievement.title} (+${achievement.points} points)" }

        // Add points and increment counter
        scope.launch {
            try {
                pointsManager.addPoints(achievement.points)
                pointsManager.incrementUnlocked()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to add points for achievement: ${achievement.title}" }
            }

            // Unlock achievement rewards (themes, badges, etc.)
            try {
                unlockableManager.unlockAchievementRewards(achievement)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to unlock rewards for achievement: ${achievement.title}" }
            }
        }

        unlockCallback?.onAchievementUnlocked(achievement)
    }
}
