package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus

class DiscoveryRowMergeTest {
    private val media = DiscoveryMediaType.MANGA
    private val policy = DiscoveryCandidatePolicy(GenreFilterSpec(requiredGenres = setOf("Fantasy")))

    private fun item(
        title: String,
        genres: List<String> = listOf("Fantasy"),
        sourceId: Long = 7L,
    ) = DiscoveryRowItem(
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = null,
        reason = null,
        seedTitle = null,
        provider = "fixture",
        score = 1.0,
        sourceId = sourceId,
        sourceUrl = "/$title",
        genres = genres,
    )

    private fun page(items: List<DiscoveryRowItem>) = DiscoveryGenrePage(
        items = items,
        verified = items.count { it.genres.isNotEmpty() },
        unattempted = items.count { it.genres.isEmpty() },
        metadataUnavailable = 0,
        transientFailures = 0,
        hasNextPage = false,
    )

    private class Catalog(
        private val answer: (Long, Int) -> DiscoveryGenrePage,
    ) : DiscoverySourceCatalog {
        override suspend fun popular(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryRowItem> = emptyList()

        override suspend fun latest(
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
        ): List<DiscoveryRowItem> = answer(sourceId, page).items

        override suspend fun popularWithGenresVerified(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            genres: List<String>,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
            budget: DiscoveryVerificationBudget?,
        ): DiscoveryGenrePage = answer(sourceId, page)
    }

    private fun context(existing: List<DiscoveryRowItem>) = DiscoveryBuildContext(
        mediaType = media,
        seeds = emptyList(),
        libraryCleanTitles = emptySet(),
        historyCleanTitles = emptySet(),
        hiddenCleanTitles = emptySet(),
        requiredGenres = setOf("Fantasy"),
        sourceIds = listOf(7L),
        existingRowItems = mapOf(tachiyomi.domain.discovery.model.DiscoveryRowType.SOURCE to existing),
    )

    @Test
    fun `a thin refresh keeps compatible existing cards in front of new ones`() = runTest {
        val existing = (1..7).map { item("Old$it") }
        val result = DiscoverySourceRowBuilder(
            Catalog { _, _ -> page(listOf(item("New"))) },
        ).build(context(existing))

        result.size shouldBe 8
        result.take(7).map { it.title } shouldBe existing.map { it.title }
        result.last().title shouldBe "New"
    }

    @Test
    fun `existing cards that no longer match required genres are dropped`() = runTest {
        val existing = listOf(item("Wrong", genres = listOf("Drama")))
        val result = DiscoverySourceRowBuilder(
            Catalog { _, _ -> page(listOf(item("New"))) },
        ).build(context(existing))

        result.map { it.title } shouldBe listOf("New")
    }

    @Test
    fun `merge never exceeds the row cap and deduplicates titles`() = runTest {
        val existing = (1..7).map { item("Old$it") }
        val fresh = listOf(item("Old1"), item("New"))
        val merged = mergeExistingRowItems(existing, fresh, policy, cap = 8)

        merged.size shouldBe 8
        merged.count { it.title == "Old1" } shouldBe 1
        merged.last().title shouldBe "New"
    }

    @Test
    fun `existing cards from deselected sources are not preserved`() = runTest {
        val existing = (1..3).map { item("Other$it", sourceId = 99L) }
        val merged = mergeExistingRowItems(
            existing = existing,
            fresh = listOf(item("New")),
            policy = policy,
            cap = 20,
            sourceIds = setOf(7L),
        )

        merged.map { it.title } shouldBe listOf("New")
    }
}
