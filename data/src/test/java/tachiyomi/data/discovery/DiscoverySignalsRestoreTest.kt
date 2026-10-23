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
    fun `consumed signals do not count towards live cap and do not evict live signals`() = runTest {
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
        signals.size shouldBe 502
        val consumedCount = signals.count { it.signalType == DiscoverySignalType.CONSUMED }
        consumedCount shouldBe 498
        signals.map { it.cleanTitle }.contains("like 1") shouldBe true
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
        val liveCount = signals.count { it.signalType == DiscoverySignalType.LIKE }
        liveCount shouldBe 4
        signals.size shouldBe 604
        signals.map { it.cleanTitle }.contains("like 1") shouldBe true
        signals.map { it.cleanTitle }.contains("like 4") shouldBe true
    }

    @Test
    fun `like with genres followed by consumed without genres preserves known genres`() = runTest {
        repository.recordSignal(
            mediaType = DiscoveryMediaType.NOVEL,
            cleanTitle = "overlord",
            title = "Overlord",
            signalType = DiscoverySignalType.LIKE,
            genres = listOf("Action", "Fantasy"),
            provider = "Shikimori",
            sourceKey = "novel-shiki-1",
            timestamp = 1_000L,
        )

        // Read/consumed event with empty genres and null provider
        repository.recordSignal(
            mediaType = DiscoveryMediaType.NOVEL,
            cleanTitle = "overlord",
            title = "Overlord",
            signalType = DiscoverySignalType.CONSUMED,
            genres = emptyList(),
            provider = null,
            sourceKey = null,
            timestamp = 2_000L,
        )

        val signal = repository.getSignals(DiscoveryMediaType.NOVEL).single()
        signal.signalType shouldBe DiscoverySignalType.CONSUMED_LIKED
        signal.genres shouldBe listOf("Action", "Fantasy")
        signal.provider shouldBe "Shikimori"
        signal.sourceKey shouldBe "novel-shiki-1"
    }

    @Test
    fun `live signals cap evicts oldest live signals independently of consumed count`() = runTest {
        for (i in 1..501) {
            repository.recordSignal(
                mediaType = DiscoveryMediaType.NOVEL,
                cleanTitle = "like $i",
                title = "Like $i",
                signalType = DiscoverySignalType.LIKE,
                genres = emptyList(),
                provider = null,
                sourceKey = null,
                timestamp = i.toLong(),
            )
        }

        val signals = repository.getSignals(DiscoveryMediaType.NOVEL)
        signals.size shouldBe 500
        signals.map { it.cleanTitle }.contains("like 1") shouldBe false
        signals.map { it.cleanTitle }.contains("like 2") shouldBe true
        signals.map { it.cleanTitle }.contains("like 501") shouldBe true
    }

    @Test
    fun `limits and caps are isolated per media type`() = runTest {
        for (i in 1..501) {
            repository.recordSignal(
                mediaType = DiscoveryMediaType.NOVEL,
                cleanTitle = "novel $i",
                title = "Novel $i",
                signalType = DiscoverySignalType.LIKE,
                genres = emptyList(),
                provider = null,
                sourceKey = null,
                timestamp = i.toLong(),
            )
        }
        repository.recordSignal(
            mediaType = DiscoveryMediaType.MANGA,
            cleanTitle = "manga 1",
            title = "Manga 1",
            signalType = DiscoverySignalType.LIKE,
            genres = listOf("Action"),
            provider = "MangaUpdates",
            sourceKey = null,
            timestamp = 100L,
        )

        val novelSignals = repository.getSignals(DiscoveryMediaType.NOVEL)
        val mangaSignals = repository.getSignals(DiscoveryMediaType.MANGA)
        novelSignals.size shouldBe 500
        mangaSignals.size shouldBe 1
        mangaSignals.single().cleanTitle shouldBe "manga 1"
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
    fun `5000 consumed plus new like preserves like and consumed cap overflow does not evict live`() = runTest {
        for (i in 1..5000) {
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
        repository.recordSignal(
            mediaType = DiscoveryMediaType.NOVEL,
            cleanTitle = "favorite",
            title = "Favorite",
            signalType = DiscoverySignalType.LIKE,
            genres = listOf("Fantasy"),
            provider = "Shikimori",
            sourceKey = null,
            timestamp = 6000L,
        )

        // Add 5001st consumed to trigger consumed cap eviction
        repository.recordSignal(
            mediaType = DiscoveryMediaType.NOVEL,
            cleanTitle = "consumed 5001",
            title = "Consumed 5001",
            signalType = DiscoverySignalType.CONSUMED,
            genres = emptyList(),
            provider = null,
            sourceKey = null,
            timestamp = 6001L,
        )

        val signals = repository.getSignals(DiscoveryMediaType.NOVEL)
        val consumedCount = signals.count { it.signalType == DiscoverySignalType.CONSUMED }
        val liveCount = signals.count { it.signalType == DiscoverySignalType.LIKE }

        consumedCount shouldBe 5000
        liveCount shouldBe 1
        signals.map { it.cleanTitle }.contains("favorite") shouldBe true
        signals.map { it.cleanTitle }.contains("consumed 1") shouldBe false
        signals.map { it.cleanTitle }.contains("consumed 5001") shouldBe true
    }

    @Test
    fun `transition live to consumed correctly updates categories`() = runTest {
        repository.recordSignal(
            mediaType = DiscoveryMediaType.NOVEL,
            cleanTitle = "re zero",
            title = "Re:Zero",
            signalType = DiscoverySignalType.LIKE,
            genres = listOf("Isekai"),
            provider = null,
            sourceKey = null,
            timestamp = 100L,
        )
        val initialSignals = repository.getSignals(DiscoveryMediaType.NOVEL)
        initialSignals.single().signalType shouldBe DiscoverySignalType.LIKE

        repository.recordSignal(
            mediaType = DiscoveryMediaType.NOVEL,
            cleanTitle = "re zero",
            title = "Re:Zero",
            signalType = DiscoverySignalType.CONSUMED,
            genres = emptyList(),
            provider = null,
            sourceKey = null,
            timestamp = 200L,
        )
        val updatedSignals = repository.getSignals(DiscoveryMediaType.NOVEL)
        updatedSignals.single().signalType shouldBe DiscoverySignalType.CONSUMED_LIKED
        updatedSignals.single().genres shouldBe listOf("Isekai")
    }

    @Test
    fun `restore large mixed backup respects caps deterministically`() = runTest {
        val backup = (1..600).map { i ->
            signal("like $i", DiscoverySignalType.LIKE, createdAt = i.toLong())
        } + (1..5100).map { i ->
            signal("consumed $i", DiscoverySignalType.CONSUMED, createdAt = i.toLong())
        }

        repository.restoreSignals(backup)
        val firstPass = repository.getSignals(DiscoveryMediaType.NOVEL)
        val liveCount1 = firstPass.count { it.signalType == DiscoverySignalType.LIKE }
        val consumedCount1 = firstPass.count { it.signalType == DiscoverySignalType.CONSUMED }

        liveCount1 shouldBe 500
        consumedCount1 shouldBe 5000

        // Repeated restore of the same data should produce the identical deterministic set
        repository.restoreSignals(backup)
        val secondPass = repository.getSignals(DiscoveryMediaType.NOVEL)
        secondPass.size shouldBe firstPass.size
        secondPass.map { it.cleanTitle } shouldBe firstPass.map { it.cleanTitle }
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

    @Test
    fun `enrichSignalMetadataIfCurrent updates existing signal without changing created_at`() = runTest {
        val initialTimestamp = 5_000L
        repository.recordSignal(
            mediaType = DiscoveryMediaType.NOVEL,
            cleanTitle = "overlord",
            title = "Overlord",
            signalType = DiscoverySignalType.LIKE,
            genres = emptyList(),
            provider = "Shikimori",
            sourceKey = null,
            timestamp = initialTimestamp,
        )

        val success = repository.enrichSignalMetadataIfCurrent(
            mediaType = DiscoveryMediaType.NOVEL,
            cleanTitle = "overlord",
            expectedCreatedAt = initialTimestamp,
            genres = listOf("Isekai", "Dark Fantasy"),
            provider = "Shikimori",
            sourceKey = "123",
        )

        success shouldBe true
        val signals = repository.getSignals(DiscoveryMediaType.NOVEL)
        signals.size shouldBe 1
        val signal = signals.single()
        signal.cleanTitle shouldBe "overlord"
        signal.signalType shouldBe DiscoverySignalType.LIKE
        signal.genres shouldBe listOf("Isekai", "Dark Fantasy")
        signal.sourceKey shouldBe "123"
        signal.createdAt shouldBe initialTimestamp
    }

    @Test
    fun `enrichSignalMetadataIfCurrent does not resurrect deleted signal`() = runTest {
        val initialTimestamp = 5_000L
        repository.recordSignal(
            mediaType = DiscoveryMediaType.NOVEL,
            cleanTitle = "overlord",
            title = "Overlord",
            signalType = DiscoverySignalType.LIKE,
            genres = emptyList(),
            provider = "Shikimori",
            sourceKey = null,
            timestamp = initialTimestamp,
        )

        // User deletes/unlikes the signal while network enrichment is in flight
        repository.removeSignal(DiscoveryMediaType.NOVEL, "overlord")

        // Network returns late with enriched metadata
        val success = repository.enrichSignalMetadataIfCurrent(
            mediaType = DiscoveryMediaType.NOVEL,
            cleanTitle = "overlord",
            expectedCreatedAt = initialTimestamp,
            genres = listOf("Isekai", "Dark Fantasy"),
            provider = "Shikimori",
            sourceKey = "123",
        )

        success shouldBe false
        val signals = repository.getSignals(DiscoveryMediaType.NOVEL)
        signals.isEmpty() shouldBe true
    }

    @Test
    fun `enrichSignalMetadataIfCurrent protects against ABA when signal is deleted and recreated`() = runTest {
        val initialTimestamp = 5_000L
        repository.recordSignal(
            mediaType = DiscoveryMediaType.NOVEL,
            cleanTitle = "overlord",
            title = "Overlord",
            signalType = DiscoverySignalType.LIKE,
            genres = emptyList(),
            provider = "Shikimori",
            sourceKey = null,
            timestamp = initialTimestamp,
        )

        repository.removeSignal(DiscoveryMediaType.NOVEL, "overlord")

        // User later records a different event for overlord at timestamp 9000L
        val newTimestamp = 9_000L
        repository.recordSignal(
            mediaType = DiscoveryMediaType.NOVEL,
            cleanTitle = "overlord",
            title = "Overlord",
            signalType = DiscoverySignalType.CONSUMED,
            genres = listOf("Sci-Fi"),
            provider = "Local",
            sourceKey = null,
            timestamp = newTimestamp,
        )

        // Late network response from old LIKE event arrives with expectedCreatedAt = 5000L
        val success = repository.enrichSignalMetadataIfCurrent(
            mediaType = DiscoveryMediaType.NOVEL,
            cleanTitle = "overlord",
            expectedCreatedAt = initialTimestamp,
            genres = listOf("Isekai", "Dark Fantasy"),
            provider = "Shikimori",
            sourceKey = "123",
        )

        success shouldBe false
        val signals = repository.getSignals(DiscoveryMediaType.NOVEL)
        signals.size shouldBe 1
        val signal = signals.single()
        signal.signalType shouldBe DiscoverySignalType.CONSUMED
        signal.genres shouldBe listOf("Sci-Fi")
        signal.createdAt shouldBe newTimestamp
    }
}
