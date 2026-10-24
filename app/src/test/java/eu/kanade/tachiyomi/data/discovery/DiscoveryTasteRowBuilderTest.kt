package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import tachiyomi.domain.discovery.model.DiscoveryRowType
import java.io.IOException

class DiscoveryTasteRowBuilderTest {

    private class FakeTrending(
        private val genreItems: List<DiscoveryTrendingItem> = emptyList(),
        private val shouldFail: Boolean = false,
    ) : DiscoveryTrendingSource {
        var lastQueriedGenres: List<String>? = null
        var lastQueriedPage: Int? = null

        override suspend fun fetch(
            mediaType: DiscoveryMediaType,
            season: TrendSeason,
            sort: TrendSort,
            page: Int,
            releaseStatuses: Set<tachiyomi.domain.discovery.model.DiscoveryReleaseStatus>,
        ): List<DiscoveryTrendingItem> = emptyList()

        override suspend fun fetchByGenres(
            mediaType: DiscoveryMediaType,
            genres: List<String>,
            sort: TrendSort,
            page: Int,
            releaseStatuses: Set<tachiyomi.domain.discovery.model.DiscoveryReleaseStatus>,
        ): List<DiscoveryTrendingItem> {
            lastQueriedGenres = genres
            lastQueriedPage = page
            if (shouldFail) throw IOException("trending boom")
            return genreItems
        }

        override suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType): DiscoveryMeta? = null
    }

    private class FakeCatalog(
        private val genreItems: List<DiscoveryRowItem> = emptyList(),
        private val perSourceGenreItems: Map<Long, List<DiscoveryRowItem>> = emptyMap(),
        private val shouldFail: Boolean = false,
    ) : DiscoverySourceCatalog {
        override suspend fun popular(
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
        ): List<DiscoveryRowItem> {
            if (shouldFail) throw IOException("catalog boom")
            return perSourceGenreItems[sourceId] ?: genreItems
        }

        override suspend fun latest(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryRowItem> = emptyList()
    }

    private fun context(
        profile: List<Pair<String, Double>> = listOf("Fantasy" to 2.0),
        blacklistedTags: Set<String> = emptySet(),
    ) = DiscoveryBuildContext(
        mediaType = DiscoveryMediaType.MANGA,
        seeds = emptyList(),
        libraryCleanTitles = emptySet(),
        historyCleanTitles = emptySet(),
        hiddenCleanTitles = emptySet(),
        tasteProfile = profile,
        blacklistedTags = blacklistedTags,
        sourceId = 7L,
    )

    private fun builder(trending: DiscoveryTrendingSource, catalog: DiscoverySourceCatalog) =
        DiscoveryTasteRowBuilder(trending, catalog, sortProvider = { TrendSort.POPULARITY })

    @Test
    fun `empty taste profile yields empty row without calls`() = runTest {
        val result = builder(FakeTrending(shouldFail = true), FakeCatalog(shouldFail = true))
            .build(context(profile = emptyList()))
        result shouldBe emptyList()
    }

    @Test
    fun `trending items are scored by genre overlap and combined with filtered source items`() = runTest {
        val trending = FakeTrending(
            genreItems = listOf(
                DiscoveryTrendingItem("Matched", "matched", null, 1L, null, genres = listOf("Fantasy")),
                DiscoveryTrendingItem("Unmatched", "unmatched", null, 2L, null, genres = listOf("Comedy")),
            ),
        )
        val catalog = FakeCatalog(
            genreItems = listOf(DiscoveryRowItem("FromSource", "fromsource", null, null, null, "MangaHub", 1.0)),
        )
        val result = builder(trending, catalog).build(context())
        result.map { it.title } shouldBe listOf("Matched", "FromSource", "Unmatched")
        result.first { it.title == "Matched" }.score shouldBe 2.0
        result.first { it.title == "FromSource" }.reason shouldBe "Fantasy"
        result.first { it.title == "FromSource" }.score shouldBe 0.6
        result.first { it.title == "Unmatched" }.score shouldBe 0.0
    }

    @Test
    fun `both providers failed without results throws so cache is preserved`() = runTest {
        val result = runCatching {
            builder(FakeTrending(shouldFail = true), FakeCatalog(shouldFail = true)).build(context())
        }
        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe IOException("taste sources failed without results")
    }

    @Test
    fun `failed trending with successful catalog does not throw`() = runTest {
        val catalog = FakeCatalog(
            genreItems = listOf(DiscoveryRowItem("OnlySource", "onlysource", null, null, null, "MangaHub", 0.9)),
        )
        val result = builder(FakeTrending(shouldFail = true), catalog).build(context())
        result.map { it.title } shouldBe listOf("OnlySource")
    }

    @Test
    fun `builder row type is TASTE`() {
        builder(FakeTrending(), FakeCatalog()).rowType shouldBe DiscoveryRowType.TASTE
    }

    @Test
    fun `blacklisted genre is excluded from profile and item filter`() = runTest {
        val trending = FakeTrending(
            genreItems = listOf(
                DiscoveryTrendingItem("Fantasy Item", "fantasy item", null, 1L, null, genres = listOf("Fantasy")),
                DiscoveryTrendingItem("Action Item", "action item", null, 2L, null, genres = listOf("Action")),
            ),
        )
        val result = builder(trending, FakeCatalog()).build(
            context(profile = listOf("Fantasy" to 2.0, "Action" to 1.0), blacklistedTags = setOf("фэнтези")),
        )
        result.map { it.title } shouldBe listOf("Action Item")
    }

    @Test
    fun `fully blacklisted profile yields empty row without calls`() = runTest {
        val result = builder(FakeTrending(shouldFail = true), FakeCatalog(shouldFail = true)).build(
            context(profile = listOf("Fantasy" to 2.0), blacklistedTags = setOf("Fantasy")),
        )
        result shouldBe emptyList()
    }

    @Test
    fun `includeExternal false builds from catalog only and ignores trending failure`() = runTest {
        // «Только плагины»: внешний провайдер не опрашивается вовсе — даже его
        // падение не считается ошибкой ряда; выдача только из каталога.
        val trending = FakeTrending(
            genreItems = listOf(
                DiscoveryTrendingItem("External Pick", "external pick", null, 1L, null, genres = listOf("Fantasy")),
            ),
            shouldFail = true,
        )
        val catalog = FakeCatalog(
            genreItems = listOf(DiscoveryRowItem("Plugin Pick", "plugin pick", null, null, null, "MangaHub", 1.0)),
        )
        val result = DiscoveryTasteRowBuilder(
            trending,
            catalog,
            sortProvider = { TrendSort.POPULARITY },
            includeExternal = false,
        ).build(context())
        result.map { it.title } shouldBe listOf("Plugin Pick")
    }

    @Test
    fun `includeExternal false with empty catalog returns empty without throwing`() = runTest {
        // Плагин-каталог пуст и падает: ряда нет, но и ложного «failed» нет —
        // вызов внешних отключён, пустой результат валиден.
        val result = runCatching {
            DiscoveryTasteRowBuilder(
                FakeTrending(shouldFail = true),
                FakeCatalog(shouldFail = true),
                sortProvider = { TrendSort.POPULARITY },
                includeExternal = false,
            ).build(context())
        }
        result.getOrDefault(emptyList()) shouldBe emptyList()
    }

    @Test
    fun `includeExternal false queries multiple sources and interleaves results`() = runTest {
        val catalog = FakeCatalog(
            perSourceGenreItems = mapOf(
                10L to listOf(
                    DiscoveryRowItem("Src10 Item 1", "src10 item 1", null, null, null, "Src10", 1.0, sourceId = 10L),
                    DiscoveryRowItem("Src10 Item 2", "src10 item 2", null, null, null, "Src10", 0.9, sourceId = 10L),
                ),
                20L to listOf(
                    DiscoveryRowItem("Src20 Item 1", "src20 item 1", null, null, null, "Src20", 1.0, sourceId = 20L),
                    DiscoveryRowItem("Src20 Item 2", "src20 item 2", null, null, null, "Src20", 0.9, sourceId = 20L),
                ),
            ),
        )
        val ctx = context().copy(
            sourceId = 10L,
            sourceIds = listOf(10L, 20L),
        )
        val result = DiscoveryTasteRowBuilder(
            trending = FakeTrending(shouldFail = true),
            catalog = catalog,
            sortProvider = { TrendSort.POPULARITY },
            includeExternal = false,
        ).build(ctx)

        result.map { it.title } shouldBe listOf("Src10 Item 1", "Src20 Item 1", "Src10 Item 2", "Src20 Item 2")
    }

    @Test
    fun `includeExternal true queries multiple sources and interleaves results with external trends`() = runTest {
        val catalog = FakeCatalog(
            perSourceGenreItems = mapOf(
                10L to listOf(
                    DiscoveryRowItem("Src10 Item 1", "src10 item 1", null, null, null, "Src10", 1.0, sourceId = 10L),
                ),
                20L to listOf(
                    DiscoveryRowItem("Src20 Item 1", "src20 item 1", null, null, null, "Src20", 1.0, sourceId = 20L),
                ),
            ),
        )
        val ctx = context().copy(
            sourceId = 10L,
            sourceIds = listOf(10L, 20L),
        )
        val result = DiscoveryTasteRowBuilder(
            trending = FakeTrending(shouldFail = true),
            catalog = catalog,
            sortProvider = { TrendSort.POPULARITY },
            includeExternal = true,
        ).build(ctx)

        result.map { it.title } shouldBe listOf("Src10 Item 1", "Src20 Item 1")
    }

    @Test
    fun `empty taste profile with required genres builds TASTE row from explicit genres`() = runTest {
        val trending = FakeTrending(
            genreItems = listOf(
                DiscoveryTrendingItem("Cyber Love", "cyber love", null, 1L, null, genres = listOf("Cyberpunk")),
            ),
        )
        val spec = GenreFilterSpec(
            requiredGenres = setOf("Cyberpunk"),
            requiredEnforcement = RequiredEnforcement.STRICT,
        )
        val ctx = context(profile = emptyList()).copy(
            requiredGenres = setOf("Cyberpunk"),
            genreFilterSpec = spec,
        )
        val result = builder(trending, FakeCatalog()).build(ctx)
        result.map { it.title } shouldBe listOf("Cyber Love")
        trending.lastQueriedGenres shouldBe listOf("Cyberpunk")
    }

    @Test
    fun `explicit required and priority genres take precedence over library profile in external query`() = runTest {
        val trending = FakeTrending(
            genreItems = listOf(
                DiscoveryTrendingItem("Item", "item", null, 1L, null, genres = listOf("Sci-Fi")),
            ),
        )
        val ctx = context(profile = listOf("Action" to 5.0, "Comedy" to 4.0)).copy(
            requiredGenres = setOf("Sci-Fi"),
            priorityGenres = setOf("Mecha"),
        )
        builder(trending, FakeCatalog()).build(ctx)
        trending.lastQueriedGenres shouldBe listOf("Sci-Fi")
    }
}
