package eu.kanade.presentation.achievement.components

import org.junit.jupiter.api.Test
import java.time.YearMonth
import java.util.Locale
import kotlin.test.assertEquals

class AchievementActivityGraphLocaleTest {

    @Test
    fun `short month labels follow locale`() {
        val month = YearMonth.of(2024, 1)

        assertEquals("jan", formatMonthShortLabel(month, Locale.ENGLISH))
        assertEquals("янв", formatMonthShortLabel(month, Locale.forLanguageTag("ru")))
    }

    @Test
    fun `full month labels follow locale`() {
        val month = YearMonth.of(2024, 1)

        assertEquals("January 2024", formatMonthYearLabel(month, Locale.ENGLISH))
        assertEquals("Январь 2024", formatMonthYearLabel(month, Locale.forLanguageTag("ru")))
    }
}
