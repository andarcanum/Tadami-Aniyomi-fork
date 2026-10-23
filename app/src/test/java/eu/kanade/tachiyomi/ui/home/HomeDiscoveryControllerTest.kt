package eu.kanade.tachiyomi.ui.home

import android.content.Context
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryBlacklistEntry
import tachiyomi.domain.discovery.model.DiscoveryHiddenEntry
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySignal
import tachiyomi.domain.discovery.model.DiscoverySignalType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.repository.DiscoveryRepository

class HomeDiscoveryControllerTest {

    private fun testSuggestion(title: String) = DiscoverySuggestion(
        id = 0L,
        mediaType = DiscoveryMediaType.ANIME,
        rowType = DiscoveryRowType.TREND,
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = "cover_$title",
        reason = null,
        seedTitle = null,
        provider = "test",
        score = 1.0,
        position = 0L,
        createdAt = 0L,
    )

    private class TestDiscoveryRepo : DiscoveryRepository {
        val poolFlow = MutableStateFlow<List<DiscoverySuggestion>>(emptyList())
        val hiddenFlow = MutableStateFlow<Set<String>>(emptySet())
        val consumedFlow = MutableStateFlow<Set<String>>(emptySet())
        val blacklistFlow = MutableStateFlow<Set<String>>(emptySet())
        val recordedSignals = mutableListOf<Triple<String, DiscoverySignalType, List<String>>>()
        val unhiddenTitles = mutableListOf<String>()
        val removedSignalTitles = mutableListOf<String>()
        val markedShownTitles = mutableListOf<String>()

        override fun subscribe(mediaType: DiscoveryMediaType): Flow<List<DiscoverySuggestion>> = poolFlow
        override fun subscribeHidden(mediaType: DiscoveryMediaType): Flow<Set<String>> = hiddenFlow
        override fun subscribeConsumed(mediaType: DiscoveryMediaType): Flow<Set<String>> = consumedFlow
        override fun subscribeBlacklist(mediaType: DiscoveryMediaType): Flow<Set<String>> = blacklistFlow

        override suspend fun replaceRows(
            mediaType: DiscoveryMediaType,
            rowType: DiscoveryRowType,
            items: List<DiscoverySuggestion>,
        ) = Unit

        override suspend fun getHiddenTitles(mediaType: DiscoveryMediaType): Set<String> = hiddenFlow.value
        override suspend fun hide(mediaType: DiscoveryMediaType, cleanTitle: String) = Unit
        override suspend fun unhide(mediaType: DiscoveryMediaType, cleanTitle: String) {
            unhiddenTitles += cleanTitle
        }
        override suspend fun clearHidden(mediaType: DiscoveryMediaType) = Unit
        override suspend fun lastUpdatedAt(mediaType: DiscoveryMediaType): Long? = null
        override suspend fun getBlacklistedTags(mediaType: DiscoveryMediaType): Set<String> = emptySet()
        override suspend fun blacklistTag(mediaType: DiscoveryMediaType, tag: String) = Unit
        override suspend fun unblacklistTag(mediaType: DiscoveryMediaType, tag: String) = Unit
        override suspend fun clearBlacklist(mediaType: DiscoveryMediaType) = Unit
        override suspend fun getHiddenEntries(mediaType: DiscoveryMediaType): List<DiscoveryHiddenEntry> = emptyList()
        override suspend fun getBlacklistEntries(
            mediaType: DiscoveryMediaType,
        ): List<DiscoveryBlacklistEntry> = emptyList()
        override suspend fun restoreHiddenEntries(
            mediaType: DiscoveryMediaType,
            entries: List<DiscoveryHiddenEntry>,
        ) = Unit
        override suspend fun restoreBlacklistEntries(
            mediaType: DiscoveryMediaType,
            entries: List<DiscoveryBlacklistEntry>,
        ) = Unit

        override suspend fun getShownTitles(mediaType: DiscoveryMediaType, windowMillis: Long): Set<String> = emptySet()
        override suspend fun getShownTitlesWithTimestamp(
            mediaType: DiscoveryMediaType,
            windowMillis: Long,
        ): Map<String, Long> = emptyMap()
        override suspend fun getShownWithCount(
            mediaType: DiscoveryMediaType,
            windowMillis: Long,
        ): List<Triple<String, Long, Int>> = emptyList()

        override suspend fun recordExposure(
            mediaType: DiscoveryMediaType,
            cleanTitles: Collection<String>,
            timestamp: Long,
        ) {
            markedShownTitles.addAll(cleanTitles)
        }

