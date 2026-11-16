package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryExternalProvider
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySourcePolicy
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

class DiscoveryTasteRowBuilderTest {

    private class FakeTrending(
        private val genreItems: List<DiscoveryTrendingItem> = emptyList(),
        private val pageItems: Map<Int, List<DiscoveryTrendingItem>> = emptyMap(),
        private val shouldFail: Boolean = false,
    ) : DiscoveryTrendingSource {
        var lastQueriedGenres: List<String>? = null
        var lastQueriedPage: Int? = null
        val queriedPages = mutableListOf<Int>()

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
            queriedPages += page
            return pageItems[page] ?: genreItems
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
            return (perSourceGenreItems[sourceId] ?: genreItems).map {
                it.copy(sourceId = sourceId, sourceUrl = it.sourceUrl ?: "/fixture/" + it.cleanTitle)
            }
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
        sourcePolicy = discoveryQueryPolicyFixture(
            DiscoveryMediaType.MANGA,
            listOf(7L),
            mapOf(DiscoveryRowType.TASTE to setOf(DiscoveryExternalProvider.ANILIST)),
        ),
    )

    private fun builder(trending: DiscoveryTrendingSource, catalog: DiscoverySourceCatalog) =
        DiscoveryTasteRowBuilder(trending, catalog, sortProvider = { TrendSort.POPULARITY })

    @Test
    fun `soft priority preserves novel interests with empty required genres and default ALL`(): Unit =
        runBlocking {
            val requestedGenres = CopyOnWriteArrayList<List<String>>()
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val buffer = Buffer()
                    chain.request().body!!.writeTo(buffer)
                    val variables = Json.parseToJsonElement(buffer.readUtf8()).jsonObject
                        .getValue("variables").jsonObject
                    val genres = variables.getValue("genres").jsonArray.map { it.jsonPrimitive.content }
                    requestedGenres += genres
                    val media = buildList {
                        if (genres.any { it.equals("Action", ignoreCase = true) }) {
                            add(
                                """{"id":101,"title":{"romaji":"Action interest","english":null,"native":null},
                                  "coverImage":{"large":null},"genres":["Action"]}""",
                            )
                        }
                        if (genres.any { it.equals("Romance", ignoreCase = true) }) {
                            add(
                                """{"id":102,"title":{"romaji":"Romance interest","english":null,"native":null},
                                  "coverImage":{"large":null},"genres":["Romance"]}""",
                            )
                        }
                    }
                    val body = "{\"data\":{\"Page\":{\"media\":[" + media.joinToString(",") + "]}}}"
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build()
            try {
                val sourcePolicy = DiscoverySourcePolicy(
                    mediaType = DiscoveryMediaType.NOVEL,
                    revision = "novel-soft-interest-fixture",
                    inventoryComplete = true,
                    allowedPluginKeys = emptySet(),
                    pluginMembers = emptyMap(),
                    allowedExternalProviders = mapOf(
                        DiscoveryRowType.TASTE to setOf(DiscoveryExternalProvider.ANILIST),
                    ),
                )
                val anilist = AniListTrendingSource(
                    clientProvider = { client },
                    jsonProvider = { Json },
                    nsfwFilterProvider = { true },
                )
                val builder = DiscoveryTasteRowBuilder(anilist, FakeCatalog(), { TrendSort.POPULARITY })
                val context = DiscoveryBuildContext(
                    mediaType = DiscoveryMediaType.NOVEL,
                    seeds = emptyList(),
                    libraryCleanTitles = emptySet(),
                    historyCleanTitles = emptySet(),
                    hiddenCleanTitles = emptySet(),
                    tasteProfile = listOf("Romance" to 2.0),
                    genreFilterSpec = GenreFilterSpec(
                        priorityGenres = setOf("Action"),
                        requiredGenres = emptySet(),
                    ),
                    sourcePolicy = sourcePolicy,
                    sourceIds = sourcePolicy.representativeSourceIds,
                    verificationBudget = DiscoveryVerificationBudget(budgetMs = 5_000, maxDetails = 0),
                )

                val items = withTimeout(5_000L) { builder.build(context) }

                items.map { it.title }.toSet() shouldBe setOf("Action interest", "Romance interest")
                items.all { it.sourceId == null } shouldBe true
                requestedGenres.flatten().any { it.equals("Romance", ignoreCase = true) } shouldBe true
            } finally {
                client.dispatcher.executorService.shutdownNow()
                client.connectionPool.evictAll()
            }
        }

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
            sourcePolicy = discoveryQueryPolicyFixture(
                DiscoveryMediaType.MANGA,
                listOf(10L, 20L),
                mapOf(DiscoveryRowType.TASTE to setOf(DiscoveryExternalProvider.ANILIST)),
            ),
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
            sourcePolicy = discoveryQueryPolicyFixture(
                DiscoveryMediaType.MANGA,
                listOf(10L, 20L),
                mapOf(DiscoveryRowType.TASTE to setOf(DiscoveryExternalProvider.ANILIST)),
            ),
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

