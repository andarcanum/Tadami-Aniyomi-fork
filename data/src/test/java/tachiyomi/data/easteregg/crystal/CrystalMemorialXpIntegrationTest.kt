package tachiyomi.data.easteregg.crystal

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.AchievementTestBase
import tachiyomi.data.achievement.UnlockableManager
import tachiyomi.data.achievement.UserProfileManager
import tachiyomi.data.achievement.UserProfileRepositoryImpl
import tachiyomi.data.achievement.handler.AchievementCalculator
import tachiyomi.data.achievement.handler.AchievementRuleRegistry
import tachiyomi.data.achievement.handler.PointsManager
import tachiyomi.data.achievement.repository.AchievementRepositoryImpl
import tachiyomi.domain.easteregg.crystal.CrystalEligibilityReason
import tachiyomi.domain.easteregg.crystal.CrystalMemorialIds

class CrystalMemorialXpIntegrationTest : AchievementTestBase() {
    private lateinit var points: PointsManager
    private lateinit var profiles: UserProfileManager
    private lateinit var memorial: CrystalMemorialRepositoryImpl
    private lateinit var calculator: AchievementCalculator
    private lateinit var cosmetics: UnlockableManager

    @BeforeEach
    override fun setup() {
        super.setup()
        points = PointsManager(database)
        profiles = UserProfileManager(UserProfileRepositoryImpl(database))
        memorial = CrystalMemorialRepositoryImpl(database, {})
        driver.execute(
            null,
            "INSERT INTO achievements(id,type,category,threshold,points,title,is_hidden,created_at) VALUES ('crystal_memorial_v1','EVENT','SECRET',1,999,'Crystal',1,0)",
            0,
        )
        val registry = mockk<AchievementRuleRegistry> { every { getRule(any()) } returns null }
        cosmetics = mockk(relaxed = true)
        calculator = AchievementCalculator(
            repository = AchievementRepositoryImpl(
                database,
            ),
            mangaHandler = mockk(relaxed = true), animeHandler = mockk(relaxed = true),
            novelHandler = mockk(
                relaxed = true,
            ),
            diversityChecker = mockk(relaxed = true), streakChecker = mockk(relaxed = true),
            achievementsDatabase = database, ruleRegistry = registry,
            featureCollector = mockk(
                relaxed = true,
            ),
            pointsManager = points,
            mangaRepository = mockk(
                relaxed = true,
            ),
            animeRepository = mockk(relaxed = true), novelRepository = mockk(relaxed = true),
            unlockableManager = cosmetics, userProfileManager = profiles,
            activityDataRepository = mockk(
                relaxed = true,
            ),
        )
    }

    @Test
    fun bothXpEntrypointsUseTheSharedMutationBoundary() = runTest {
        withContext(Dispatchers.IO) {
            database.withProfileMutation {
                val ordinary = launch(start = CoroutineStart.UNDISPATCHED) { points.addPoints(10) }
                val profile = launch(start = CoroutineStart.UNDISPATCHED) { profiles.addXP(20) }
                assertFalse(ordinary.isCompleted, "Ordinary points must wait for the receipt transaction boundary")
                assertFalse(profile.isCompleted, "Profile XP must not bypass the same boundary")
            }
        }
        assertEquals(30, points.getCurrentPoints().totalPoints)
    }

    @Test
    fun derivedXpIsUpdatedBelowAndAcrossLevelBoundaries() = runTest {
        points.addPoints(100)
        var p = database.userProfileQueries.getDefaultProfile().executeAsOne()
        assertEquals(100L, p.current_xp)
        assertEquals(282L, p.xp_to_next_level)
        profiles.addXP(899)
        p = database.userProfileQueries.getDefaultProfile().executeAsOne()
        assertEquals(999L, p.total_xp)
        assertEquals(3L, p.level)
        assertEquals(198L, p.current_xp) // 999 - (282 + 519), not the inconsistent old formulas.
        assertEquals(800L, p.xp_to_next_level)
    }

    @Test
    fun recomputeRepairsMissingProjectionWithoutErasingReceiptXp() = runTest {
        val eligible = memorial.makeEligible(CrystalEligibilityReason.DEBUG)
        memorial.completeMemorial(eligible.generation)
        database.achievementProgressQueries.deleteById(CrystalMemorialIds.GRANT_KEY)
        assertTrue(calculator.calculateInitialProgress().success)
        assertEquals(999, points.getCurrentPoints().totalPoints)
        assertEquals(
            1L,
            database.achievementProgressQueries.getById(CrystalMemorialIds.GRANT_KEY).executeAsOne().is_unlocked,
        )
        coVerify(exactly = 0) { cosmetics.unlockAchievementRewards(match { it.id == CrystalMemorialIds.GRANT_KEY }) }
        coVerify {
            cosmetics.recomputeUnlockablesFromUnlockedAchievements(
                match { list ->
                    list.any {
                        it.id ==
                            CrystalMemorialIds.GRANT_KEY
                    }
                },
            )
        }
        assertTrue(calculator.calculateInitialProgress().success)
        assertEquals(999, points.getCurrentPoints().totalPoints)
    }

    @Test
    fun genericUnlockedProgressWithoutReceiptCannotMintTheGift() = runTest {
        database.achievementProgressQueries.upsert(
            achievement_id = CrystalMemorialIds.GRANT_KEY,
            progress = 1,
            max_progress = 1,
            is_unlocked = 1,
            unlocked_at = 0,
            last_updated = 0,
        )
        assertTrue(calculator.calculateInitialProgress().success)
        assertEquals(0, points.getCurrentPoints().totalPoints)
        assertEquals(
            0L,
            database.achievementProgressQueries.getById(CrystalMemorialIds.GRANT_KEY).executeAsOne().is_unlocked,
        )
    }
}
