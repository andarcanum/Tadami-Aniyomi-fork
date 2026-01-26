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

    companion object {
        const val NAME = "achievements.db"
        const val VERSION = 1L
    }

    private val database = SqlDelightAchievementsDatabase(
        driver = driver,
        achievementsAdapter = AchievementsAdapter,
        achievement_progressAdapter = Achievement_progressAdapter,
    )
}
