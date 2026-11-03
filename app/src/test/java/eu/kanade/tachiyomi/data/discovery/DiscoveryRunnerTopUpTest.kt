package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryPreparedPool
import tachiyomi.domain.discovery.model.DiscoveryPublicationOutcome
import tachiyomi.domain.discovery.model.DiscoveryPublicationSnapshot
import tachiyomi.domain.discovery.model.DiscoveryPublishReceipt
import tachiyomi.domain.discovery.model.DiscoveryRetirementPolicy
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySourceCursor
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.model.DiscoveryTopUpReceipt
import tachiyomi.domain.discovery.model.DiscoveryTraversalState
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle

class DiscoveryRunnerTopUpTest {

    private val media = DiscoveryMediaType.NOVEL

    private fun suggestion(number: Int): DiscoverySuggestion = DiscoverySuggestion(
        id = number.toLong(),
        mediaType = media,
        rowType = DiscoveryRowType.SOURCE,
        title = "Title $number",
        cleanTitle = normalizeDiscoveryTitle("Title $number"),
        coverUrl = null,
        reason = null,
        seedTitle = null,
        provider = "source",
        score = 1.0,
        position = number.toLong(),
        createdAt = 0L,
        sourceId = 1L,
        sourceUrl = "/title-$number",
        identityKey = "key:$number",
    )

    private fun testSourcePrefs() = SourcePreferences(InMemoryPreferenceStore())

    private class TopUpTestRepository(
        var poolSnapshot: DiscoveryPublicationSnapshot,
    ) : FakeDiscoveryRepositoryBase() {
        var topUpPoolCalled = false
        var topUpAdditions: List<DiscoverySuggestion> = emptyList()
        var publishGenerationCalled = false
        var customTopUpReceipt: DiscoveryTopUpReceipt? = null

        override suspend fun publication(media: DiscoveryMediaType): DiscoveryPublicationSnapshot = poolSnapshot

        override suspend fun topUpPool(
            media: DiscoveryMediaType,
            expectedGeneration: Long,
            additions: List<DiscoverySuggestion>,
            cursors: List<DiscoverySourceCursor>,
            maxPoolSize: Int,
        ): DiscoveryTopUpReceipt {
            topUpPoolCalled = true
            topUpAdditions = additions
            customTopUpReceipt?.let { return it }
            val updated = poolSnapshot.suggestions + additions
            poolSnapshot = poolSnapshot.copy(
                suggestions = updated,
                eligibleSuggestions = updated,
            )
            return DiscoveryTopUpReceipt.ToppedUp(
                added = additions.size,
                eligibleAfter = updated.size,
                snapshot = poolSnapshot,
            )
        }

        override suspend fun reserveGeneration(
            media: DiscoveryMediaType,
            requestId: String,
            expectedGeneration: Long,
            configurationKey: String,
        ) = tachiyomi.domain.discovery.model.DiscoveryGenerationToken(
            mediaType = media,
            requestId = requestId,
            ownerRevision = 1L,
            expectedGeneration = expectedGeneration,
            configurationKey = configurationKey,
        )

        override suspend fun publishGeneration(
            token: tachiyomi.domain.discovery.model.DiscoveryGenerationToken,
            prepared: DiscoveryPreparedPool,
            retirement: DiscoveryRetirementPolicy,
        ): DiscoveryPublishReceipt {
            publishGenerationCalled = true
            val items = (prepared as? DiscoveryPreparedPool.Fresh)?.suggestions.orEmpty()
            val outcome = when (prepared) {
                is DiscoveryPreparedPool.VerifiedEmpty -> DiscoveryPublicationOutcome.EXHAUSTED
                is DiscoveryPreparedPool.Fresh -> if (prepared.partial) {
                    DiscoveryPublicationOutcome.PARTIAL
                } else {
                    DiscoveryPublicationOutcome.READY
                }
            }
            poolSnapshot = poolSnapshot.copy(
                generation = token.expectedGeneration + 1,
                outcome = outcome,
                suggestions = items,
                eligibleSuggestions = items,
            )
            return DiscoveryPublishReceipt.Published(poolSnapshot)
        }
    }

    @Test
    fun `pool below low water triggers top up and appends items without changing generation or full publish`() =
        runTest {
            val initialItems = (1..40).map(::suggestion)
            val initialSnapshot = DiscoveryPublicationSnapshot(
                mediaType = media,
                generation = 5L,
                projectionVersion = 5L,
                configurationKey = "cfg",
                desiredConfigurationKey = "cfg",
                publishedByRequestId = null,
                committedAt = 1000L,
                outcome = null,
                suggestions = initialItems,
                eligibleSuggestions = initialItems,
                homeBatchKeys = emptyList(),
                homeBatchVersion = 1L,
                issuedKeys = emptySet(),
                traversal = DiscoveryTraversalState.VALID,
                cursors = emptyList(),
            )
            val repo = TopUpTestRepository(initialSnapshot)
            val preferences = DiscoveryPreferences(InMemoryPreferenceStore())

            var builderInvoked = false
            val builder = object : DiscoveryRowBuilder {
                override val rowType = DiscoveryRowType.SOURCE
                override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
                    builderInvoked = true
                    return listOf(
                        DiscoveryRowItem(
                            title = "Added 1",
                            cleanTitle = "added 1",
                            coverUrl = null,
                            reason = null,
                            seedTitle = null,
                            provider = "source",
                            score = 1.0,
                            sourceId = 1L,
                            sourceUrl = "/added-1",
                        ),
                    )
                }
            }

