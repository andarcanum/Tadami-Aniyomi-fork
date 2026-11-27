package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import tachiyomi.domain.discovery.model.canonicalDiscoveryKey

class DiscoveryRefillRegressionTest {
    private val media = DiscoveryMediaType.MANGA
    private val fantasy = DiscoveryCandidatePolicy(GenreFilterSpec(requiredGenres = setOf("Fantasy")))

    private fun item(title: String, source: Long = 1L, genres: List<String> = listOf("Fantasy")) = DiscoveryRowItem(
        title = title, cleanTitle = title.lowercase(), coverUrl = null, reason = null,
        seedTitle = null, provider = "source-$source", score = 1.0,
        sourceId = source, sourceUrl = "/$title", genres = genres,
    )

    private fun page(items: List<DiscoveryRowItem>, next: Boolean = false) = DiscoveryGenrePage(
        items = items,
        verified = items.count { it.genres.isNotEmpty() },
        unattempted = items.count { it.genres.isEmpty() },
        metadataUnavailable = 0,
        transientFailures = 0,
        hasNextPage = next,
        pending = items.filter { it.genres.isEmpty() },
    )

    private class Catalog(
        private val pages: suspend (Long, Int) -> DiscoveryGenrePage,
        private val details: suspend (String) -> List<String> = { listOf("Fantasy") },
    ) : DiscoverySourceCatalog {
        val pageCalls = mutableListOf<Pair<Long, Int>>()
        val browseCalls = mutableListOf<Pair<Long, Int>>()
        private val hydrator = DiscoveryGenreHydrator(maxDetailsPerCall = 64)

        override suspend fun popular(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryRowItem> {
            browseCalls += sourceId to page
            return pages(sourceId, page).items
        }
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
        ): List<DiscoveryRowItem> = pages(sourceId, page).items
        override suspend fun popularWithGenresAllocated(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            genres: List<String>,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
            budget: DiscoveryVerificationBudget?,
            maxDetails: Int,
        ): DiscoveryGenrePage {
            pageCalls += sourceId to page
            val raw = pages(sourceId, page)
            if (budget == null) return raw
            return hydrated(mediaType, sourceId, raw.items, budget, maxDetails).copy(hasNextPage = raw.hasNextPage)
        }
        override suspend fun hydrateGenres(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            items: List<DiscoveryRowItem>,
            budget: DiscoveryVerificationBudget?,
            maxDetails: Int,
        ): DiscoveryGenrePage =
            hydrated(mediaType, sourceId, items, requireNotNull(budget), maxDetails)

        private suspend fun hydrated(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            items: List<DiscoveryRowItem>,
            budget: DiscoveryVerificationBudget,
            maxDetails: Int,
        ): DiscoveryGenrePage {
            val r = hydrator.hydrate(mediaType, sourceId, items, budget, maxDetails, fetchDetails = details)
            return DiscoveryGenrePage(
                items = r.items,
                verified = r.verified,
                unattempted = r.unattempted,
                metadataUnavailable = r.metadataUnavailable,
                transientFailures = r.transientFailures,
                pending = r.pending,
                detailsUsed = r.detailsUsed,
            )
        }
    }

    private suspend fun query(
        catalog: Catalog,
        sources: List<Long> = listOf(1L),
        excluded: Set<String> = emptySet(),
        target: Int = 30,
    ) = queryDiscoveryGenreSources(
        catalog = catalog,
        media = media,
        sourceIds = sources,
        genres = listOf("Fantasy"),
        page = 1,
        releaseStatuses = emptySet(),
        policy = fantasy,
        budget = DiscoveryVerificationBudget(maxDetails = 160),
        excludedKeys = excluded, targetItems = target,
    )

    @Test
    fun filteredFirstPageDoesNotHideMatchingSecondPage() = runTest {
        val c = Catalog(pages = { _, n ->
            if (n == 1) {
                page(listOf(item("Wrong", genres = listOf("Drama"))), true)
            } else {
                page(listOf(item("Fresh")))
            }
        })
        query(c).single().summary.items.map { it.title } shouldBe listOf("Fresh")
        c.pageCalls shouldBe listOf(1L to 1, 1L to 2)
    }

    @Test
    fun twoRetiredPagesDoNotHideFreshThirdPage() = runTest {
        val c = Catalog(pages = { _, n ->
            when (n) {
                1 -> page(listOf(item("RetiredAlpha")), true)
                2 -> page(listOf(item("RetiredBeta")), true)
                else -> page(listOf(item("Fresh")))
            }
        })
        query(c, excluded = setOf("retiredalpha", "retiredbeta")).single().summary.items.map { it.title } shouldBe
            listOf("Fresh")
        c.pageCalls shouldBe listOf(1L to 1, 1L to 2, 1L to 3)
    }

    @Test
    fun canonicalRetirementIsExcludedBeforeTarget() = runTest {
        val retired = item("Retired")
        val c = Catalog(pages = { _, n -> if (n == 1) page(listOf(retired), true) else page(listOf(item("Fresh"))) })
        val key = canonicalDiscoveryKey(media, retired.sourceId, retired.sourceUrl, retired.cleanTitle)
        query(c, excluded = setOf(key), target = 1).single().summary.items.map { it.title } shouldBe listOf("Fresh")
    }

