package tachiyomi.data.achievement.migration

import kotlinx.coroutines.flow.first
import tachiyomi.data.achievement.handler.AchievementCalculator
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.AchievementTier
import tachiyomi.domain.achievement.repository.AchievementRepository

/**
 * Хелпер для миграции достижений в многоуровневую систему
 *
 * Пример использования:
 * ```
 * val migrationHelper = AchievementMigrationHelper(repository)
 *
 * // Объединить достижения "Прочитано 10/50/100 глав" в одно tiers
 * migrationHelper.mergeQuantityAchievements(
 *     baseId = "reader_chapters",
 *     oldIds = listOf("read_10_chapters", "read_50_chapters", "read_100_chapters"),
 *     tiers = listOf(
 *         AchievementTier.bronze(10, 10, "Читатель", "Прочитано 10 глав"),
 *         AchievementTier.silver(50, 50, "Продвинутый читатель", "Прочитано 50 глав"),
 *         AchievementTier.gold(100, 100, "Мастер чтения", "Прочитано 100 глав")
 *     )
 * )
 * ```
 */
class AchievementMigrationHelper(
    private val repository: AchievementRepository,
) {

    /**
     * Объединяет несколько достижений в одно многоуровневое
     *
     * @param baseId ID нового объединенного достижения
     * @param oldIds Список ID старых достижений для объединения
     * @param tiers Уровни нового достижения
     * @param migrateProgress Перенести прогресс из старых достижений
     */
    suspend fun mergeIntoTiered(
        baseId: String,
        oldIds: List<String>,
        tiers: List<AchievementTier>,
        migrateProgress: Boolean = true,
    ): MigrationResult {
        val allAchievements = repository.getAll().first()
        val allProgress = repository.getAllProgress().first()

        // Проверяем существование старых достижений
        val existingOldAchievements = allAchievements.filter { it.id in oldIds }
        if (existingOldAchievements.isEmpty()) {
            return MigrationResult.NothingToMigrate
        }

        // Создаем новое многоуровневое достижение
        val baseAchievement = existingOldAchievements.firstOrNull()
        val newAchievement = Achievement.createTiered(
            id = baseId,
            type = baseAchievement?.type ?: tachiyomi.domain.achievement.model.AchievementType.QUANTITY,
            category = baseAchievement?.category ?: tachiyomi.domain.achievement.model.AchievementCategory.MANGA,
            tiers = tiers,
            title = baseAchievement?.title ?: "Tiered Achievement",
            description = baseAchievement?.description,
            badgeIcon = baseAchievement?.badgeIcon,
            isHidden = baseAchievement?.isHidden ?: false,
            isSecret = baseAchievement?.isSecret ?: false,
        )

        // Вычисляем прогресс на основе старых достижений
        val migratedProgress = if (migrateProgress) {
            calculateMigratedProgress(oldIds, allProgress, tiers)
        } else {
            0
        }

        val currentTier = tiers.indexOfLast { migratedProgress >= it.threshold } + 1
        val nextTier = tiers.getOrNull(currentTier)

        val tierProgress = if (nextTier != null) {
            val previousThreshold = tiers.getOrNull(currentTier - 1)?.threshold ?: 0
            migratedProgress - previousThreshold
        } else {
            0
        }

        val tierMaxProgress = if (nextTier != null) {
            val previousThreshold = tiers.getOrNull(currentTier - 1)?.threshold ?: 0
            nextTier.threshold - previousThreshold
        } else {
            100
        }

        // Создаем прогресс для нового достижения
        val newProgress = AchievementProgress.createTiered(
            achievementId = baseId,
            progress = migratedProgress,
            currentTier = currentTier,
            maxTier = tiers.size,
            tierProgress = tierProgress,
            tierMaxProgress = tierMaxProgress,
            isUnlocked = currentTier > 0,
            unlockedAt = if (currentTier > 0) System.currentTimeMillis() else null,
        )

        // Удаляем старые достижения и их прогресс
        oldIds.forEach { oldId ->
            repository.deleteAchievement(oldId)
        }

        // Добавляем новое достижение
        repository.insertAchievement(newAchievement)
        repository.insertOrUpdateProgress(newProgress)

        return MigrationResult.Success(
            mergedCount = oldIds.size,
            newAchievementId = baseId,
            currentTier = currentTier,
            maxTier = tiers.size,
        )
    }

    /**
     * Вычисляет прогресс на основе старых достижений
     */
    private fun calculateMigratedProgress(
        oldIds: List<String>,
        allProgress: List<AchievementProgress>,
        tiers: List<AchievementTier>,
    ): Int {
        // Находим максимальный прогресс среди старых достижений
        val maxProgress = oldIds
            .mapNotNull { oldId -> allProgress.find { it.achievementId == oldId } }
            .maxOfOrNull { it.progress } ?: 0

        // Если прогресс уже превышает максимальный уровень, возвращаем его
        return maxProgress.coerceAtMost(tiers.last().threshold)
    }

    /**
     * Создает примеры многоуровневых достижений для миграции
     */
    companion object {
        /**
         * Пример: Читатель (глава/манга)
         */
        fun createReaderTiers() = listOf(
            AchievementTier.bronze(
                threshold = 10,
                points = 10,
                title = "Новичок",
                description = "Прочитано 10 глав",
            ),
            AchievementTier.silver(
                threshold = 50,
                points = 50,
                title = "Читатель",
                description = "Прочитано 50 глав",
            ),
            AchievementTier.gold(
                threshold = 100,
                points = 100,
                title = "Библиофил",
                description = "Прочитано 100 глав",
            ),
        )

        /**
         * Пример: Исследователь (источники)
         */
        fun createExplorerTiers() = listOf(
            AchievementTier.bronze(
                threshold = 3,
                points = 20,
                title = "Исследователь",
                description = "Использовано 3 источника",
            ),
            AchievementTier.silver(
                threshold = 5,
                points = 50,
                title = "Путешественник",
                description = "Использовано 5 источников",
            ),
            AchievementTier.gold(
                threshold = 10,
                points = 100,
                title = "Мегапутешественник",
                description = "Использовано 10 источников",
            ),
        )

        /**
         * Пример: Коллекционер (библиотека)
         */
        fun createCollectorTiers() = listOf(
            AchievementTier.bronze(
                threshold = 10,
                points = 20,
                title = "Коллекционер",
                description = "В библиотеке 10 тайтлов",
            ),
            AchievementTier.silver(
                threshold = 50,
                points = 50,
                title = "Хранитель",
                description = "В библиотеке 50 тайтлов",
            ),
            AchievementTier.gold(
                threshold = 100,
                points = 100,
                title = "Архивариус",
                description = "В библиотеке 100 тайтлов",
            ),
        )
    }
}

/**
 * Результат миграции
 */
sealed class MigrationResult {
    data object NothingToMigrate : MigrationResult()
    data class Success(
        val mergedCount: Int,
        val newAchievementId: String,
        val currentTier: Int,
        val maxTier: Int,
    ) : MigrationResult()
}