            val runner = DiscoveryRunner(
                repository = repo,
                preferences = preferences,
                seedSources = object : DiscoverySeedSources {
                    override suspend fun candidates(mediaType: DiscoveryMediaType) = emptyList<DiscoverySeedInput>()
                    override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = emptySet<String>()
                },
                coordinatorFactory = { DiscoveryCoordinator(listOf(builder)) },
                sourcePreferencesProvider = ::testSourcePrefs,
                installedPluginsProvider = { emptyList() },
                fallbackSourceIdProvider = { 1L },
            )

            val outcome = runner.run(listOf(media), topUp = true).getValue(media)

            builderInvoked shouldBe true
            repo.topUpPoolCalled shouldBe true
            repo.publishGenerationCalled shouldBe false
            repo.poolSnapshot.generation shouldBe 5L
            outcome.status shouldBe DiscoveryRunStatus.SUCCESS
        }

    @Test
    fun `pool at or above low water is SKIPPED without querying builders`() = runTest {
        val initialItems = (1..50).map(::suggestion)
        val initialSnapshot = DiscoveryPublicationSnapshot(
            mediaType = media,
            generation = 5L,
            projectionVersion = 5L,
            configurationKey = "cfg",
            desiredConfigurationKey = "cfg",
            publishedByRequestId = null,
            committedAt = 1000L,
            outcome = null,
            suggestions = initialItems,
            eligibleSuggestions = initialItems,
            homeBatchKeys = emptyList(),
            homeBatchVersion = 1L,
            issuedKeys = emptySet(),
            traversal = DiscoveryTraversalState.VALID,
            cursors = emptyList(),
        )
        val repo = TopUpTestRepository(initialSnapshot)
        val preferences = DiscoveryPreferences(InMemoryPreferenceStore())

        var builderInvoked = false
        val builder = object : DiscoveryRowBuilder {
            override val rowType = DiscoveryRowType.SOURCE
            override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
                builderInvoked = true
                return emptyList()
            }
        }

        val runner = DiscoveryRunner(
            repository = repo,
            preferences = preferences,
            seedSources = object : DiscoverySeedSources {
                override suspend fun candidates(mediaType: DiscoveryMediaType) = emptyList<DiscoverySeedInput>()
                override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = emptySet<String>()
            },
            coordinatorFactory = { DiscoveryCoordinator(listOf(builder)) },
            sourcePreferencesProvider = ::testSourcePrefs,
            installedPluginsProvider = { emptyList() },
            fallbackSourceIdProvider = { 1L },
        )

        val outcome = runner.run(listOf(media), topUp = true).getValue(media)

        builderInvoked shouldBe false
        repo.topUpPoolCalled shouldBe false
        outcome.status shouldBe DiscoveryRunStatus.SKIPPED
    }

    @Test
    fun `pool empty and top up returns NothingToAdd falls back to full run`() = runTest {
        val initialSnapshot = DiscoveryPublicationSnapshot(
            mediaType = media,
            generation = 5L,
            projectionVersion = 5L,
            configurationKey = "cfg",
            desiredConfigurationKey = "cfg",
            publishedByRequestId = null,
            committedAt = 1000L,
            outcome = null,
            suggestions = emptyList(),
            eligibleSuggestions = emptyList(),
            homeBatchKeys = emptyList(),
            homeBatchVersion = 1L,
            issuedKeys = emptySet(),
            traversal = DiscoveryTraversalState.VALID,
            cursors = emptyList(),
        )
        val repo = TopUpTestRepository(initialSnapshot)
        repo.customTopUpReceipt = DiscoveryTopUpReceipt.NothingToAdd
        val preferences = DiscoveryPreferences(
            InMemoryPreferenceStore(
                sequenceOf(
                    InMemoryPreferenceStore.InMemoryPreference("discovery_row_like", false, true),
                    InMemoryPreferenceStore.InMemoryPreference("discovery_row_taste", false, true),
                    InMemoryPreferenceStore.InMemoryPreference("discovery_row_trend", false, true),
                    InMemoryPreferenceStore.InMemoryPreference("discovery_row_source", true, true),
                ),
            ),
        )

        val builder = object : DiscoveryRowBuilder {
            override val rowType = DiscoveryRowType.SOURCE
            override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
                return listOf(
                    DiscoveryRowItem(
                        title = "Fallback Title",
                        cleanTitle = "fallback title",
                        coverUrl = null,
                        reason = null,
                        seedTitle = null,
                        provider = "source",
                        score = 1.0,
                        sourceId = 1L,
                        sourceUrl = "/fb",
                    ),
                )
            }
        }

        val runner = DiscoveryRunner(
            repository = repo,
            preferences = preferences,
            seedSources = object : DiscoverySeedSources {
                override suspend fun candidates(mediaType: DiscoveryMediaType) = emptyList<DiscoverySeedInput>()
                override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = emptySet<String>()
            },
            coordinatorFactory = { DiscoveryCoordinator(listOf(builder)) },
            sourcePreferencesProvider = ::testSourcePrefs,
            installedPluginsProvider = { emptyList() },
            fallbackSourceIdProvider = { 1L },
        )

        val outcome = runner.run(listOf(media), topUp = true).getValue(media)

        repo.topUpPoolCalled shouldBe true
        repo.publishGenerationCalled shouldBe true
        repo.poolSnapshot.generation shouldBe 6L
        outcome.status shouldBe DiscoveryRunStatus.SUCCESS
    }
}
