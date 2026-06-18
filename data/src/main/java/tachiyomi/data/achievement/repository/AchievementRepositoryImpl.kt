package tachiyomi.data.achievement.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import logcat.LogPriority
import logcat.logcat
import tachiyomi.data.achievement.Achievement_progress
import tachiyomi.data.achievement.Achievements
import tachiyomi.data.achievement.database.AchievementsDatabase
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.AchievementRarity
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.model.Reward
import tachiyomi.domain.achievement.repository.AchievementRepository

class AchievementRepositoryImpl(
    private val database: AchievementsDatabase,
    private val json: Json = DefaultJson,
) : AchievementRepository {

    private val rewardListSerializer = ListSerializer(Reward.serializer())

    override fun getAll(): Flow<List<Achievement>> {
        return database.achievementsQueries
            .selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list ->
                logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Repository.getAll() returning ${list.size} items from DB" }
                list.map { it.toDomainModel() }
            }
    }

    override fun getByCategory(category: AchievementCategory): Flow<List<Achievement>> {
        return database.achievementsQueries
            .getByCategory(category.name)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map { it.toDomainModel() } }
    }

    override fun getProgress(achievementId: String): Flow<AchievementProgress?> {
        return database.achievementProgressQueries
            .getById(achievementId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.toDomainModel() }
    }

    override fun getAllProgress(): Flow<List<AchievementProgress>> {
        return database.achievementProgressQueries
            .selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map { it.toDomainModel() } }
    }

    override suspend fun insertAchievement(achievement: Achievement) {
        val rewardsJson = achievement.rewards?.let { json.encodeToString(rewardListSerializer, it) }
        val tagsJson = encodeTags(achievement.tags)
        database.achievementsQueries.insert(
            id = achievement.id,
            type = achievement.type.name,
            category = achievement.category.name,
            threshold = achievement.threshold?.toLong(),
            points = achievement.points.toLong(),
            title = achievement.title,
            description = achievement.description,
            badge_icon = achievement.badgeIcon,
            is_hidden = if (achievement.isHidden) 1L else 0L,
            is_secret = if (achievement.isSecret) 1L else 0L,
            unlockable_id = achievement.unlockableId,
            version = achievement.version.toLong(),
            created_at = achievement.createdAt,
            rewards = rewardsJson,
            rarity = achievement.rarity.name.lowercase(),
            tags = tagsJson,
            hint = achievement.hint,
            season = achievement.season,
            tier_group = achievement.tierGroup,
            tier_level = achievement.tierLevel?.toLong(),
            reward_set = achievement.rewardSet,
        )
        database.achievementsQueries.updateMutableFields(
            id = achievement.id,
            type = achievement.type.name,
            category = achievement.category.name,
            threshold = achievement.threshold?.toLong(),
            points = achievement.points.toLong(),
            title = achievement.title,
            description = achievement.description,
            badge_icon = achievement.badgeIcon,
            is_hidden = if (achievement.isHidden) 1L else 0L,
            is_secret = if (achievement.isSecret) 1L else 0L,
            unlockable_id = achievement.unlockableId,
            rewards = rewardsJson,
            rarity = achievement.rarity.name.lowercase(),
            tags = tagsJson,
            hint = achievement.hint,
            season = achievement.season,
            tier_group = achievement.tierGroup,
            tier_level = achievement.tierLevel?.toLong(),
            reward_set = achievement.rewardSet,
        )
    }

    override suspend fun updateProgress(progress: AchievementProgress) {
        database.achievementProgressQueries.updateWithTiers(
            achievement_id = progress.achievementId,
            progress = progress.progress.toLong(),
            max_progress = progress.maxProgress.toLong(),
            is_unlocked = if (progress.isUnlocked) 1L else 0L,
            unlocked_at = progress.unlockedAt,
            last_updated = progress.lastUpdated,
            current_tier = progress.currentTier.toLong(),
            max_tier = progress.maxTier.toLong(),
            tier_progress = progress.tierProgress.toLong(),
            tier_max_progress = progress.tierMaxProgress.toLong(),
        )
    }

    override suspend fun insertOrUpdateProgress(progress: AchievementProgress) {
        database.achievementProgressQueries.upsertWithTiers(
            achievement_id = progress.achievementId,
            progress = progress.progress.toLong(),
            max_progress = progress.maxProgress.toLong(),
            is_unlocked = if (progress.isUnlocked) 1L else 0L,
            unlocked_at = progress.unlockedAt,
            last_updated = progress.lastUpdated,
            current_tier = progress.currentTier.toLong(),
            max_tier = progress.maxTier.toLong(),
            tier_progress = progress.tierProgress.toLong(),
            tier_max_progress = progress.tierMaxProgress.toLong(),
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
            rewards = rewards?.let { decodeRewards(it) },
            rarity = AchievementRarity.fromString(rarity),
            tags = tags?.let { decodeTags(it) } ?: emptyList(),
            hint = hint,
            season = season,
            tierGroup = tier_group,
            tierLevel = tier_level?.toInt(),
            rewardSet = reward_set,
        )
    }

    private fun decodeRewards(raw: String): List<Reward>? {
        if (raw.isBlank()) return null
        return runCatching { json.decodeFromString(rewardListSerializer, raw) }
            .onFailure { logcat(LogPriority.WARN) { "[ACHIEVEMENTS] Failed to decode rewards: ${it.message}" } }
            .getOrNull()
    }

    private fun encodeTags(tags: List<String>): String? {
        if (tags.isEmpty()) return null
        return json.encodeToString(tags)
    }

    private fun decodeTags(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun Achievement_progress.toDomainModel(): AchievementProgress {
        return AchievementProgress(
            achievementId = achievement_id,
            progress = progress.toInt(),
            maxProgress = max_progress.toInt(),
            isUnlocked = is_unlocked == 1L,
            unlockedAt = unlocked_at,
            lastUpdated = last_updated,
            // Tier support
            currentTier = current_tier.toInt(),
            maxTier = max_tier.toInt(),
            tierProgress = tier_progress.toInt(),
            tierMaxProgress = tier_max_progress.toInt(),
        )
    }

    private companion object {
        val DefaultJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
