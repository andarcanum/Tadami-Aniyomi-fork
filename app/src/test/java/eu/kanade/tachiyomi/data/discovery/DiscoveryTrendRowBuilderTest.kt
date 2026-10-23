package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import java.io.IOException

class DiscoveryTrendRowBuilderTest {

    private class FakeTrending(
        private val items: List<DiscoveryTrendingItem> = emptyList(),
        private val shouldFail: Boolean = false,
    ) : AniListTrendingSource(clientProvider = { error("unused") }) {
        override suspend fun fetch(
            mediaType: DiscoveryMediaType,
            season: TrendSeason,
            sort: TrendSort,
            page: Int,
            releaseStatuses: Set<tachiyomi.domain.discovery.model.DiscoveryReleaseStatus>,
        ): List<DiscoveryTrendingItem> {
            if (shouldFail) throw IOException("AniList circuit breaker open (403 backoff)")
            return items
        }
    }

    private class FakeCatalog(
        val latestItems: List<DiscoveryRowItem> = emptyList(),
    ) : DiscoverySourceCatalog {
        override suspend fun popular(
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

        override suspend fun latest(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            page: Int,
            releaseStatuses: Set<tachiyomi.domain.discovery.model.DiscoveryReleaseStatus>,
        ): List<DiscoveryRowItem> = latestItems
    }

    private val testContext = DiscoveryBuildContext(
        mediaType = DiscoveryMediaType.NOVEL,
        seeds = emptyList(),
        libraryCleanTitles = emptySet(),
        historyCleanTitles = emptySet(),
        hiddenCleanTitles = emptySet(),
        sourceId = 42L,
    )

    @Test
    fun `returns AniList items when trending succeeds`() = runTest {
        val trending = FakeTrending(
            items = listOf(
                DiscoveryTrendingItem("Solo Leveling", "solo leveling", "http://c/1.jpg", 1L, seasonLabel = "current"),
            ),
        )
        val catalog = FakeCatalog()
        val builder = DiscoveryTrendRowBuilder(
            trending = trending,
            catalog = catalog,
            seasonProvider = { TrendSeason.CURRENT },
            sortProvider = { TrendSort.TRENDING },
        )

        val result = builder.build(testContext)
        result.size shouldBe 1
        result.first().title shouldBe "Solo Leveling"
        result.first().provider shouldBe "anilist_trend"
        builder.rowType shouldBe DiscoveryRowType.TREND
    }

    @Test
    fun `falls back to catalog latest when AniList fails with 403 or circuit breaker`() = runTest {
        val trending = FakeTrending(shouldFail = true)
        val catalog = FakeCatalog(
            latestItems = listOf(
                DiscoveryRowItem(
                    "Source Fresh Novel",
                    "source fresh novel",
                    "http://c/2.jpg",
                    null,
                    null,
                    "InkStory",
                    0.0,
                ),
            ),
        )
        val builder = DiscoveryTrendRowBuilder(
            trending = trending,
            catalog = catalog,
            seasonProvider = { TrendSeason.CURRENT },
            sortProvider = { TrendSort.TRENDING },
        )

        val result = builder.build(testContext)
        result.size shouldBe 1
        result.first().title shouldBe "Source Fresh Novel"
        result.first().reason shouldBe "source"
        result.first().provider shouldBe "InkStory"
    }

    @Test
    fun `blacklisted genre filtered from trending items`() = runTest {
        val trending = FakeTrending(
            items = listOf(
                DiscoveryTrendingItem(
                    "Fantasy Anime",
                    "fantasy anime",
                    null,
                    1L,
                    seasonLabel = "current",
                    genres = listOf("Fantasy"),
                ),
                DiscoveryTrendingItem(
                    "Action Anime",
                    "action anime",
                    null,
                    2L,
                    seasonLabel = "current",
                    genres = listOf("Action"),
                ),
            ),
        )
        val builder = DiscoveryTrendRowBuilder(
            trending = trending,
            catalog = FakeCatalog(),
            seasonProvider = { TrendSeason.CURRENT },
            sortProvider = { TrendSort.POPULARITY },
        )
        val result = builder.build(
            testContext.copy(mediaType = DiscoveryMediaType.ANIME, blacklistedTags = setOf("фэнтези")),
        )
        result.map { it.title } shouldBe listOf("Action Anime")
    }

    @Test
    fun `falls back to catalog latest when AniList returns empty list`() = runTest {
        val trending = FakeTrending(items = emptyList())
        val catalog = FakeCatalog(
            latestItems = listOf(
                DiscoveryRowItem(
                    "Another Fresh Novel",
                    "another fresh novel",
                    "http://c/3.jpg",
                    null,
                    null,
                    "InkStory",
                    0.0,
                ),
            ),
        )
        val builder = DiscoveryTrendRowBuilder(
            trending = trending,
            catalog = catalog,
            seasonProvider = { TrendSeason.CURRENT },
            sortProvider = { TrendSort.TRENDING },
        )

        val result = builder.build(testContext)
        result.size shouldBe 1
        result.first().title shouldBe "Another Fresh Novel"
        result.first().reason shouldBe "source"
    }

    @Test
    fun `globally ignored genre filtered from fallback source in trend builder`() = runTest {
        val trending = FakeTrending(shouldFail = true)
        val catalog = FakeCatalog(
            latestItems = listOf(
                DiscoveryRowItem(
                    "Clean Novel",
                    "clean novel",
                    null,
                    null,
                    null,
                    "InkStory",
                    0.0,
                    genres = listOf("Fantasy"),
                ),
                DiscoveryRowItem(
                    "Ignored Novel",
                    "ignored novel",
                    null,
                    null,
                    null,
                    "InkStory",
                    0.0,
                    genres = listOf("Ecchi"),
                ),
            ),
        )
        val builder = DiscoveryTrendRowBuilder(
            trending = trending,
            catalog = catalog,
            seasonProvider = { TrendSeason.CURRENT },
            sortProvider = { TrendSort.TRENDING },
        )

        val result = builder.build(testContext.copy(ignoredGenres = setOf("Ecchi")))
        result.map { it.title } shouldBe listOf("Clean Novel")
    }
}