        override suspend fun suppressRecentlyConsumed(
            mediaType: DiscoveryMediaType,
            cleanTitles: Collection<String>,
            timestamp: Long,
        ) = Unit

        override suspend fun getRecentExclusions(
            mediaType: DiscoveryMediaType,
            windowMillis: Long,
        ): Set<String> = emptySet()

        override suspend fun getRecentExclusionsWithTimestamp(
            mediaType: DiscoveryMediaType,
            windowMillis: Long,
        ): Map<String, Long> = emptyMap()

        override suspend fun markShown(
            mediaType: DiscoveryMediaType,
            cleanTitles: Collection<String>,
            timestamp: Long,
        ) {
            recordExposure(mediaType, cleanTitles, timestamp)
        }

        override suspend fun clearShown(mediaType: DiscoveryMediaType) = Unit
        override suspend fun hasUnboundSourceRows(): Boolean = false
        override suspend fun getSignals(mediaType: DiscoveryMediaType): List<DiscoverySignal> = emptyList()

        override suspend fun recordSignal(
            mediaType: DiscoveryMediaType,
            cleanTitle: String,
            title: String,
            signalType: DiscoverySignalType,
            genres: List<String>,
            provider: String?,
            sourceKey: String?,
            timestamp: Long,
        ) {
            recordedSignals += Triple(cleanTitle, signalType, genres)
        }

        override suspend fun removeSignal(mediaType: DiscoveryMediaType, cleanTitle: String) {
            removedSignalTitles += cleanTitle
        }

        override suspend fun clearSignals(mediaType: DiscoveryMediaType) = Unit
        override suspend fun clearAllSignals() = Unit
        override suspend fun restoreSignals(signals: List<DiscoverySignal>) = Unit
    }

    @Test
    fun `likeDiscoveryItem records signal, dismisses card from pool and updates teaser`() {
        runBlocking {
            val repo = TestDiscoveryRepo()
            val prefs = DiscoveryPreferences(PersistingPreferenceStore())
            prefs.discoveryEnabled().set(true)
            val context = mockk<Context>(relaxed = true)

            var currentState = HomeHubUiState(
                userName = "Test",
                userAvatar = "",
                greeting = tachiyomi.i18n.MR.strings.app_name,
                greetingReady = true,
                isLoading = false,
                showWelcome = false,
                discovery = listOf(
                    testSuggestion("Card 1").toHomeHubDiscoveryItem(),
                    testSuggestion("Card 2").toHomeHubDiscoveryItem(),
                ),
                discoveryPool = listOf(
                    testSuggestion("Card 1").toHomeHubDiscoveryItem(),
                    testSuggestion("Card 2").toHomeHubDiscoveryItem(),
                    testSuggestion("Card 3").toHomeHubDiscoveryItem(),
                ),
            )

            val controller = HomeDiscoveryController(
                mediaType = DiscoveryMediaType.ANIME,
                repository = repo,
                preferences = prefs,
                context = context,
                scope = CoroutineScope(Dispatchers.Unconfined),
                onStateUpdate = { updater -> currentState = updater(currentState) },
                currentState = { currentState },
                onEmitEffect = {},
            )

            controller.likeDiscoveryItem(testSuggestion("Card 1").toHomeHubDiscoveryItem())

            waitUntil { repo.recordedSignals.isNotEmpty() && currentState.discovery.size == 2 }

            repo.recordedSignals.size shouldBe 1
            repo.recordedSignals[0].first shouldBe "card 1"
            repo.recordedSignals[0].second shouldBe DiscoverySignalType.LIKE

            currentState.discovery.map { it.title } shouldBe listOf("Card 3", "Card 2")
            currentState.discoveryPool.map { it.title } shouldBe listOf("Card 2", "Card 3")
        }
    }

