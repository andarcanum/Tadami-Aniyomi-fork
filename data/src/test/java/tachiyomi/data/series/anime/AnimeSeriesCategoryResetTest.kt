package tachiyomi.data.series.anime

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dataanime.Animehistory
import dataanime.Animes
import dataanime.Episodes
import dataanime.Reels_album_watched
import dataanime.Reels_albums
import dataanime.Reels_favorites
import dataanime.Reels_follows
import dataanime.Reels_hidden
import dataanime.Reels_watch_history
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.data.AnimeUpdateStrategyColumnAdapter
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.FetchTypeColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.mi.data.AnimeDatabase

class AnimeSeriesCategoryResetTest {

    @Test
    fun `resetCategory moves only the deleted category's series to default`() {
        withDb { db, _ ->
            val deletedCategoryId = db.insertCategory("Deleted")
            val survivingCategoryId = db.insertCategory("Surviving")
            val seriesInDeleted = db.insertSeries(categoryId = deletedCategoryId)
            val seriesInSurviving = db.insertSeries(categoryId = survivingCategoryId)

            db.anime_seriesQueries.resetCategory(deletedCategoryId)

            db.seriesCategory(seriesInDeleted) shouldBe 0L
            db.seriesCategory(seriesInSurviving) shouldBe survivingCategoryId
        }
    }

    private fun withDb(block: (AnimeDatabase, JdbcSqliteDriver) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AnimeDatabase.Schema.create(driver)
        val db = AnimeDatabase(
            driver = driver,
            animehistoryAdapter = Animehistory.Adapter(
                last_seenAdapter = DateColumnAdapter,
            ),
            episodesAdapter = Episodes.Adapter(memoAdapter = MemoColumnAdapter),
            animesAdapter = Animes.Adapter(
                memoAdapter = MemoColumnAdapter,
                genreAdapter = StringListColumnAdapter,
                custom_genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = AnimeUpdateStrategyColumnAdapter,
                fetch_typeAdapter = FetchTypeColumnAdapter,
            ),
            reels_favoritesAdapter = Reels_favorites.Adapter(
                added_atAdapter = DateColumnAdapter,
            ),
            reels_followsAdapter = Reels_follows.Adapter(
                added_atAdapter = DateColumnAdapter,
            ),
            reels_watch_historyAdapter = Reels_watch_history.Adapter(
                watched_atAdapter = DateColumnAdapter,
            ),
            reels_hiddenAdapter = Reels_hidden.Adapter(
                hidden_atAdapter = DateColumnAdapter,
            ),
            reels_albumsAdapter = Reels_albums.Adapter(
                added_atAdapter = DateColumnAdapter,
            ),
            reels_album_watchedAdapter = Reels_album_watched.Adapter(
                marked_atAdapter = DateColumnAdapter,
            ),
        )
        try {
            block(db, driver)
        } finally {
            driver.close()
        }
    }

    private fun AnimeDatabase.insertCategory(name: String): Long {
        categoriesQueries.insert(name = name, order = 0L, flags = 0L)
        return categoriesQueries.selectLastInsertedRowId().executeAsOne()
    }

    private fun AnimeDatabase.insertSeries(categoryId: Long): Long {
        anime_seriesQueries.insert(
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
        return anime_seriesQueries.selectLastInsertedRowId().executeAsOne()
    }

    private fun AnimeDatabase.seriesCategory(seriesId: Long): Long =
        anime_seriesQueries.getSeriesById(seriesId).executeAsOne().category_id
}
