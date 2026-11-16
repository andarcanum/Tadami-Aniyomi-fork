package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import java.util.concurrent.atomic.AtomicLong

class DiscoverySourceSuccessEvidenceTest {

    @Test
    fun `SOURCE reports actual successes then clears them when the next build is budget skipped`() = runTest {
        val clock = AtomicLong(0L)
        val budget = DiscoveryVerificationBudget(budgetMs = 10, maxDetails = 0, clock = { clock.get() })
        val catalog = EvidenceCatalog()
        val builder = DiscoverySourceRowBuilder(catalog, rowCap = 4)

        builder.build(context(budget))
        builder.successfulSourceIds shouldBe setOf(11L)
        catalog.browseRequests shouldBe 2

        clock.set(10L)
        builder.build(context(budget))

        builder.successfulSourceIds shouldBe emptySet()
        catalog.browseRequests shouldBe 2
    }

    @Test
    fun `budget skipped genre query has zero successful pages and cannot reward reliability`() = runTest {
        val clock = AtomicLong(0L)
        val budget = DiscoveryVerificationBudget(budgetMs = 10, maxDetails = 0, clock = { clock.get() })
        val catalog = EvidenceCatalog()
        clock.set(10L)

        val result = verify(catalog, budget).single()

        result.successfulPages shouldBe 0
        result.canRewardReliability shouldBe false
        result.summary.outcome shouldBe GenreVerificationOutcome.INCOMPLETE
        catalog.genreRequests shouldBe 0
    }

    @Test
    fun `actual terminal empty genre page is successful transport evidence without any returned cards`() = runTest {
        val catalog = EvidenceCatalog()
        val budget = DiscoveryVerificationBudget(budgetMs = 10, maxDetails = 0, clock = { 0L })

        val result = verify(catalog, budget).single()

        result.successfulPages shouldBe 1
        result.summary.outcome shouldBe GenreVerificationOutcome.VERIFIED_EMPTY
        result.canRewardReliability shouldBe true
        catalog.genreRequests shouldBe 1
    }

    @Test
    fun `returned genre page with incomplete metadata cannot reward reliability or SOURCE success`() = runTest {
        val catalog = EvidenceCatalog(
            genrePage = DiscoveryGenrePage(
                items = listOf(rowItem()),
                verified = 1,
                unattempted = 0,
                metadataUnavailable = 1,
                transientFailures = 0,
                hasNextPage = false,
            ),
        )
        val budget = DiscoveryVerificationBudget(budgetMs = 10, maxDetails = 0, clock = { 0L })

        val result = verify(catalog, budget).single()
        result.successfulPages shouldBe 1
        result.summary.hasCoverageGap shouldBe true
        result.canRewardReliability shouldBe false

        val builder = DiscoverySourceRowBuilder(catalog, rowCap = 4)
        builder.build(context(budget, required = true))
        builder.successfulSourceIds shouldBe emptySet()
        builder.degradedSourceIds shouldBe setOf(11L)
    }

    @Test
    fun `SOURCE does not reward unattempted metadata even after successful terminal browse pages`() = runTest {
        val catalog = EvidenceCatalog(listGenres = emptyList())
        val budget = DiscoveryVerificationBudget(budgetMs = 10, maxDetails = 0, clock = { 0L })
        val builder = DiscoverySourceRowBuilder(catalog, rowCap = 4)

        builder.build(context(budget))

        catalog.browseRequests shouldBe 2
        builder.successfulSourceIds shouldBe emptySet()
        builder.degradedSourceIds shouldBe setOf(11L)
    }

    @Test
    fun `SOURCE rewards only the active source when another selected source receives no slot`() = runTest {
        val catalog = EvidenceCatalog()
        val budget = DiscoveryVerificationBudget(budgetMs = 10, maxDetails = 0, clock = { 0L })
        val builder = DiscoverySourceRowBuilder(catalog, rowCap = 1)

        builder.build(
            context(budget).copy(
                sourceIds = listOf(11L, 22L),
                sourcePolicy = discoveryQueryPolicyFixture(DiscoveryMediaType.MANGA, listOf(11L, 22L)),
            ),
        )

        builder.successfulSourceIds shouldBe setOf(11L)
        catalog.browseRequests shouldBe 2
    }

