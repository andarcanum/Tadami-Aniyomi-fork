package eu.kanade.tachiyomi.data.discovery

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryExternalProvider
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import tachiyomi.domain.discovery.model.DiscoveryRowType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DiscoveryHardTimeoutTest {

    @Test
    fun `withHardTimeoutOrNull returns null on blocking call without waiting for blocked thread`(): Unit = runBlocking(
        Dispatchers.IO,
    ) {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val t0 = System.nanoTime() / 1_000_000L
        val result = withHardTimeoutOrNull(80L) {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            "late"
        }
        val elapsed = System.nanoTime() / 1_000_000L - t0
        try {
            started.await(1, TimeUnit.SECONDS) shouldBe true
            result shouldBe null
            elapsed shouldBeLessThan 1_500L
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `withHardTimeoutOrNull propagates parent cancellation`(): Unit = runTest {
        val cleanedUp = AtomicBoolean(false)
        val job = launch {
            withHardTimeoutOrNull(10_000L) {
                try {
                    awaitCancellation()
                } finally {
                    cleanedUp.set(true)
                }
            }
        }
        delay(10L)
        job.cancelAndJoin()
        cleanedUp.get() shouldBe true
    }

    @Test
    fun `queryDiscoveryGenreSources lets fast sources reach wave 2 when one blocks`(): Unit =
        runBlocking(Dispatchers.IO) {
            val releaseHung = CountDownLatch(1)
            val fastPages = mutableListOf<Int>()
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

                override suspend fun popularWithGenresAllocated(
                    mediaType: DiscoveryMediaType,
                    sourceId: Long,
                    genres: List<String>,
                    page: Int,
                    releaseStatuses: Set<DiscoveryReleaseStatus>,
                    budget: DiscoveryVerificationBudget?,
                    maxDetails: Int,
                    excludedKeys: Set<String>,
                ): DiscoveryGenrePage {
                    if (sourceId == 1L) {
                        releaseHung.await(5, TimeUnit.SECONDS)
                        return DiscoveryGenrePage(
                            items = emptyList(),
                            verified = 0,
                            unattempted = 0,
                            metadataUnavailable = 0,
                            transientFailures = 0,
                            hasNextPage = false,
                        )
                    }
                    synchronized(fastPages) { fastPages += page }
                    val item = DiscoveryRowItem(
                        title = "Fast p$page",
                        cleanTitle = "fast p$page",
                        coverUrl = null,
                        reason = null,
                        seedTitle = null,
                        provider = "Fast",
                        score = 1.0,
                        sourceId = sourceId,
                        sourceUrl = "/fast/$page",
                        genres = listOf("Fantasy"),
                    )
                    return DiscoveryGenrePage(
                        items = listOf(item),
                        verified = 1,
                        unattempted = 0,
                        metadataUnavailable = 0,
                        transientFailures = 0,
                        hasNextPage = page == 1,
                        genreFilterApplied = true,
                    )
                }
            }
            val policy = DiscoveryCandidatePolicy(GenreFilterSpec(requiredGenres = setOf("Fantasy")))
            val budget = DiscoveryVerificationBudget(budgetMs = 2_000L, maxDetails = 20)

            val t0 = System.nanoTime() / 1_000_000L
            val results = try {
                queryDiscoveryGenreSources(
                    catalog = catalog,
                    media = DiscoveryMediaType.MANGA,
                    sourceIds = listOf(1L, 2L),
                    genres = listOf("Fantasy"),
                    page = 1,
                    releaseStatuses = emptySet(),
                    policy = policy,
                    maxConcurrency = 2,
                    timeoutMs = 150L,
                    hydrate = true,
                    targetItems = 5,
                    budget = budget,
                )
            } finally {
                releaseHung.countDown()
            }
            val elapsed = System.nanoTime() / 1_000_000L - t0

            elapsed shouldBeLessThan 1_500L
            val hung = results.first { it.sourceId == 1L }
            val fast = results.first { it.sourceId == 2L }
            hung.failure?.reason shouldBe GenreSourceFailure.TIMEOUT
            fast.summary.items.map { it.title } shouldBe listOf("Fast p1", "Fast p2")
            fastPages shouldBe listOf(1, 2)
        }

    @Test
    fun `DiscoveryTasteRowBuilder bounds non-split external fetchByGenres by verificationBudget`(): Unit = runTest {
        val slowTrending = object : DiscoveryTrendingSource {
            override suspend fun fetch(
                mediaType: DiscoveryMediaType,
                season: TrendSeason,
                sort: TrendSort,
                page: Int,
                releaseStatuses: Set<DiscoveryReleaseStatus>,
            ) = emptyList<DiscoveryTrendingItem>()

            override suspend fun fetchByGenres(
                mediaType: DiscoveryMediaType,
                genres: List<String>,
                sort: TrendSort,
                page: Int,
                releaseStatuses: Set<DiscoveryReleaseStatus>,
            ): List<DiscoveryTrendingItem> {
                delay(10_000L)
                return emptyList()
            }

            override suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType): DiscoveryMeta? = null
        }
        val emptyCatalog = object : DiscoverySourceCatalog {
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
        }
        val budget = DiscoveryVerificationBudget(
            budgetMs = 50L,
            maxDetails = 0,
            clock = { testScheduler.currentTime },
        )
        val context = DiscoveryBuildContext(
            mediaType = DiscoveryMediaType.MANGA,
            seeds = emptyList(),
            libraryCleanTitles = emptySet(),
            historyCleanTitles = emptySet(),
            hiddenCleanTitles = emptySet(),
            tasteProfile = listOf("Fantasy" to 1.0),
            requiredGenres = setOf("Fantasy", "Action"),
            genreFilterSpec = GenreFilterSpec(
                requiredGenres = setOf("Fantasy", "Action"),
                requiredEnforcement = RequiredEnforcement.STRICT,
                requiredMatchMode = RequiredMatchMode.ALL,
            ),
            sourcePolicy = discoveryQueryPolicyFixture(
                DiscoveryMediaType.MANGA,
                emptyList(),
                mapOf(DiscoveryRowType.TASTE to setOf(DiscoveryExternalProvider.ANILIST)),
            ),
            verificationBudget = budget,
        )
        val builder =
            DiscoveryTasteRowBuilder(slowTrending, emptyCatalog, { TrendSort.POPULARITY }, includeExternal = true)

        shouldThrow<java.io.IOException> {
            builder.build(context)
        }
        (testScheduler.currentTime <= 50L) shouldBe true
    }
}
