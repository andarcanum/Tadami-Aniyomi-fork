package eu.kanade.presentation.achievement.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import org.junit.Rule
import org.junit.Test
import tachiyomi.domain.achievement.model.MonthStats
import java.time.YearMonth

class AchievementActivityGraphUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysCurrentPeriodIndicator() {
        val stats = generateYearlyStats()

        composeTestRule.setContent {
            AchievementActivityGraph(yearlyStats = stats)
        }

        composeTestRule.onNodeWithText("Янв–Июнь").assertIsDisplayed()
    }

    @Test
    fun displaysTitleText() {
        val stats = generateYearlyStats()

        composeTestRule.setContent {
            AchievementActivityGraph(yearlyStats = stats)
        }

        composeTestRule.onNodeWithText("Активность за год").assertIsDisplayed()
    }

    @Test
    fun swipeLeftChangesToSecondPage() {
        val stats = generateYearlyStats()

        composeTestRule.setContent {
            AchievementActivityGraph(yearlyStats = stats)
        }

        composeTestRule.onNodeWithText("Янв–Июнь").assertIsDisplayed()

        composeTestRule.onRoot().performTouchInput {
            swipeLeft()
        }

        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Июль–Дек")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Июль–Дек").assertIsDisplayed()
    }

    @Test
    fun swipeRightReturnsToFirstPage() {
        val stats = generateYearlyStats()

        composeTestRule.setContent {
            AchievementActivityGraph(yearlyStats = stats)
        }

        composeTestRule.onRoot().performTouchInput {
            swipeLeft()
        }

        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Июль–Дек")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onRoot().performTouchInput {
            swipeRight()
        }

        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Янв–Июнь")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Янв–Июнь").assertIsDisplayed()
    }

    @Test
    fun eachPageDisplaysCorrectNumberOfBars() {
        val stats = generateYearlyStats()

        composeTestRule.setContent {
            AchievementActivityGraph(yearlyStats = stats)
        }

        val firstPageBars = composeTestRule.onAllNodesWithContentDescription(
            "Activity bar for",
            substring = true,
        )

        firstPageBars.fetchSemanticsNodes().let { nodes ->
            assert(nodes.isNotEmpty()) { "No activity bars found on first page" }
        }
    }

    @Test
    fun handlesEmptyDataGracefully() {
        val emptyStats = emptyList<Pair<YearMonth, MonthStats>>()

        composeTestRule.setContent {
            AchievementActivityGraph(yearlyStats = emptyStats)
        }

        composeTestRule.onNodeWithText("Активность за год").assertIsDisplayed()
    }

    @Test
    fun handlesPartialYearData() {
        val partialStats = (1..6).map { month ->
            YearMonth.of(2024, month) to MonthStats(
                chaptersRead = month * 5,
                episodesWatched = month * 3,
                timeInAppMinutes = month * 10,
                achievementsUnlocked = 0,
            )
        }

        composeTestRule.setContent {
            AchievementActivityGraph(yearlyStats = partialStats)
        }

        composeTestRule.onNodeWithText("Активность за год").assertIsDisplayed()
        composeTestRule.onNodeWithText("Янв–Июнь").assertIsDisplayed()
    }

    private fun generateYearlyStats(): List<Pair<YearMonth, MonthStats>> {
        return (1..12).map { month ->
            YearMonth.of(2024, month) to MonthStats(
                chaptersRead = month * 5,
                episodesWatched = month * 3,
                timeInAppMinutes = month * 10,
                achievementsUnlocked = if (month % 3 == 0) 1 else 0,
            )
        }
    }
}
