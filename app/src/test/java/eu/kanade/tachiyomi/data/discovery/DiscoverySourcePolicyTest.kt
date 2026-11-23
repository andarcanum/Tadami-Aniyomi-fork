package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySourceInventory
import tachiyomi.domain.discovery.model.DiscoverySourcePlugin

class DiscoverySourcePolicyTest {
    @org.junit.Test
    fun `a failed initial loader rearms once on the next bounded readiness request`() =
        kotlinx.coroutines.test.runTest {
            val media = DiscoveryMediaType.MANGA
            var loads = 0
            val frame = kotlinx.coroutines.flow.MutableStateFlow(
                DiscoverySourceInventory(
                    true,
                    listOf(DiscoverySourcePlugin("recovered", listOf(7L))),
                ),
            )
            val resolver = DiscoverySourcePolicyResolver(
                preferences = DiscoveryPreferences(PersistingPreferenceStore()),
                sourcePreferences = SourcePreferences(PersistingPreferenceStore()),
                inventorySourceLoader = { requested ->
                    if (requested == media && ++loads == 1) throw java.io.IOException("initial loader failure")
                    DiscoverySourceInventorySource({ frame.value }, frame)
                },
                scope = backgroundScope,
            )
            runCurrent()
            resolver.current(media).inventoryComplete shouldBe false
            val recovered = kotlinx.coroutines.withTimeoutOrNull(100L) { resolver.awaitComplete(media) }
            (recovered?.allowsNative(7L) == true) shouldBe true
            loads shouldBe 2
        }

    @org.junit.Test
    fun `awaitComplete fails fast when loader retry throws instead of hanging forever`() =
        kotlinx.coroutines.test.runTest {
            val media = DiscoveryMediaType.MANGA
            var loads = 0
            val resolver = DiscoverySourcePolicyResolver(
                preferences = DiscoveryPreferences(PersistingPreferenceStore()),
                sourcePreferences = SourcePreferences(PersistingPreferenceStore()),
                inventorySourceLoader = { requested ->
                    if (requested == media) loads++
                    throw java.io.IOException("persistent loader failure")
                },
                scope = backgroundScope,
            )
            runCurrent()
            shouldThrow<java.io.IOException> {
                withTimeout(500L) { resolver.awaitComplete(media) }
            }
            loads shouldBe 2
        }

    @Test
    fun `manual selection does not query an excluded library source while extension inventory is unavailable`() =
        runTest {
            val preferences = DiscoveryPreferences(PersistingPreferenceStore()).apply {
                discoverySourceMode(DiscoveryMediaType.MANGA).set("manual")
                discoverySourceKnown(DiscoveryMediaType.MANGA).set("ext.allowed,ext.excluded")
                discoverySourceExcluded(DiscoveryMediaType.MANGA).set("ext.excluded")
                rowLikeEnabled().set(false)
                rowTasteEnabled().set(false)
                rowTrendEnabled().set(false)
                rowSourceEnabled().set(true)
                externalProvidersEnabled().set(false)
            }
            val sourcePreferences = SourcePreferences(PersistingPreferenceStore()).apply {
                lastUsedMangaSource().set(22L)
            }
            val catalog = RecordingNativeCatalog()
            val runner = DiscoveryRunner(
                repository = object : FakeDiscoveryRepositoryBase() {},
                preferences = preferences,
                seedSources = object : DiscoverySeedSources {
                    override suspend fun candidates(mediaType: DiscoveryMediaType) = listOf(
                        DiscoverySeedInput(
                            entryId = 1L,
                            title = "An already saved manga",
                            sourceId = 22L,
                            genres = listOf("Action"),
                        ),
                    )

                    override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = emptySet<String>()
                },
                sourcePreferencesProvider = { sourcePreferences },
                sourceCatalog = catalog,
                installedPluginsProvider = { emptyList() },
                fallbackSourceIdProvider = { 22L },
            )

            val outcome = runner.run(listOf(DiscoveryMediaType.MANGA)).getValue(DiscoveryMediaType.MANGA)

            outcome.cause shouldBe null
            catalog.requests.toList() shouldBe emptyList()
            preferences.discoverySourceExcluded(DiscoveryMediaType.MANGA).get() shouldBe "ext.excluded"
        }

