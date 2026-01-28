package eu.kanade.presentation.achievement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.achievement.components.AchievementActivityGraph
import eu.kanade.presentation.achievement.components.AchievementStatsComparison
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.LocalAuroraColors
import org.junit.Rule
import org.junit.Test
import tachiyomi.domain.achievement.model.*
import java.time.LocalDate

class AchievementScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun statsComparison_displaysCorrectValues() {
        // Given
        val currentStats = MonthStats(
            chaptersRead = 127,
            episodesWatched = 45,
            timeInAppMinutes = 2340,
            achievementsUnlocked = 8,
        )
        val previousStats = MonthStats(
            chaptersRead = 98,
            episodesWatched = 62,
            timeInAppMinutes = 1890,
            achievementsUnlocked = 5,
        )

        // When
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAuroraColors provides AuroraColors.Dark) {
                Box(
                    modifier = Modifier
                        .background(AuroraColors.Dark.background)
                        .padding(16.dp)
                ) {
                    AchievementStatsComparison(
                        currentMonth = currentStats,
                        previousMonth = previousStats,
                    )
                }
            }
        }

        // Then
        composeTestRule.onNodeWithText("Сравнение с прошлым месяцем").assertIsDisplayed()
        composeTestRule.onNodeWithText("127").assertIsDisplayed()
        composeTestRule.onNodeWithText("Глав прочитано").assertIsDisplayed()
    }

    @Test
    fun activityGraph_displaysTitleAndLegend() {
        // Given
        val activityData = generateTestActivityData()

        // When
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAuroraColors provides AuroraColors.Dark) {
                Box(
                    modifier = Modifier
                        .background(AuroraColors.Dark.background)
                        .padding(16.dp)
                ) {
                    AchievementActivityGraph(activityData = activityData)
                }
            }
        }

        // Then
        composeTestRule.onNodeWithText("Активность за год").assertIsDisplayed()
        composeTestRule.onNodeWithText("Меньше").assertIsDisplayed()
        composeTestRule.onNodeWithText("Больше").assertIsDisplayed()
    }

    @Test
    fun statCards_displayWithDifferentValueLengths() {
        // Given - stats with very different value lengths
        val currentStats = MonthStats(
            chaptersRead = 9999,
            episodesWatched = 1,
            timeInAppMinutes = 9999,
            achievementsUnlocked = 999,
        )

        // When
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAuroraColors provides AuroraColors.Dark) {
                Box(
                    modifier = Modifier
                        .background(AuroraColors.Dark.background)
                        .padding(16.dp)
                ) {
                    AchievementStatsComparison(
                        currentMonth = currentStats,
                        previousMonth = currentStats,
                    )
                }
            }
        }

        // Then - all stat cards should be visible with different length values
        composeTestRule.onNodeWithText("9999").assertIsDisplayed()
        composeTestRule.onNodeWithText("1").assertIsDisplayed()
    }

    private fun generateTestActivityData(): List<DayActivity> {
        val data = mutableListOf<DayActivity>()
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(365)

        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            val level = when {
                currentDate.dayOfWeek.value >= 6 -> (0..4).random()
                currentDate.isAfter(endDate.minusMonths(3)) -> (0..4).random()
                else -> (0..3).random()
            }

            if (level > 0) {
                val type = listOf(
                    ActivityType.READING,
                    ActivityType.WATCHING,
                    ActivityType.APP_OPEN
                ).random()
                data.add(DayActivity(date = currentDate, level = level, type = type))
            }

            currentDate = currentDate.plusDays(1)
        }

        return data
    }
}
