package tachiyomi.data.achievement.handler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import tachiyomi.data.achievement.UnlockableManager
import tachiyomi.data.achievement.UserProfileManager
import tachiyomi.data.achievement.handler.AchievementRuleRegistry
import tachiyomi.data.achievement.handler.RuleContextImpl
import tachiyomi.data.achievement.handler.checkers.DiversityAchievementChecker
import tachiyomi.data.achievement.handler.checkers.FeatureBasedAchievementChecker
import tachiyomi.data.achievement.handler.checkers.StreakAchievementChecker
import tachiyomi.data.achievement.handler.checkers.TimeBasedAchievementChecker
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import tachiyomi.domain.achievement.rule.RuleResult
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.entries.novel.repository.NovelRepository

class AchievementHandler(
    private val eventBus: AchievementEventBus,
    private val repository: AchievementRepository,
    private val diversityChecker: DiversityAchievementChecker,
    private val streakChecker: StreakAchievementChecker,
    private val timeBasedChecker: TimeBasedAchievementChecker,
    private val featureBasedChecker: FeatureBasedAchievementChecker,
    private val featureCollector: FeatureUsageCollector,
    private val pointsManager: PointsManager,
    private val unlockableManager: UnlockableManager,
    private val mangaHandler: MangaDatabaseHandler,
    private val animeHandler: AnimeDatabaseHandler,
    private val novelHandler: NovelDatabaseHandler,
    private val mangaRepository: MangaRepository,
    private val animeRepository: AnimeRepository,
    private val novelRepository: NovelRepository,
    private val userProfileManager: UserProfileManager,
    private val activityDataRepository: ActivityDataRepository,
    private val ruleRegistry: AchievementRuleRegistry,
) {

    interface AchievementUnlockCallback {
        fun onAchievementUnlocked(achievement: Achievement)
    }

    var unlockCallback: AchievementUnlockCallback? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        logcat(LogPriority.INFO) {
            "[ACHIEVEMENTS] AchievementHandler.start() called - subscribing to event bus (${eventBus.hashCode()})"
        }
        scope.launch {
            try {
                sanitizeCrossCategoryFirstAchievements()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) {
                    "[ACHIEVEMENTS] Failed to sanitize first achievements: ${e.message}"
                }
            }

            eventBus.events.collect { event ->
                try {
                    logcat(LogPriority.VERBOSE) { "[ACHIEVEMENTS] Event received: $event" }
                    processEvent(event)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) {
                        "[ACHIEVEMENTS] Error processing achievement event: $event, ${e.message}"
                    }
                }
            }
        }
    }

    fun trackFeatureUsed(feature: AchievementEvent.Feature) {
        scope.launch {
            try {
                eventBus.tryEmit(AchievementEvent.FeatureUsed(feature = feature))
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "[ACHIEVEMENTS] Failed to track feature usage: ${e.message}" }
            }
        }
    }

    private suspend fun sanitizeCrossCategoryFirstAchievements() {
        val mangaRead = (mangaHandler.awaitOneOrNull { db -> db.historyQueries.getTotalChaptersRead() } ?: 0L) > 0L
        val animeWatched =
            (animeHandler.awaitOneOrNull { db -> db.animehistoryQueries.getTotalEpisodesWatched() } ?: 0L) > 0L
        val novelRead =
            (novelHandler.awaitOneOrNull { db -> db.novel_historyQueries.getTotalChaptersRead() } ?: 0L) > 0L

        var corrected = false
        corrected = sanitizeFirstAchievement("first_chapter", mangaRead) || corrected
        corrected = sanitizeFirstAchievement("first_episode", animeWatched) || corrected
        corrected = sanitizeFirstAchievement("first_novel_chapter", novelRead) || corrected

        if (corrected) {
            val allAchievements = repository.getAll().first()
            val allProgress = repository.getAllProgress().first().associateBy { it.achievementId }
            val context = RuleContextImpl(
                mangaHandler = mangaHandler,
                animeHandler = animeHandler,
                novelHandler = novelHandler,
                mangaRepository = mangaRepository,
                animeRepository = animeRepository,
                novelRepository = novelRepository,
                diversityChecker = diversityChecker,
                streakChecker = streakChecker,
                featureCollector = featureCollector,
                allProgress = allProgress,
                allAchievementsMap = allAchievements.associateBy { it.id },
            )
            val metaAchievements = allAchievements.filter { it.type == AchievementType.META }
            metaAchievements.forEach { achievement ->
                val rule = ruleRegistry.getRule(achievement.id)
                val newProgress = rule?.evaluateFull(context) ?: 0
                val currentProgress = allProgress[achievement.id]
                applyProgressUpdate(achievement, currentProgress, newProgress)
            }
        }
    }

    internal suspend fun sanitizeFirstAchievement(
        achievementId: String,
        hasRelevantHistory: Boolean,
    ): Boolean {
        val progress = repository.getProgress(achievementId).first() ?: return false
        if (!progress.isUnlocked || hasRelevantHistory) return false

        repository.insertOrUpdateProgress(
            progress.copy(
                progress = 0,
                isUnlocked = false,
                unlockedAt = null,
                lastUpdated = System.currentTimeMillis(),
            ),
        )

        val achievement = repository.getAll().first().find { it.id == achievementId }
        if (achievement != null) {
            unlockableManager.lockUnlockablesForAchievement(achievement)
        }

        logcat(LogPriority.WARN) {
            "[ACHIEVEMENTS] Corrected invalid unlock for $achievementId (no relevant history found)"
        }
        return true
    }

    internal suspend fun processEvent(event: AchievementEvent) {
        // Pre-processing step: update feature usage stats / streak logs
        when (event) {
            is AchievementEvent.ChapterRead -> streakChecker.logChapterRead()
            is AchievementEvent.NovelChapterRead -> streakChecker.logChapterRead()
            is AchievementEvent.EpisodeWatched -> streakChecker.logEpisodeWatched()
            is AchievementEvent.FeatureUsed -> featureCollector.onFeatureUsed(event.feature, event.count)
            is AchievementEvent.LibraryAdded -> diversityChecker.clearCache()
            is AchievementEvent.LibraryRemoved -> diversityChecker.clearCache()
            else -> {}
        }

        val allAchievements = repository.getAll().first()
        val allAchievementsMap = allAchievements.associateBy { it.id }
        val allProgress = repository.getAllProgress().first().associateBy { it.achievementId }.toMutableMap()

        // Standard rules are evaluated first.
        val standardAchievements = allAchievements.filter { it.type != AchievementType.META && it.id != "secret_goku" }
        var anyUnlockHappened = false

        val context = RuleContextImpl(
            mangaHandler = mangaHandler,
            animeHandler = animeHandler,
            novelHandler = novelHandler,
            mangaRepository = mangaRepository,
            animeRepository = animeRepository,
            novelRepository = novelRepository,
            diversityChecker = diversityChecker,
            streakChecker = streakChecker,
            featureCollector = featureCollector,
            allProgress = allProgress,
            allAchievementsMap = allAchievementsMap,
        )

        for (achievement in standardAchievements) {
            val rule = ruleRegistry.getRule(achievement.id) ?: continue
            val currentProgress = allProgress[achievement.id]
            if (currentProgress?.isUnlocked == true) continue

            val result = rule.evaluateDelta(event, currentProgress?.progress ?: 0, context)
            if (result is RuleResult.Update) {
                val threshold = achievement.threshold ?: 1
                val isUnlockedNow = result.newProgress >= threshold

                val updatedProgress = applyProgressUpdate(achievement, currentProgress, result.newProgress)
                if (updatedProgress != null) {
                    allProgress[achievement.id] = updatedProgress
                }
                if (isUnlockedNow) {
                    anyUnlockHappened = true
                }
            }
        }

        // Meta-rules and GokuRule are cheap and derived from global state. Evaluate
        // them on every event instead of only when a standard rule unlocks, so
        // restored/migrated progress and delayed point updates cannot leave meta
        // achievements permanently stale.
        val metaAchievements = allAchievements.filter { it.type == AchievementType.META || it.id == "secret_goku" }
        for (achievement in metaAchievements) {
            val rule = ruleRegistry.getRule(achievement.id) ?: continue
            val currentProgress = allProgress[achievement.id]
            if (currentProgress?.isUnlocked == true) continue

            val result = rule.evaluateDelta(event, currentProgress?.progress ?: 0, context)
            if (result is RuleResult.Update) {
                val updatedProgress = applyProgressUpdate(achievement, currentProgress, result.newProgress)
                if (updatedProgress != null) {
                    allProgress[achievement.id] = updatedProgress
                }
            }
        }
    }

    private suspend fun onAchievementUnlocked(achievement: Achievement) {
        logcat(LogPriority.INFO) { "Achievement unlocked: ${achievement.title} (+${achievement.points} points)" }

        try {
            activityDataRepository.recordAchievementUnlock()
            pointsManager.addPoints(achievement.points)
            pointsManager.incrementUnlocked()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to add points for achievement: ${achievement.title}, ${e.message}" }
        }

        try {
            unlockableManager.unlockAchievementRewards(achievement)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "Failed to unlock rewards for achievement: ${achievement.title}, ${e.message}"
            }
        }

        // Выдаем награды за достижение
        if (achievement.hasRewards) {
            try {
                userProfileManager.grantRewards(achievement.getAllRewards())
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to grant profile rewards: ${e.message}" }
            }
        }

        unlockCallback?.onAchievementUnlocked(achievement)
    }

    private suspend fun applyProgressUpdate(
        achievement: Achievement,
        currentProgress: AchievementProgress?,
        newProgress: Int,
    ): AchievementProgress? {
        return if (achievement.isTiered) {
            // Обработка многоуровневого достижения
            applyTieredProgressUpdate(achievement, currentProgress, newProgress)
        } else {
            // Обработка обычного достижения
            applyStandardProgressUpdate(achievement, currentProgress, newProgress)
        }
    }

    private suspend fun applyStandardProgressUpdate(
        achievement: Achievement,
        currentProgress: AchievementProgress?,
        newProgress: Int,
    ): AchievementProgress? {
        val threshold = achievement.threshold ?: 1
        logcat(LogPriority.INFO) {
            "[ACHIEVEMENTS] Checking ${achievement.id}: current=$currentProgress, new=$newProgress, threshold=$threshold"
        }

        if (currentProgress == null) {
            if (newProgress <= 0) return null
            logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Creating new progress for ${achievement.id}" }
            val progressToSave = AchievementProgress.createStandard(
                achievementId = achievement.id,
                progress = newProgress,
                maxProgress = threshold,
                isUnlocked = newProgress >= threshold,
                unlockedAt = if (newProgress >= threshold) System.currentTimeMillis() else null,
            )
            repository.insertOrUpdateProgress(progressToSave)

            if (newProgress >= threshold) {
                logcat(LogPriority.INFO) { "[ACHIEVEMENTS] UNLOCKING ${achievement.id} on first check!" }
                onAchievementUnlocked(achievement)
            }
            return progressToSave
        } else if (!currentProgress.isUnlocked) {
            if (newProgress == currentProgress.progress && newProgress < threshold) {
                return currentProgress
            }
            val shouldUnlock = newProgress >= threshold
            logcat(LogPriority.INFO) {
                "[ACHIEVEMENTS] Updating progress for ${achievement.id}: shouldUnlock=$shouldUnlock"
            }
            val progressToSave = currentProgress.copy(
                progress = newProgress,
                isUnlocked = shouldUnlock,
                unlockedAt = if (shouldUnlock) System.currentTimeMillis() else currentProgress.unlockedAt,
                lastUpdated = System.currentTimeMillis(),
            )
            repository.insertOrUpdateProgress(progressToSave)

            if (shouldUnlock) {
                logcat(LogPriority.INFO) { "[ACHIEVEMENTS] UNLOCKING ${achievement.id}!" }
                onAchievementUnlocked(achievement)
            }
            return progressToSave
        } else {
            logcat(LogPriority.VERBOSE) { "[ACHIEVEMENTS] ${achievement.id} already unlocked, skipping" }
            return currentProgress
        }
    }

    private suspend fun applyTieredProgressUpdate(
        achievement: Achievement,
        currentProgress: AchievementProgress?,
        newProgress: Int,
    ): AchievementProgress? {
        val tiers = achievement.tiers ?: return null
        logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Checking tiered ${achievement.id}: newProgress=$newProgress" }

        if (currentProgress == null && newProgress <= 0) return null
        if (currentProgress != null && newProgress == currentProgress.progress) return currentProgress

        // Вычисляем текущий уровень
        val newTierIndex = tiers.indexOfLast { newProgress >= it.threshold }
        val newCurrentTier = newTierIndex + 1 // 0-based index to 1-based tier
        val previousTier = currentProgress?.currentTier ?: 0

        // Вычисляем прогресс до следующего уровня
        val nextTier = tiers.getOrNull(newCurrentTier)
        val tierProgress = if (nextTier != null) {
            val previousThreshold = tiers.getOrNull(newCurrentTier - 1)?.threshold ?: 0
            newProgress - previousThreshold
        } else {
            // Уже на максимальном уровне
            0
        }

        val tierMaxProgress = if (nextTier != null) {
            val previousThreshold = tiers.getOrNull(newCurrentTier - 1)?.threshold ?: 0
            nextTier.threshold - previousThreshold
        } else {
            100
        }

        logcat(LogPriority.INFO) {
            "[ACHIEVEMENTS] Tiered ${achievement.id}: " +
                "tier=$newCurrentTier/${tiers.size}, " +
                "tierProgress=$tierProgress/$tierMaxProgress, " +
                "previousTier=$previousTier"
        }

        // Создаем или обновляем прогресс
        val progressToSave = AchievementProgress.createTiered(
            achievementId = achievement.id,
            progress = newProgress,
            currentTier = newCurrentTier,
            maxTier = tiers.size,
            tierProgress = tierProgress,
            tierMaxProgress = tierMaxProgress,
            isUnlocked = newCurrentTier > 0,
            unlockedAt = if (newCurrentTier > 0 && previousTier == 0) {
                System.currentTimeMillis()
            } else {
                currentProgress?.unlockedAt
            },
        )

        repository.insertOrUpdateProgress(progressToSave)

        // Если уровень повысился, отправляем уведомление
        if (newCurrentTier > previousTier && newCurrentTier > 0) {
            val unlockedTier = tiers[newTierIndex]
            logcat(LogPriority.INFO) {
                "[ACHIEVEMENTS] TIER UP! ${achievement.id}: " +
                    "tier $previousTier -> $newCurrentTier (${unlockedTier.title})"
            }
            onAchievementTierUp(achievement, unlockedTier, newCurrentTier)
        }
        return progressToSave
    }

    private suspend fun onAchievementTierUp(
        achievement: Achievement,
        tier: tachiyomi.domain.achievement.model.AchievementTier,
        tierLevel: Int,
    ) {
        logcat(LogPriority.INFO) {
            "Achievement tier up: ${achievement.title} - Tier $tierLevel: ${tier.title} (+${tier.points} points)"
        }

        try {
            unlockableManager.unlockAchievementRewards(achievement)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "Failed to unlock tier rewards for achievement: ${achievement.title}, ${e.message}"
            }
        }

        // Use the shared reward-granting path so tier-ups stay consistent
        // with normal unlocks. The current achievements.json does not
        // declare rewards on tiered achievements, so this is a no-op
        // today, but it future-proofs the path.
        if (achievement.hasRewards) {
            try {
                userProfileManager.grantRewards(achievement.getAllRewards())
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) {
                    "Failed to grant tier rewards for achievement: ${achievement.title}, ${e.message}"
                }
            }
        }

        // Выдаем XP за достижение уровня
        try {
            val xpReward = tier.points * 10 // XP = points * 10
            userProfileManager.addXP(xpReward)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "Failed to add XP for tier up: ${achievement.title}, ${e.message}"
            }
        }
    }
}
