package tachiyomi.data.reels.anime

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.mi.data.AnimeDatabase
import java.io.File

class ReelsSessionMigrationTest {

    @Test
    fun `migration 157 creates session tables, extends candidate, and sanitizes telegram transport hints`() {
        val dbFile = File.createTempFile("reels-session-mig", ".db").apply { delete() }
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")

        // Migration 150 requires animes table
        driver.execute(null, "CREATE TABLE animes(id INTEGER NOT NULL PRIMARY KEY)", 0)

        // Set up pre-157 state (as created by 156.sqm)
        driver.execute(
            null,
            """
            CREATE TABLE IF NOT EXISTS reels_consumption(
                account_scope_id TEXT NOT NULL,
                source_id INTEGER NOT NULL,
                item_id TEXT NOT NULL,
                watched_qualified_at INTEGER,
                skipped_forward_at INTEGER,
                last_action_at INTEGER NOT NULL,
                last_surface TEXT,
                policy_version INTEGER NOT NULL,
                history_generation INTEGER NOT NULL,
                PRIMARY KEY (account_scope_id, source_id, item_id)
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE IF NOT EXISTS reels_candidate(
                account_scope_id TEXT NOT NULL,
                source_id INTEGER NOT NULL,
                item_id TEXT NOT NULL,
                title TEXT,
                author TEXT,
                video_url TEXT NOT NULL,
                video_url_hd TEXT,
                poster_url TEXT,
                poster_url_vertical TEXT,
                web_url TEXT,
                duration_sec REAL,
                has_audio INTEGER NOT NULL DEFAULT 0,
                created_at_epoch_sec INTEGER,
                discovered_at INTEGER NOT NULL,
                last_validated_at INTEGER,
                availability TEXT NOT NULL DEFAULT 'available',
                retry_after INTEGER,
                resume_position_ms INTEGER,
                metadata_schema_version INTEGER NOT NULL,
                PRIMARY KEY (account_scope_id, source_id, item_id)
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE IF NOT EXISTS reels_for_you_state(
                account_scope_id TEXT NOT NULL PRIMARY KEY,
                history_generation INTEGER NOT NULL DEFAULT 0,
                policy_version INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent(),
            0,
        )

        // Insert Telegram candidate (source_id = 8529104719284712L)
        driver.execute(
            null,
            """
            INSERT INTO reels_candidate(
                account_scope_id, source_id, item_id, title, author, video_url, video_url_hd,
                poster_url, poster_url_vertical, web_url, duration_sec, has_audio,
                created_at_epoch_sec, discovered_at, last_validated_at, availability,
                retry_after, resume_position_ms, metadata_schema_version
            ) VALUES (
                'tg_user_1', 8529104719284712, 'tg_clip_1', 'Telegram Title', '@tg',
                'https://tg.cdn/vid.mp4', 'https://tg.cdn/hd.mp4',
                'https://tg.cdn/poster.jpg', 'https://tg.cdn/vposter.jpg',
                'https://t.me/tg/1', 15.0, 1, 1700000000, 1000, 1000, 'available',
                NULL, 5000, 1
            );
            """.trimIndent(),
            0,
        )

        // Insert candidate from another source (source_id = 999999L)
        driver.execute(
            null,
            """
            INSERT INTO reels_candidate(
                account_scope_id, source_id, item_id, title, author, video_url, video_url_hd,
                poster_url, poster_url_vertical, web_url, duration_sec, has_audio,
                created_at_epoch_sec, discovered_at, last_validated_at, availability,
                retry_after, resume_position_ms, metadata_schema_version
            ) VALUES (
                'user_2', 999999, 'other_clip_1', 'Other Title', '@other',
                'https://other.cdn/vid.mp4', 'https://other.cdn/hd.mp4',
                'https://other.cdn/poster.jpg', NULL,
                'https://other.example/1', 20.0, 1, 1700000000, 2000, 2000, 'available',
                NULL, NULL, 1
            );
            """.trimIndent(),
            0,
        )

        // Migrate from 156 to current
        AnimeDatabase.Schema.migrate(driver, oldVersion = 156L, newVersion = AnimeDatabase.Schema.version)

        // Verify table existence
        val tables = tableNames(driver)
        tables shouldContainAll listOf("reels_session", "reels_session_queue", "reels_candidate")

        // Verify new columns on reels_candidate
        val candidateColumns = columnNames(driver, "reels_candidate")
        candidateColumns shouldContainAll listOf(
            "tags_json",
            "views_count",
            "likes_count",
            "is_author_verified",
            "captured_context_key",
        )

        // Verify reels_session columns
        val sessionColumns = columnNames(driver, "reels_session")
        sessionColumns shouldContainAll listOf(
            "account_scope_id",
            "source_id",
            "context_key",
            "session_id",
            "owner_nonce",
            "history_generation",
            "checkpoint_sequence",
            "active_item_id",
            "active_position_ms",
            "active_completed",
            "active_title",
            "active_author",
            "active_duration_sec",
            "active_has_audio",
            "active_tags_json",
            "active_web_url",
            "active_created_at_epoch_sec",
            "active_views_count",
            "active_likes_count",
            "active_is_author_verified",
            "active_metadata_schema_version",
            "updated_at_ms",
        )

        // Verify Telegram transport hints are blanked
        driver.executeQuery(
            null,
            "SELECT video_url, video_url_hd, poster_url FROM reels_candidate WHERE source_id = 8529104719284712",
            mapper = { cursor ->
                QueryResult.Value(
                    buildList {
                        while (cursor.next().value) {
                            add(Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
                        }
                    },
                )
            },
            0,
        ).value.first() shouldBe Triple("", null, null)

        // Verify other source transport hints are preserved intact
        driver.executeQuery(
            null,
            "SELECT video_url, video_url_hd, poster_url FROM reels_candidate WHERE source_id = 999999",
            mapper = { cursor ->
                QueryResult.Value(
                    buildList {
                        while (cursor.next().value) {
                            add(Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
                        }
                    },
                )
            },
            0,
        ).value.first() shouldBe
            Triple("https://other.cdn/vid.mp4", "https://other.cdn/hd.mp4", "https://other.cdn/poster.jpg")

        driver.close()
        dbFile.delete()
    }

    private fun tableNames(driver: JdbcSqliteDriver): List<String> {
        return driver.executeQuery(
            null,
            "SELECT name FROM sqlite_master WHERE type = 'table'",
            mapper = { cursor ->
                QueryResult.Value(
                    buildList {
                        while (cursor.next().value) {
                            add(cursor.getString(0).orEmpty())
                        }
                    },
                )
            },
            0,
        ).value
    }

    private fun columnNames(driver: JdbcSqliteDriver, tableName: String): List<String> {
        return driver.executeQuery(
            null,
            "PRAGMA table_info($tableName)",
            mapper = { cursor ->
                QueryResult.Value(
                    buildList {
                        while (cursor.next().value) {
                            add(cursor.getString(1).orEmpty())
                        }
                    },
                )
            },
            0,
        ).value
    }
}