    @Test
    fun `loaded policy keeps selected language members and reads exclusions before flow collectors run`() = runTest {
        val preferences = DiscoveryPreferences(PersistingPreferenceStore()).apply {
            discoverySourceMode(DiscoveryMediaType.MANGA).set("manual")
            discoverySourceKnown(DiscoveryMediaType.MANGA).set("ext.allowed,ext.excluded")
            discoverySourceExcluded(DiscoveryMediaType.MANGA).set("ext.excluded")
        }
        val sourcePreferences = SourcePreferences(PersistingPreferenceStore())
        val inventory = MutableStateFlow(DiscoverySourceInventory.Unavailable)
        val loads = mutableListOf<DiscoveryMediaType>()
        val resolver = resolverFor(preferences, sourcePreferences, inventory, backgroundScope, loads)
        val loadsBeforeCurrent = loads.size

        resolver.current(DiscoveryMediaType.MANGA).allowsNative(12L) shouldBe false
        loads.size shouldBe loadsBeforeCurrent
        runCurrent()
        inventory.value = DiscoverySourceInventory(
            complete = true,
            plugins = listOf(
                DiscoverySourcePlugin("ext.allowed", listOf(11L, 12L), mapOf(11L to "en", 12L to "ru"), "1"),
                DiscoverySourcePlugin("ext.excluded", listOf(21L, 22L), mapOf(21L to "en", 22L to "ru"), "1"),
            ),
        )
        resolver.updateRetrievalWeights(DiscoveryMediaType.MANGA, mapOf(12L to 2), emptyMap(), appLanguage = "en")
        val loaded = resolver.current(DiscoveryMediaType.MANGA)

        loaded.inventoryComplete shouldBe true
        loaded.allowedSourceIds shouldBe setOf(11L, 12L)
        loaded.representativeSourceIds shouldBe listOf(12L)
        loaded.primarySourceId(22L) shouldBe 12L
        loaded.primarySourceId(999L) shouldBe 12L
        loaded.allowsNative(0L) shouldBe false
        loaded.allowsNative(-42L) shouldBe false
        sourcePreferences.suggestionsUseShikimori().set(false)
        val providerChange = resolver.current(DiscoveryMediaType.MANGA)
        providerChange.allows(DiscoveryRowType.LIKE, null, "Shikimori") shouldBe false
        providerChange.allows(DiscoveryRowType.TASTE, null, "shikimori_trend") shouldBe true
        preferences.externalProvidersEnabled().set(false)
        val externalChange = resolver.current(DiscoveryMediaType.MANGA)
        externalChange.allows(DiscoveryRowType.LIKE, null, "AniList") shouldBe false
        externalChange.allowsNative(11L) shouldBe true
        preferences.discoverySourceExcluded(DiscoveryMediaType.MANGA).set("ext.allowed,ext.excluded")
        resolver.current(DiscoveryMediaType.MANGA).allowedSourceIds shouldBe emptySet()
    }

    @Test
    fun `complete empty inventory finishes without native fallback and leaves external policy independent`() = runTest {
        val preferences = DiscoveryPreferences(PersistingPreferenceStore()).apply {
            discoverySourceMode(DiscoveryMediaType.MANGA).set("manual")
            discoverySourceKnown(DiscoveryMediaType.MANGA).set("ext.removed")
        }
        val inventory = MutableStateFlow(DiscoverySourceInventory(complete = true, plugins = emptyList()))
        val resolver = resolverFor(
            preferences,
            SourcePreferences(PersistingPreferenceStore()),
            inventory,
            backgroundScope,
        )

        val policy = withTimeout(1_000L) { resolver.awaitComplete(DiscoveryMediaType.MANGA) }

        policy.inventoryComplete shouldBe true
        policy.representativeSourceIds shouldBe emptyList()
        policy.primarySourceId(22L) shouldBe -1L
        policy.allows(DiscoveryRowType.LIKE, null, "AniList") shouldBe true
        policy.allows(DiscoveryRowType.SOURCE, null, "AniList") shouldBe false
    }

    @Test
    fun `inventory stays incomplete until sourceManager finishes cold init`() = runTest {
        val sdk = Any()
        var registered: Any? = null
        val extensionFrame = MutableStateFlow(
            DiscoverySourceInventory(
                complete = true,
                plugins = listOf(DiscoverySourcePlugin("ext.a", listOf(7L))),
            ),
        )
        val sourceInitialized = MutableStateFlow(false)
        val resolver = DiscoverySourcePolicyResolver(
            preferences = DiscoveryPreferences(PersistingPreferenceStore()),
            sourcePreferences = SourcePreferences(PersistingPreferenceStore()),
            inventorySourceLoader = { media ->
                if (media == DiscoveryMediaType.MANGA) {
                    bindInventoryWithSourceManager(
                        extensionInventory = { extensionFrame.value },
                        extensionChanges = extensionFrame,
                        matchesExtension = { id, source, owners, _ ->
                            id == 7L && source === sdk && "ext.a" in owners
                        },
                        sourceInitialized = sourceInitialized,
                        getSource = { id -> if (id == 7L) registered else null },
                    )
                } else {
                    val empty = MutableStateFlow(DiscoverySourceInventory(true, emptyList()))
                    DiscoverySourceInventorySource({ empty.value }, empty)
                }
            },
            scope = backgroundScope,
        )

        runCurrent()
        resolver.current(DiscoveryMediaType.MANGA).inventoryComplete shouldBe false
        resolver.isCurrentSourceBinding(DiscoveryMediaType.MANGA, 7L, sdk) shouldBe false

        val awaited = async { resolver.awaitComplete(DiscoveryMediaType.MANGA) }
        runCurrent()
        awaited.isCompleted shouldBe false

        registered = sdk
        sourceInitialized.value = true
        runCurrent()
        val policy = withTimeout(1_000L) { awaited.await() }
        policy.inventoryComplete shouldBe true
        resolver.isCurrentSourceBinding(DiscoveryMediaType.MANGA, 7L, sdk) shouldBe true
    }

