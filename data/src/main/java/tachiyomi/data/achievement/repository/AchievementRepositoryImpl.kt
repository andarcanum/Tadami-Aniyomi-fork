package tachiyomi.data.achievement.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.achievement.Achievements
import tachiyomi.data.achievement.Achievement_progress
import tachiyomi.data.achievement.database.AchievementsDatabase
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.repository.AchievementRepository

class AchievementRepositoryImpl(
    private val database: AchievementsDatabase,
) : AchievementRepository {

    override fun getAll(): Flow<List<Achievement>> {
        return database.achievementsQueries
            .selectAll()
            .asFlow()
            .mapToList()
            .map { list -> list.map { it.toDomainModel() } }
    }

    override fun getByCategory(category: AchievementCategory): Flow<List<Achievement>> {
        return database.achievementsQueries
            .getByCategory(category.name)
            .asFlow()
            .mapToList()
            .map { list -> list.map { it.toDomainModel() } }
    }

    override fun getProgress(achievementId: String): Flow<AchievementProgress?> {
        return database.achievementProgressQueries
            .getById(achievementId)
            .asFlow()
            .mapToOneOrNull()
            .map { it?.toDomainModel() }
    }

    override fun getAllProgress(): Flow<List<AchievementProgress>> {
        return database.achievementProgressQueries
            .selectAll()
            .asFlow()
            .mapToList()
            .map { list -> list.map { it.toDomainModel() } }
    }

    override suspend fun insertAchievement(achievement: Achievement) {
        database.achievementsQueries.insert(
            id = achievement.id,
            type = achievement.type.name,
            category = achievement.category.name,
            threshold = achievement.threshold?.toLong(),
            points = achievement.points.toLong(),
            title = achievement.title,
            description = achievement.description,
            badge_icon = achievement.badgeIcon,
            is_hidden = achievement.isHidden.toLong(),
            is_secret = achievement.isSecret.toLong(),
            unlockable_id = achievement.unlockableId,
            version = achievement.version.toLong(),
            created_at = achievement.createdAt,
        )
    }

    override suspend fun updateProgress(progress: AchievementProgress) {
        database.achievementProgressQueries.update(
            progress = progress.progress.toLong(),
            max_progress = progress.maxProgress.toLong(),
            is_unlocked = progress.isUnlocked.toLong(),
            unlocked_at = progress.unlockedAt,
            last_updated = progress.lastUpdated,
            achievement_id = progress.achievementId,
        )
    }

    override suspend fun insertOrUpdateProgress(progress: AchievementProgress) {
        database.achievementProgressQueries.upsert(
            achievement_id = progress.achievementId,
            progress = progress.progress.toLong(),
            max_progress = progress.maxProgress.toLong(),
            is_unlocked = progress.isUnlocked.toLong(),
            unlocked_at = progress.unlockedAt,
            last_updated = progress.lastUpdated,
        )
    }

    override suspend fun deleteAchievement(id: String) {
        database.achievementsQueries.deleteById(id)
    }

    override suspend fun deleteAllAchievements() {
        database.achievementsQueries.deleteAll()
    }

    private fun Achievements.toDomainModel(): Achievement {
        return Achievement(
            id = id,
            type = AchievementType.valueOf(type),
            category = AchievementCategory.valueOf(category),
            threshold = threshold?.toInt(),
            points = points.toInt(),
            title = title,
            description = description,
            badgeIcon = badge_icon,
            isHidden = is_hidden == 1L,
            isSecret = is_secret == 1L,
            unlockableId = unlockable_id,
            version = version.toInt(),
            createdAt = created_at,
        )
    }

    private fun Achievement_progress.toDomainModel(): AchievementProgress {
        return AchievementProgress(
            achievementId = achievement_id,
            progress = progress.toInt(),
            maxProgress = max_progress.toInt(),
            isUnlocked = is_unlocked == 1L,
            unlockedAt = unlocked_at,
            lastUpdated = last_updated,
        )
    }
}
