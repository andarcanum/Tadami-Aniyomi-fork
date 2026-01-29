package tachiyomi.data.achievement

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.achievement.model.ActivityType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ActivityDataRepositoryTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var repository: ActivityDataRepositoryImpl

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    @BeforeEach
    fun setup() {
        context = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)

        every { context.getSharedPreferences("activity_data", Context.MODE_PRIVATE) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putInt(any(), any()) } returns editor
        every { editor.apply() } returns Unit

        repository = ActivityDataRepositoryImpl(context, Dispatchers.Unconfined)
    }

    @Test
    fun `recordReading should save chapters count for today`() = runTest {
        // Given
        val today = LocalDate.now().format(dateFormatter)
        every { prefs.getInt("chapters_$today", 0) } returns 5

        // When
        repository.recordReading(3)

        // Then
        verify { editor.putInt("chapters_$today", 8) }
        verify { editor.apply() }
    }

    @Test
    fun `recordWatching should save episodes count for today`() = runTest {
        // Given
        val today = LocalDate.now().format(dateFormatter)
        every { prefs.getInt("episodes_$today", 0) } returns 2

        // When
        repository.recordWatching(2)

        // Then
        verify { editor.putInt("episodes_$today", 4) }
        verify { editor.apply() }
    }

    @Test
    fun `recordAppOpen should increment app open count for today`() = runTest {
        // Given
        val today = LocalDate.now().format(dateFormatter)
        every { prefs.getInt("app_opens_$today", 0) } returns 1

        // When
        repository.recordAppOpen()

        // Then
        verify { editor.putInt("app_opens_$today", 2) }
        verify { editor.apply() }
    }

    @Test
    fun `getActivityData should return reading activities`() = runTest {
        // Given
        val today = LocalDate.now()
        val todayStr = today.format(dateFormatter)

        every { prefs.getInt("chapters_$todayStr", 0) } returns 10
        every { prefs.getInt("episodes_$todayStr", 0) } returns 0
        every { prefs.getInt("app_opens_$todayStr", 0) } returns 0

        // When
        val activities = repository.getActivityData(days = 1).first()

        // Then
        assertEquals(1, activities.size)
        assertEquals(ActivityType.READING, activities[0].type)
        assertEquals(3, activities[0].level) // 10 chapters = level 3
        assertEquals(today, activities[0].date)
    }

    @Test
    fun `getActivityData should return watching activities`() = runTest {
        // Given
        val today = LocalDate.now()
        val todayStr = today.format(dateFormatter)

        every { prefs.getInt("chapters_$todayStr", 0) } returns 0
        every { prefs.getInt("episodes_$todayStr", 0) } returns 5
        every { prefs.getInt("app_opens_$todayStr", 0) } returns 0

        // When
        val activities = repository.getActivityData(days = 1).first()

        // Then
        assertEquals(1, activities.size)
        assertEquals(ActivityType.WATCHING, activities[0].type)
        assertEquals(3, activities[0].level) // 5 episodes = level 3
    }

    @Test
    fun `getActivityData should return app open activities when no other activity`() = runTest {
        // Given
        val today = LocalDate.now()
        val todayStr = today.format(dateFormatter)

        every { prefs.getInt("chapters_$todayStr", 0) } returns 0
        every { prefs.getInt("episodes_$todayStr", 0) } returns 0
        every { prefs.getInt("app_opens_$todayStr", 0) } returns 1

        // When
        val activities = repository.getActivityData(days = 1).first()

        // Then
        assertEquals(1, activities.size)
        assertEquals(ActivityType.APP_OPEN, activities[0].type)
        assertEquals(1, activities[0].level)
    }

    @Test
    fun `getActivityData should not return app open when reading or watching exists`() = runTest {
        // Given
        val today = LocalDate.now()
        val todayStr = today.format(dateFormatter)

        every { prefs.getInt("chapters_$todayStr", 0) } returns 5
        every { prefs.getInt("episodes_$todayStr", 0) } returns 0
        every { prefs.getInt("app_opens_$todayStr", 0) } returns 1

        // When
        val activities = repository.getActivityData(days = 1).first()

        // Then
        assertEquals(1, activities.size)
        assertEquals(ActivityType.READING, activities[0].type)
    }

    @Test
    fun `getMonthStats should calculate correct stats for given month`() = runBlocking {
        // Given - simulate 3 days of activity in January 2024
        val yearMonth = java.time.YearMonth.of(2024, 1)

        for (day in 1..3) {
            val dateStr = yearMonth.atDay(day).format(dateFormatter)
            every { prefs.getInt("chapters_$dateStr", 0) } returns 5
            every { prefs.getInt("episodes_$dateStr", 0) } returns 2
            every { prefs.getInt("app_opens_$dateStr", 0) } returns 3
            every { prefs.getInt("achievements_$dateStr", 0) } returns 1
        }

        // Remaining days return 0
        for (day in 4..31) {
            val dateStr = yearMonth.atDay(day).format(dateFormatter)
            every { prefs.getInt("chapters_$dateStr", 0) } returns 0
            every { prefs.getInt("episodes_$dateStr", 0) } returns 0
            every { prefs.getInt("app_opens_$dateStr", 0) } returns 0
            every { prefs.getInt("achievements_$dateStr", 0) } returns 0
        }

        // When
        val stats = repository.getMonthStats(2024, 1)

        // Then
        assertEquals(15, stats.chaptersRead) // 5 * 3
        assertEquals(6, stats.episodesWatched) // 2 * 3
        assertEquals(3, stats.achievementsUnlocked) // 1 * 3
        // timeInAppMinutes = 15 * 5 + 6 * 20 = 75 + 120 = 195
        assertEquals(195, stats.timeInAppMinutes)
    }

    @Test
    fun `getActivityData should calculate correct activity levels for reading`() = runTest {
        // Given
        val today = LocalDate.now()
        val todayStr = today.format(dateFormatter)

        // Test different reading levels
        every { prefs.getInt("chapters_$todayStr", 0) } returnsMany listOf(1, 5, 10, 20)
        every { prefs.getInt("episodes_$todayStr", 0) } returns 0
        every { prefs.getInt("app_opens_$todayStr", 0) } returns 0

        // When & Then
        val level1 = repository.getActivityData(days = 1).first()
        assertEquals(1, level1[0].level)

        every { prefs.getInt("chapters_$todayStr", 0) } returns 5
        val level2 = repository.getActivityData(days = 1).first()
        assertEquals(2, level2[0].level)

        every { prefs.getInt("chapters_$todayStr", 0) } returns 10
        val level3 = repository.getActivityData(days = 1).first()
        assertEquals(3, level3[0].level)

        every { prefs.getInt("chapters_$todayStr", 0) } returns 20
        val level4 = repository.getActivityData(days = 1).first()
        assertEquals(4, level4[0].level)
    }

    @Test
    fun `getActivityData should calculate correct activity levels for watching`() = runTest {
        // Given
        val today = LocalDate.now()
        val todayStr = today.format(dateFormatter)

        every { prefs.getInt("chapters_$todayStr", 0) } returns 0
        every { prefs.getInt("app_opens_$todayStr", 0) } returns 0

        // Test level 1 (1 episode)
        every { prefs.getInt("episodes_$todayStr", 0) } returns 1
        val level1 = repository.getActivityData(days = 1).first()
        assertEquals(1, level1[0].level)

        // Test level 2 (2 episodes)
        every { prefs.getInt("episodes_$todayStr", 0) } returns 2
        val level2 = repository.getActivityData(days = 1).first()
        assertEquals(2, level2[0].level)

        // Test level 3 (5 episodes)
        every { prefs.getInt("episodes_$todayStr", 0) } returns 5
        val level3 = repository.getActivityData(days = 1).first()
        assertEquals(3, level3[0].level)

        // Test level 4 (10 episodes)
        every { prefs.getInt("episodes_$todayStr", 0) } returns 10
        val level4 = repository.getActivityData(days = 1).first()
        assertEquals(4, level4[0].level)
    }

    @Test
    fun `getActivityData should return empty list when no activity`() = runTest {
        // Given
        val today = LocalDate.now()
        val todayStr = today.format(dateFormatter)

        every { prefs.getInt("chapters_$todayStr", 0) } returns 0
        every { prefs.getInt("episodes_$todayStr", 0) } returns 0
        every { prefs.getInt("app_opens_$todayStr", 0) } returns 0

        // When
        val activities = repository.getActivityData(days = 1).first()

        // Then
        assertTrue(activities.isEmpty())
    }

    @Test
    fun `getActivityData should return activities sorted by date descending`() = runTest {
        // Given
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val todayStr = today.format(dateFormatter)
        val yesterdayStr = yesterday.format(dateFormatter)

        every { prefs.getInt("chapters_$todayStr", 0) } returns 5
        every { prefs.getInt("episodes_$todayStr", 0) } returns 0
        every { prefs.getInt("app_opens_$todayStr", 0) } returns 0

        every { prefs.getInt("chapters_$yesterdayStr", 0) } returns 0
        every { prefs.getInt("episodes_$yesterdayStr", 0) } returns 3
        every { prefs.getInt("app_opens_$yesterdayStr", 0) } returns 0

        // When
        val activities = repository.getActivityData(days = 2).first()

        // Then
        assertEquals(2, activities.size)
        assertEquals(today, activities[0].date)
        assertEquals(yesterday, activities[1].date)
    }
}
