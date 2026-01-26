package tachiyomi.data.achievement.handler

import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapOnNotNull
import kotlinx.coroutines.withContext
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
        database.userStatsQueries.initUserStats()
    }

    suspend fun addPoints(points: Int) {
        if (points > 0) {
            withContext(Dispatchers.IO) {
                database.userStatsQueries.addPoints(points.toLong())
                recalculateLevel()
            }
        }
    }

    suspend fun incrementUnlocked() {
        withContext(Dispatchers.IO) {
            database.userStatsQueries.incrementUnlocked()
        }
    }

    fun subscribeToPoints(): Flow<UserPoints> {
        return database.userStatsQueries.getUserStats()
            .asFlow()
            .mapOnNotNull { query ->
                query.executeAsOneOrNull()?.let {
                    UserPoints(
                        totalPoints = it.total_points.toInt(),
                        level = it.level.toInt(),
                        achievementsUnlocked = it.achievements_unlocked.toInt(),
                    )
                } ?: UserPoints()
            }
    }

    suspend fun getCurrentPoints(): UserPoints {
        return withContext(Dispatchers.IO) {
            try {
                database.userStatsQueries.getUserStats()
                    .executeAsOne()
                    .let {
                        UserPoints(
                            totalPoints = it.total_points.toInt(),
                            level = it.level.toInt(),
                            achievementsUnlocked = it.achievements_unlocked.toInt(),
                        )
                    }
            } catch (e: Exception) {
                initializeStats()
                UserPoints()
            }
        }
    }

    private suspend fun recalculateLevel() {
        val current = getCurrentPoints()
        val newLevel = calculateLevel(current.totalPoints)
        database.userStatsQueries.setLevel(newLevel.toLong())
    }

    fun calculateLevel(points: Int): Int {
        // Formula: level = sqrt(points / 100) + 1
        return (sqrt(points.toDouble() / 100.0)).toInt() + 1
    }
}
