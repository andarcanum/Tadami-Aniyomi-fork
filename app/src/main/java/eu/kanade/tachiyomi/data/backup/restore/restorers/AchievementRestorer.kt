package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupAchievement
import eu.kanade.tachiyomi.data.backup.models.BackupDayActivity
import eu.kanade.tachiyomi.data.backup.models.BackupStats
import eu.kanade.tachiyomi.data.backup.models.BackupUserProfile
import kotlinx.coroutines.flow.firstOrNull
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.achievement.UserProfileManager
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AchievementRestorer(
    private val achievementRepository: AchievementRepository = Injekt.get(),
    private val activityDataRepository: ActivityDataRepository = Injekt.get(),
    private val userProfileRepository: tachiyomi.domain.achievement.repository.UserProfileRepository = Injekt.get(),
    private val userProfileManager: UserProfileManager = Injekt.get(),
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
    ) {
        if (backupAchievements.isEmpty() &&
            backupUserProfile == null &&
            backupActivityLog.isEmpty() &&
            backupStats == null
        ) {
            logcat { "[BACKUP] No achievement data to restore" }
            return
        }

        try {
            restoreAchievementsList(backupAchievements)
            restoreUserProfile(backupUserProfile)
            restoreActivityLog(backupActivityLog)
            restoreStats(backupStats)
            logcat { "[BACKUP] Achievement data restored successfully" }
        } catch (e: Exception) {
            logcat(throwable = e) { "[BACKUP] Error restoring achievement data" }
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

    /**
     * Restore user profile
     * Strategy: Merge - take max values for XP and unlocked items
     */
    private suspend fun restoreUserProfile(backupUserProfile: BackupUserProfile?) {
        if (backupUserProfile == null) return

        try {
            val currentProfile = userProfileRepository.getProfileSync("default")
            val backupProfile = backupUserProfile.toUserProfile()

            if (currentProfile == null) {
                // No existing profile - restore backup as-is
                userProfileRepository.saveProfile(backupProfile)
                logcat {
                    "[BACKUP] User profile restored (new): level ${backupProfile.level}, XP ${backupProfile.totalXP}"
                }
                return
            }

            // Merge strategy: take max values
            val mergedProfile = currentProfile.copy(
                username = backupProfile.username ?: currentProfile.username,
                level = maxOf(currentProfile.level, backupProfile.level),
                totalXP = maxOf(currentProfile.totalXP, backupProfile.totalXP),
                currentXP = if (backupProfile.level > currentProfile.level) {
                    backupProfile.currentXP
                } else {
                    maxOf(currentProfile.currentXP, backupProfile.currentXP)
                },
                xpToNextLevel = if (backupProfile.level > currentProfile.level) {
                    backupProfile.xpToNextLevel
                } else {
                    currentProfile.xpToNextLevel
                },
                // Merge lists - combine and deduplicate
                titles = (currentProfile.titles + backupProfile.titles).distinct(),
                badges = (currentProfile.badges + backupProfile.badges).distinct(),
                unlockedThemes = (currentProfile.unlockedThemes + backupProfile.unlockedThemes).distinct(),
                achievementsUnlocked = maxOf(currentProfile.achievementsUnlocked, backupProfile.achievementsUnlocked),
                totalAchievements = maxOf(currentProfile.totalAchievements, backupProfile.totalAchievements),
                // Keep earliest join date
                joinDate = minOf(currentProfile.joinDate, backupProfile.joinDate),
            )

            // Save merged profile directly to database
            userProfileRepository.saveProfile(mergedProfile)
            logcat {
                "[BACKUP] User profile restored (merged): level ${mergedProfile.level}, XP ${mergedProfile.totalXP}"
            }
        } catch (e: Exception) {
            logcat(throwable = e) { "[BACKUP] Error restoring user profile" }
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
}
