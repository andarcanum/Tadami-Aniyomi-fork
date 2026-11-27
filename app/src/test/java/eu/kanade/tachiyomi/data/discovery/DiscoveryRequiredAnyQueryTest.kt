package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryExternalProvider
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import tachiyomi.domain.discovery.model.DiscoveryRowType
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryRequiredAnyQueryTest {

    @Test
    fun `required ANY queries fourth and fifth genres on page one when manga plugins are absent`() = runTest {
        val source = RecordingTrendingSource { query ->
            delay(1)
            tailOnlyPageOne(query)
        }
        val catalog = MissingMangaCatalog()
        val builder = DiscoveryTasteRowBuilder(source, catalog, { TrendSort.POPULARITY })
        val budget =
            DiscoveryVerificationBudget(budgetMs = 1_000, maxDetails = 0, clock = { testScheduler.currentTime })

        val built = builder.build(requiredAnyContext(budget))

        built.map { it.title }.toSet() shouldBe setOf("Fourth Only Manga", "Fifth Only Manga")
        source.queries.filter { it.page == 1 }.map { it.genres.single() }.toSet() shouldBe
            setOf("action", "comedy", "fantasy", "romance", "mystery", "drama")
        source.queries.all {
            it.mediaType == DiscoveryMediaType.MANGA && it.sort == TrendSort.POPULARITY &&
                it.matchMode == RequiredMatchMode.ANY && it.releaseStatuses.isEmpty()
        } shouldBe true
        catalog.queryableRequests shouldBe listOf(listOf(111L))
        catalog.fetches shouldBe 0
    }

    @Test
    fun `required ANY external queries never exceed three concurrent requests`() = runTest {
        val source = RecordingTrendingSource { query ->
            delay(1)
            tailOnlyPageOne(query)
        }
        val builder = DiscoveryTasteRowBuilder(source, MissingMangaCatalog(), { TrendSort.POPULARITY })
        val budget =
            DiscoveryVerificationBudget(budgetMs = 1_000, maxDetails = 0, clock = { testScheduler.currentTime })

        builder.build(requiredAnyContext(budget))

        (source.peakConcurrent in 1..3) shouldBe true
        source.activeRequests shouldBe 0
    }

    @Test
    fun `required ANY does not start queued genres after the shared budget expires`() = runTest {
        val clock = AtomicLong(0L)
        val source = RecordingTrendingSource {
            delay(1)
            clock.set(100L)
            emptyList()
        }
        val builder = DiscoveryTasteRowBuilder(source, MissingMangaCatalog(), { TrendSort.POPULARITY })
        val budget = DiscoveryVerificationBudget(budgetMs = 100, maxDetails = 0, clock = { clock.get() })

        builder.build(requiredAnyContext(budget))

        source.queries.map { it.genres.single() } shouldBe listOf("action", "comedy", "fantasy")
        source.queries.all { it.page == 1 } shouldBe true
        source.activeRequests shouldBe 0
    }

    @Test
    fun `cancelling required ANY drains active requests without starting queued genres or source fallback`() = runTest {
        val source = RecordingTrendingSource { awaitCancellation() }
        val catalog = MissingMangaCatalog()
        val builder = DiscoveryTasteRowBuilder(source, catalog, { TrendSort.POPULARITY })
        val worker = launch { builder.build(requiredAnyContext()) }
        runCurrent()
        source.activeRequests shouldBe 3

        worker.cancelAndJoin()

        source.activeRequests shouldBe 0
        source.queries.map { it.genres.single() } shouldBe listOf("action", "comedy", "fantasy")
        source.cancelledQueries.map { it.genres.single() }.toSet() shouldBe setOf("action", "comedy", "fantasy")
        catalog.queryableRequests shouldBe emptyList()
        catalog.fetches shouldBe 0
    }

    @Test
    fun `slow head genres leave shared time for tail genres on their first pages`() = runTest {
        val source = RecordingTrendingSource { query ->
            if (query.genres.single() in setOf("action", "comedy", "fantasy")) {
                delay(1_000)
            } else {
                delay(1)
            }
            tailOnlyPageOne(query)
        }
        val builder = DiscoveryTasteRowBuilder(source, MissingMangaCatalog(), { TrendSort.POPULARITY })
        val budget = DiscoveryVerificationBudget(budgetMs = 100, maxDetails = 0, clock = { testScheduler.currentTime })

        val built = builder.build(requiredAnyContext(budget))

        built.map { it.title }.toSet() shouldBe setOf("Fourth Only Manga", "Fifth Only Manga")
        source.queries.filter { it.page == 1 }.map { it.genres.single() }.toSet() shouldBe
            setOf("action", "comedy", "fantasy", "romance", "mystery", "drama")
        (testScheduler.currentTime <= 100L) shouldBe true
        (source.peakConcurrent in 1..3) shouldBe true
        source.activeRequests shouldBe 0
    }

    @Test
    fun `soft interests query the six-genre union independently of required mode`() =
        runTest {
            val source = RecordingTrendingSource {
                delay(1)
                emptyList()
            }
            val builder = DiscoveryTasteRowBuilder(source, MissingMangaCatalog(), { TrendSort.POPULARITY })
            val budget =
                DiscoveryVerificationBudget(budgetMs = 1_000, maxDetails = 0, clock = { testScheduler.currentTime })
            val context = requiredAnyContext(budget).copy(
                genreFilterSpec = GenreFilterSpec(),
                tasteProfile = listOf(
                    "action" to 6.0,
                    "comedy" to 5.0,
                    "fantasy" to 4.0,
                    "romance" to 3.0,
                    "mystery" to 2.0,
                    "drama" to 1.0,
                ),
            )

            builder.build(context)

            source.queries.filter { it.genres.size == 1 }.map { it.genres.single() } shouldBe
                listOf("action", "comedy", "fantasy", "romance", "mystery", "drama")
            source.queries.all { it.page == 1 } shouldBe true
        }

    private fun requiredAnyContext(budget: DiscoveryVerificationBudget? = null) = DiscoveryBuildContext(
        mediaType = DiscoveryMediaType.MANGA,
        seeds = emptyList(),
        libraryCleanTitles = emptySet(),
        historyCleanTitles = emptySet(),
        hiddenCleanTitles = emptySet(),
        genreFilterSpec = GenreFilterSpec(
            requiredGenres = linkedSetOf("action", "comedy", "fantasy", "romance", "mystery", "drama"),
            requiredMatchMode = RequiredMatchMode.ANY,
            requiredEnforcement = RequiredEnforcement.STRICT,
        ),
        sourceId = 111L,
        sourceIds = listOf(111L),
        sourcePolicy = discoveryQueryPolicyFixture(
            DiscoveryMediaType.MANGA,
            listOf(111L),
            mapOf(DiscoveryRowType.TASTE to setOf(DiscoveryExternalProvider.ANILIST)),
        ),
        verificationBudget = budget,
    )

    private fun tailOnlyPageOne(query: GenreQuery): List<DiscoveryTrendingItem> {
        if (query.page != 1) return emptyList()
        return when (query.genres.single()) {
            "romance" -> listOf(
                DiscoveryTrendingItem(
                    title = "Fourth Only Manga",
                    cleanTitle = "fourth only manga",
                    coverUrl = null,
                    seasonLabel = null,
                    genres = listOf("Romance"),
                    provider = "anilist_trend",
                ),
            )
            "mystery" -> listOf(
                DiscoveryTrendingItem(
                    title = "Fifth Only Manga",
                    cleanTitle = "fifth only manga",
                    coverUrl = null,
                    seasonLabel = null,
                    genres = listOf("Mystery"),
                    provider = "anilist_trend",
                ),
            )
            else -> emptyList()
        }
    }

    private data class GenreQuery(
        val mediaType: DiscoveryMediaType,
        val genres: List<String>,
        val sort: TrendSort,
        val page: Int,
        val releaseStatuses: Set<DiscoveryReleaseStatus>,
        val matchMode: RequiredMatchMode,
    )

    private class RecordingTrendingSource(
        private val response: suspend (GenreQuery) -> List<DiscoveryTrendingItem>,
    ) : DiscoveryTrendingSource {
        val queries = mutableListOf<GenreQuery>()
        val cancelledQueries = mutableListOf<GenreQuery>()
        var activeRequests = 0
            private set
        var peakConcurrent = 0
            private set

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
        ) = fetchByGenres(mediaType, genres, sort, page, releaseStatuses, RequiredMatchMode.ANY)

        override suspend fun fetchByGenres(
            mediaType: DiscoveryMediaType,
            genres: List<String>,
            sort: TrendSort,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
            matchMode: RequiredMatchMode,
        ): List<DiscoveryTrendingItem> {
            val query = GenreQuery(mediaType, genres.toList(), sort, page, releaseStatuses.toSet(), matchMode)
            queries += query
            activeRequests++
            peakConcurrent = maxOf(peakConcurrent, activeRequests)
            try {
                return response(query)
            } catch (e: CancellationException) {
                cancelledQueries += query
                throw e
            } finally {
                activeRequests--
            }
        }

        override suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType): DiscoveryMeta? = null
    }

    private class MissingMangaCatalog : DiscoverySourceCatalog {
        val queryableRequests = mutableListOf<List<Long>>()
        var fetches = 0
            private set

        override fun queryableSourceIds(mediaType: DiscoveryMediaType, sourceIds: List<Long>): List<Long> {
            queryableRequests += sourceIds.toList()
            return emptyList()
        }

        override suspend fun popular(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryRowItem> = unexpectedFetch()

        override suspend fun popularWithGenres(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            genres: List<String>,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryRowItem> = unexpectedFetch()

        override suspend fun latest(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryRowItem> = unexpectedFetch()

        private fun unexpectedFetch(): Nothing {
            fetches++
            error("A missing manga extension must not receive a catalogue request")
        }
    }
}
