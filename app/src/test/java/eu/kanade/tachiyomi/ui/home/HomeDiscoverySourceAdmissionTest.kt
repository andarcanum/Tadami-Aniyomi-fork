package eu.kanade.tachiyomi.ui.home

import android.content.Context
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.discovery.DiscoverySourceInventorySource
import eu.kanade.tachiyomi.data.discovery.DiscoverySourcePolicyResolver
import eu.kanade.tachiyomi.data.discovery.FakeDiscoveryRepositoryBase
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySourceInventory
import tachiyomi.domain.discovery.model.DiscoverySourcePlugin
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import java.util.concurrent.atomic.AtomicInteger

class HomeDiscoverySourceAdmissionTest {

    @Test
    fun `excluding native source removes cached Home card without repository emission`(): Unit = runBlocking {
        val media = DiscoveryMediaType.ANIME
        val preferences = DiscoveryPreferences(PersistingPreferenceStore()).apply {
            discoveryEnabled().set(true)
            teaserCount().set(3)
            discoverySourceMode(media).set("manual")
            discoverySourceKnown(media).set("ext.native")
        }
        val card = DiscoverySuggestion(
            id = 7L,
            mediaType = media,
            rowType = DiscoveryRowType.SOURCE,
            title = "Cached native card",
            cleanTitle = "cached native card",
            coverUrl = "https://native.invalid/cover.jpg",
            reason = null,
            seedTitle = null,
            provider = "Native catalogue",
            score = 1.0,
            position = 0L,
            createdAt = 1_000L,
            sourceId = 7L,
            sourceUrl = "/cached-native-card",
            genres = listOf("Fantasy"),
        )
        val repository = CachedRepository(listOf(card))
        val inventory = MutableStateFlow(
            DiscoverySourceInventory(
                complete = true,
                plugins = listOf(DiscoverySourcePlugin("ext.native", setOf(7L, 8L))),
            ),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val sourcePolicies = DiscoverySourcePolicyResolver(
                preferences = preferences,
                sourcePreferences = SourcePreferences(PersistingPreferenceStore()),
                inventorySourceLoader = { requestedMedia ->
                    val frame = if (requestedMedia == media) {
                        inventory
                    } else {
                        MutableStateFlow(DiscoverySourceInventory(complete = true, plugins = emptyList()))
                    }
                    DiscoverySourceInventorySource({ frame.value }, frame)
                },
                scope = scope,
            )
            withTimeout(5_000L) { sourcePolicies.observe(media).first { it.inventoryComplete } }
            val uiState = MutableStateFlow(
                HomeHubUiState(
                    userName = "Test",
                    userAvatar = "",
                    greeting = tachiyomi.i18n.MR.strings.app_name,
                    greetingReady = true,
                    isLoading = false,
                    showWelcome = false,
                ),
            )
            val controller = HomeDiscoveryController(
                mediaType = media,
                repository = repository,
                preferences = preferences,
                context = mockk<Context>(relaxed = true),
                scope = scope,
                onStateUpdate = { updater -> uiState.update(updater) },
                currentState = { uiState.value },
                onEmitEffect = {},
                sourcePolicyProvider = sourcePolicies,
            )
            controller.startObserving()
            withTimeout(5_000L) {
                uiState.first { state -> state.discovery.any { it.cleanTitle == card.cleanTitle } }
            }
            uiState.value.discoveryPool.map { it.title } shouldBe listOf("Cached native card")
            repository.poolEmissions.get() shouldBe 1

            preferences.discoverySourceExcluded(media).set("ext.native")
            sourcePolicies.current(media).allows(card) shouldBe false
            // Bound the wait, then assert the actual rendered state so Red identifies the leaked card.
            withTimeoutOrNull(1_000L) {
                uiState.first { it.discoveryPool.isEmpty() && it.discovery.isEmpty() }
            }

            repository.poolEmissions.get() shouldBe 1
            uiState.value.discoveryPool.map { it.title } shouldBe emptyList()
            uiState.value.discovery.map { it.title } shouldBe emptyList()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `Undo does not restore a best effort fallback when the current row has an exact match`(): Unit = runBlocking {
        val media = DiscoveryMediaType.ANIME
        val preferences = DiscoveryPreferences(PersistingPreferenceStore()).apply {
            discoveryEnabled().set(true)
            teaserCount().set(3)
            discoverySourceMode(media).set("manual")
            discoverySourceKnown(media).set("ext.native")
            requiredGenres(media).set("fantasy")
            requiredEnforcement().set("BEST_EFFORT")
        }
        fun card(title: String, genre: String) = DiscoverySuggestion(
            id = if (title == "Drama") 1L else 2L,
            mediaType = media,
            rowType = DiscoveryRowType.TASTE,
            title = title,
            cleanTitle = title.lowercase(),
            coverUrl = "https://native.invalid/$title.jpg",
            reason = null, seedTitle = null, provider = "Native catalogue", score = 1.0,
            position = 0L,
            createdAt = 1_000L,
            sourceId = 7L,
            sourceUrl = "/$title",
            genres = listOf(genre),
        )
        val drama = card("Drama", "Drama")
        val fantasy = card("Fantasy", "Fantasy")
        val repository = CachedRepository(listOf(drama))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val inventory = MutableStateFlow(
                DiscoverySourceInventory(
                    true,
                    listOf(DiscoverySourcePlugin("ext.native", setOf(7L))),
                ),
            )
            val sourcePolicies = DiscoverySourcePolicyResolver(
                preferences = preferences,
                sourcePreferences = SourcePreferences(PersistingPreferenceStore()),
                inventorySourceLoader = {
                    DiscoverySourceInventorySource({
                        inventory.value
                    }, inventory)
                },
                scope = scope,
            )
            val uiState = MutableStateFlow(
                HomeHubUiState(
                    userName = "Test",
                    userAvatar = "",
                    greeting = tachiyomi.i18n.MR.strings.app_name,
                    greetingReady = true,
                    isLoading = false,
                    showWelcome = false,
                ),
            )
            val updates = MutableStateFlow(0)
            val controller = HomeDiscoveryController(
                mediaType = media, repository = repository, preferences = preferences,
                context = mockk<Context>(relaxed = true), scope = scope,
                onStateUpdate = { updater ->
                    uiState.update(updater)
                    updates.update { it + 1 }
                },
                currentState = { uiState.value },
                onEmitEffect = {},
                sourcePolicyProvider = sourcePolicies,
            )
            controller.startObserving()
            withTimeout(5_000L) { uiState.first { state -> state.discovery.any { it.cleanTitle == "drama" } } }
            repository.hide(media, drama.cleanTitle)
            controller.onItemDismissedFromPool(drama.cleanTitle)
            repository.updatePool(listOf(drama, fantasy))
            withTimeout(5_000L) {
                uiState.first {
                    it.discoveryPool.map { card ->
                        card.cleanTitle
                    } == listOf("fantasy")
                }
            }
            val beforeUndo = updates.value
            repository.undoGate = kotlinx.coroutines.CompletableDeferred()
            controller.restoreHiddenTeaserItem(drama.toHomeHubDiscoveryItem())
            withTimeout(5_000L) {
                repository.undoEntered.await()
                updates.first { it > beforeUndo }
            }
            repository.undoGate!!.complete(Unit)
            // Under BEST_EFFORT with fewer than 60 exact matches, Drama tops up behind Fantasy.
            withTimeout(5_000L) {
                uiState.first { state -> state.discoveryPool.map { it.cleanTitle } == listOf("fantasy", "drama") }
            }
            uiState.value.discoveryPool.map { it.cleanTitle } shouldBe listOf("fantasy", "drama")

            // When 60 exact matches already fill the row, Drama fallback stays excluded.
            val fullExact = (1..60).map { i -> card("Fantasy$i", "Fantasy").copy(id = (i + 10).toLong()) }
            repository.updatePool(listOf(drama) + fullExact)
            withTimeout(5_000L) {
                uiState.first { state ->
                    state.discoveryPool.size == 60 && state.discoveryPool.none { it.cleanTitle == "drama" }
                }
            }
            uiState.value.discoveryPool.none { it.cleanTitle == "drama" } shouldBe true
        } finally {
            scope.cancel()
        }
    }

    private class CachedRepository(items: List<DiscoverySuggestion>) : FakeDiscoveryRepositoryBase() {
        private val pool = MutableStateFlow(items)
        val poolEmissions = AtomicInteger()
        private val hidden = MutableStateFlow(emptySet<String>())
        val undoEntered = kotlinx.coroutines.CompletableDeferred<Unit>()
        var undoGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

        fun updatePool(items: List<DiscoverySuggestion>) {
            pool.value = items
        }
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

        override fun subscribe(mediaType: DiscoveryMediaType): Flow<List<DiscoverySuggestion>> =
            pool.onEach { poolEmissions.incrementAndGet() }
    }

    @Test
    fun `initial incomplete inventory rebalances Home teaser once native inventory completes`(): Unit = runBlocking {
        val media = DiscoveryMediaType.ANIME
        val preferences = DiscoveryPreferences(PersistingPreferenceStore()).apply {
            discoveryEnabled().set(true)
            teaserCount().set(2)
            discoverySourceMode(media).set("manual")
            discoverySourceKnown(media).set("ext.native")
        }
        val external = DiscoverySuggestion(
            id = 1L,
            mediaType = media,
            rowType = DiscoveryRowType.TREND,
            title = "External card",
            cleanTitle = "external card",
            coverUrl = "https://ext.invalid/1.jpg",
            reason = null,
            seedTitle = null,
            provider = "AniList",
            score = 0.5,
            position = 0L,
            createdAt = 1_000L,
            sourceId = null,
            sourceUrl = null,
            genres = listOf("Drama"),
        )
        val external2 = external.copy(id = 2L, title = "External two", cleanTitle = "external two", position = 1L)
        val native = DiscoverySuggestion(
            id = 3L,
            mediaType = media,
            rowType = DiscoveryRowType.SOURCE,
            title = "Native card",
            cleanTitle = "native card",
            coverUrl = "https://nat.invalid/1.jpg",
            reason = null,
            seedTitle = null,
            provider = "Native",
            score = 1.0,
            position = 0L,
            createdAt = 1_000L,
            sourceId = 7L,
            sourceUrl = "/native",
            genres = listOf("Action"),
        )
        val repository = CachedRepository(listOf(native, external, external2))
        val inventory = MutableStateFlow(DiscoverySourceInventory.Unavailable)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val sourcePolicies = DiscoverySourcePolicyResolver(
                preferences = preferences,
                sourcePreferences = SourcePreferences(PersistingPreferenceStore()),
                inventorySourceLoader = { requestedMedia ->
                    val frame = if (requestedMedia == media) {
                        inventory
                    } else {
                        MutableStateFlow(DiscoverySourceInventory(complete = true, plugins = emptyList()))
                    }
                    DiscoverySourceInventorySource({ frame.value }, frame)
                },
                scope = scope,
            )
            val uiState = kotlinx.coroutines.flow.MutableStateFlow(
                HomeHubUiState(
                    userName = "Test",
                    userAvatar = "",
                    greeting = tachiyomi.i18n.MR.strings.app_name,
                    greetingReady = true,
                    isLoading = false,
                    showWelcome = false,
                ),
            )
            val controller = HomeDiscoveryController(
                mediaType = media,
                repository = repository,
                preferences = preferences,
                context = mockk<Context>(relaxed = true),
                scope = scope,
                onStateUpdate = { updater -> uiState.update(updater) },
                currentState = { uiState.value },
                onEmitEffect = {},
                sourcePolicyProvider = sourcePolicies,
            )
            controller.startObserving()
            withTimeout(5_000L) {
                uiState.first { state ->
                    state.discovery.map { it.cleanTitle }.toSet() == setOf("external card", "external two")
                }
            }
            inventory.value = DiscoverySourceInventory(
                complete = true,
                plugins = listOf(DiscoverySourcePlugin("ext.native", setOf(7L))),
            )
            withTimeout(5_000L) {
                uiState.first { state -> state.discovery.any { it.cleanTitle == "native card" } }
            }
        } finally {
            scope.cancel()
        }
    }
}
