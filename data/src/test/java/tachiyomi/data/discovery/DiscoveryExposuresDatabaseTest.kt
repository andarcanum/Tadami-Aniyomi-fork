package tachiyomi.data.discovery

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.manga.AndroidMangaDatabaseHandler
import tachiyomi.domain.discovery.model.DiscoveryMediaType

class DiscoveryExposuresDatabaseTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var repository: DiscoveryRepositoryImpl

    @BeforeEach
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        val database = Database(
            driver = driver,
            chaptersAdapter = data.Chapters.Adapter(memoAdapter = MemoColumnAdapter),
            historyAdapter = data.History.Adapter(last_readAdapter = DateColumnAdapter),
            mangasAdapter = data.Mangas.Adapter(
                memoAdapter = MemoColumnAdapter,
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
                custom_genreAdapter = StringListColumnAdapter,
            ),
        )
        repository = DiscoveryRepositoryImpl(
            AndroidMangaDatabaseHandler(
                db = database,
                driver = driver,
                queryDispatcher = Dispatchers.Default,
                transactionDispatcher = Dispatchers.Default,
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `exposures at 0, 47, 94 hours result in count 2 within 48 hour sliding window`() = runTest {
        val hour = 3600_000L
        val t0 = 0L
        val t47 = 47 * hour
        val t94 = 94 * hour

        repository.recordExposure(DiscoveryMediaType.ANIME, listOf("title a"), timestamp = t0)
        repository.recordExposure(DiscoveryMediaType.ANIME, listOf("title a"), timestamp = t47)
        repository.recordExposure(DiscoveryMediaType.ANIME, listOf("title a"), timestamp = t94)

        // Querying at 94h with 48h window: cutoff is 94h - 48h = 46h. Events at 47h and 94h are included (count = 2)
        val window = 48 * hour
        val since = t94 - window
        val count = repository.getShownWithCount(
            DiscoveryMediaType.ANIME,
            windowMillis =
            System.currentTimeMillis() - since,
        )
        count.single { it.first == "title a" }.third shouldBe 2
    }

    @Test
    fun `two exposure callbacks within 15 minutes deduplicate to single record`() = runTest {
        val t1 = 1_000_000L
        val t2 = t1 + 10 * 60_000L // 10 minutes later (< 15 min)

        repository.recordExposure(DiscoveryMediaType.MANGA, listOf("solo leveling"), timestamp = t1)
        repository.recordExposure(DiscoveryMediaType.MANGA, listOf("solo leveling"), timestamp = t2)

        val shown = repository.getShownWithCount(DiscoveryMediaType.MANGA, windowMillis = Long.MAX_VALUE / 2)
        shown.single() shouldBe Triple("solo leveling", t1, 1)
    }

    @Test
    fun `exposure callback after 15 minutes inserts new exposure record`() = runTest {
        val t1 = 1_000_000L
        val t2 = t1 + 16 * 60_000L // 16 minutes later (> 15 min)

        repository.recordExposure(DiscoveryMediaType.MANGA, listOf("solo leveling"), timestamp = t1)
        repository.recordExposure(DiscoveryMediaType.MANGA, listOf("solo leveling"), timestamp = t2)

        val shown = repository.getShownWithCount(DiscoveryMediaType.MANGA, windowMillis = Long.MAX_VALUE / 2)
        shown.single() shouldBe Triple("solo leveling", t2, 2)
    }

    @Test
    fun `exposures are isolated across media types`() = runTest {
        val t = 1_000_000L
        repository.recordExposure(DiscoveryMediaType.ANIME, listOf("title x"), timestamp = t)

        val animeShown = repository.getShownWithCount(DiscoveryMediaType.ANIME, windowMillis = Long.MAX_VALUE / 2)
        val mangaShown = repository.getShownWithCount(DiscoveryMediaType.MANGA, windowMillis = Long.MAX_VALUE / 2)
        val novelShown = repository.getShownWithCount(DiscoveryMediaType.NOVEL, windowMillis = Long.MAX_VALUE / 2)

        animeShown.map { it.first } shouldBe listOf("title x")
        mangaShown shouldBe emptyList()
        novelShown shouldBe emptyList()
    }

    @Test
    fun `cleanup removes exposures older than 48 hours`() = runTest {
        val hour = 3600_000L
        val now = System.currentTimeMillis()
        val oldTimestamp = now - 50 * hour // 50 hours old (> 48h)

        repository.recordExposure(DiscoveryMediaType.NOVEL, listOf("old title"), timestamp = oldTimestamp)

        // New exposure at now will trigger cleanupOld(cutoff)
        repository.recordExposure(DiscoveryMediaType.NOVEL, listOf("new title"), timestamp = now)

        val titles = repository.getShownTitles(DiscoveryMediaType.NOVEL, windowMillis = 100 * hour)
        titles shouldBe setOf("new title")
    }

    @Test
    fun `suppressRecentlyConsumed does not increase exposure count`() = runTest {
        val t = 1_000_000L
        repository.suppressRecentlyConsumed(DiscoveryMediaType.NOVEL, listOf("consumed book"), timestamp = t)

        val exposures = repository.getShownWithCount(DiscoveryMediaType.NOVEL, windowMillis = Long.MAX_VALUE / 2)
        exposures shouldBe emptyList()

        // But getRecentExclusions includes the suppressed title
        val exclusions = repository.getRecentExclusions(DiscoveryMediaType.NOVEL, windowMillis = Long.MAX_VALUE / 2)
        exclusions shouldBe setOf("consumed book")

        val exclusionsWithTime = repository.getRecentExclusionsWithTimestamp(
            DiscoveryMediaType.NOVEL,
            windowMillis =
            Long.MAX_VALUE / 2,
        )
        exclusionsWithTime["consumed book"] shouldBe t
    }
}
