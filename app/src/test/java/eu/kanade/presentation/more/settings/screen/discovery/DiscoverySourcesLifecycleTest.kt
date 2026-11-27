package eu.kanade.presentation.more.settings.screen.discovery

import android.content.Context
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.model.ScreenModelStore
import dev.mihon.injekt.patchInjekt
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.data.discovery.DiscoverySeedInput
import eu.kanade.tachiyomi.data.discovery.DiscoverySeedSources
import eu.kanade.tachiyomi.data.discovery.DiscoverySourceInventorySource
import eu.kanade.tachiyomi.data.discovery.DiscoverySourcePolicyResolver
import eu.kanade.tachiyomi.data.discovery.FakeDiscoveryRepositoryBase
import eu.kanade.tachiyomi.data.discovery.parseKeyCsv
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoverySignal
import tachiyomi.domain.discovery.model.DiscoverySignalType
import tachiyomi.domain.discovery.model.DiscoverySourceInventory
import tachiyomi.domain.discovery.model.DiscoverySourcePlugin
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektScope
import uy.kohesive.injekt.api.addSingleton

@OptIn(ExperimentalCoroutinesApi::class, InternalVoyagerApi::class)
class DiscoverySourcesLifecycleTest {
    private lateinit var previousInjekt: InjektScope
    private val fixtures = mutableListOf<Fixture>()

