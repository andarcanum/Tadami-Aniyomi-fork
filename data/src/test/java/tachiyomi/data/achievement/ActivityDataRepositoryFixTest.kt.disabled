package tachiyomi.data.achievement

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.achievement.model.ActivityType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ActivityDataRepositoryFixTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var repository: ActivityDataRepositoryImpl

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    @BeforeEach
    fun setup() {
        context = mockk(relaxed = true)
        prefs = mockk(relaxed = true)

        every { context.getSharedPreferences("activity_data", Context.MODE_PRIVATE) } returns prefs
        
        repository = ActivityDataRepositoryImpl(context, Dispatchers.Unconfined)
    }

    @Test
    fun `getActivityData returns both reading and watching activities for same day`() = runTest {
        // Given
        val today = LocalDate.now()
        val todayStr = today.format(dateFormatter)

        every { prefs.getInt("chapters_$todayStr", 0) } returns 5
        every { prefs.getInt("episodes_$todayStr", 0) } returns 3
        every { prefs.getInt("app_opens_$todayStr", 0) } returns 1

        // When
        val activities = repository.getActivityData(days = 1).first()

        // Then
        assertEquals(2, activities.size)
        // Order is not strictly guaranteed by map/add logic but likely sequential
        assertTrue(activities.any { it.type == ActivityType.READING && it.level == 2 })
        assertTrue(activities.any { it.type == ActivityType.WATCHING && it.level == 2 })
    }

    @Test
    fun `getActivityData returns correct data for 365 days`() = runTest {
        // Given
        val today = LocalDate.now()
        val todayStr = today.format(dateFormatter)
        val lastYear = today.minusDays(364)
        val lastYearStr = lastYear.format(dateFormatter)

        every { prefs.getInt("chapters_$todayStr", 0) } returns 10
        every { prefs.getInt("chapters_$lastYearStr", 0) } returns 5
        
        // Mock default 0 for everything else to avoid massive mocking overhead? 
        // Mockk "relaxed = true" handles methods returning simple values, but for getInt specific keys, 
        // we might need to be careful. Relaxed mock returns 0 for ints by default.
        
        // When
        val activities = repository.getActivityData(days = 365).first()

        // Then
        // Should have at least 2 entries
        assertTrue(activities.size >= 2)
        
        val todayActivity = activities.find { it.date == today && it.type == ActivityType.READING }
        val lastYearActivity = activities.find { it.date == lastYear && it.type == ActivityType.READING }

        assertEquals(3, todayActivity?.level)
        assertEquals(2, lastYearActivity?.level)
    }
}