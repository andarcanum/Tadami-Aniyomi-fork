package tachiyomi.data.achievement

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.data.achievement.repository.AchievementRepositoryImpl

@Execution(ExecutionMode.CONCURRENT)
class AchievementRepositoryImplTest : AchievementTestBase() {

    private lateinit var repository: AchievementRepositoryImpl

    @org.junit.jupiter.api.BeforeEach
    override fun setup() {
        super.setup()
        repository = AchievementRepositoryImpl(database)
    }

    @Test
    fun `insert achievement and retrieve`() = runTest {
        val achievement = Achievement(
            id = "test_1",
            type = AchievementType.QUANTITY,
            category = AchievementCategory.MANGA,
            threshold = 10,
            points = 100,
            title = "Test Achievement",
            description = "Test Description",
        )

        repository.insertAchievement(achievement)

        val retrieved = repository.getAll().first()
        retrieved.size shouldBe 1
        retrieved[0].id shouldBe "test_1"
        retrieved[0].title shouldBe "Test Achievement"
    }

    @Test
    fun `insert multiple achievements and get all`() = runTest {
        val achievements = listOf(
            Achievement(
                id = "test_1",
                type = AchievementType.QUANTITY,
                category = AchievementCategory.MANGA,
                threshold = 10,
                points = 100,
                title = "First Achievement",
            ),
            Achievement(
                id = "test_2",
                type = AchievementType.DIVERSITY,
                category = AchievementCategory.ANIME,
                threshold = 5,
                points = 50,
                title = "Second Achievement",
            ),
        )

        achievements.forEach { repository.insertAchievement(it) }

        val retrieved = repository.getAll().first()
        retrieved.size shouldBe 2
    }

    @Test
    fun `get achievements by category`() = runTest {
        val mangaAchievement = Achievement(
            id = "manga_1",
            type = AchievementType.QUANTITY,
            category = AchievementCategory.MANGA,
            threshold = 10,
            points = 100,
            title = "Manga Achievement",
        )

        val animeAchievement = Achievement(
            id = "anime_1",
            type = AchievementType.QUANTITY,
            category = AchievementCategory.ANIME,
            threshold = 5,
            points = 50,
            title = "Anime Achievement",
        )

        repository.insertAchievement(mangaAchievement)
        repository.insertAchievement(animeAchievement)

        val mangaResults = repository.getByCategory(AchievementCategory.MANGA).first()
        mangaResults.size shouldBe 1
        mangaResults[0].category shouldBe AchievementCategory.MANGA

        val animeResults = repository.getByCategory(AchievementCategory.ANIME).first()
        animeResults.size shouldBe 1
        animeResults[0].category shouldBe AchievementCategory.ANIME
    }

    @Test
    fun `insert and update progress`() = runTest {
        val achievement = Achievement(
            id = "progress_test",
            type = AchievementType.QUANTITY,
            category = AchievementCategory.MANGA,
            threshold = 100,
            points = 100,
            title = "Progress Test",
        )

        repository.insertAchievement(achievement)

        val progress = AchievementProgress(
            achievementId = "progress_test",
            progress = 50,
            maxProgress = 100,
            isUnlocked = false,
        )

        repository.insertOrUpdateProgress(progress)

        val retrieved = repository.getProgress("progress_test").first()
        retrieved shouldNotBe null
        retrieved!!.progress shouldBe 50
        retrieved.isUnlocked shouldBe false
    }

