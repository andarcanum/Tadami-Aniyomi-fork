package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.achievement.model.UserProfile

@Serializable
data class BackupUserProfile(
    @ProtoNumber(1) var userId: String = "default",
    @ProtoNumber(2) var username: String? = null,
    @ProtoNumber(3) var level: Int = 1,
    @ProtoNumber(4) var currentXP: Int = 0,
    @ProtoNumber(5) var xpToNextLevel: Int = 100,
    @ProtoNumber(6) var totalXP: Int = 0,
    @ProtoNumber(7) var titles: List<String> = emptyList(),
    @ProtoNumber(8) var badges: List<String> = emptyList(),
    @ProtoNumber(9) var unlockedThemes: List<String> = emptyList(),
    @ProtoNumber(10) var achievementsUnlocked: Int = 0,
    @ProtoNumber(11) var totalAchievements: Int = 0,
    @ProtoNumber(12) var joinDate: Long = System.currentTimeMillis(),
) {
    fun toUserProfile(): UserProfile {
        return UserProfile(
            userId = userId,
            username = username,
            level = level,
            currentXP = currentXP,
            xpToNextLevel = xpToNextLevel,
            totalXP = totalXP,
            titles = titles,
            badges = badges,
            unlockedThemes = unlockedThemes,
            achievementsUnlocked = achievementsUnlocked,
            totalAchievements = totalAchievements,
            joinDate = joinDate,
        )
    }

    companion object {
        fun fromUserProfile(profile: UserProfile): BackupUserProfile {
            return BackupUserProfile(
                userId = profile.userId,
                username = profile.username,
                level = profile.level,
                currentXP = profile.currentXP,
                xpToNextLevel = profile.xpToNextLevel,
                totalXP = profile.totalXP,
                titles = profile.titles,
                badges = profile.badges,
                unlockedThemes = profile.unlockedThemes,
                achievementsUnlocked = profile.achievementsUnlocked,
                totalAchievements = profile.totalAchievements,
                joinDate = profile.joinDate,
            )
        }
    }
}
