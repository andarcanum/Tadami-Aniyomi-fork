package tachiyomi.data.updates

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.mi.data.AnimeDatabase
import tachiyomi.novel.data.NovelDatabase

/**
 * Regression: the Updates tab re-runs its recent-chapters query as a Flow on every DB write,
 * and the underlying views filter on chapters.date_upload / episodes.date_upload /
 * novel_chapters.date_fetch -- none of which had an index. Every invalidation scanned the
 * whole chapter table while materializing rows, one of the memory-pressure/OOM drivers on
 * large libraries (crash log #bug 0.60).
 *
 * Strategy: Schema.create builds the full current schema *without* the new indexes (they are
 * migration-only), so the plan assertions below fail before the migration and pass after it.
 */
class UpdatesDateFilterIndexTest {

    private fun explainPlan(driver: JdbcSqliteDriver, sql: String): String {
        return driver.executeQuery(
            identifier = null,
            sql = "EXPLAIN QUERY PLAN $sql",
            mapper = { cursor: SqlCursor ->
                val details = StringBuilder()
                while (cursor.next().value) {
                    details.append(cursor.getString(3)).append('\n')
                }
                QueryResult.Value(details.toString())
            },
            parameters = 0,
        ).value
    }

    @Test
    fun `manga updates query uses date_upload index`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            val schema = Database.Schema
            schema.create(driver)
            val query = "SELECT * FROM updatesView WHERE dateUpload > 0 LIMIT 25"

            // Before the migration the date filter has no usable index.
            explainPlan(driver, query) shouldNotContain "chapters_manga_id_date_upload_index"

            schema.migrate(driver, schema.version - 1L, schema.version)

            explainPlan(driver, query) shouldContain "chapters_manga_id_date_upload_index"
        } finally {
            driver.close()
        }
    }

    @Test
    fun `anime updates query uses date_upload index`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            val schema = AnimeDatabase.Schema
            schema.create(driver)
            val query = "SELECT * FROM animeupdatesView WHERE seen = 0 AND dateUpload > 0 LIMIT 25"

            // Before the migration the date filter has no usable index.
            explainPlan(driver, query) shouldNotContain "episodes_anime_id_date_upload_index"

            schema.migrate(driver, schema.version - 1L, schema.version)

            explainPlan(driver, query) shouldContain "episodes_anime_id_date_upload_index"
        } finally {
            driver.close()
        }
    }

    @Test
    fun `novel updates query uses date_fetch index`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            val schema = NovelDatabase.Schema
            schema.create(driver)
            val query = "SELECT * FROM novelupdatesView WHERE datefetch > 0 LIMIT 25"

            // Before the migration the date filter has no usable index.
            explainPlan(driver, query) shouldNotContain "novel_chapters_novel_id_date_fetch_index"

            schema.migrate(driver, schema.version - 1L, schema.version)

            explainPlan(driver, query) shouldContain "novel_chapters_novel_id_date_fetch_index"
        } finally {
            driver.close()
        }
    }
}
