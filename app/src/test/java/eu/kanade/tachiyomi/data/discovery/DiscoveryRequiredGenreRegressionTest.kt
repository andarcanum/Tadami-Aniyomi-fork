package eu.kanade.tachiyomi.data.discovery

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import java.io.IOException

class DiscoveryRequiredGenreRegressionTest {
    private val media = DiscoveryMediaType.MANGA
    private val policy = DiscoveryCandidatePolicy(GenreFilterSpec(requiredGenres = setOf("Fantasy")))

    private fun item(title: String, genres: List<String> = emptyList()) = DiscoveryRowItem(
        title, title.lowercase(), "cover", null, null, "fixture", 1.0,
        sourceId = 7L, sourceUrl = "/$title", genres = genres,
    )

    private fun page(
        items: List<DiscoveryRowItem>,
        verified: Int = items.count { it.genres.isNotEmpty() },
        unattempted: Int = items.count { it.genres.isEmpty() },
        metadataUnavailable: Int = 0,
        failures: Int = 0,
    ) = DiscoveryGenrePage(items, verified, unattempted, metadataUnavailable, failures)

    private class Catalog(
        val answer: suspend (Long, Int) -> DiscoveryGenrePage,
    ) : DiscoverySourceCatalog {
        val genreCalls = mutableListOf<Long>()
        var unfilteredCalls = 0

        override suspend fun popular(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryRowItem> {
            unfilteredCalls++
            return emptyList()
        }

        override suspend fun latest(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryRowItem> {
            unfilteredCalls++
            return emptyList()
        }

        override suspend fun popularWithGenres(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            genres: List<String>,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryRowItem> {
            genreCalls += sourceId
            return answer(sourceId, page).items
        }

        override suspend fun popularWithGenresVerified(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            genres: List<String>,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
            budget: DiscoveryVerificationBudget?,
        ): DiscoveryGenrePage {
            genreCalls += sourceId
            return answer(sourceId, page)
        }
    }

    private fun context() = DiscoveryBuildContext(
        mediaType = media,
        seeds = emptyList(),
        libraryCleanTitles = emptySet(),
        historyCleanTitles = emptySet(),
        hiddenCleanTitles = emptySet(),
        requiredGenres = setOf("Fantasy"),
        sourceIds = (1L..10L).toList(),
    )

    private fun noExternal() = object : DiscoveryTrendingSource {
        override suspend fun fetch(
            mediaType: DiscoveryMediaType,
            season: TrendSeason,
            sort: TrendSort,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryTrendingItem> = error("external providers must not run")

        override suspend fun fetchByGenres(
            mediaType: DiscoveryMediaType,
            genres: List<String>,
            sort: TrendSort,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryTrendingItem> = error("external providers must not run")

        override suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType): DiscoveryMeta? = null
    }

    @Test
    fun `source row searches all ten selected sources by genre`() = runTest {
        val catalog = Catalog { id, _ ->
            if (id == 7L) page(listOf(item("Match", listOf("Fantasy")))) else page(emptyList())
        }
        val result = DiscoverySourceRowBuilder(catalog).build(context())
        // Adaptive refill may ask a matched source for one more page.
        catalog.genreCalls.distinct().sorted() shouldBe (1L..10L).toList()
        catalog.unfilteredCalls shouldBe 0
        result.map { it.title } shouldBe listOf("Match")
    }

    @Test
    fun `taste row does not truncate the source list at six`() = runTest {
        val catalog = Catalog { id, _ ->
            if (id == 7L) page(listOf(item("Match", listOf("Fantasy")))) else page(emptyList())
        }
        val result = DiscoveryTasteRowBuilder(noExternal(), catalog, { TrendSort.POPULARITY }, false).build(context())
        // Adaptive refill may ask a matched source for one more page.
        catalog.genreCalls.distinct().sorted() shouldBe (1L..10L).toList()
        result.map { it.title } shouldBe listOf("Match")
    }

    @Test
    fun `taste without required keeps the previous fast six-source path`() = runTest {
        val catalog = Catalog { _, _ -> page(emptyList()) }
        val fastContext = context().copy(
            requiredGenres = emptySet(),
            genreFilterSpec = GenreFilterSpec(),
            tasteProfile = listOf("Fantasy" to 1.0),
        )
        DiscoveryTasteRowBuilder(noExternal(), catalog, { TrendSort.POPULARITY }, false).build(fastContext)
        catalog.genreCalls.sorted() shouldBe (1L..6L).toList()
    }

    @Test
    fun `cheap taste path is not starved by an expired verification budget`() = runTest {
        var now = 0L
        val expired = DiscoveryVerificationBudget(budgetMs = 1_000L, clock = { now })
        now = 5_000L
        val catalog = Catalog { _, _ -> page(listOf(item("Plain", listOf("Fantasy")))) }
        val fastContext = context().copy(
            requiredGenres = emptySet(),
            genreFilterSpec = GenreFilterSpec(),
            tasteProfile = listOf("Fantasy" to 1.0),
            verificationBudget = expired,
        )
        val result = DiscoveryTasteRowBuilder(noExternal(), catalog, { TrendSort.POPULARITY }, false)
            .build(fastContext)
        // The cheap path spends no details, so the media deadline must not cancel it.
        catalog.genreCalls.sorted() shouldBe (1L..6L).toList()
        result.map { it.title } shouldBe listOf("Plain")
    }

    @Test
    fun `cheap taste row is not degraded by cards it never had to hydrate`() = runTest {
        val catalog = Catalog { _, _ -> page(emptyList(), verified = 0, unattempted = 3) }
        val fastContext = context().copy(
            requiredGenres = emptySet(),
            genreFilterSpec = GenreFilterSpec(),
            tasteProfile = listOf("Fantasy" to 1.0),
        )
        val builder = DiscoveryTasteRowBuilder(noExternal(), catalog, { TrendSort.POPULARITY }, false)
        builder.build(fastContext) shouldBe emptyList()
        builder.isDegraded shouldBe false
    }

    @Test
    fun `expired media budget skips required verification instead of failing`() = runTest {
        var now = 0L
        val expired = DiscoveryVerificationBudget(budgetMs = 1_000L, clock = { now })
        now = 5_000L
        val catalog = Catalog { _, _ -> page(listOf(item("Match", listOf("Fantasy")))) }
        val result = DiscoverySourceRowBuilder(catalog).build(
            context().copy(verificationBudget = expired),
        )
        catalog.genreCalls shouldBe emptyList()
        result shouldBe emptyList()
    }

    @Test
    fun `details make list items with null genres verifiable without changing binding`() = runTest {
        val hydrator = DiscoveryGenreHydrator()
        val budget = DiscoveryVerificationBudget(maxDetails = 10)
        val original = item("Match")
        val enriched = hydrator.hydrate(media, 7L, listOf(original), budget) { listOf("Fantasy", "Adventure") }
        policy.filterRowCandidates(enriched.items, { it.genres }).single().title shouldBe "Match"
        enriched.items.single().sourceUrl shouldBe original.sourceUrl
        enriched.items.single().coverUrl shouldBe original.coverUrl
        enriched.items.single().sourceId shouldBe original.sourceId
    }

    @Test
    fun `unknown list to details to required source row works for every media type`() = runTest {
        val hydrator = DiscoveryGenreHydrator()
        val budget = DiscoveryVerificationBudget(maxDetails = 100)
        for (kind in DiscoveryMediaType.entries) {
            val catalog = Catalog { id, _ ->
                if (id == 7L) {
                    val hydrated = hydrator.hydrate(kind, id, listOf(item("FromDetails")), budget) {
                        listOf("Fantasy")
                    }
                    DiscoveryGenrePage(
                        items = hydrated.items,
                        verified = hydrated.verified,
                        unattempted = hydrated.unattempted,
                        metadataUnavailable = hydrated.metadataUnavailable,
                        transientFailures = hydrated.transientFailures,
                    )
                } else {
                    page(emptyList())
                }
            }
            val result = DiscoverySourceRowBuilder(catalog).build(context().copy(mediaType = kind))
            result.single().title shouldBe "FromDetails"
            result.single().genres shouldBe listOf("Fantasy")
        }
    }

    @Test
    fun `details concurrency is bounded independently of the number of candidates`() = runTest {
        val budget = DiscoveryVerificationBudget(maxDetails = 100, maxConcurrentDetails = 2)
        val hydrator = DiscoveryGenreHydrator()
        var active = 0
        var peak = 0
        hydrator.hydrate(media, 7L, (1..8).map { item("p$it") }, budget) {
            active++
            peak = maxOf(peak, active)
            delay(10)
            active--
            listOf("Fantasy")
        }
        peak shouldBe 2
        active shouldBe 0
    }

    @Test
    fun `unknown details are never replaced by the requested genre`() = runTest {
        val budget = DiscoveryVerificationBudget(maxDetails = 10)
        val hydrator = DiscoveryGenreHydrator()
        val enriched = hydrator.hydrate(media, 7L, listOf(item("Unknown")), budget) { emptyList() }
        enriched.items.single().genres shouldBe emptyList()
        enriched.metadataUnavailable shouldBe 1
        policy.filterRowCandidates(enriched.items, { it.genres }) shouldBe emptyList()
    }

    @Test
    fun `cached genres are source scoped and do not consume the next hydration budget`() = runTest {
        val budget = DiscoveryVerificationBudget(maxDetails = 2)
        val hydrator = DiscoveryGenreHydrator()
        val rows = (1..4).map { item("item$it") }
        var calls = 0
        val first = hydrator.hydrate(media, 7L, rows, budget) {
            calls++
            listOf("Fantasy")
        }
        first.verified shouldBe 2
        first.unattempted shouldBe 2
        // A new media-run budget: cached cards must not consume its details allowance.
        val secondBudget = DiscoveryVerificationBudget(maxDetails = 2)
        val second = hydrator.hydrate(media, 7L, rows, secondBudget) {
            calls++
            listOf("Fantasy")
        }
        second.verified shouldBe 4
        calls shouldBe 4
        val otherBudget = DiscoveryVerificationBudget(maxDetails = 10)
        val other = hydrator.hydrate(media, 8L, listOf(rows.first().copy(sourceId = 8L)), otherBudget) {
            calls++
            listOf("Drama")
        }
        other.items.single().genres shouldBe listOf("Drama")
        calls shouldBe 5
    }

    @Test
    fun `metadata unavailable uses a short negative cache instead of a permanent one`() = runTest {
        var now = 0L
        var calls = 0
        val hydrator = DiscoveryGenreHydrator(clock = { now })
        val budget = DiscoveryVerificationBudget(maxDetails = 10)
        val rows = listOf(item("Unknown"))

        val first = hydrator.hydrate(media, 7L, rows, budget) {
            calls++
            emptyList()
        }
        first.metadataUnavailable shouldBe 1
        val second = hydrator.hydrate(media, 7L, rows, budget) {
            calls++
            emptyList()
        }
        second.metadataUnavailable shouldBe 1
        calls shouldBe 1

        now += 10 * 60_000L + 1
        hydrator.hydrate(media, 7L, rows, budget) {
            calls++
            listOf("Fantasy")
        }
        calls shouldBe 2
    }

    @Test
    fun `eight verified non-matches among thirty unknown cards are incomplete not failed`() = runTest {
        val budget = DiscoveryVerificationBudget(maxDetails = 8)
        val hydrator = DiscoveryGenreHydrator()
        val rows = (1..30).map { item("card$it") }
        val hydrated = hydrator.hydrate(media, 7L, rows, budget) { listOf("Drama") }
        hydrated.verified shouldBe 8
        hydrated.unattempted shouldBe 22
        val summary = GenreVerificationSummary(
            items = emptyList(),
            verifiedCount = hydrated.verified,
            unattemptedCount = hydrated.unattempted,
            metadataUnavailableCount = hydrated.metadataUnavailable,
            failedCount = hydrated.transientFailures,
        )
        summary.outcome shouldBe GenreVerificationOutcome.INCOMPLETE
    }

    @Test
    fun `verified empty with full coverage stays an honest empty result`() = runTest {
        val result = queryDiscoveryGenreSources(
            Catalog { _, _ -> page((1..20).map { item("wrong$it", listOf("Drama")) }) },
            media,
            listOf(7L),
            listOf("Fantasy"),
            1,
            emptySet(),
            policy,
        ).single()
        result.summary.outcome shouldBe GenreVerificationOutcome.VERIFIED_EMPTY
        result.failure shouldBe null
    }

    @Test
    fun `budget exhaustion is incomplete rather than a source failure`() = runTest {
        val result = queryDiscoveryGenreSources(
            Catalog { _, _ -> page(emptyList(), verified = 8, unattempted = 22) },
            media,
            listOf(7L),
            listOf("Fantasy"),
            1,
            emptySet(),
            policy,
        ).single()
        result.summary.outcome shouldBe GenreVerificationOutcome.INCOMPLETE
        result.failure shouldBe null
    }

    @Test
    fun `real metadata failures are an operational failure`() = runTest {
        val result = queryDiscoveryGenreSources(
            Catalog { _, _ -> page(emptyList(), verified = 0, unattempted = 0, failures = 3) },
            media,
            listOf(7L),
            listOf("Fantasy"),
            1,
            emptySet(),
            policy,
        ).single()
        result.summary.outcome shouldBe GenreVerificationOutcome.FAILED
        (result.failure as GenreSourceException).reason shouldBe GenreSourceFailure.METADATA_UNAVAILABLE
    }

    @Test
    fun `timeout is not converted into success empty`() = runTest {
        val result = queryDiscoveryGenreSources(
            Catalog { _, _ ->
                delay(2_000)
                page(emptyList())
            },
            media,
            listOf(7L),
            listOf("Fantasy"),
            1,
            emptySet(),
            policy,
            timeoutMs = 50L,
        ).single()
        result.summary.outcome shouldBe GenreVerificationOutcome.FAILED
        (result.failure as GenreSourceException).reason shouldBe GenreSourceFailure.TIMEOUT
    }

    @Test
    fun `failure of one source does not discard another source matching result`() = runTest {
        val results = queryDiscoveryGenreSources(
            Catalog { id, _ ->
                if (id == 1L) throw IOException("offline")
                page(listOf(item("Match", listOf("Fantasy"))))
            },
            media,
            listOf(1L, 7L),
            listOf("Fantasy"),
            1,
            emptySet(),
            policy,
        )
        results.first().summary.outcome shouldBe GenreVerificationOutcome.FAILED
        results.first().failure?.reason shouldBe GenreSourceFailure.SOURCE_ERROR
        results.last().summary.items.single().title shouldBe "Match"
    }

    @Test
    fun `one failed source does not turn a verified empty row into a failure`() = runTest {
        val catalog = Catalog { id, _ ->
            if (id == 1L) throw IOException("offline") else page(emptyList())
        }
        val result = DiscoverySourceRowBuilder(catalog).build(
            context().copy(sourceIds = listOf(1L, 2L)),
        )
        result shouldBe emptyList()
    }

    @Test
    fun `a matching second page is reachable with strict required`() = runTest {
        val result = queryDiscoveryGenreSources(
            Catalog { _, pageNumber ->
                if (pageNumber == 1) {
                    page(listOf(item("Wrong", listOf("Drama"))))
                } else {
                    page(listOf(item("Match", listOf("Fantasy"))))
                }
            },
            media,
            listOf(7L),
            listOf("Fantasy"),
            1,
            emptySet(),
            policy,
        ).single().summary
        result.items.single().title shouldBe "Match"
    }

    @Test
    fun `catalogue transport exceptions are not swallowed by the genre query path`() {
        val failed = Result.failure<List<DiscoveryRowItem>>(IOException("network failed"))
        shouldThrow<IOException> { failed.getForDiscoveryGenreQuery(listOf("Fantasy")) }
        failed.getForDiscoveryGenreQuery(null) shouldBe null
    }

    @Test
    fun `parent cancellation is not a source empty result`() = runTest {
        shouldThrow<CancellationException> {
            queryDiscoveryGenreSources(
                Catalog { _, _ -> throw CancellationException("cancelled") },
                media,
                listOf(7L),
                listOf("Fantasy"),
                1,
                emptySet(),
                policy,
            )
        }
    }

    @Test
    fun `hydration keeps known genres without requests`() = runTest {
        var requests = 0
        val budget = DiscoveryVerificationBudget(maxDetails = 10)
        val result = DiscoveryGenreHydrator().hydrate(
            media,
            7L,
            listOf(item("Known", listOf("Fantasy"))),
            budget,
        ) {
            requests++
            emptyList()
        }
        requests shouldBe 0
        result.items.single().genres shouldBe listOf("Fantasy")
    }
}
