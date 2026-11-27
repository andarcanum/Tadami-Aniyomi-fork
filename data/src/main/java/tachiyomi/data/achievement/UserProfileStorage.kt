package tachiyomi.data.achievement

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.data.achievement.database.AchievementsDatabase
import tachiyomi.domain.achievement.model.UserProfile

private val profileJson = Json { ignoreUnknownKeys = true }

internal fun User_profile.toDomainProfile(): UserProfile = UserProfile(
    userId = user_id,
    username = username,
    level = level.toInt(),
    currentXP = current_xp.toInt(),
    xpToNextLevel = xp_to_next_level.toInt(),
    totalXP = total_xp.toInt(),
    titles = runCatching { profileJson.decodeFromString<List<String>>(titles) }.getOrDefault(emptyList()),
    badges = runCatching { profileJson.decodeFromString<List<String>>(badges) }.getOrDefault(emptyList()),
    unlockedThemes = runCatching {
        profileJson.decodeFromString<List<String>>(unlocked_themes)
    }.getOrDefault(emptyList()),
    achievementsUnlocked = achievements_unlocked.toInt(),
    totalAchievements = total_achievements.toInt(),
    joinDate = join_date,
)

/** Raw write inside the shared profile mutation and synchronous transaction. */
internal object UserProfileStorage {
    fun write(database: AchievementsDatabase, profile: UserProfile, now: Long) {
        database.userProfileQueries.upsertProfile(
            user_id = profile.userId,
            username = profile.username,
            level = profile.level.toLong(),
            current_xp = profile.currentXP.toLong(),
            xp_to_next_level = profile.xpToNextLevel.toLong(),
            total_xp = profile.totalXP.toLong(),
            titles = profileJson.encodeToString(profile.titles),
            badges = profileJson.encodeToString(profile.badges),
            unlocked_themes = profileJson.encodeToString(profile.unlockedThemes),
            achievements_unlocked = profile.achievementsUnlocked.toLong(),
            total_achievements = profile.totalAchievements.toLong(),
            join_date = profile.joinDate,
            last_updated = now,
        )
    }
}
