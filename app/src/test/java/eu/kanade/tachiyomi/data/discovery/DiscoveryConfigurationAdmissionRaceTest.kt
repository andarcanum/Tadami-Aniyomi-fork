package eu.kanade.tachiyomi.data.discovery

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySourceInventory
import tachiyomi.domain.discovery.model.DiscoverySourcePlugin
import tachiyomi.domain.discovery.model.DiscoveryTitleIdentity
import tachiyomi.domain.discovery.repository.DiscoveryKnownTitlesProvider
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import java.util.concurrent.atomic.AtomicBoolean

class DiscoveryConfigurationAdmissionRaceTest {
    @Test
    fun `initial empty top-up runs a fresh full generation`() =
        runTest {
            val media = DiscoveryMediaType.MANGA
            val preferences = DiscoveryPreferences(PersistingPreferenceStore()).apply {
                discoverySourceMode(media).set("manual")
                discoverySourceKnown(media).set("ext.a")
                rowLikeEnabled().set(false)
                rowTasteEnabled().set(false)
                rowTrendEnabled().set(false)
            }
            val sources = SourcePreferences(PersistingPreferenceStore())
            val frame = MutableStateFlow(
                DiscoverySourceInventory(
                    true,
                    listOf(DiscoverySourcePlugin("ext.a", listOf(11L))),
                ),
            )
            val policies = DiscoverySourcePolicyResolver(preferences, sources, {
                DiscoverySourceInventorySource({
                    frame.value
                }, frame)
            }, backgroundScope)
            runCurrent()
            Class.forName("org.sqlite.JDBC", true, JdbcSqliteDriver::class.java.classLoader)
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            try {
                Database.Schema.create(driver)
                val db = raceDatabase(driver)
                val known = object : DiscoveryKnownTitlesProvider {
                    override suspend fun snapshot(media: DiscoveryMediaType) = emptySet<DiscoveryTitleIdentity>()
                    override fun observe(media: DiscoveryMediaType) = flowOf(emptySet<DiscoveryTitleIdentity>())
                }
                val repository = DiscoveryRepositoryImpl(
                    AndroidMangaDatabaseHandler(
                        db,
                        driver,
                        Dispatchers.Default,
                        Dispatchers.Default,
                    ),
                    known,
                    AppDiscoveryCandidateValidator(preferences, policies) {
                        sources
                    },
                )
                val runner = raceRunner(repository, preferences, sources, policies) { listOf(raceItem("Fresh")) }
                runner.run(listOf(media), topUp = true).getValue(media).status shouldBe DiscoveryRunStatus.SUCCESS
                repository.publication(media).generation shouldBe 1L
                repository.subscribe(media).first().map { it.title } shouldBe listOf("Fresh")
            } finally {
                driver.close()
            }
        }

