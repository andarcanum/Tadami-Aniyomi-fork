package tachiyomi.data.achievement.handler

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import tachiyomi.data.achievement.User_profile
import tachiyomi.data.achievement.database.AchievementsDatabase
import tachiyomi.domain.achievement.model.UserPoints
import kotlin.math.sqrt

class PointsManager(
    private val database: AchievementsDatabase,
) {

    init {
        // Initialize stats on creation
        initializeStats()
    }

    private fun initializeStats() {
        // Create default profile if doesn't exist
        database.userProfileQueries.upsertProfile(
            user_id = "default",
            username = null,
            level = 1,
            current_xp = 0,
            xp_to_next_level = 100,
            total_xp = 0,
            titles = "[]",
            badges = "[]",
            unlocked_themes = "[]",
            achievements_unlocked = 0,
            total_achievements = 0,
            join_date = System.currentTimeMillis(),
            last_updated = System.currentTimeMillis(),
        )
    }

    suspend fun addPoints(points: Int) {
        if (points > 0) {
            withContext(Dispatchers.IO) {
                val current = getCurrentPoints()
                val newTotal = current.totalPoints + points
                val newLevel = calculateLevel(newTotal)

                database.userProfileQueries.updateXP(
                    user_id = "default",
                    total_xp = newTotal.toLong(),
                    current_xp = (newTotal % 100).toLong(), // Simple XP calculation
                    level = newLevel.toLong(),
                    xp_to_next_level = 100,
                    last_updated = System.currentTimeMillis(),
                )
            }
        }
    }

    suspend fun incrementUnlocked() {
        withContext(Dispatchers.IO) {
            val current = getCurrentPoints()
            database.userProfileQueries.updateAchievementCounts(
                user_id = "default",
                unlocked = (current.achievementsUnlocked + 1).toLong(),
                total = current.achievementsUnlocked.toLong() + 1,
                last_updated = System.currentTimeMillis(),
            )
        }
    }

    fun subscribeToPoints(): Flow<UserPoints> {
        return database.userProfileQueries.getDefaultProfile()
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { profile: User_profile? ->
                if (profile != null) {
                    UserPoints(
                        totalPoints = profile.total_xp.toInt(),
                        level = profile.level.toInt(),
                        achievementsUnlocked = profile.achievements_unlocked.toInt(),
                    )
                } else {
                    UserPoints()
                }
            }
    }

    suspend fun getCurrentPoints(): UserPoints {
        return withContext(Dispatchers.IO) {
            try {
                val profile: User_profile? = database.userProfileQueries.getDefaultProfile()
                    .executeAsOneOrNull()

                if (profile != null) {
                    UserPoints(
                        totalPoints = profile.total_xp.toInt(),
                        level = profile.level.toInt(),
                        achievementsUnlocked = profile.achievements_unlocked.toInt(),
                    )
                } else {
                    initializeStats()
                    UserPoints()
                }
            } catch (e: Exception) {
                initializeStats()
                UserPoints()
            }
        }
    }

    fun calculateLevel(points: Int): Int {
        // Formula: level = sqrt(points / 100) + 1
        return (sqrt(points.toDouble() / 100.0)).toInt() + 1
    }
}