    @Test
    fun zeroFreshAdmissionStillRefillsProductivePending() = runTest {
        val wrong = (1..6).map { item("Wrong$it", genres = emptyList()) }
        val pending = listOf(item("PendingAlpha", genres = emptyList()), item("PendingBeta", genres = emptyList()))
        val c = Catalog(
            pages = { _, _ -> page(wrong + pending) },
            details = { if (it.startsWith("/Wrong")) listOf("Drama") else listOf("Fantasy") },
        )
        query(c).single().summary.items.map { it.title } shouldBe listOf("PendingAlpha", "PendingBeta")
        c.pageCalls shouldBe listOf(1L to 1)
    }

    @Test
    fun retiredPendingCannotConsumeLastFreshSlot() = runTest {
        val known = item("Known")
        val wrong = (1..6).map { item("Wrong$it", genres = emptyList()) }
        val retired = item("Retired", genres = emptyList())
        val c = Catalog(
            pages = { _, n ->
                if (n == 1) {
                    page(listOf(known) + wrong + retired, true)
                } else {
                    page(listOf(item("Fresh")))
                }
            },
            details = { if (it.startsWith("/Wrong")) listOf("Drama") else listOf("Fantasy") },
        )
        query(c, excluded = setOf("retired"), target = 2).single().summary.items.map { it.title } shouldBe
            listOf("Known", "Fresh")
    }

    @Test
    fun everySelectedSourceGetsPageBeforeAnySecondPage() = runTest {
        val c = Catalog(pages = { source, n ->
            delay(10)
            page(listOf(item("Source$source-Page$n", source)), n == 1)
        })
        val result = query(c, sources = (1L..5L).toList(), target = 2)
        (c.pageCalls.indexOfLast { it.second == 1 } < c.pageCalls.indexOfFirst { it.second == 2 }) shouldBe true
        result.map { it.sourceId } shouldBe (1L..5L).toList()
        result.map { it.summary.items.size } shouldBe listOf(2, 2, 2, 2, 2)
    }

    @Test
    fun knownListMetadataOnNextPageIsUsableWithoutHydrationBudget() = runTest {
        val c = Catalog(pages = { _, n ->
            if (n == 1) {
                page(listOf(item("Unknown", genres = emptyList())), true)
            } else {
                page(listOf(item("Known")))
            }
        })
        val result = queryDiscoveryGenreSources(c, media, listOf(1L), listOf("Fantasy"), 1, emptySet(), fantasy)
        result.single().summary.items.map { it.title } shouldBe listOf("Known")
        c.pageCalls shouldBe listOf(1L to 1, 1L to 2)
    }

    @Test
    fun knownListMetadataOnNextPageIsUsableAfterDetailsAreExhausted() = runTest {
        val c = Catalog(pages = { _, n ->
            if (n == 1) {
                page(listOf(item("Unknown", genres = emptyList())), true)
            } else {
                page(listOf(item("Known")))
            }
        })
        val result = queryDiscoveryGenreSources(
            c,
            media,
            listOf(1L),
            listOf("Fantasy"),
            1,
            emptySet(),
            fantasy,
            budget = DiscoveryVerificationBudget(maxDetails = 0),
        )
        result.single().summary.items.map { it.title } shouldBe listOf("Known")
        c.pageCalls shouldBe listOf(1L to 1, 1L to 2)
    }

    @Test
    fun duplicateTitlesDoNotConsumeTheInterleavedCapOrHideAnotherSource() {
        val a = listOf(item("Alpha", 1L), item("Beta", 1L), item("Gamma", 1L))
        val b = listOf(item("Alpha", 2L), item("Beta", 2L), item("Gamma", 2L))
        val result = interleaveSourceParts(listOf(a, b), cap = 3)
        result.map { it.title } shouldBe listOf("Alpha", "Beta", "Gamma")
        result.map { it.provider } shouldBe listOf("source-1", "source-2", "source-1")
    }

    @Test
    fun ordinarySourcesGetFirstProbeBeforeLongerHarvest() = runTest {
        val c = Catalog(pages = { source, n ->
            delay(10)
            page(listOf(item("Source$source-Page$n", source)), n == 1)
        })
        val result = DiscoverySourceRowBuilder(
            c,
            rowCap = 20,
        ).build(
            sourceContext().copy(
                sourceIds = (1L..5L).toList(),
                sourcePolicy = discoveryQueryPolicyFixture(media, (1L..5L).toList()),
                harvestPages = 2,
            ),
        )
        (c.browseCalls.indexOfLast { it.second == 1 } < c.browseCalls.indexOfFirst { it.second == 2 }) shouldBe true
        result.map { it.provider }.toSet() shouldBe (1L..5L).map { "source-$it" }.toSet()
    }