    @Test
    fun `TASTE reports verified source successes and clears them on a budget skipped rebuild`() = runTest {
        val catalog = EvidenceCatalog(
            genrePage = DiscoveryGenrePage(
                items = listOf(rowItem()),
                verified = 1,
                unattempted = 0,
                metadataUnavailable = 0,
                transientFailures = 0,
                hasNextPage = false,
            ),
        )
        val clock = AtomicLong(0L)
        val budget = DiscoveryVerificationBudget(budgetMs = 10, maxDetails = 0, clock = { clock.get() })
        val builder =
            DiscoveryTasteRowBuilder(unusedTrending(), catalog, { TrendSort.POPULARITY }, includeExternal = false)

        builder.build(context(budget, required = true))
        builder.successfulSourceIds shouldBe setOf(11L)
        catalog.genreRequests shouldBe 1

        clock.set(10L)
        builder.build(context(budget, required = true))
        builder.successfulSourceIds shouldBe emptySet()
        catalog.genreRequests shouldBe 1
    }

    private fun unusedTrending() = object : DiscoveryTrendingSource {
        override suspend fun fetch(
            mediaType: DiscoveryMediaType,
            season: TrendSeason,
            sort: TrendSort,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryTrendingItem> = error("External discovery is disabled")

        override suspend fun fetchByGenres(
            mediaType: DiscoveryMediaType,
            genres: List<String>,
            sort: TrendSort,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryTrendingItem> = error("External discovery is disabled")

        override suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType): DiscoveryMeta? = null
    }

    private suspend fun verify(catalog: DiscoverySourceCatalog, budget: DiscoveryVerificationBudget) =
        queryDiscoveryGenreSources(
            catalog = catalog,
            media = DiscoveryMediaType.MANGA,
            sourceIds = listOf(11L),
            genres = listOf("Action"),
            page = 1,
            releaseStatuses = emptySet(),
            policy = DiscoveryCandidatePolicy(requiredGenres = setOf("Action"), strictRequired = true),
            targetItems = 4,
            budget = budget,
        )

    private fun context(budget: DiscoveryVerificationBudget, required: Boolean = false) = DiscoveryBuildContext(
        mediaType = DiscoveryMediaType.MANGA,
        seeds = emptyList(),
        libraryCleanTitles = emptySet(),
        historyCleanTitles = emptySet(),
        hiddenCleanTitles = emptySet(),
        sourceId = 11L,
        sourceIds = listOf(11L),
        sourcePolicy = discoveryQueryPolicyFixture(DiscoveryMediaType.MANGA, listOf(11L)),
        verificationBudget = budget,
        genreFilterSpec = if (required) GenreFilterSpec(requiredGenres = setOf("Action")) else GenreFilterSpec(),
    )

    private fun rowItem() = DiscoveryRowItem(
        title = "Observed Source Manga",
        cleanTitle = "observed source manga",
        coverUrl = null,
        reason = null,
        seedTitle = null,
        provider = "evidence-catalogue",
        score = 1.0,
        sourceId = 11L,
        sourceUrl = "/observed/11",
        genres = listOf("Action"),
    )

    private class EvidenceCatalog(
        private val listGenres: List<String> = listOf("Action"),
        private val genrePage: DiscoveryGenrePage = DiscoveryGenrePage(
            items = emptyList(),
            verified = 0,
            unattempted = 0,
            metadataUnavailable = 0,
            transientFailures = 0,
            hasNextPage = false,
        ),
    ) : DiscoverySourceCatalog {
        var browseRequests = 0
            private set
        var genreRequests = 0
            private set

        override suspend fun latestPage(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
            budget: DiscoveryVerificationBudget?,
        ): DiscoveryBrowsePage {
            browseRequests++
            return DiscoveryBrowsePage(
                items = listOf(
                    DiscoveryRowItem(
                        title = "Observed Source Manga",
                        cleanTitle = "observed source manga",
                        coverUrl = null,
                        reason = null,
                        seedTitle = null,
                        provider = "evidence-catalogue",
                        score = 1.0,
                        sourceId = sourceId,
                        sourceUrl = "/observed/$sourceId",
                        genres = listGenres,
                    ),
                ),
                hasNextPage = false,
            )
        }

        override suspend fun popularPage(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
            budget: DiscoveryVerificationBudget?,
        ): DiscoveryBrowsePage {
            browseRequests++
            return DiscoveryBrowsePage(emptyList(), hasNextPage = false)
        }

        override suspend fun popularWithGenresVerified(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            genres: List<String>,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
            budget: DiscoveryVerificationBudget?,
        ): DiscoveryGenrePage {
            genreRequests++
            return genrePage
        }

        override suspend fun latest(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ) = latestPage(mediaType, sourceId, page, releaseStatuses, null).items

        override suspend fun popular(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ) = popularPage(mediaType, sourceId, page, releaseStatuses, null).items

        override suspend fun popularWithGenres(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            genres: List<String>,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ) = popularWithGenresVerified(mediaType, sourceId, genres, page, releaseStatuses, null).items
    }
}
