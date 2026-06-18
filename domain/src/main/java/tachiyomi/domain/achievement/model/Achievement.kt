package tachiyomi.domain.achievement.model

import androidx.compose.runtime.Immutable

@Immutable
data class Achievement(
    val id: String,
    val type: AchievementType,
    val category: AchievementCategory,
    val threshold: Int? = null,
    val points: Int = 0,
    val title: String,
    val description: String? = null,
    val badgeIcon: String? = null,
    val isHidden: Boolean = false,
    val isSecret: Boolean = false,
    val unlockableId: String? = null,
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    // Многоуровневая система (Tiered System)
    val tiers: List<AchievementTier>? = null,
    val maxTier: Int = tiers?.size ?: 0,
    // Система наград (Rewards)
    val rewards: List<Reward>? = null,
    // ---- Refactor additions (all optional / backwards-compatible) ----
    val rarity: AchievementRarity = AchievementRarity.COMMON,
    val tags: List<String> = emptyList(),
    val hint: String? = null,
    val hintVague: String? = null,
    val hintDirect: String? = null,
    val hintObvious: String? = null,
    val season: String? = null,
    val tierGroup: String? = null,
    val tierLevel: Int? = null,
    val rewardSet: String? = null,
) {
    /**
     * Является ли это достижением с уровнями
     */
    val isTiered: Boolean
        get() = tiers != null && tiers.isNotEmpty()

    /**
     * Имеет ли это достижение награды
     */
    val hasRewards: Boolean
        get() = rewards != null && rewards.isNotEmpty()

    /**
     * Получить уровень по прогрессу
     * @return Tier (null если не tiers или прогресс меньше первого уровня)
     */
    fun getTierForProgress(progress: Int): AchievementTier? {
        if (!isTiered) return null
        return tiers?.lastOrNull { progress >= it.threshold }
    }

    /**
     * Получить следующий уровень для достижения
     *
     * Returns the next tier whose threshold exceeds [currentProgress].
     * Returns `null` when:
     * - the achievement is not tiered, or
     * - [currentProgress] already meets or exceeds all tier thresholds (i.e. max tier reached).
     *
     * **Contract change (v8):** previously threw [NoSuchElementException] via `first {}`
     * when no higher tier existed. Now uses `firstOrNull {}` and returns `null` instead.
     *
     * @return Следующий Tier или null если уже максимальный уровень
     */
    fun getNextTier(currentProgress: Int): AchievementTier? {
        if (!isTiered) return null
        return tiers?.firstOrNull { currentProgress < it.threshold }
    }

    /**
     * Получить максимальный уровень
     */
    fun getMaxTier(): AchievementTier? {
        if (!isTiered) return null
        return tiers?.lastOrNull()
    }

    /**
     * Получить все награды для достижения
     */
    fun getAllRewards(): List<Reward> {
        if (!hasRewards) return emptyList()
        return rewards ?: emptyList()
    }

    /**
     * Получить награды по типу
     */
    fun getRewardsByType(type: RewardType): List<Reward> {
        return getAllRewards().filter { it.type == type }
    }

    /**
     * Получить общий XP из всех наград
     */
    fun getTotalRewardXP(): Int {
        return getAllRewards()
            .filter { it.type == RewardType.EXPERIENCE }
            .sumOf { it.value }
    }

    companion object {
        /**
         * Создать многоуровневое достижение
         */
        fun createTiered(
            id: String,
            type: AchievementType,
            category: AchievementCategory,
            tiers: List<AchievementTier>,
            title: String,
            description: String? = null,
            badgeIcon: String? = null,
            isHidden: Boolean = false,
            isSecret: Boolean = false,
            unlockableId: String? = null,
            rewards: List<Reward>? = null,
            version: Int = 1,
            createdAt: Long = System.currentTimeMillis(),
        ): Achievement {
            return Achievement(
                id = id,
                type = type,
                category = category,
                threshold = tiers.last().threshold, // Основной threshold - последний уровень
                points = tiers.sumOf { it.points }, // Суммарные очки
                title = title,
                description = description,
                badgeIcon = badgeIcon,
                isHidden = isHidden,
                isSecret = isSecret,
                unlockableId = unlockableId,
                version = version,
                createdAt = createdAt,
                tiers = tiers,
                rewards = rewards,
            )
        }

        /**
         * Создать достижение с наградами
         */
        fun createWithRewards(
            id: String,
            type: AchievementType,
            category: AchievementCategory,
            threshold: Int? = null,
            points: Int = 0,
            title: String,
            description: String? = null,
            badgeIcon: String? = null,
            isHidden: Boolean = false,
            isSecret: Boolean = false,
            rewards: List<Reward>,
            unlockableId: String? = null,
            version: Int = 1,
            createdAt: Long = System.currentTimeMillis(),
        ): Achievement {
            return Achievement(
                id = id,
                type = type,
                category = category,
                threshold = threshold,
                points = points,
                title = title,
                description = description,
                badgeIcon = badgeIcon,
                isHidden = isHidden,
                isSecret = isSecret,
                unlockableId = unlockableId,
                version = version,
                createdAt = createdAt,
                rewards = rewards,
            )
        }
    }
}
