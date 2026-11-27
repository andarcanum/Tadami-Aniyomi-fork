package tachiyomi.data.translation.profile

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.novel.data.NovelDatabase

class ProfileCatalogMigrationTest {
    @Test
    fun `migration 32 preserves queue fragment and membership history while adding empty profile storage`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            legacy(driver)
            NovelDatabase.Schema.migrate(driver, 32, 33)
            rows(driver, "SELECT _id, generation, progress FROM translation_queue") shouldBe listOf("7:9:66")
            strings(driver, "SELECT profile_snapshot_json FROM translation_queue") shouldBe listOf("fixture-json")
            strings(driver, "SELECT payload_json FROM translation_checkpoint") shouldBe
                listOf("fixture-old-checkpoint")
            strings(driver, "SELECT batch_token FROM translation_batch_membership") shouldBe listOf("legacy")
            strings(driver, "SELECT CAST(outcome AS TEXT) FROM translation_batch_membership") shouldBe listOf("0")
            tables.forEach { table ->
                strings(driver, "SELECT CAST(COUNT(*) AS TEXT) FROM $table") shouldBe listOf("0")
            }
        } finally {
            driver.close()
        }
    }

    @Test
    fun `fresh and upgraded profile layouts agree without rebuilding legacy tables`() {
        val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val upgraded = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            NovelDatabase.Schema.create(fresh)
            legacy(upgraded)
            NovelDatabase.Schema.migrate(upgraded, 32, 33)
            tables.forEach { table -> columns(fresh, table) shouldBe columns(upgraded, table) }
        } finally {
            fresh.close()
            upgraded.close()
        }
    }

    private fun legacy(driver: JdbcSqliteDriver) {
        driver.execute(
            null,
            """
            CREATE TABLE translation_queue (
                _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, chapter_id INTEGER NOT NULL UNIQUE,
                novel_id INTEGER NOT NULL, batch_token TEXT NOT NULL DEFAULT '',
                batch_order INTEGER NOT NULL DEFAULT 0, profile_snapshot_json TEXT,
                status INTEGER NOT NULL DEFAULT 0, progress INTEGER NOT NULL DEFAULT 0, error_message TEXT,
                created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL DEFAULT 0,
                retry_count INTEGER NOT NULL DEFAULT 0, generation INTEGER NOT NULL DEFAULT 0,
                next_attempt_at INTEGER NOT NULL DEFAULT 0, source_fingerprint TEXT, profile_fingerprint TEXT,
                source_segment_count INTEGER NOT NULL DEFAULT 0, force_retranslate INTEGER NOT NULL DEFAULT 0,
                priority INTEGER NOT NULL DEFAULT 1, implicit_owner INTEGER NOT NULL DEFAULT 1,
                session_only INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE translation_checkpoint (
                queue_id INTEGER NOT NULL, generation INTEGER NOT NULL, fragment_id INTEGER NOT NULL,
                payload_json TEXT NOT NULL, updated_at INTEGER NOT NULL,
                PRIMARY KEY(queue_id, generation, fragment_id)
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE translation_batch_membership (
                batch_token TEXT NOT NULL, queue_id INTEGER NOT NULL, generation INTEGER NOT NULL,
                chapter_id INTEGER NOT NULL, batch_order INTEGER NOT NULL, outcome INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(batch_token, queue_id, generation)
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            INSERT INTO translation_queue VALUES
            (7,70,1,'legacy',2,'fixture-json',1,66,NULL,1,2,2,9,5000,'source-old','profile-old',3,0,1,0,0)
            """.trimIndent(),
            0,
        )
        driver.execute(null, "INSERT INTO translation_checkpoint VALUES(7,9,0,'fixture-old-checkpoint',2)", 0)
        driver.execute(null, "INSERT INTO translation_batch_membership VALUES('legacy',7,9,70,2,0)", 0)
    }

    private fun rows(driver: JdbcSqliteDriver, sql: String): List<String> = driver.executeQuery(
        null,
        sql,
        { cursor ->
            val rows = mutableListOf<String>()
            while (cursor.next().value) rows += "${cursor.getLong(0)}:${cursor.getLong(1)}:${cursor.getLong(2)}"
            QueryResult.Value(rows)
        },
        0,
    ).value

    private fun strings(driver: JdbcSqliteDriver, sql: String): List<String> = driver.executeQuery(
        null,
        sql,
        { cursor ->
            val rows = mutableListOf<String>()
            while (cursor.next().value) rows += cursor.getString(0)!!
            QueryResult.Value(rows)
        },
        0,
    ).value

    private fun columns(driver: JdbcSqliteDriver, table: String): List<String> = driver.executeQuery(
        null,
        "PRAGMA table_info($table)",
        { cursor ->
            val rows = mutableListOf<String>()
            while (cursor.next().value) rows += cursor.getString(1)!!
            QueryResult.Value(rows)
        },
        0,
    ).value

    private val tables = listOf(
        "translation_connections", "translation_profiles", "translation_credential_metadata",
        "translation_retired_credentials",
        "translation_profile_assignments", "translation_legacy_credential_refs", "translation_routing_state",
        "translation_request_ledgers", "translation_request_reservations", "translation_completed_fragments",
    )
}
