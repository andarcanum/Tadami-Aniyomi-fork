package tachiyomi.data.achievement

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.data.achievement.handler.PointsManager
import tachiyomi.domain.achievement.model.UserPoints

@Execution(ExecutionMode.CONCURRENT)
class PointsManagerTest : AchievementTestBase() {

    private lateinit var pointsManager: PointsManager

    override fun setup() {
        super.setup()
        pointsManager = PointsManager(database)
    }

    @Test
    fun `initial points are zero`() = runTest {
        val points = pointsManager.getCurrentPoints()
        points.totalPoints shouldBe 0
        points.level shouldBe 1
        points.achievementsUnlocked shouldBe 0
    }

    @Test
    fun `add points increases total`() = runTest {
        pointsManager.addPoints(100)

        val points = pointsManager.getCurrentPoints()
        points.totalPoints shouldBe 100
    }

    @Test
    fun `add multiple points accumulates correctly`() = runTest {
        pointsManager.addPoints(50)
        pointsManager.addPoints(30)
        pointsManager.addPoints(20)

        val points = pointsManager.getCurrentPoints()
        points.totalPoints shouldBe 100
    }

    @Test
    fun `level calculation is correct for level 1`() = runTest {
        pointsManager.addPoints(0)

        val points = pointsManager.getCurrentPoints()
        points.level shouldBe 1
    }

    @Test
    fun `level calculation is correct for level 2`() = runTest {
        pointsManager.addPoints(100)

        val points = pointsManager.getCurrentPoints()
        points.level shouldBe 2
    }

    @Test
    fun `level calculation is correct for level 3`() = runTest {
        pointsManager.addPoints(400)

        val points = pointsManager.getCurrentPoints()
        points.level shouldBe 3
    }

    @Test
    fun `level calculation is correct for level 4`() = runTest {
        pointsManager.addPoints(900)

        val points = pointsManager.getCurrentPoints()
        points.level shouldBe 4
    }

    @Test
    fun `level calculation formula is correct`() {
        // Formula: level = sqrt(points / 100) + 1
        pointsManager.calculateLevel(0) shouldBe 1
        pointsManager.calculateLevel(100) shouldBe 2
        pointsManager.calculateLevel(400) shouldBe 3
        pointsManager.calculateLevel(900) shouldBe 4
        pointsManager.calculateLevel(1600) shouldBe 5
    }

    @Test
    fun `increment unlocked increases count`() = runTest {
        pointsManager.incrementUnlocked()
        pointsManager.incrementUnlocked()
        pointsManager.incrementUnlocked()

        val points = pointsManager.getCurrentPoints()
        points.achievementsUnlocked shouldBe 3
    }

    @Test
    fun `add points does not add negative values`() = runTest {
        pointsManager.addPoints(100)
        pointsManager.addPoints(-50)

        val points = pointsManager.getCurrentPoints()
        points.totalPoints shouldBe 100
    }

    @Test
    fun `add zero points does not change total`() = runTest {
        pointsManager.addPoints(50)
        pointsManager.addPoints(0)

        val points = pointsManager.getCurrentPoints()
        points.totalPoints shouldBe 50
    }

    @Test
    fun `subscribe to points emits initial values`() = runTest {
        val pointsFlow = pointsManager.subscribeToPoints()

        val points = pointsFlow.first()
        points.totalPoints shouldBe 0
        points.level shouldBe 1
        points.achievementsUnlocked shouldBe 0
    }

    @Test
    fun `subscribe to points emits updated values`() = runTest {
        val pointsFlow = pointsManager.subscribeToPoints()

        pointsManager.addPoints(100)
        pointsManager.incrementUnlocked()

        val points = pointsFlow.first()
        points.totalPoints shouldBe 100
        points.level shouldBe 2
        points.achievementsUnlocked shouldBe 1
    }

    @Test
    fun `level recalculates when points are added in batches`() = runTest {
        pointsManager.addPoints(50)
        var points = pointsManager.getCurrentPoints()
        points.level shouldBe 1

        pointsManager.addPoints(50)
        points = pointsManager.getCurrentPoints()
        points.level shouldBe 2

        pointsManager.addPoints(300)
        points = pointsManager.getCurrentPoints()
        points.level shouldBe 3
    }

    @Test
    fun `user points model contains all fields`() = runTest {
        pointsManager.addPoints(500)
        pointsManager.incrementUnlocked()
        pointsManager.incrementUnlocked()

        val points = pointsManager.getCurrentPoints()
        points shouldBe UserPoints(
            totalPoints = 500,
            level = 3,
            achievementsUnlocked = 2,
        )
    }
}