    @Test
    fun `top-up known snapshot rejects a prefs-only policy change without advancing cursors`() = runTest {
        val media = DiscoveryMediaType.MANGA
        val preferences = DiscoveryPreferences(PersistingPreferenceStore()).apply {
            discoverySourceMode(media).set("manual")
            discoverySourceKnown(media).set("ext.a")
            rowLikeEnabled().set(false)
            rowTasteEnabled().set(false)
            rowTrendEnabled().set(false)
        }
        val sources = SourcePreferences(PersistingPreferenceStore())
        val frame = MutableStateFlow(
            DiscoverySourceInventory(
                true,
                listOf(DiscoverySourcePlugin("ext.a", listOf(11L))),
            ),
        )
        val policies = DiscoverySourcePolicyResolver(preferences, sources, {
            DiscoverySourceInventorySource({
                frame.value
            }, frame)
        }, backgroundScope)
        runCurrent()
        Class.forName("org.sqlite.JDBC", true, JdbcSqliteDriver::class.java.classLoader)
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            Database.Schema.create(driver)
            val db = raceDatabase(driver)
            val arm = AtomicBoolean(false)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val known = object : DiscoveryKnownTitlesProvider {
                override suspend fun snapshot(media: DiscoveryMediaType): Set<DiscoveryTitleIdentity> {
                    if (arm.compareAndSet(true, false)) {
                        entered.complete(Unit)
                        release.await()
                    }
                    return emptySet()
                }
                override fun observe(media: DiscoveryMediaType) = flowOf(emptySet<DiscoveryTitleIdentity>())
            }
            val repository = DiscoveryRepositoryImpl(
                AndroidMangaDatabaseHandler(
                    db,
                    driver,
                    Dispatchers.Default,
                    Dispatchers.Default,
                ),
                known,
                AppDiscoveryCandidateValidator(preferences, policies) {
                    sources
                },
            )
            var phase = 0
            val runner = raceRunner(repository, preferences, sources, policies) {
                if (phase == 1) arm.set(true)
                listOf(raceItem(if (phase == 0) "Initial" else "Added"))
            }
            runner.run(listOf(media)).getValue(media).status shouldBe DiscoveryRunStatus.SUCCESS
            val before = repository.publication(media)
            phase = 1
            val updating = async { runner.run(listOf(media), topUp = true) }
            entered.await()
            sources.suggestionsUseShikimori().set(false)
            release.complete(Unit)
            updating.await().getValue(media).status shouldBe DiscoveryRunStatus.SKIPPED
            val after = repository.publication(media)
            after.suggestions shouldBe before.suggestions
            after.cursors shouldBe before.cursors
            after.generation shouldBe before.generation
        } finally {
            driver.close()
        }
    }

    private fun raceDatabase(driver: JdbcSqliteDriver) = Database(
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
    private fun raceRunner(
        repository: DiscoveryRepository,
        preferences: DiscoveryPreferences,
        sources: SourcePreferences,
        policies: DiscoverySourcePolicyResolver,
        items: () -> List<DiscoveryRowItem>,
    ) = DiscoveryRunner(
        repository,
        preferences,
        object : DiscoverySeedSources {
            override suspend fun candidates(mediaType: DiscoveryMediaType) = emptyList<DiscoverySeedInput>()
            override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = emptySet<String>()
        },
        coordinatorFactory = {
            DiscoveryCoordinator(
                listOf(object : DiscoveryRowBuilder {
                    override val rowType = DiscoveryRowType.SOURCE
                    override suspend fun build(context: DiscoveryBuildContext) = items()
                }),
            )
        },
        sourcePreferencesProvider = { sources },
        sourcePolicyProvider = policies,
    )
    private fun raceItem(
        title: String,
    ) = DiscoveryRowItem(
        title,
        title.lowercase(),
        null,
        null,
        null,
        "Source A",
        1.0,
        11L,
        "/" + title,
        listOf("Fantasy"),
    )

    @Test
    fun `provider change during publication snapshot supersedes without counter advance`() =
        runTest {
            val media = DiscoveryMediaType.MANGA
            val preferences = DiscoveryPreferences(PersistingPreferenceStore()).apply {
                discoverySourceMode(media).set("manual")
                discoverySourceKnown(media).set("ext.a")
                rowLikeEnabled().set(false)
                rowTasteEnabled().set(false)
                rowTrendEnabled().set(false)
                rowSourceEnabled().set(true)
            }
            val sources = SourcePreferences(PersistingPreferenceStore())
            val inventory = MutableStateFlow(
                DiscoverySourceInventory(
                    true,
                    listOf(DiscoverySourcePlugin("ext.a", listOf(11L))),
                ),
            )
            val policies = DiscoverySourcePolicyResolver(preferences, sources, {
                DiscoverySourceInventorySource({
                    inventory.value
                }, inventory)
            }, backgroundScope)
            runCurrent()
            Class.forName("org.sqlite.JDBC", true, JdbcSqliteDriver::class.java.classLoader)
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            try {
                Database.Schema.create(driver)
                val db = Database(
                    driver,
                    data.Chapters.Adapter(MemoColumnAdapter),
                    data.History.Adapter(DateColumnAdapter),
                    data.Mangas.Adapter(
                        memoAdapter = MemoColumnAdapter,
                        genreAdapter = StringListColumnAdapter,
                        update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
                        custom_genreAdapter = StringListColumnAdapter,
                    ),
                )
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val arm = AtomicBoolean(true)
                val known = object : DiscoveryKnownTitlesProvider {
                    override suspend fun snapshot(media: DiscoveryMediaType): Set<DiscoveryTitleIdentity> {
                        val owner = db.discovery_publicationQueries.selectByMedia(
                            media.key,
                        ).executeAsOneOrNull()?.owner_request_id
                        if (owner != null && arm.compareAndSet(true, false)) {
                            entered.complete(Unit)
                            release.await()
                        }
                        return emptySet()
                    }
                    override fun observe(media: DiscoveryMediaType) = flowOf(emptySet<DiscoveryTitleIdentity>())
                }
                val repository = DiscoveryRepositoryImpl(
                    AndroidMangaDatabaseHandler(db, driver, Dispatchers.Default, Dispatchers.Default),
                    known,
                    AppDiscoveryCandidateValidator(preferences, policies) { sources },
                )
                val builder = object : DiscoveryRowBuilder {
                    override val rowType = DiscoveryRowType.SOURCE
                    override suspend fun build(
                        context: DiscoveryBuildContext,
                    ) = listOf(
                        DiscoveryRowItem(
                            "Lawful",
                            "lawful",
                            null,
                            null,
                            null,
                            "Source A",
                            1.0,
                            11L,
                            "/lawful",
                            listOf("Fantasy"),
                        ),
                    )
                }
                val seeds = object : DiscoverySeedSources {
                    override suspend fun candidates(mediaType: DiscoveryMediaType) = emptyList<DiscoverySeedInput>()
                    override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = emptySet<String>()
                }
                val runner = DiscoveryRunner(repository, preferences, seeds, coordinatorFactory = {
                    DiscoveryCoordinator(listOf(builder))
                }, sourcePreferencesProvider = {
                    sources
                }, sourcePolicyProvider = policies)
                val generation = async { runner.run(listOf(media), isManualRefresh = true) }
                entered.await()
                sources.suggestionsUseShikimori().set(false)
                policies.current(media).allowsNative(11L) shouldBe true
                release.complete(Unit)
                val outcome = generation.await().getValue(media)
                outcome.status shouldBe DiscoveryRunStatus.SKIPPED
                preferences.manualRefreshCount(media).get() shouldBe 0
                repository.publication(media).generation shouldBe 0L
                repository.subscribe(media).first() shouldBe emptyList()
            } finally {
                driver.close()
            }
        }
}
