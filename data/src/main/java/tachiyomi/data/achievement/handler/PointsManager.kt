package tachiyomi.data.achievement.handler

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import tachiyomi.data.achievement.AchievementXp
import tachiyomi.data.achievement.User_profile
import tachiyomi.data.achievement.database.AchievementsDatabase
import tachiyomi.domain.achievement.model.UserPoints

class PointsManager(private val database: AchievementsDatabase) {
    init {
        initializeStats()
    }

    private fun initializeStats() {
        // INSERT OR IGNORE cannot overwrite a profile restored or awarded by another initializer.
        database.transaction { AchievementXp.ensureProfile(database, System.currentTimeMillis()) }
    }

    suspend fun addPoints(points: Int) {
        if (points <= 0) return
        database.withProfileMutation {
            database.transaction { AchievementXp.add(database, points.toLong(), System.currentTimeMillis()) }
        }
    }

    /** Reverses a known contribution; negative inputs never create XP. */
    suspend fun subtractPoints(points: Int) {
        if (points <= 0) return
        database.withProfileMutation {
            database.transaction { AchievementXp.add(database, -points.toLong(), System.currentTimeMillis()) }
        }
    }

    suspend fun incrementUnlocked() {
        database.withProfileMutation {
            database.userProfileQueries.incrementAchievementUnlocked(
                user_id = "default",
                last_updated = System.currentTimeMillis(),
            )
        }
    }

    suspend fun decrementAchievementUnlocked() {
        database.withProfileMutation {
            database.userProfileQueries.decrementAchievementUnlocked(
                user_id = "default",
                last_updated = System.currentTimeMillis(),
            )
        }
    }

    fun subscribeToPoints(): Flow<UserPoints> = database.userProfileQueries.getDefaultProfile()
        .asFlow().mapToOneOrNull(Dispatchers.IO).map { profile: User_profile? ->
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

    suspend fun getCurrentPoints(): UserPoints = withContext(Dispatchers.IO) {
        try {
            val profile = database.userProfileQueries.getDefaultProfile().executeAsOneOrNull()
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

    fun calculateLevel(points: Int): Int = AchievementXp.fields(points.coerceAtLeast(0).toLong()).level
}
