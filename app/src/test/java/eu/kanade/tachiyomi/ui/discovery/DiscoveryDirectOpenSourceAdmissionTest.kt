package eu.kanade.tachiyomi.ui.discovery

import dev.mihon.injekt.patchInjekt
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.discovery.DiscoverySourceInventorySource
import eu.kanade.tachiyomi.data.discovery.DiscoverySourcePolicyResolver
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySourceInventory
import tachiyomi.domain.discovery.model.DiscoverySourcePlugin
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektScope
import uy.kohesive.injekt.api.addSingleton
import java.util.concurrent.CopyOnWriteArrayList

class DiscoveryDirectOpenSourceAdmissionTest {
    private lateinit var previousInjekt: InjektScope
    private val scopes = mutableListOf<CoroutineScope>()

    @Before
    fun isolateInjekt() {
        previousInjekt = Injekt
        patchInjekt()
    }

    @After
    fun closeFixtures() {
        scopes.forEach { it.cancel() }
        Injekt = previousInjekt
    }

    @Test
    fun `an excluded bound source cannot create a local entry or even resolve its installed source`(): Unit =
        runBlocking {
            val fixture = fixture()
            fixture.preferences.discoverySourceExcluded(DiscoveryMediaType.MANGA).set("ext.native")
            fixture.resolver.resolveEntryId(card()) shouldBe null
            fixture.lookups.toList() shouldBe emptyList()
            fixture.inserted.toList() shouldBe emptyList()
        }

    @Test
    fun `source exclusion during installed-source lookup is rechecked before materialization`(): Unit = runBlocking {
        val fixture = fixture(excludeDuringLookup = true)
        fixture.resolver.resolveEntryId(card()) shouldBe null
        fixture.inserted.toList() shouldBe emptyList()
    }

    @Test
    fun `selected direct open retains exact source identity without favoriting it`(): Unit = runBlocking {
        val fixture = fixture()
        fixture.resolver.resolveEntryId(card()) shouldBe 101L
        fixture.inserted.map { it.source to it.url } shouldBe listOf(7L to "/selected")
        fixture.inserted.single().favorite shouldBe false
    }

    @Test
    fun `direct open materialization cancellation still propagates`(): Unit = runBlocking {
        val fixture = fixture()
        coEvery { fixture.repository.insertManga(any()) } throws CancellationException("fixture cancellation")
        shouldThrow<CancellationException> { fixture.resolver.resolveEntryId(card()) }
    }

    private fun fixture(excludeDuringLookup: Boolean = false): Fixture {
        val media = DiscoveryMediaType.MANGA
        val preferences = DiscoveryPreferences(PersistingPreferenceStore()).apply {
            discoverySourceMode(media).set("manual")
            discoverySourceKnown(media).set("ext.native")
        }
        val source = mockk<CatalogueSource>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also { scopes += it }
        val inventory = MutableStateFlow(
            DiscoverySourceInventory(
                true,
                listOf(DiscoverySourcePlugin("ext.native", setOf(7L))),
            ),
        )
        val policies = DiscoverySourcePolicyResolver(
            preferences = preferences,
            sourcePreferences = SourcePreferences(PersistingPreferenceStore()),
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
                DiscoverySourceInventorySource(
                    { frame.value },
                    frame,
                ) { id, sdk, owners, expected ->
                    id == 7L &&
                        sdk === source &&
                        "ext.native" in owners &&
                        expected.plugins == inventory.value.plugins
                }
            },
            scope = scope,
        )
        val lookups = CopyOnWriteArrayList<Long>()
        val manager = mockk<MangaSourceManager>()
        every { manager.get(7L) } answers {
            lookups += 7L
            if (excludeDuringLookup) preferences.discoverySourceExcluded(media).set("ext.native")
            source
        }
        Injekt.addSingleton<MangaSourceManager>(manager)
        val inserted = CopyOnWriteArrayList<Manga>()
        val repository = mockk<MangaRepository>()
        coEvery { repository.getMangaByUrlAndSourceId(any(), any()) } returns null
        coEvery { repository.insertManga(any()) } answers {
            inserted += firstArg<Manga>()
            101L
        }
        val resolver = DiscoveryDirectOpenResolver(
            networkToLocalManga = NetworkToLocalManga(repository),
            networkToLocalAnime = mockk(),
            networkToLocalNovel = mockk(),
            sourcePolicyProvider = policies,
        )
        return Fixture(preferences, resolver, repository, inserted, lookups)
    }

    private data class Fixture(
        val preferences: DiscoveryPreferences,
        val resolver: DiscoveryDirectOpenResolver,
        val repository: MangaRepository,
        val inserted: CopyOnWriteArrayList<Manga>,
        val lookups: CopyOnWriteArrayList<Long>,
    )

    private fun card() = DiscoverySuggestion(
        id = 1L, mediaType = DiscoveryMediaType.MANGA, rowType = DiscoveryRowType.SOURCE,
        title = "Selected",
        cleanTitle = "selected",
        coverUrl = null,
        reason = null,
        seedTitle = null,
        provider = "Duplicate provider name", score = 1.0, position = 0L, createdAt = 1L,
        sourceId = 7L, sourceUrl = "/selected", genres = listOf("Fantasy"),
    )
}
