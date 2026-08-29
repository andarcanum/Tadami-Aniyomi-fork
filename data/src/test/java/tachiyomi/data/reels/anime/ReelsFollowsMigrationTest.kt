package tachiyomi.data.reels.anime

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.mi.data.AnimeDatabase
import java.io.File

/**
 * Smoke-tests the 149.sqm contract-v18 migration: an upgrade device (db version 149, no
 * reels_follows table) must come up with the fresh creator-follows schema, PK and
 * source_id index — a plain CREATE, no rebuild of existing tables.
 */
class ReelsFollowsMigrationTest {

    @Test
    fun `149 creates reels_follows with pk dedupe and source index`() {
        val dbFile = File.createTempFile("reels-follows-mig", ".db").apply { delete() }
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")

        // Upgrade from 149 to current runs 149.sqm (CREATE TABLE + CREATE INDEX).
        AnimeDatabase.Schema.migrate(driver, oldVersion = 149L, newVersion = AnimeDatabase.Schema.version)

        columnNames(driver) shouldContainAll listOf("source_id", "creator", "added_at")
        indexNames(driver) shouldContainAll listOf("reels_follows_source_id_index")

        // Composite PK survives the migration: INSERT OR REPLACE on the same key keeps one row.
        driver.execute(
            identifier = null,
            sql = "INSERT OR REPLACE INTO reels_follows(source_id, creator, added_at) VALUES (101, 'alice', 1000)",
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = "INSERT OR REPLACE INTO reels_follows(source_id, creator, added_at) VALUES (101, 'alice', 2000)",
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = "INSERT OR REPLACE INTO reels_follows(source_id, creator, added_at) VALUES (202, 'alice', 3000)",
            parameters = 0,
        )
        rowCount(driver) shouldBe 2L

        driver.close()
        dbFile.delete()
    }

    private fun columnNames(driver: JdbcSqliteDriver): List<String> {
        return driver.executeQuery(
            identifier = null,
            sql = "PRAGMA table_info(reels_follows)",
            mapper = { cursor ->
                QueryResult.Value(
                    buildList {
                        while (cursor.next().value) {
                            add(cursor.getString(1).orEmpty())
                        }
                    },
                )
            },
            parameters = 0,
        ).value
    }

    private fun indexNames(driver: JdbcSqliteDriver): List<String> {
        return driver.executeQuery(
            identifier = null,
            sql = "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'reels_follows'",
            mapper = { cursor ->
                QueryResult.Value(
                    buildList {
                        while (cursor.next().value) {
                            add(cursor.getString(0).orEmpty())
                        }
                    },
                )
            },
            parameters = 0,
        ).value
    }

    private fun rowCount(driver: JdbcSqliteDriver): Long {
        return driver.executeQuery(
            identifier = null,
            sql = "SELECT COUNT(*) FROM reels_follows",
            mapper = { cursor ->
                QueryResult.Value(
                    if (cursor.next().value) cursor.getLong(0) ?: -1L else -1L,
                )
            },
            parameters = 0,
        ).value
    }
}
