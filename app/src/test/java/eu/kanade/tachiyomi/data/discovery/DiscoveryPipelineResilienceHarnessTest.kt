package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryEligibilitySnapshot
import tachiyomi.domain.discovery.model.DiscoveryGenerationToken
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryPreparedPool
import tachiyomi.domain.discovery.model.DiscoveryPublicationOutcome
import tachiyomi.domain.discovery.model.DiscoveryPublicationSnapshot
import tachiyomi.domain.discovery.model.DiscoveryPublishReceipt
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import tachiyomi.domain.discovery.model.DiscoverySourcePolicy
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.model.DiscoveryTraversalState
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import tachiyomi.domain.discovery.service.DiscoverySourcePolicyProvider
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DiscoveryPipelineResilienceHarnessTest {

    @Test
    fun `mixed source fleet with blocking timeouts and DNS errors publishes fast results within SLA`(): Unit =
        runBlocking(Dispatchers.IO) {
            val releaseHung = CountDownLatch(1)
            val productiveIds = listOf(101L, 102L, 103L, 104L)
            val hungIds = setOf(201L, 202L)
            val dnsErrorIds = setOf(301L)
            val emptyIds = setOf(401L)
            val allIds = productiveIds + hungIds + dnsErrorIds + emptyIds

            val catalog = object : DiscoverySourceCatalog {
                override suspend fun popular(
                    mediaType: DiscoveryMediaType,
                    sourceId: Long,
                    page: Int,
                    releaseStatuses: Set<DiscoveryReleaseStatus>,
                ) = emptyList<DiscoveryRowItem>()

                override suspend fun latest(
                    mediaType: DiscoveryMediaType,
                    sourceId: Long,
                    page: Int,
                    releaseStatuses: Set<DiscoveryReleaseStatus>,
                ) = emptyList<DiscoveryRowItem>()

                override suspend fun popularWithGenres(
                    mediaType: DiscoveryMediaType,
                    sourceId: Long,
                    genres: List<String>,
                    page: Int,
                    releaseStatuses: Set<DiscoveryReleaseStatus>,
                ) = emptyList<DiscoveryRowItem>()

                override suspend fun popularWithGenresAllocated(
                    mediaType: DiscoveryMediaType,
                    sourceId: Long,
                    genres: List<String>,
                    page: Int,
                    releaseStatuses: Set<DiscoveryReleaseStatus>,
                    budget: DiscoveryVerificationBudget?,
                    maxDetails: Int,
                    excludedKeys: Set<String>,
                ): DiscoveryGenrePage {
                    if (sourceId in hungIds) {
                        releaseHung.await(5, TimeUnit.SECONDS)
                        return emptyGenrePage()
                    }
                    if (sourceId in dnsErrorIds) {
                        throw IOException("Unable to resolve host")
                    }
                    if (sourceId in emptyIds) {
                        return emptyGenrePage()
                    }
                    val items = (1..5).map { idx ->
                        DiscoveryRowItem(
                            title = "Title_${sourceId}_p${page}_$idx",
                            cleanTitle = "title_${sourceId}_p${page}_$idx",
                            coverUrl = null,
                            reason = null,
                            seedTitle = null,
                            provider = "Source$sourceId",
                            score = 1.0,
                            sourceId = sourceId,
                            sourceUrl = "/$sourceId/$page/$idx",
                            genres = listOf("Fantasy"),
                        )
                    }
                    return DiscoveryGenrePage(
                        items = items,
                        verified = items.size,
                        unattempted = 0,
                        metadataUnavailable = 0,
                        transientFailures = 0,
                        hasNextPage = page < 2,
                        genreFilterApplied = true,
                    )
                }
            }

            val published = mutableListOf<DiscoverySuggestion>()
            val repo = harnessRepository(published)
            val prefs = DiscoveryPreferences(PersistingPreferenceStore()).apply {
                rowLikeEnabled().set(false)
                rowTasteEnabled().set(false)
                rowTrendEnabled().set(false)
                rowSourceEnabled().set(true)
                externalProvidersEnabled().set(false)
                requiredGenres(DiscoveryMediaType.MANGA).set("Fantasy")
            }
            val policy = DiscoverySourcePolicy(
                mediaType = DiscoveryMediaType.MANGA,
                revision = "rev-1",
                inventoryComplete = true,
                allowedPluginKeys = allIds.map { "p$it" }.toSet(),
                pluginMembers = allIds.associate { "p$it" to setOf(it) },
                representativeSourceIds = allIds,
                sourceWeights = allIds.associateWith { 1.0 },
            )

            // Verify fast wave throughput without chunked barriers when hung sources exist.
            val t0 = System.nanoTime() / 1_000_000L
            val waveResults = try {
                queryDiscoveryGenreSources(
                    catalog = catalog,
                    media = DiscoveryMediaType.MANGA,
                    sourceIds = allIds,
                    genres = listOf("Fantasy"),
                    page = 1,
                    releaseStatuses = emptySet(),
                    policy = DiscoveryCandidatePolicy(GenreFilterSpec(requiredGenres = setOf("Fantasy"))),
                    maxConcurrency = 4,
                    timeoutMs = 150L,
                    hydrate = true,
                    targetItems = 10,
                    budget = DiscoveryVerificationBudget(budgetMs = 3_000L, maxDetails = 40),
                )
            } finally {
                releaseHung.countDown()
            }
            val waveElapsed = System.nanoTime() / 1_000_000L - t0
            waveElapsed shouldBeLessThan 2_000L
            waveResults.filter { it.sourceId in productiveIds }.sumOf { it.summary.items.size } shouldBe 40
            waveResults.filter { it.sourceId in hungIds }.all {
                it.failure?.reason == GenreSourceFailure.TIMEOUT
            } shouldBe true

            // Verify runner publication & failure accounting (with quick source timeout via short media budget).
            val runnerHungRelease = CountDownLatch(1)
            val runnerCatalog = object : DiscoverySourceCatalog by catalog {
                override suspend fun popularWithGenresAllocated(
                    mediaType: DiscoveryMediaType,
                    sourceId: Long,
                    genres: List<String>,
                    page: Int,
                    releaseStatuses: Set<DiscoveryReleaseStatus>,
                    budget: DiscoveryVerificationBudget?,
                    maxDetails: Int,
                    excludedKeys: Set<String>,
                ): DiscoveryGenrePage = if (sourceId in hungIds) {
                    runnerHungRelease.await(15, TimeUnit.SECONDS)
                    emptyGenrePage()
                } else {
                    catalog.popularWithGenresAllocated(
                        mediaType,
                        sourceId,
                        genres,
                        page,
                        releaseStatuses,
                        budget,
                        maxDetails,
                        excludedKeys,
                    )
                }
            }
            val fastRunner = DiscoveryRunner(
                repository = repo,
                preferences = prefs,
                seedSources = object : DiscoverySeedSources {
                    override suspend fun candidates(mediaType: DiscoveryMediaType) = emptyList<DiscoverySeedInput>()
                    override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = emptySet<String>()
                },
                sourceCatalog = runnerCatalog,
                coordinatorFactory = { builders -> DiscoveryCoordinator(builders, rowLimit = 50) },
                sourcePreferencesProvider = { SourcePreferences(PersistingPreferenceStore()) },
                sourcePolicyProvider = object : DiscoverySourcePolicyProvider {
                    override fun current(mediaType: DiscoveryMediaType) = policy
                    override fun observe(mediaType: DiscoveryMediaType): Flow<DiscoverySourcePolicy> = flowOf(policy)
                    override suspend fun awaitComplete(mediaType: DiscoveryMediaType) = policy
                },
            )
            val runnerStartedAt = System.nanoTime() / 1_000_000L
            val outcome = try {
                fastRunner.run(listOf(DiscoveryMediaType.MANGA), isManualRefresh = true, totalBudgetMs = 22_000L)
                    .getValue(DiscoveryMediaType.MANGA)
            } finally {
                runnerHungRelease.countDown()
            }
            val runnerElapsed = System.nanoTime() / 1_000_000L - runnerStartedAt
            runnerElapsed shouldBeLessThan 10_000L
            outcome.status shouldBe DiscoveryRunStatus.PARTIAL
            published.size shouldBeGreaterThan 0
            prefs.lastSourceFailureCount(DiscoveryMediaType.MANGA).get() shouldBe 3
            val recordedHistory = parseSourceFailureHistory(prefs.sourceFailureHistory(DiscoveryMediaType.MANGA).get())
            recordedHistory.keys shouldContainAll (hungIds + dnsErrorIds)
        }

    @Test
    fun `prioritizeHealthySources demotes open-circuit sources behind healthy ones`() {
        val now = 1_000_000L
        val history = mapOf(
            20L to SourceFailureRecord(20L, lastFailedAt = now - 60_000L, failCount = 3),
            30L to SourceFailureRecord(30L, lastFailedAt = now - 60_000L, failCount = 1),
        )
        prioritizeHealthySources(listOf(20L, 10L, 30L, 40L), history, now) shouldBe listOf(10L, 30L, 40L, 20L)
        prioritizeHealthySources(listOf(20L), history, now) shouldBe listOf(20L)
    }

    @Test
    fun `effectiveTopUpLowWater switches between hysteresis 48 and eager full-60 refill`() {
        topUpNeed(eligible = 59, lowWater = effectiveTopUpLowWater(eagerRefill = false)) shouldBe 0
        topUpNeed(eligible = 47, lowWater = effectiveTopUpLowWater(eagerRefill = false)) shouldBe 13
        topUpNeed(eligible = 59, lowWater = effectiveTopUpLowWater(eagerRefill = true)) shouldBe 1
        topUpNeed(eligible = 60, lowWater = effectiveTopUpLowWater(eagerRefill = true)) shouldBe 0
    }

    private fun emptyGenrePage() = DiscoveryGenrePage(
        items = emptyList(),
        verified = 0,
        unattempted = 0,
        metadataUnavailable = 0,
        transientFailures = 0,
        hasNextPage = false,
    )

    private fun harnessRepository(published: MutableList<DiscoverySuggestion>): DiscoveryRepository =
        mockk<DiscoveryRepository>(relaxed = true).also { repo ->
            var generation = 0L
            coEvery { repo.getSignals(any()) } returns emptyList()
            coEvery { repo.publication(any()) } returns DiscoveryPublicationSnapshot(
                mediaType = DiscoveryMediaType.MANGA,
                generation = 0L,
                projectionVersion = 0L,
                configurationKey = "",
                desiredConfigurationKey = "",
                publishedByRequestId = null,
                committedAt = 0L,
                outcome = null,
                suggestions = emptyList(),
                eligibleSuggestions = emptyList(),
                homeBatchKeys = emptyList(),
                homeBatchVersion = 0L,
                issuedKeys = emptySet(),
                traversal = DiscoveryTraversalState.VALID,
                cursors = emptyList(),
            )
            coEvery { repo.eligibility(any()) } returns DiscoveryEligibilitySnapshot(DiscoveryMediaType.MANGA, 0L)
            coEvery { repo.getHiddenTitles(any()) } returns emptySet()
            coEvery { repo.getBlacklistedTags(any()) } returns emptySet()
            coEvery { repo.getRecentExclusionsWithTimestamp(any(), any()) } returns emptyMap()
            coEvery { repo.getShownWithCount(any(), any()) } returns emptyList()
            coEvery { repo.reserveGeneration(any(), any(), any(), any()) } answers {
                DiscoveryGenerationToken(firstArg(), secondArg(), 1L, thirdArg(), arg(3))
            }
            coEvery { repo.publishGeneration(any(), any(), any()) } answers {
                val token = firstArg<DiscoveryGenerationToken>()
                val prepared = secondArg<DiscoveryPreparedPool>()
                val items = (prepared as? DiscoveryPreparedPool.Fresh)?.suggestions ?: emptyList()
                published.clear()
                published.addAll(items)
                generation++
                DiscoveryPublishReceipt.Published(
                    DiscoveryPublicationSnapshot(
                        mediaType = token.mediaType,
                        generation = generation,
                        projectionVersion = 1L,
                        configurationKey = token.configurationKey,
                        desiredConfigurationKey = token.configurationKey,
                        publishedByRequestId = token.requestId,
                        committedAt = 1L,
                        outcome = if (prepared is DiscoveryPreparedPool.Fresh && prepared.partial) {
                            DiscoveryPublicationOutcome.PARTIAL
                        } else {
                            DiscoveryPublicationOutcome.READY
                        },
                        suggestions = items,
                        eligibleSuggestions = items,
                        homeBatchKeys = emptyList(),
                        homeBatchVersion = 0L,
                        issuedKeys = emptySet(),
                        traversal = DiscoveryTraversalState.VALID,
                        cursors = emptyList(),
                    ),
                )
            }
        }
}
