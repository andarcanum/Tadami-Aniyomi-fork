package eu.kanade.presentation.achievement.screenmodel

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.handler.PointsManager
import tachiyomi.data.achievement.loader.AchievementLoader
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.model.DayActivity
import tachiyomi.domain.achievement.model.MonthStats
import tachiyomi.domain.achievement.model.UserPoints
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import java.time.LocalDate

class AchievementScreenModelTest {

    private val repository: AchievementRepository = mockk()
    private val loader: AchievementLoader = mockk()
    private val pointsManager: PointsManager = mockk()
    private val activityDataRepository: ActivityDataRepository = mockk()

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        coEvery { repository.getAll() } returns flowOf(emptyList())
        coEvery { repository.getAllProgress() } returns flowOf(emptyList())
        every { pointsManager.subscribeToPoints() } returns flowOf(UserPoints())
        coEvery { activityDataRepository.getActivityData(any()) } returns flowOf(emptyList())
        coEvery { activityDataRepository.getCurrentMonthStats() } returns MonthStats(0, 0, 0, 0)
        coEvery { activityDataRepository.getPreviousMonthStats() } returns MonthStats(0, 0, 0, 0)
        coEvery { activityDataRepository.getLastTwelveMonthsStats() } returns emptyList()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should load activity data into Success state`() {
        runTest {
            // Given
            val activity =
                listOf(DayActivity(LocalDate.now(), 1, tachiyomi.domain.achievement.model.ActivityType.APP_OPEN))
            coEvery { activityDataRepository.getActivityData(365) } returns flowOf(activity)

            val screenModel = AchievementScreenModel(repository, loader, pointsManager, activityDataRepository)

            // When
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            val state = screenModel.state.value
            state.shouldBeInstanceOf<AchievementScreenState.Success>()
            (state as AchievementScreenState.Success).activityData shouldBe activity
        }
    }

    @Test
    fun `filtered achievements include NOVEL and BOTH for NOVEL tab`() {
        val novelAchievement = Achievement(
            id = "novel_1",
            type = AchievementType.EVENT,
            category = AchievementCategory.NOVEL,
            title = "Novel",
        )
        val bothAchievement = Achievement(
            id = "both_1",
            type = AchievementType.EVENT,
            category = AchievementCategory.BOTH,
            title = "Both",
        )
        val mangaAchievement = Achievement(
            id = "manga_1",
            type = AchievementType.EVENT,
            category = AchievementCategory.MANGA,
            title = "Manga",
        )

        val state = AchievementScreenState.Success(
            achievements = listOf(novelAchievement, bothAchievement, mangaAchievement),
            selectedCategory = AchievementCategory.NOVEL,
        )

        state.filteredAchievements shouldBe listOf(novelAchievement, bothAchievement)
    }
}
