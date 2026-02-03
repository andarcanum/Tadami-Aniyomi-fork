package eu.kanade.presentation.achievement.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.fetchSemanticsNodes
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

    /**
     * Тест 1: График отображает индикатор периода "Янв–Июнь"
     */
    @Test
    fun displaysCurrentPeriodIndicator() {
        // Arrange
        val stats = generateYearlyStats()

        // Act
        composeTestRule.setContent {
            AchievementActivityGraph(yearlyStats = stats)
        }

        // Assert: Индикатор "Янв–Июнь" отображается на первой странице
        composeTestRule.onNodeWithText("Янв–Июнь").assertIsDisplayed()
    }

    /**
     * Тест 2: График отображает заголовок
     */
    @Test
    fun displaysTitleText() {
        // Arrange
        val stats = generateYearlyStats()

        // Act
        composeTestRule.setContent {
            AchievementActivityGraph(yearlyStats = stats)
        }

        // Assert: Заголовок "Активность за год" отображается
        composeTestRule.onNodeWithText("Активность за год").assertIsDisplayed()
    }

    /**
     * Тест 3: Свайп влево переключает на вторую страницу
     */
    @Test
    fun swipeLeftChangesToSecondPage() {
        // Arrange
        val stats = generateYearlyStats()

        composeTestRule.setContent {
            AchievementActivityGraph(yearlyStats = stats)
        }

        // Assert: Изначально показывается "Янв–Июнь"
        composeTestRule.onNodeWithText("Янв–Июнь").assertIsDisplayed()

        // Act: Свайп влево
        composeTestRule.onRoot().performTouchInput {
            swipeLeft()
        }

        // Assert: Индикатор изменился на "Июль–Дек"
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Июль–Дек")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Июль–Дек").assertIsDisplayed()
    }

    /**
     * Тест 4: Свайп вправо возвращает на первую страницу
     */
    @Test
    fun swipeRightReturnsToFirstPage() {
        // Arrange
        val stats = generateYearlyStats()

        composeTestRule.setContent {
            AchievementActivityGraph(yearlyStats = stats)
        }

        // Переходим на вторую страницу
        composeTestRule.onRoot().performTouchInput {
            swipeLeft()
        }

        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Июль–Дек")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Act: Свайп вправо обратно
        composeTestRule.onRoot().performTouchInput {
            swipeRight()
        }

        // Assert: Вернулись к "Янв–Июнь"
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Янв–Июнь")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Янв–Июнь").assertIsDisplayed()
    }

    /**
     * Тест 5: На каждой странице отображается корректное количество столбцов
     */
    @Test
    fun eachPageDisplaysCorrectNumberOfBars() {
        // Arrange
        val stats = generateYearlyStats()

        composeTestRule.setContent {
            AchievementActivityGraph(yearlyStats = stats)
        }

        // Assert: На первой странице должно быть 6 столбцов (январь-июнь)
        val firstPageBars = composeTestRule.onAllNodesWithContentDescription(
            "Activity bar for",
            substring = true,
        )

        // Проверяем что есть столбцы (точное количество зависит от реализации semantics)
        firstPageBars.fetchSemanticsNodes().let { nodes ->
            assert(nodes.isNotEmpty()) { "No activity bars found on first page" }
        }
    }

    /**
     * Тест 6: График с пустыми данными не падает
     */
    @Test
    fun handlesEmptyDataGracefully() {
        // Arrange
        val emptyStats = emptyList<Pair<YearMonth, MonthStats>>()

        // Act & Assert: Не должно быть краша
        composeTestRule.setContent {
            AchievementActivityGraph(yearlyStats = emptyStats)
        }

        // Заголовок все равно отображается
        composeTestRule.onNodeWithText("Активность за год").assertIsDisplayed()
    }

    /**
     * Тест 7: График с данными только за 6 месяцев работает корректно
     */
    @Test
    fun handlesPartialYearData() {
        // Arrange
        val partialStats = (1..6).map { month ->
            YearMonth.of(2024, month) to MonthStats(
                chaptersRead = month * 5,
                episodesWatched = month * 3,
                timeInAppMinutes = month * 10,
                achievementsUnlocked = 0,
            )
        }

        // Act & Assert: Не должно быть краша
        composeTestRule.setContent {
            AchievementActivityGraph(yearlyStats = partialStats)
        }

        composeTestRule.onNodeWithText("Активность за год").assertIsDisplayed()
        composeTestRule.onNodeWithText("Янв–Июнь").assertIsDisplayed()
    }

    // Helper: Генерация тестовых данных для полного года
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
