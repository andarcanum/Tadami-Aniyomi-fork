package tachiyomi.data.achievement.handler

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.domain.achievement.rule.AchievementRule
import tachiyomi.domain.achievement.rule.RuleResult

class AchievementHandlerOptimizationTest {

    private val repository = mockk<AchievementRepository>(relaxed = true)
    private val ruleRegistry = mockk<AchievementRuleRegistry>(relaxed = true)

    private fun testAchievement(id: String, type: AchievementType) = Achievement(
        id = id,
        type = type,
        category = AchievementCategory.MANGA,
        threshold = 10,
        points = 10,
        title = "Test $id",
    )

    @Test
    fun `processEvent retrieves all progress in one query and does not poll individual progress`() = runTest {
        val standardAch = testAchievement("std_1", AchievementType.EVENT)
        val metaAch = testAchievement("meta_1", AchievementType.META)
        val achievements = listOf(standardAch, metaAch)

        val progressList = listOf(
            AchievementProgress(achievementId = "std_1", progress = 0, maxProgress = 1, isUnlocked = false),
            AchievementProgress(achievementId = "meta_1", progress = 0, maxProgress = 1, isUnlocked = false),
        )

        every { repository.getAll() } returns flowOf(achievements)
        every { repository.getAllProgress() } returns flowOf(progressList)
        every { repository.getProgress(any()) } returns flowOf(null)

        val handler = AchievementHandler(
            eventBus = mockk(relaxed = true),
            repository = repository,
            diversityChecker = mockk(relaxed = true),
            streakChecker = mockk(relaxed = true),
            timeBasedChecker = mockk(relaxed = true),
            featureBasedChecker = mockk(relaxed = true),
            featureCollector = mockk(relaxed = true),
            pointsManager = mockk(relaxed = true),
            unlockableManager = mockk(relaxed = true),
            mangaHandler = mockk(relaxed = true),
            animeHandler = mockk(relaxed = true),
            novelHandler = mockk(relaxed = true),
            mangaRepository = mockk(relaxed = true),
            animeRepository = mockk(relaxed = true),
            novelRepository = mockk(relaxed = true),
            userProfileManager = mockk(relaxed = true),
            activityDataRepository = mockk(relaxed = true),
            ruleRegistry = ruleRegistry,
        )

        val event = AchievementEvent.ChapterRead(mangaId = 1L, chapterNumber = 1)
        handler.processEvent(event)

        // Verify that we load all progress in bulk exactly once
        coVerify(exactly = 1) { repository.getAllProgress() }

        // Verify that getProgress is NEVER called in a loop (which causes excessive DB queries)
        coVerify(exactly = 0) { repository.getProgress(any()) }
    }

    @Test
    fun `processEvent does not write progress to DB if new progress is equal to current progress`() = runTest {
        val standardAch = testAchievement("std_1", AchievementType.EVENT)
        val achievements = listOf(standardAch)

        val progressList = listOf(
            AchievementProgress(achievementId = "std_1", progress = 5, maxProgress = 10, isUnlocked = false),
        )

        every { repository.getAll() } returns flowOf(achievements)
        every { repository.getAllProgress() } returns flowOf(progressList)
        every { repository.getProgress(any()) } returns flowOf(null)

        val rule = mockk<AchievementRule>()
        // Rule returns Update(5) - same progress!
        every { ruleRegistry.getRule("std_1") } returns rule
        coEvery { rule.evaluateDelta(any(), any(), any()) } returns RuleResult.Update(5)

        val handler = AchievementHandler(
            eventBus = mockk(relaxed = true),
            repository = repository,
            diversityChecker = mockk(relaxed = true),
            streakChecker = mockk(relaxed = true),
            timeBasedChecker = mockk(relaxed = true),
            featureBasedChecker = mockk(relaxed = true),
            featureCollector = mockk(relaxed = true),
            pointsManager = mockk(relaxed = true),
            unlockableManager = mockk(relaxed = true),
            mangaHandler = mockk(relaxed = true),
            animeHandler = mockk(relaxed = true),
            novelHandler = mockk(relaxed = true),
            mangaRepository = mockk(relaxed = true),
            animeRepository = mockk(relaxed = true),
            novelRepository = mockk(relaxed = true),
            userProfileManager = mockk(relaxed = true),
            activityDataRepository = mockk(relaxed = true),
            ruleRegistry = ruleRegistry,
        )

        val event = AchievementEvent.ChapterRead(mangaId = 1L, chapterNumber = 1)
        handler.processEvent(event)

        // Verify that insertOrUpdateProgress is NEVER called because progress is unchanged
        coVerify(exactly = 0) { repository.insertOrUpdateProgress(any()) }
    }
}
