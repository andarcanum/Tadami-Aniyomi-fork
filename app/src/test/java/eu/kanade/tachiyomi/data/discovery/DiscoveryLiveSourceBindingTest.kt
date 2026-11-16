package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.manga.MangaSourceUpdateLock
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoverySourcePolicy
import tachiyomi.domain.discovery.service.DiscoverySourcePolicyProvider
import tachiyomi.domain.source.manga.service.MangaSourceManager
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class DiscoveryLiveSourceBindingTest {
    @Test
    fun `queued Discovery details recheck exclusion after acquiring the real shared manga URL lock`() = runTest {
        val fixture = fixture()
        val locked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val holder = async {
            MangaSourceUpdateLock.withLock(7L, "/entry") {
                locked.complete(Unit)
                release.await()
            }
        }
        locked.await()
        val details = async { details(fixture) }
        runCurrent()
        fixture.callsA.get() shouldBe 0
        fixture.policies.value = policy("a", selected = false)
        release.complete(Unit)
        holder.await()
        runCatching { details.await() }
        fixture.callsA.get() shouldBe 0
    }

    @Test
    fun `raw owner replacement does not authorize the obsolete registered object with the same numeric id`() = runTest {
        val fixture = fixture()
        fixture.raw.value = mapOf("b" to fixture.sourceB)
        fixture.policies.value = policy("b")
        runCatching { details(fixture) }
        fixture.callsA.get() shouldBe 0
        fixture.registry.set(fixture.sourceB)
        details(fixture) shouldBe listOf("Drama")
        fixture.callsB.get() shouldBe 1
    }

    @Test
    fun `captured source is rejected when raw binding changes during registry lookup`() = runTest {
        val fixture = fixture()
        every { fixture.manager.getOrStub(7L) } answers {
            fixture.raw.value = mapOf("b" to fixture.sourceB)
            fixture.policies.value = policy("b")
            fixture.registry.set(fixture.sourceB)
            fixture.sourceA
        }
        runCatching { details(fixture) }
        fixture.callsA.get() shouldBe 0
        fixture.callsB.get() shouldBe 0
    }

    @Test
    fun `matching selected raw and registered source still returns combined API details`() = runTest {
        val fixture = fixture()
        details(fixture) shouldBe listOf("Action")
        fixture.callsA.get() shouldBe 1
    }

    @Test
    fun `same-owner replacement during SDK lookup cancels stale hydration`() =
        runTest {
            val fixture = fixture()
            every { fixture.manager.getOrStub(7L) } answers {
                fixture.raw.value = mapOf("a" to fixture.sourceB)
                fixture.policies.value = DiscoverySourcePolicy(
                    DiscoveryMediaType.MANGA,
                    "a-v2",
                    true,
                    setOf("a"),
                    mapOf("a" to setOf(7L)),
                    listOf(7L),
                )
                fixture.registry.set(fixture.sourceB)
                fixture.sourceB
            }
            runCatching { details(fixture) }
            fixture.callsB.get() shouldBe 0
        }

    @Test
    fun `same revision and owner with a replaced SDK instance cannot reuse old hydrated metadata`() = runTest {
        val fixture = fixture()
        details(fixture) shouldBe listOf("Action")
        fixture.raw.value = mapOf("a" to fixture.sourceB)
        fixture.registry.set(fixture.sourceB)
        details(fixture) shouldBe listOf("Drama")
        fixture.callsB.get() shouldBe 1
    }

    @Test
    fun `queryable source inventory excludes a registry object that no longer belongs to the selected raw owner`() =
        runTest {
            val fixture = fixture()
            fixture.raw.value = mapOf("b" to fixture.sourceB)
            fixture.policies.value = policy("b")
            fixture.catalog.queryableSourceIds(DiscoveryMediaType.MANGA, listOf(7L)) shouldBe emptyList()
        }

    private suspend fun details(fixture: Fixture): List<String> = fixture.catalog.hydrateGenres(
        DiscoveryMediaType.MANGA,
        7L,
        listOf(DiscoveryRowItem("Entry", "entry", null, null, null, "Native", 1.0, 7L, "/entry")),
        DiscoveryVerificationBudget(maxDetails = 1),
        maxDetails = 1,
    ).items.single().genres

    @Test
    fun `hydrated metadata does not survive a different raw SDK owner reusing the same source id`() = runTest {
        val fixture = fixture()
        details(fixture) shouldBe listOf("Action")
        fixture.raw.value = mapOf("b" to fixture.sourceB)
        fixture.policies.value = policy("b")
        fixture.registry.set(fixture.sourceB)
        details(fixture) shouldBe listOf("Drama")
        fixture.callsB.get() shouldBe 1
    }

    private fun fixture(): Fixture {
        val a = AtomicInteger()
        val b = AtomicInteger()
        fun source(calls: AtomicInteger, genre: String) = mockk<CatalogueSource>().also { source ->
            every { source.id } returns 7L
            coEvery { source.getMangaUpdate(any(), any(), true, false) } answers {
                calls.incrementAndGet()
                SMangaUpdate(
                    SManga.create().apply {
                        title = "Entry"
                        url = "/entry"
                        this.genre = genre
                    },
                    emptyList(),
                )
            }
        }
        val sourceA = source(a, "Action")
        val sourceB = source(b, "Drama")
        val policies = MutableStateFlow(policy("a"))
        val raw = MutableStateFlow(mapOf("a" to sourceA))
        val registry = AtomicReference(sourceA)
        val manager = mockk<MangaSourceManager>()
        every { manager.getOrStub(7L) } answers { registry.get() }
        val provider = object : DiscoverySourcePolicyProvider {
            override fun current(mediaType: DiscoveryMediaType) = policies.value
            override fun observe(mediaType: DiscoveryMediaType) = policies
            override suspend fun awaitComplete(mediaType: DiscoveryMediaType) = policies.value
        }
        val catalog = AppDiscoverySourceCatalog({ manager }, { provider }) { media, id, sdk ->
            val allowed = provider.current(media)
            allowed.allowsNative(id) && raw.value.any { (owner, source) ->
                owner in allowed.allowedPluginKeys && source === sdk
            }
        }
        return Fixture(catalog, manager, sourceA, sourceB, policies, raw, registry, a, b)
    }
    private data class Fixture(
        val catalog: AppDiscoverySourceCatalog,
        val manager: MangaSourceManager,
        val sourceA: CatalogueSource,
        val sourceB: CatalogueSource,
        val policies: MutableStateFlow<DiscoverySourcePolicy>,
        val raw: MutableStateFlow<Map<String, CatalogueSource>>,
        val registry: AtomicReference<CatalogueSource>,
        val callsA: AtomicInteger,
        val callsB: AtomicInteger,
    )
    private fun policy(owner: String, selected: Boolean = true) = DiscoverySourcePolicy(
        DiscoveryMediaType.MANGA,
        owner,
        true,
        if (selected) setOf(owner) else emptySet(),
        mapOf(owner to setOf(7L)),
        listOf(7L),
    )
}
