package tachiyomi.data.translation

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import datanovel.Novel_history
import datanovel.Novels
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.novel.AndroidNovelDatabaseHandler
import tachiyomi.domain.translation.model.TranslationFragment
import tachiyomi.domain.translation.model.TranslationRunStatus
import tachiyomi.novel.data.NovelDatabase

class TranslationQueueMigrationTest {

    @Test
    fun `migration 31 preserves legacy queue data and imports explicit and implicit ownership`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            createLegacyTables(driver)
            NovelDatabase.Schema.migrate(driver, 31, NovelDatabase.Schema.version)

            longs(driver, "SELECT status FROM translation_queue ORDER BY _id") shouldBe listOf(0L, 1L, 2L, 3L, 4L, 0L)
            longs(driver, "SELECT progress FROM translation_queue ORDER BY _id") shouldBe
                listOf(35L, 55L, 100L, 44L, 20L, 0L)
            longs(driver, "SELECT implicit_owner FROM translation_queue ORDER BY _id") shouldBe
                listOf(0L, 0L, 0L, 0L, 0L, 1L)
            longs(driver, "SELECT generation FROM translation_queue ORDER BY _id") shouldBe List(6) { 0L }
            longs(driver, "SELECT next_attempt_at FROM translation_queue ORDER BY _id") shouldBe List(6) { 0L }
            longs(driver, "SELECT session_only FROM translation_queue ORDER BY _id") shouldBe List(6) { 0L }
            strings(driver, "SELECT profile_snapshot_json FROM translation_queue ORDER BY _id") shouldBe
                listOf(null, "corrupt-profile", "{}", "{}", "{}", null)
            longs(driver, "SELECT retry_count FROM translation_queue ORDER BY _id") shouldBe
                listOf(2L, 3L, 0L, 1L, 0L, 0L)
            longs(driver, "SELECT outcome FROM translation_batch_membership ORDER BY chapter_id") shouldBe
                listOf(0L, 0L, 1L, 2L, 3L)
            longs(driver, "SELECT completed FROM translation_batch_state") shouldBe listOf(10L)
            longs(driver, "SELECT failed FROM translation_batch_state") shouldBe listOf(7L)
            longs(driver, "SELECT base_completed FROM translation_batch_state") shouldBe listOf(9L)
            longs(driver, "SELECT base_failed FROM translation_batch_state") shouldBe listOf(6L)
            longs(driver, "SELECT cancelled_count FROM translation_batch_state") shouldBe listOf(1L)
            longs(driver, "SELECT COUNT(*) FROM translation_checkpoint") shouldBe listOf(0L)
        } finally {
            driver.close()
        }
    }

    @Test
    fun `legacy completed and failed offsets survive later transactional terminal outcomes`() = runBlocking<Unit> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            createLegacyTables(driver)
            NovelDatabase.Schema.migrate(driver, 31, NovelDatabase.Schema.version)
            val db = openDatabase(driver)
            val repo = TranslationRunRepositoryImpl(
                AndroidNovelDatabaseHandler(db, driver),
                Json {
                    encodeDefaults =
                        true
                },
            )
            repo.recoverInterrupted(100)
            val first = repo.claimNext(101).shouldNotBeNull()
            first.chapterId shouldBe 11L
            val frozen = repo.freezeProfile(first, "{}", "legacy-profile", 102).shouldNotBeNull()
            repo.prepare(
                frozen,
                "legacy-source",
                "legacy-profile",
                1,
                listOf(TranslationFragment(0, 0, 0, 1, "fixture-source", translatedText = "fixture-result")),
                103,
            ) shouldBe true
            repo.publish(frozen, 104) {} shouldBe true
            val second = repo.claimNext(105).shouldNotBeNull()
            second.chapterId shouldBe 12L
            repo.release(second, TranslationRunStatus.FAILED, 55, "fixture-invalid-profile", now = 106) shouldBe true

            val batch = repo.getBatch("legacy").shouldNotBeNull()
            batch.total shouldBe 25
            batch.enqueued shouldBe 25
            batch.completed shouldBe 11
            batch.failed shouldBe 8
            batch.cancelled shouldBe 1
            batch.lastSuccessfulChapterId shouldBe 11L
            repo.publish(frozen, 107) {} shouldBe false
            repo.release(second, TranslationRunStatus.FAILED, 55, "duplicate", now = 107) shouldBe false
            repo.getBatch("legacy")?.completed shouldBe 11
            repo.getBatch("legacy")?.failed shouldBe 8
        } finally {
            driver.close()
        }
    }

    @Test
    fun `fresh and migrated queue layouts agree and checkpoint keys retain distinct generations`() {
        val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val migrated = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            NovelDatabase.Schema.create(fresh)
            createLegacyTables(migrated)
            NovelDatabase.Schema.migrate(migrated, 31, NovelDatabase.Schema.version)
            columns(fresh, "translation_queue") shouldBe columns(migrated, "translation_queue")
            columns(fresh, "translation_batch_state") shouldBe columns(migrated, "translation_batch_state")
            migrated.execute(null, "INSERT INTO translation_checkpoint VALUES (1, 0, 0, '{}', 1)", 0)
            migrated.execute(null, "INSERT INTO translation_checkpoint VALUES (1, 1, 0, '{}', 2)", 0)
            longs(migrated, "SELECT generation FROM translation_checkpoint ORDER BY generation") shouldBe
                listOf(0L, 1L)
        } finally {
            fresh.close()
            migrated.close()
        }
    }

    private fun createLegacyTables(driver: JdbcSqliteDriver) {
        driver.execute(
            null,
            """
                CREATE TABLE translation_queue (
                    _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    chapter_id INTEGER NOT NULL UNIQUE,
                    novel_id INTEGER NOT NULL,
                    batch_token TEXT NOT NULL DEFAULT '',
                    batch_order INTEGER NOT NULL DEFAULT 0,
                    profile_snapshot_json TEXT,
                    status INTEGER NOT NULL DEFAULT 0,
                    progress INTEGER NOT NULL DEFAULT 0,
                    error_message TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL DEFAULT 0,
                    retry_count INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
                CREATE TABLE translation_batch_state (
                    batch_token TEXT NOT NULL PRIMARY KEY,
                    novel_id INTEGER NOT NULL,
                    status INTEGER NOT NULL DEFAULT 0,
                    total INTEGER NOT NULL DEFAULT 0,
                    enqueued INTEGER NOT NULL DEFAULT 0,
                    skipped INTEGER NOT NULL DEFAULT 0,
                    completed INTEGER NOT NULL DEFAULT 0,
                    failed INTEGER NOT NULL DEFAULT 0,
                    last_successful_chapter_id INTEGER,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
                INSERT INTO translation_queue VALUES
                (1, 11, 1, 'legacy', 0, NULL, 0, 35, NULL, 10, 20, 2),
                (2, 12, 1, 'legacy', 1, 'corrupt-profile', 1, 55, 'fixture-error', 11, 21, 3),
                (3, 13, 1, 'legacy', 2, '{}', 2, 100, NULL, 12, 22, 0),
                (4, 14, 1, 'legacy', 3, '{}', 3, 44, 'fixture-error', 13, 23, 1),
                (5, 15, 1, 'legacy', 4, '{}', 4, 20, NULL, 14, 24, 0),
                (6, 16, 1, '', 0, NULL, 0, 0, NULL, 15, 25, 0)
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            "INSERT INTO translation_batch_state VALUES ('legacy', 1, 1, 25, 25, 0, 10, 7, 13, 1, 2)",
            0,
        )
    }

    private fun openDatabase(driver: JdbcSqliteDriver) = NovelDatabase(
        driver = driver,
        novel_chaptersAdapter = datanovel.Novel_chapters.Adapter(memoAdapter = MemoColumnAdapter),
        novel_historyAdapter = Novel_history.Adapter(last_readAdapter = DateColumnAdapter),
        novelsAdapter = Novels.Adapter(
            memoAdapter = MemoColumnAdapter,
            genreAdapter = StringListColumnAdapter,
            update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
            custom_genreAdapter = StringListColumnAdapter,
        ),
    )

    private fun longs(driver: JdbcSqliteDriver, sql: String): List<Long> = driver.executeQuery(
        null,
        sql,
        { cursor ->
            val result = mutableListOf<Long>()
            while (cursor.next().value) result += cursor.getLong(0)!!
            QueryResult.Value(result)
        },
        0,
    ).value

    private fun strings(driver: JdbcSqliteDriver, sql: String): List<String?> = driver.executeQuery(
        null,
        sql,
        { cursor ->
            val result = mutableListOf<String?>()
            while (cursor.next().value) result += cursor.getString(0)
            QueryResult.Value(result)
        },
        0,
    ).value

    private fun columns(driver: JdbcSqliteDriver, table: String): List<String> = driver.executeQuery(
        null,
        "PRAGMA table_info($table)",
        { cursor ->
            val result = mutableListOf<String>()
            while (cursor.next().value) result += cursor.getString(1)!!
            QueryResult.Value(result)
        },
        0,
    ).value
}
