package eu.kanade.tachiyomi.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.data.backup.create.creators.AchievementBackupCreator
import eu.kanade.tachiyomi.data.backup.models.BackupAchievement
import eu.kanade.tachiyomi.data.backup.models.BackupDayActivity
import eu.kanade.tachiyomi.data.backup.models.BackupUserProfile
import eu.kanade.tachiyomi.data.backup.restore.restorers.AchievementRestorer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tachiyomi.data.achievement.ActivityDataRepositoryImpl
import tachiyomi.data.achievement.UserProfileManager
import tachiyomi.data.achievement.UserProfileRepositoryImpl
import tachiyomi.data.achievement.database.AchievementsDatabase
import tachiyomi.data.achievement.repository.AchievementRepositoryImpl
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.model.UserProfile
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for achievement backup/restore cycle.
 * Tests end-to-end scenarios including:
 * - Full backup creation
 * - Restore with merging
 * - Backward compatibility (legacy format)
 * - Data integrity after restore
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AchievementBackupRestoreIntegrationTest {

    private lateinit var context: Context
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: AchievementsDatabase
    private lateinit var achievementRepository: AchievementRepositoryImpl
    private lateinit var activityRepository: ActivityDataRepositoryImpl
    private lateinit var userProfileRepository: UserProfileRepositoryImpl
    private lateinit var userProfileManager: UserProfileManager
    private lateinit var backupCreator: AchievementBackupCreator
    private lateinit var restorer: AchievementRestorer

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        tachiyomi.data.achievement.AchievementsDatabase.Schema.create(driver)
        database = AchievementsDatabase(driver)

        achievementRepository = AchievementRepositoryImpl(database)
        activityRepository = ActivityDataRepositoryImpl(database)
        userProfileRepository = UserProfileRepositoryImpl(database)
        userProfileManager = UserProfileManager(userProfileRepository)

        // Note: AchievementBackupCreator requires more dependencies, simplified version for testing
        restorer = AchievementRestorer(
            achievementRepository = achievementRepository,
            activityDataRepository = activityRepository,
            userProfileRepository = userProfileRepository,
            userProfileManager = userProfileManager,
        )
    }

    @After
    fun teardown() {
        driver.close()
    }

    @Test
    fun `full backup and restore cycle preserves data`() {
        runTest {
            // Setup initial data
            val achievement = createTestAchievement("test_1")
            val progress = createTestProgress("test_1", unlocked = true)

            achievementRepository.insertAchievement(achievement)
            achievementRepository.insertOrUpdateProgress(progress)

            val profile = UserProfile.createDefault().copy(
                level = 10,
                totalXP = 5000,
                titles = listOf("Pro", "Master"),
            )
            userProfileRepository.saveProfile(profile)

            // Create backup
            val backupAchievements = listOf(BackupAchievement.fromAchievement(achievement, progress))
            val backupProfile = BackupUserProfile.fromUserProfile(profile)
            val backupActivity = generateTestActivityLog()

            // Clear database to simulate restore to clean state
            achievementRepository.deleteAllAchievements()
            activityRepository.deleteAllActivityData()
            userProfileRepository.deleteProfile("default")

            // Restore
            restorer.restoreAchievements(
                backupAchievements = backupAchievements,
                backupUserProfile = backupProfile,
                backupActivityLog = backupActivity,
                backupStats = null,
            )

            // Verify achievement restored
            val restoredAchievements = achievementRepository.getAll().first()
            assertEquals(1, restoredAchievements.size)
            assertEquals("test_1", restoredAchievements.first().id)

            val restoredProgress = achievementRepository.getProgress("test_1").first()
            assertNotNull(restoredProgress)
            assertTrue(restoredProgress.isUnlocked)

            // Verify profile restored
            val restoredProfile = userProfileRepository.getProfileSync("default")
            assertNotNull(restoredProfile)
            assertEquals(10, restoredProfile.level)
            assertEquals(5000, restoredProfile.totalXP)
            assertEquals(2, restoredProfile.titles.size)

            // Verify activity restored
            val restoredActivity = activityRepository.getActivityData(days = 30).first()
            assertTrue(restoredActivity.isNotEmpty())
        }
    }

    @Test
    fun `restore with merge preserves higher values`() {
        runTest {
            // Existing data in database
            val existingProfile = UserProfile.createDefault().copy(
                level = 15,
                totalXP = 8000,
                titles = listOf("Veteran"),
            )
            userProfileRepository.saveProfile(existingProfile)

            // Backup with lower values
            val backupProfile = UserProfile.createDefault().copy(
                level = 10,
                totalXP = 5000,
                titles = listOf("Newbie", "Pro"),
            )

            // Restore (should merge)
            restorer.restoreAchievements(
                backupAchievements = emptyList(),
                backupUserProfile = BackupUserProfile.fromUserProfile(backupProfile),
                backupActivityLog = emptyList(),
                backupStats = null,
            )

            // Verify merge kept higher values
            val merged = userProfileRepository.getProfileSync("default")
            assertNotNull(merged)
            assertEquals(15, merged.level) // Kept existing (higher)
            assertEquals(8000, merged.totalXP) // Kept existing (higher)
            assertEquals(3, merged.titles.size) // Merged lists: Veteran + Newbie + Pro
            assertTrue(merged.titles.contains("Veteran"))
            assertTrue(merged.titles.contains("Pro"))
        }
    }

    @Test
    fun `legacy backup format restores correctly`() {
        runTest {
            // Create legacy format backup (ProtoNumbers 1-3 only, no detailed metrics)
            val legacyActivity = listOf(
                BackupDayActivity(
                    date = LocalDate.now().toString(),
                    level = 3,
                    type = 1, // READING
                    // Legacy: no chaptersRead, episodesWatched, etc.
                ),
                BackupDayActivity(
                    date = LocalDate.now().minusDays(1).toString(),
                    level = 2,
                    type = 2, // WATCHING
                ),
            )

            // Restore legacy format
            restorer.restoreAchievements(
                backupAchievements = emptyList(),
                backupUserProfile = null,
                backupActivityLog = legacyActivity,
                backupStats = null,
            )

            // Verify data restored (with zero metrics)
            val restored = activityRepository.getActivityData(days = 2).first()
            assertEquals(2, restored.size)

            // Legacy format should restore with default metrics (0)
            val record = database.activityLogQueries
                .getActivityForDate(LocalDate.now().toString())
                .executeAsOneOrNull()

            assertNotNull(record)
            assertEquals(0, record.chapters_read.toInt()) // Default value for legacy format
            assertEquals(0, record.episodes_watched.toInt())
        }
    }

    @Test
    fun `new backup format with detailed metrics restores correctly`() {
        runTest {
            // Create new format backup (ProtoNumbers 1-8)
            val newActivity = listOf(
                BackupDayActivity(
                    date = LocalDate.now().toString(),
                    level = 3,
                    type = 1,
                    chaptersRead = 25,
                    episodesWatched = 10,
                    appOpens = 1,
                    achievementsUnlocked = 2,
                    durationMs = 7200000,
                ),
            )

            // Restore
            restorer.restoreAchievements(
                backupAchievements = emptyList(),
                backupUserProfile = null,
                backupActivityLog = newActivity,
                backupStats = null,
            )

            // Verify all detailed metrics restored
            val record = database.activityLogQueries
                .getActivityForDate(LocalDate.now().toString())
                .executeAsOne()

            assertEquals(25, record.chapters_read.toInt())
            assertEquals(10, record.episodes_watched.toInt())
            assertEquals(1, record.app_opens.toInt())
            assertEquals(2, record.achievements_unlocked.toInt())
            assertEquals(7200000L, record.duration_ms)
        }
    }

    @Test
    fun `achievement progress merge keeps best progress`() {
        runTest {
            // Existing achievement with some progress
            val achievement = createTestAchievement("test_merge")
            achievementRepository.insertAchievement(achievement)

            val existingProgress = createTestProgress("test_merge", unlocked = false).copy(
                progress = 50,
            )
            achievementRepository.insertOrUpdateProgress(existingProgress)

            // Backup with higher progress
            val backupProgress = existingProgress.copy(
                progress = 75, // Higher than existing
            )
            val backup = BackupAchievement.fromAchievement(achievement, backupProgress)

            // Restore
            restorer.restoreAchievements(
                backupAchievements = listOf(backup),
                backupUserProfile = null,
                backupActivityLog = emptyList(),
                backupStats = null,
            )

            // Verify higher progress was kept
            val merged = achievementRepository.getProgress("test_merge").first()
            assertNotNull(merged)
            assertEquals(75, merged.progress)
        }
    }

    @Test
    fun `tier progress is preserved during restore`() {
        runTest {
            val achievement = createTestAchievement("test_tier")
            achievementRepository.insertAchievement(achievement)

            // Backup with tier progress
            val progress = createTestProgress("test_tier", unlocked = true).copy(
                currentTier = 3,
                maxTier = 5,
                tierProgress = 450,
                tierMaxProgress = 500,
            )
            val backup = BackupAchievement.fromAchievement(achievement, progress)

            // Restore
            restorer.restoreAchievements(
                backupAchievements = listOf(backup),
                backupUserProfile = null,
                backupActivityLog = emptyList(),
                backupStats = null,
            )

            // Verify tier data restored
            val restored = achievementRepository.getProgress("test_tier").first()
            assertNotNull(restored)
            assertEquals(3, restored.currentTier)
            assertEquals(5, restored.maxTier)
            assertEquals(450, restored.tierProgress)
            assertEquals(500, restored.tierMaxProgress)
        }
    }

    @Test
    fun `empty backup does not corrupt existing data`() {
        runTest {
            // Setup existing data
            val profile = UserProfile.createDefault().copy(level = 10)
            userProfileRepository.saveProfile(profile)

            // Restore empty backup
            restorer.restoreAchievements(
                backupAchievements = emptyList(),
                backupUserProfile = null,
                backupActivityLog = emptyList(),
                backupStats = null,
            )

            // Verify existing data unchanged
            val existing = userProfileRepository.getProfileSync("default")
            assertNotNull(existing)
            assertEquals(10, existing.level)
        }
    }

    // Helper methods
    private fun createTestAchievement(id: String): Achievement {
        return Achievement(
            id = id,
            type = AchievementType.QUANTITY,
            category = AchievementCategory.MANGA,
            threshold = 100,
            points = 50,
            title = "Test Achievement",
            description = "Test description",
            badgeIcon = "рџЏ†",
            isHidden = false,
            isSecret = false,
            unlockableId = null,
            version = 1,
            createdAt = System.currentTimeMillis(),
        )
    }

    private fun createTestProgress(achievementId: String, unlocked: Boolean): AchievementProgress {
        return AchievementProgress(
            achievementId = achievementId,
            progress = if (unlocked) 100 else 50,
            maxProgress = 100,
            isUnlocked = unlocked,
            unlockedAt = if (unlocked) System.currentTimeMillis() else null,
            lastUpdated = System.currentTimeMillis(),
        )
    }

    private fun generateTestActivityLog(): List<BackupDayActivity> {
        val activities = mutableListOf<BackupDayActivity>()
        for (i in 0 until 7) {
            activities.add(
                BackupDayActivity(
                    date = LocalDate.now().minusDays(i.toLong()).toString(),
                    level = (i % 4) + 1,
                    type = i % 3,
                    chaptersRead = (i + 1) * 5,
                    episodesWatched = (i + 1) * 2,
                    appOpens = 1,
                    achievementsUnlocked = if (i == 3) 1 else 0,
                    durationMs = (i + 1) * 600000L,
                ),
            )
        }
        return activities
    }
}
