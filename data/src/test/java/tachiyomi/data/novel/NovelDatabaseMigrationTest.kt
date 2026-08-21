package tachiyomi.data.novel

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import tachiyomi.novel.data.NovelDatabase
import java.io.File

class NovelDatabaseMigrationTest {

    @Test
    fun `schema version stays ahead of the highest migration`() {
        // SqlDelight derives the schema version from the migrations, so pinning a literal breaks on
        // every new .sqm; assert the invariant instead.
        val highestMigration = File("src/main/sqldelightnovel/migrations")
            .listFiles { file -> file.extension == "sqm" }
            .orEmpty()
            .mapNotNull { it.nameWithoutExtension.toLongOrNull() }
            .maxOrNull()

        highestMigration shouldNotBe null
        NovelDatabase.Schema.version shouldBe highestMigration!! + 1
    }

    @Test
    fun `migration narrows last modified triggers to business columns`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        createLegacyNovelVersionTables(driver)

        NovelDatabase.Schema.migrate(
            driver,
            oldVersion = 1L,
            newVersion = NovelDatabase.Schema.version,
        )

        columnNames(driver, "novel_chapters") shouldContain "date_upload_raw"
        triggerSql(driver, "update_last_modified_at_novel_chapters") shouldContain
            "AFTER UPDATE OF novel_id, url, name, scanlator, read, bookmark, last_page_read, chapter_number, source_order, date_fetch, date_upload, date_upload_raw ON novel_chapters"
        triggerSql(driver, "update_last_modified_at_novels") shouldContain
            "AFTER UPDATE OF source, url, author, description, genre, title, status, thumbnail_url, favorite, last_update, next_update, initialized, viewer, chapter_flags, cover_last_modified, date_added, update_strategy, calculate_interval ON novels"
    }

    @Test
    fun `migration creates translation queue table`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        createLegacyNovelVersionTables(driver)

        NovelDatabase.Schema.migrate(
            driver,
            oldVersion = 1L,
            newVersion = NovelDatabase.Schema.version,
        )

        columnNames(driver, "translation_queue") shouldContain "_id"
        columnNames(driver, "translation_queue") shouldContain "chapter_id"
        columnNames(driver, "translation_queue") shouldContain "novel_id"
        columnNames(driver, "translation_queue") shouldContain "batch_token"
        columnNames(driver, "translation_queue") shouldContain "batch_order"
        columnNames(driver, "translation_queue") shouldContain "profile_snapshot_json"
        columnNames(driver, "translation_batch_state") shouldContain "batch_token"
        columnNames(driver, "translation_batch_state") shouldContain "last_successful_chapter_id"
    }

    @Test
    fun `migration creates novel highlights table`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        createLegacyNovelVersionTables(driver)

        NovelDatabase.Schema.migrate(
            driver,
            oldVersion = 1L,
            newVersion = NovelDatabase.Schema.version,
        )

        columnNames(driver, "novel_highlights") shouldContain "_id"
        columnNames(driver, "novel_highlights") shouldContain "novel_id"
        columnNames(driver, "novel_highlights") shouldContain "chapter_id"
        columnNames(driver, "novel_highlights") shouldContain "block_index"
        columnNames(driver, "novel_highlights") shouldContain "char_start"
        columnNames(driver, "novel_highlights") shouldContain "char_end_exclusive"
        columnNames(driver, "novel_highlights") shouldContain "normalized_text"
        columnNames(driver, "novel_highlights") shouldContain "color_argb"
        columnNames(driver, "novel_highlights") shouldContain "note"
        columnNames(driver, "novel_highlights") shouldContain "created_at"
        columnNames(driver, "novel_highlights") shouldContain "updated_at"
        columnNames(driver, "novel_highlights") shouldContain "page_index"
        columnNames(driver, "novel_highlights") shouldContain "page_count"
        indexNames(driver) shouldContain "novel_highlights_novel_id_index"
        indexNames(driver) shouldContain "novel_highlights_chapter_id_index"
    }

    private fun createLegacyNovelVersionTables(driver: JdbcSqliteDriver) {
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE novels(
                    _id INTEGER NOT NULL PRIMARY KEY,
                    source INTEGER NOT NULL,
                    url TEXT NOT NULL,
                    author TEXT,
                    description TEXT,
                    genre TEXT,
                    title TEXT NOT NULL,
                    status INTEGER NOT NULL,
                    thumbnail_url TEXT,
                    favorite INTEGER NOT NULL,
                    last_update INTEGER,
                    next_update INTEGER,
                    initialized INTEGER NOT NULL,
                    viewer INTEGER NOT NULL,
                    chapter_flags INTEGER NOT NULL,
                    cover_last_modified INTEGER NOT NULL,
                    date_added INTEGER NOT NULL,
                    update_strategy INTEGER NOT NULL DEFAULT 0,
                    calculate_interval INTEGER DEFAULT 0 NOT NULL,
                    last_modified_at INTEGER NOT NULL DEFAULT 0,
                    favorite_modified_at INTEGER,
                    version INTEGER NOT NULL DEFAULT 0,
                    is_syncing INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent(),
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE novel_chapters(
                    _id INTEGER NOT NULL PRIMARY KEY,
                    novel_id INTEGER NOT NULL,
                    url TEXT NOT NULL,
                    name TEXT NOT NULL,
                    scanlator TEXT,
                    read INTEGER NOT NULL,
                    bookmark INTEGER NOT NULL,
                    last_page_read INTEGER NOT NULL,
                    chapter_number REAL NOT NULL,
                    source_order INTEGER NOT NULL,
                    date_fetch INTEGER NOT NULL,
                    date_upload INTEGER NOT NULL,
                    last_modified_at INTEGER NOT NULL DEFAULT 0,
                    version INTEGER NOT NULL DEFAULT 0,
                    is_syncing INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(novel_id) REFERENCES novels (_id)
                    ON DELETE CASCADE
                )
            """.trimIndent(),
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE novel_history(
                    _id INTEGER NOT NULL PRIMARY KEY,
                    chapter_id INTEGER NOT NULL UNIQUE,
                    last_read INTEGER,
                    time_read INTEGER NOT NULL,
                    FOREIGN KEY(chapter_id) REFERENCES novel_chapters (_id)
                    ON DELETE CASCADE
                )
            """.trimIndent(),
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = "CREATE INDEX novel_history_chapter_id_index ON novel_history(chapter_id);",
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE novel_excluded_scanlators(
                    novel_id INTEGER NOT NULL,
                    scanlator TEXT NOT NULL,
                    FOREIGN KEY(novel_id) REFERENCES novels (_id)
                    ON DELETE CASCADE
                )
            """.trimIndent(),
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = "CREATE INDEX novel_excluded_scanlators_novel_id_index ON novel_excluded_scanlators(novel_id);",
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE categories(
                    _id INTEGER NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    sort INTEGER NOT NULL,
                    flags INTEGER NOT NULL,
                    hidden INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent(),
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE novel_categories(
                    _id INTEGER NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    sort INTEGER NOT NULL,
                    flags INTEGER NOT NULL,
                    hidden INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent(),
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE novels_categories(
                    _id INTEGER NOT NULL PRIMARY KEY,
                    novel_id INTEGER NOT NULL,
                    category_id INTEGER NOT NULL,
                    FOREIGN KEY(category_id) REFERENCES novel_categories (_id)
                    ON DELETE CASCADE,
                    FOREIGN KEY(novel_id) REFERENCES novels (_id)
                    ON DELETE CASCADE
                )
            """.trimIndent(),
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = """
                CREATE TRIGGER update_last_modified_at_novel_chapters
                AFTER UPDATE ON novel_chapters
                FOR EACH ROW
                BEGIN
                  UPDATE novel_chapters
                  SET last_modified_at = strftime('%s', 'now')
                  WHERE _id = new._id;
                END
            """.trimIndent(),
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = """
                CREATE TRIGGER update_last_modified_at_novels
                AFTER UPDATE ON novels
                FOR EACH ROW
                BEGIN
                  UPDATE novels
                  SET last_modified_at = strftime('%s', 'now')
                  WHERE _id = new._id;
                END
            """.trimIndent(),
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE novel_plugins (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    site TEXT NOT NULL,
                    lang TEXT NOT NULL,
                    version INTEGER NOT NULL,
                    url TEXT NOT NULL,
                    icon_url TEXT,
                    custom_js TEXT,
                    custom_css TEXT,
                    has_settings INTEGER NOT NULL,
                    sha256 TEXT NOT NULL,
                    repo_url TEXT NOT NULL
                )
            """.trimIndent(),
            parameters = 0,
        )
    }

    private fun triggerSql(driver: JdbcSqliteDriver, triggerName: String): String {
        return driver.executeQuery(
            identifier = null,
            sql = "SELECT sql FROM sqlite_master WHERE type = 'trigger' AND name = ?",
            mapper = { cursor ->
                QueryResult.Value(
                    buildString {
                        if (cursor.next().value) {
                            append(cursor.getString(0))
                        }
                    },
                )
            },
            parameters = 1,
            binders = {
                bindString(0, triggerName)
            },
        ).value
    }

    private fun columnNames(driver: JdbcSqliteDriver, tableName: String): List<String> {
        return driver.executeQuery(
            identifier = null,
            sql = "PRAGMA table_info($tableName)",
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
            sql = "SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE 'novel_highlights%'",
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
}
