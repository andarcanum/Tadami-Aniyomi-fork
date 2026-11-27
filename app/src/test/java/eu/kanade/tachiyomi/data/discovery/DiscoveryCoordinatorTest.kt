package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryExternalProvider
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySourcePolicy
import java.io.IOException

class DiscoveryCoordinatorTest {

    private fun item(title: String, score: Double = 0.0) = DiscoveryRowItem(
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = null,
        reason = null,
        seedTitle = null,
        provider = "test",
        score = score,
        sourceId = 7L,
        sourceUrl = "/${title.lowercase()}",
    )

    private fun builder(type: DiscoveryRowType, items: List<DiscoveryRowItem>, fail: Boolean = false) =
        object : DiscoveryRowBuilder {
            override val rowType = type
            override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
                if (fail) throw IOException("boom")
                return items
            }
        }

    private fun sourcePolicy(
        mediaType: DiscoveryMediaType = DiscoveryMediaType.NOVEL,
        inventoryComplete: Boolean = true,
    ) = DiscoverySourcePolicy(
        mediaType = mediaType,
        revision = "fixture-${mediaType.key}",
        inventoryComplete = inventoryComplete,
        allowedPluginKeys = setOf("fixture"),
        pluginMembers = mapOf("fixture" to setOf(7L)),
        representativeSourceIds = listOf(7L),
        allowedExternalProviders = mapOf(
            DiscoveryRowType.LIKE to setOf(DiscoveryExternalProvider.ANILIST),
            DiscoveryRowType.TASTE to setOf(DiscoveryExternalProvider.ANILIST),
            DiscoveryRowType.TREND to setOf(DiscoveryExternalProvider.ANILIST),
        ),
    )

    private val baseContext = DiscoveryBuildContext(
        mediaType = DiscoveryMediaType.NOVEL,
        seeds = emptyList(),
        libraryCleanTitles = emptySet(),
        historyCleanTitles = emptySet(),
        hiddenCleanTitles = emptySet(),
        sourcePolicy = sourcePolicy(),
    )

    @Test
    fun `best effort excludes a known exact match before choosing the eligible fallback`() = runTest {
        val known = item("Known fantasy").copy(
            provider = "anilist",
            sourceId = null,
            sourceUrl = null,
            genres = listOf("Fantasy"),
        )
        val fallback = item("New drama").copy(
            provider = "anilist",
            sourceId = null,
            sourceUrl = null,
            genres = listOf("Drama"),
        )
        val like = builder(DiscoveryRowType.LIKE, listOf(known, fallback))

        val feed = DiscoveryCoordinator(listOf(like)).buildFeed(
            baseContext.copy(
                libraryCleanTitles = setOf("known fantasy"),
                genreFilterSpec = GenreFilterSpec(
                    requiredGenres = setOf("Fantasy"),
                    requiredEnforcement = RequiredEnforcement.BEST_EFFORT,
                ),
            ),
        )

        feed.rows[DiscoveryRowType.LIKE]?.map { it.title } shouldBe listOf("New drama")
        feed.failedRows shouldBe emptySet()
    }

    @Test
    fun `best effort chooses fallback after history hidden current and exposure exclusions`() = runTest {
        val known = item("Known fantasy").copy(genres = listOf("Fantasy"))
        val fallback = item("New drama").copy(genres = listOf("Drama"))
        val context = baseContext.copy(
            genreFilterSpec = GenreFilterSpec(
                requiredGenres = setOf("Fantasy"),
                requiredEnforcement = RequiredEnforcement.BEST_EFFORT,
            ),
        )
        val excludedContexts = listOf(
            context.copy(historyCleanTitles = setOf("known fantasy")),
            context.copy(hiddenCleanTitles = setOf("known fantasy")),
            context.copy(currentFeedCleanTitles = setOf("known fantasy")),
            context.copy(recentCleanTitles = setOf("known fantasy")),
        )

        for (excludedContext in excludedContexts) {
            val feed = DiscoveryCoordinator(listOf(builder(DiscoveryRowType.LIKE, listOf(known, fallback))))
                .buildFeed(excludedContext)
            feed.rows[DiscoveryRowType.LIKE]?.map { it.title } shouldBe listOf("New drama")
        }
    }

    @Test
    fun `best effort excludes a known alias before applying the row cap`() = runTest {
        val known = item("Known fantasy").copy(
            genres = listOf("Fantasy"),
            altCleanTitles = listOf("library alias"),
        )
        val candidates = listOf(
            known,
            item("New drama").copy(genres = listOf("Drama")),
            item("Other drama").copy(genres = listOf("Drama")),
        )
        val feed = DiscoveryCoordinator(listOf(builder(DiscoveryRowType.LIKE, candidates)), rowLimit = 1)
            .buildFeed(
                baseContext.copy(
                    libraryCleanTitles = setOf("library alias"),
                    genreFilterSpec = GenreFilterSpec(
                        requiredGenres = setOf("Fantasy"),
                        requiredEnforcement = RequiredEnforcement.BEST_EFFORT,
                    ),
                ),
            )

        feed.rows[DiscoveryRowType.LIKE]?.map { it.title } shouldBe listOf("New drama")
    }

    @Test
    fun `best effort lower row falls back after an exact match is claimed by LIKE`() = runTest {
        val exact = item("Shared fantasy").copy(genres = listOf("Fantasy"))
        val fallback = item("New drama").copy(genres = listOf("Drama"))
        val feed = DiscoveryCoordinator(
            listOf(
                builder(DiscoveryRowType.TASTE, listOf(exact, fallback)),
                builder(DiscoveryRowType.LIKE, listOf(exact)),
            ),
        ).buildFeed(
            baseContext.copy(
                genreFilterSpec = GenreFilterSpec(
                    requiredGenres = setOf("Fantasy"),
                    requiredEnforcement = RequiredEnforcement.BEST_EFFORT,
                ),
            ),
        )

        feed.rows[DiscoveryRowType.LIKE]?.map { it.title } shouldBe listOf("Shared fantasy")
        feed.rows[DiscoveryRowType.TASTE]?.map { it.title } shouldBe listOf("New drama")
    }

    @Test
    fun `strict required and forbidden veto are preserved after eligibility filtering`() = runTest {
        val known = item("Known fantasy").copy(genres = listOf("Fantasy"))
        val forbiddenFallback = item("Forbidden drama").copy(genres = listOf("Drama", "Hentai"))
        val like = builder(DiscoveryRowType.LIKE, listOf(known, forbiddenFallback))
        val context = baseContext.copy(libraryCleanTitles = setOf("known fantasy"))

        val strict = DiscoveryCoordinator(listOf(like)).buildFeed(
            context.copy(genreFilterSpec = GenreFilterSpec(requiredGenres = setOf("Fantasy"))),
        )
        strict.rows[DiscoveryRowType.LIKE].orEmpty() shouldBe emptyList()

        val bestEffort = DiscoveryCoordinator(listOf(like)).buildFeed(
            context.copy(
                blacklistedTags = setOf("Hentai"),
                genreFilterSpec = GenreFilterSpec(
                    requiredGenres = setOf("Fantasy"),
                    requiredEnforcement = RequiredEnforcement.BEST_EFFORT,
                ),
            ),
        )
        bestEffort.rows[DiscoveryRowType.LIKE].orEmpty() shouldBe emptyList()
    }

    @Test
    fun `unselected native exact match cannot consume the best effort tier`() = runTest {
        val unselected = item("Excluded fantasy").copy(sourceId = 99L, genres = listOf("Fantasy"))
        val allowed = item("New drama").copy(genres = listOf("Drama"))
        val feed = DiscoveryCoordinator(listOf(builder(DiscoveryRowType.LIKE, listOf(unselected, allowed))))
            .buildFeed(
                baseContext.copy(
                    genreFilterSpec = GenreFilterSpec(
                        requiredGenres = setOf("Fantasy"),
                        requiredEnforcement = RequiredEnforcement.BEST_EFFORT,
                    ),
                ),
            )

        feed.rows[DiscoveryRowType.LIKE]?.map { it.title } shouldBe listOf("New drama")
    }

    @Test
    fun `unselected higher row cannot deduplicate a selected native alternative`() = runTest {
        val unselected = item("Shared story").copy(sourceId = 99L)
        val selected = item("Shared story")
        val feed = DiscoveryCoordinator(
            listOf(
                builder(DiscoveryRowType.LIKE, listOf(unselected)),
                builder(DiscoveryRowType.SOURCE, listOf(selected)),
            ),
        ).buildFeed(baseContext)

        feed.rows[DiscoveryRowType.LIKE].orEmpty() shouldBe emptyList()
        feed.rows[DiscoveryRowType.SOURCE]?.map { it.title } shouldBe listOf("Shared story")
    }

    @Test
    fun `unavailable native inventory still permits explicitly enabled external candidates`() = runTest {
        val native = item("Native story")
        val external = item("External story").copy(provider = "anilist", sourceId = null, sourceUrl = null)
        val feed = DiscoveryCoordinator(listOf(builder(DiscoveryRowType.LIKE, listOf(native, external))))
            .buildFeed(baseContext.copy(sourcePolicy = sourcePolicy(inventoryComplete = false)))

        feed.rows[DiscoveryRowType.LIKE]?.map { it.title } shouldBe listOf("External story")
    }

    @Test
    fun `library history and hidden titles are filtered out`() = runTest {
        val like = builder(DiscoveryRowType.LIKE, listOf(item("Overlord"), item("Hidden One"), item("In Library")))
        val coordinator = DiscoveryCoordinator(listOf(like))
        val feed = coordinator.buildFeed(
            baseContext.copy(
                libraryCleanTitles = setOf("in library"),
                hiddenCleanTitles = setOf("hidden one"),
            ),
        )
        feed.rows[DiscoveryRowType.LIKE]?.map { it.title } shouldBe listOf("Overlord")
        feed.failedRows shouldBe emptySet()
    }

    @Test
    fun `cross-row dedup keeps higher priority row`() = runTest {
        val like = builder(DiscoveryRowType.LIKE, listOf(item("Dup")))
        val trend = builder(DiscoveryRowType.TREND, listOf(item("Dup"), item("Unique Trend")))
        val feed = DiscoveryCoordinator(listOf(trend, like)).buildFeed(baseContext)
        feed.rows[DiscoveryRowType.LIKE]?.map { it.title } shouldBe listOf("Dup")
        feed.rows[DiscoveryRowType.TREND]?.map { it.title } shouldBe listOf("Unique Trend")
    }

    @Test
    fun `row limit caps items preserving order`() = runTest {
        val like = builder(DiscoveryRowType.LIKE, (1..25).map { item("T$it") })
        val feed = DiscoveryCoordinator(listOf(like), rowLimit = 20).buildFeed(baseContext)
        feed.rows[DiscoveryRowType.LIKE]?.size shouldBe 20
    }

    @Test
    fun `no stale backfill is added to a fresh row`() = runTest {
        val like = builder(
            DiscoveryRowType.LIKE,
            listOf(item("Fresh"), item("ShownRecent"), item("NoTimestamp"), item("ShownOld")),
        )
        val feed = DiscoveryCoordinator(listOf(like), rowLimit = 10).buildFeed(
            baseContext.copy(
                recentCleanTitles = setOf("shownrecent", "notimestamp", "shownold"),
                shownCutoffMap = mapOf(
                    "shownrecent" to 2_000L,
                    "shownold" to 1_000L,
                ),
            ),
        )
        feed.rows[DiscoveryRowType.LIKE]?.map { it.title } shouldBe listOf("Fresh")
    }

    @Test
    fun `manual refresh never backfills previously shown titles`() = runTest {
        // Previously shown titles are excluded, not backfilled: a full generation
        // only keeps genuinely fresh candidates.
        val like = builder(
            DiscoveryRowType.LIKE,
            listOf(item("Fresh A"), item("Current B"), item("Fallen C")),
        )
        val feed = DiscoveryCoordinator(listOf(like), rowLimit = 10).buildFeed(
            baseContext.copy(
                recentCleanTitles = setOf("current b", "fallen c"),
                shownCutoffMap = mapOf("current b" to 2_000L, "fallen c" to 1_000L),
                currentFeedCleanTitles = setOf("current b"),
            ),
        )
        feed.rows[DiscoveryRowType.LIKE]?.map { it.title } shouldBe listOf("Fresh A")

        // Фон (currentFeed пуст): прежнее поведение — добор возвращает обоих.
        val backgroundFeed = DiscoveryCoordinator(listOf(like), rowLimit = 10).buildFeed(
            baseContext.copy(
                recentCleanTitles = setOf("current b", "fallen c"),
                shownCutoffMap = mapOf("current b" to 2_000L, "fallen c" to 1_000L),
            ),
        )
        backgroundFeed.rows[DiscoveryRowType.LIKE]?.map { it.title } shouldBe listOf("Fresh A")
    }

    @Test
    fun `feed unions source ids across rows without double counting`() = runTest {
        val taste = object : DiscoveryRowBuilder {
            override val rowType = DiscoveryRowType.TASTE
            override val failedSourceIds = setOf(7L)
            override val degradedSourceIds = setOf(8L)
            override val successfulSourceIds = setOf(9L, 10L)
            override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> = emptyList()
        }
        val source = object : DiscoveryRowBuilder {
            override val rowType = DiscoveryRowType.SOURCE
            override val failedSourceIds = setOf(7L)
            override val degradedSourceIds = setOf(8L)
            override val successfulSourceIds = setOf(10L, 11L)
            override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> = emptyList()
        }

        val feed = DiscoveryCoordinator(listOf(taste, source)).buildFeed(baseContext)

        feed.failedSourceIds shouldBe setOf(7L)
        feed.degradedSourceIds shouldBe setOf(8L)
        feed.successfulSourceIds shouldBe setOf(9L, 10L, 11L)
    }

    @Test
    fun `failing builder yields empty row and failedRows marker, others survive`() = runTest {
        val like = builder(DiscoveryRowType.LIKE, emptyList(), fail = true)
        val trend = builder(DiscoveryRowType.TREND, listOf(item("Ok")))
        val feed = DiscoveryCoordinator(listOf(like, trend)).buildFeed(baseContext)
        feed.failedRows shouldBe setOf(DiscoveryRowType.LIKE)
        feed.rows[DiscoveryRowType.TREND]?.map { it.title } shouldBe listOf("Ok")
    }

    @Test
    fun `item matching library by alt title is excluded`() = runTest {
        val item = DiscoveryRowItem(
            title = "Kimetsu no Yaiba",
            cleanTitle = "kimetsu no yaiba",
            coverUrl = null,
            reason = null,
            seedTitle = null,
            provider = "anilist",
            score = 1.0,
            altCleanTitles = listOf("клинок рассекающий демонов"),
        )
        val context = DiscoveryBuildContext(
            mediaType = DiscoveryMediaType.MANGA,
            seeds = emptyList(),
            libraryCleanTitles = setOf("клинок рассекающий демонов"),
            historyCleanTitles = emptySet(),
            hiddenCleanTitles = emptySet(),
            sourcePolicy = sourcePolicy(DiscoveryMediaType.MANGA),
        )
        val feed = DiscoveryCoordinator(listOf(builder(DiscoveryRowType.TREND, listOf(item)))).buildFeed(context)
        feed.rows[DiscoveryRowType.TREND].orEmpty() shouldBe emptyList()
    }

    @Test
    fun `blacklisted candidate in higher row is filtered before dedup so lower row survives`() = runTest {
        val likeItem = DiscoveryRowItem(
            title = "Shared Novel",
            cleanTitle = "shared novel",
            coverUrl = null,
            reason = null,
            seedTitle = null,
            provider = "anilist",
            score = 10.0,
            genres = listOf("Hentai"),
        )
        val trendItem = DiscoveryRowItem(
            title = "Shared Novel",
            cleanTitle = "shared novel",
            coverUrl = null,
            reason = null,
            seedTitle = null,
            provider = "anilist_trend",
            score = 1.0,
            genres = listOf("Fantasy"),
        )

        val like = builder(DiscoveryRowType.LIKE, listOf(likeItem))
        val trend = builder(DiscoveryRowType.TREND, listOf(trendItem))

        val context = baseContext.copy(
            blacklistedTags = setOf("Hentai"),
        )
        val feed = DiscoveryCoordinator(listOf(like, trend)).buildFeed(context)

        feed.rows[DiscoveryRowType.LIKE]?.map { it.title }.orEmpty() shouldBe emptyList()
        feed.rows[DiscoveryRowType.TREND]?.map { it.title } shouldBe listOf("Shared Novel")
    }
}
