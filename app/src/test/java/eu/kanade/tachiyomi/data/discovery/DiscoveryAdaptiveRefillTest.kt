package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import java.io.IOException

class DiscoveryAdaptiveRefillTest {
    private val media = DiscoveryMediaType.MANGA
    private val policy = DiscoveryCandidatePolicy(GenreFilterSpec(requiredGenres = setOf("Fantasy")))

    private fun item(title: String, genres: List<String> = emptyList()) = DiscoveryRowItem(
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = null,
        reason = null,
        seedTitle = null,
        provider = "fixture",
        score = 1.0,
        sourceId = 7L,
        sourceUrl = "/$title",
        genres = genres,
    )

    private fun page(
        items: List<DiscoveryRowItem>,
        verified: Int = items.count { it.genres.isNotEmpty() },
        unattempted: Int = items.count { it.genres.isEmpty() },
        metadataUnavailable: Int = 0,
        failures: Int = 0,
        hasNextPage: Boolean = true,
        pending: List<DiscoveryRowItem> = emptyList(),
        detailsUsed: Int = 0,
    ) = DiscoveryGenrePage(
        items = items,
        verified = verified,
        unattempted = unattempted,
        metadataUnavailable = metadataUnavailable,
        transientFailures = failures,
        hasNextPage = hasNextPage,
        pending = pending,
        detailsUsed = detailsUsed,
    )

    private class Catalog(
        private val answer: suspend (Long, Int) -> DiscoveryGenrePage,
    ) : DiscoverySourceCatalog {
        val allocations = mutableMapOf<Long, MutableList<Int>>()
        val pageCalls = mutableListOf<Pair<Long, Int>>()

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

        override suspend fun popularWithGenresAllocated(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            genres: List<String>,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
            budget: DiscoveryVerificationBudget?,
            maxDetails: Int,
        ): DiscoveryGenrePage {
            allocations.getOrPut(sourceId) { mutableListOf() }.add(maxDetails)
            pageCalls += sourceId to page
            return answer(sourceId, page)
        }
    }

    private suspend fun query(
        catalog: DiscoverySourceCatalog,
        sourceIds: List<Long> = listOf(7L),
        page: Int = 1,
        budget: DiscoveryVerificationBudget? = null,
        targetItems: Int = 50,
        timeoutMs: Long = GENRE_SOURCE_TIMEOUT_MS,
    ): List<GenreSourceVerification> = queryDiscoveryGenreSources(
        catalog = catalog,
        media = media,
        sourceIds = sourceIds,
        genres = listOf("Fantasy"),
        page = page,
        releaseStatuses = emptySet(),
        policy = policy,
        timeoutMs = timeoutMs,
        targetItems = targetItems,
        budget = budget,
    )

    @Test
    fun `fair allocation gives every source a share of the details allowance`() {
        val allocation = allocateDetailAllowance((1L..10L).toList(), 48)

        allocation.size shouldBe 10
        allocation.values.all { it in 4..5 } shouldBe true
        allocation.values.sum() shouldBe 48
    }

    @Test
    fun `all selected sources receive a bounded allowance including the late productive one`() = runTest {
        val budget = DiscoveryVerificationBudget(maxDetails = 48)
        val catalog = Catalog { id, _ ->
            if (id == 10L) page(listOf(item("Late", listOf("Fantasy")))) else page(emptyList())
        }

        val results = query(catalog, sourceIds = (1L..10L).toList(), budget = budget)

        catalog.allocations.size shouldBe 10
        // The productive source is queried again for its next page, so compare initial shares.
        catalog.allocations.values.map { it.first() }.sum() shouldBe 48
        catalog.allocations.values.flatten().all { it in 4..5 } shouldBe true
        results.last().summary.items.single().title shouldBe "Late"
    }

    @Test
    fun `source timeout is split across concurrency waves`() {
        val budget = DiscoveryVerificationBudget(budgetMs = 36_000L, clock = { 0L })

        genreSourceTimeoutMs(budget, sourceCount = 10, maxConcurrency = 4) shouldBe 12_000L
        genreSourceTimeoutMs(budget, sourceCount = 2, maxConcurrency = 4) shouldBe 30_000L
        genreSourceTimeoutMs(null, sourceCount = 10, maxConcurrency = 4) shouldBe GENRE_SOURCE_TIMEOUT_MS
    }

    @Test
    fun `admitted items accumulate across pages instead of replacing the previous page`() = runTest {
        val catalog = Catalog { _, pageNumber ->
            if (pageNumber == 1) {
                page((1..3).map { item("first$it", listOf("Fantasy")) })
            } else {
                page((1..5).map { item("second$it", listOf("Fantasy")) }, hasNextPage = false)
            }
        }

        val summary = query(catalog).single().summary

        summary.items.size shouldBe 8
        summary.items.map { it.title }.distinct().size shouldBe 8
    }

    @Test
    fun `reaching the target stops before the next page`() = runTest {
        val catalog = Catalog { _, _ ->
            page((1..10).map { item("card$it", listOf("Fantasy")) })
        }

        val summary = query(catalog, targetItems = 5).single().summary

        summary.items.size shouldBe 5
        catalog.pageCalls.map { it.second } shouldBe listOf(1)
    }

