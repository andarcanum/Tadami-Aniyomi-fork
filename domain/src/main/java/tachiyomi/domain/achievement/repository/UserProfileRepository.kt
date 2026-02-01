package tachiyomi.domain.achievement.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.achievement.model.UserProfile

interface UserProfileRepository {
    fun getProfile(userId: String = "default"): Flow<UserProfile?>
    suspend fun getProfileSync(userId: String = "default"): UserProfile?
    suspend fun saveProfile(profile: UserProfile)
    suspend fun updateXP(userId: String, totalXP: Int, currentXP: Int, level: Int, xpToNextLevel: Int)
    suspend fun addTitle(userId: String, title: String)
    suspend fun addBadge(userId: String, badge: String)
    suspend fun addTheme(userId: String, themeId: String)
    suspend fun updateAchievementCounts(userId: String, unlocked: Int, total: Int)
    suspend fun deleteProfile(userId: String)
}
