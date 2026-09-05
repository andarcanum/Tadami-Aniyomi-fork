package tachiyomi.data.series.manga

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import data.History
import data.Mangas
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.Database as MangaDatabase

/**
 * D-H1: manga_series.category_id has no FK, so deleting a category left its series with a
 * dangling id - the series group is not associated with any displayed category and its volumes
 * are suppressed as series members, making them invisible on every library page with no UI path
 * to recover. Pins the reset query used by DeleteMangaCategory and the repair statement of
 * migration 53.
 */
class MangaSeriesCategoryResetTest {

    private val migration53Sql = "UPDATE manga_series SET category_id = 0 " +
        "WHERE category_id != 0 AND category_id NOT IN (SELECT _id FROM categories);"

    @Test
    fun `resetCategory moves only the deleted category's series to default`() {
        withDb { db, _ ->
            val deletedCategoryId = db.insertCategory("Deleted")
            val survivingCategoryId = db.insertCategory("Surviving")
            val seriesInDeleted = db.insertSeries(categoryId = deletedCategoryId)
            val seriesInSurviving = db.insertSeries(categoryId = survivingCategoryId)

            db.manga_seriesQueries.resetCategory(deletedCategoryId)

            db.seriesCategory(seriesInDeleted) shouldBe 0L
            db.seriesCategory(seriesInSurviving) shouldBe survivingCategoryId
        }
    }

    @Test
    fun `migration repair statement rehomes dangling series only`() {
        withDb { db, driver ->
            val existingCategoryId = db.insertCategory("Existing")
            val seriesInExisting = db.insertSeries(categoryId = existingCategoryId)
            val danglingSeries = db.insertSeries(categoryId = 99L)
            val defaultSeries = db.insertSeries(categoryId = 0L)

            driver.execute(identifier = null, sql = migration53Sql, parameters = 0)

            db.seriesCategory(seriesInExisting) shouldBe existingCategoryId
            db.seriesCategory(danglingSeries) shouldBe 0L
            db.seriesCategory(defaultSeries) shouldBe 0L
        }
    }

    private fun withDb(block: (MangaDatabase, JdbcSqliteDriver) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MangaDatabase.Schema.create(driver)
        val db = MangaDatabase(
            driver = driver,
            chaptersAdapter = data.Chapters.Adapter(memoAdapter = MemoColumnAdapter),
            historyAdapter = History.Adapter(last_readAdapter = DateColumnAdapter),
            mangasAdapter = Mangas.Adapter(
                memoAdapter = MemoColumnAdapter,
                genreAdapter = StringListColumnAdapter,
                custom_genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
            ),
        )
        try {
            block(db, driver)
        } finally {
            driver.close()
        }
    }

    private fun MangaDatabase.insertCategory(name: String): Long {
        categoriesQueries.insert(name = name, order = 0L, flags = 0L)
        return categoriesQueries.selectLastInsertedRowId().executeAsOne()
    }

    private fun MangaDatabase.insertSeries(categoryId: Long): Long {
        manga_seriesQueries.insert(
            title = "Series in category $categoryId",
            description = null,
            categoryId = categoryId,
            sortOrder = 0L,
            dateAdded = 0L,
            coverLastModified = 0L,
            pinned = false,
            coverMode = 0L,
            coverEntryId = null,
        )
        return manga_seriesQueries.selectLastInsertedRowId().executeAsOne()
    }

    private fun MangaDatabase.seriesCategory(seriesId: Long): Long =
        manga_seriesQueries.getSeriesById(seriesId).executeAsOne().category_id
}
