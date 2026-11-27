package tachiyomi.data.achievement.database

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tachiyomi.db.achievement.AchievementsDatabase as SqlDelightAchievementsDatabase

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

    val crystalMemorialQueries
        get() = database.crystal_memorialQueries

    private val profileMutationMutex = Mutex()

    /** Serializes profile read/modify/write and receipt projection across all feature managers. */
    suspend fun <T> withProfileMutation(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        profileMutationMutex.withLock { block() }
    }

    /** Synchronous transaction: never suspend or switch threads from this block. */
    fun <T> transaction(block: () -> T): T = database.transactionWithResult { block() }

    companion object {
        const val NAME = "achievements.db"
        val VERSION: Long get() = SqlDelightAchievementsDatabase.Schema.version
    }

    private val database = SqlDelightAchievementsDatabase(driver)
}
