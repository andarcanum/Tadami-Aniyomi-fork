package tachiyomi.data.discovery

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.data.Database

/**
 * Миграция 59.sqm (Taste Learning Engine): новая таблица discovery_signals.
 * Проверяем оба пути: свежая установка (CREATE TABLE из .sq) и апгрейд с v59
 * (CREATE TABLE из .sqm) — таблица доступна и колонки на месте.
 */
class DiscoverySignalsMigrationTest {

    private fun tableColumns(driver: JdbcSqliteDriver, table: String): List<String> {
        return driver.executeQuery(
            identifier = null,
            sql = "PRAGMA table_info($table)",
            mapper = { cursor: SqlCursor ->
                val rows = mutableListOf<String>()
                while (cursor.next().value) {
                    rows += cursor.getString(1)!!
                }
                QueryResult.Value(rows)
            },
            parameters = 0,
        ).value
    }

    @Test
    fun `fresh schema contains discovery_signals table`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            Database.Schema.create(driver)
            val columns = tableColumns(driver, "discovery_signals")
            columns shouldContain "media_type"
            columns shouldContain "clean_title"
            columns shouldContain "signal"
            columns shouldContain "weight"
            columns shouldContain "genres"
            columns shouldContain "source_key"
            columns shouldContain "created_at"
            columns shouldContain "identity_key"
            columns shouldContain "positive_signal"
            columns shouldContain "positive_created_at"
            columns.size shouldBe 17
        } finally {
            driver.close()
        }
    }

    @Test
    fun `upgrade from 59 creates discovery_signals table`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            // Схема ДО миграции 59 (v58/59 без discovery_signals): минимальная
            // база — проверяем, что migrate(59, 60) создаёт таблицу.
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
            Database.Schema.migrate(driver, 59, 60)
            val columns = tableColumns(driver, "discovery_signals")
            columns shouldContain "signal"
            columns shouldContain "weight"
            columns shouldContain "source_key"
            columns.size shouldBe 9
        } finally {
            driver.close()
        }
    }

    @Test
    fun `fresh schema no longer contains legacy discovery_shown table`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            Database.Schema.create(driver)
            tableColumns(driver, "discovery_shown").isEmpty() shouldBe true
        } finally {
            driver.close()
        }
    }

    @Test
    fun `upgrade from 60 adds shown_count to discovery_shown`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            // Схема ДО миграции 60 (v59/60 без shown_count): таблица discovery_shown
            // как она была после 59.sqm (media_type, clean_title, shown_at).
            driver.execute(
                null,
                """
                CREATE TABLE discovery_shown (
                    media_type TEXT NOT NULL,
                    clean_title TEXT NOT NULL,
                    shown_at INTEGER NOT NULL,
                    PRIMARY KEY (media_type, clean_title)
                )
                """.trimIndent(),
                0,
            )
            Database.Schema.migrate(driver, 60, 61)
            val columns = tableColumns(driver, "discovery_shown")
            columns shouldContain "shown_count"
            columns.size shouldBe 4
        } finally {
            driver.close()
        }
    }
}
