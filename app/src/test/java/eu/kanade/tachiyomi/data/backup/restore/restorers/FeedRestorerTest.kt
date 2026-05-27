package eu.kanade.tachiyomi.data.backup.restore.restorers

import `data`.History
import `data`.Mangas
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.Database as MangaDb
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.manga.AndroidMangaDatabaseHandler
import tachiyomi.domain.source.model.FeedListingType
import tachiyomi.data.source.FeedSavedSearchMapper

class FeedRestorerTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: MangaDb
    private lateinit var handler: AndroidMangaDatabaseHandler
    private lateinit var restorer: FeedRestorer

    @BeforeEach
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MangaDb.Schema.create(driver)
        database = MangaDb(
            driver = driver,
            historyAdapter = History.Adapter(
                last_readAdapter = DateColumnAdapter,
            ),
            mangasAdapter = Mangas.Adapter(
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
                custom_genreAdapter = StringListColumnAdapter,
            ),
        )
        handler = AndroidMangaDatabaseHandler(
            db = database,
            driver = driver,
            queryDispatcher = Dispatchers.Default,
            transactionDispatcher = Dispatchers.Default,
        )
        restorer = FeedRestorer(handler)
    }

    @AfterEach
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `restorer keeps listing type when importing feed rows`() = runTest {
        val backup = BackupFeed(
            source = 7L,
            global = true,
            feedOrder = 2,
            sourceType = 1L,
            listingType = 1L,
            savedSearchName = null,
            savedSearchQuery = null,
            savedSearchFiltersJson = null,
        )

        restorer.restoreFeeds(listOf(backup))

        val result = database.feed_saved_searchQueries
            .selectAllGlobal(1L, FeedSavedSearchMapper::map)
            .executeAsList()

        result.size shouldBe 1
        result.single().listingType.id shouldBe 1L
    }

    @Test
    fun `restorer keeps distinct listing types for the same source`() = runTest {
        database.feed_saved_searchQueries.insert(
            sourceId = 7L,
            sourceType = 1L,
            listingType = FeedListingType.LATEST.id,
            savedSearch = null,
            global = true,
        )

        val backup = BackupFeed(
            source = 7L,
            global = true,
            feedOrder = 2,
            sourceType = 1L,
            listingType = FeedListingType.POPULAR.id,
            savedSearchName = null,
            savedSearchQuery = null,
            savedSearchFiltersJson = null,
        )

        restorer.restoreFeeds(listOf(backup))

        val result = database.feed_saved_searchQueries
            .selectAllGlobal(1L, FeedSavedSearchMapper::map)
            .executeAsList()

        result.map { it.listingType } shouldBe listOf(
            FeedListingType.LATEST,
            FeedListingType.POPULAR,
        )
    }
}
