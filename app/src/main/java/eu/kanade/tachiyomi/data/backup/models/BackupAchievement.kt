package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.AchievementTier
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.model.Reward

@Serializable
data class BackupAchievement(
    // Achievement definition fields
    @ProtoNumber(1) var id: String = "",
    @ProtoNumber(2) var type: Int = 0, // AchievementType ordinal
    @ProtoNumber(3) var category: Int = 0, // AchievementCategory ordinal
    @ProtoNumber(4) var threshold: Int? = null,
    @ProtoNumber(5) var points: Int = 0,
    @ProtoNumber(6) var title: String = "",
    @ProtoNumber(7) var description: String? = null,
    @ProtoNumber(8) var badgeIcon: String? = null,
    @ProtoNumber(9) var isHidden: Boolean = false,
    @ProtoNumber(10) var isSecret: Boolean = false,
    @ProtoNumber(11) var unlockableId: String? = null,
    @ProtoNumber(12) var version: Int = 1,
    @ProtoNumber(13) var createdAt: Long = 0,
    // Progress fields
    @ProtoNumber(14) var progress: Int = 0,
    @ProtoNumber(15) var maxProgress: Int = 100,
    @ProtoNumber(16) var isUnlocked: Boolean = false,
    @ProtoNumber(17) var unlockedAt: Long? = null,
    @ProtoNumber(18) var lastUpdated: Long = 0,
    // Tiered achievement fields
    @ProtoNumber(19) var currentTier: Int = 0,
    @ProtoNumber(20) var maxTier: Int = 0,
    @ProtoNumber(21) var tierProgress: Int = 0,
    @ProtoNumber(22) var tierMaxProgress: Int = 100,
    // Nested lists (using 100+ for convention)
    @ProtoNumber(100) var tiers: List<BackupAchievementTier> = emptyList(),
    @ProtoNumber(101) var rewards: List<BackupReward> = emptyList(),
) {
    fun toAchievement(): Achievement {
        return Achievement(
            id = id,
            type = AchievementType.entries.getOrElse(type) { AchievementType.EVENT },
            category = AchievementCategory.entries.getOrElse(category) { AchievementCategory.BOTH },
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
            tiers = tiers.map { it.toAchievementTier() },
            maxTier = maxTier,
            rewards = rewards.map { it.toReward() },
        )
    }

    fun toAchievementProgress(): AchievementProgress {
        return AchievementProgress(
            achievementId = id,
            progress = progress,
            maxProgress = maxProgress,
            isUnlocked = isUnlocked,
            unlockedAt = unlockedAt,
            lastUpdated = lastUpdated,
            currentTier = currentTier,
            maxTier = maxTier,
            tierProgress = tierProgress,
            tierMaxProgress = tierMaxProgress,
        )
    }

    companion object {
        fun fromAchievement(
            achievement: Achievement,
            progress: AchievementProgress? = null,
        ): BackupAchievement {
            return BackupAchievement(
                id = achievement.id,
                type = achievement.type.ordinal,
                category = achievement.category.ordinal,
                threshold = achievement.threshold,
                points = achievement.points,
                title = achievement.title,
                description = achievement.description,
                badgeIcon = achievement.badgeIcon,
                isHidden = achievement.isHidden,
                isSecret = achievement.isSecret,
                unlockableId = achievement.unlockableId,
                version = achievement.version,
                createdAt = achievement.createdAt,
                progress = progress?.progress ?: 0,
                maxProgress = progress?.maxProgress ?: 100,
                isUnlocked = progress?.isUnlocked ?: false,
                unlockedAt = progress?.unlockedAt,
                lastUpdated = progress?.lastUpdated ?: System.currentTimeMillis(),
                currentTier = progress?.currentTier ?: 0,
                maxTier = progress?.maxTier ?: achievement.maxTier,
                tierProgress = progress?.tierProgress ?: 0,
                tierMaxProgress = progress?.tierMaxProgress ?: 100,
                tiers = achievement.tiers?.map { BackupAchievementTier.fromAchievementTier(it) } ?: emptyList(),
                rewards = achievement.rewards?.map { BackupReward.fromReward(it) } ?: emptyList(),
            )
        }
    }
}

@Serializable
data class BackupAchievementTier(
    @ProtoNumber(1) var level: Int = 0,
    @ProtoNumber(2) var threshold: Int = 0,
    @ProtoNumber(3) var points: Int = 0,
    @ProtoNumber(4) var title: String = "",
    @ProtoNumber(5) var description: String? = null,
    @ProtoNumber(6) var badgeIcon: String? = null,
) {
    fun toAchievementTier(): AchievementTier {
        return AchievementTier(
            level = level,
            threshold = threshold,
            points = points,
            title = title,
            description = description,
            badgeIcon = badgeIcon,
        )
    }

    companion object {
        fun fromAchievementTier(tier: AchievementTier): BackupAchievementTier {
            return BackupAchievementTier(
                level = tier.level,
                threshold = tier.threshold,
                points = tier.points,
                title = tier.title,
                description = tier.description,
                badgeIcon = tier.badgeIcon,
            )
        }
    }
}

@Serializable
data class BackupReward(
    @ProtoNumber(1) var type: Int = 0, // RewardType ordinal
    @ProtoNumber(2) var id: String = "",
    @ProtoNumber(3) var value: Int = 0,
    @ProtoNumber(4) var title: String = "",
    @ProtoNumber(5) var description: String? = null,
    @ProtoNumber(6) var icon: String? = null,
    @ProtoNumber(7) var unlocked: Boolean = false,
) {
    fun toReward(): Reward {
        return Reward(
            type = tachiyomi.domain.achievement.model.RewardType.entries.getOrElse(type) {
                tachiyomi.domain.achievement.model.RewardType.EXPERIENCE
            },
            id = id,
            value = value,
            title = title,
            description = description,
            icon = icon,
            unlocked = unlocked,
        )
    }

    companion object {
        fun fromReward(reward: Reward): BackupReward {
            return BackupReward(
                type = reward.type.ordinal,
                id = reward.id,
                value = reward.value,
                title = reward.title,
                description = reward.description,
                icon = reward.icon,
                unlocked = reward.unlocked,
            )
        }
    }
}
