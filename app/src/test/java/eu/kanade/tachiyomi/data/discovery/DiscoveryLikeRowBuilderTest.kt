package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.data.suggestions.SuggestionCoordinator
import eu.kanade.tachiyomi.data.suggestions.SuggestionFetchResult
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import java.io.IOException

class DiscoveryLikeRowBuilderTest {

    @Test
    fun `partial feedback provider failure returns successful items and marks builder degraded`() = runTest {
        val suggestions = mockk<SuggestionCoordinator>()
        coEvery { suggestions.fetchSuggestions(any(), any(), any()) } returns SuggestionFetchResult(
            items = listOf(
                SuggestionItem(
                    title = "Fresh Pick",
                    thumbnailUrl = null,
                    providerName = "AniList",
                    providerUrl = "https://anilist.co/manga/1",
                    providerId = "1",
                    mediaType = SuggestionMediaType.NOVEL,
                ),
            ),
            attemptedSources = 2,
            failedSources = 1,
        )
        val builder = DiscoveryLikeRowBuilder(suggestions)

        val items = builder.build(externalContext(feedback = true))

        items.map { it.title } shouldBe listOf("Fresh Pick")
        builder.isDegraded shouldBe true
    }

    @Test
    fun `complete provider failure for all seeds throws exception`() = runTest {
        val suggestions = mockk<SuggestionCoordinator>()
        coEvery { suggestions.fetchSuggestions(any(), any(), any()) } returns SuggestionFetchResult(
            items = emptyList(),
            attemptedSources = 2,
            failedSources = 2,
        )
        val builder = DiscoveryLikeRowBuilder(suggestions)

        val failure = runCatching { builder.build(externalContext(feedback = true)) }.exceptionOrNull()

        (failure is IOException) shouldBe true
    }

    @Test
    fun `empty successful providers remain honestly empty for library and feedback seeds`() = runTest {
        val suggestions = mockk<SuggestionCoordinator>()
        coEvery { suggestions.fetchSuggestions(any(), any(), any()) } returns
            SuggestionFetchResult(emptyList(), attemptedSources = 2, failedSources = 0)
        val builder = DiscoveryLikeRowBuilder(suggestions)

        builder.build(externalContext(feedback = false)) shouldBe emptyList()
        builder.build(externalContext(feedback = true)) shouldBe emptyList()
    }

    private fun externalContext(feedback: Boolean) = DiscoveryBuildContext(
        mediaType = DiscoveryMediaType.NOVEL,
        seeds = if (feedback) emptyList() else listOf(DiscoverySeedInput(entryId = 1L, title = "Seed One")),
        feedbackSeeds = if (feedback) listOf(DiscoveryFeedbackSeed("Seed One", "seed one")) else emptyList(),
        libraryCleanTitles = emptySet(),
        historyCleanTitles = emptySet(),
        hiddenCleanTitles = emptySet(),
    )

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

    @Test
    fun `blacklisted tags filter external suggestions in like row builder`() = runTest {
        val suggestions = mockk<SuggestionCoordinator>()
        val item1 =
            SuggestionItem(
                "Clean Like",
                listOf("Clean Like"),
                null,
                "AniList",
                "",
                null,
                eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType.NOVEL,
                genres = listOf("Adventure"),
            )
        val item2 =
            SuggestionItem(
                "Dirty Like",
                listOf("Dirty Like"),
                null,
                "AniList",
                "",
                null,
                eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType.NOVEL,
                genres = listOf("Ecchi"),
            )
        coEvery { suggestions.fetchSuggestions(any(), any(), any()) } returns
            SuggestionFetchResult(listOf(item1, item2), attemptedSources = 1, failedSources = 0)
        val builder = DiscoveryLikeRowBuilder(suggestions)

        val context = externalContext(feedback = false).copy(
            blacklistedTags = setOf("Ecchi"),
        )
        val result = builder.build(context)
        result.map { it.title } shouldBe listOf("Clean Like")
    }
}
