package tachiyomi.data.achievement.database

import app.cash.sqldelight.db.SqlDriver
import tachiyomi.data.achievement.AchievementsDatabase as SqlDelightAchievementsDatabase

class AchievementsDatabase(
    private val driver: SqlDriver,
) {

    val achievementsQueries
        get() = database.achievementsQueries

    val achievementProgressQueries
        get() = database.achievement_progressQueries

    val activityLogQueries
        get() = database.activity_logQueries

    val userProfileQueries
        get() = database.user_profileQueries

    companion object {
        const val NAME = "achievements.db"
        const val VERSION = 5L
    }

    private val database = SqlDelightAchievementsDatabase(driver)
}
