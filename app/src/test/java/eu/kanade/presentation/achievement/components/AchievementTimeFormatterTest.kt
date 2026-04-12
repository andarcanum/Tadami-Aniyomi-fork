package eu.kanade.presentation.achievement.components

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AchievementTimeFormatterTest {

    @Test
    fun `formats hours and minutes when both are present`() {
        val result = formatAchievementTimeMinutes(
            minutes = 125,
            hoursMinutesText = "2h 5m",
            hoursText = "2h",
            minutesText = "5m",
        )

        assertEquals("2h 5m", result)
    }

    @Test
    fun `formats only hours when minutes are zero`() {
        val result = formatAchievementTimeMinutes(
            minutes = 180,
            hoursMinutesText = "3h 0m",
            hoursText = "3h",
            minutesText = "0m",
        )

        assertEquals("3h", result)
    }

    @Test
    fun `formats only minutes when under one hour`() {
        val result = formatAchievementTimeMinutes(
            minutes = 59,
            hoursMinutesText = "0h 59m",
            hoursText = "0h",
            minutesText = "59m",
        )

        assertEquals("59m", result)
    }
}
