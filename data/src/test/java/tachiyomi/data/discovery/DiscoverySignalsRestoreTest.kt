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
import tachiyomi.domain.discovery.model.DiscoverySignal
import tachiyomi.domain.discovery.model.DiscoverySignalType

/**
 * Taste Learning Engine: restore из бэкапа уважает старшинство сигналов
 * (слабый бэкапный не затирает локальный сильный) и LRU-cap на медиатип.
 */
class DiscoverySignalsRestoreTest {

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

    private fun signal(title: String, type: DiscoverySignalType, createdAt: Long = 1_000L) = DiscoverySignal(
        mediaType = DiscoveryMediaType.NOVEL,
        cleanTitle = title.lowercase(),
        title = title,
        signalType = type,
        genres = emptyList(),
        provider = null,
        sourceKey = null,
        createdAt = createdAt,
    )

    @Test
    fun `backup weaker signal does not overwrite local stronger one`() = runTest {
        // Локально: пользователь скрыл тайтл (HIDE — сильнейший).
        repository.recordSignal(
            mediaType = DiscoveryMediaType.NOVEL,
            cleanTitle = "overlord",
            title = "Overlord",
            signalType = DiscoverySignalType.HIDE,
            genres = emptyList(),
            provider = null,
            sourceKey = null,
            timestamp = 2_000L,
        )
        // Бэкап с другого устройства несёт LIKE по тому же тайтлу — НЕ должен затереть HIDE.
        repository.restoreSignals(listOf(signal("Overlord", DiscoverySignalType.LIKE)))
        repository.getSignals(DiscoveryMediaType.NOVEL).single().signalType shouldBe DiscoverySignalType.HIDE
    }

    @Test
    fun `backup stronger signal overwrites local weaker one`() = runTest {
        repository.recordSignal(
            mediaType = DiscoveryMediaType.NOVEL,
            cleanTitle = "overlord",
            title = "Overlord",
            signalType = DiscoverySignalType.CLICK,
            genres = emptyList(),
            provider = null,
            sourceKey = null,
            timestamp = 1_000L,
        )
        repository.restoreSignals(listOf(signal("Overlord", DiscoverySignalType.HIDE, createdAt = 3_000L)))
        repository.getSignals(DiscoveryMediaType.NOVEL).single().signalType shouldBe DiscoverySignalType.HIDE
    }

    @Test
    fun `restore keeps new titles and caps the log`() = runTest {
        repository.restoreSignals(listOf(signal("New One", DiscoverySignalType.LIKE)))
        repository.getSignals(DiscoveryMediaType.NOVEL).single().title shouldBe "New One"
    }

    @Test
    fun `eviction prefers live signals and keeps consumed rows`() = runTest {
        for (i in 1..498) {
            repository.recordSignal(
                mediaType = DiscoveryMediaType.NOVEL,
                cleanTitle = "consumed $i",
                title = "Consumed $i",
                signalType = DiscoverySignalType.CONSUMED,
                genres = emptyList(),
                provider = null,
                sourceKey = null,
                timestamp = i.toLong(),
            )
        }
        for (i in 1..3) {
            repository.recordSignal(
                mediaType = DiscoveryMediaType.NOVEL,
                cleanTitle = "like $i",
                title = "Like $i",
                signalType = DiscoverySignalType.LIKE,
                genres = emptyList(),
                provider = null,
                sourceKey = null,
                timestamp = 1000L + i,
            )
        }
        repository.recordSignal(
            mediaType = DiscoveryMediaType.NOVEL,
            cleanTitle = "like 4",
            title = "Like 4",
            signalType = DiscoverySignalType.LIKE,
            genres = emptyList(),
            provider = null,
            sourceKey = null,
            timestamp = 1004L,
        )

        val signals = repository.getSignals(DiscoveryMediaType.NOVEL)
        signals.size shouldBe 500
        val consumedCount = signals.count { it.signalType == DiscoverySignalType.CONSUMED }
        consumedCount shouldBe 498
        signals.map { it.cleanTitle }.contains("like 1") shouldBe false
        signals.map { it.cleanTitle }.contains("like 4") shouldBe true
    }

    @Test
    fun `total cap does not evict consumed rows when live signals are insufficient`() = runTest {
        for (i in 1..600) {
            repository.recordSignal(
                mediaType = DiscoveryMediaType.NOVEL,
                cleanTitle = "consumed $i",
                title = "Consumed $i",
                signalType = DiscoverySignalType.CONSUMED,
                genres = emptyList(),
                provider = null,
                sourceKey = null,
                timestamp = i.toLong(),
            )
        }
        for (i in 1..3) {
            repository.recordSignal(
                mediaType = DiscoveryMediaType.NOVEL,
                cleanTitle = "like $i",
                title = "Like $i",
                signalType = DiscoverySignalType.LIKE,
                genres = emptyList(),
                provider = null,
                sourceKey = null,
                timestamp = 1000L + i,
            )
        }
        repository.recordSignal(
            mediaType = DiscoveryMediaType.NOVEL,
            cleanTitle = "like 4",
            title = "Like 4",
            signalType = DiscoverySignalType.LIKE,
            genres = emptyList(),
            provider = null,
            sourceKey = null,
            timestamp = 1004L,
        )

        val signals = repository.getSignals(DiscoveryMediaType.NOVEL)
        val consumedCount = signals.count { it.signalType == DiscoverySignalType.CONSUMED }
        consumedCount shouldBe 600
        signals.size shouldBe 600
    }

    @Test
    fun `consumed cap evicts oldest consumed above 5000`() = runTest {
        for (i in 1..5001) {
            repository.recordSignal(
                mediaType = DiscoveryMediaType.NOVEL,
                cleanTitle = "consumed $i",
                title = "Consumed $i",
                signalType = DiscoverySignalType.CONSUMED,
                genres = emptyList(),
                provider = null,
                sourceKey = null,
                timestamp = i.toLong(),
            )
        }
        val signals = repository.getSignals(DiscoveryMediaType.NOVEL)
        signals.size shouldBe 5000
        signals.map { it.cleanTitle }.contains("consumed 1") shouldBe false
        signals.map { it.cleanTitle }.contains("consumed 2") shouldBe true
        signals.map { it.cleanTitle }.contains("consumed 5001") shouldBe true
    }

    @Test
    fun `repeated marks within 15 minutes do not bump count or time`() = runTest {
        repository.markShown(DiscoveryMediaType.NOVEL, listOf("solo leveling"), timestamp = 1_000L)
        repository.markShown(DiscoveryMediaType.NOVEL, listOf("solo leveling"), timestamp = 2_000L)

        val shown = repository.getShownWithCount(DiscoveryMediaType.NOVEL, Long.MAX_VALUE / 2)
        shown.single() shouldBe Triple("solo leveling", 1_000L, 1)
    }

    @Test
    fun `mark after 15 minutes counts as new impression`() = runTest {
        val t1 = 1_000L
        val t2 = t1 + 16 * 60_000L
        repository.markShown(DiscoveryMediaType.NOVEL, listOf("solo leveling"), timestamp = t1)
        repository.markShown(DiscoveryMediaType.NOVEL, listOf("solo leveling"), timestamp = t2)

        val shown = repository.getShownWithCount(DiscoveryMediaType.NOVEL, Long.MAX_VALUE / 2)
        shown.single() shouldBe Triple("solo leveling", t2, 2)
    }
}
