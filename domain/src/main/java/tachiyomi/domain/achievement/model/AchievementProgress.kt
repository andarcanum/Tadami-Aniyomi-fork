package tachiyomi.domain.achievement.model

import androidx.compose.runtime.Immutable

@Immutable
data class AchievementProgress(
    val achievementId: String,
    val progress: Int = 0,
    val maxProgress: Int = 100,
    val isUnlocked: Boolean = false,
    val unlockedAt: Long? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    // Многоуровневая система
    val currentTier: Int = 0, // Текущий достигнутый уровень (0 = нет уровня)
    val maxTier: Int = 0, // Максимальный уровень в достижении
    val tierProgress: Int = 0, // Прогресс до следующего уровня
    val tierMaxProgress: Int = 100, // Максимум прогресса для текущего уровня
) {
    /**
     * Процент выполнения текущего уровня (0.0 - 1.0)
     */
    val tierPercentage: Float
        get() = if (tierMaxProgress > 0) {
            tierProgress.toFloat() / tierMaxProgress.toFloat()
        } else {
            0f
        }

    /**
     * Является ли это прогрессом для многоуровневого достижения
     */
    val isTiered: Boolean
        get() = maxTier > 0

    /**
     * Получить название текущего уровня
     */
    fun getCurrentTierName(): String? {
        if (currentTier == 0) return null
        return TierLevel.fromLevel(currentTier)?.displayName
    }

    companion object {
        /**
         * Создать прогресс для обычного (не tiers) достижения
         */
        fun createStandard(
            achievementId: String,
            progress: Int = 0,
            maxProgress: Int = 100,
            isUnlocked: Boolean = false,
            unlockedAt: Long? = null,
            lastUpdated: Long = System.currentTimeMillis(),
        ): AchievementProgress {
            return AchievementProgress(
                achievementId = achievementId,
                progress = progress,
                maxProgress = maxProgress,
                isUnlocked = isUnlocked,
                unlockedAt = unlockedAt,
                lastUpdated = lastUpdated,
                currentTier = 0,
                maxTier = 0,
                tierProgress = 0,
                tierMaxProgress = 100,
            )
        }

        /**
         * Создать прогресс для многоуровневого достижения
         */
        fun createTiered(
            achievementId: String,
            progress: Int,
            currentTier: Int,
            maxTier: Int,
            tierProgress: Int,
            tierMaxProgress: Int,
            isUnlocked: Boolean = false,
            unlockedAt: Long? = null,
            lastUpdated: Long = System.currentTimeMillis(),
        ): AchievementProgress {
            return AchievementProgress(
                achievementId = achievementId,
                progress = progress,
                maxProgress = 100, // Не используется для tiers
                isUnlocked = isUnlocked,
                unlockedAt = unlockedAt,
                lastUpdated = lastUpdated,
                currentTier = currentTier,
                maxTier = maxTier,
                tierProgress = tierProgress,
                tierMaxProgress = tierMaxProgress,
            )
        }
    }
}
