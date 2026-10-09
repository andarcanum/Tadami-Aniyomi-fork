package tachiyomi.data.discovery

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.data.Database

/**
 * Миграция 58.sqm (прямое открытие plugin-bound подборок): аддитивные nullable-колонки
 * source_id/source_url в discovery_suggestions. Проверяем оба пути: свежая установка
 * (CREATE TABLE, порядок колонок = порядок ALTER для SELECT *-маппера) и апгрейд с v58.
 */
class DiscoverySuggestionsMigrationTest {

    private fun tableInfo(driver: JdbcSqliteDriver): List<Pair<Int, String>> {
        return driver.executeQuery(
            identifier = null,
            sql = "PRAGMA table_info(discovery_suggestions)",
            mapper = { cursor: SqlCursor ->
                val rows = mutableListOf<Pair<Int, String>>()
                while (cursor.next().value) {
                    rows += cursor.getLong(0)!!.toInt() to cursor.getString(1)!!
                }
                QueryResult.Value(rows)
            },
            parameters = 0,
        ).value
    }

    @Test
    fun `fresh schema has source binding and genres columns at the end`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            Database.Schema.create(driver)
            val info = tableInfo(driver)
            info.map { it.second } shouldContain "source_id"
            info.map { it.second } shouldContain "source_url"
            info.map { it.second } shouldContain "genres"
            // Порядок обязан совпадать с ALTER TABLE из 58.sqm и 61.sqm: колонки в конце, после created_at.
            val names = info.sortedBy { it.first }.map { it.second }
            names.takeLast(3) shouldBe listOf("source_id", "source_url", "genres")
        } finally {
            driver.close()
        }
    }

    @Test
    fun `upgrade from 58 adds source binding columns`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            // Схема discovery_suggestions ДО миграции 58 (12 колонок, без привязки).
            driver.execute(
                null,
                """
                CREATE TABLE discovery_suggestions (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    media_type TEXT NOT NULL,
                    row_type TEXT NOT NULL,
                    title TEXT NOT NULL,
                    clean_title TEXT NOT NULL,
                    cover_url TEXT,
                    reason TEXT,
                    seed_title TEXT,
                    provider TEXT NOT NULL,
                    score REAL NOT NULL DEFAULT 0,
                    position INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent(),
                0,
            )
            Database.Schema.migrate(driver, 58, 59)
            val names = tableInfo(driver).sortedBy { it.first }.map { it.second }
            names.takeLast(2) shouldBe listOf("source_id", "source_url")
        } finally {
            driver.close()
        }
    }

    @Test
    fun `upgrade from 60 adds genres column`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            // Схема discovery_suggestions ДО миграции 61 (14 колонок).
            driver.execute(
                null,
                """
                CREATE TABLE discovery_suggestions (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    media_type TEXT NOT NULL,
                    row_type TEXT NOT NULL,
                    title TEXT NOT NULL,
                    clean_title TEXT NOT NULL,
                    cover_url TEXT,
                    reason TEXT,
                    seed_title TEXT,
                    provider TEXT NOT NULL,
                    score REAL NOT NULL DEFAULT 0,
                    position INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    source_id INTEGER,
                    source_url TEXT
                )
                """.trimIndent(),
                0,
            )
            Database.Schema.migrate(driver, 61, 62)
            val names = tableInfo(driver).sortedBy { it.first }.map { it.second }
            names.takeLast(3) shouldBe listOf("source_id", "source_url", "genres")
        } finally {
            driver.close()
        }
    }
}
