package tachiyomi.data.achievement.handler

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import logcat.LogPriority
import logcat.logcat
import tachiyomi.data.achievement.AchievementXp
import tachiyomi.data.achievement.UnlockableManager
import tachiyomi.data.achievement.UserProfileManager
import tachiyomi.data.achievement.database.AchievementsDatabase
import tachiyomi.data.achievement.handler.checkers.DiversityAchievementChecker
import tachiyomi.data.achievement.handler.checkers.StreakAchievementChecker
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.model.RewardType
import tachiyomi.domain.achievement.model.UserProfile
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import tachiyomi.domain.easteregg.crystal.CrystalMemorialIds
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.entries.novel.repository.NovelRepository

class AchievementCalculator(
    private val repository: AchievementRepository,
    private val mangaHandler: MangaDatabaseHandler,
    private val animeHandler: AnimeDatabaseHandler,
    private val novelHandler: NovelDatabaseHandler,
    private val diversityChecker: DiversityAchievementChecker,
    private val streakChecker: StreakAchievementChecker,
    private val achievementsDatabase: AchievementsDatabase,
    private val ruleRegistry: AchievementRuleRegistry,
    private val featureCollector: FeatureUsageCollector,
    private val pointsManager: PointsManager,
    private val mangaRepository: MangaRepository,
    private val animeRepository: AnimeRepository,
    private val novelRepository: NovelRepository,
    private val unlockableManager: UnlockableManager,
    private val userProfileManager: UserProfileManager,
    private val activityDataRepository: ActivityDataRepository,
) {
    companion object {
        private const val BATCH_SIZE = 50
    }

    suspend fun calculateInitialProgress(): CalculationResult = achievementsDatabase.withProfileMutation {
        AchievementXp.ensureProfile(achievementsDatabase, System.currentTimeMillis())
        calculateLocked()
    }

    private suspend fun calculateLocked(): CalculationResult {
        val startTime = System.currentTimeMillis()
        var achievementsProcessed = 0
        var achievementsUnlocked = 0

        try {
            logcat(LogPriority.INFO) { "Starting initial achievement calculation..." }
            diversityChecker.clearCache()

            val allAchievements = repository.getAll().first()
            val achievementsById = allAchievements.associateBy { it.id }
            val existingProgress = repository.getAllProgress().first()
                .associateBy { it.achievementId }
            val allProgressMap = existingProgress.toMutableMap()

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
                allProgress = allProgressMap,
                allAchievementsMap = achievementsById,
            )

            // Step 1: Evaluate all standard rules (non-meta, excluding secret_goku)
            val standardAchievements = allAchievements.filter {
                it.type != AchievementType.META &&
                    it.id != "secret_goku"
            }
            val standardProgressUpdates = standardAchievements.map { achievement ->
                val rule = ruleRegistry.getRule(achievement.id)
                val progress = rule?.evaluateFull(context) ?: 0
                val updated = buildProgress(achievement, progress, existingProgress[achievement.id])
                allProgressMap[achievement.id] = updated
                updated
            }

            // Step 2: Evaluate meta rules & Goku rule
            val metaAchievements = allAchievements.filter { it.type == AchievementType.META || it.id == "secret_goku" }
            val metaProgressUpdates = metaAchievements.map { achievement ->
                val rule = ruleRegistry.getRule(achievement.id)
                val progress = rule?.evaluateFull(context) ?: 0
                val updated = buildProgress(achievement, progress, existingProgress[achievement.id])
                allProgressMap[achievement.id] = updated
                updated
            }

            val allProgressUpdates = standardProgressUpdates + metaProgressUpdates

            allProgressUpdates.chunked(BATCH_SIZE).forEach { batch ->
                batch.forEach { progress ->
                    repository.insertOrUpdateProgress(progress)
                    achievementsProcessed++
                    if (progress.isUnlocked) achievementsUnlocked++
                }
            }

            // Replay side effects for newly-unlocked achievements only.
            // Achievements that were already unlocked (existing.isUnlocked) must not
            // be replayed: that would double-count points/XP and re-unlock already-
            // granted treasury rewards.
            val newlyUnlocked = allProgressUpdates.filter { progress ->
                val wasUnlocked = existingProgress[progress.achievementId]?.isUnlocked == true
                progress.achievementId != CrystalMemorialIds.GRANT_KEY && progress.isUnlocked && !wasUnlocked
            }
            newlyUnlocked.forEach { progress ->
                val achievement = achievementsById[progress.achievementId] ?: return@forEach
                replayUnlockSideEffects(achievement)
            }

            // Keep the Treasury/Preferences state in sync with the authoritative
            // achievement progress table. This fixes cases where achievements were
            // already marked as unlocked, but their cosmetic rewards were lost after
            // a restore, migration, preferences reset, or an earlier side-effect
            // failure. Recomputing unlockables is idempotent and does not replay XP.
            val unlockedAchievements = allProgressUpdates.mapNotNull { progress ->
                if (progress.isUnlocked) achievementsById[progress.achievementId] else null
            }
            try {
                unlockableManager.recomputeUnlockablesFromUnlockedAchievements(unlockedAchievements)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) {
                    "Failed to synchronize treasury rewards: ${e.message}"
                }
            }

            val totalPoints = allProgressUpdates
                .filter { it.isUnlocked }
                .sumOf { progress ->
                    val achievement = achievementsById[progress.achievementId] ?: return@sumOf 0
                    if (achievement.isTiered) {
                        achievement.tiers?.take(progress.currentTier)?.sumOf { it.points } ?: 0
                    } else {
                        achievement.points
                    }
                }
            achievementsDatabase.transaction {
                AchievementXp.setTotal(achievementsDatabase, totalPoints.toLong(), System.currentTimeMillis())
                achievementsDatabase.userProfileQueries.updateAchievementCounts(
                    user_id = "default",
                    unlocked = achievementsUnlocked.toLong(),
                    total = allProgressUpdates.size.toLong(),
                    last_updated = System.currentTimeMillis(),
                )
            }

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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Achievement calculation failed: ${e.message}" }
            return CalculationResult(
                success = false,
                error = e.message ?: "Unknown error",
            )
        }
    }

    private fun buildProgress(
        achievement: Achievement,
        progress: Int,
        existing: AchievementProgress?,
    ): AchievementProgress {
        if (achievement.id == CrystalMemorialIds.GRANT_KEY) {
            val receipt = achievementsDatabase.crystalMemorialQueries.getState(
                CrystalMemorialIds.GRANT_KEY,
            ).executeAsOneOrNull()
            val granted = receipt?.granted_at != null && receipt.granted_xp == CrystalMemorialIds.XP.toLong()
            return AchievementProgress(
                achievementId = achievement.id,
                progress = if (granted) 1 else 0,
                maxProgress = 1,
                isUnlocked = granted,
                unlockedAt = if (granted) receipt.granted_at else null,
                lastUpdated = System.currentTimeMillis(),
            )
        }
        val threshold = achievement.threshold ?: 1
        val isUnlocked = progress >= threshold
        val now = System.currentTimeMillis()
        // Merge semantics: preserve unlock state and timestamp for achievements
        // that were already unlocked. The fresh evaluation must never silently
        // re-lock or rewrite the unlock metadata of an already-unlocked row.
        val preservedUnlock = existing?.takeIf { it.isUnlocked } != null
        return AchievementProgress(
            achievementId = achievement.id,
            progress = progress,
            maxProgress = threshold,
            isUnlocked = isUnlocked || preservedUnlock,
            unlockedAt = when {
                existing?.unlockedAt != null -> existing.unlockedAt
                isUnlocked -> now
                else -> null
            },
            lastUpdated = now,
        )
    }

    private suspend fun populateActivityLog() {
        // Activity log is populated incrementally via recordAchievementUnlock()
        // called in replayUnlockSideEffects for each newly-unlocked achievement.
        // No additional bulk population is needed here.
    }

    private suspend fun replayUnlockSideEffects(achievement: Achievement) {
        logcat(LogPriority.INFO) {
            "Replaying unlock side effects for ${achievement.id} (+${achievement.points} points)"
        }
        // XP and counters are committed once from the final aggregate, while the shared mutation lock is held.
        try {
            unlockableManager.unlockAchievementRewards(achievement)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "Failed to unlock treasury rewards for retroactively unlocked achievement: ${achievement.id}, ${e.message}"
            }
        }
        if (achievement.hasRewards) {
            try {
                userProfileManager.grantRewards(achievement.getAllRewards().filter { it.type != RewardType.EXPERIENCE })
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) {
                    "Failed to grant profile rewards for retroactively unlocked achievement: ${achievement.id}, ${e.message}"
                }
            }
        }
        try {
            activityDataRepository.recordAchievementUnlock()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "Failed to log activity for retroactively unlocked achievement: ${achievement.id}, ${e.message}"
            }
        }
    }

    data class CalculationResult(
        val success: Boolean,
        val achievementsProcessed: Int = 0,
        val achievementsUnlocked: Int = 0,
        val duration: Long = 0,
        val error: String? = null,
    )
}