    @Test
    fun `update progress to unlocked`() = runTest {
        val achievement = Achievement(
            id = "unlock_test",
            type = AchievementType.QUANTITY,
            category = AchievementCategory.MANGA,
            threshold = 100,
            points = 100,
            title = "Unlock Test",
        )

        repository.insertAchievement(achievement)

        val initialProgress = AchievementProgress(
            achievementId = "unlock_test",
            progress = 50,
            maxProgress = 100,
            isUnlocked = false,
        )

        repository.insertOrUpdateProgress(initialProgress)

        val unlockedProgress = AchievementProgress(
            achievementId = "unlock_test",
            progress = 100,
            maxProgress = 100,
            isUnlocked = true,
            unlockedAt = System.currentTimeMillis(),
            lastUpdated = System.currentTimeMillis(),
        )

        repository.updateProgress(unlockedProgress)

        val retrieved = repository.getProgress("unlock_test").first()
        retrieved shouldNotBe null
        retrieved!!.isUnlocked shouldBe true
        retrieved.progress shouldBe 100
    }

    @Test
    fun `get all progress returns empty list when no progress`() = runTest {
        val allProgress = repository.getAllProgress().first()
        allProgress.size shouldBe 0
    }

    @Test
    fun `get all progress returns multiple progress entries`() = runTest {
        val achievements = listOf(
            Achievement(id = "ach1", type = AchievementType.QUANTITY, category = AchievementCategory.MANGA, points = 10, title = "A1"),
            Achievement(id = "ach2", type = AchievementType.QUANTITY, category = AchievementCategory.ANIME, points = 20, title = "A2"),
        )

        achievements.forEach { repository.insertAchievement(it) }

        val progressList = listOf(
            AchievementProgress(achievementId = "ach1", progress = 10, maxProgress = 100),
            AchievementProgress(achievementId = "ach2", progress = 20, maxProgress = 200),
        )

        progressList.forEach { repository.insertOrUpdateProgress(it) }

        val allProgress = repository.getAllProgress().first()
        allProgress.size shouldBe 2
    }

    @Test
    fun `delete achievement by id`() = runTest {
        val achievement = Achievement(
            id = "delete_test",
            type = AchievementType.QUANTITY,
            category = AchievementCategory.MANGA,
            threshold = 10,
            points = 100,
            title = "Delete Test",
        )

        repository.insertAchievement(achievement)

        var allAchievements = repository.getAll().first()
        allAchievements.size shouldBe 1

        repository.deleteAchievement("delete_test")

        allAchievements = repository.getAll().first()
        allAchievements.size shouldBe 0
    }

    @Test
    fun `delete all achievements`() = runTest {
        val achievements = listOf(
            Achievement(id = "del1", type = AchievementType.QUANTITY, category = AchievementCategory.MANGA, points = 10, title = "D1"),
            Achievement(id = "del2", type = AchievementType.DIVERSITY, category = AchievementCategory.ANIME, points = 20, title = "D2"),
        )

        achievements.forEach { repository.insertAchievement(it) }

        repository.getAll().first().size shouldBe 2

        repository.deleteAllAchievements()

        repository.getAll().first().size shouldBe 0
    }

    @Test
    fun `achievement with all fields is stored correctly`() = runTest {
        val achievement = Achievement(
            id = "full_test",
            type = AchievementType.STREAK,
            category = AchievementCategory.BOTH,
            threshold = 30,
            points = 500,
            title = "Full Achievement",
            description = "Complete description",
            badgeIcon = "badge_icon_path",
            isHidden = true,
            isSecret = false,
            unlockableId = "unlock_123",
            version = 2,
            createdAt = 1234567890L,
        )

        repository.insertAchievement(achievement)

        val retrieved = repository.getAll().first()[0]
        retrieved.id shouldBe "full_test"
        retrieved.type shouldBe AchievementType.STREAK
        retrieved.category shouldBe AchievementCategory.BOTH
        retrieved.threshold shouldBe 30
        retrieved.points shouldBe 500
        retrieved.title shouldBe "Full Achievement"
        retrieved.description shouldBe "Complete description"
        retrieved.badgeIcon shouldBe "badge_icon_path"
        retrieved.isHidden shouldBe true
        retrieved.isSecret shouldBe false
        retrieved.unlockableId shouldBe "unlock_123"
        retrieved.version shouldBe 2
        retrieved.createdAt shouldBe 1234567890L
    }
}
