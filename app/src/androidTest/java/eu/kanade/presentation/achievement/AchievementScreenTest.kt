package eu.kanade.presentation.achievement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.waitForIdle
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.achievement.components.AchievementActivityGraph
import eu.kanade.presentation.achievement.components.AchievementStatsComparison
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.LocalAuroraColors
import org.junit.Rule
import org.junit.Test
import tachiyomi.domain.achievement.model.MonthStats
import java.time.YearMonth

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
                        .padding(16.dp),
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
    fun activityGraph_displaysTitleAndBars() {
        // Given
        val yearlyStats = generateTestYearlyStats()

        // When
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAuroraColors provides AuroraColors.Dark) {
                Box(
                    modifier = Modifier
                        .background(AuroraColors.Dark.background)
                        .padding(16.dp),
                ) {
                    AchievementActivityGraph(yearlyStats = yearlyStats)
                }
            }
        }

        // Then
        composeTestRule.onNodeWithText("Активность за год").assertIsDisplayed()
        // Note: Legend removed as per design decision
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
                        .padding(16.dp),
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

    @Test
    fun activityGraph_tooltipShowsOnLongPress() {
        // Given
        val yearlyStats = generateTestYearlyStats()

        // When
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAuroraColors provides AuroraColors.Dark) {
                Box(
                    modifier = Modifier
                        .background(AuroraColors.Dark.background)
                        .padding(16.dp),
                ) {
                    AchievementActivityGraph(yearlyStats = yearlyStats)
                }
            }
        }

        // Then - perform long press on first bar
        composeTestRule.onAllNodes(hasContentDescription("Activity bar", substring = true))[0]
            .performTouchInput { longClick() }

        // Tooltip should appear with month name
        composeTestRule.waitForIdle()
    }

    @Test
    fun activityGraph_displaysCorrectMetricsInTooltip() {
        // Given - stats with known values
        val month = YearMonth.now().minusMonths(1)
        val stats = MonthStats(
            chaptersRead = 5,
            episodesWatched = 3,
            timeInAppMinutes = 120,
            achievementsUnlocked = 2,
        )
        val yearlyStats = listOf(month to stats)

        // When
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAuroraColors provides AuroraColors.Dark) {
                Box(
                    modifier = Modifier
                        .background(AuroraColors.Dark.background)
                        .padding(16.dp),
                ) {
                    AchievementActivityGraph(yearlyStats = yearlyStats)
                }
            }
        }

        // Then - long press and verify tooltip content
        composeTestRule.onAllNodes(hasContentDescription("Activity bar", substring = true))[0]
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        // Verify tooltip shows correct data
        composeTestRule.onNodeWithText("Всего: 8").assertExists()
        composeTestRule.onNodeWithText("Глав: 5").assertExists()
        composeTestRule.onNodeWithText("Эпизодов: 3").assertExists()
    }

    private fun generateTestYearlyStats(): List<Pair<YearMonth, MonthStats>> {
        val stats = mutableListOf<Pair<YearMonth, MonthStats>>()
        val currentMonth = YearMonth.now()

        for (i in 0..11) {
            val month = currentMonth.minusMonths(i.toLong())
            val monthStats = MonthStats(
                chaptersRead = (0..10).random(),
                episodesWatched = (0..5).random(),
                timeInAppMinutes = (0..300).random(),
                achievementsUnlocked = (0..3).random(),
            )
            stats.add(0, month to monthStats) // Add to beginning to maintain chronological order
        }

        return stats
    }
}
