package tachiyomi.data.achievement

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import tachiyomi.data.achievement.AchievementsDatabase as SqlDelightAchievementsDatabase
import tachiyomi.data.achievement.database.AchievementsDatabase

/**
 * Base test class for achievement system tests.
 * Provides in-memory database instance and common setup/teardown.
 */
abstract class AchievementTestBase {

    protected lateinit var database: AchievementsDatabase
    protected lateinit var driver: JdbcSqliteDriver
    protected val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    open fun setup() {
        // Create in-memory SQLite driver for testing
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

        // Create database schema
        SqlDelightAchievementsDatabase.Schema.create(driver)

        // Initialize AchievementsDatabase wrapper
        database = AchievementsDatabase(driver)
    }

    @AfterEach
    open fun tearDown() {
        // Close database connection
        driver.close()
    }

    /**
     * Helper function to run test with test dispatcher
     */
    fun runTestWithDispatcher(block: suspend () -> Unit) = runTest(testDispatcher) {
        block()
    }
}
