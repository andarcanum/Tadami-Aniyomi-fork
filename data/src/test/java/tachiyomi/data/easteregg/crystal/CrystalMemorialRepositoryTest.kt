package tachiyomi.data.easteregg.crystal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.AchievementTestBase
import tachiyomi.data.achievement.handler.PointsManager
import tachiyomi.domain.easteregg.crystal.CrystalCompletionResult
import tachiyomi.domain.easteregg.crystal.CrystalEligibilityReason
import tachiyomi.domain.easteregg.crystal.CrystalMemorialIds
import tachiyomi.db.achievement.AchievementsDatabase as GeneratedDatabase

class CrystalMemorialRepositoryTest : AchievementTestBase() {
    private lateinit var repository: CrystalMemorialRepositoryImpl
    private lateinit var points: PointsManager
    private val owned = mutableSetOf<String>()

    @BeforeEach
    override fun setup() {
        super.setup()
        points = PointsManager(database)
        driver.execute(
            null,
            "INSERT INTO achievements(id,type,category,threshold,points,title,is_hidden,created_at) VALUES ('crystal_memorial_v1','event','secret',1,999,'Crystal',1,0)",
            0,
        )
        repository = CrystalMemorialRepositoryImpl(database, { granted ->
            if (granted) owned.addAll(CrystalMemorialIds.REWARDS) else owned.removeAll(CrystalMemorialIds.REWARDS)
        }, { 1234L })
    }

    @Test
    fun finaleAddsExactly999ToExistingBalanceAndUnlocksSixItems() = runTest {
        points.addPoints(5000)
        val state = repository.makeEligible(CrystalEligibilityReason.DEBUG)
        assertTrue(repository.completeMemorial(state.generation) is CrystalCompletionResult.Granted)
        assertEquals(5999, points.getCurrentPoints().totalPoints)
        assertTrue(repository.getState().isGranted)
        assertEquals(6, owned.size)
        assertEquals(
            1L,
            database.achievementProgressQueries.getById(CrystalMemorialIds.GRANT_KEY).executeAsOne().is_unlocked,
        )
        assertTrue(repository.completeMemorial(state.generation) is CrystalCompletionResult.AlreadyGranted)
        assertEquals(5999, points.getCurrentPoints().totalPoints)
    }

    @Test
    fun lockedSceneCannotGrant() = runTest {
        assertEquals(CrystalCompletionResult.NotEligible, repository.completeMemorial(0L))
        assertEquals(0, points.getCurrentPoints().totalPoints)
        assertFalse(repository.getState().isGranted)
    }

    @Test
    fun resetConsumesOnlyOwnContributionAndEmptyResetDoesNotSubtractAgain() = runTest {
        points.addPoints(1000)
        database.userProfileQueries.updateAchievementCounts(
            user_id = "default",
            unlocked = 3L,
            total = 10L,
            last_updated = 0L,
        )
        owned += "unrelated_reward"
        val state = repository.makeEligible(CrystalEligibilityReason.RELEASE)
        repository.completeMemorial(state.generation)
        points.addPoints(17)
        repository.debugReset()
        assertEquals(1017, points.getCurrentPoints().totalPoints)
        assertEquals(3, points.getCurrentPoints().achievementsUnlocked)
        assertEquals(10L, database.userProfileQueries.getDefaultProfile().executeAsOne().total_achievements)
        assertEquals(setOf("unrelated_reward"), owned)
        repository.debugReset()
        assertEquals(1017, points.getCurrentPoints().totalPoints)
        assertTrue(database.achievementsQueries.getById(CrystalMemorialIds.GRANT_KEY).executeAsOneOrNull() != null)
    }

    @Test
    fun oldScreenCallbackCannotAwardAfterReset() = runTest {
        val previous = repository.makeEligible(CrystalEligibilityReason.DEBUG)
        repository.debugReset()
        val current = repository.makeEligible(CrystalEligibilityReason.DEBUG)
        assertTrue(current.generation > previous.generation)
        assertEquals(CrystalCompletionResult.StaleSession, repository.completeMemorial(previous.generation))
        assertEquals(0, points.getCurrentPoints().totalPoints)
        assertTrue(repository.completeMemorial(current.generation) is CrystalCompletionResult.Granted)
    }

    @Test
    fun concurrentFinalCallbacksCommitOneReceipt() = runTest {
        val state = repository.makeEligible(CrystalEligibilityReason.TITLE)
        val results = (1..20).map {
            async(Dispatchers.Default) { repository.completeMemorial(state.generation) }
        }.awaitAll()
        assertEquals(1, results.count { it is CrystalCompletionResult.Granted })
        assertEquals(999, points.getCurrentPoints().totalPoints)
        assertEquals(1, points.getCurrentPoints().achievementsUnlocked)
    }

    @Test
    fun projectionFailureDoesNotLoseReceiptOrReplayXp() = runTest {
        var fail = true
        val repo = CrystalMemorialRepositoryImpl(database, { granted ->
            if (fail) throw IllegalStateException("Simulated derived preference failure")
            if (granted) owned.addAll(CrystalMemorialIds.REWARDS)
        })
        val state = repo.makeEligible(CrystalEligibilityReason.DEBUG)
        assertTrue(repo.completeMemorial(state.generation) is CrystalCompletionResult.Granted)
        fail = false
        repo.repairProjections()
        assertEquals(6, owned.size)
        assertEquals(999, points.getCurrentPoints().totalPoints)
    }

    @Test
    fun storageFailureAfterXpWriteRollsBackTheWholeGrant() = runTest {
        points.addPoints(500)
        val state = repository.makeEligible(CrystalEligibilityReason.RELEASE)
        driver.execute(
            null,
            "CREATE TRIGGER fail_crystal_counter BEFORE UPDATE OF achievements_unlocked ON user_profile BEGIN SELECT RAISE(ABORT, 'injected storage failure'); END",
            0,
        )
        assertEquals(CrystalCompletionResult.RetryableFailure, repository.completeMemorial(state.generation))
        assertFalse(repository.getState().isGranted)
        assertEquals(500, points.getCurrentPoints().totalPoints)
        assertTrue(
            database.achievementProgressQueries.getById(CrystalMemorialIds.GRANT_KEY).executeAsOneOrNull() == null,
        )
        driver.execute(null, "DROP TRIGGER fail_crystal_counter", 0)
        assertTrue(repository.completeMemorial(state.generation) is CrystalCompletionResult.Granted)
        assertEquals(1499, points.getCurrentPoints().totalPoints)
    }

    @Test
    fun additiveMigrationCreatesReceiptWithoutChangingExistingProfile() = runTest {
        points.addPoints(321)
        // This feature only adds one table; removing it reconstructs the unchanged v9 fixture.
        driver.execute(null, "DROP TABLE crystal_memorial", 0)
        GeneratedDatabase.Schema.migrate(driver, 9, 10)
        assertFalse(repository.getState().isGranted)
        assertEquals(321, points.getCurrentPoints().totalPoints)
        assertEquals(10L, GeneratedDatabase.Schema.version)
    }
}
