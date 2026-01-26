package tachiyomi.domain.achievement.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementProgress

interface AchievementRepository {
    fun getAll(): Flow<List<Achievement>>
    fun getByCategory(category: AchievementCategory): Flow<List<Achievement>>
    fun getProgress(achievementId: String): Flow<AchievementProgress?>
    fun getAllProgress(): Flow<List<AchievementProgress>>

    suspend fun insertAchievement(achievement: Achievement)
    suspend fun updateProgress(progress: AchievementProgress)
    suspend fun insertOrUpdateProgress(progress: AchievementProgress)
    suspend fun deleteAchievement(id: String)
    suspend fun deleteAllAchievements()
}
