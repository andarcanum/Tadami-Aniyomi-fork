package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupAchievement
import eu.kanade.tachiyomi.data.backup.models.BackupCrystalMemorial
import eu.kanade.tachiyomi.data.backup.models.BackupDayActivity
import eu.kanade.tachiyomi.data.backup.models.BackupStats
import eu.kanade.tachiyomi.data.backup.models.BackupUserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.achievement.UnlockableManager
import tachiyomi.data.achievement.UserProfileManager
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.model.RewardType
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import tachiyomi.domain.easteregg.crystal.CrystalMemorialIds
import tachiyomi.domain.easteregg.crystal.CrystalMemorialRepository
import tachiyomi.domain.easteregg.crystal.CrystalMemorialRestoreException
import tachiyomi.domain.easteregg.crystal.CrystalMemorialSnapshot
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AchievementRestorer(
    private val achievementRepository: AchievementRepository = Injekt.get(),
    private val activityDataRepository: ActivityDataRepository = Injekt.get(),
    private val userProfileRepository: tachiyomi.domain.achievement.repository.UserProfileRepository = Injekt.get(),
    private val userProfileManager: UserProfileManager = Injekt.get(),
    private val unlockableManager: UnlockableManager = Injekt.get(),
    private val memorialRepository: CrystalMemorialRepository = Injekt.get(),
) {

    /**
     * Restore all achievement data from backup
     * Note: Stats are recalculated automatically from restored history/chapters
     */
    suspend fun restoreAchievements(
        backupAchievements: List<BackupAchievement>,
        backupUserProfile: BackupUserProfile?,
        backupActivityLog: List<BackupDayActivity>,
        backupStats: BackupStats? = null,
        backupCrystalMemorial: BackupCrystalMemorial? = null,
    ) {
        if (backupAchievements.isEmpty() &&
            backupUserProfile == null &&
            backupActivityLog.isEmpty() &&
            backupStats == null && backupCrystalMemorial == null
        ) {
            logcat { "[BACKUP] No achievement data to restore; rehydrating derived unlockable state" }
            rehydrateUnlockables()
            memorialRepository.repairProjections()
            return
        }

        try {
            val crystalEntry = backupAchievements.firstOrNull { it.id == CrystalMemorialIds.GRANT_KEY }
            val state = backupCrystalMemorial?.toState()
            ensureMemorialDefinition(crystalEntry)
            restoreAchievementsList(backupAchievements.filterNot { it.id == CrystalMemorialIds.GRANT_KEY })
            if (backupUserProfile != null || state != null || crystalEntry?.isUnlocked == true) {
                memorialRepository.restoreSnapshot(
                    CrystalMemorialSnapshot(
                        state,
                        backupUserProfile?.toUserProfile(),
                        crystalEntry?.toAchievementProgress(),
                    ),
                )
            }
            restoreActivityLog(backupActivityLog)
            restoreStats(backupStats)
            rehydrateUnlockables()
            memorialRepository.repairProjections()
            logcat { "[BACKUP] Achievement data restored successfully" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(throwable = e) { "[BACKUP] Error restoring achievement data" }
            if (e is CrystalMemorialRestoreException) throw e
        }
    }

    /**
     * Restore achievements and their progress
     * Strategy: Merge with existing (keep unlocked achievements from backup)
     */
    private suspend fun restoreAchievementsList(backupAchievements: List<BackupAchievement>) {
        if (backupAchievements.isEmpty()) return

        // Get existing achievements to merge
        val existingAchievements = achievementRepository.getAll().firstOrNull() ?: emptyList()
        val existingProgress = achievementRepository.getAllProgress().firstOrNull() ?: emptyList()
        val existingProgressMap = existingProgress.associateBy { it.achievementId }

        backupAchievements.forEach { backupAchievement ->
            try {
                val achievement = backupAchievement.toAchievement()
                val progress = backupAchievement.toAchievementProgress()

                // Check if achievement already exists
                val existing = existingAchievements.find { it.id == achievement.id }

                if (existing == null) {
                    // New achievement from backup
                    achievementRepository.insertAchievement(achievement)
                    if (progress.isUnlocked || progress.progress > 0) {
                        achievementRepository.insertOrUpdateProgress(progress)
                    }
                } else {
                    // Merge: if backup has unlocked but local doesn't, update
                    val existingProg = existingProgressMap[achievement.id]
                    val shouldUpdateProgress = when {
                        // Backup has unlocked, local doesn't - update
                        progress.isUnlocked && !(existingProg?.isUnlocked ?: false) -> true
                        // Backup has more progress - update
                        progress.progress > (existingProg?.progress ?: 0) -> true
                        // Backup has higher tier - update
                        progress.currentTier > (existingProg?.currentTier ?: 0) -> true
                        else -> false
                    }

                    if (shouldUpdateProgress) {
                        achievementRepository.insertOrUpdateProgress(progress)
                    }
                }
            } catch (e: Exception) {
                logcat(throwable = e) { "[BACKUP] Error restoring achievement ${backupAchievement.id}" }
            }
        }
    }

    /** Restore only the canonical immutable gift definition; the receipt owns its progress and XP. */
    private suspend fun ensureMemorialDefinition(entry: BackupAchievement?) {
        if (entry == null) return
        if (entry.type != AchievementType.EVENT.ordinal || entry.points != CrystalMemorialIds.XP ||
            entry.rewards.map { it.id }.toSet() != CrystalMemorialIds.REWARDS ||
            entry.rewards.any { it.type == RewardType.EXPERIENCE.ordinal }
        ) {
            throw CrystalMemorialRestoreException("Invalid memorial catalog definition in backup")
        }
        val existing = achievementRepository.getAll().firstOrNull().orEmpty().any {
            it.id ==
                CrystalMemorialIds.GRANT_KEY
        }
        if (!existing) {
            achievementRepository.insertAchievement(
                entry.toAchievement().copy(
                    id = CrystalMemorialIds.GRANT_KEY,
                    type = AchievementType.EVENT,
                    category = AchievementCategory.SECRET,
                    threshold = 1,
                    points = CrystalMemorialIds.XP,
                    isHidden = true,
                    isSecret = true,
                ),
            )
        }
    }

    /**
     * Restore activity log
     * Strategy: Merge - accumulate values for each day
     * Supports both legacy format (date/level/type only) and new format (detailed metrics)
     */
    private suspend fun restoreActivityLog(backupActivityLog: List<BackupDayActivity>) {
        if (backupActivityLog.isEmpty()) return

        try {
            var recordsRestored = 0
            var recordsFailed = 0

            backupActivityLog.forEach { backupActivity ->
                try {
                    val params = backupActivity.toDatabaseParams()

                    // Use upsertActivityData which handles merging/accumulation
                    activityDataRepository.upsertActivityData(
                        date = params.date,
                        chaptersRead = params.chaptersRead,
                        episodesWatched = params.episodesWatched,
                        appOpens = params.appOpens,
                        achievementsUnlocked = params.achievementsUnlocked,
                        durationMs = params.durationMs,
                    )

                    recordsRestored++
                } catch (e: Exception) {
                    logcat(throwable = e) { "[BACKUP] Error restoring activity for date ${backupActivity.date}" }
                    recordsFailed++
                }
            }

            logcat {
                "[BACKUP] Activity log restored: $recordsRestored records restored, $recordsFailed failed"
            }
        } catch (e: Exception) {
            logcat(throwable = e) { "[BACKUP] Error restoring activity log" }
        }
    }

    /**
     * Restore stats
     * Note: Stats are recalculated automatically when history and chapters are restored.
     * This method logs the backed-up stats for reference.
     */
    private suspend fun restoreStats(backupStats: BackupStats?) {
        if (backupStats == null) return

        try {
            // Stats are aggregate data calculated from:
            // - Library manga/anime counts
            // - Chapter/Episode read counts
            // - History read duration
            // - Download counts
            // - Tracker scores
            //
            // These are automatically recalculated when:
            // 1. Manga/Anime are restored (library counts)
            // 2. Chapters/Episodes are restored (total counts)
            // 3. History is restored (read counts, duration)
            // 4. Downloads are restored (download counts)
            // 5. Tracks are restored (scores)

            logcat {
                "[BACKUP] Stats backed up: " +
                    "${backupStats.mangaLibraryCount} manga, " +
                    "${backupStats.animeLibraryCount} anime, " +
                    "${backupStats.chaptersReadCount} chapters read, " +
                    "${backupStats.episodesWatchedCount} episodes watched"
            }
        } catch (e: Exception) {
            logcat(throwable = e) { "[BACKUP] Error logging stats" }
        }
    }

    /**
     * Re-hydrate the treasury (unlockable SharedPreferences) from the
     * achievement definitions. This is the durable fix for the
     * `achievement_unlockables` prefs not being part of the backup: after a
     * restore we replay the unlockables for every achievement that the user
     * had unlocked, using the canonical reward ids in [Achievement.rewards].
     * The canonical definitions in the DB are the source of truth; prefs are
     * just a derived cache.
     */
    private suspend fun rehydrateUnlockables() {
        val existingAchievements = achievementRepository.getAll().firstOrNull() ?: emptyList()
        val existingMap = existingAchievements.associateBy { it.id }

        val allProgress = achievementRepository.getAllProgress().firstOrNull() ?: emptyList()
        val unlockedIds = allProgress.filter { it.isUnlocked }.map { it.achievementId }.toSet()

        val rehydrated = mutableListOf<String>()
        unlockedIds.forEach { id ->
            try {
                val achievement = existingMap[id] ?: return@forEach
                unlockableManager.unlockAchievementRewards(achievement)
                rehydrated += id
            } catch (e: Exception) {
                logcat(throwable = e) {
                    "[BACKUP] Error rehydrating unlockables for $id"
                }
            }
        }
        if (rehydrated.isNotEmpty()) {
            logcat {
                "[BACKUP] Rehydrated treasury for ${rehydrated.size} achievement(s): $rehydrated"
            }
        }
    }
}
