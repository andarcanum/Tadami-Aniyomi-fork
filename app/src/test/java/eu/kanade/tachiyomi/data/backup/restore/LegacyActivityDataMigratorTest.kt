package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tachiyomi.data.achievement.ActivityDataRepositoryImpl
import tachiyomi.data.achievement.database.AchievementsDatabase
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LegacyActivityDataMigratorTest {

    private lateinit var context: Context
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: AchievementsDatabase
    private lateinit var repository: ActivityDataRepositoryImpl
    private lateinit var migrator: LegacyActivityDataMigrator

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        // Clear any existing SharedPreferences
        context.getSharedPreferences("activity_data", Context.MODE_PRIVATE).edit().clear().apply()

        // Setup database
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        tachiyomi.data.achievement.AchievementsDatabase.Schema.create(driver)
        database = AchievementsDatabase(driver)
        repository = ActivityDataRepositoryImpl(database)

        migrator = LegacyActivityDataMigrator(context, repository)
    }

    @After
    fun teardown() {
        context.getSharedPreferences("activity_data", Context.MODE_PRIVATE).edit().clear().apply()
        driver.close()
    }

    @Test
    fun `isMigrationNeeded returns false when no legacy data`() {
        runTest {
            val needed = migrator.isMigrationNeeded()
            assertFalse(needed)
        }
    }

    @Test
    fun `isMigrationNeeded returns false when already migrated`() {
        runTest {
            // Populate legacy data
            val prefs = context.getSharedPreferences("activity_data", Context.MODE_PRIVATE)
            prefs.edit()
                .putInt("chapters_2024-01-15", 10)
                .putBoolean("legacy_activity_migrated_v5", true) // Already migrated flag
                .apply()

            val needed = migrator.isMigrationNeeded()
            assertFalse(needed)
        }
    }

    @Test
    fun `isMigrationNeeded returns true when legacy data exists`() {
        runTest {
            // Populate legacy data
            val prefs = context.getSharedPreferences("activity_data", Context.MODE_PRIVATE)
            prefs.edit()
                .putInt("chapters_2024-01-15", 10)
                .putInt("episodes_2024-01-15", 5)
                .apply()

            val needed = migrator.isMigrationNeeded()
            assertTrue(needed)
        }
    }

    @Test
    fun `migrate transfers all data correctly`() {
        runTest {
            val prefs = context.getSharedPreferences("activity_data", Context.MODE_PRIVATE)
            val date1 = "2024-01-15"
            val date2 = "2024-01-16"

            // Populate legacy data for 2 days
            prefs.edit()
                .putInt("chapters_$date1", 10)
                .putInt("episodes_$date1", 5)
                .putInt("app_opens_$date1", 1)
                .putInt("achievements_$date1", 0)
                .putLong("duration_$date1", 3600000L)
                .putInt("chapters_$date2", 20)
                .putInt("episodes_$date2", 0)
                .putInt("app_opens_$date2", 1)
                .putInt("achievements_$date2", 1)
                .putLong("duration_$date2", 7200000L)
                .apply()

            val result = migrator.migrate()

            assertTrue(result.success)
            assertEquals(2, result.recordsMigrated)
            assertEquals(0, result.recordsFailed)

            // Verify data in database
            val record1 = database.activityLogQueries.getActivityForDate(date1).executeAsOne()
            assertEquals(10, record1.chapters_read.toInt())
            assertEquals(5, record1.episodes_watched.toInt())
            assertEquals(1, record1.app_opens.toInt())
            assertEquals(0, record1.achievements_unlocked.toInt())
            assertEquals(3600000L, record1.duration_ms)

            val record2 = database.activityLogQueries.getActivityForDate(date2).executeAsOne()
            assertEquals(20, record2.chapters_read.toInt())
            assertEquals(0, record2.episodes_watched.toInt())
            assertEquals(1, record2.app_opens.toInt())
            assertEquals(1, record2.achievements_unlocked.toInt())
            assertEquals(7200000L, record2.duration_ms)
        }
    }

    @Test
    fun `migrate skips empty records`() {
        runTest {
            val prefs = context.getSharedPreferences("activity_data", Context.MODE_PRIVATE)

            // One real record, one empty record
            prefs.edit()
                .putInt("chapters_2024-01-15", 10)
                .putInt("chapters_2024-01-16", 0) // All zeros
                .putInt("episodes_2024-01-16", 0)
                .apply()

            val result = migrator.migrate()

            assertTrue(result.success)
            assertEquals(1, result.recordsMigrated) // Only one non-empty record

            // Empty record should not be in database
            val emptyRecord = database.activityLogQueries.getActivityForDate("2024-01-16").executeAsOneOrNull()
            assertEquals(null, emptyRecord)
        }
    }

    @Test
    fun `migrate handles partial data for a date`() {
        runTest {
            val prefs = context.getSharedPreferences("activity_data", Context.MODE_PRIVATE)
            val date = "2024-01-15"

            // Only chapters, no episodes or duration
            prefs.edit()
                .putInt("chapters_$date", 15)
                .apply()

            val result = migrator.migrate()

            assertTrue(result.success)
            assertEquals(1, result.recordsMigrated)

            val record = database.activityLogQueries.getActivityForDate(date).executeAsOne()
            assertEquals(15, record.chapters_read.toInt())
            assertEquals(0, record.episodes_watched.toInt()) // Default
            assertEquals(0, record.app_opens.toInt()) // Default
            assertEquals(0, record.duration_ms) // Default
        }
    }

    @Test
    fun `migrate sets completion flag`() {
        runTest {
            val prefs = context.getSharedPreferences("activity_data", Context.MODE_PRIVATE)

            prefs.edit()
                .putInt("chapters_2024-01-15", 10)
                .apply()

            assertFalse(prefs.getBoolean("legacy_activity_migrated_v5", false))

            migrator.migrate()

            assertTrue(prefs.getBoolean("legacy_activity_migrated_v5", false))
        }
    }

    @Test
    fun `migrate ignores invalid date formats`() {
        runTest {
            val prefs = context.getSharedPreferences("activity_data", Context.MODE_PRIVATE)

            prefs.edit()
                .putInt("chapters_2024-01-15", 10) // Valid
                .putInt("chapters_invalid-date", 5) // Invalid
                .putInt("chapters_20240115", 5) // Wrong format
                .apply()

            val result = migrator.migrate()

            assertTrue(result.success)
            assertEquals(1, result.recordsMigrated) // Only valid date
        }
    }

    @Test
    fun `clearLegacyData removes all legacy keys`() {
        runTest {
            val prefs = context.getSharedPreferences("activity_data", Context.MODE_PRIVATE)

            prefs.edit()
                .putInt("chapters_2024-01-15", 10)
                .putInt("episodes_2024-01-15", 5)
                .putInt("app_opens_2024-01-16", 1)
                .putInt("achievements_2024-01-17", 1)
                .putLong("duration_2024-01-18", 3600000L)
                .putString("some_other_key", "value") // Non-legacy key
                .apply()

            migrator.clearLegacyData()

            // Legacy keys should be gone
            assertFalse(prefs.contains("chapters_2024-01-15"))
            assertFalse(prefs.contains("episodes_2024-01-15"))
            assertFalse(prefs.contains("app_opens_2024-01-16"))
            assertFalse(prefs.contains("achievements_2024-01-17"))
            assertFalse(prefs.contains("duration_2024-01-18"))

            // Non-legacy key should remain
            assertTrue(prefs.contains("some_other_key"))
        }
    }

    @Test
    fun `migrate handles large dataset efficiently`() {
        runTest {
            val prefs = context.getSharedPreferences("activity_data", Context.MODE_PRIVATE)
            val editor = prefs.edit()

            // Generate 365 days of data
            val today = LocalDate.now()
            for (i in 0 until 365) {
                val date = today.minusDays(i.toLong()).toString()
                editor.putInt("chapters_$date", (i % 20) + 1)
                editor.putInt("episodes_$date", (i % 10) + 1)
                editor.putInt("app_opens_$date", 1)
                editor.putLong("duration_$date", (i % 60) * 60000L)
            }
            editor.apply()

            val result = migrator.migrate()

            assertTrue(result.success)
            assertEquals(365, result.recordsMigrated)
            assertEquals(0, result.recordsFailed)
            assertTrue(result.duration > 0) // Should complete in reasonable time
        }
    }

    @Test
    fun `second migration attempt is skipped`() {
        runTest {
            val prefs = context.getSharedPreferences("activity_data", Context.MODE_PRIVATE)
            prefs.edit()
                .putInt("chapters_2024-01-15", 10)
                .apply()

            // First migration
            val result1 = migrator.migrate()
            assertTrue(result1.success)
            assertEquals(1, result1.recordsMigrated)

            // Second migration attempt
            val needed = migrator.isMigrationNeeded()
            assertFalse(needed) // Should be skipped
        }
    }
}
