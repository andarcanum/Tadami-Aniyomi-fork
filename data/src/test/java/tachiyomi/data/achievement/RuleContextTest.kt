package tachiyomi.data.achievement

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.handler.FeatureUsageCollector
import tachiyomi.data.achievement.handler.RuleContextImpl
import tachiyomi.data.achievement.handler.checkers.DiversityAchievementChecker
import tachiyomi.data.achievement.handler.checkers.StreakAchievementChecker
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.AchievementTier
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.entries.novel.repository.NovelRepository

class RuleContextTest : AchievementTestBase() {

    private lateinit var mangaHandler: MangaDatabaseHandler
    private lateinit var animeHandler: AnimeDatabaseHandler
    private lateinit var novelHandler: NovelDatabaseHandler
    private lateinit var mangaRepository: MangaRepository
    private lateinit var animeRepository: AnimeRepository
    private lateinit var novelRepository: NovelRepository
    private lateinit var diversityChecker: DiversityAchievementChecker
    private lateinit var streakChecker: StreakAchievementChecker
    private lateinit var featureCollector: FeatureUsageCollector
    private lateinit var pointsManager: tachiyomi.data.achievement.handler.PointsManager
    private lateinit var achievementRepository: AchievementRepository

    private lateinit var context: RuleContextImpl

    @BeforeEach
    override fun setup() {
        super.setup()

        mangaHandler = mockk()
        animeHandler = mockk()
        novelHandler = mockk()
        mangaRepository = mockk()
        animeRepository = mockk()
        novelRepository = mockk()
        diversityChecker = mockk()
        streakChecker = mockk()
        featureCollector = mockk()
        pointsManager = mockk()
        achievementRepository = mockk()

        context = RuleContextImpl(
            mangaHandler = mangaHandler,
            animeHandler = animeHandler,
            novelHandler = novelHandler,
            mangaRepository = mangaRepository,
            animeRepository = animeRepository,
            novelRepository = novelRepository,
            diversityChecker = diversityChecker,
            streakChecker = streakChecker,
            featureCollector = featureCollector,
            allProgress = emptyMap(),
            allAchievementsMap = emptyMap(),
        )
    }

    @Test
    fun `getChaptersRead Manga calls MangaDatabaseHandler`() = runTest {
        coEvery { mangaHandler.awaitOneOrNull<Long>(any(), any()) } returns 42L

        val result = context.getChaptersRead(AchievementCategory.MANGA)

        result shouldBe 42
    }

    @Test
    fun `getCurrentPoints correctly calculates tiered achievement points`() = runTest {
        val tieredAchievement = Achievement(
            id = "tiered_1",
            type = AchievementType.QUANTITY,
            category = AchievementCategory.MANGA,
            title = "Tiered 1",
            tiers = listOf(
                AchievementTier(1, 10, 5, "Tier 1"),
                AchievementTier(2, 50, 15, "Tier 2"),
                AchievementTier(3, 100, 30, "Tier 3"),
            ),
        )
        val standardAchievement = Achievement(
            id = "standard_1",
            type = AchievementType.EVENT,
            category = AchievementCategory.MANGA,
            points = 25,
            title = "Standard 1",
        )
        val achievementsMap = mapOf(
            "tiered_1" to tieredAchievement,
            "standard_1" to standardAchievement,
        )

        val progressMap = mapOf(
            "tiered_1" to AchievementProgress(
                achievementId = "tiered_1",
                progress = 60,
                isUnlocked = true,
                currentTier = 2,
                maxTier = 3,
            ),
            "standard_1" to AchievementProgress(
                achievementId = "standard_1",
                progress = 1,
                isUnlocked = true,
            ),
        )

        val testContext = RuleContextImpl(
            mangaHandler = mangaHandler,
            animeHandler = animeHandler,
            novelHandler = novelHandler,
            mangaRepository = mangaRepository,
            animeRepository = animeRepository,
            novelRepository = novelRepository,
            diversityChecker = diversityChecker,
            streakChecker = streakChecker,
            featureCollector = featureCollector,
            allProgress = progressMap,
            allAchievementsMap = achievementsMap,
        )

        // Tiered points up to tier 2: 5 + 15 = 20
        // Standard points: 25
        // Total points: 45
        testContext.getCurrentPoints() shouldBe 45
    }
}