    @Test
    fun `strict required taste pages external provider until rich target`() = runTest {
        val trending = FakeTrending(
            pageItems = (1..3).associateWith { page ->
                (1..10).map { index ->
                    val title = "Page $page Item $index"
                    DiscoveryTrendingItem(
                        title,
                        title.lowercase(),
                        null,
                        index.toLong(),
                        null,
                        genres = listOf("Milf"),
                    )
                }
            },
        )
        val spec = GenreFilterSpec(
            requiredGenres = setOf("Milf"),
            requiredEnforcement = RequiredEnforcement.STRICT,
        )
        val ctx = context(profile = emptyList()).copy(
            requiredGenres = setOf("Milf"),
            genreFilterSpec = spec,
        )

        val result = builder(trending, FakeCatalog()).build(ctx)

        result.size shouldBe RICH_SOURCE_TARGET
        trending.queriedPages shouldBe listOf(1, 2, 3)
    }

    @Test
    fun `repeated external page stops required paging`() = runTest {
        val repeated = (1..10).map { index ->
            DiscoveryTrendingItem("Item $index", "item $index", null, index.toLong(), null, genres = listOf("Milf"))
        }
        val trending = FakeTrending(
            pageItems = mapOf(1 to repeated, 2 to repeated, 3 to repeated),
        )
        val spec = GenreFilterSpec(
            requiredGenres = setOf("Milf"),
            requiredEnforcement = RequiredEnforcement.STRICT,
        )
        val ctx = context(profile = emptyList()).copy(
            requiredGenres = setOf("Milf"),
            genreFilterSpec = spec,
        )

        val result = builder(trending, FakeCatalog()).build(ctx)

        result.size shouldBe 10
        trending.queriedPages shouldBe listOf(1, 2)
    }

    @Test
    fun `best effort required taste keeps paging external provider past page one non-matches`() =
        runTest {
            val page1NonMatches = (1..30).map { index ->
                DiscoveryTrendingItem(
                    "Drama $index",
                    "drama $index",
                    null,
                    index.toLong(),
                    null,
                    genres = listOf("Drama"),
                )
            }
            val page2Matches = (1..30).map { index ->
                DiscoveryTrendingItem(
                    "Milf $index",
                    "milf $index",
                    null,
                    100L + index,
                    null,
                    genres = listOf("Milf"),
                )
            }
            val trending = FakeTrending(
                pageItems = mapOf(1 to page1NonMatches, 2 to page2Matches),
            )
            val spec = GenreFilterSpec(
                requiredGenres = setOf("Milf"),
                requiredEnforcement = RequiredEnforcement.BEST_EFFORT,
            )
            val ctx = context(profile = emptyList()).copy(
                requiredGenres = setOf("Milf"),
                genreFilterSpec = spec,
            )

            val result = builder(trending, FakeCatalog()).build(ctx)

            trending.queriedPages shouldBe listOf(1, 2)
            result.take(30).all { "Milf" in it.genres } shouldBe true
        }

    @Test
    fun `ordinary taste row keeps non-required native query unsplit and unhydrated even with active budget`() =
        runTest {
            var calls = 0
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
                    calls++
                    return emptyList()
                }

                override suspend fun latest(
                    mediaType: DiscoveryMediaType,
                    sourceId: Long,
                    page: Int,
                    releaseStatuses: Set<DiscoveryReleaseStatus>,
                ) = emptyList<DiscoveryRowItem>()
            }
            val ctx = context(profile = listOf("Action" to 5.0, "Comedy" to 4.0, "Drama" to 3.0)).copy(
                verificationBudget = DiscoveryVerificationBudget(budgetMs = 5_000L, maxDetails = 10),
            )
            DiscoveryTasteRowBuilder(FakeTrending(), catalog, { TrendSort.POPULARITY }, includeExternal = false)
                .build(ctx)

            calls shouldBe 1
        }

    @Test
    fun `split queries without item genres use queried genre not head profile genres`() = runTest {
        val trending = object : DiscoveryTrendingSource {
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
            ) = when (genres.singleOrNull()) {
                "Mystery" -> listOf(
                    DiscoveryTrendingItem("Shikimori Mystery", "shikimori mystery", null, 1L, null, emptyList()),
                )
                else -> emptyList()
            }

            override suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType): DiscoveryMeta? = null
        }
        val profile = listOf("Action" to 5.0, "Comedy" to 4.0, "Mystery" to 3.0)
        val result = builder(trending, FakeCatalog()).build(context(profile = profile))
        result.single { it.title == "Shikimori Mystery" }.reason shouldBe "Mystery"
    }

    @Test
    fun `fallback items with disjoint genres do not fabricate top profile genres in reason`() = runTest {
        val trending = FakeTrending(
            genreItems = listOf(
                DiscoveryTrendingItem("Drama Fallback", "drama fallback", null, 1L, null, genres = listOf("Drama")),
                DiscoveryTrendingItem("Action Match", "action match", null, 2L, null, genres = listOf("Action")),
            ),
        )
        val spec = GenreFilterSpec(
            requiredGenres = setOf("Action", "Fantasy"),
            requiredMatchMode = RequiredMatchMode.ANY,
            requiredEnforcement = RequiredEnforcement.BEST_EFFORT,
        )
        val ctx = context(profile = listOf("Action" to 5.0, "Fantasy" to 4.0)).copy(
            requiredGenres = setOf("Action", "Fantasy"),
            genreFilterSpec = spec,
        )
        val result = builder(trending, FakeCatalog()).build(ctx)
        result.single { it.title == "Action Match" }.reason shouldBe "Action"
        result.single { it.title == "Drama Fallback" }.reason shouldBe null
    }
}
