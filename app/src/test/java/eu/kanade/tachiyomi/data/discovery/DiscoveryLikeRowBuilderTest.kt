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
    ) : DiscoverySourceCatalog {
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
        ): List<DiscoveryRowItem> = items

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
}
