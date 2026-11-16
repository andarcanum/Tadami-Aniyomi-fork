package eu.kanade.tachiyomi.ui.discovery

import android.content.Context
import androidx.work.WorkManager
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.model.ScreenModelStore
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.discovery.DiscoveryLibraryAdder
import eu.kanade.tachiyomi.data.discovery.DiscoverySourceInventorySource
import eu.kanade.tachiyomi.data.discovery.DiscoverySourcePolicyResolver
import eu.kanade.tachiyomi.data.discovery.DiscoveryTrendingSource
import eu.kanade.tachiyomi.data.discovery.FakeDiscoveryRepositoryBase
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import eu.kanade.tachiyomi.util.system.isRunningOrEnqueuedFlow
import eu.kanade.tachiyomi.util.system.workManager
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySourceInventory
import tachiyomi.domain.discovery.model.DiscoverySourcePlugin
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class, InternalVoyagerApi::class)
class DiscoveryFeedSourceAdmissionTest {
    private val fixtures = mutableListOf<Fixture>()

    @Before
    fun setupAndroidBoundary() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkStatic("eu.kanade.tachiyomi.util.system.WorkManagerExtensionsKt")
    }

    @After
    fun closeFixtures() {
        fixtures.forEach {
            ScreenModelStore.onDisposeNavigator(it.holderKey)
            it.scope.cancel()
        }
        unmockkStatic("eu.kanade.tachiyomi.util.system.WorkManagerExtensionsKt")
        Dispatchers.resetMain()
    }

    @Test
    fun `source exclusion removes cached Feed and window without pool emission`(): Unit =
        runBlocking {
            val card = card("Cached", "Fantasy")
            val fixture = fixture(listOf(card))
            fixture.model.start()
            withTimeout(5_000L) {
                fixture.model.state.first {
                    it.mix.any { candidate ->
                        candidate.cleanTitle == card.cleanTitle
                    }
                }
            }
            fixture.repository.poolEmissions.get() shouldBe 1
            fixture.preferences.discoverySourceExcluded(DiscoveryMediaType.ANIME).set("ext.native")
            fixture.policies.current(DiscoveryMediaType.ANIME).allows(card) shouldBe false
            withTimeoutOrNull(1_000L) {
                fixture.model.state.first {
                    it.mix.isEmpty() && it.activeWindows.values.all { window ->
                        window.isEmpty()
                    }
                }
            }
            fixture.repository.poolEmissions.get() shouldBe 1
            fixture.model.state.value.mix shouldBe emptyList()
            fixture.model.state.value.rows.values.flatten() shouldBe emptyList()
            fixture.model.displayedItems(FeedSignalTab.MIX, null) shouldBe emptyList()
        }

    @Test
    fun `Feed add forwards the full source-bound card instead of the legacy provider name`(): Unit = runBlocking {
        val card = card("Bound add", "Fantasy")
        val fixture = fixture(listOf(card))
        val forwarded = CopyOnWriteArrayList<DiscoverySuggestion>()
        coEvery { fixture.adder.add(any()) } answers {
            forwarded += firstArg<DiscoverySuggestion>()
            true
        }
        coEvery { fixture.adder.addFromProvider(any(), any(), any()) } returns false
        fixture.model.addToLibrary(card)
        withTimeout(5_000L) {
            fixture.model.state.first { it.addedSnackbarTitle != null || it.searchFallbackItem != null }
        }
        forwarded.toList() shouldBe listOf(card)
        fixture.model.state.value.addedSnackbarTitle shouldBe card.title
        fixture.model.state.value.searchFallbackItem shouldBe null
    }

    @Test
    fun `Feed Undo does not put a fallback in the current exact-match row window`(): Unit = runBlocking {
        val drama = card("Drama", "Drama")
        val fantasy = card("Fantasy", "Fantasy")
        val fixture = fixture(listOf(drama), bestEffort = true)
        fixture.model.start()
        withTimeout(5_000L) {
            fixture.model.state.first {
                it.mix.any { candidate ->
                    candidate.cleanTitle == "drama"
                }
            }
        }
        fixture.model.hide(drama)
        withTimeout(5_000L) { fixture.repository.hidden.first { "drama" in it } }
        fixture.repository.pool.value = listOf(drama, fantasy)
        withTimeout(5_000L) {
            fixture.model.state.first {
                it.mix.map { candidate ->
                    candidate.cleanTitle
                } == listOf("fantasy")
            }
        }
        fixture.repository.undoGate = CompletableDeferred()
        fixture.model.undoHide()
        withTimeout(5_000L) {
            fixture.repository.undoEntered.await()
            fixture.model.state.first {
                it.hidden.isEmpty() && it.mix.map { candidate ->
                    candidate.cleanTitle
                } == listOf("fantasy")
            }
        }
        fixture.repository.undoGate!!.complete(Unit)
        withTimeoutOrNull(500L) {
            fixture.model.state.first {
                it.activeWindows[FeedSignalTab.MIX].orEmpty().any { candidate ->
                    candidate.cleanTitle == "drama"
                }
            }
        }
        fixture.model.state.value.mix.map { it.cleanTitle } shouldBe listOf("fantasy")
        fixture.model.displayedItems(FeedSignalTab.MIX, null).map { it.cleanTitle } shouldBe listOf("fantasy")
    }

    private fun fixture(items: List<DiscoverySuggestion>, bestEffort: Boolean = false): Fixture {
        val media = DiscoveryMediaType.ANIME
        val preferences = DiscoveryPreferences(PersistingPreferenceStore()).apply {
            discoveryEnabled().set(true)
            discoverySourceMode(media).set("manual")
            discoverySourceKnown(media).set("ext.native")
            if (bestEffort) {
                requiredGenres(media).set("fantasy")
                requiredEnforcement().set("BEST_EFFORT")
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val inventory = MutableStateFlow(
            DiscoverySourceInventory(
                true,
                listOf(DiscoverySourcePlugin("ext.native", setOf(7L, 8L))),
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
                DiscoverySourceInventorySource({ frame.value }, frame)
            },
            scope = scope,
        )
        val context = mockk<Context>(relaxed = true)
        val workManager = mockk<WorkManager>(relaxed = true)
        every { context.workManager } returns workManager
        every { workManager.isRunningOrEnqueuedFlow(any()) } returns flowOf(false)
        val repository = CachedRepository(items)
        val adder = mockk<DiscoveryLibraryAdder>()
        val trending = mockk<DiscoveryTrendingSource>()
        coEvery { trending.fetchMeta(any(), any()) } returns null
        val holderKey = "feed-test-" + java.util.UUID.randomUUID()
        val model = ScreenModelStore.getOrPut<DiscoveryFeedScreenModel>(holderKey, null) {
            DiscoveryFeedScreenModel(
                initialMedia = media,
                context = context,
                repository = repository,
                adder = adder,
                preferences = preferences, trendingSource = trending,
                networkToLocalManga = mockk(),
                networkToLocalAnime = mockk(),
                networkToLocalNovel = mockk(),
                sourcePolicyProvider = policies,
            )
        }
        return Fixture(preferences, repository, policies, adder, model, scope, holderKey).also { fixtures += it }
    }

    private data class Fixture(
        val preferences: DiscoveryPreferences,
        val repository: CachedRepository,
        val policies: DiscoverySourcePolicyResolver,
        val adder: DiscoveryLibraryAdder,
        val model: DiscoveryFeedScreenModel,
        val scope: CoroutineScope,
        val holderKey: String,
    )

    private class CachedRepository(items: List<DiscoverySuggestion>) : FakeDiscoveryRepositoryBase() {
        val pool = MutableStateFlow(items)
        val hidden = MutableStateFlow(emptySet<String>())
        val poolEmissions = AtomicInteger()
        val undoEntered = CompletableDeferred<Unit>()
        var undoGate: CompletableDeferred<Unit>? = null
        override fun subscribe(
            mediaType: DiscoveryMediaType,
        ): Flow<List<DiscoverySuggestion>> = pool.onEach { poolEmissions.incrementAndGet() }
        override fun subscribeHidden(mediaType: DiscoveryMediaType): Flow<Set<String>> = hidden
        override suspend fun getHiddenTitles(mediaType: DiscoveryMediaType) = hidden.value
        override suspend fun hide(mediaType: DiscoveryMediaType, cleanTitle: String) {
            hidden.value += cleanTitle
        }
        override suspend fun undoHide(mediaType: DiscoveryMediaType, cleanTitle: String) {
            hidden.value -= cleanTitle
            undoEntered.complete(Unit)
            undoGate?.await()
        }
    }

    @Test
    fun `undoHide restores sole hidden item even when tab window became empty`(): Unit = runBlocking {
        val other = card("Other Like", "Fantasy").copy(rowType = DiscoveryRowType.LIKE)
        val sole = card("Only Taste", "Fantasy").copy(rowType = DiscoveryRowType.TASTE)
        val fixture = fixture(listOf(other, sole))
        fixture.model.start()
        withTimeout(5_000L) {
            fixture.model.state.first {
                it.mix.size == 2 &&
                    it.rows[DiscoveryRowType.TASTE]?.map { c -> c.cleanTitle } == listOf(sole.cleanTitle)
            }
        }
        fixture.model.onTabSelected(FeedSignalTab.TASTE)
        fixture.model.hide(sole, FeedSignalTab.TASTE)
        withTimeout(5_000L) {
            fixture.model.state.first {
                it.activeWindows[FeedSignalTab.TASTE]?.isEmpty() == true
            }
        }
        fixture.model.undoHide()
        withTimeout(5_000L) {
            fixture.model.state.first {
                it.activeWindows[FeedSignalTab.TASTE]?.map { c -> c.cleanTitle } == listOf(sole.cleanTitle)
            }
        }
    }

    private fun card(title: String, genre: String) = DiscoverySuggestion(
        id = title.hashCode().toLong(),
        mediaType = DiscoveryMediaType.ANIME,
        rowType = DiscoveryRowType.TASTE,
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = "https://native.invalid/$title.jpg",
        reason = null,
        seedTitle = null,
        provider = "Duplicate provider name",
        score = 1.0,
        position = 0L,
        createdAt = 1_000L,
        sourceId = 7L,
        sourceUrl = "/$title",
        genres = listOf(genre),
    )
}
