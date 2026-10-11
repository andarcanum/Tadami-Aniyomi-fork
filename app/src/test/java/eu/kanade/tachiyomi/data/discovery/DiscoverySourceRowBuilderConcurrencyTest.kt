package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import java.util.concurrent.atomic.AtomicInteger

class DiscoverySourceRowBuilderConcurrencyTest {

    private class ConcurrencyTrackingCatalog(
        private val delayMs: Long = 50L,
        private val latestItemCount: Int = 3,
    ) : DiscoverySourceCatalog {
        val currentConcurrent = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        val latestCalls = AtomicInteger(0)
        val popularCalls = AtomicInteger(0)

        override suspend fun popular(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryRowItem> {
            popularCalls.incrementAndGet()
            val cur = currentConcurrent.incrementAndGet()
            maxConcurrent.updateAndGet { maxOf(it, cur) }
            try {
                if (delayMs > 0) delay(delayMs)
                return listOf(
                    DiscoveryRowItem("Pop $sourceId", "pop $sourceId", null, null, null, "src$sourceId", 0.5),
                )
            } finally {
                currentConcurrent.decrementAndGet()
            }
        }

        override suspend fun popularWithGenres(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            genres: List<String>,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryRowItem> = emptyList()

        override suspend fun latest(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryRowItem> {
            latestCalls.incrementAndGet()
            val cur = currentConcurrent.incrementAndGet()
            maxConcurrent.updateAndGet { maxOf(it, cur) }
            try {
                if (delayMs > 0) delay(delayMs)
                return (1..latestItemCount).map { idx ->
                    DiscoveryRowItem(
                        "Latest $sourceId #$idx",
                        "latest $sourceId #$idx",
                        null,
                        null,
                        null,
                        "src$sourceId",
                        0.9,
                    )
                }
            } finally {
                currentConcurrent.decrementAndGet()
            }
        }
    }

    @Test
    fun `sources are queried concurrently with concurrency greater than 1`() = runTest {
        val catalog = ConcurrencyTrackingCatalog(delayMs = 50L, latestItemCount = 5)
        val builder = DiscoverySourceRowBuilder(catalog, rowCap = 20)
        val context = DiscoveryBuildContext(
            mediaType = DiscoveryMediaType.MANGA,
            seeds = emptyList(),
            libraryCleanTitles = emptySet(),
            historyCleanTitles = emptySet(),
            hiddenCleanTitles = emptySet(),
            sourceId = 1L,
            sourceIds = listOf(1L, 2L, 3L, 4L, 5L, 6L),
            sourceWeights = mapOf(1L to 1.0, 2L to 1.0, 3L to 1.0, 4L to 1.0, 5L to 1.0, 6L to 1.0),
        )

        val result = builder.build(context)

        catalog.maxConcurrent.get() shouldBeGreaterThan 1
        result.isNotEmpty() shouldBe true
    }

    @Test
    fun `latest and popular are queried concurrently within each source`() = runTest {
        val catalog = ConcurrencyTrackingCatalog(delayMs = 50L, latestItemCount = 5)
        val builder = DiscoverySourceRowBuilder(catalog, rowCap = 10)
        val context = DiscoveryBuildContext(
            mediaType = DiscoveryMediaType.MANGA,
            seeds = emptyList(),
            libraryCleanTitles = emptySet(),
            historyCleanTitles = emptySet(),
            hiddenCleanTitles = emptySet(),
            sourceId = 1L,
            sourceIds = listOf(1L),
            sourceWeights = mapOf(1L to 1.0),
        )

        val result = builder.build(context)

        result.isNotEmpty() shouldBe true
        catalog.latestCalls.get() shouldBe 1
        catalog.popularCalls.get() shouldBe 1
        // Since latest and popular run concurrently via async, maxConcurrent is at least 2 even with 1 source
        catalog.maxConcurrent.get() shouldBeGreaterThan 1
    }

    @Test
    fun `hanging source does not prevent successful results from responsive source`() = runTest {
        val catalog = object : DiscoverySourceCatalog {
            override suspend fun popular(
                mediaType: DiscoveryMediaType,
                sourceId: Long,
                page: Int,
                releaseStatuses: Set<DiscoveryReleaseStatus>,
            ): List<DiscoveryRowItem> = if (sourceId == 1L) {
                delay(10_000L)
                emptyList()
            } else {
                listOf(DiscoveryRowItem("Responsive Pop", "responsive pop", null, null, null, "S2", 0.5))
            }

            override suspend fun popularWithGenres(
                mediaType: DiscoveryMediaType,
                sourceId: Long,
                genres: List<String>,
                page: Int,
                releaseStatuses: Set<DiscoveryReleaseStatus>,
            ): List<DiscoveryRowItem> = emptyList()

            override suspend fun latest(
                mediaType: DiscoveryMediaType,
                sourceId: Long,
                page: Int,
                releaseStatuses: Set<DiscoveryReleaseStatus>,
            ): List<DiscoveryRowItem> = if (sourceId == 1L) {
                delay(10_000L)
                emptyList()
            } else {
                listOf(DiscoveryRowItem("Responsive Latest", "responsive latest", null, null, null, "S2", 0.9))
            }
        }

        val builder = DiscoverySourceRowBuilder(catalog, rowCap = 10, sourceTimeoutMs = 200L)
        val context = DiscoveryBuildContext(
            mediaType = DiscoveryMediaType.MANGA,
            seeds = emptyList(),
            libraryCleanTitles = emptySet(),
            historyCleanTitles = emptySet(),
            hiddenCleanTitles = emptySet(),
            sourceId = 1L,
            sourceIds = listOf(1L, 2L),
            sourceWeights = mapOf(1L to 1.0, 2L to 1.0),
        )

        val result = builder.build(context)

        result.isNotEmpty() shouldBe true
        result.any { it.provider == "S2" } shouldBe true
    }
}
