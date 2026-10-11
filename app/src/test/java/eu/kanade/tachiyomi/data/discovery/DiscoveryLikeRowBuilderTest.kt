package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.data.suggestions.SuggestionCoordinator
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus

class DiscoveryLikeRowBuilderTest {

    private class FakeCatalog(
        private val items: List<DiscoveryRowItem> = emptyList(),
        private val delayMs: Long = 0L,
    ) : DiscoverySourceCatalog {
        val currentConcurrent = java.util.concurrent.atomic.AtomicInteger(0)
        val maxConcurrent = java.util.concurrent.atomic.AtomicInteger(0)

        override suspend fun popular(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryRowItem> = emptyList()

        override suspend fun popularWithGenres(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            genres: List<String>,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryRowItem> {
            val cur = currentConcurrent.incrementAndGet()
            maxConcurrent.updateAndGet { maxOf(it, cur) }
            try {
                if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
                return items
            } finally {
                currentConcurrent.decrementAndGet()
            }
        }

        override suspend fun latest(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryRowItem> = emptyList()
    }

    @Test
    fun `includeExternal false queries catalog with seed genres and attaches seedTitle`() = runTest {
        val catalog = FakeCatalog(
            items = listOf(
                DiscoveryRowItem("Rec Title", "rec title", null, null, null, "Catalog", 0.9),
            ),
        )
        val builder = DiscoveryLikeRowBuilder(
            suggestionCoordinator = SuggestionCoordinator(),
            catalog = catalog,
            includeExternal = false,
        )
        val context = DiscoveryBuildContext(
            mediaType = DiscoveryMediaType.MANGA,
            seeds = emptyList(),
            feedbackSeeds = listOf(
                DiscoveryFeedbackSeed(
                    title = "Seed Title",
                    cleanTitle = "seed title",
                    genres = listOf("Action", "Fantasy"),
                ),
            ),
            libraryCleanTitles = emptySet(),
            historyCleanTitles = emptySet(),
            hiddenCleanTitles = emptySet(),
            sourceId = 1L,
            sourceIds = listOf(1L),
        )
        val results = builder.build(context)
        results.size shouldBe 1
        results.first().title shouldBe "Rec Title"
        results.first().seedTitle shouldBe "Seed Title"
    }

    @Test
    fun `multiple seeds are queried concurrently`() = runTest {
        val catalog = FakeCatalog(
            items = listOf(
                DiscoveryRowItem("Rec Title", "rec title", null, null, null, "Catalog", 0.9),
            ),
            delayMs = 50L,
        )
        val builder = DiscoveryLikeRowBuilder(
            suggestionCoordinator = SuggestionCoordinator(),
            catalog = catalog,
            includeExternal = false,
        )
        val context = DiscoveryBuildContext(
            mediaType = DiscoveryMediaType.MANGA,
            seeds = emptyList(),
            feedbackSeeds = listOf(
                DiscoveryFeedbackSeed("Seed 1", "seed 1", listOf("Action")),
                DiscoveryFeedbackSeed("Seed 2", "seed 2", listOf("Comedy")),
                DiscoveryFeedbackSeed("Seed 3", "seed 3", listOf("Drama")),
            ),
            libraryCleanTitles = emptySet(),
            historyCleanTitles = emptySet(),
            hiddenCleanTitles = emptySet(),
            sourceId = 1L,
            sourceIds = listOf(1L, 2L),
        )
        val results = builder.build(context)
        catalog.maxConcurrent.get() shouldBe io.kotest.matchers.ints.beGreaterThan(1)
        results.isNotEmpty() shouldBe true
    }
}
