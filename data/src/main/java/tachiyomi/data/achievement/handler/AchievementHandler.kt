package tachiyomi.data.achievement.handler

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
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
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
    private val mangaHandler: MangaDatabaseHandler,
    private val animeHandler: AnimeDatabaseHandler,
) {

    interface AchievementUnlockCallback {
        fun onAchievementUnlocked(achievement: Achievement)
    }

    var unlockCallback: AchievementUnlockCallback? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        logcat(LogPriority.INFO) { "[ACHIEVEMENTS] AchievementHandler.start() called - subscribing to event bus (${eventBus.hashCode()})" }
        scope.launch {
            eventBus.events
                .catch { e ->
                    logcat(LogPriority.ERROR) { "[ACHIEVEMENTS] Error in achievement event stream: ${e.message}" }
                }
                .collect { event ->
                    try {
                        logcat(LogPriority.VERBOSE) { "[ACHIEVEMENTS] Event received: $event" }
                        processEvent(event)
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR) { "[ACHIEVEMENTS] Error processing achievement event: $event, ${e.message}" }
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
        logcat(LogPriority.INFO) { "[ACHIEVEMENTS] handleChapterRead: mangaId=${event.mangaId}, chapter=${event.chapterNumber}" }
        streakChecker.logChapterRead()

        val achievements = getAchievementsForCategory(AchievementCategory.MANGA)
        logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Found ${achievements.size} MANGA achievements (incl BOTH)" }

        val relevantAchievements = achievements.filter {
            it.type == AchievementType.QUANTITY ||
                it.type == AchievementType.EVENT ||
                it.type == AchievementType.STREAK ||
                it.type == AchievementType.DIVERSITY ||
                it.type == AchievementType.LIBRARY ||
                it.type == AchievementType.META
        }

        relevantAchievements.forEach { achievement ->
            checkAndUpdateProgress(achievement, event)
        }
    }

    private suspend fun handleEpisodeWatched(event: AchievementEvent.EpisodeWatched) {
        streakChecker.logEpisodeWatched()

        val achievements = getAchievementsForCategory(AchievementCategory.ANIME)
        val relevantAchievements = achievements.filter {
            it.type == AchievementType.QUANTITY ||
                it.type == AchievementType.EVENT ||
                it.type == AchievementType.STREAK ||
                it.type == AchievementType.DIVERSITY ||
                it.type == AchievementType.LIBRARY ||
                it.type == AchievementType.META
        }

        relevantAchievements.forEach { achievement ->
            checkAndUpdateProgress(achievement, event)
        }
    }

    private suspend fun handleLibraryAdded(event: AchievementEvent.LibraryAdded) {
        val achievements = getAchievementsForCategory(event.type)
            .filter {
                it.type == AchievementType.EVENT ||
                    it.type == AchievementType.QUANTITY ||
                    it.type == AchievementType.DIVERSITY ||
                    it.type == AchievementType.LIBRARY ||
                    it.type == AchievementType.META
            }

        achievements.forEach { achievement ->
            checkAndUpdateProgress(achievement, event)
        }
    }

    private suspend fun handleLibraryRemoved(event: AchievementEvent.LibraryRemoved) {
        // no-op for now
    }

    private suspend fun handleMangaCompleted(event: AchievementEvent.MangaCompleted) {
        val achievements = getAchievementsForCategory(AchievementCategory.MANGA)
            .filter { it.type == AchievementType.EVENT }

        achievements.forEach { achievement ->
            checkAndUpdateProgress(achievement, event)
        }
    }

    private suspend fun handleAnimeCompleted(event: AchievementEvent.AnimeCompleted) {
        val achievements = getAchievementsForCategory(AchievementCategory.ANIME)
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
        applyProgressUpdate(achievement, currentProgress, newProgress)
    }

    private suspend fun applyProgressUpdate(
        achievement: Achievement,
        currentProgress: AchievementProgress?,
        newProgress: Int,
    ) {
        val threshold = achievement.threshold ?: 1
        logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Checking ${achievement.id}: current=$currentProgress, new=$newProgress, threshold=$threshold" }

        if (currentProgress == null) {
            logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Creating new progress for ${achievement.id}" }
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
                logcat(LogPriority.INFO) { "[ACHIEVEMENTS] UNLOCKING ${achievement.id} on first check!" }
                onAchievementUnlocked(achievement)
            }
        } else if (!currentProgress.isUnlocked) {
            val shouldUnlock = newProgress >= threshold
            logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Updating progress for ${achievement.id}: shouldUnlock=$shouldUnlock" }
            repository.insertOrUpdateProgress(
                currentProgress.copy(
                    progress = newProgress,
                    isUnlocked = shouldUnlock,
                    unlockedAt = if (shouldUnlock) System.currentTimeMillis() else currentProgress.unlockedAt,
                    lastUpdated = System.currentTimeMillis(),
                ),
            )

            if (shouldUnlock) {
                logcat(LogPriority.INFO) { "[ACHIEVEMENTS] UNLOCKING ${achievement.id}!" }
                onAchievementUnlocked(achievement)
            }
        } else {
            logcat(LogPriority.VERBOSE) { "[ACHIEVEMENTS] ${achievement.id} already unlocked, skipping" }
        }
    }

    private suspend fun calculateProgress(
        achievement: Achievement,
        event: AchievementEvent,
        currentProgress: AchievementProgress?,
    ): Int {
        return when (achievement.type) {
            AchievementType.EVENT -> {
                if (currentProgress != null && currentProgress.progress > 0) {
                    currentProgress.progress
                } else if (isEventMatch(achievement.id, event)) {
                    1
                } else {
                    0
                }
            }
            AchievementType.QUANTITY -> {
                getTotalReadForCategory(achievement.category)
            }
            AchievementType.DIVERSITY -> {
                when {
                    achievement.id.contains("genre", ignoreCase = true) -> {
                        when {
                            achievement.id.contains("manga", ignoreCase = true) -> diversityChecker.getMangaGenreDiversity()
                            achievement.id.contains("anime", ignoreCase = true) -> diversityChecker.getAnimeGenreDiversity()
                            else -> diversityChecker.getGenreDiversity()
                        }
                    }
                    achievement.id.contains("source", ignoreCase = true) -> {
                        when {
                            achievement.id.contains("manga", ignoreCase = true) -> diversityChecker.getMangaSourceDiversity()
                            achievement.id.contains("anime", ignoreCase = true) -> diversityChecker.getAnimeSourceDiversity()
                            else -> diversityChecker.getSourceDiversity()
                        }
                    }
                    else -> currentProgress?.progress ?: 0
                }
            }
            AchievementType.STREAK -> {
                streakChecker.getCurrentStreak()
            }
            AchievementType.LIBRARY -> {
                getLibraryCountForCategory(achievement.category)
            }
            AchievementType.META -> {
                getUnlockedCountExcludingMeta()
            }
        }
    }

    private fun isEventMatch(achievementId: String, event: AchievementEvent): Boolean {
        val id = achievementId.lowercase()
        return when (event) {
            is AchievementEvent.ChapterRead ->
                id.contains("chapter") || id.contains("read")
            is AchievementEvent.EpisodeWatched ->
                id.contains("episode") || id.contains("watch")
            is AchievementEvent.LibraryAdded ->
                id.contains("library") || id.contains("favorite") || id.contains("collect") || id.contains("added")
            is AchievementEvent.LibraryRemoved -> false
            is AchievementEvent.MangaCompleted ->
                id.contains("manga_complete") || id.contains("completed_manga") || id.contains("manga_completed")
            is AchievementEvent.AnimeCompleted ->
                id.contains("anime_complete") || id.contains("completed_anime") || id.contains("anime_completed")
        }
    }

    private suspend fun getAchievementsForCategory(category: AchievementCategory): List<Achievement> {
        val primary = repository.getByCategory(category).first()
        if (category == AchievementCategory.BOTH) return primary
        val both = repository.getByCategory(AchievementCategory.BOTH).first()
        return (primary + both).distinctBy { it.id }
    }

    private suspend fun getTotalReadForCategory(category: AchievementCategory): Int {
        return when (category) {
            AchievementCategory.MANGA -> {
                mangaHandler.awaitOneOrNull { historyQueries.getTotalChaptersRead() }?.toInt() ?: 0
            }
            AchievementCategory.ANIME -> {
                animeHandler.awaitOneOrNull { animehistoryQueries.getTotalEpisodesWatched() }?.toInt() ?: 0
            }
            AchievementCategory.BOTH, AchievementCategory.SECRET -> {
                val mangaCount = mangaHandler.awaitOneOrNull { historyQueries.getTotalChaptersRead() } ?: 0L
                val animeCount = animeHandler.awaitOneOrNull { animehistoryQueries.getTotalEpisodesWatched() } ?: 0L
                (mangaCount + animeCount).toInt()
            }
        }
    }

    private suspend fun getLibraryCountForCategory(category: AchievementCategory): Int {
        val mangaCount = mangaHandler.awaitOneOrNull { mangasQueries.getLibraryCount() } ?: 0L
        val animeCount = animeHandler.awaitOneOrNull { animesQueries.getLibraryCount() } ?: 0L
        return when (category) {
            AchievementCategory.MANGA -> mangaCount.toInt()
            AchievementCategory.ANIME -> animeCount.toInt()
            AchievementCategory.BOTH, AchievementCategory.SECRET -> (mangaCount + animeCount).toInt()
        }
    }

    private suspend fun getUnlockedCountExcludingMeta(): Int {
        val metaIds = repository.getAll().first()
            .filter { it.type == AchievementType.META }
            .map { it.id }
            .toSet()
        return repository.getAllProgress().first()
            .count { it.isUnlocked && it.achievementId !in metaIds }
    }

    private suspend fun updateMetaAchievements() {
        val metaAchievements = repository.getAll().first()
            .filter { it.type == AchievementType.META }
        if (metaAchievements.isEmpty()) return

        val unlockedCount = getUnlockedCountExcludingMeta()
        metaAchievements.forEach { achievement ->
            val currentProgress = repository.getProgress(achievement.id).first()
            applyProgressUpdate(achievement, currentProgress, unlockedCount)
        }
    }

    private fun onAchievementUnlocked(achievement: Achievement) {
        logcat(LogPriority.INFO) { "Achievement unlocked: ${achievement.title} (+${achievement.points} points)" }

        scope.launch {
            try {
                pointsManager.addPoints(achievement.points)
                pointsManager.incrementUnlocked()
                updateMetaAchievements()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to add points for achievement: ${achievement.title}, ${e.message}" }
            }

            try {
                unlockableManager.unlockAchievementRewards(achievement)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to unlock rewards for achievement: ${achievement.title}, ${e.message}" }
            }
        }

        unlockCallback?.onAchievementUnlocked(achievement)
    }
}
