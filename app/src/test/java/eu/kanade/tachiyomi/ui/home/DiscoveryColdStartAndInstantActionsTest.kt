package eu.kanade.tachiyomi.ui.home

import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.tachiyomi.data.discovery.FakeDiscoveryRepositoryBase
import eu.kanade.tachiyomi.data.discovery.discoveryQueryPolicyFixture
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryExternalProvider
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryPublicationSnapshot
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySignalType
import tachiyomi.domain.discovery.model.DiscoverySourcePolicy
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.model.DiscoveryTasteMetadata
import tachiyomi.domain.discovery.model.DiscoveryTitleIdentity
import tachiyomi.domain.discovery.model.DiscoveryTraversalState
import tachiyomi.domain.discovery.service.DiscoverySourcePolicyProvider

class DiscoveryColdStartAndInstantActionsTest {

    private fun suggestion(
        title: String,
        sourceId: Long? = 42L,
        provider: String = "MangaLib",
        rowType: DiscoveryRowType = DiscoveryRowType.SOURCE,
    ) = DiscoverySuggestion(
        id = title.hashCode().toLong(),
        mediaType = DiscoveryMediaType.MANGA,
        rowType = rowType,
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = "https://example.com/$title.jpg",
        reason = "Fantasy",
        seedTitle = null,
        provider = provider,
        score = 1.0,
        position = 0L,
        createdAt = 1_000L,
        sourceId = sourceId,
        sourceUrl = "/manga/${title.lowercase()}",
        genres = listOf("Fantasy"),
    )

    private class SlowWriteCommittedRepo(
        private val generation: Long = 1L,
    ) : FakeDiscoveryRepositoryBase() {
        val poolFlow = MutableStateFlow<List<DiscoverySuggestion>>(emptyList())
        val hiddenFlow = MutableStateFlow<Set<String>>(emptySet())
        val consumedFlow = MutableStateFlow<Set<String>>(emptySet())
        val writeGate = CompletableDeferred<Unit>()
        val hiddenCalls = mutableListOf<String>()
        val consumedCalls = mutableListOf<String>()

        override fun subscribe(mediaType: DiscoveryMediaType): Flow<List<DiscoverySuggestion>> = poolFlow
        override fun subscribeHidden(mediaType: DiscoveryMediaType): Flow<Set<String>> = hiddenFlow
        override fun subscribeConsumed(mediaType: DiscoveryMediaType): Flow<Set<String>> = consumedFlow
        override suspend fun getHiddenTitles(mediaType: DiscoveryMediaType): Set<String> = hiddenFlow.value

        override fun observePublication(media: DiscoveryMediaType): Flow<DiscoveryPublicationSnapshot> =
            super.observePublication(media).map { snapshot ->
                snapshot.copy(generation = generation, committedAt = 10_000L)
            }

        override suspend fun hide(mediaType: DiscoveryMediaType, cleanTitle: String) {
            writeGate.await()
            hiddenCalls += cleanTitle
            hiddenFlow.value += cleanTitle
        }

        override suspend fun recordTaste(
            identity: DiscoveryTitleIdentity,
            kind: DiscoverySignalType,
            metadata: DiscoveryTasteMetadata,
        ): tachiyomi.domain.discovery.model.DiscoveryPositiveReceipt {
            writeGate.await()
            if (kind == DiscoverySignalType.CONSUMED) {
                consumedCalls += identity.primaryCleanTitle
                consumedFlow.value += identity.primaryCleanTitle
            }
            return tachiyomi.domain.discovery.model.DiscoveryPositiveReceipt(
                identityKey = identity.identityKey,
                effectiveKind = kind,
                positiveCreatedAt = 10_000L,
            )
        }
    }

