package tachiyomi.data.source

import `data`.History
import `data`.Mangas
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.data.Database as MangaDb
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.manga.AndroidMangaDatabaseHandler
import tachiyomi.domain.source.model.FeedListingType
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SourceType

@Execution(ExecutionMode.CONCURRENT)
class FeedSavedSearchRepositoryImplTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: MangaDb
    private lateinit var handler: AndroidMangaDatabaseHandler
    private lateinit var repository: FeedSavedSearchRepositoryImpl

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
        repository = FeedSavedSearchRepositoryImpl(handler)
    }

    @AfterEach
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `latest and popular feed rows stay separate`() = runTest {
        val latest = FeedSavedSearch(
            id = -1,
            source = 42L,
            sourceType = SourceType.ANIME,
            listingType = FeedListingType.LATEST,
            savedSearch = null,
            global = true,
            feedOrder = 0,
        )
        val popular = latest.copy(listingType = FeedListingType.POPULAR)

        repository.insert(latest)
        repository.insert(popular)

        val result = repository.getGlobal(SourceType.ANIME)
        result.map { it.listingType } shouldBe listOf(
            FeedListingType.LATEST,
            FeedListingType.POPULAR,
        )
    }

    @Test
    fun `insert duplicate with same listing type returns existing id`() = runTest {
        val feed = FeedSavedSearch(
            id = -1,
            source = 7L,
            sourceType = SourceType.MANGA,
            listingType = FeedListingType.LATEST,
            savedSearch = null,
            global = true,
            feedOrder = 0,
        )

        val firstId = repository.insert(feed)
        val secondId = repository.insert(feed)

        firstId shouldBe secondId
        repository.getGlobal(SourceType.MANGA).size shouldBe 1
    }

    @Test
    fun `insert different listing types for same source creates two rows`() = runTest {
        val latest = FeedSavedSearch(
            id = -1,
            source = 1L,
            sourceType = SourceType.ANIME,
            listingType = FeedListingType.LATEST,
            savedSearch = null,
            global = true,
            feedOrder = 0,
        )
        val popular = latest.copy(listingType = FeedListingType.POPULAR)

        repository.insert(latest)
        repository.insert(popular)

        repository.getGlobal(SourceType.ANIME).size shouldBe 2
    }

    @Test
    fun `delete removes row and preserves other`() = runTest {
        val latest = FeedSavedSearch(
            id = -1,
            source = 1L,
            sourceType = SourceType.ANIME,
            listingType = FeedListingType.LATEST,
            savedSearch = null,
            global = true,
            feedOrder = 0,
        )
        val popular = latest.copy(listingType = FeedListingType.POPULAR)

        val latestId = repository.insert(latest)!!
        repository.insert(popular)
        repository.delete(latestId)

        repository.getGlobal(SourceType.ANIME).size shouldBe 1
        repository.getGlobal(SourceType.ANIME).single().listingType shouldBe FeedListingType.POPULAR
    }
}
