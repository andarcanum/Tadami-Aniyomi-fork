package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import java.io.IOException

class DiscoveryConfigurationPipelineTest {
    private val media = DiscoveryMediaType.NOVEL
    private val row = DiscoveryRowType.SOURCE

    private fun cached() = DiscoverySuggestion(
        id = 0L,
        mediaType = media,
        rowType = row,
        title = "Cached Match",
        cleanTitle = "cached match",
        coverUrl = "cover",
        reason = null,
        seedTitle = null,
        provider = "fixture",
        score = 1.0,
        position = 0L,
        createdAt = 1L,
        sourceId = 7L,
        sourceUrl = "/match",
        genres = listOf("Fantasy"),
    )

    private class Fixture(
        val runner: DiscoveryRunner,
        val preferences: DiscoveryPreferences,
        val stored: MutableStateFlow<List<DiscoverySuggestion>>,
        val captured: MutableList<DiscoveryBuildContext>,
    )

    private fun fixture(
        degraded: Boolean = false,
        empty: Boolean = false,
        failed: Boolean = false,
        cachedGenres: List<String> = listOf("Fantasy"),
        targetRow: DiscoveryRowType = row,
        cacheBound: Boolean = true,
    ): Fixture {
        val preferences = DiscoveryPreferences(PersistingPreferenceStore())
        preferences.rowLikeEnabled().set(false)
        preferences.rowTrendEnabled().set(false)
        preferences.rowTasteEnabled().set(targetRow == DiscoveryRowType.TASTE)
        preferences.rowSourceEnabled().set(targetRow == DiscoveryRowType.SOURCE)
        preferences.externalProvidersEnabled().set(false)
        preferences.requiredGenres().set("fantasy")
        preferences.manualRefreshCount(media).set(7)
        preferences.backgroundCycleCount(media).set(9)
        val initial = cached().copy(
            rowType = targetRow,
            genres = cachedGenres,
            provider = if (cacheBound) "fixture" else "anilist_genre",
            sourceId = if (cacheBound) 7L else null,
            sourceUrl = if (cacheBound) "/match" else null,
        )
        val stored = MutableStateFlow(listOf(initial))
        val captured = mutableListOf<DiscoveryBuildContext>()
        var generation = 0L
        val repository = object : FakeDiscoveryRepositoryBase() {
            override fun subscribe(
                mediaType: DiscoveryMediaType,
            ): kotlinx.coroutines.flow.Flow<List<DiscoverySuggestion>> =
                stored

            override suspend fun getSignals(mediaType: DiscoveryMediaType) =
                emptyList<tachiyomi.domain.discovery.model.DiscoverySignal>()

            override suspend fun getHiddenTitles(mediaType: DiscoveryMediaType) = emptySet<String>()
            override suspend fun getBlacklistedTags(mediaType: DiscoveryMediaType) = emptySet<String>()
            override suspend fun getRecentExclusionsWithTimestamp(
                mediaType: DiscoveryMediaType,
                windowMillis: Long,
            ): Map<String, Long> = mapOf("cached match" to 1L)
            override suspend fun getShownWithCount(
                mediaType: DiscoveryMediaType,
                windowMillis: Long,
            ): List<Triple<String, Long, Int>> = emptyList()
            override suspend fun lastUpdatedAt(mediaType: DiscoveryMediaType): Long? = null

            override suspend fun publication(media: DiscoveryMediaType) =
                emptyPublication(media).copy(generation = generation, suggestions = stored.value)

            override suspend fun reserveGeneration(
                media: DiscoveryMediaType,
                requestId: String,
                expectedGeneration: Long,
                configurationKey: String,
            ): tachiyomi.domain.discovery.model.DiscoveryGenerationToken =
                tachiyomi.domain.discovery.model.DiscoveryGenerationToken(
                    mediaType = media,
                    requestId = requestId,
                    ownerRevision = 1L,
                    expectedGeneration = expectedGeneration,
                    configurationKey = configurationKey,
                )

            override suspend fun publishGeneration(
                token: tachiyomi.domain.discovery.model.DiscoveryGenerationToken,
                prepared: tachiyomi.domain.discovery.model.DiscoveryPreparedPool,
            ): tachiyomi.domain.discovery.model.DiscoveryPublishReceipt {
                generation = token.expectedGeneration + 1
                val suggestions =
                    (prepared as? tachiyomi.domain.discovery.model.DiscoveryPreparedPool.Fresh)
                        ?.suggestions.orEmpty()
                stored.value = suggestions
                return tachiyomi.domain.discovery.model.DiscoveryPublishReceipt.Published(
                    emptyPublication(token.mediaType).copy(generation = generation, suggestions = suggestions),
                )
            }
        }
        val sourceBuilder = object : DiscoveryRowBuilder {
            override val rowType = targetRow
            override val isDegraded = degraded
            override val exhaustionEvidence = if (degraded || failed) {
                null
            } else {
                listOf(
                    tachiyomi.domain.discovery.model.DiscoveryQueryEvidence(
                        queryKey = "fixture-finite-catalogue",
                        transportOk = true,
                        rawEnd = true,
                        pending = 0,
                        unattempted = 0,
                        metadataFailures = 0,
                        budgetStopped = false,
                        duplicatePageStopped = false,
                    ),
                )
            }
            override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
                captured += context
                if (failed) throw IOException("source failed")
                if (empty) return emptyList()
                return listOf(
                    DiscoveryRowItem(
                        "Cached Match", "cached match", "cover", null, null, "fixture", 1.0,
                        sourceId = 7L, sourceUrl = "/match", genres = listOf("Fantasy"),
                    ),
                )
            }
        }
        val runner = DiscoveryRunner(
            repository = repository,
            preferences = preferences,
            seedSources = object : DiscoverySeedSources {
                override suspend fun candidates(mediaType: DiscoveryMediaType) = emptyList<DiscoverySeedInput>()
                override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = emptySet<String>()
            },
            coordinatorFactory = { DiscoveryCoordinator(listOf(sourceBuilder)) },
            trendingFactory = { mockk(relaxed = true) },
            sourcePreferencesProvider = { SourcePreferences(PersistingPreferenceStore()) },
            installedPluginsProvider = { emptyList() },
            fallbackSourceIdProvider = { -1L },
        )
        return Fixture(runner, preferences, stored, captured)
    }

    @Test
    fun `configuration refresh replaces the old pool instead of retaining it`(): Unit = runTest {
        val f = fixture(cachedGenres = emptyList())
        val outcome = f.runner.run(listOf(media), isConfigurationRefresh = true).getValue(media)
        // The only candidate is the old pool title, so the honest new pool is empty.
        outcome.status shouldBe DiscoveryRunStatus.SUCCESS
        outcome.persistedRows shouldBe emptySet()
        f.stored.value shouldBe emptyList()
        f.captured.single().currentFeedCleanTitles shouldBe setOf("cached match")
        f.captured.single().pageOffset shouldBe 1
        f.captured.single().harvestPages shouldBe 1
        f.preferences.manualRefreshCount(media).get() shouldBe 7
        f.preferences.backgroundCycleCount(media).get() shouldBe 9
    }

    @Test
    fun `manual rotation still excludes the old candidate window`(): Unit = runTest {
        val f = fixture()
        f.runner.run(listOf(media), isManualRefresh = true)
        f.captured.single().currentFeedCleanTitles shouldBe setOf("cached match")
        f.captured.single().pageOffset shouldBe 1
        // The old title is filtered out at selection time, so the honest result is an
        // exhausted (empty) pool rather than a stale backfill.
        f.stored.value shouldBe emptyList()
        f.preferences.manualRefreshCount(media).get() shouldBe 8
    }

    @Test
    fun `incomplete empty required row keeps cache through the real runner cleanup`(): Unit = runTest {
        val f = fixture(degraded = true, empty = true)
        val before = f.stored.value
        val outcome = f.runner.run(listOf(media), isConfigurationRefresh = true).getValue(media)
        outcome.status shouldBe DiscoveryRunStatus.PARTIAL
        outcome.degradedRows shouldBe setOf(row)
        f.stored.value shouldBe before
    }

    @Test
    fun `incomplete plugin taste preserves bound cached candidates`(): Unit = runTest {
        val f = fixture(degraded = true, empty = true, targetRow = DiscoveryRowType.TASTE)
        val before = f.stored.value
        f.runner.run(listOf(media), isConfigurationRefresh = true)
        f.stored.value shouldBe before
    }

    @Test
    fun `incomplete generation keeps the atomic pool unchanged`(): Unit = runTest {
        val f = fixture(degraded = true, empty = true, targetRow = DiscoveryRowType.TASTE, cacheBound = false)
        val before = f.stored.value
        f.runner.run(listOf(media), isConfigurationRefresh = true)
        f.stored.value shouldBe before
    }

    @Test
    fun `conclusive empty required row can clear cache`(): Unit = runTest {
        val f = fixture(empty = true)
        val outcome = f.runner.run(listOf(media), isConfigurationRefresh = true).getValue(media)
        outcome.status shouldBe DiscoveryRunStatus.SUCCESS
        f.stored.value shouldBe emptyList()
    }

    @Test
    fun `failed required row keeps cache`(): Unit = runTest {
        val f = fixture(failed = true)
        val before = f.stored.value
        val outcome = f.runner.run(listOf(media), isConfigurationRefresh = true).getValue(media)
        outcome.status shouldBe DiscoveryRunStatus.FAILED
        f.stored.value shouldBe before
    }

    @Test
    fun `missing and degraded rows never count as conclusive empty`() {
        DiscoveryFeed(media, emptyMap(), emptySet(), generatedAt = 1L).isConclusiveEmpty(row) shouldBe false
        DiscoveryFeed(media, mapOf(row to emptyList()), emptySet(), setOf(row), 1L)
            .isConclusiveEmpty(row) shouldBe false
        DiscoveryFeed(media, mapOf(row to emptyList()), setOf(row), generatedAt = 1L)
            .isConclusiveEmpty(row) shouldBe false
        DiscoveryFeed(media, mapOf(row to emptyList()), emptySet(), generatedAt = 1L)
            .isConclusiveEmpty(row) shouldBe true
    }
}
