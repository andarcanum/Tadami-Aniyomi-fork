package eu.kanade.tachiyomi.data.discovery

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import data.Chapters
import data.History
import data.Mangas
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.suggestions.SuggestionCoordinator
import eu.kanade.tachiyomi.data.suggestions.SuggestionFetchResult
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import eu.kanade.tachiyomi.ui.discovery.DiscoveryFeedScreenModel
import eu.kanade.tachiyomi.ui.discovery.FeedSignalTab
import eu.kanade.tachiyomi.ui.discovery.countBlacklistImpact
import eu.kanade.tachiyomi.ui.home.HomeDiscoveryController
import eu.kanade.tachiyomi.ui.home.HomeHubDiscoveryItem
import eu.kanade.tachiyomi.ui.home.HomeHubUiState
import eu.kanade.tachiyomi.ui.home.composeTeaserItems
import eu.kanade.tachiyomi.ui.home.patchTeaserItems
import eu.kanade.tachiyomi.util.system.isRunningOrEnqueuedFlow
import eu.kanade.tachiyomi.util.system.workManager
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.discovery.DiscoveryRepositoryImpl
import tachiyomi.data.handlers.manga.AndroidMangaDatabaseHandler
import tachiyomi.domain.discovery.model.DiscoveryEligibilitySnapshot
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryPreparedPool
import tachiyomi.domain.discovery.model.DiscoveryPublishReceipt
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import tachiyomi.domain.discovery.model.DiscoveryRetirementPolicy
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.model.DiscoveryTitleIdentity
import tachiyomi.domain.discovery.model.DiscoveryTopUpReceipt
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle
import tachiyomi.domain.discovery.repository.DiscoveryKnownTitlesProvider
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryAdversarialStressTest {

    private fun suggestion(
        title: String,
        rowType: DiscoveryRowType = DiscoveryRowType.TREND,
        mediaType: DiscoveryMediaType = DiscoveryMediaType.MANGA,
        genres: List<String> = emptyList(),
        reason: String? = null,
        score: Double = 1.0,
        position: Long = 0L,
        provider: String = "test",
        sourceId: Long? = 1L,
        sourceUrl: String? = "/$title",
        trustedAliases: Set<String> = emptySet(),
    ): DiscoverySuggestion = DiscoverySuggestion(
        id = position + 1L,
        mediaType = mediaType,
        rowType = rowType,
        title = title,
        cleanTitle = normalizeDiscoveryTitle(title),
        coverUrl = "cover_$title",
        reason = reason,
        seedTitle = if (rowType == DiscoveryRowType.LIKE) "Seed" else null,
        provider = provider,
        score = score,
        position = position,
        createdAt = 1_000L,
        sourceId = sourceId,
        sourceUrl = sourceUrl,
        genres = genres,
        identityKey = "${mediaType.key}:${normalizeDiscoveryTitle(title)}",
        trustedAliases = trustedAliases,
    )

    private fun rowItem(
        title: String,
        genres: List<String> = emptyList(),
        reason: String? = null,
        score: Double = 1.0,
        provider: String = "test",
        sourceId: Long? = 1L,
        sourceUrl: String? = "/$title",
        altCleanTitles: List<String> = emptyList(),
    ): DiscoveryRowItem = DiscoveryRowItem(
        title = title,
        cleanTitle = normalizeDiscoveryTitle(title),
        coverUrl = "cover_$title",
        reason = reason,
        seedTitle = null,
        provider = provider,
        score = score,
        sourceId = sourceId,
        sourceUrl = sourceUrl,
        genres = genres,
        altCleanTitles = altCleanTitles,
    )

    private class FlowDiscoveryRepo : FakeDiscoveryRepositoryBase() {
        val poolFlow = MutableStateFlow<List<DiscoverySuggestion>>(emptyList())
        val hiddenFlow = MutableStateFlow<Set<String>>(emptySet())
        val consumedFlow = MutableStateFlow<Set<String>>(emptySet())
        val blacklistFlow = MutableStateFlow<Set<String>>(emptySet())

        override fun subscribe(mediaType: DiscoveryMediaType): Flow<List<DiscoverySuggestion>> = poolFlow
        override fun subscribeHidden(mediaType: DiscoveryMediaType): Flow<Set<String>> = hiddenFlow
        override fun subscribeConsumed(mediaType: DiscoveryMediaType): Flow<Set<String>> = consumedFlow
        override fun subscribeBlacklist(mediaType: DiscoveryMediaType): Flow<Set<String>> = blacklistFlow
        override suspend fun getHiddenTitles(mediaType: DiscoveryMediaType): Set<String> = hiddenFlow.value
        override suspend fun getBlacklistedTags(mediaType: DiscoveryMediaType): Set<String> = blacklistFlow.value
    }

    private suspend fun waitUntil(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                throw AssertionError("Condition not met within ${timeoutMs}ms")
            }
            kotlinx.coroutines.delay(10)
        }
    }

    private fun newDbRepo(preferences: DiscoveryPreferences): Pair<JdbcSqliteDriver, DiscoveryRepositoryImpl> {
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        val database = Database(
            driver = driver,
            chaptersAdapter = Chapters.Adapter(memoAdapter = MemoColumnAdapter),
            historyAdapter = History.Adapter(last_readAdapter = DateColumnAdapter),
            mangasAdapter = Mangas.Adapter(
                memoAdapter = MemoColumnAdapter,
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
                custom_genreAdapter = StringListColumnAdapter,
            ),
        )
        val handler = AndroidMangaDatabaseHandler(
            db = database,
            driver = driver,
            queryDispatcher = Dispatchers.Default,
            transactionDispatcher = Dispatchers.Default,
        )
        val knownProvider = object : DiscoveryKnownTitlesProvider {
            override suspend fun snapshot(media: DiscoveryMediaType) = emptySet<DiscoveryTitleIdentity>()
            override fun observe(media: DiscoveryMediaType) = flowOf(emptySet<DiscoveryTitleIdentity>())
        }
        return driver to DiscoveryRepositoryImpl(
            handler = handler,
            knownTitlesProvider = knownProvider,
            candidateValidator = AppDiscoveryCandidateValidator(preferences),
        )
    }

    // ==================== 1. IGNORED GENRES & BLACKLISTED TAGS ====================

    @Test
    fun `T01 read-time ignored raw genre and blacklist filter all 4 rows in HomeDiscoveryController`() {
        runBlocking {
            val repo = FlowDiscoveryRepo()
            val prefs = DiscoveryPreferences(PersistingPreferenceStore())
            prefs.discoveryEnabled().set(true)
            val context = mockk<Context>(relaxed = true)

            var state = HomeHubUiState(
                userName = "User",
                userAvatar = "",
                greeting = tachiyomi.i18n.MR.strings.app_name,
                greetingReady = true,
                isLoading = false,
                showWelcome = false,
            )

            repo.poolFlow.value = listOf(
                suggestion("Trend Isekai", rowType = DiscoveryRowType.TREND, genres = listOf("Исекай")),
                suggestion("Like Ecchi", rowType = DiscoveryRowType.LIKE, genres = listOf("Ecchi")),
                suggestion("Source Custom", rowType = DiscoveryRowType.SOURCE, genres = listOf("RawOnlyCustomTag")),
                suggestion("Taste Safe", rowType = DiscoveryRowType.TASTE, genres = listOf("Drama")),
            )

            val controller = HomeDiscoveryController(
                mediaType = DiscoveryMediaType.MANGA,
                repository = repo,
                preferences = prefs,
                context = context,
                scope = CoroutineScope(Dispatchers.Unconfined),
                onStateUpdate = { updater -> state = updater(state) },
                currentState = { state },
                onEmitEffect = {},
            )
            controller.startObserving()

            waitUntil { state.discoveryPool.size == 4 }

            // Mutate prefs & blacklist WITHOUT regenerating DB pool
            prefs.ignoredGenres(DiscoveryMediaType.MANGA).set("isekai,raw:RawOnlyCustomTag")
            repo.blacklistFlow.value = setOf("этти")

            waitUntil { state.discoveryPool.size == 1 }
            state.discoveryPool.map { it.title } shouldBe listOf("Taste Safe")
            state.discovery.map { it.title } shouldBe listOf("Taste Safe")
        }
    }

    @Test
    fun `T02 empty-genres catalog items are hydrated upstream and filtered`() {
        runBlocking {
            val catalog = object : DiscoverySourceCatalog {
                override suspend fun popular(
                    mediaType: DiscoveryMediaType,
                    sourceId: Long,
                    page: Int,
                    releaseStatuses: Set<DiscoveryReleaseStatus>,
                ) = listOf(
                    rowItem("Source Hidden Ecchi", genres = emptyList(), sourceId = sourceId, sourceUrl = "/ecchi"),
                    rowItem("Source Hidden Isekai", genres = emptyList(), sourceId = sourceId, sourceUrl = "/isekai"),
                    rowItem("Source Safe Comedy", genres = emptyList(), sourceId = sourceId, sourceUrl = "/safe"),
                )

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

                override suspend fun hydrateGenres(
                    mediaType: DiscoveryMediaType,
                    sourceId: Long,
                    items: List<DiscoveryRowItem>,
                    budget: DiscoveryVerificationBudget?,
                    maxDetails: Int,
                ): DiscoveryGenrePage {
                    val hydrated = items.map { item ->
                        when (item.title) {
                            "Source Hidden Ecchi" -> item.copy(genres = listOf("Ecchi"))
                            "Source Hidden Isekai" -> item.copy(genres = listOf("Исекай"))
                            "Source Safe Comedy" -> item.copy(genres = listOf("Comedy"))
                            else -> item
                        }
                    }
                    return DiscoveryGenrePage(
                        items = hydrated,
                        verified = hydrated.size,
                        unattempted = 0,
                        metadataUnavailable = 0,
                        transientFailures = 0,
                    )
                }
            }
            val context = DiscoveryBuildContext(
                mediaType = DiscoveryMediaType.MANGA,
                seeds = emptyList(),
                libraryCleanTitles = emptySet(),
                historyCleanTitles = emptySet(),
                hiddenCleanTitles = emptySet(),
                ignoredGenres = setOf("isekai"),
                blacklistedTags = setOf("ecchi"),
                genreFilterSpec = GenreFilterSpec(
                    ignoredGenres = setOf("isekai"),
                ),
                sourceIds = listOf(7L),
                verificationBudget = DiscoveryVerificationBudget(),
            )
            val built = DiscoverySourceRowBuilder(catalog).build(context)
            built.map { it.title } shouldBe listOf("Source Safe Comedy")
        }
    }

    @Test
    fun `T03 adversarial ignoring yuri must not block Shoujo Ai and blacklisting milf must not block Mature`() {
        val policy = DiscoveryCandidatePolicy(
            ignoredGenres = setOf("yuri"),
            blacklistedTags = setOf("milf"),
        )
        // Distinct genres should NOT bleed into each other
        policy.isAllowed(suggestion("Pure Shoujo Ai", genres = listOf("Shoujo Ai"))) shouldBe true
        policy.isAllowed(suggestion("Mature Seinen", genres = listOf("Mature"))) shouldBe true
    }

    @Test
    fun `T04 adversarial countBlacklistImpact matches actual DiscoveryCandidatePolicy rejection count`() {
        val items = listOf(
            suggestion(
                "Taste Reason Match",
                rowType = DiscoveryRowType.TASTE,
                genres = emptyList(),
                reason = "Fantasy",
            ),
            suggestion("Trend SciFi", rowType = DiscoveryRowType.TREND, genres = listOf("Science Fiction")),
            suggestion("Trend Pure Drama", rowType = DiscoveryRowType.TREND, genres = listOf("Drama")),
        )
        val tag = "Science"
        val impacted = countBlacklistImpact(items, tag)
        val policy = DiscoveryCandidatePolicy(blacklistedTags = setOf(tag))
        val actuallyRemoved = items.count { !policy.isAllowed(it) }
        impacted shouldBe actuallyRemoved
    }

    // ==================== 2. PRIORITY GENRES ====================

    @Test
    fun `T05 priority genres lift across all 4 rows and rank multi-match higher than single-match`() {
        val parsedPriority = parseGenreFilterCsv("fantasy,action,raw:LitRPG")
        val policy = DiscoveryCandidatePolicy(
            priorityGenres = parsedPriority.first + parsedPriority.second,
        )
        for (row in DiscoveryRowType.entries) {
            val items = listOf(
                suggestion("Zero Score Normal", rowType = row, genres = listOf("Drama"), score = 0.0),
                suggestion("High Score Normal", rowType = row, genres = listOf("Comedy"), score = 99.0),
                suggestion("Empty Genres", rowType = row, genres = emptyList(), score = 50.0),
                suggestion("Raw Priority", rowType = row, genres = listOf("LitRPG"), score = 0.0),
                suggestion("Single Priority", rowType = row, genres = listOf("Fantasy"), score = 10.0),
                suggestion("Multi Priority", rowType = row, genres = listOf("Fantasy", "Action"), score = 10.0),
            )
            val ranked = policy.filterRowCandidates(items, { it.genres }, scoreSelector = { it.score })
            // Multi-match priority should rank strictly above single-match at equal base score,
            // and all priority items above normal/empty items.
            ranked.take(3).map { it.title }.toSet() shouldBe setOf("Multi Priority", "Single Priority", "Raw Priority")
            ranked.first().title shouldBe "Multi Priority"
        }
    }

    @Test
    fun `T06 priority items rank above non-priority in MIX feed and composeTeaserItems`() {
        runBlocking {
            Dispatchers.setMain(Dispatchers.Unconfined)
            mockkStatic("eu.kanade.tachiyomi.util.system.WorkManagerExtensionsKt")
            val wm = mockk<WorkManager>(relaxed = true)
            val context = mockk<Context>(relaxed = true)
            every { context.workManager } returns wm
            every { wm.getWorkInfoByIdFlow(any()) } returns flowOf(null)
            every { wm.isRunningOrEnqueuedFlow(any()) } returns flowOf(false)
            try {
                val repo = FlowDiscoveryRepo()
                val prefs = DiscoveryPreferences(PersistingPreferenceStore())
                prefs.discoveryEnabled().set(true)
                prefs.priorityGenres(DiscoveryMediaType.NOVEL).set("cyberpunk")

                val nonPriorityLike = suggestion(
                    "NonPriority Like",
                    rowType = DiscoveryRowType.LIKE,
                    mediaType = DiscoveryMediaType.NOVEL,
                    genres = listOf("Comedy"),
                    score = 100.0,
                )
                val prioritySource = suggestion(
                    "Priority Source",
                    rowType = DiscoveryRowType.SOURCE,
                    mediaType = DiscoveryMediaType.NOVEL,
                    genres = listOf("Cyberpunk"),
                    score = 1.0,
                )
                val priorityTrend = suggestion(
                    "Priority Trend",
                    rowType = DiscoveryRowType.TREND,
                    mediaType = DiscoveryMediaType.NOVEL,
                    genres = listOf("Cyberpunk"),
                    score = 0.5,
                )
                repo.poolFlow.value = listOf(nonPriorityLike, prioritySource, priorityTrend)

                // Check composeTeaserItems (HomeHub)
                val policy = DiscoveryCandidatePolicy(priorityGenres = setOf("cyberpunk"))
                val teaser = composeTeaserItems(
                    items = listOf(nonPriorityLike, prioritySource, priorityTrend),
                    limit = 3,
                    hasPriorityBoost = { policy.hasPriorityBoost(it) },
                )
                teaser.take(2).all { it.hasPriorityBoost } shouldBe true

                // Check DiscoveryFeedScreenModel MIX tab
                val model = DiscoveryFeedScreenModel(
                    initialMedia = DiscoveryMediaType.NOVEL,
                    context = context,
                    repository = repo,
                    adder = mockk(relaxed = true),
                    preferences = prefs,
                    trendingSource = mockk(relaxed = true),
                    networkToLocalManga = mockk(relaxed = true),
                    networkToLocalAnime = mockk(relaxed = true),
                    networkToLocalNovel = mockk(relaxed = true),
                )
                model.start()
                waitUntil { !model.state.value.isLoading && model.state.value.mix.size == 3 }
                model.state.value.mix.take(2).map { it.title }.toSet() shouldBe
                    setOf("Priority Source", "Priority Trend")
            } finally {
                Dispatchers.resetMain()
                unmockkAll()
            }
        }
    }

    @Test
    fun `T07 adversarial removing priority genre clears hasPriorityBoost in patchTeaserItems`() {
        val boostedItem = suggestion("Cyber Hero", genres = listOf("Cyberpunk"))
        val initialTeaser = listOf(
            HomeHubDiscoveryItem(
                title = boostedItem.title,
                cleanTitle = boostedItem.cleanTitle,
                coverUrl = boostedItem.coverUrl,
                seedTitle = null,
                reasonPayload = null,
                provider = boostedItem.provider,
                rowType = boostedItem.rowType,
                mediaType = boostedItem.mediaType,
                genres = boostedItem.genres,
                hasPriorityBoost = true,
            ),
            HomeHubDiscoveryItem(
                title = "Other 1",
                cleanTitle = "other 1",
                coverUrl = null,
                seedTitle = null,
                reasonPayload = null,
                provider = "test",
                rowType = DiscoveryRowType.TREND,
                mediaType = DiscoveryMediaType.MANGA,
            ),
            HomeHubDiscoveryItem(
                title = "Other 2",
                cleanTitle = "other 2",
                coverUrl = null,
                seedTitle = null,
                reasonPayload = null,
                provider = "test",
                rowType = DiscoveryRowType.TREND,
                mediaType = DiscoveryMediaType.MANGA,
            ),
        )
        val pool = listOf(
            boostedItem,
            suggestion("Other 1", genres = listOf("Drama")),
            suggestion("Other 2", genres = listOf("Comedy")),
        )
        // User cleared priorityGenres: policy.hasPriorityBoost now returns false!
        val patched = patchTeaserItems(
            currentItems = initialTeaser,
            pool = pool,
            validCleanTitles = pool.mapTo(HashSet()) { it.cleanTitle },
            targetCount = 3,
            hasPriorityBoost = { false },
        )
        patched.first { it.cleanTitle == "cyber hero" }.hasPriorityBoost shouldBe false
    }

    @Test
    fun `T08 TASTE builder keeps priority items ahead even past 6 per primary genre`() =
        runTest {
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
                ): List<DiscoveryTrendingItem> {
                    val priorityItems = (1..8).map { i ->
                        DiscoveryTrendingItem(
                            title = "Pri Action $i",
                            cleanTitle = "pri action $i",
                            coverUrl = null,
                            seasonLabel = null,
                            genres = listOf("Action"),
                            provider = "anilist",
                        )
                    }
                    val normalItems = (1..4).map { i ->
                        DiscoveryTrendingItem(
                            title = "Normal Drama $i",
                            cleanTitle = "normal drama $i",
                            coverUrl = null,
                            seasonLabel = null,
                            genres = listOf("Drama"),
                            provider = "anilist",
                        )
                    }
                    return priorityItems + normalItems
                }

                override suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType) = null
            }
            val catalog = mockk<DiscoverySourceCatalog>(relaxed = true)
            val builder = DiscoveryTasteRowBuilder(trending, catalog, { TrendSort.POPULARITY }, includeExternal = true)
            val context = DiscoveryBuildContext(
                mediaType = DiscoveryMediaType.MANGA,
                seeds = emptyList(),
                libraryCleanTitles = emptySet(),
                historyCleanTitles = emptySet(),
                hiddenCleanTitles = emptySet(),
                tasteProfile = listOf("Drama" to 5.0, "Action" to 1.0),
                priorityGenres = setOf("action"),
                genreFilterSpec = GenreFilterSpec(priorityGenres = setOf("action")),
            )

            val built = builder.build(context)
            // All 8 priority Action items must rank ahead of Normal Drama items!
            built.take(8).all { it.title.startsWith("Pri Action") } shouldBe true
        }

    // ==================== 3. REQUIRED GENRES (ALL/ANY x STRICT/BEST_EFFORT) ====================

    @Test
    fun `T09 read-time REQUIRED mode switching updates HomeDiscoveryController immediately`() {
        runBlocking {
            val repo = FlowDiscoveryRepo()
            val prefs = DiscoveryPreferences(PersistingPreferenceStore())
            prefs.discoveryEnabled().set(true)
            val context = mockk<Context>(relaxed = true)

            var state = HomeHubUiState(
                userName = "User",
                userAvatar = "",
                greeting = tachiyomi.i18n.MR.strings.app_name,
                greetingReady = true,
                isLoading = false,
                showWelcome = false,
            )

            val itemAB =
                suggestion("Fantasy Action", rowType = DiscoveryRowType.TREND, genres = listOf("Fantasy", "Action"))
            val itemA = suggestion("Fantasy Only", rowType = DiscoveryRowType.LIKE, genres = listOf("Fantasy"))
            val itemC = suggestion("Comedy Only", rowType = DiscoveryRowType.SOURCE, genres = listOf("Comedy"))
            val itemEmpty = suggestion("Empty Genres", rowType = DiscoveryRowType.TASTE, genres = emptyList())
            repo.poolFlow.value = listOf(itemAB, itemA, itemC, itemEmpty)

            val controller = HomeDiscoveryController(
                mediaType = DiscoveryMediaType.MANGA,
                repository = repo,
                preferences = prefs,
                context = context,
                scope = CoroutineScope(Dispatchers.Unconfined),
                onStateUpdate = { updater -> state = updater(state) },
                currentState = { state },
                onEmitEffect = {},
            )
            controller.startObserving()
            waitUntil { state.discoveryPool.size == 4 }

            // (ALL, STRICT) -> only Fantasy Action
            prefs.requiredGenres(DiscoveryMediaType.MANGA).set("fantasy,action")
            prefs.requiredMatchMode().set(RequiredMatchMode.ALL.name)
            prefs.requiredEnforcement().set(RequiredEnforcement.STRICT.name)
            waitUntil { state.discoveryPool.map { it.title } == listOf("Fantasy Action") }

            // (ANY, STRICT) -> Fantasy Action + Fantasy Only (empty genres excluded!)
            prefs.requiredMatchMode().set(RequiredMatchMode.ANY.name)
            waitUntil { state.discoveryPool.map { it.title }.toSet() == setOf("Fantasy Action", "Fantasy Only") }

            // Remove itemAB -> pool has NO exact ALL match: (ALL, STRICT) -> empty!
            prefs.requiredMatchMode().set(RequiredMatchMode.ALL.name)
            repo.poolFlow.value = listOf(itemA, itemC, itemEmpty)
            waitUntil { state.discoveryPool.isEmpty() }

            // Switch to BEST_EFFORT: partial match Fantasy Only should NOT be crowded out by Empty Genres!
            prefs.requiredEnforcement().set(RequiredEnforcement.BEST_EFFORT.name)
            waitUntil { state.discoveryPool.isNotEmpty() }
            state.discoveryPool.any { it.title == "Fantasy Only" } shouldBe true
        }
    }

    @Test
    fun `T10 adversarial DiscoveryTasteRowBuilder multi-genre ANY mode MUST paginate beyond page 1`() =
        runTest {
            val queriedPages = mutableListOf<Pair<List<String>, Int>>()
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
                ): List<DiscoveryTrendingItem> {
                    queriedPages += genres to page
                    return if (page == 1) {
                        (1..3).map { i ->
                            DiscoveryTrendingItem(
                                title = "P1 ${genres.joinToString()} $i",
                                cleanTitle = "p1 ${genres.joinToString()} $i".lowercase(),
                                coverUrl = null,
                                seasonLabel = null,
                                genres = genres,
                                provider = "anilist",
                            )
                        }
                    } else {
                        (1..10).map { i ->
                            DiscoveryTrendingItem(
                                title = "P2 ${genres.joinToString()} $i",
                                cleanTitle = "p2 ${genres.joinToString()} $i".lowercase(),
                                coverUrl = null,
                                seasonLabel = null,
                                genres = genres,
                                provider = "anilist",
                            )
                        }
                    }
                }

                override suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType) = null
            }
            val catalog = mockk<DiscoverySourceCatalog>(relaxed = true)
            val builder = DiscoveryTasteRowBuilder(trending, catalog, { TrendSort.POPULARITY }, includeExternal = true)
            val context = DiscoveryBuildContext(
                mediaType = DiscoveryMediaType.MANGA,
                seeds = emptyList(),
                libraryCleanTitles = emptySet(),
                historyCleanTitles = emptySet(),
                hiddenCleanTitles = emptySet(),
                tasteProfile = listOf("Fantasy" to 2.0),
                requiredGenres = setOf("fantasy", "action"),
                genreFilterSpec = GenreFilterSpec(
                    requiredGenres = setOf("fantasy", "action"),
                    requiredMatchMode = RequiredMatchMode.ANY,
                    requiredEnforcement = RequiredEnforcement.STRICT,
                ),
            )
            val built = builder.build(context)
            // Page 1 only yields 6 items (< RICH_SOURCE_TARGET = 12), so page 2 MUST be queried
            queriedPages.any { it.second >= 2 } shouldBe true
            built.size shouldBeGreaterThan 6
        }

    @Test
    fun `T11 required custom raw genre works in ALL and ANY STRICT modes and is preserved in anilistGenreVariables`() {
        val policyAll = DiscoveryCandidatePolicy(
            GenreFilterSpec(
                requiredGenres = setOf("fantasy", "CustomSystemTag"),
                requiredMatchMode = RequiredMatchMode.ALL,
                requiredEnforcement = RequiredEnforcement.STRICT,
            ),
        )
        policyAll.isAllowed(suggestion("Match Both", genres = listOf("Фэнтези", "CustomSystemTag"))) shouldBe true
        policyAll.isAllowed(suggestion("Missing Raw", genres = listOf("Fantasy"))) shouldBe false
        policyAll.isAllowed(suggestion("Empty", genres = emptyList())) shouldBe false

        val policyAny = DiscoveryCandidatePolicy(
            GenreFilterSpec(
                requiredGenres = setOf("fantasy", "CustomSystemTag"),
                requiredMatchMode = RequiredMatchMode.ANY,
                requiredEnforcement = RequiredEnforcement.STRICT,
            ),
        )
        policyAny.isAllowed(suggestion("Raw Only", genres = listOf("CustomSystemTag"))) shouldBe true
        policyAny.isAllowed(suggestion("Other", genres = listOf("Comedy"))) shouldBe false

        val vars = anilistGenreVariables(listOf("fantasy", "CustomSystemTag"))
        vars.contains("fantasy") shouldBe true
        vars.contains("CustomSystemTag") shouldBe true
    }

    // ==================== 4. FULL FEED ASSEMBLY, QUOTAS, STARVATION & DB ROUND-TRIP ====================

    @Test
    fun `T12 SQLite round-trip preserves genres, reason, score, rowType, consumed and hidden`() =
        runTest {
            val prefs = DiscoveryPreferences(PersistingPreferenceStore())
            val (driver, repo) = newDbRepo(prefs)
            try {
                val items = listOf(
                    suggestion(
                        "Like Item",
                        rowType = DiscoveryRowType.LIKE,
                        genres = listOf("Action", "Sci-Fi"),
                        reason = "Similar to Seed",
                        score = 42.5,
                        position = 0L,
                    ),
                    suggestion(
                        "Taste Item",
                        rowType = DiscoveryRowType.TASTE,
                        genres = listOf("Fantasy", "Drama"),
                        reason = "Фэнтези, Драма",
                        score = 7.25,
                        position = 1L,
                    ),
                    suggestion(
                        "Trend Item",
                        rowType = DiscoveryRowType.TREND,
                        genres = listOf("Slice of Life"),
                        reason = "current",
                        score = 0.0,
                        position = 2L,
                    ),
                    suggestion(
                        "Source Item",
                        rowType = DiscoveryRowType.SOURCE,
                        genres = listOf("Boys' Love, Drama", "Mystery"),
                        reason = "MangaDex",
                        score = 1.8,
                        position = 3L,
                    ),
                )

                val token = repo.reserveGeneration(DiscoveryMediaType.MANGA, "req-1", 0L, "cfg")!!
                val receipt = repo.publishGeneration(
                    token = token,
                    prepared = DiscoveryPreparedPool.Fresh(items, partial = false),
                    retirement = DiscoveryRetirementPolicy.NONE,
                )
                (receipt is DiscoveryPublishReceipt.Published) shouldBe true

                val loaded = repo.subscribe(DiscoveryMediaType.MANGA).first()
                loaded.size shouldBe 4
                loaded.find { it.title == "Like Item" }!!.let {
                    it.rowType shouldBe DiscoveryRowType.LIKE
                    it.genres shouldBe listOf("Action", "Sci-Fi")
                    it.reason shouldBe "Similar to Seed"
                    it.score shouldBe 42.5
                }
                loaded.find { it.title == "Taste Item" }!!.let {
                    it.rowType shouldBe DiscoveryRowType.TASTE
                    it.genres shouldBe listOf("Fantasy", "Drama")
                    it.reason shouldBe "Фэнтези, Драма"
                    it.score shouldBe 7.25
                }
                loaded.find { it.title == "Trend Item" }!!.let {
                    it.rowType shouldBe DiscoveryRowType.TREND
                    it.genres shouldBe listOf("Slice of Life")
                    it.reason shouldBe "current"
                    it.score shouldBe 0.0
                }
                // Adversarial: genre containing an embedded comma gets split on unescaped ','
                loaded.find { it.title == "Source Item" }!!.let {
                    it.rowType shouldBe DiscoveryRowType.SOURCE
                    it.genres shouldContainExactly listOf("Boys' Love, Drama", "Mystery")
                }
            } finally {
                driver.close()
            }
        }

    @Test
    fun `T13 adversarial topUpPool in DiscoveryRunner MUST NOT starve TASTE, TREND, SOURCE when LIKE fills room`() =
        runTest {
            val prefs = DiscoveryPreferences(PersistingPreferenceStore())
            prefs.discoveryEnabled().set(true)
            val (driver, repo) = newDbRepo(prefs)
            try {
                // Seed initial pool with 45 items (< low water 50, target 60 -> room = 15)
                val cfgKey = computeDiscoveryConfigurationKey(prefs, DiscoveryMediaType.NOVEL)
                val initial = (1..45).map { i ->
                    suggestion(
                        "Existing $i",
                        rowType = DiscoveryRowType.SOURCE,
                        mediaType = DiscoveryMediaType.NOVEL,
                        position = i.toLong(),
                    )
                }
                val token = repo.reserveGeneration(DiscoveryMediaType.NOVEL, "init", 0L, cfgKey)!!
                repo.publishGeneration(
                    token,
                    DiscoveryPreparedPool.Fresh(initial, partial = false),
                    DiscoveryRetirementPolicy.NONE,
                )

                // Builders return 20 items for EACH row type (LIKE, TASTE, TREND, SOURCE)
                fun fakeBuilder(type: DiscoveryRowType) = object : DiscoveryRowBuilder {
                    override val rowType = type
                    override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> =
                        (1..20).map { i ->
                            rowItem(
                                "New ${type.key} $i",
                                genres = listOf("Fantasy"),
                                score = (21 - i).toDouble(),
                                sourceUrl = "/${type.key}/$i",
                            )
                        }
                }
                val runner = DiscoveryRunner(
                    repository = repo,
                    preferences = prefs,
                    seedSources = object : DiscoverySeedSources {
                        override suspend fun candidates(mediaType: DiscoveryMediaType) = emptyList<DiscoverySeedInput>()
                        override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = emptySet<String>()
                    },
                    coordinatorFactory = {
                        DiscoveryCoordinator(
                            listOf(
                                fakeBuilder(DiscoveryRowType.LIKE),
                                fakeBuilder(DiscoveryRowType.TASTE),
                                fakeBuilder(DiscoveryRowType.TREND),
                                fakeBuilder(DiscoveryRowType.SOURCE),
                            ),
                        )
                    },
                    sourcePreferencesProvider = { SourcePreferences(InMemoryPreferenceStore()) },
                    installedPluginsProvider = { emptyList() },
                    fallbackSourceIdProvider = { 1L },
                )

                runner.run(listOf(DiscoveryMediaType.NOVEL), topUp = true)
                val after = repo.subscribe(DiscoveryMediaType.NOVEL).first()
                val added = after.filter { it.title.startsWith("New ") }
                added.size shouldBe 15
                // Top-up should distribute across rows (interleaved), not starve TASTE/TREND/SOURCE completely!
                val addedRowTypes = added.map { it.rowType }.toSet()
                addedRowTypes shouldBe setOf(
                    DiscoveryRowType.LIKE,
                    DiscoveryRowType.TASTE,
                    DiscoveryRowType.TREND,
                    DiscoveryRowType.SOURCE,
                )
            } finally {
                driver.close()
            }
        }

    @Test
    fun `T14 DiscoveryCoordinator selectRowItems excludes eligibility, currentFeed, and recent altTitles`() =
        runTest {
            val builder = object : DiscoveryRowBuilder {
                override val rowType = DiscoveryRowType.TREND
                override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> = listOf(
                    rowItem("Blocked By Eligibility", genres = listOf("Action")),
                    rowItem("Already In Current Feed", genres = listOf("Action")),
                    rowItem(
                        "Reincarnated Slime",
                        genres = listOf("Action"),
                        altCleanTitles = listOf("tensei shitara slime datta ken"),
                    ),
                    rowItem("Genuinely Fresh", genres = listOf("Action")),
                )
            }
            val eligibility = DiscoveryEligibilitySnapshot(
                mediaType = DiscoveryMediaType.MANGA,
                capturedAt = 0L,
                excludedIdentityKeys = setOf("manga:blocked by eligibility"),
                excludedLookupKeys = setOf("blocked by eligibility"),
            )
            val context = DiscoveryBuildContext(
                mediaType = DiscoveryMediaType.MANGA,
                seeds = emptyList(),
                libraryCleanTitles = emptySet(),
                historyCleanTitles = emptySet(),
                hiddenCleanTitles = emptySet(),
                recentCleanTitles = setOf("tensei shitara slime datta ken"),
                currentFeedCleanTitles = setOf("already in current feed"),
                eligibilitySnapshot = eligibility,
            )
            val feed = DiscoveryCoordinator(listOf(builder)).buildFeed(context)
            feed.rows[DiscoveryRowType.TREND]?.map { it.title } shouldBe listOf("Genuinely Fresh")
        }

    @Test
    fun `T15 adversarial SharedRequiredGenreQuery parity split must not discard the only catalog match for SOURCE`() =
        runTest {
            val prefs = DiscoveryPreferences(PersistingPreferenceStore())
            prefs.discoveryEnabled().set(true)
            prefs.requiredGenres(DiscoveryMediaType.NOVEL).set("fantasy")
            prefs.ignoredGenres(DiscoveryMediaType.NOVEL).set("fantasy") // forces activeProfile to be empty in TASTE!
            prefs.rowLikeEnabled().set(false)
            prefs.rowTrendEnabled().set(false)
            prefs.rowTasteEnabled().set(true)
            prefs.rowSourceEnabled().set(true)

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
                ) = listOf(rowItem("Only Fantasy Novel", genres = listOf("Fantasy"), sourceId = sourceId))

                override suspend fun popularWithGenresVerified(
                    mediaType: DiscoveryMediaType,
                    sourceId: Long,
                    genres: List<String>,
                    page: Int,
                    releaseStatuses: Set<DiscoveryReleaseStatus>,
                    budget: DiscoveryVerificationBudget?,
                ) = DiscoveryGenrePage(
                    items = listOf(rowItem("Only Fantasy Novel", genres = listOf("Fantasy"), sourceId = sourceId)),
                    verified = 1,
                    unattempted = 0,
                    metadataUnavailable = 0,
                    transientFailures = 0,
                    hasNextPage = false,
                )
            }

            // Directly test SharedRequiredGenreQuery parity split when TASTE fails or produces 0
            // and catalog has 1 item at index 0
            val policy = DiscoveryCandidatePolicy(requiredGenres = setOf("Fantasy"), strictRequired = true)
            val sharedQuery = SharedRequiredGenreQuery {
                queryDiscoveryGenreSources(
                    catalog = catalog,
                    media = DiscoveryMediaType.NOVEL,
                    sourceIds = listOf(7L),
                    genres = listOf("Fantasy"),
                    page = 1,
                    releaseStatuses = emptySet(),
                    policy = policy,
                    targetItems = 50,
                )
            }
            val context = DiscoveryBuildContext(
                mediaType = DiscoveryMediaType.NOVEL,
                seeds = emptyList(),
                libraryCleanTitles = emptySet(),
                historyCleanTitles = emptySet(),
                hiddenCleanTitles = emptySet(),
                tasteProfile = emptyList(),
                ignoredGenres = setOf(
                    "isekai", "action", "drama", "romance", "comedy", "adventure",
                    "sci-fi", "slice of life", "mystery", "horror", "supernatural",
                ),
                requiredGenres = setOf("Fantasy"),
                genreFilterSpec = GenreFilterSpec(
                    ignoredGenres = setOf(
                        "isekai", "action", "drama", "romance", "comedy", "adventure",
                        "sci-fi", "slice of life", "mystery", "horror", "supernatural",
                    ),
                    requiredGenres = setOf("Fantasy"),
                    requiredEnforcement = RequiredEnforcement.STRICT,
                ),
                sourceIds = listOf(7L),
                sharedGenreQuery = sharedQuery,
            )
            // TASTE builder fails/returns empty; SOURCE builder uses parity=1 and loses index-0 item!
            val sourceItems = DiscoverySourceRowBuilder(catalog).build(context)
            sourceItems.map { it.title } shouldBe listOf("Only Fantasy Novel")
        }

    @Test
    fun `T16 row starvation fallback in interleaveMix redistributes quotas when LIKE and TASTE are empty`() {
        val rows = mapOf(
            DiscoveryRowType.LIKE to emptyList(),
            DiscoveryRowType.TASTE to emptyList(),
            DiscoveryRowType.TREND to (1..15).map {
                rowItem("Trend $it", genres = listOf("Genre$it"), score = (16 - it).toDouble())
            },
            DiscoveryRowType.SOURCE to (1..15).map {
                rowItem("Source $it", genres = listOf("Alt$it"), score = (16 - it).toDouble())
            },
        )
        val mix = interleaveMix(rows, quotas = DiscoveryMixQuotas(), total = 30, rrf = rrfScores(rows))
        mix.size shouldBe 30
        mix.map { it.cleanTitle }.toSet().size shouldBe 30
        mix.count { it.title.startsWith("Trend") } shouldBe 15
        mix.count { it.title.startsWith("Source") } shouldBe 15
    }

    @Test
    fun `T17 DB consumed and hidden exclusions prevent re-entry in eligibility and topUpPool`() = runTest {
        val prefs = DiscoveryPreferences(PersistingPreferenceStore())
        val (driver, repo) = newDbRepo(prefs)
        try {
            val item1 = suggestion("Consumed Title", rowType = DiscoveryRowType.TREND, position = 0L)
            val item2 = suggestion("Hidden Title", rowType = DiscoveryRowType.LIKE, position = 1L)
            val item3 = suggestion("Fresh Title", rowType = DiscoveryRowType.SOURCE, position = 2L)

            val token = repo.reserveGeneration(DiscoveryMediaType.MANGA, "g1", 0L, "cfg")!!
            repo.publishGeneration(
                token,
                DiscoveryPreparedPool.Fresh(listOf(item1, item2, item3), partial = false),
                DiscoveryRetirementPolicy.NONE,
            )

            repo.hide(DiscoveryMediaType.MANGA, item2.cleanTitle)
            repo.recordFact(
                DiscoveryTitleIdentity(
                    mediaType = DiscoveryMediaType.MANGA,
                    identityKey = item1.identityKey!!,
                    title = item1.title,
                    primaryCleanTitle = item1.cleanTitle,
                ),
                tachiyomi.domain.discovery.model.DiscoveryFactKind.EXPLICIT_CONSUMED,
            )

            val pub = repo.publication(DiscoveryMediaType.MANGA)
            pub.eligibleSuggestions.map { it.title } shouldBe listOf("Fresh Title")

            val topUpReceipt = repo.topUpPool(
                media = DiscoveryMediaType.MANGA,
                expectedGeneration = pub.generation,
                additions = listOf(item1, item2),
                cursors = emptyList(),
                maxPoolSize = 60,
            )
            topUpReceipt shouldBe DiscoveryTopUpReceipt.NothingToAdd
        } finally {
            driver.close()
        }
    }

    @Test
    fun `T18 BEST_EFFORT partial required matches outrank empty-genre and zero-match items`() {
        val policy = DiscoveryCandidatePolicy(
            GenreFilterSpec(
                requiredGenres = setOf("fantasy", "action", "drama"),
                requiredMatchMode = RequiredMatchMode.ALL,
                requiredEnforcement = RequiredEnforcement.BEST_EFFORT,
            ),
        )
        val candidates = listOf(
            suggestion("Empty High Score", genres = emptyList(), score = 99.0),
            suggestion("Zero Match", genres = listOf("Comedy"), score = 50.0),
            suggestion("One Match", genres = listOf("Fantasy"), score = 10.0),
            suggestion("Two Matches", genres = listOf("Fantasy", "Action"), score = 1.0),
        )
        val ranked = policy.filterRowCandidates(candidates, { it.genres }, scoreSelector = { it.score })
        ranked.map { it.title } shouldBe listOf("Two Matches", "One Match")
    }
}