    @Test
    fun requiredSourceCarriesNativeEndEvidence() = runTest {
        val c = Catalog(pages = { _, _ -> page(emptyList()) })
        val builder = DiscoverySourceRowBuilder(c)
        builder.build(sourceContext().copy(genreFilterSpec = GenreFilterSpec(requiredGenres = setOf("Fantasy"))))
        builder.exhaustionEvidence?.map { it.rawEnd } shouldBe listOf(true)
    }

    @Test
    fun aPageBudgetStopIsNotCatalogueExhaustion() = runTest {
        val c = Catalog(pages = { _, _ -> page(listOf(item("Wrong", genres = listOf("Drama"))), true) })
        val builder = DiscoverySourceRowBuilder(c)
        builder.build(sourceContext().copy(genreFilterSpec = GenreFilterSpec(requiredGenres = setOf("Fantasy"))))
        builder.exhaustionEvidence?.map { it.rawEnd } shouldBe listOf(false)
        builder.isDegraded shouldBe true
    }

    private fun sourceContext() = DiscoveryBuildContext(
        mediaType = media,
        seeds = emptyList(),
        libraryCleanTitles = emptySet(),
        historyCleanTitles = emptySet(),
        hiddenCleanTitles = emptySet(),
        sourceIds = listOf(1L),
        sourcePolicy = discoveryQueryPolicyFixture(media, listOf(1L)),
        harvestPages = 4,
    )

    @Test
    fun ordinarySourceReachesFreshAfterTwoRetiredPages() = runTest {
        val c = Catalog(pages = { _, n ->
            when (n) {
                1 -> page(listOf(item("RetiredAlpha")), true)
                2 -> page(listOf(item("RetiredBeta")), true)
                3 -> page(listOf(item("Fresh")))
                else -> page(emptyList())
            }
        })
        DiscoverySourceRowBuilder(
            c,
            rowCap = 20,
        ).build(sourceContext().copy(recentCleanTitles = setOf("retiredalpha", "retiredbeta"))).map {
            it.title
        } shouldBe listOf("Fresh")
    }

    @Test
    fun ordinarySourceContinuesAfterIgnoredFirstPage() = runTest {
        val c = Catalog(pages = { _, n ->
            when (n) {
                1 -> page(listOf(item("Wrong", genres = listOf("Drama"))), true)
                2 -> page(listOf(item("Fresh")))
                else -> page(emptyList())
            }
        })
        DiscoverySourceRowBuilder(
            c,
            rowCap = 20,
        ).build(sourceContext().copy(genreFilterSpec = GenreFilterSpec(ignoredGenres = setOf("Drama")))).map {
            it.title
        } shouldBe
            listOf("Fresh")
    }

    @Test
    fun drySourceWithPendingCardsDoesNotStealRefillFromProductiveSource() = runTest {
        val dryWrong = (1..20).map { item("DryWrong$it", source = 1L, genres = emptyList()) }
        val prodPage1 = listOf(item("ProdMatch1", source = 2L, genres = listOf("Fantasy"))) +
            (1..10).map { item("ProdPending$it", source = 2L, genres = emptyList()) }
        val c = Catalog(
            pages = { sourceId, _ ->
                if (sourceId == 1L) {
                    page(dryWrong, next = false)
                } else {
                    page(prodPage1, next = false)
                }
            },
            details = { if (it.startsWith("/DryWrong")) listOf("Drama") else listOf("Fantasy") },
        )
        val results = query(c, sources = listOf(1L, 2L))
        results.first { it.sourceId == 1L }.summary.items shouldBe emptyList()
        results.first { it.sourceId == 2L }.summary.items.size shouldBe 11
    }

    @Test
    fun sourceMatchingRequiredButRejectedByIgnoredOnFirstPageContinuesToSecondPage() = runTest {
        val policyWithIgnored = DiscoveryCandidatePolicy(
            GenreFilterSpec(
                requiredGenres = setOf("Fantasy"),
                ignoredGenres = setOf("Horror"),
            ),
        )
        val c = Catalog(
            pages = { sourceId, pageNum ->
                if (sourceId == 1L) {
                    if (pageNum == 1) {
                        page(
                            listOf(item("IgnoredMatch", source = 1L, genres = listOf("Fantasy", "Horror"))),
                            next = true,
                        )
                    } else {
                        page(listOf(item("CleanMatch", source = 1L, genres = listOf("Fantasy"))), next = false)
                    }
                } else {
                    page(listOf(item("OtherSourceMatch", source = 2L, genres = listOf("Fantasy"))), next = false)
                }
            },
        )
        val results = queryDiscoveryGenreSources(
            catalog = c,
            media = media,
            sourceIds = listOf(1L, 2L),
            genres = listOf("Fantasy"),
            page = 1,
            releaseStatuses = emptySet(),
            policy = policyWithIgnored,
            budget = DiscoveryVerificationBudget(maxDetails = 160),
            targetItems = 30,
        )
        results.first { it.sourceId == 1L }.summary.items.map { it.title } shouldBe listOf("CleanMatch")
    }
}
