package tachiyomi.data.achievement.database

import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import tachiyomi.data.achievement.AchievementsDatabase as SqlDelightAchievementsDatabase

class AchievementsDatabase(
    private val driver: AndroidSqliteDriver,
) {

    val achievementsQueries: AchievementsQueries
        get() = database.achievementsQueries

    val achievementProgressQueries: Achievement_progressQueries
        get() = database.achievementProgressQueries

    val achievementActivityLogQueries: Achievement_activity_logQueries
        get() = database.achievementActivityLogQueries

    val userStatsQueries: User_statsQueries
        get() = database.userStatsQueries

    companion object {
        const val NAME = "achievements.db"
        const val VERSION = 4L
    }

    private val database = SqlDelightAchievementsDatabase(
        driver = driver,
        achievementsAdapter = AchievementsAdapter,
        achievement_progressAdapter = Achievement_progressAdapter,
        achievement_activity_logAdapter = Achievement_activity_logAdapter,
        user_statsAdapter = User_statsAdapter,
    )
}
