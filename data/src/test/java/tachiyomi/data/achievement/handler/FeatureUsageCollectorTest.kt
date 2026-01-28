package tachiyomi.data.achievement.handler

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.model.AchievementEvent

class FeatureUsageCollectorTest {

    private lateinit var eventBus: AchievementEventBus
    private lateinit var collector: FeatureUsageCollector

    @BeforeEach
    fun setup() {
        eventBus = mockk(relaxed = true)
        collector = FeatureUsageCollector(eventBus)
    }

    @Test
    fun `onFeatureUsed increments feature count`() = runTest {
        collector.onFeatureUsed(AchievementEvent.Feature.SEARCH, 1)

        assert(collector.getFeatureCount(AchievementEvent.Feature.SEARCH) == 1)
    }

    @Test
    fun `onFeatureUsed increments multiple times`() = runTest {
        collector.onFeatureUsed(AchievementEvent.Feature.DOWNLOAD, 5)
        collector.onFeatureUsed(AchievementEvent.Feature.DOWNLOAD, 3)

        assert(collector.getFeatureCount(AchievementEvent.Feature.DOWNLOAD) == 8)
    }

    @Test
    fun `onFeatureUsed different features tracked separately`() = runTest {
        collector.onFeatureUsed(AchievementEvent.Feature.SEARCH, 10)
        collector.onFeatureUsed(AchievementEvent.Feature.FILTER, 5)

        assert(collector.getFeatureCount(AchievementEvent.Feature.SEARCH) == 10)
        assert(collector.getFeatureCount(AchievementEvent.Feature.FILTER) == 5)
    }

    @Test
    fun `onSessionStart stores session time`() = runTest {
        val timestamp = System.currentTimeMillis()
        val hourOfDay = 3

        collector.onSessionStart(timestamp, hourOfDay)

        // Проверка косвенная - через hasSessionInTimeRange
        assert(collector.hasSessionInTimeRange(2, 5))
    }

    @Test
    fun `onSessionEnd stores session duration`() = runTest {
        collector.onSessionEnd(3_600_000L) // 1 час

        assert(collector.hasLongSession(1_800_000L)) // Есть сессия >= 30 мин
        assert(!collector.hasLongSession(7_200_000L)) // Нет сессии >= 2 часов
    }

    @Test
    fun `hasSessionInTimeRange returns true for matching sessions`() = runTest {
        collector.onSessionStart(System.currentTimeMillis(), 3) // 3 AM
        collector.onSessionStart(System.currentTimeMillis() + 1000, 8) // 8 AM (разный timestamp!)

        assert(collector.hasSessionInTimeRange(2, 5)) // Ночь (2-5)
        assert(collector.hasSessionInTimeRange(6, 9)) // Утро (6-9)
        assert(!collector.hasSessionInTimeRange(10, 14)) // День
    }

    @Test
    fun `hasLongSession returns true for long sessions`() = runTest {
        collector.onSessionEnd(1_800_000L) // 30 минут
        collector.onSessionEnd(7_200_000L) // 2 часа

        assert(collector.hasLongSession(3_600_000L)) // Есть сессия >= 1 часа
        assert(!collector.hasLongSession(10_000_000L)) // Нет сессии >= 10 секунд
    }

    @Test
    fun `getMaxSessionDuration returns maximum`() = runTest {
        collector.onSessionEnd(1_800_000L) // 30 минут
        collector.onSessionEnd(7_200_000L) // 2 часа
        collector.onSessionEnd(3_600_000L) // 1 час

        assert(collector.getMaxSessionDuration() == 7_200_000L)
    }

    @Test
    fun `getAverageSessionDuration calculates correctly`() = runTest {
        collector.onSessionEnd(3_600_000L) // 1 час
        collector.onSessionEnd(7_200_000L) // 2 часа

        val average = collector.getAverageSessionDuration()
        assert(average == 5_400_000L) // (3600000 + 7200000) / 2
    }

    @Test
    fun `getSessionCount returns correct count`() = runTest {
        val baseTime = System.currentTimeMillis()
        collector.onSessionStart(baseTime, 10)
        collector.onSessionStart(baseTime + 1000, 14)
        collector.onSessionStart(baseTime + 2000, 18)

        assert(collector.getSessionCount() == 3)
    }

    @Test
    fun `reset clears all data`() = runTest {
        collector.onFeatureUsed(AchievementEvent.Feature.SEARCH, 10)
        collector.onSessionStart(System.currentTimeMillis(), 3)
        collector.onSessionEnd(3_600_000L)

        collector.reset()

        assert(collector.getFeatureCount(AchievementEvent.Feature.SEARCH) == 0)
        assert(collector.getSessionCount() == 0)
        assert(collector.getMaxSessionDuration() == 0L)
    }
}