    @Test
    fun `an empty page without a continuation is source exhausted`() = runTest {
        val catalog = Catalog { _, _ -> page(emptyList(), hasNextPage = false) }

        val result = query(catalog).single()

        result.stopReason shouldBe DiscoveryFillStopReason.SOURCE_EXHAUSTED
        result.exhausted shouldBe true
        catalog.pageCalls.map { it.second } shouldBe listOf(1)
    }

    @Test
    fun `page five continuation is requested once and never reset to page one`() = runTest {
        val catalog = Catalog { _, _ -> page(emptyList(), hasNextPage = false) }

        val result = query(catalog, page = 5).single()

        catalog.pageCalls.map { it.second } shouldBe listOf(5)
        result.stopReason shouldBe DiscoveryFillStopReason.SOURCE_EXHAUSTED
    }

    @Test
    fun `budget expiry is a pass stop and not source exhaustion`() = runTest {
        var now = 0L
        val budget = DiscoveryVerificationBudget(budgetMs = 1_000L, clock = { now })
        val catalog = Catalog { _, _ ->
            now = 5_000L
            page(listOf(item("Unknown")), hasNextPage = true)
        }

        val result = query(catalog, budget = budget).single()

        result.stopReason shouldBe DiscoveryFillStopReason.PASS_BUDGET_EXHAUSTED
        result.exhausted shouldBe false
    }

    @Test
    fun `transport failure is reported as retry after`() = runTest {
        val budget = DiscoveryVerificationBudget(maxDetails = 48)
        val result = query(
            Catalog { _, _ -> throw IOException("offline") },
            budget = budget,
        ).single()

        result.stopReason shouldBe DiscoveryFillStopReason.RETRY_AFTER
        result.failure?.reason shouldBe GenreSourceFailure.SOURCE_ERROR
    }

    @Test
    fun `hydration reports pending cards and charged lookups`() = runTest {
        val budget = DiscoveryVerificationBudget(maxDetails = 48)
        val rows = (1..30).map { item("card$it") }
        var requests = 0

        val result = DiscoveryGenreHydrator().hydrate(media, 7L, rows, budget, maxDetails = 8) {
            requests++
            listOf("Fantasy")
        }

        requests shouldBe 8
        result.detailsUsed shouldBe 8
        result.verified shouldBe 8
        result.pending.size shouldBe 22
        result.unattempted shouldBe 22
        budget.detailsUsed shouldBe 8
    }

    @Test
    fun `settled batches drain pending cards until the source allowance is spent`() = runTest {
        val budget = DiscoveryVerificationBudget(maxDetails = 48)
        val rows = (1..30).map { item("card$it") }
        val hydrator = DiscoveryGenreHydrator()

        val settled = hydrateDiscoveryBatchUntilSettled(rows, maxDetails = 16) { batch, allowance ->
            hydrator.hydrate(media, 7L, batch, budget, maxDetails = allowance) {
                listOf("Fantasy")
            }
        }

        settled.detailsUsed shouldBe 16
        settled.verified shouldBe 16
        settled.pending.size shouldBe 14
        settled.unattempted shouldBe 14
        settled.items.size shouldBe 30
        settled.items.count { it.genres.isNotEmpty() } shouldBe 16
    }

    @Test
    fun `blank urls are unattempted but never pending`() = runTest {
        val budget = DiscoveryVerificationBudget(maxDetails = 48)
        val blank = (1..3).map { item("blank$it").copy(sourceUrl = null) }
        val valid = (1..5).map { item("valid$it") }
        var requests = 0

        val result = DiscoveryGenreHydrator().hydrate(media, 7L, blank + valid, budget, maxDetails = 5) {
            requests++
            listOf("Fantasy")
        }

        requests shouldBe 5
        result.detailsUsed shouldBe 5
        result.pending shouldBe emptyList()
        result.unattempted shouldBe 3
    }

    @Test
    fun `zero allowance makes no request and keeps valid cards pending`() = runTest {
        val budget = DiscoveryVerificationBudget(maxDetails = 48)
        val rows = (1..5).map { item("valid$it") }
        var requests = 0

        val result = DiscoveryGenreHydrator().hydrate(media, 7L, rows, budget, maxDetails = 0) {
            requests++
            listOf("Fantasy")
        }

        requests shouldBe 0
        result.detailsUsed shouldBe 0
        result.pending.size shouldBe 5
        result.unattempted shouldBe 5
    }

    @Test
    fun `cached metadata is verified without charging a lookup`() = runTest {
        val hydrator = DiscoveryGenreHydrator()
        val rows = listOf(item("cached"))
        hydrator.hydrate(media, 7L, rows, DiscoveryVerificationBudget(maxDetails = 1)) {
            listOf("Fantasy")
        }

        val result = hydrator.hydrate(media, 7L, rows, DiscoveryVerificationBudget(maxDetails = 1), maxDetails = 0) {
            error("cache hit must not request details")
        }

        result.verified shouldBe 1
        result.detailsUsed shouldBe 0
        result.pending shouldBe emptyList()
    }
}