    @Test
    fun `restoreHiddenTeaserItem unhides and removes signal in repository and updates state`() {
        runBlocking {
            val repo = TestDiscoveryRepo()
            val prefs = DiscoveryPreferences(PersistingPreferenceStore())
            val context = mockk<Context>(relaxed = true)

            var currentState = HomeHubUiState(
                userName = "Test",
                userAvatar = "",
                greeting = tachiyomi.i18n.MR.strings.app_name,
                greetingReady = true,
                isLoading = false,
                showWelcome = false,
                discovery = listOf(
                    testSuggestion("Card 2").toHomeHubDiscoveryItem(),
                ),
                discoveryPool = listOf(
                    testSuggestion("Card 2").toHomeHubDiscoveryItem(),
                ),
            )

            val controller = HomeDiscoveryController(
                mediaType = DiscoveryMediaType.ANIME,
                repository = repo,
                preferences = prefs,
                context = context,
                scope = CoroutineScope(Dispatchers.Unconfined),
                onStateUpdate = { updater -> currentState = updater(currentState) },
                currentState = { currentState },
                onEmitEffect = {},
            )

            controller.restoreHiddenTeaserItem(testSuggestion("Card 1").toHomeHubDiscoveryItem())

            waitUntil {
                repo.unhiddenTitles.isNotEmpty() &&
                    repo.removedSignalTitles.isNotEmpty() &&
                    currentState.discovery.any { it.cleanTitle == "card 1" }
            }

            repo.unhiddenTitles shouldBe listOf("card 1")
            repo.removedSignalTitles shouldBe listOf("card 1")

            currentState.discovery.any { it.cleanTitle == "card 1" } shouldBe true
            currentState.discoveryPool.any { it.cleanTitle == "card 1" } shouldBe true
        }
    }

    @Test
    fun `markDiscoveryItemShown calls repository markShown`() {
        runBlocking {
            val repo = TestDiscoveryRepo()
            val prefs = DiscoveryPreferences(PersistingPreferenceStore())
            val context = mockk<Context>(relaxed = true)
            var currentState = HomeHubUiState(
                userName = "Test",
                userAvatar = "",
                greeting = tachiyomi.i18n.MR.strings.app_name,
                greetingReady = true,
                isLoading = false,
                showWelcome = false,
            )

            val controller = HomeDiscoveryController(
                mediaType = DiscoveryMediaType.ANIME,
                repository = repo,
                preferences = prefs,
                context = context,
                scope = CoroutineScope(Dispatchers.Unconfined),
                onStateUpdate = { updater -> currentState = updater(currentState) },
                currentState = { currentState },
                onEmitEffect = {},
            )

            controller.markDiscoveryItemShown(testSuggestion("Card 1").toHomeHubDiscoveryItem())

            waitUntil { repo.markedShownTitles.isNotEmpty() }

            repo.markedShownTitles shouldBe listOf("card 1")
        }
    }

    @Test
    fun `dismissed card does not re-enter teaser or pool when repository re-emits`() {
        runBlocking {
            val repo = TestDiscoveryRepo()
            val prefs = DiscoveryPreferences(PersistingPreferenceStore())
            prefs.discoveryEnabled().set(true)
            val context = mockk<Context>(relaxed = true)

            var currentState = HomeHubUiState(
                userName = "Test",
                userAvatar = "",
                greeting = tachiyomi.i18n.MR.strings.app_name,
                greetingReady = true,
                isLoading = false,
                showWelcome = false,
            )

            val controller = HomeDiscoveryController(
                mediaType = DiscoveryMediaType.ANIME,
                repository = repo,
                preferences = prefs,
                context = context,
                scope = CoroutineScope(Dispatchers.Unconfined),
                onStateUpdate = { updater -> currentState = updater(currentState) },
                currentState = { currentState },
                onEmitEffect = {},
            )

            repo.poolFlow.value = listOf(
                testSuggestion("Card 1"),
                testSuggestion("Card 2"),
                testSuggestion("Card 3"),
            )
            controller.startObserving()

            waitUntil { currentState.discovery.size == 3 }
            currentState.discovery.map { it.title } shouldBe listOf("Card 1", "Card 2", "Card 3")

            controller.likeDiscoveryItem(testSuggestion("Card 1").toHomeHubDiscoveryItem())
            waitUntil { currentState.discovery.none { it.cleanTitle == "card 1" } }

            repo.poolFlow.value = listOf(
                testSuggestion("Card 1"),
                testSuggestion("Card 2"),
                testSuggestion("Card 3"),
                testSuggestion("Card 4"),
            )

            waitUntil { currentState.discoveryPool.any { it.cleanTitle == "card 4" } }

            currentState.discovery.none { it.cleanTitle == "card 1" } shouldBe true
            currentState.discoveryPool.none { it.cleanTitle == "card 1" } shouldBe true
        }
    }

    private suspend fun waitUntil(timeoutMs: Long = 5000, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                throw AssertionError("Condition not met within ${timeoutMs}ms")
            }
            kotlinx.coroutines.delay(10)
        }
    }
}
