package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import java.io.IOException

class DiscoveryCoordinatorStreamTest {

    private fun item(title: String, score: Double = 0.0) = DiscoveryRowItem(
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = null,
        reason = null,
        seedTitle = null,
        provider = "test",
        score = score,
    )

    private fun delayedBuilder(
        type: DiscoveryRowType,
        items: List<DiscoveryRowItem>,
        delayMs: Long,
        fail: Boolean = false,
    ) = object : DiscoveryRowBuilder {
        override val rowType = type
        override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
            if (delayMs > 0) delay(delayMs)
            if (fail) throw IOException("builder error")
            return items
        }
    }

    private val baseContext = DiscoveryBuildContext(
        mediaType = DiscoveryMediaType.MANGA,
        seeds = emptyList(),
        libraryCleanTitles = emptySet(),
        historyCleanTitles = emptySet(),
        hiddenCleanTitles = emptySet(),
    )

    @Test
    fun `streamFeed emits rows as they arrive without waiting for slower builders`() = runTest {
        val fastTrend = delayedBuilder(
            DiscoveryRowType.TREND,
            listOf(item("Fast Trend")),
            delayMs = 10,
        )
        val slowLike = delayedBuilder(
            DiscoveryRowType.LIKE,
            listOf(item("Slow Like")),
            delayMs = 100,
        )

        val coordinator = DiscoveryCoordinator(listOf(slowLike, fastTrend))
        val emittedRows = mutableListOf<Pair<DiscoveryRowType, List<DiscoveryRowItem>>>()

        val feed = coordinator.streamFeed(baseContext) { type, items ->
            emittedRows.add(type to items)
        }

        // Fast row should be emitted first despite list order
        emittedRows.size shouldBe 2
        emittedRows[0].first shouldBe DiscoveryRowType.TREND
        emittedRows[0].second.map { it.title } shouldBe listOf("Fast Trend")

        emittedRows[1].first shouldBe DiscoveryRowType.LIKE
        emittedRows[1].second.map { it.title } shouldBe listOf("Slow Like")

        feed.rows[DiscoveryRowType.TREND]?.map { it.title } shouldBe listOf("Fast Trend")
        feed.rows[DiscoveryRowType.LIKE]?.map { it.title } shouldBe listOf("Slow Like")
        feed.failedRows shouldBe emptySet()
    }

    @Test
    fun `streamFeed filters duplicates across arriving streams and excluded lists`() = runTest {
        val fastBuilder = delayedBuilder(
            DiscoveryRowType.TASTE,
            listOf(item("Solo Leveling"), item("Berserk")),
            delayMs = 10,
        )
        val slowBuilder = delayedBuilder(
            DiscoveryRowType.TREND,
            listOf(item("Solo Leveling"), item("One Piece")),
            delayMs = 50,
        )

        val coordinator = DiscoveryCoordinator(listOf(fastBuilder, slowBuilder))
        val emittedRows = mutableListOf<Pair<DiscoveryRowType, List<DiscoveryRowItem>>>()

        coordinator.streamFeed(
            baseContext.copy(
                libraryCleanTitles = setOf("berserk"),
            ),
        ) { type, items ->
            emittedRows.add(type to items)
        }

        // TASTE emitted first: "Berserk" in library so only "Solo Leveling" remains
        emittedRows[0].first shouldBe DiscoveryRowType.TASTE
        emittedRows[0].second.map { it.title } shouldBe listOf("Solo Leveling")

        // TREND emitted second: "Solo Leveling" already seen, so only "One Piece" remains
        emittedRows[1].first shouldBe DiscoveryRowType.TREND
        emittedRows[1].second.map { it.title } shouldBe listOf("One Piece")
    }

    @Test
    fun `streamFeed ensures higher-priority slower rows reclaim titles from faster lower-priority rows`() = runTest {
        val fastTrend = delayedBuilder(
            DiscoveryRowType.TREND,
            listOf(item("Solo Leveling"), item("One Piece")),
            delayMs = 10,
        )
        val slowLike = delayedBuilder(
            DiscoveryRowType.LIKE,
            listOf(item("Solo Leveling"), item("Omniscient Reader")),
            delayMs = 50,
        )

        val coordinator = DiscoveryCoordinator(listOf(slowLike, fastTrend))
        val emittedRows = mutableListOf<Pair<DiscoveryRowType, List<DiscoveryRowItem>>>()

        val feed = coordinator.streamFeed(baseContext) { type, items ->
            emittedRows.add(type to items)
        }

        feed.rows[DiscoveryRowType.LIKE]?.map { it.title } shouldBe listOf("Solo Leveling", "Omniscient Reader")
        feed.rows[DiscoveryRowType.TREND]?.map { it.title } shouldBe listOf("One Piece")
    }

    @Test
    fun `trend builder cancellation propagates instead of empty row`() = runTest {
        val cancellingTrending = object : DiscoveryTrendingSource {
            override suspend fun fetch(
                mediaType: DiscoveryMediaType,
                season: TrendSeason,
                sort: TrendSort,
                page: Int,
                releaseStatuses: Set<tachiyomi.domain.discovery.model.DiscoveryReleaseStatus>,
            ): List<DiscoveryTrendingItem> {
                throw kotlinx.coroutines.CancellationException("cancelled")
            }
            override suspend fun fetchByGenres(
                mediaType: DiscoveryMediaType,
                genres: List<String>,
                sort: TrendSort,
                page: Int,
                releaseStatuses: Set<tachiyomi.domain.discovery.model.DiscoveryReleaseStatus>,
            ): List<DiscoveryTrendingItem> = emptyList()
            override suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType): DiscoveryMeta? = null
        }
        val emptyCatalog = object : DiscoverySourceCatalog {
            override suspend fun popular(
                mediaType: DiscoveryMediaType,
                sourceId: Long,
                page: Int,
                releaseStatuses: Set<tachiyomi.domain.discovery.model.DiscoveryReleaseStatus>,
            ): List<DiscoveryRowItem> = emptyList()
            override suspend fun latest(
                mediaType: DiscoveryMediaType,
                sourceId: Long,
                page: Int,
                releaseStatuses: Set<tachiyomi.domain.discovery.model.DiscoveryReleaseStatus>,
            ): List<DiscoveryRowItem> = emptyList()
            override suspend fun popularWithGenres(
                mediaType: DiscoveryMediaType,
                sourceId: Long,
                genres: List<String>,
                page: Int,
                releaseStatuses: Set<tachiyomi.domain.discovery.model.DiscoveryReleaseStatus>,
            ): List<DiscoveryRowItem> = emptyList()
        }
        val builder = DiscoveryTrendRowBuilder(
            trending = cancellingTrending,
            catalog = emptyCatalog,
            seasonProvider = { TrendSeason.CURRENT },
            sortProvider = { TrendSort.POPULARITY },
        )
        io.kotest.assertions.throwables.shouldThrow<kotlinx.coroutines.CancellationException> {
            builder.build(baseContext)
        }
    }
}