    @Before
    fun isolateRuntime() {
        previousInjekt = Injekt
        patchInjekt()
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun closeFixtures() {
        fixtures.forEach {
            ScreenModelStore.onDisposeNavigator(it.holderKey)
            it.scope.cancel()
        }
        Injekt = previousInjekt
        Dispatchers.resetMain()
    }

    @Test
    fun `partial Kotlin inventory cannot become the manual baseline before JS arrives`(): Unit =
        runBlocking {
            val fixture = fixture(mapOf(11L to "ext.kotlin"), complete = false, manual = true)
            withTimeout(5_000L) { fixture.model.state.first { DiscoveryMediaType.NOVEL in it.counts } }
            fixture.preferences.discoverySourceKnown(DiscoveryMediaType.NOVEL).get() shouldBe ""
            fixture.preferences.discoverySourceExcluded(DiscoveryMediaType.NOVEL).get() shouldBe ""
            fixture.setInventory(mapOf(11L to "ext.kotlin", 22L to "ext.js"), true)
            withTimeout(5_000L) {
                fixture.model.state.first {
                    it.entries.map { entry ->
                        entry.pluginKey
                    }.toSet() == setOf("ext.kotlin", "ext.js")
                }
            }
            parseKeyCsv(fixture.preferences.discoverySourceKnown(DiscoveryMediaType.NOVEL).get()) shouldBe setOf(
                "ext.kotlin",
                "ext.js",
            )
            fixture.preferences.discoverySourceExcluded(DiscoveryMediaType.NOVEL).get() shouldBe ""
            // A genuinely new plugin is still excluded after the complete baseline is committed.
            fixture.setInventory(mapOf(11L to "ext.kotlin", 22L to "ext.js", 33L to "ext.new"), true)
            withTimeout(5_000L) {
                fixture.model.state.first {
                    it.entries.any { entry ->
                        entry.pluginKey == "ext.new" && entry.excluded
                    }
                }
            }
            val excluded = fixture.preferences.discoverySourceExcluded(DiscoveryMediaType.NOVEL).get()
            parseKeyCsv(excluded) shouldBe setOf("ext.new")
        }

    @Test
    fun `cold AUTO picker prepares weights and affinity before participant projection`(): Unit =
        runBlocking {
            val fixture = fixture(
                mapOf(11L to "ext.a", 22L to "ext.b", 33L to "ext.c", 44L to "ext.d"),
                complete = true,
            )
            withTimeout(5_000L) { fixture.model.state.first { it.entries.size == 4 } }
            fixture.model.state.value.entries.first { it.id == 11L }.weight shouldBe 10
            val policy = fixture.policies.current(DiscoveryMediaType.NOVEL)
            policy.sourceWeights[11L] shouldBe 10.0
            policy.sourceWeights[33L] shouldBe 0.4
            (policy.sourceWeights.getValue(44L) > 1.0) shouldBe true
            fixture.model.state.value.entries.filter {
                it.isTop3Auto
            }.map {
                it.id
            }.toSet() shouldBe policy.representativeSourceIds.toSet()
            policy.representativeSourceIds shouldBe listOf(11L, 22L, 44L)
        }

    private fun fixture(keys: Map<Long, String>, complete: Boolean, manual: Boolean = false): Fixture {
        val media = DiscoveryMediaType.NOVEL
        val preferences = DiscoveryPreferences(PersistingPreferenceStore()).apply {
            lastSourcesMediaType().set(media.key)
            if (manual) discoverySourceMode(media).set("manual")
            sourceFailureHistory(media).set("33:1000:3")
        }
        val sourcePreferences = SourcePreferences(PersistingPreferenceStore())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val inventory = MutableStateFlow(inventory(keys, complete))
        val installed = MutableStateFlow(keys.values.map(::plugin))
        val nativeSources = MutableStateFlow(keys.keys.map(::source))
        val ownership = MutableStateFlow(keys)
        // Flow getter and list method share a JVM name.
        // This explicit fake avoids MockK return-type ambiguity.
        val sourceManager = object : NovelSourceManager {
            override val isInitialized = MutableStateFlow(true)
            override val catalogueSources = nativeSources
            override fun get(sourceKey: Long) = nativeSources.value.firstOrNull { it.id == sourceKey }
            override fun getOrStub(sourceKey: Long) = get(sourceKey) ?: error("Unexpected fixture source")
            override fun getOnlineSources() = emptyList<eu.kanade.tachiyomi.novelsource.online.NovelHttpSource>()
            override fun getCatalogueSources() = nativeSources.value
            override fun getStubSources() = emptyList<tachiyomi.domain.source.novel.model.StubNovelSource>()
        }
        val extensionManager = mockk<NovelExtensionManager>()
        every { extensionManager.installedPluginsFlow } returns installed
        every { extensionManager.getPluginId(any()) } answers { ownership.value[firstArg<Long>()] }
        Injekt.addSingleton<NovelSourceManager>(sourceManager)
        Injekt.addSingleton<NovelExtensionManager>(extensionManager)
        val mangaSources = object : MangaSourceManager {
            override val isInitialized = MutableStateFlow(true)
            override val catalogueSources = MutableStateFlow(emptyList<CatalogueSource>())
            override fun get(sourceKey: Long): eu.kanade.tachiyomi.source.MangaSource? = null
            override fun getOrStub(
                sourceKey: Long,
            ): eu.kanade.tachiyomi.source.MangaSource = error("Unexpected fixture source")
            override fun getOnlineSources() = emptyList<eu.kanade.tachiyomi.source.online.HttpSource>()
            override fun getCatalogueSources() = emptyList<CatalogueSource>()
            override fun getStubSources() = emptyList<tachiyomi.domain.source.manga.model.StubMangaSource>()
        }
        Injekt.addSingleton<MangaSourceManager>(mangaSources)
        val mangaExtensions = mockk<MangaExtensionManager>()
        every {
            mangaExtensions.installedExtensionsFlow
        } returns MutableStateFlow(emptyList<MangaExtension.Installed>())
        Injekt.addSingleton<MangaExtensionManager>(mangaExtensions)
        val animeSources = object : AnimeSourceManager {
            override val isInitialized = MutableStateFlow(true)
            override val sources = MutableStateFlow(emptyList<eu.kanade.tachiyomi.animesource.AnimeSource>())
            override val catalogueSources = MutableStateFlow(emptyList<AnimeCatalogueSource>())
            override fun get(sourceKey: Long): eu.kanade.tachiyomi.animesource.AnimeSource? = null
            override fun getOrStub(
                sourceKey: Long,
            ): eu.kanade.tachiyomi.animesource.AnimeSource = error("Unexpected fixture source")
            override fun getOnlineSources() = emptyList<eu.kanade.tachiyomi.animesource.online.AnimeHttpSource>()
            override fun getCatalogueSources() = emptyList<AnimeCatalogueSource>()
            override fun getStubSources() = emptyList<tachiyomi.domain.source.anime.model.StubAnimeSource>()
        }
        Injekt.addSingleton<AnimeSourceManager>(animeSources)
        val animeExtensions = mockk<AnimeExtensionManager>()
        every {
            animeExtensions.installedExtensionsFlow
        } returns MutableStateFlow(emptyList<AnimeExtension.Installed>())
        Injekt.addSingleton<AnimeExtensionManager>(animeExtensions)
        val policies = DiscoverySourcePolicyResolver(
            preferences = preferences,
            sourcePreferences = sourcePreferences,
            inventorySourceLoader = { requested ->
                val frame = if (requested == media) {
                    inventory
                } else {
                    MutableStateFlow(
                        DiscoverySourceInventory(
                            true,
                            emptyList(),
                        ),
                    )
                }
                DiscoverySourceInventorySource({ frame.value }, frame)
            },
            scope = scope,
            clock = { 1_000L },
        )
        val seeds = object : DiscoverySeedSources {
            override suspend fun candidates(
                mediaType: DiscoveryMediaType,
            ): List<DiscoverySeedInput> = if (mediaType != media) {
                emptyList()
            } else {
                mapOf(11L to 10, 22L to 9, 33L to 8).flatMap { (id, count) ->
                    (1..count).map {
                        DiscoverySeedInput(entryId = id * 100 + it, title = "Seed-$id-$it", sourceId = id)
                    }
                }
            }
            override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = emptySet<String>()
        }
        val signals = (1..20).map { index ->
            DiscoverySignal(
                mediaType = media,
                cleanTitle = "liked-$index",
                title = "Liked $index",
                signalType = DiscoverySignalType.LIKE,
                genres = emptyList(),
                provider = null,
                sourceKey = "ext.d",
                createdAt = System.currentTimeMillis(),
            )
        }
        val repository = object : FakeDiscoveryRepositoryBase() {
            override suspend fun getSignals(
                mediaType: DiscoveryMediaType,
            ) = signals
        }
        val holderKey = "picker-test-" + java.util.UUID.randomUUID()
        val model = ScreenModelStore.getOrPut<DiscoverySourcesScreenModel>(holderKey, null) {
            DiscoverySourcesScreenModel(
                mockk<Context>(relaxed = true),
                preferences,
                sourcePreferences,
                seeds,
                policies,
            ) {
                repository
            }
        }
        return Fixture(preferences, policies, model, scope, holderKey) { newKeys, isComplete ->
            ownership.value = newKeys
            nativeSources.value = newKeys.keys.map(::source)
            installed.value = newKeys.values.map(::plugin)
            inventory.value = inventory(newKeys, isComplete)
        }.also { fixtures += it }
    }

    private data class Fixture(
        val preferences: DiscoveryPreferences,
        val policies: DiscoverySourcePolicyResolver,
        val model: DiscoverySourcesScreenModel,
        val scope: CoroutineScope,
        val holderKey: String,
        val setInventory: (Map<Long, String>, Boolean) -> Unit,
    )

    private fun inventory(keys: Map<Long, String>, complete: Boolean) = DiscoverySourceInventory(
        complete,
        keys.map { (id, key) -> DiscoverySourcePlugin(key, listOf(id), mapOf(id to "en"), "1") },
    )
    private fun source(id: Long) = mockk<NovelCatalogueSource>().also { source ->
        every { source.id } returns id
        every { source.lang } returns "en"
    }
    private fun plugin(key: String) = NovelPlugin.Installed(
        id = key,
        name = key,
        site = "",
        lang = "en",
        versionCode = 1,
        versionName = "1",
        url = "",
        iconUrl = null,
        customJs = null,
        customCss = null,
        hasSettings = false,
        sha256 = "",
        repoUrl = "",
    )

    @Test
    fun `toggling mode and source skips redundant loadMedia when plugin inventory is unchanged`(): Unit =
        runBlocking {
            val base = fixture(mapOf(11L to "ext.a", 22L to "ext.b"), complete = true)
            withTimeout(5_000L) { base.model.state.first { it.entries.size == 2 } }
            val candidatesCalls = java.util.concurrent.atomic.AtomicInteger(0)
            val seeds = object : DiscoverySeedSources {
                override suspend fun candidates(mediaType: DiscoveryMediaType): List<DiscoverySeedInput> {
                    if (mediaType == DiscoveryMediaType.NOVEL) candidatesCalls.incrementAndGet()
                    return emptyList()
                }
                override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = emptySet<String>()
            }
            val holderKey = "skip-load-" + java.util.UUID.randomUUID()
            val countedModel = ScreenModelStore.getOrPut<DiscoverySourcesScreenModel>(holderKey, null) {
                DiscoverySourcesScreenModel(
                    mockk<Context>(relaxed = true),
                    base.preferences,
                    SourcePreferences(PersistingPreferenceStore()),
                    seeds,
                    base.policies,
                ) { object : FakeDiscoveryRepositoryBase() {} }
            }
            fixtures += Fixture(base.preferences, base.policies, countedModel, base.scope, holderKey) { _, _ -> }
            withTimeout(5_000L) { countedModel.state.first { it.entries.size == 2 } }
            candidatesCalls.get() shouldBe 1

            countedModel.setMode("manual")
            countedModel.toggleSource("ext.a")
            countedModel.toggleSource("ext.a")
            withTimeout(5_000L) {
                countedModel.state.first { it.mode == "manual" && it.entries.none { e -> e.excluded } }
            }
            candidatesCalls.get() shouldBe 1
        }

    @Test
    fun `inventory loader failure exposes error state and retry recovers`(): Unit =
        runBlocking {
            val media = DiscoveryMediaType.NOVEL
            val preferences = DiscoveryPreferences(PersistingPreferenceStore()).apply {
                lastSourcesMediaType().set(media.key)
            }
            val sourcePreferences = SourcePreferences(PersistingPreferenceStore())
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val allowRecover = java.util.concurrent.atomic.AtomicBoolean(false)
            val loads = java.util.concurrent.atomic.AtomicInteger(0)
            val nativeSources = MutableStateFlow(listOf(source(11L)))
            val installed = MutableStateFlow(listOf(plugin("ext.a")))
            val sourceManager = object : NovelSourceManager {
                override val isInitialized = MutableStateFlow(true)
                override val catalogueSources = nativeSources
                override fun get(sourceKey: Long) = nativeSources.value.firstOrNull { it.id == sourceKey }
                override fun getOrStub(sourceKey: Long) = get(sourceKey) ?: error("Unexpected")
                override fun getOnlineSources() = emptyList<eu.kanade.tachiyomi.novelsource.online.NovelHttpSource>()
                override fun getCatalogueSources() = nativeSources.value
                override fun getStubSources() = emptyList<tachiyomi.domain.source.novel.model.StubNovelSource>()
            }
            val extensionManager = mockk<NovelExtensionManager>().also {
                every { it.installedPluginsFlow } returns installed
                every { it.getPluginId(any()) } returns "ext.a"
            }
            Injekt.addSingleton<NovelSourceManager>(sourceManager)
            Injekt.addSingleton<NovelExtensionManager>(extensionManager)
            val mangaExt = mockk<MangaExtensionManager>().also {
                every { it.installedExtensionsFlow } returns MutableStateFlow(emptyList())
            }
            val animeExt = mockk<AnimeExtensionManager>().also {
                every { it.installedExtensionsFlow } returns MutableStateFlow(emptyList())
            }
            Injekt.addSingleton<MangaExtensionManager>(mangaExt)
            Injekt.addSingleton<AnimeExtensionManager>(animeExt)
            val policies = DiscoverySourcePolicyResolver(
                preferences = preferences,
                sourcePreferences = sourcePreferences,
                inventorySourceLoader = { requested ->
                    if (requested == media) {
                        loads.incrementAndGet()
                        if (!allowRecover.get()) {
                            throw java.io.IOException("inventory boom")
                        }
                    }
                    val frame = MutableStateFlow(inventory(mapOf(11L to "ext.a"), true))
                    DiscoverySourceInventorySource({ frame.value }, frame)
                },
                scope = scope,
            )
            val seeds = object : DiscoverySeedSources {
                override suspend fun candidates(mediaType: DiscoveryMediaType) = emptyList<DiscoverySeedInput>()
                override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = emptySet<String>()
            }
            val holderKey = "retry-test-" + java.util.UUID.randomUUID()
            val model = ScreenModelStore.getOrPut<DiscoverySourcesScreenModel>(holderKey, null) {
                DiscoverySourcesScreenModel(
                    mockk<Context>(relaxed = true),
                    preferences,
                    sourcePreferences,
                    seeds,
                    policies,
                ) { object : FakeDiscoveryRepositoryBase() {} }
            }
            fixtures += Fixture(preferences, policies, model, scope, holderKey) { _, _ -> }

            withTimeout(5_000L) {
                model.state.first { it.hasInventoryError && !it.isLoadingInventory }
            }
            val loadsBeforeRetry = loads.get()
            allowRecover.set(true)
            model.retryInventoryLoad()
            withTimeout(5_000L) {
                model.state.first { !it.hasInventoryError && !it.isLoadingInventory && it.entries.size == 1 }
            }
            loads.get() shouldBe (loadsBeforeRetry + 1)
        }

    @Test
    fun `loadMedia failure with complete inventory exposes error and retries`(): Unit =
        runBlocking {
            val base = fixture(mapOf(11L to "ext.a"), complete = true)
            withTimeout(5_000L) { base.model.state.first { it.entries.size == 1 } }
            val failCandidates = java.util.concurrent.atomic.AtomicBoolean(true)
            val seeds = object : DiscoverySeedSources {
                override suspend fun candidates(mediaType: DiscoveryMediaType): List<DiscoverySeedInput> {
                    if (mediaType == DiscoveryMediaType.NOVEL && failCandidates.get()) {
                        throw java.io.IOException("seed boom")
                    }
                    return emptyList()
                }
                override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = emptySet<String>()
            }
            val holderKey = "complete-error-" + java.util.UUID.randomUUID()
            val model = ScreenModelStore.getOrPut<DiscoverySourcesScreenModel>(holderKey, null) {
                DiscoverySourcesScreenModel(
                    mockk<Context>(relaxed = true),
                    base.preferences,
                    SourcePreferences(PersistingPreferenceStore()),
                    seeds,
                    base.policies,
                ) { object : FakeDiscoveryRepositoryBase() {} }
            }
            fixtures += Fixture(base.preferences, base.policies, model, base.scope, holderKey) { _, _ -> }

            withTimeout(5_000L) {
                model.state.first { it.hasInventoryError && !it.isLoadingInventory }
            }
            failCandidates.set(false)
            model.retryInventoryLoad()
            withTimeout(5_000L) {
                model.state.first { !it.hasInventoryError && it.entries.size == 1 }
            }
        }
}