    private fun waitUntil(timeoutMs: Long = 2_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L)
        }
    }

    @Test
    fun `committed SQLite publication shows cached native cards immediately before inventoryComplete`(): Unit =
        runBlocking {
            val card1 = suggestion("Alpha")
            val card2 = suggestion("Beta")
            val repo = SlowWriteCommittedRepo(generation = 3L).apply {
                poolFlow.value = listOf(card1, card2)
            }
            val prefs = DiscoveryPreferences(PersistingPreferenceStore()).apply {
                discoveryEnabled().set(true)
                lastGenerationAt(DiscoveryMediaType.MANGA).set(10_000L)
            }
            val incompletePolicy = DiscoverySourcePolicy(
                mediaType = DiscoveryMediaType.MANGA,
                revision = "cold",
                inventoryComplete = false,
                allowedPluginKeys = emptySet(),
                pluginMembers = emptyMap(),
            )
            val completePolicy = discoveryQueryPolicyFixture(DiscoveryMediaType.MANGA, listOf(42L))
            val policyFlow = MutableStateFlow(incompletePolicy)
            val policyProvider = object : DiscoverySourcePolicyProvider {
                override fun current(mediaType: DiscoveryMediaType) = policyFlow.value
                override fun observe(mediaType: DiscoveryMediaType) = policyFlow
                override suspend fun awaitComplete(mediaType: DiscoveryMediaType) = completePolicy
            }
            var state = HomeHubUiState(
                userName = "User",
                userAvatar = "",
                greeting = tachiyomi.i18n.MR.strings.app_name,
                greetingReady = true,
                isLoading = false,
                showWelcome = false,
            )
            val controller = HomeDiscoveryController(
                sourcePolicyProvider = policyProvider,
                mediaType = DiscoveryMediaType.MANGA,
                repository = repo,
                preferences = prefs,
                context = mockk(relaxed = true),
                scope = CoroutineScope(Dispatchers.Unconfined),
                onStateUpdate = { updater -> state = updater(state) },
                currentState = { state },
                onEmitEffect = {},
            )

            controller.startObserving()
            waitUntil { state.discovery.size == 2 }

            state.discovery.map { it.title } shouldBe listOf("Alpha", "Beta")
            state.isDiscoveryLoading shouldBe false

            // Once inventory finishes loading, the visible cards stay intact without flashing empty/skeleton.
            policyFlow.value = completePolicy
            waitUntil { !state.isDiscoveryLoading && state.discovery.size == 2 }
            state.discovery.map { it.title } shouldBe listOf("Alpha", "Beta")
            state.isDiscoveryLoading shouldBe false
        }

    @Test
    fun `hideDiscoveryItem and markDiscoveryItemConsumed remove cards synchronously before DB completes`(): Unit =
        runBlocking {
            val card1 = suggestion("Alpha")
            val card2 = suggestion("Beta")
            val card3 = suggestion("Gamma")
            val repo = SlowWriteCommittedRepo(generation = 2L).apply {
                poolFlow.value = listOf(card1, card2, card3)
            }
            val prefs = DiscoveryPreferences(PersistingPreferenceStore()).apply {
                discoveryEnabled().set(true)
                lastGenerationAt(DiscoveryMediaType.MANGA).set(10_000L)
            }
            val completePolicy = discoveryQueryPolicyFixture(DiscoveryMediaType.MANGA, listOf(42L))
            val policyProvider = object : DiscoverySourcePolicyProvider {
                override fun current(mediaType: DiscoveryMediaType) = completePolicy
                override fun observe(mediaType: DiscoveryMediaType) = MutableStateFlow(completePolicy)
                override suspend fun awaitComplete(mediaType: DiscoveryMediaType) = completePolicy
            }
            var state = HomeHubUiState(
                userName = "User",
                userAvatar = "",
                greeting = tachiyomi.i18n.MR.strings.app_name,
                greetingReady = true,
                isLoading = false,
                showWelcome = false,
            )
            val controller = HomeDiscoveryController(
                sourcePolicyProvider = policyProvider,
                mediaType = DiscoveryMediaType.MANGA,
                repository = repo,
                preferences = prefs,
                context = mockk(relaxed = true),
                scope = CoroutineScope(Dispatchers.Unconfined),
                onStateUpdate = { updater -> state = updater(state) },
                currentState = { state },
                onEmitEffect = {},
            )

            controller.startObserving()
            waitUntil { state.discovery.size == 3 }

            // DB writeGate is still locked: UI removal MUST happen synchronously on the very next line.
            controller.hideDiscoveryItem(card1.toHomeHubDiscoveryItem())
            state.discovery.map { it.title } shouldBe listOf("Beta", "Gamma")

            controller.markDiscoveryItemConsumed(card2.toHomeHubDiscoveryItem())
            state.discovery.map { it.title } shouldBe listOf("Gamma")

            // Racing emission from repo while DB write is still in flight must NOT resurrect dismissed cards.
            repo.poolFlow.value = listOf(card1, card2, card3)
            state.discovery.map { it.title } shouldBe listOf("Gamma")

            // Undo restores the hidden item cleanly.
            controller.restoreHiddenTeaserItem(card1.toHomeHubDiscoveryItem())
            waitUntil { state.discovery.any { it.title == "Alpha" } }
            state.discovery.map { it.title }.contains("Alpha") shouldBe true

            repo.writeGate.complete(Unit)
        }

    @Test
    fun `selectColdStartVisibleCandidates enforces external provider policy during provisional cold start`() {
        val nativeCard = suggestion("Native", sourceId = 42L, provider = "MangaLib")
        val externalCard =
            suggestion("External", sourceId = null, provider = "anilist_trend", rowType = DiscoveryRowType.TREND)
        val snapshot = DiscoveryPublicationSnapshot(
            mediaType = DiscoveryMediaType.MANGA,
            generation = 4L,
            projectionVersion = 1L,
            configurationKey = "cfg",
            desiredConfigurationKey = "cfg",
            publishedByRequestId = "req",
            committedAt = 10_000L,
            outcome = null,
            suggestions = listOf(nativeCard, externalCard),
            eligibleSuggestions = listOf(nativeCard, externalCard),
            homeBatchKeys = emptyList(),
            homeBatchVersion = 0L,
            issuedKeys = emptySet(),
            traversal = DiscoveryTraversalState.VALID,
            cursors = emptyList(),
        )
        val sourcesOnlyIncomplete = DiscoverySourcePolicy(
            mediaType = DiscoveryMediaType.MANGA,
            revision = "cold",
            inventoryComplete = false,
            allowedPluginKeys = emptySet(),
            pluginMembers = emptyMap(),
            allowedExternalProviders = emptyMap(),
        )

        // Native card is shown provisionally, while disallowed external provider stays strictly excluded.
        selectColdStartVisibleCandidates(snapshot, sourcesOnlyIncomplete).map { it.title } shouldBe listOf("Native")

        // Synthetic generation=0 snapshot does NOT bypass inventoryComplete gate.
        selectColdStartVisibleCandidates(snapshot.copy(generation = 0L), sourcesOnlyIncomplete) shouldBe emptyList()

        // When external provider IS allowed, both are visible.
        val mixedIncomplete = DiscoverySourcePolicy(
            mediaType = DiscoveryMediaType.MANGA,
            revision = "cold",
            inventoryComplete = false,
            allowedPluginKeys = emptySet(),
            pluginMembers = emptyMap(),
            allowedExternalProviders = mapOf(DiscoveryRowType.TREND to setOf(DiscoveryExternalProvider.ANILIST)),
        )
        selectColdStartVisibleCandidates(snapshot, mixedIncomplete).map { it.title } shouldBe
            listOf("Native", "External")
    }

    @Test
    fun `onScreenReentry before first publication keeps fast-cached discovery`(): Unit =
        runBlocking {
            val card1 = suggestion("Alpha")
            val card2 = suggestion("Beta")
            val cachedItems = listOf(card1.toHomeHubDiscoveryItem(), card2.toHomeHubDiscoveryItem())
            val repo = object : FakeDiscoveryRepositoryBase() {
                override fun observePublication(media: DiscoveryMediaType): Flow<DiscoveryPublicationSnapshot> =
                    kotlinx.coroutines.flow.emptyFlow()
            }
            val prefs = DiscoveryPreferences(PersistingPreferenceStore()).apply {
                discoveryEnabled().set(true)
                lastGenerationAt(DiscoveryMediaType.MANGA).set(10_000L)
            }
            val incompletePolicy = DiscoverySourcePolicy(
                mediaType = DiscoveryMediaType.MANGA,
                revision = "cold",
                inventoryComplete = false,
                allowedPluginKeys = emptySet(),
                pluginMembers = emptyMap(),
            )
            val policyProvider = object : DiscoverySourcePolicyProvider {
                override fun current(mediaType: DiscoveryMediaType) = incompletePolicy
                override fun observe(mediaType: DiscoveryMediaType) = MutableStateFlow(incompletePolicy)
                override suspend fun awaitComplete(mediaType: DiscoveryMediaType) = incompletePolicy
            }
            var state = HomeHubUiState(
                userName = "User",
                userAvatar = "",
                greeting = tachiyomi.i18n.MR.strings.app_name,
                greetingReady = true,
                isLoading = false,
                showWelcome = false,
                discovery = cachedItems,
                discoveryPool = cachedItems,
                discoveryEnabled = true,
                isDiscoveryLoading = false,
                discoveryLastUpdated = 10_000L,
            )
            val persisted = mutableListOf<List<HomeHubDiscoveryItem>>()
            val controller = HomeDiscoveryController(
                sourcePolicyProvider = policyProvider,
                mediaType = DiscoveryMediaType.MANGA,
                repository = repo,
                preferences = prefs,
                context = mockk(relaxed = true),
                scope = CoroutineScope(Dispatchers.Unconfined),
                onStateUpdate = { updater -> state = updater(state) },
                currentState = { state },
                onEmitEffect = {},
                onTeaserPersisted = { persisted += it },
            )

            // Cold-start ON_RESUME triggers onScreenReentry() before observePublication() emits.
            controller.onScreenReentry()

            state.discovery.map { it.title } shouldBe listOf("Alpha", "Beta")
            state.discoveryPool.map { it.title } shouldBe listOf("Alpha", "Beta")
            state.isDiscoveryLoading shouldBe false
            persisted shouldBe emptyList()
            resolveForYouEmptyState(
                discoveryEnabled = state.discoveryEnabled,
                isRefreshing = state.isDiscoveryRefreshing,
                hasItems = state.discoveryPool.isNotEmpty(),
                lastUpdatedAt = state.discoveryLastUpdated,
                isFailed = state.isDiscoveryFailed,
                isLoading = state.isDiscoveryLoading,
            ) shouldBe null
        }
}
