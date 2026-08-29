package tachiyomi.data.reels.anime

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import tachiyomi.mi.data.AnimeDatabase
import java.io.File

/**
 * Guards the reels_favorites 148.sqm recreate-table migration (contract v17 URL flip):
 * devices that ran an intermediate build upgrade through 146+147 and must keep their
 * favorites, with the legacy primary URL remapped onto the new video_url column.
 */
class ReelsFavoritesMigrationTest {

    @Test
    fun `schema version stays ahead of the highest migration`() {
        // SqlDelight derives the schema version from the migrations, so pinning a literal breaks on
        // every new .sqm; assert the invariant instead.
        val highestMigration = File("src/main/sqldelightanime/migrations")
            .listFiles { file -> file.extension == "sqm" }
            .orEmpty()
            .mapNotNull { it.nameWithoutExtension.toLongOrNull() }
            .maxOrNull()

        highestMigration shouldNotBe null
        AnimeDatabase.Schema.version shouldBe highestMigration!! + 1
    }

    @Test
    fun `148 remaps legacy favorite rows onto video_url without loss`() {
        val dbFile = File.createTempFile("reels-mig", ".db").apply { delete() }
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")

        // reels_favorites exactly as an upgrade device has it at db version 148: 146's CREATE
        // (no has_audio) plus 147's ALTER appending has_audio last, and only the added_at index.
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE reels_favorites(
                    video_id TEXT NOT NULL,
                    source_id INTEGER NOT NULL,
                    title TEXT,
                    author TEXT,
                    video_url_hd TEXT NOT NULL,
                    video_url_sd TEXT,
                    poster_url TEXT NOT NULL,
                    poster_url_vertical TEXT,
                    web_url TEXT,
                    duration_sec REAL,
                    added_at INTEGER NOT NULL,
                    has_audio INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (video_id, source_id)
                )
            """.trimIndent(),
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = "CREATE INDEX reels_favorites_added_at_index ON reels_favorites(added_at DESC);",
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = """
                INSERT INTO reels_favorites(
                    video_id, source_id, title, author, video_url_hd, video_url_sd,
                    poster_url, poster_url_vertical, web_url, duration_sec, added_at, has_audio
                ) VALUES ('vid-1', 101, 'T', 'A', 'https://cdn/primary.mp4', 'https://cdn/sd.mp4',
                          'https://cdn/p.jpg', NULL, 'https://site/vid-1', 12.5, 1700, 1);
            """.trimIndent(),
            parameters = 0,
        )

        // Upgrade from 148 to current runs only 148.sqm (the recreate).
        AnimeDatabase.Schema.migrate(driver, oldVersion = 148L, newVersion = AnimeDatabase.Schema.version)

        val columns = columnNames(driver, "reels_favorites")
        columns shouldContain "video_url"
        columns shouldContain "video_url_hd"
        columns shouldNotContain "video_url_sd"

        val row = firstRow(driver)
        // The legacy primary URL becomes the new base; the SD downgrade variant is dropped;
        // every other field must survive untouched.
        row shouldBe listOf(
            "vid-1",
            "101",
            "T",
            "A",
            "https://cdn/primary.mp4",
            null,
            "https://cdn/p.jpg",
            null,
            "https://site/vid-1",
            "12.5",
            "1700",
            "1",
        )

        val indexes = indexNames(driver)
        indexes shouldContain "reels_favorites_added_at_index"
        indexes shouldContain "reels_favorites_source_id_index"

        driver.close()
        dbFile.delete()
    }

    private fun firstRow(driver: JdbcSqliteDriver): List<String?> {
        return driver.executeQuery(
            identifier = null,
            sql = """
                SELECT video_id, source_id, title, author, video_url, video_url_hd, poster_url,
                       poster_url_vertical, web_url, duration_sec, added_at, has_audio
                FROM reels_favorites WHERE video_id = 'vid-1' AND source_id = 101
            """.trimIndent(),
            mapper = { cursor ->
                QueryResult.Value(
                    if (cursor.next().value) {
                        (0 until 12).map { cursor.getString(it) }
                    } else {
                        emptyList()
                    },
                )
            },
            parameters = 0,
        ).value
    }

    private fun columnNames(driver: JdbcSqliteDriver, table: String): List<String> {
        return driver.executeQuery(
            identifier = null,
            sql = "PRAGMA table_info($table)",
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
            sql = "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'reels_favorites'",
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
