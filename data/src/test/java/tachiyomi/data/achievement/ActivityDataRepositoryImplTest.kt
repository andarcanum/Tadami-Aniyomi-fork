package tachiyomi.data.achievement

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.database.AchievementsDatabase
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ActivityDataRepositoryImplTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: AchievementsDatabase
    private lateinit var repository: ActivityDataRepositoryImpl

    @BeforeEach
    fun setup() {
        // Create in-memory database for testing
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        tachiyomi.data.achievement.AchievementsDatabase.Schema.create(driver)
        database = AchievementsDatabase(driver)
        repository = ActivityDataRepositoryImpl(database)
    }

    @AfterEach
    fun teardown() {
        driver.close()
    }

    @Test
    fun `upsertActivityData inserts new record`() = runTest {
        val date = LocalDate.now()

        repository.upsertActivityData(
            date = date,
            chaptersRead = 10,
            episodesWatched = 5,
            appOpens = 1,
            achievementsUnlocked = 0,
            durationMs = 3600000,
        )

        val activities = repository.getActivityData(days = 1).first()
        assertEquals(1, activities.size)

        val activity = activities.first()
        assertEquals(date, activity.date)
        // Level and type are calculated based on metrics
        assertTrue(activity.level > 0)
    }

    @Test
    fun `upsertActivityData updates existing record`() = runTest {
        val date = LocalDate.now()

        // Insert initial data
        repository.upsertActivityData(
            date = date,
            chaptersRead = 10,
            episodesWatched = 0,
            appOpens = 1,
            achievementsUnlocked = 0,
            durationMs = 600000,
        )

        // Update with new data
        repository.upsertActivityData(
            date = date,
            chaptersRead = 20, // Updated
            episodesWatched = 5, // Added
            appOpens = 1,
            achievementsUnlocked = 1, // Added
            durationMs = 1200000, // Updated
        )

        // Verify database has the updated values (not accumulated)
        val record = database.activityLogQueries.getActivityForDate(date.toString()).executeAsOne()
        assertEquals(20, record.chapters_read.toInt())
        assertEquals(5, record.episodes_watched.toInt())
        assertEquals(1, record.achievements_unlocked.toInt())
        assertEquals(1200000L, record.duration_ms)
    }

    @Test
    fun `getActivityData returns correct date range`() = runTest {
        // Insert 10 days of data
        val today = LocalDate.now()
        for (i in 0 until 10) {
            val date = today.minusDays(i.toLong())
            repository.upsertActivityData(
                date = date,
                chaptersRead = i + 1,
                episodesWatched = 0,
                appOpens = 1,
                achievementsUnlocked = 0,
                durationMs = 600000,
            )
        }

        // Request last 5 days
        val activities = repository.getActivityData(days = 5).first()

        // Should return exactly 5 days (including empty days if needed)
        assertEquals(5, activities.size)

        // Verify date range (oldest to newest)
        assertEquals(today.minusDays(4), activities.first().date)
        assertEquals(today, activities.last().date)
    }

    @Test
    fun `getActivityData includes empty days`() = runTest {
        val today = LocalDate.now()

        // Insert data for today and 2 days ago (skip yesterday)
        repository.upsertActivityData(
            date = today,
            chaptersRead = 10,
            episodesWatched = 0,
            appOpens = 1,
            achievementsUnlocked = 0,
            durationMs = 600000,
        )

        repository.upsertActivityData(
            date = today.minusDays(2),
            chaptersRead = 5,
            episodesWatched = 0,
            appOpens = 1,
            achievementsUnlocked = 0,
            durationMs = 300000,
        )

        // Request 3 days
        val activities = repository.getActivityData(days = 3).first()

        // Should return 3 days including the empty one
        assertEquals(3, activities.size)

        // Yesterday should have level=0 (empty)
        val yesterday = activities[1]
        assertEquals(today.minusDays(1), yesterday.date)
        assertEquals(0, yesterday.level)
    }

    @Test
    fun `getMonthStats aggregates correctly`() = runTest {
        val yearMonth = YearMonth.now()
        val daysInMonth = yearMonth.lengthOfMonth()

        // Insert data for first 10 days of current month
        for (day in 1..10) {
            val date = yearMonth.atDay(day)
            repository.upsertActivityData(
                date = date,
                chaptersRead = 5,
                episodesWatched = 2,
                appOpens = 1,
                achievementsUnlocked = if (day == 5) 1 else 0,
                durationMs = 1800000, // 30 minutes
            )
        }

        val stats = repository.getMonthStats(yearMonth.year, yearMonth.monthValue)

        assertEquals(50, stats.chaptersRead) // 10 days * 5 chapters
        assertEquals(20, stats.episodesWatched) // 10 days * 2 episodes
        assertEquals(300, stats.timeInAppMinutes) // 10 days * 30 minutes
        assertEquals(1, stats.achievementsUnlocked) // Only day 5
    }

    @Test
    fun `getLastTwelveMonthsStats returns correct number of months`() = runTest {
        // Insert some data for current month
        repository.upsertActivityData(
            date = LocalDate.now(),
            chaptersRead = 10,
            episodesWatched = 5,
            appOpens = 1,
            achievementsUnlocked = 0,
            durationMs = 600000,
        )

        val stats = repository.getLastTwelveMonthsStats()

        // Should return exactly 12 months (oldest to newest)
        assertEquals(12, stats.size)

        // Verify order (oldest first)
        val firstMonth = stats.first().first
        val lastMonth = stats.last().first
        assertEquals(YearMonth.now().minusMonths(11), firstMonth)
        assertEquals(YearMonth.now(), lastMonth)
    }

    @Test
    fun `deleteAllActivityData clears all records`() = runTest {
        // Insert multiple records
        for (i in 0 until 5) {
            repository.upsertActivityData(
                date = LocalDate.now().minusDays(i.toLong()),
                chaptersRead = 10,
                episodesWatched = 5,
                appOpens = 1,
                achievementsUnlocked = 0,
                durationMs = 600000,
            )
        }

        // Verify data exists
        val beforeDelete = repository.getActivityData(days = 5).first()
        assertTrue(beforeDelete.isNotEmpty())

        // Delete all
        repository.deleteAllActivityData()

        // Verify all data is gone (should return empty days)
        val afterDelete = repository.getActivityData(days = 5).first()
        assertEquals(5, afterDelete.size) // Still returns 5 days
        assertTrue(afterDelete.all { it.level == 0 }) // But all are empty
    }

    @Test
    fun `recordReading increments chapters correctly`() = runTest {
        val today = LocalDate.now()

        // Record reading twice
        repository.recordReading(id = 1L, chaptersCount = 5, durationMs = 300000)
        repository.recordReading(id = 2L, chaptersCount = 3, durationMs = 200000)

        // Verify accumulated values
        val record = database.activityLogQueries.getActivityForDate(today.toString()).executeAsOne()
        assertEquals(8, record.chapters_read.toInt()) // 5 + 3
        assertEquals(500000L, record.duration_ms) // 300000 + 200000
    }

    @Test
    fun `activity level calculation is correct`() = runTest {
        val date = LocalDate.now()

        // Test different activity levels
        val testCases = listOf(
            Triple(1, 0, 1), // 1 chapter = level 1
            Triple(7, 0, 2), // 7 chapters = level 2
            Triple(15, 0, 3), // 15 chapters = level 3
            Triple(25, 0, 4), // 25 chapters = level 4
            Triple(0, 1, 1), // 1 episode = level 1
            Triple(0, 4, 2), // 4 episodes = level 2
            Triple(0, 7, 3), // 7 episodes = level 3
            Triple(0, 12, 4), // 12 episodes = level 4
        )

        testCases.forEachIndexed { index, (chapters, episodes, expectedLevel) ->
            val testDate = date.minusDays(index.toLong())
            repository.upsertActivityData(
                date = testDate,
                chaptersRead = chapters,
                episodesWatched = episodes,
                appOpens = 1,
                achievementsUnlocked = 0,
                durationMs = 600000,
            )

            val activities = repository.getActivityData(days = index + 1).first()
            val activity = activities.find { it.date == testDate }
            assertNotNull(activity)
            assertEquals(expectedLevel, activity.level, "Failed for chapters=$chapters, episodes=$episodes")
        }
    }

    @Test
    fun `achievement unlocked sets max level`() = runTest {
        val date = LocalDate.now()

        repository.upsertActivityData(
            date = date,
            chaptersRead = 1, // Would normally be level 1
            episodesWatched = 0,
            appOpens = 1,
            achievementsUnlocked = 1, // This should force level 4
            durationMs = 600000,
        )

        val activities = repository.getActivityData(days = 1).first()
        val activity = activities.first()
        assertEquals(4, activity.level) // Max level due to achievement
    }
}
