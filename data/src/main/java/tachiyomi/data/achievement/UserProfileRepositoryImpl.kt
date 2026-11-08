package tachiyomi.data.achievement

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.data.achievement.database.AchievementsDatabase
import tachiyomi.domain.achievement.model.UserProfile
import tachiyomi.domain.achievement.repository.UserProfileRepository

class UserProfileRepositoryImpl(
    private val database: AchievementsDatabase,
) : UserProfileRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun getProfile(userId: String): Flow<UserProfile?> {
        return database.userProfileQueries
            .getUserProfile(userId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.toDomainProfile() }
    }

    override suspend fun getProfileSync(userId: String): UserProfile? {
        return database.userProfileQueries
            .getUserProfile(userId)
            .executeAsOneOrNull()
            ?.toDomainProfile()
    }

    override suspend fun saveProfile(profile: UserProfile) {
        database.withProfileMutation {
            database.transaction { UserProfileStorage.write(database, profile, System.currentTimeMillis()) }
        }
    }

    override suspend fun addXP(userId: String, amount: Int): Boolean {
        if (amount <= 0) return false
        return database.withProfileMutation {
            database.transaction { AchievementXp.add(database, amount.toLong(), System.currentTimeMillis(), userId) }
        }
    }

    override suspend fun updateXP(userId: String, totalXP: Int, currentXP: Int, level: Int, xpToNextLevel: Int) {
        database.userProfileQueries.updateXP(
            user_id = userId,
            total_xp = totalXP.toLong(),
            current_xp = currentXP.toLong(),
            level = level.toLong(),
            xp_to_next_level = xpToNextLevel.toLong(),
            last_updated = System.currentTimeMillis(),
        )
    }

    override suspend fun addTitle(userId: String, title: String) {
        val profile = getProfileSync(userId) ?: return
        if (profile.titles.contains(title)) return

        val newTitles = profile.titles + title
        database.userProfileQueries.addTitle(
            user_id = userId,
            titles = json.encodeToString(newTitles),
            last_updated = System.currentTimeMillis(),
        )
    }

    override suspend fun addBadge(userId: String, badge: String) {
        val profile = getProfileSync(userId) ?: return
        if (profile.badges.contains(badge)) return

        val newBadges = profile.badges + badge
        database.userProfileQueries.addBadge(
            user_id = userId,
            badges = json.encodeToString(newBadges),
            last_updated = System.currentTimeMillis(),
        )
    }

    override suspend fun removeBadge(userId: String, badge: String) {
        val profile = getProfileSync(userId) ?: return
        if (!profile.badges.contains(badge)) return

        val newBadges = profile.badges - badge
        database.userProfileQueries.addBadge(
            user_id = userId,
            badges = json.encodeToString(newBadges),
            last_updated = System.currentTimeMillis(),
        )
    }

    override suspend fun addTheme(userId: String, themeId: String) {
        val profile = getProfileSync(userId) ?: return
        if (profile.unlockedThemes.contains(themeId)) return

        val newThemes = profile.unlockedThemes + themeId
        database.userProfileQueries.addTheme(
            user_id = userId,
            unlocked_themes = json.encodeToString(newThemes),
            last_updated = System.currentTimeMillis(),
        )
    }

    override suspend fun removeTheme(userId: String, themeId: String) {
        val profile = getProfileSync(userId) ?: return
        if (!profile.unlockedThemes.contains(themeId)) return

        val newThemes = profile.unlockedThemes - themeId
        database.userProfileQueries.addTheme(
            user_id = userId,
            unlocked_themes = json.encodeToString(newThemes),
            last_updated = System.currentTimeMillis(),
        )
    }

    override suspend fun updateAchievementCounts(userId: String, unlocked: Int, total: Int) {
        database.userProfileQueries.updateAchievementCounts(
            user_id = userId,
            unlocked = unlocked.toLong(),
            total = total.toLong(),
            last_updated = System.currentTimeMillis(),
        )
    }

    override suspend fun deleteProfile(userId: String) {
        database.userProfileQueries.deleteProfile(userId)
    }
}
