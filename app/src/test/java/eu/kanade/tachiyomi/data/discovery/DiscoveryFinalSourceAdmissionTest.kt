package eu.kanade.tachiyomi.data.discovery

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import data.Chapters
import data.History
import data.Mangas
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.discovery.DiscoveryRepositoryImpl
import tachiyomi.data.handlers.manga.AndroidMangaDatabaseHandler
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryPreparedPool
import tachiyomi.domain.discovery.model.DiscoveryPublishReceipt
import tachiyomi.domain.discovery.model.DiscoveryRetirementPolicy
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySourceInventory
import tachiyomi.domain.discovery.model.DiscoverySourcePlugin
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.model.DiscoveryTitleIdentity
import tachiyomi.domain.discovery.repository.DiscoveryKnownTitlesProvider

class DiscoveryFinalSourceAdmissionTest {
    @Test
    fun `real publication rejects a source excluded by the live manual policy`() = runTest {
        val media = DiscoveryMediaType.MANGA
        val preferences = DiscoveryPreferences(PersistingPreferenceStore()).apply {
            discoverySourceMode(media).set("manual")
            discoverySourceKnown(media).set("ext.allowed,ext.excluded")
            discoverySourceExcluded(media).set("ext.excluded")
        }
        val inventory = MutableStateFlow(
            DiscoverySourceInventory(
                complete = true,
                plugins = listOf(
                    DiscoverySourcePlugin("ext.allowed", setOf(11L)),
                    DiscoverySourcePlugin("ext.excluded", setOf(22L)),
                ),
            ),
        )
        val sources = SourcePreferences(PersistingPreferenceStore())
        val sourcePolicy = DiscoverySourcePolicyResolver(
            preferences = preferences,
            sourcePreferences = sources,
            inventorySourceLoader = { DiscoverySourceInventorySource({ inventory.value }, inventory) },
            scope = backgroundScope,
        )
        runCurrent()
        sourcePolicy.current(media).allowsNative(22L) shouldBe false
        sourcePolicy.current(media).allowsNative(11L) shouldBe true

        Class.forName("org.sqlite.JDBC", true, JdbcSqliteDriver::class.java.classLoader)
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            Database.Schema.create(driver)
            val database = Database(
                driver = driver,
                chaptersAdapter = Chapters.Adapter(memoAdapter = MemoColumnAdapter),
                historyAdapter = History.Adapter(last_readAdapter = DateColumnAdapter),
                mangasAdapter = Mangas.Adapter(
                    memoAdapter = MemoColumnAdapter,
                    genreAdapter = StringListColumnAdapter,
                    update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
                    custom_genreAdapter = StringListColumnAdapter,
                ),
            )
            val repository = DiscoveryRepositoryImpl(
                AndroidMangaDatabaseHandler(
                    db = database,
                    driver = driver,
                    queryDispatcher = Dispatchers.Default,
                    transactionDispatcher = Dispatchers.Default,
                ),
                knownTitlesProvider = object : DiscoveryKnownTitlesProvider {
                    override suspend fun snapshot(
                        media: DiscoveryMediaType,
                    ) = emptySet<DiscoveryTitleIdentity>()
                    override fun observe(media: DiscoveryMediaType) = kotlinx.coroutines.flow.flowOf(
                        emptySet<DiscoveryTitleIdentity>(),
                    )
                },
                candidateValidator = AppDiscoveryCandidateValidator(preferences, sourcePolicy) { sources },
            )
            val candidate = DiscoverySuggestion(
                id = 0L,
                mediaType = media,
                rowType = DiscoveryRowType.SOURCE,
                title = "Fresh excluded manga",
                cleanTitle = "fresh excluded manga",
                coverUrl = null,
                reason = null,
                seedTitle = null,
                provider = "Excluded plugin",
                score = 1.0,
                position = 0L,
                createdAt = 1L,
                sourceId = 22L,
                sourceUrl = "/excluded-manga",
                genres = listOf("Action"),
            )
            val token = repository.reserveGeneration(
                media,
                "excluded-publication",
                0L,
                computeDiscoveryConfigurationKey(preferences, media, sources, sourcePolicy.current(media).revision),
            )!!
            val receipt = repository.publishGeneration(
                token,
                DiscoveryPreparedPool.Fresh(listOf(candidate), partial = false),
                DiscoveryRetirementPolicy.NONE,
            )

            (receipt is DiscoveryPublishReceipt.NoAdmissibleItems) shouldBe true
            repository.subscribe(media).first() shouldBe emptyList()
        } finally {
            driver.close()
        }
    }

    @Test
    fun `committed SQLite publication keeps native candidates visible during cold start before inventoryComplete`() =
        runTest {
            val media = DiscoveryMediaType.MANGA
            val preferences = DiscoveryPreferences(PersistingPreferenceStore()).apply {
                discoverySourceMode(media).set("manual")
                discoverySourceKnown(media).set("ext.allowed")
                externalProvidersEnabled().set(false)
            }
            val inventory = MutableStateFlow(
                DiscoverySourceInventory(
                    complete = true,
                    plugins = listOf(DiscoverySourcePlugin("ext.allowed", setOf(11L))),
                ),
            )
            val sources = SourcePreferences(PersistingPreferenceStore())
            val sourcePolicy = DiscoverySourcePolicyResolver(
                preferences = preferences,
                sourcePreferences = sources,
                inventorySourceLoader = { DiscoverySourceInventorySource({ inventory.value }, inventory) },
                scope = backgroundScope,
            )
            runCurrent()

            Class.forName("org.sqlite.JDBC", true, JdbcSqliteDriver::class.java.classLoader)
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            try {
                Database.Schema.create(driver)
                val database = Database(
                    driver = driver,
                    chaptersAdapter = Chapters.Adapter(memoAdapter = MemoColumnAdapter),
                    historyAdapter = History.Adapter(last_readAdapter = DateColumnAdapter),
                    mangasAdapter = Mangas.Adapter(
                        memoAdapter = MemoColumnAdapter,
                        genreAdapter = StringListColumnAdapter,
                        update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
                        custom_genreAdapter = StringListColumnAdapter,
                    ),
                )
                val validator = AppDiscoveryCandidateValidator(preferences, sourcePolicy) { sources }
                val repository = DiscoveryRepositoryImpl(
                    AndroidMangaDatabaseHandler(
                        db = database,
                        driver = driver,
                        queryDispatcher = Dispatchers.Default,
                        transactionDispatcher = Dispatchers.Default,
                    ),
                    knownTitlesProvider = object : DiscoveryKnownTitlesProvider {
                        override suspend fun snapshot(
                            media: DiscoveryMediaType,
                        ) = emptySet<DiscoveryTitleIdentity>()
                        override fun observe(media: DiscoveryMediaType) = kotlinx.coroutines.flow.flowOf(
                            emptySet<DiscoveryTitleIdentity>(),
                        )
                    },
                    candidateValidator = validator,
                )
                val nativeCandidate = DiscoverySuggestion(
                    id = 0L,
                    mediaType = media,
                    rowType = DiscoveryRowType.SOURCE,
                    title = "Cached native manga",
                    cleanTitle = "cached native manga",
                    coverUrl = "https://example.com/cover.jpg",
                    reason = null,
                    seedTitle = null,
                    provider = "Allowed plugin",
                    score = 1.0,
                    position = 0L,
                    createdAt = 1L,
                    sourceId = 11L,
                    sourceUrl = "/cached-native-manga",
                    genres = listOf("Action"),
                )
                val token = repository.reserveGeneration(
                    media,
                    "cold-start-publication",
                    0L,
                    computeDiscoveryConfigurationKey(preferences, media, sources, sourcePolicy.current(media).revision),
                )!!
                val receipt = repository.publishGeneration(
                    token,
                    DiscoveryPreparedPool.Fresh(listOf(nativeCandidate), partial = false),
                    DiscoveryRetirementPolicy.NONE,
                )
                (receipt is DiscoveryPublishReceipt.Published) shouldBe true

                // Simulate cold start: extensions/sources have not finished initializing yet.
                inventory.value = DiscoverySourceInventory(complete = false, plugins = emptyList())
                runCurrent()
                sourcePolicy.current(media).inventoryComplete shouldBe false

                // Strict write-time admission still rejects native candidates while inventory is incomplete.
                validator.isAllowed(nativeCandidate, emptySet()) shouldBe false

                // Read-time snapshot keeps already-committed native candidates visible provisionally.
                val coldSnapshot = repository.observePublication(media).first()
                coldSnapshot.generation shouldBe 1L
                coldSnapshot.eligibleSuggestions.map { it.title } shouldBe listOf("Cached native manga")

                // Once inventory completes and excludes the source, strict admission removes it.
                preferences.discoverySourceExcluded(media).set("ext.allowed")
                inventory.value = DiscoverySourceInventory(
                    complete = true,
                    plugins = listOf(DiscoverySourcePlugin("ext.allowed", setOf(11L))),
                )
                runCurrent()
                repository.observePublication(media).first().eligibleSuggestions shouldBe emptyList()
            } finally {
                driver.close()
            }
        }
}