    @Test
    fun `inventory stays incomplete when sourceManager reports initialized before extension sources are registered`() =
        runTest {
            val sdk = Any()
            var registered: Any? = null
            val extensionFrame = MutableStateFlow(
                DiscoverySourceInventory(
                    complete = true,
                    plugins = listOf(DiscoverySourcePlugin("ext.a", listOf(7L))),
                ),
            )
            // Simulates SourceManager flipping isInitialized=true on an initial empty extensions emission
            // before the actual extension sources are published to sourcesMapFlow/catalogueSources.
            val sourceInitialized = MutableStateFlow(true)
            val sourceChanges = MutableStateFlow<List<Any>>(emptyList())
            val resolver = DiscoverySourcePolicyResolver(
                preferences = DiscoveryPreferences(PersistingPreferenceStore()),
                sourcePreferences = SourcePreferences(PersistingPreferenceStore()),
                inventorySourceLoader = { media ->
                    if (media == DiscoveryMediaType.MANGA) {
                        bindInventoryWithSourceManager(
                            extensionInventory = { extensionFrame.value },
                            extensionChanges = extensionFrame,
                            matchesExtension = { id, source, owners, _ ->
                                id == 7L && source === sdk && "ext.a" in owners
                            },
                            sourceInitialized = sourceInitialized,
                            sourceChanges = sourceChanges,
                            getSource = { id -> if (id == 7L) registered else null },
                        )
                    } else {
                        val empty = MutableStateFlow(DiscoverySourceInventory(true, emptyList()))
                        DiscoverySourceInventorySource({ empty.value }, empty)
                    }
                },
                scope = backgroundScope,
            )

            runCurrent()
            resolver.current(DiscoveryMediaType.MANGA).inventoryComplete shouldBe false
            resolver.isCurrentSourceBinding(DiscoveryMediaType.MANGA, 7L, sdk) shouldBe false

            val awaited = async { resolver.awaitComplete(DiscoveryMediaType.MANGA) }
            runCurrent()
            awaited.isCompleted shouldBe false

            registered = sdk
            sourceChanges.value = listOf(sdk)
            runCurrent()
            val policy = withTimeout(1_000L) { awaited.await() }
            policy.inventoryComplete shouldBe true
            resolver.isCurrentSourceBinding(DiscoveryMediaType.MANGA, 7L, sdk) shouldBe true
        }

    private fun resolverFor(
        preferences: DiscoveryPreferences,
        sourcePreferences: SourcePreferences,
        inventory: MutableStateFlow<DiscoverySourceInventory>,
        scope: CoroutineScope,
        loads: MutableList<DiscoveryMediaType> = mutableListOf(),
    ) = DiscoverySourcePolicyResolver(
        preferences = preferences,
        sourcePreferences = sourcePreferences,
        inventorySourceLoader = { media ->
            loads += media
            val frame = if (media == DiscoveryMediaType.MANGA) {
                inventory
            } else {
                MutableStateFlow(DiscoverySourceInventory(complete = true, plugins = emptyList()))
            }
            DiscoverySourceInventorySource({ frame.value }, frame)
        },
        scope = scope,
        clock = { 1_000L },
    )

    private data class NativeRequest(val media: DiscoveryMediaType, val sourceId: Long, val page: Int)

    private class RecordingNativeCatalog : DiscoverySourceCatalog {
        val requests = java.util.Collections.synchronizedList(mutableListOf<NativeRequest>())

        override suspend fun popular(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryRowItem> = fetch(mediaType, sourceId, page)

        override suspend fun latest(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryRowItem> = fetch(mediaType, sourceId, page)

        override suspend fun popularWithGenres(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            genres: List<String>,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryRowItem> = fetch(mediaType, sourceId, page)

        private fun fetch(media: DiscoveryMediaType, sourceId: Long, page: Int): List<DiscoveryRowItem> {
            requests += NativeRequest(media, sourceId, page)
            return listOf(
                DiscoveryRowItem(
                    title = "A fresh excluded-source manga",
                    cleanTitle = "a fresh excluded source manga",
                    coverUrl = null,
                    reason = null,
                    seedTitle = null,
                    provider = "Excluded extension",
                    score = 1.0,
                    sourceId = sourceId,
                    sourceUrl = "/fresh-excluded-manga",
                    genres = listOf("Action"),
                ),
            )
        }
    }
}
