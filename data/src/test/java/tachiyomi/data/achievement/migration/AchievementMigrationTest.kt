package tachiyomi.data.achievement.migration

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.AchievementTier
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.repository.AchievementRepository

class AchievementMigrationTest {

    private lateinit var repository: AchievementRepository
    private lateinit var migrationHelper: AchievementMigrationHelper

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        migrationHelper = AchievementMigrationHelper(repository)
    }

    @Test
    fun `mergeIntoTiered merges multiple achievements into one`() = runTest {
        // Подготовка данных
        val oldAchievements = listOf(
            Achievement(
                id = "read_10_chapters",
                type = AchievementType.QUANTITY,
                category = AchievementCategory.MANGA,
                threshold = 10,
                points = 10,
                title = "Read 10 Chapters",
            ),
            Achievement(
                id = "read_50_chapters",
                type = AchievementType.QUANTITY,
                category = AchievementCategory.MANGA,
                threshold = 50,
                points = 50,
                title = "Read 50 Chapters",
            ),
            Achievement(
                id = "read_100_chapters",
                type = AchievementType.QUANTITY,
                category = AchievementCategory.MANGA,
                threshold = 100,
                points = 100,
                title = "Read 100 Chapters",
            ),
        )

        val oldProgress = listOf(
            AchievementProgress(
                achievementId = "read_10_chapters",
                progress = 10,
                maxProgress = 10,
                isUnlocked = true,
            ),
            AchievementProgress(
                achievementId = "read_50_chapters",
                progress = 50,
                maxProgress = 50,
                isUnlocked = true,
            ),
            AchievementProgress(
                achievementId = "read_100_chapters",
                progress = 75,
                maxProgress = 100,
                isUnlocked = false,
            ),
        )

        val tiers = listOf(
            AchievementTier.bronze(10, 10, "Новичок", "Прочитано 10 глав"),
            AchievementTier.silver(50, 50, "Читатель", "Прочитано 50 глав"),
            AchievementTier.gold(100, 100, "Библиофил", "Прочитано 100 глав"),
        )

        coEvery { repository.getAll() } returns flowOf(oldAchievements)
        coEvery { repository.getAllProgress() } returns flowOf(oldProgress)

        // Выполнение миграции
        val result = migrationHelper.mergeIntoTiered(
            baseId = "reader_chapters",
            oldIds = listOf("read_10_chapters", "read_50_chapters", "read_100_chapters"),
            tiers = tiers,
            migrateProgress = true,
        )

        // Проверка результатов
        assert(result is MigrationResult.Success)
        val successResult = result as MigrationResult.Success
        assert(successResult.mergedCount == 3)
        assert(successResult.newAchievementId == "reader_chapters")
        assert(successResult.currentTier == 2) // Прогресс 75 -> silver tier (50)
        assert(successResult.maxTier == 3)

        // Проверка вызовов
        coVerify(exactly = 3) { repository.deleteAchievement(any()) }
        coVerify(exactly = 1) { repository.insertAchievement(any()) }
        coVerify(exactly = 1) { repository.insertOrUpdateProgress(any()) }
    }

    @Test
    fun `mergeIntoTiered returns NothingToMigrate when no old achievements exist`() = runTest {
        coEvery { repository.getAll() } returns flowOf(emptyList())
        coEvery { repository.getAllProgress() } returns flowOf(emptyList())

        val tiers = listOf(
            AchievementTier.bronze(10, 10, "Tier 1", "Level 1"),
        )

        val result = migrationHelper.mergeIntoTiered(
            baseId = "test_achievement",
            oldIds = listOf("old_1", "old_2"),
            tiers = tiers,
        )

        assert(result is MigrationResult.NothingToMigrate)
    }

    @Test
    fun `mergeIntoTiered correctly calculates tier progress`() = runTest {
        val oldAchievements = listOf(
            Achievement(
                id = "old_achievement",
                type = AchievementType.QUANTITY,
                category = AchievementCategory.MANGA,
                threshold = 100,
                points = 100,
                title = "Old Achievement",
            ),
        )

        val oldProgress = listOf(
            AchievementProgress(
                achievementId = "old_achievement",
                progress = 45,
                maxProgress = 100,
                isUnlocked = false,
            ),
        )

        val tiers = listOf(
            AchievementTier.bronze(10, 10, "Tier 1", "Level 1"),
            AchievementTier.silver(50, 50, "Tier 2", "Level 2"),
            AchievementTier.gold(100, 100, "Tier 3", "Level 3"),
        )

        coEvery { repository.getAll() } returns flowOf(oldAchievements)
        coEvery { repository.getAllProgress() } returns flowOf(oldProgress)

        val result = migrationHelper.mergeIntoTiered(
            baseId = "new_tiered",
            oldIds = listOf("old_achievement"),
            tiers = tiers,
            migrateProgress = true,
        )

        assert(result is MigrationResult.Success)
        val successResult = result as MigrationResult.Success
        // Прогресс 45 -> bronze tier (10), но не silver (50)
        assert(successResult.currentTier == 1)
    }

    @Test
    fun `mergeIntoTiered handles max tier correctly`() = runTest {
        val oldAchievements = listOf(
            Achievement(
                id = "old_max",
                type = AchievementType.QUANTITY,
                category = AchievementCategory.MANGA,
                threshold = 150,
                points = 150,
                title = "Old Max",
            ),
        )

        val oldProgress = listOf(
            AchievementProgress(
                achievementId = "old_max",
                progress = 150,
                maxProgress = 150,
                isUnlocked = true,
            ),
        )

        val tiers = listOf(
            AchievementTier.bronze(10, 10, "Tier 1", "Level 1"),
            AchievementTier.silver(50, 50, "Tier 2", "Level 2"),
            AchievementTier.gold(100, 100, "Tier 3", "Level 3"),
        )

        coEvery { repository.getAll() } returns flowOf(oldAchievements)
        coEvery { repository.getAllProgress() } returns flowOf(oldProgress)

        val result = migrationHelper.mergeIntoTiered(
            baseId = "max_tiered",
            oldIds = listOf("old_max"),
            tiers = tiers,
            migrateProgress = true,
        )

        assert(result is MigrationResult.Success)
        val successResult = result as MigrationResult.Success
        // Прогресс 150 -> gold tier (100), ограничен максимальным уровнем
        assert(successResult.currentTier == 3)
    }

    @Test
    fun `AchievementTier factory methods create correct tiers`() {
        val bronzeTier = AchievementTier.bronze(
            threshold = 10,
            points = 10,
            title = "Bronze",
        )
        assert(bronzeTier.level == 1)
        assert(bronzeTier.threshold == 10)
        assert(bronzeTier.points == 10)

        val silverTier = AchievementTier.silver(
            threshold = 50,
            points = 50,
            title = "Silver",
        )
        assert(silverTier.level == 2)
        assert(silverTier.threshold == 50)

        val goldTier = AchievementTier.gold(
            threshold = 100,
            points = 100,
            title = "Gold",
        )
        assert(goldTier.level == 3)
        assert(goldTier.threshold == 100)

        val customTier = AchievementTier.custom(
            level = 5,
            threshold = 500,
            points = 500,
            title = "Custom",
        )
        assert(customTier.level == 5)
        assert(customTier.threshold == 500)
    }
}
