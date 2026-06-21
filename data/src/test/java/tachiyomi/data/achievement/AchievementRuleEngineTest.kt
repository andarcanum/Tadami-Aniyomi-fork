package tachiyomi.data.achievement

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.data.achievement.handler.AchievementHandler
import tachiyomi.data.achievement.handler.AchievementRuleRegistry
import tachiyomi.data.achievement.handler.FeatureUsageCollector
import tachiyomi.data.achievement.handler.PointsManager
import tachiyomi.data.achievement.handler.checkers.DiversityAchievementChecker
import tachiyomi.data.achievement.handler.checkers.FeatureBasedAchievementChecker
import tachiyomi.data.achievement.handler.checkers.StreakAchievementChecker
import tachiyomi.data.achievement.handler.checkers.TimeBasedAchievementChecker
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import tachiyomi.domain.achievement.rule.AchievementRule
import tachiyomi.domain.achievement.rule.RuleResult
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.entries.novel.repository.NovelRepository

class AchievementRuleEngineTest {

    private lateinit var eventBus: AchievementEventBus
    private lateinit var repository: AchievementRepository
    private lateinit var diversityChecker: DiversityAchievementChecker
    private lateinit var streakChecker: StreakAchievementChecker
    private lateinit var timeBasedChecker: TimeBasedAchievementChecker
    private lateinit var featureBasedChecker: FeatureBasedAchievementChecker
    private lateinit var featureCollector: FeatureUsageCollector
    private lateinit var pointsManager: PointsManager
    private lateinit var unlockableManager: UnlockableManager
    private lateinit var mangaHandler: MangaDatabaseHandler
    private lateinit var animeHandler: AnimeDatabaseHandler
    private lateinit var novelHandler: NovelDatabaseHandler
    private lateinit var mangaRepository: MangaRepository
    private lateinit var animeRepository: AnimeRepository
    private lateinit var novelRepository: NovelRepository
    private lateinit var userProfileManager: UserProfileManager
    private lateinit var activityDataRepository: ActivityDataRepository
    private lateinit var ruleRegistry: AchievementRuleRegistry

    private lateinit var handler: AchievementHandler

    @BeforeEach
    fun setup() {
        eventBus = mockk(relaxed = true)
        repository = mockk()
        diversityChecker = mockk(relaxed = true)
        streakChecker = mockk(relaxed = true)
        timeBasedChecker = mockk(relaxed = true)
        featureBasedChecker = mockk(relaxed = true)
        featureCollector = mockk(relaxed = true)
        pointsManager = mockk(relaxed = true)
        unlockableManager = mockk(relaxed = true)
        mangaHandler = mockk(relaxed = true)
        animeHandler = mockk(relaxed = true)
        novelHandler = mockk(relaxed = true)
        mangaRepository = mockk(relaxed = true)
        animeRepository = mockk(relaxed = true)
        novelRepository = mockk(relaxed = true)
        userProfileManager = mockk(relaxed = true)
        activityDataRepository = mockk(relaxed = true)
        ruleRegistry = mockk()

        handler = AchievementHandler(
            eventBus = eventBus,
            repository = repository,
            diversityChecker = diversityChecker,
            streakChecker = streakChecker,
            timeBasedChecker = timeBasedChecker,
            featureBasedChecker = featureBasedChecker,
            featureCollector = featureCollector,
            pointsManager = pointsManager,
            unlockableManager = unlockableManager,
            mangaHandler = mangaHandler,
            animeHandler = animeHandler,
            novelHandler = novelHandler,
            mangaRepository = mangaRepository,
            animeRepository = animeRepository,
            novelRepository = novelRepository,
            userProfileManager = userProfileManager,
            activityDataRepository = activityDataRepository,
            ruleRegistry = ruleRegistry,
        )
    }

    @Test
    fun `engine evaluates meta rules on every event`() = runTest {
        val standardAch = mockk<Achievement> {
            every { id } returns "standard_1"
            every { type } returns AchievementType.QUANTITY
            every { threshold } returns 1
            every { points } returns 10
            every { isTiered } returns false
            every { title } returns "Standard Achievement"
            every { hasRewards } returns false
        }
        val metaAch = mockk<Achievement> {
            every { id } returns "meta_1"
            every { type } returns AchievementType.META
            every { threshold } returns 1
            every { points } returns 20
            every { isTiered } returns false
            every { title } returns "Meta Achievement"
            every { hasRewards } returns false
        }

        val allAchievements = listOf(standardAch, metaAch)
        coEvery { repository.getAll() } returns flowOf(allAchievements)
        coEvery { repository.getAllProgress() } returns flowOf(emptyList())
        coEvery { repository.getProgress("standard_1") } returns flowOf(null)
        coEvery { repository.getProgress("meta_1") } returns flowOf(null)
        coEvery { repository.insertOrUpdateProgress(any()) } returns Unit

        val standardRule = mockk<AchievementRule> {
            every { achievementId } returns "standard_1"
        }
        val metaRule = mockk<AchievementRule> {
            every { achievementId } returns "meta_1"
        }

        every { ruleRegistry.getRule("standard_1") } returns standardRule
        every { ruleRegistry.getRule("meta_1") } returns metaRule

        // Case 1: Standard rule does NOT unlock
        coEvery { standardRule.evaluateDelta(any(), any(), any()) } returns RuleResult.Update(0)
        coEvery { metaRule.evaluateDelta(any(), any(), any()) } returns RuleResult.Update(0)

        val event = AchievementEvent.ChapterRead(1L, 1)
        handler.processEvent(event)

        coVerify(exactly = 1) { standardRule.evaluateDelta(any(), any(), any()) }
        coVerify(exactly = 1) { metaRule.evaluateDelta(any(), any(), any()) }

        // Case 2: Standard rule unlocks (returns progress = 1, matching threshold)
        coEvery { standardRule.evaluateDelta(any(), any(), any()) } returns RuleResult.Update(1)
        coEvery { metaRule.evaluateDelta(any(), any(), any()) } returns RuleResult.Update(1)

        handler.processEvent(event)

        coVerify(exactly = 2) { standardRule.evaluateDelta(any(), any(), any()) }
        coVerify(exactly = 2) { metaRule.evaluateDelta(any(), any(), any()) }
    }
}
