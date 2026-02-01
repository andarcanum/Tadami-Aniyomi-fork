package eu.kanade.tachiyomi.data.backup.test

import android.content.Context
import android.content.SharedPreferences
import eu.kanade.tachiyomi.data.backup.models.BackupAchievement
import eu.kanade.tachiyomi.data.backup.models.BackupDayActivity
import eu.kanade.tachiyomi.data.backup.models.BackupUserProfile
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.model.UserProfile
import java.time.LocalDate

/**
 * Test data generator for achievement migration testing.
 *
 * Generates:
 * - Legacy SharedPreferences data (v4 format)
 * - Legacy backup format (ProtoNumbers 1-3 only)
 * - New backup format (ProtoNumbers 1-8 with detailed metrics)
 */
object AchievementTestDataGenerator {

    /**
     * Populate SharedPreferences with legacy activity data (v4 format)
     * Used to test LegacyActivityDataMigrator
     */
    fun populateLegacyActivityData(context: Context) {
        val prefs = context.getSharedPreferences("activity_data", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        // Generate 30 days of test data
        val today = LocalDate.now()

        for (i in 0 until 30) {
            val date = today.minusDays(i.toLong())
            val dateStr = date.toString()

            // Vary activity levels
            val chaptersRead = when (i % 7) {
                0 -> 0 // No activity on some days
                1, 2 -> (5..10).random()
                3, 4 -> (10..20).random()
                else -> (1..5).random()
            }

            val episodesWatched = when (i % 5) {
                0 -> 0
                1 -> (1..3).random()
                2, 3 -> (3..8).random()
                else -> (1..2).random()
            }

            val appOpens = if (chaptersRead > 0 || episodesWatched > 0) 1 else 0
            val durationMs = (chaptersRead * 600_000L) + (episodesWatched * 1_200_000L) // 10min per chapter, 20min per episode

            if (chaptersRead > 0) {
                editor.putInt("chapters_$dateStr", chaptersRead)
            }
            if (episodesWatched > 0) {
                editor.putInt("episodes_$dateStr", episodesWatched)
            }
            if (appOpens > 0) {
                editor.putInt("app_opens_$dateStr", appOpens)
            }
            if (durationMs > 0) {
                editor.putLong("duration_$dateStr", durationMs)
            }

            // Some days have achievements unlocked
            if (i % 10 == 0 && i > 0) {
                editor.putInt("achievements_$dateStr", 1)
            }
        }

        editor.apply()
    }

    /**
     * Generate legacy backup format (v4)
     * Only includes ProtoNumbers 1-3 (date, level, type)
     */
    fun generateLegacyBackup(): List<BackupDayActivity> {
        val activities = mutableListOf<BackupDayActivity>()
        val today = LocalDate.now()

        for (i in 0 until 30) {
            val date = today.minusDays(i.toLong())

            // Simple level/type calculation (no detailed metrics)
            val level = when (i % 5) {
                0 -> 0
                1, 2 -> 2
                3 -> 3
                else -> 4
            }

            val type = when (i % 3) {
                0 -> 0 // APP_OPEN
                1 -> 1 // READING
                else -> 2 // WATCHING
            }

            activities.add(
                BackupDayActivity(
                    date = date.toString(),
                    level = level,
                    type = type,
                    // Legacy format: no detailed metrics (default = 0)
                )
            )
        }

        return activities
    }

    /**
     * Generate new backup format (v5)
     * Includes all ProtoNumbers 1-8 with detailed metrics
     */
    fun generateNewBackup(): List<BackupDayActivity> {
        val activities = mutableListOf<BackupDayActivity>()
        val today = LocalDate.now()

        for (i in 0 until 30) {
            val date = today.minusDays(i.toLong())

            val chaptersRead = when (i % 7) {
                0 -> 0
                1, 2 -> (5..10).random()
                3, 4 -> (10..20).random()
                else -> (1..5).random()
            }

            val episodesWatched = when (i % 5) {
                0 -> 0
                1 -> (1..3).random()
                2, 3 -> (3..8).random()
                else -> (1..2).random()
            }

            val appOpens = if (chaptersRead > 0 || episodesWatched > 0) 1 else 0
            val achievementsUnlocked = if (i % 10 == 0 && i > 0) 1 else 0
            val durationMs = (chaptersRead * 600_000L) + (episodesWatched * 1_200_000L)

            // Calculate level and type
            val (type, level) = when {
                achievementsUnlocked > 0 -> 1 to 4 // READING with max level
                episodesWatched > 0 -> 2 to minOf(4, episodesWatched / 2 + 1)
                chaptersRead > 0 -> 1 to minOf(4, chaptersRead / 5 + 1)
                appOpens > 0 -> 0 to 1
                else -> 0 to 0
            }

            activities.add(
                BackupDayActivity(
                    date = date.toString(),
                    level = level,
                    type = type,
                    chaptersRead = chaptersRead,
                    episodesWatched = episodesWatched,
                    appOpens = appOpens,
                    achievementsUnlocked = achievementsUnlocked,
                    durationMs = durationMs,
                )
            )
        }

        return activities
    }

    /**
     * Generate test achievements with progress and tiers
     */
    fun generateTestAchievements(): List<Pair<Achievement, AchievementProgress?>> {
        return listOf(
            // Simple quantity achievement
            Achievement(
                id = "test_read_10",
                type = AchievementType.QUANTITY,
                category = AchievementCategory.MANGA,
                threshold = 10,
                points = 50,
                title = "Bookworm",
                description = "Read 10 chapters",
                badgeIcon = "📚",
                isHidden = false,
                isSecret = false,
                unlockableId = null,
                version = 1,
                createdAt = System.currentTimeMillis(),
            ) to AchievementProgress(
                achievementId = "test_read_10",
                progress = 15,
                maxProgress = 10,
                isUnlocked = true,
                unlockedAt = System.currentTimeMillis() - 86400000, // 1 day ago
                lastUpdated = System.currentTimeMillis(),
            ),

            // Tiered achievement (3 tiers)
            Achievement(
                id = "test_read_tiered",
                type = AchievementType.QUANTITY,
                category = AchievementCategory.MANGA,
                threshold = 100,
                points = 200,
                title = "Master Reader",
                description = "Read chapters (Tier 1: 100, Tier 2: 500, Tier 3: 1000)",
                badgeIcon = "🏆",
                isHidden = false,
                isSecret = false,
                unlockableId = null,
                version = 1,
                createdAt = System.currentTimeMillis(),
            ) to AchievementProgress(
                achievementId = "test_read_tiered",
                progress = 350,
                maxProgress = 1000,
                isUnlocked = true,
                unlockedAt = System.currentTimeMillis() - 172800000, // 2 days ago
                lastUpdated = System.currentTimeMillis(),
                currentTier = 2, // Tier 2 unlocked (500 reached)
                maxTier = 3,
                tierProgress = 350,
                tierMaxProgress = 500,
            ),

            // Locked achievement
            Achievement(
                id = "test_streak_7",
                type = AchievementType.STREAK,
                category = AchievementCategory.BOTH,
                threshold = 7,
                points = 100,
                title = "Week Warrior",
                description = "7 day streak",
                badgeIcon = "🔥",
                isHidden = false,
                isSecret = false,
                unlockableId = null,
                version = 1,
                createdAt = System.currentTimeMillis(),
            ) to AchievementProgress(
                achievementId = "test_streak_7",
                progress = 4,
                maxProgress = 7,
                isUnlocked = false,
                unlockedAt = null,
                lastUpdated = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Generate test user profile
     */
    fun generateTestUserProfile(): UserProfile {
        return UserProfile(
            userId = "default",
            username = "TestUser",
            level = 5,
            currentXP = 230,
            xpToNextLevel = 500,
            totalXP = 1230,
            titles = listOf("Early Adopter", "Bookworm", "Anime Fan"),
            badges = listOf("🏅", "📚", "🎬"),
            unlockedThemes = listOf("aurora", "dark_blue"),
            achievementsUnlocked = 12,
            totalAchievements = 50,
            joinDate = System.currentTimeMillis() - 7776000000, // 90 days ago
        )
    }

    /**
     * Clear legacy SharedPreferences data (for clean testing)
     */
    fun clearLegacyData(context: Context) {
        val prefs = context.getSharedPreferences("activity_data", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    /**
     * Convert test achievements to backup format
     */
    fun achievementsToBackup(achievements: List<Pair<Achievement, AchievementProgress?>>): List<BackupAchievement> {
        return achievements.map { (achievement, progress) ->
            BackupAchievement.fromAchievement(achievement, progress)
        }
    }

    /**
     * Convert user profile to backup format
     */
    fun profileToBackup(profile: UserProfile): BackupUserProfile {
        return BackupUserProfile.fromUserProfile(profile)
    }
}
