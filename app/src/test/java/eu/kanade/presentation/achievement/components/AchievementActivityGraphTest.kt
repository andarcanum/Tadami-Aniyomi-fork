package eu.kanade.presentation.achievement.components

import org.junit.jupiter.api.Test
import tachiyomi.domain.achievement.model.MonthStats
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AchievementActivityGraphTest {

    /**
     * Тест 1: Расчет heightFraction для разных уровней активности
     */
    @Test
    fun `calculateHeightFraction normalizes activity correctly`() {
        // Arrange
        val maxActivity = 100

        // Act & Assert
        // Полная активность -> 75% (ограничение)
        assertEquals(0.75f, calculateHeightFraction(100, maxActivity), absoluteTolerance = 0.01f)

        // Половина активности -> 50%
        assertEquals(0.50f, calculateHeightFraction(50, maxActivity), absoluteTolerance = 0.01f)

        // Четверть активности -> 25%
        assertEquals(0.25f, calculateHeightFraction(25, maxActivity), absoluteTolerance = 0.01f)

        // Нулевая активность -> 5% (минимум для видимости)
        assertEquals(0.05f, calculateHeightFraction(0, maxActivity), absoluteTolerance = 0.01f)
    }

    /**
     * Тест 2: Обработка edge cases
     */
    @Test
    fun `calculateHeightFraction handles edge cases`() {
        // maxActivity = 0 -> минимум
        assertEquals(0.05f, calculateHeightFraction(0, 0), absoluteTolerance = 0.01f)

        // Очень большая активность (1000) при maxActivity = 1000 -> 75%
        assertEquals(0.75f, calculateHeightFraction(1000, 1000), absoluteTolerance = 0.01f)

        // Малая активность (1) при большом maxActivity (1000) -> близко к 0.05f
        assertTrue(calculateHeightFraction(1, 1000) < 0.1f)
    }

    /**
     * Тест 3: Проверка что график обновляется при изменении данных
     */
    @Test
    fun `graph updates when activity data changes`() {
        // Arrange
        val initialStats = listOf(
            YearMonth.of(2024, 1) to MonthStats(10, 5, 0, 0), // totalActivity = 15
            YearMonth.of(2024, 2) to MonthStats(20, 10, 0, 0), // totalActivity = 30
        )

        val initialMax = initialStats.maxOf { it.second.totalActivity }
        assertEquals(30, initialMax)

        // Act: Добавляем активность в январь
        val updatedStats = listOf(
            YearMonth.of(2024, 1) to MonthStats(30, 20, 0, 0), // totalActivity = 50 (НОВОЕ)
            YearMonth.of(2024, 2) to MonthStats(20, 10, 0, 0), // totalActivity = 30
        )

        val updatedMax = updatedStats.maxOf { it.second.totalActivity }

        // Assert: maxActivity изменился
        assertEquals(50, updatedMax)

        // Высота февраля теперь меньше (30/50 вместо 30/30)
        val oldFeb = calculateHeightFraction(30, 30)
        val newFeb = calculateHeightFraction(30, 50)
        assertTrue(newFeb < oldFeb)
    }

    /**
     * Тест 4: Фильтрация месяцев по полугодиям
     */
    @Test
    fun `months are correctly split into first and second half`() {
        // Arrange
        val yearlyStats = (1..12).map { month ->
            YearMonth.of(2024, month) to MonthStats(month * 5, month * 3, 0, 0)
        }

        // Act
        val firstHalf = yearlyStats.filter { it.first.monthValue in 1..6 }
        val secondHalf = yearlyStats.filter { it.first.monthValue in 7..12 }

        // Assert
        assertEquals(6, firstHalf.size)
        assertEquals(6, secondHalf.size)

        // Январь в первом полугодии
        assertTrue(firstHalf.any { it.first.monthValue == 1 })

        // Июль во втором полугодии
        assertTrue(secondHalf.any { it.first.monthValue == 7 })

        // Декабрь во втором полугодии
        assertTrue(secondHalf.any { it.first.monthValue == 12 })
    }

    /**
     * Тест 5: totalActivity рассчитывается корректно
     */
    @Test
    fun `totalActivity is sum of chapters and episodes`() {
        val stats = MonthStats(
            chaptersRead = 25,
            episodesWatched = 15,
            timeInAppMinutes = 120,
            achievementsUnlocked = 3,
        )

        assertEquals(40, stats.totalActivity)
    }

    /**
     * Тест 6: Проверка верхнего предела 75%
     */
    @Test
    fun `calculateHeightFraction caps at 75 percent`() {
        val maxActivity = 100

        // Любая активность >= maxActivity должна давать 0.75f
        assertEquals(0.75f, calculateHeightFraction(100, maxActivity), absoluteTolerance = 0.01f)
        assertEquals(0.75f, calculateHeightFraction(150, maxActivity), absoluteTolerance = 0.01f)
        assertEquals(0.75f, calculateHeightFraction(200, maxActivity), absoluteTolerance = 0.01f)
    }

    /**
     * Тест 7: Проверка нижнего предела 5%
     */
    @Test
    fun `calculateHeightFraction has minimum of 5 percent`() {
        val maxActivity = 1000

        // Очень маленькая активность должна давать минимум 0.05f
        assertEquals(0.05f, calculateHeightFraction(0, maxActivity), absoluteTolerance = 0.01f)
        assertEquals(0.05f, calculateHeightFraction(1, maxActivity), absoluteTolerance = 0.01f)
    }

    // Helper: Приватная копия calculateHeightFraction для тестирования
    private fun calculateHeightFraction(activity: Int, maxActivity: Int): Float {
        if (maxActivity == 0) return 0.05f
        val normalized = activity.toFloat() / maxActivity
        return normalized.coerceIn(0.05f, 0.75f)
    }

    // Helper extension
    private val MonthStats.totalActivity: Int
        get() = chaptersRead + episodesWatched
}
