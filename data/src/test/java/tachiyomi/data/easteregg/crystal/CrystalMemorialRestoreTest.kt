package tachiyomi.data.easteregg.crystal

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.AchievementTestBase
import tachiyomi.data.achievement.handler.PointsManager
import tachiyomi.domain.easteregg.crystal.CrystalCompletionResult
import tachiyomi.domain.easteregg.crystal.CrystalEligibilityReason
import tachiyomi.domain.easteregg.crystal.CrystalMemorialRestoreException
import tachiyomi.domain.easteregg.crystal.CrystalMemorialSnapshot

class CrystalMemorialRestoreTest : AchievementTestBase() {
    private lateinit var repo: CrystalMemorialRepositoryImpl
    private lateinit var points: PointsManager

    @BeforeEach override fun setup() {
        super.setup()
        points = PointsManager(database)
        repo = CrystalMemorialRepositoryImpl(database, {})
        driver.execute(
            null,
            "INSERT INTO achievements(id,type,category,threshold,points,title,is_hidden,created_at) VALUES ('crystal_memorial_v1','EVENT','SECRET',1,999,'Crystal',1,0)",
            0,
        )
    }
    private suspend fun grantAndSnapshot(): CrystalMemorialSnapshot {
        points.addPoints(1000)
        val state = repo.makeEligible(CrystalEligibilityReason.RELEASE)
        repo.completeMemorial(state.generation)
        return repo.snapshotForBackup()
    }

    @Test fun mergePreservesHigherLocalBaseXpAndAddsUnionedGiftOnce() = runTest {
        val backup = grantAndSnapshot()
        repo.debugReset()
        points.addPoints(4000)
        repo.restoreSnapshot(backup)
        assertEquals(5999, points.getCurrentPoints().totalPoints)
        assertTrue(repo.getState().isGranted)
        repo.restoreSnapshot(backup)
        assertEquals(5999, points.getCurrentPoints().totalPoints)
        assertEquals(1, points.getCurrentPoints().achievementsUnlocked)
    }

    @Test fun oldBackupWithoutReceiptNeverErasesCurrentGift() = runTest {
        grantAndSnapshot()
        val profile = repo.snapshotForBackup().profile!!.copy(totalXP = 6000, achievementsUnlocked = 0)
        repo.restoreSnapshot(CrystalMemorialSnapshot(profile = profile))
        assertTrue(repo.getState().isGranted)
        assertEquals(6999, points.getCurrentPoints().totalPoints)
    }

    @Test fun legacyUnlockedProjectionWithCoherentProfileIsAdoptedWithoutReplay() = runTest {
        val legacy = grantAndSnapshot().copy(state = null)
        repo.debugReset()
        repo.restoreSnapshot(legacy)
        assertEquals(1999, points.getCurrentPoints().totalPoints)
        assertTrue(repo.getState().isGranted)
        assertTrue(repo.completeMemorial(repo.getState().generation) is CrystalCompletionResult.AlreadyGranted)
        assertEquals(1999, points.getCurrentPoints().totalPoints)
    }

    @Test fun incompleteGrantedBackupIsRejectedWithoutChangingLocalState() = runTest {
        val backup = grantAndSnapshot().copy(profile = null)
        repo.debugReset()
        val before = repo.getState()
        var rejected = false
        try {
            repo.restoreSnapshot(backup)
        } catch (_: CrystalMemorialRestoreException) {
            rejected = true
        }
        assertTrue(rejected)
        assertEquals(before, repo.getState())
        assertEquals(1000, points.getCurrentPoints().totalPoints)
    }

    @Test fun restoreInvalidatesOutstandingSceneGeneration() = runTest {
        val backup = grantAndSnapshot()
        val before = repo.getState()
        repo.restoreSnapshot(backup)
        assertEquals(CrystalCompletionResult.StaleSession, repo.completeMemorial(before.generation))
    }
}
