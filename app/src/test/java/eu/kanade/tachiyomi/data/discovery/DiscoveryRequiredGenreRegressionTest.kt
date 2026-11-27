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
        pending: List<DiscoveryRowItem> = emptyList(),
        hasNextPage: Boolean = true,
    ) = DiscoveryGenrePage(
        items = items,
        verified = verified,
        unattempted = unattempted,
        metadataUnavailable = metadataUnavailable,
        transientFailures = failures,
        pending = pending,
        hasNextPage = hasNextPage,
    )

    private class Catalog(
        val answer: suspend (Long, Int) -> DiscoveryGenrePage,
    ) : DiscoverySourceCatalog {
        val genreCalls = mutableListOf<Long>()
        val genrePages = mutableListOf<Pair<Long, Int>>()
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
            genrePages += sourceId to page
            return boundPage(sourceId, page).items
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
            genrePages += sourceId to page
            return boundPage(sourceId, page)
        }
        private suspend fun boundPage(sourceId: Long, page: Int): DiscoveryGenrePage {
            val response = answer(sourceId, page)
            fun bind(
                items: List<DiscoveryRowItem>,
            ) = items.map { it.copy(sourceId = sourceId, sourceUrl = it.sourceUrl ?: "/fixture/" + it.cleanTitle) }
            return response.copy(items = bind(response.items), pending = bind(response.pending))
        }
    }

    private fun context(
        mediaType: DiscoveryMediaType = media,
        ids: List<Long> = (1L..10L).toList(),
    ) = DiscoveryBuildContext(
        mediaType = mediaType,
        seeds = emptyList(),
        libraryCleanTitles = emptySet(),
        historyCleanTitles = emptySet(),
        hiddenCleanTitles = emptySet(),
        requiredGenres = setOf("Fantasy"),
        sourceIds = ids,
        sourcePolicy = discoveryQueryPolicyFixture(mediaType, ids),
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
    fun `multi-source required query is not capped at twenty four per source`() = runTest {
        val catalog = Catalog { id, _ ->
            page((1..30).map { item("${id}_$it", listOf("Fantasy")) }, hasNextPage = false)
        }

        val results = queryDiscoveryGenreSources(
            catalog = catalog,
            media = media,
            sourceIds = listOf(1L, 2L),
            genres = listOf("Fantasy"),
            page = 1,
            releaseStatuses = emptySet(),
            policy = policy,
            targetItems = 60,
            budget = DiscoveryVerificationBudget(maxDetails = 160),
        )

        results.forEach { it.summary.items.size shouldBe 30 }
        results.sumOf { it.summary.items.size } shouldBe 60
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
    fun `taste and source share one required query and take disjoint slices`() = runTest {
        var calls = 0
        val sharedQuery = SharedRequiredGenreQuery {
            calls++
            listOf(
                GenreSourceVerification(
                    sourceId = 7L,
                    summary = GenreVerificationSummary(
                        items = (1..6).map { item("shared$it", listOf("Fantasy")) },
                        verifiedCount = 6,
                        unattemptedCount = 0,
                        metadataUnavailableCount = 0,
                        failedCount = 0,
                    ),
                    queryKey = "key",
                ),
            )
        }
        val catalog = Catalog { _, _ -> error("the shared query must be used") }
        val sharedContext = context(ids = listOf(7L)).copy(
            sourceIds = listOf(7L),
            sharedGenreQuery = sharedQuery,
        )

        val taste = DiscoveryTasteRowBuilder(noExternal(), catalog, { TrendSort.POPULARITY }, false)
            .build(sharedContext)
        val source = DiscoverySourceRowBuilder(catalog).build(sharedContext)

        calls shouldBe 1
        taste.map { it.title } shouldBe listOf("shared1", "shared3", "shared5")
        source.map { it.title } shouldBe listOf("shared2", "shared4", "shared6")
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
            val result = DiscoverySourceRowBuilder(catalog).build(context(mediaType = kind))
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
    fun `paging continues while genre matches are retired and admits the next page`() = runTest {
        val excluded = (1..20).mapTo(HashSet()) { "retired$it".lowercase() }
        val catalog = Catalog { _, pageNumber ->
            when (pageNumber) {
                1 -> page((1..20).map { item("Retired$it", listOf("Fantasy")) })
                2 -> page((1..5).map { item("Fresh$it", listOf("Fantasy")) }, hasNextPage = false)
                else -> page(emptyList())
            }
        }

        val result = queryDiscoveryGenreSources(
            catalog = catalog,
            media = media,
            sourceIds = listOf(7L),
            genres = listOf("Fantasy"),
            page = 1,
            releaseStatuses = emptySet(),
            policy = policy,
            pages = 5,
            excludedKeys = excluded,
        ).single()

        result.summary.items.map { it.cleanTitle }.toSet() shouldBe
            (1..5).mapTo(HashSet()) { "fresh$it" }
        catalog.genrePages.map { it.second } shouldBe listOf(1, 2)
    }

    @Test
    fun `unknown genres on the first page do not stop paging`() = runTest {
        val unknown = item("Unknown").copy(genres = emptyList())
        val catalog = Catalog { _, pageNumber ->
            when (pageNumber) {
                1 -> page(listOf(unknown), unattempted = 1, pending = listOf(unknown))
                else -> page(listOf(item("Match", listOf("Fantasy"))))
            }
        }

        val result = queryDiscoveryGenreSources(
            catalog = catalog,
            media = media,
            sourceIds = listOf(7L),
            genres = listOf("Fantasy"),
            page = 1,
            releaseStatuses = emptySet(),
            policy = policy,
            pages = 5,
        ).single()

        catalog.genrePages.map { it.second }.contains(2) shouldBe true
        result.summary.items.map { it.cleanTitle }.contains("match") shouldBe true
    }

    @Test
    fun `an alias of a retired title does not consume a slot and paging continues`() = runTest {
        val aliased = item("Primary", listOf("Fantasy")).copy(altCleanTitles = listOf("alternate name"))
        val catalog = Catalog { _, pageNumber ->
            when (pageNumber) {
                1 -> page(listOf(aliased))
                else -> page(listOf(item("Fresh", listOf("Fantasy"))))
            }
        }

        val result = queryDiscoveryGenreSources(
            catalog = catalog,
            media = media,
            sourceIds = listOf(7L),
            genres = listOf("Fantasy"),
            page = 1,
            releaseStatuses = emptySet(),
            policy = policy,
            pages = 5,
            excludedKeys = setOf("alternate name"),
        ).single()

        catalog.genrePages.map { it.second }.contains(2) shouldBe true
        result.summary.items.map { it.cleanTitle }.toSet() shouldBe setOf("fresh")
    }

    @Test
    fun `two filtered empty pages do not hide productive continuation`() = runTest {
        val catalog = Catalog { _, pageNumber ->
            when (pageNumber) {
                1 -> page(listOf(item("First", listOf("Fantasy"))))
                4 -> page(listOf(item("Second", listOf("Fantasy"))), hasNextPage = false)
                else -> page(emptyList())
            }
        }

        queryDiscoveryGenreSources(
            catalog = catalog,
            media = media,
            sourceIds = listOf(7L),
            genres = listOf("Fantasy"),
            page = 1,
            releaseStatuses = emptySet(),
            policy = policy,
            pages = 10,
        )

        catalog.genrePages.map { it.second } shouldBe listOf(1, 2, 3, 4)
    }

    @Test
    fun `filtered pages continue until the source reports its end`() = runTest {
        val catalog = Catalog { _, pageNumber ->
            page(listOf(item("Drama$pageNumber", listOf("Drama"))), hasNextPage = pageNumber == 1)
        }

        queryDiscoveryGenreSources(
            catalog = catalog,
            media = media,
            sourceIds = listOf(7L),
            genres = listOf("Fantasy"),
            page = 1,
            releaseStatuses = emptySet(),
            policy = policy,
            pages = 5,
        )

        catalog.genrePages.map { it.second } shouldBe listOf(1, 2)
    }

    @Test
    fun `verified empty with full coverage stays an honest empty result`() = runTest {
        val result = queryDiscoveryGenreSources(
            Catalog { _, _ -> page((1..20).map { item("wrong$it", listOf("Drama")) }, hasNextPage = false) },
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
    fun `first page without genre matches continues to a matching second page`() = runTest {
        val catalog = Catalog { _, pageNumber ->
            if (pageNumber == 1) {
                page(listOf(item("Wrong", listOf("Drama"))))
            } else {
                page(listOf(item("Match", listOf("Fantasy"))), hasNextPage = false)
            }
        }
        val result = queryDiscoveryGenreSources(
            catalog,
            media,
            listOf(7L),
            listOf("Fantasy"),
            1,
            emptySet(),
            policy,
        ).single().summary
        result.items.map { it.title } shouldBe listOf("Match")
        catalog.genrePages.map { it.second } shouldBe listOf(1, 2)
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

    @Test
    fun `best effort required continues to page two when page one fills target with fallback items`() = runTest {
        val catalog = Catalog { _, pageNumber ->
            if (pageNumber == 1) {
                page((1..25).map { item("Drama $it", listOf("Drama")) }, hasNextPage = true)
            } else {
                page(listOf(item("Fantasy Hit", listOf("Fantasy"))), hasNextPage = false)
            }
        }
        val bestEffort = DiscoveryCandidatePolicy(
            requiredGenres = setOf("Fantasy"),
            requiredEnforcement = RequiredEnforcement.BEST_EFFORT,
        )
        val result = queryDiscoveryGenreSources(
            catalog = catalog,
            media = media,
            sourceIds = listOf(7L),
            genres = listOf("Fantasy"),
            page = 1,
            releaseStatuses = emptySet(),
            policy = bestEffort,
            targetItems = 20,
        ).single().summary
        catalog.genrePages.map { it.second } shouldBe listOf(1, 2)
        result.items.first().title shouldBe "Fantasy Hit"
    }

    @Test
    fun `multi-branch ANY query keeps successful branch items when sibling branch fails`() = runTest {
        val catalog = object : DiscoverySourceCatalog {
            override suspend fun popular(
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
            ): List<DiscoveryRowItem> {
                if ("Drama" in genres) throw IOException("drama branch down")
                return listOf(item("Fantasy Match", listOf("Fantasy")))
            }

            override suspend fun latest(
                mediaType: DiscoveryMediaType,
                sourceId: Long,
                page: Int,
                releaseStatuses: Set<DiscoveryReleaseStatus>,
            ) = emptyList<DiscoveryRowItem>()
        }
        val anyPolicy = DiscoveryCandidatePolicy(
            requiredGenres = linkedSetOf("Fantasy", "Drama"),
            requiredMatchMode = RequiredMatchMode.ANY,
            requiredEnforcement = RequiredEnforcement.STRICT,
        )
        val result = queryDiscoveryGenreSources(
            catalog = catalog,
            media = media,
            sourceIds = listOf(7L),
            genres = listOf("Fantasy", "Drama"),
            page = 1,
            releaseStatuses = emptySet(),
            policy = anyPolicy,
            matchMode = RequiredMatchMode.ANY,
        ).single()
        result.failure shouldBe null
        result.summary.items.map { it.title } shouldBe listOf("Fantasy Match")
        result.summary.hasIncompletePages shouldBe true
    }

    @Test
    fun `multi-branch ANY merge keeps branch transient failure count`() = runTest {
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

            override suspend fun popularWithGenresVerified(
                mediaType: DiscoveryMediaType,
                sourceId: Long,
                genres: List<String>,
                page: Int,
                releaseStatuses: Set<DiscoveryReleaseStatus>,
                budget: DiscoveryVerificationBudget?,
            ) = if ("Action" in genres) {
                page(items = emptyList(), verified = 1, failures = 1, hasNextPage = false)
            } else {
                page(items = emptyList(), verified = 0, failures = 0, hasNextPage = false)
            }
        }
        val anyPolicy = DiscoveryCandidatePolicy(
            requiredGenres = linkedSetOf("Action", "Comedy"),
            requiredMatchMode = RequiredMatchMode.ANY,
            requiredEnforcement = RequiredEnforcement.STRICT,
        )
        val result = queryDiscoveryGenreSources(
            catalog = catalog,
            media = media,
            sourceIds = listOf(7L),
            genres = listOf("Action", "Comedy"),
            page = 1,
            releaseStatuses = emptySet(),
            policy = anyPolicy,
            matchMode = RequiredMatchMode.ANY,
        ).single()
        result.failure shouldBe null
        result.summary.failedCount shouldBe 1
        result.canRewardReliability shouldBe false
    }
}
