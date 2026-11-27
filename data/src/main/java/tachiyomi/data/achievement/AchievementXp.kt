package tachiyomi.data.achievement

import tachiyomi.data.achievement.database.AchievementsDatabase
import tachiyomi.domain.achievement.model.UserProfile

/** Synchronous operations for the shared profile transaction; callers own withProfileMutation. */
internal object AchievementXp {
    fun ensureProfile(database: AchievementsDatabase, now: Long, userId: String = "default") {
        database.userProfileQueries.insertProfileIfNotExists(
            user_id = userId, username = null, level = 1L, current_xp = 0L,
            xp_to_next_level = UserProfile.getXPForLevel(2).toLong(), total_xp = 0L,
            titles = "[]", badges = "[]", unlocked_themes = "[]",
            achievements_unlocked = 0L, total_achievements = 0L, join_date = now, last_updated = now,
        )
    }

    fun add(database: AchievementsDatabase, delta: Long, now: Long, userId: String = "default"): Boolean {
        ensureProfile(database, now, userId)
        val before = database.userProfileQueries.getUserProfile(userId).executeAsOne()
        val total = (before.total_xp + delta).coerceAtLeast(0L)
        val fields = fields(total)
        database.userProfileQueries.updateXP(
            user_id = userId,
            total_xp = total,
            current_xp = fields.current,
            level = fields.level.toLong(),
            xp_to_next_level = fields.next,
            last_updated = now,
        )
        return fields.level > before.level
    }

    fun setTotal(database: AchievementsDatabase, total: Long, now: Long, userId: String = "default") {
        ensureProfile(database, now, userId)
        val fields = fields(total)
        database.userProfileQueries.updateXP(
            user_id = userId,
            total_xp = total,
            current_xp = fields.current,
            level = fields.level.toLong(),
            xp_to_next_level = fields.next,
            last_updated = now,
        )
    }

    data class Fields(val level: Int, val current: Long, val next: Long)
    fun fields(total: Long): Fields {
        require(total in 0L..Int.MAX_VALUE.toLong()) { "XP is outside the supported profile range" }
        var level = 1
        var spent = 0L
        while (true) {
            val next = UserProfile.getXPForLevel(level + 1).toLong()
            if (spent + next > total) return Fields(level, total - spent, next)
            spent += next
            level++
        }
    }
}
