package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.data.suggestions.SuggestionCoordinator
import eu.kanade.tachiyomi.data.suggestions.SuggestionFetchResult
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryEligibilitySnapshot
import tachiyomi.domain.discovery.model.DiscoveryExternalProvider
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.source.manga.service.MangaSourceManager
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryLikeRowBuilderTest {

    @Test
    fun `partial feedback provider failure returns successful items and marks builder degraded`() = runTest {
        val suggestions = mockk<SuggestionCoordinator>()
        coEvery { suggestions.fetchSuggestions(any(), any(), any()) } returns SuggestionFetchResult(
            items = listOf(
                SuggestionItem(
                    title = "Fresh Pick",
                    thumbnailUrl = null,
                    providerName = "AniList",
                    providerUrl = "https://anilist.co/manga/1",
                    providerId = "1",
                    mediaType = SuggestionMediaType.NOVEL,
                ),
            ),
            attemptedSources = 2,
            failedSources = 1,
        )
        val builder = DiscoveryLikeRowBuilder(suggestions)

        val items = builder.build(externalContext(feedback = true))

        items.map { it.title } shouldBe listOf("Fresh Pick")
        builder.isDegraded shouldBe true
    }

    @Test
    fun `complete provider failure for all seeds throws exception`() = runTest {
        val suggestions = mockk<SuggestionCoordinator>()
        coEvery { suggestions.fetchSuggestions(any(), any(), any()) } returns SuggestionFetchResult(
            items = emptyList(),
            attemptedSources = 2,
            failedSources = 2,
        )
        val builder = DiscoveryLikeRowBuilder(suggestions)

        val failure = runCatching { builder.build(externalContext(feedback = true)) }.exceptionOrNull()

        (failure is IOException) shouldBe true
    }

    @Test
    fun `empty successful providers remain honestly empty for library and feedback seeds`() = runTest {
        val suggestions = mockk<SuggestionCoordinator>()
        coEvery { suggestions.fetchSuggestions(any(), any(), any()) } returns
            SuggestionFetchResult(emptyList(), attemptedSources = 2, failedSources = 0)
        val builder = DiscoveryLikeRowBuilder(suggestions)

        builder.build(externalContext(feedback = false)) shouldBe emptyList()
        builder.build(externalContext(feedback = true)) shouldBe emptyList()
    }

    private fun externalContext(feedback: Boolean) = DiscoveryBuildContext(
        mediaType = DiscoveryMediaType.NOVEL,
        seeds = if (feedback) emptyList() else listOf(DiscoverySeedInput(entryId = 1L, title = "Seed One")),
        feedbackSeeds = if (feedback) listOf(DiscoveryFeedbackSeed("Seed One", "seed one")) else emptyList(),
        libraryCleanTitles = emptySet(),
        historyCleanTitles = emptySet(),
        hiddenCleanTitles = emptySet(),
        sourcePolicy = discoveryQueryPolicyFixture(
            DiscoveryMediaType.NOVEL,
            emptyList(),
            mapOf(DiscoveryRowType.LIKE to setOf(DiscoveryExternalProvider.ANILIST)),
        ),
    )

    private class FakeCatalog(
        private val items: List<DiscoveryRowItem> = emptyList(),
        private val delayMs: Long = 0L,
        private val genreResponse: (suspend (Long, List<String>) -> List<DiscoveryRowItem>)? = null,
    ) : DiscoverySourceCatalog {
        val genreQueries = mutableListOf<Pair<Long, List<String>>>()
        val currentConcurrent = java.util.concurrent.atomic.AtomicInteger(0)
        val maxConcurrent = java.util.concurrent.atomic.AtomicInteger(0)

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
            genreQueries += sourceId to genres.toList()
            val cur = currentConcurrent.incrementAndGet()
            maxConcurrent.updateAndGet { maxOf(it, cur) }
            try {
                if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
                return (genreResponse?.invoke(sourceId, genres) ?: items).map {
                    it.copy(sourceId = sourceId, sourceUrl = it.sourceUrl ?: "/fixture/" + it.cleanTitle)
                }
            } finally {
                currentConcurrent.decrementAndGet()
            }
        }

        override suspend fun latest(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            page: Int,
            releaseStatuses: Set<DiscoveryReleaseStatus>,
        ): List<DiscoveryRowItem> = emptyList()
    }

    @Test
    fun `includeExternal false queries catalog with seed genres and attaches seedTitle when at least 4 genres match`() =
        runTest {
            val sharedGenres = listOf("Action", "Fantasy", "Adventure", "Comedy")
            val catalog = FakeCatalog(
                items = listOf(
                    DiscoveryRowItem(
                        title = "Rec Title",
                        cleanTitle = "rec title",
                        coverUrl = null,
                        reason = null,
                        seedTitle = null,
                        provider = "Catalog",
                        score = 0.9,
                        genres = sharedGenres,
                    ),
                    DiscoveryRowItem(
                        title = "Unrelated Popular",
                        cleanTitle = "unrelated popular",
                        coverUrl = null,
                        reason = null,
                        seedTitle = null,
                        provider = "Catalog",
                        score = 1.0,
                        genres = listOf("Action", "Drama"),
                    ),
                    DiscoveryRowItem(
                        title = "Unknown Genres",
                        cleanTitle = "unknown genres",
                        coverUrl = null,
                        reason = null,
                        seedTitle = null,
                        provider = "Catalog",
                        score = 1.0,
                        genres = emptyList(),
                    ),
                ),
            )
            val builder = DiscoveryLikeRowBuilder(
                suggestionCoordinator = SuggestionCoordinator(),
                catalog = catalog,
                includeExternal = false,
            )
            val context = DiscoveryBuildContext(
                mediaType = DiscoveryMediaType.MANGA,
                seeds = emptyList(),
                feedbackSeeds = listOf(
                    DiscoveryFeedbackSeed(
                        title = "Seed Title",
                        cleanTitle = "seed title",
                        genres = sharedGenres,
                    ),
                ),
                libraryCleanTitles = emptySet(),
                historyCleanTitles = emptySet(),
                hiddenCleanTitles = emptySet(),
                sourceId = 1L,
                sourceIds = listOf(1L),
                sourcePolicy = discoveryQueryPolicyFixture(DiscoveryMediaType.MANGA, listOf(1L)),
            )
            val results = builder.build(context)
            results.map { it.title } shouldBe listOf("Rec Title")
            results.first().seedTitle shouldBe "Seed Title"
        }

    @Test
    fun `plugins-only LIKE includes a title found only in the second required ANY genre`(): Unit = runTest {
        val source = mockk<CatalogueSource>()
        every { source.id } returns 7L
        every { source.name } returns "Genre fixture"
        every { source.lang } returns "en"
        every { source.supportsLatest } returns false
        every { source.getFilterList() } answers {
            FilterList(
                object : Filter.Group<Filter.CheckBox>(
                    "Genres",
                    listOf(
                        object : Filter.CheckBox("Action") {},
                        object : Filter.CheckBox("Romance") {},
                    ),
                ) {},
            )
        }
        coEvery { source.getSearchManga(1, "", any()) } coAnswers {
            val selected = thirdArg<FilterList>()
                .filterIsInstance<Filter.Group<*>>()
                .flatMap { it.state }
                .filterIsInstance<Filter.CheckBox>()
                .filter { it.state }
                .map { it.name }
            // The source boundary honors the filters: Action and the conjunction are empty.
            val mangas = if (selected == listOf("Romance")) {
                listOf(
                    SManga.create().apply {
                        title = "Romance-only pick"
                        url = "/romance-only"
                        genre = "Romance, Comedy, Drama, Fantasy"
                        status = SManga.ONGOING
                    },
                )
            } else {
                emptyList()
            }
            MangasPage(mangas, hasNextPage = false)
        }
        val manager = mockk<MangaSourceManager>()
        every { manager.getOrStub(7L) } returns source
        val builder = DiscoveryLikeRowBuilder(
            suggestionCoordinator = SuggestionCoordinator(),
            catalog = AppDiscoverySourceCatalog(
                mangaSourceManagerProvider = { manager },
                sourcePolicyProvider = {
                    fixedDiscoveryQueryPolicyProvider(
                        discoveryQueryPolicyFixture(DiscoveryMediaType.MANGA, listOf(7L)),
                        mapOf(7L to source),
                    )
                },
            ),
            includeExternal = false,
        )
        val context = DiscoveryBuildContext(
            mediaType = DiscoveryMediaType.MANGA,
            seeds = listOf(
                DiscoverySeedInput(
                    entryId = 1L,
                    title = "Romance seed",
                    genres = listOf("Romance", "Comedy", "Drama", "Fantasy"),
                ),
            ),
            libraryCleanTitles = setOf("romance seed"),
            historyCleanTitles = emptySet(),
            hiddenCleanTitles = emptySet(),
            genreFilterSpec = GenreFilterSpec(
                requiredGenres = linkedSetOf("Action", "Romance"),
                requiredMatchMode = RequiredMatchMode.ANY,
                requiredEnforcement = RequiredEnforcement.STRICT,
            ),
            sourceId = 7L,
            sourceIds = listOf(7L),
            sourcePolicy = discoveryQueryPolicyFixture(DiscoveryMediaType.MANGA, listOf(7L)),
            verificationBudget = DiscoveryVerificationBudget(maxDetails = 0, clock = { 0L }),
        )

        val items = builder.build(context)

        items.map { it.title } shouldBe listOf("Romance-only pick")
        items.single().sourceId shouldBe 7L
        items.single().sourceUrl shouldBe "/romance-only"
        items.single().genres shouldBe listOf("Romance", "Comedy", "Drama", "Fantasy")
        items.single().seedTitle shouldBe "Romance seed"
    }

    @Test
    fun `native LIKE ANY status details share the media allowance instead of multiplying it per genre`(): Unit =
        runTest {
            val source = mockk<CatalogueSource>()
            every { source.id } returns 7L
            every { source.name } returns "Status fixture"
            every { source.lang } returns "en"
            every { source.supportsLatest } returns false
            every { source.getFilterList() } answers {
                FilterList(
                    object : Filter.Group<Filter.CheckBox>(
                        "Genres",
                        listOf(
                            object : Filter.CheckBox("Action") {},
                            object : Filter.CheckBox("Romance") {},
                        ),
                    ) {},
                )
            }
            coEvery { source.getSearchManga(1, "", any()) } coAnswers {
                val selected = thirdArg<FilterList>()
                    .filterIsInstance<Filter.Group<*>>()
                    .flatMap { it.state }
                    .filterIsInstance<Filter.CheckBox>()
                    .filter { it.state }
                    .map { it.name }
                val manga = when (selected) {
                    listOf("Action") -> SManga.create().apply {
                        title = "Action pick"
                        url = "/action"
                        genre = "Action, Comedy, Fantasy, Mystery"
                    }
                    listOf("Romance") -> SManga.create().apply {
                        title = "Romance pick"
                        url = "/romance"
                        genre = "Romance, Comedy, Fantasy, Drama, Adventure"
                    }
                    else -> null
                }
                MangasPage(listOfNotNull(manga), hasNextPage = false)
            }
            var detailsCalls = 0
            coEvery { source.getMangaUpdate(any(), emptyList(), true, false) } coAnswers {
                detailsCalls++
                val entry = firstArg<SManga>()
                val details = SManga.create().apply {
                    title = entry.title
                    url = entry.url
                    genre = entry.genre
                    status = SManga.ONGOING
                }
                SMangaUpdate(details, emptyList())
            }
            val manager = mockk<MangaSourceManager>()
            every { manager.getOrStub(7L) } returns source
            val builder = DiscoveryLikeRowBuilder(
                SuggestionCoordinator(),
                AppDiscoverySourceCatalog(
                    mangaSourceManagerProvider = { manager },
                    sourcePolicyProvider = {
                        fixedDiscoveryQueryPolicyProvider(
                            discoveryQueryPolicyFixture(DiscoveryMediaType.MANGA, listOf(7L)),
                            mapOf(7L to source),
                        )
                    },
                ),
                includeExternal = false,
            )
            val budget = DiscoveryVerificationBudget(maxDetails = 1, clock = { 0L })
            val context = nativeAnyContext(budget).copy(
                genreFilterSpec = GenreFilterSpec(
                    requiredGenres = linkedSetOf("Action", "Romance"),
                    requiredMatchMode = RequiredMatchMode.ANY,
                    requiredEnforcement = RequiredEnforcement.STRICT,
                ),
                releaseStatuses = setOf(DiscoveryReleaseStatus.ONGOING),
            )

            val items = builder.build(context)

            items.map { it.title }.toSet() shouldBe setOf("Action pick", "Romance pick")
            detailsCalls shouldBe 1
            budget.detailsUsed shouldBe 1
            budget.remainingDetails shouldBe 0
        }

    @Test
    fun `slow LIKE ANY head queries leave fair time for tails with at most three active calls`(): Unit =
        runTest {
            val catalog = FakeCatalog(genreResponse = { sourceId, genres ->
                if (genres.single() in setOf("Action", "Comedy", "Fantasy")) {
                    delay(1_000)
                } else {
                    delay(1)
                }
                if (genres.single() == "Mystery") {
                    listOf(
                        DiscoveryRowItem(
                            title = "Tail $sourceId",
                            cleanTitle = "tail $sourceId",
                            coverUrl = null,
                            reason = null,
                            seedTitle = null,
                            provider = "Fixture $sourceId",
                            score = 0.9,
                            sourceId = sourceId,
                            sourceUrl = "/tail-$sourceId",
                            genres = listOf("Mystery", "Action", "Comedy", "Fantasy", "Drama"),
                        ),
                    )
                } else {
                    emptyList()
                }
            })
            val budget = DiscoveryVerificationBudget(budgetMs = 100, maxDetails = 0, clock = {
                testScheduler.currentTime
            })
            val builder = DiscoveryLikeRowBuilder(SuggestionCoordinator(), catalog, includeExternal = false)

            val items = builder.build(nativeAnyContext(budget, sourceIds = listOf(7L, 8L)))

            items.map { it.title }.toSet() shouldBe setOf("Tail 7", "Tail 8")
            items.map { it.sourceId }.toSet() shouldBe setOf(7L, 8L)
            (catalog.maxConcurrent.get() in 1..3) shouldBe true
            catalog.currentConcurrent.get() shouldBe 0
            catalog.genreQueries.all { it.second.size == 1 } shouldBe true
            (testScheduler.currentTime <= 100L) shouldBe true
        }

    @Test
    fun `LIKE ANY does not start queued calls after the shared deadline`(): Unit =
        runTest {
            val clock = AtomicLong(0L)
            val startedAt = mutableListOf<Long>()
            val catalog = FakeCatalog(genreResponse = { _, _ ->
                startedAt += clock.get()
                delay(1)
                clock.set(100L)
                emptyList()
            })
            val budget = DiscoveryVerificationBudget(budgetMs = 100, maxDetails = 0, clock = { clock.get() })
            val builder = DiscoveryLikeRowBuilder(SuggestionCoordinator(), catalog, includeExternal = false)

            builder.build(nativeAnyContext(budget, sourceIds = listOf(7L, 8L))) shouldBe emptyList()

            startedAt shouldBe listOf(0L, 0L, 0L)
            catalog.genreQueries.map { it.second.single() }.toSet() shouldBe setOf("Action", "Comedy", "Fantasy")
            catalog.currentConcurrent.get() shouldBe 0
        }

    @Test
    fun `native LIKE ANY retains a finished tail query when sibling queries exhaust its time window`(): Unit = runTest {
        val catalog = FakeCatalog(genreResponse = { sourceId, genres ->
            when (genres.single()) {
                "Action", "Comedy", "Fantasy" -> {
                    delay(1)
                    emptyList()
                }
                "Mystery" -> listOf(
                    DiscoveryRowItem(
                        title = "Budget survivor",
                        cleanTitle = "budget survivor",
                        coverUrl = null,
                        reason = null,
                        seedTitle = null,
                        provider = "Fixture",
                        score = 0.9,
                        sourceId = sourceId,
                        sourceUrl = "/budget-survivor",
                        genres = listOf("Mystery", "Action", "Comedy", "Fantasy", "Drama"),
                    ),
                )
                else -> awaitCancellation()
            }
        })
        val budget = DiscoveryVerificationBudget(budgetMs = 100, maxDetails = 0, clock = { testScheduler.currentTime })
        val builder = DiscoveryLikeRowBuilder(SuggestionCoordinator(), catalog, includeExternal = false)

        val items = builder.build(nativeAnyContext(budget))

        items.map { it.title } shouldBe listOf("Budget survivor")
        items.single().genres shouldBe listOf("Mystery", "Action", "Comedy", "Fantasy", "Drama")
        catalog.currentConcurrent.get() shouldBe 0
        (testScheduler.currentTime <= 100L) shouldBe true
    }

    @Test
    fun `cancelled LIKE ANY drains requests without publishing or starting tails`(): Unit =
        runTest {
            val catalog = FakeCatalog(genreResponse = { _, _ -> awaitCancellation() })
            val builder = DiscoveryLikeRowBuilder(SuggestionCoordinator(), catalog, includeExternal = false)
            val budget = DiscoveryVerificationBudget(maxDetails = 0, clock = { testScheduler.currentTime })
            var returned = false
            val worker = launch {
                builder.build(nativeAnyContext(budget))
                returned = true
            }
            runCurrent()
            catalog.currentConcurrent.get() shouldBe 3

            worker.cancelAndJoin()

            returned shouldBe false
            catalog.currentConcurrent.get() shouldBe 0
            catalog.genreQueries.map { it.second.single() }.toSet() shouldBe setOf("Action", "Comedy", "Fantasy")
        }

    @Test
    fun `native LIKE keeps the observed duplicate with zero details allowance`() = runTest {
        val sharedGenres = listOf("Romance", "Action", "Comedy", "Fantasy", "Drama")
        val catalog = FakeCatalog(genreResponse = { _, genres ->
            listOf(g5Item("Shared", if (genres.single() == "Romance") sharedGenres else emptyList()))
        })
        val budget = DiscoveryVerificationBudget(maxDetails = 0, clock = { testScheduler.currentTime })
        val context = nativeAnyContext(budget).copy(
            genreFilterSpec = GenreFilterSpec(
                requiredGenres = linkedSetOf("Action", "Romance"),
                requiredMatchMode = RequiredMatchMode.ANY,
            ),
        )
        val builder = DiscoveryLikeRowBuilder(SuggestionCoordinator(), catalog, includeExternal = false)
        val rows = DiscoveryCoordinator(listOf(builder)).streamFeed(context) { _, _ ->
        }.rows[DiscoveryRowType.LIKE].orEmpty()
        rows.map { it.title } shouldBe listOf("Shared")
        rows.single().genres shouldBe sharedGenres
        rows.single().sourceId shouldBe 7L
        budget.detailsUsed shouldBe 0
    }

    @Test
    fun `external LIKE keeps a whole observed copy instead of its first empty metadata template`() = runTest {
        val suggestions = mockk<SuggestionCoordinator>()
        var calls = 0
        coEvery { suggestions.fetchSuggestions(any(), any(), any()) } answers {
            calls++
            SuggestionFetchResult(
                listOf(
                    g5Suggestion(
                        "Shared",
                        if (calls == 1) emptyList() else listOf("Action"),
                        if (calls == 1) "unknown.jpg" else "known.jpg",
                    ),
                ),
                1,
                0,
            )
        }
        val context = externalContext(false).copy(
            seeds = listOf(DiscoverySeedInput(1L, "Seed One"), DiscoverySeedInput(2L, "Seed Two")),
            genreFilterSpec = GenreFilterSpec(requiredGenres = setOf("action")),
        )
        val rows = DiscoveryCoordinator(listOf(DiscoveryLikeRowBuilder(suggestions))).streamFeed(context) { _, _ ->
        }.rows[DiscoveryRowType.LIKE].orEmpty()
        rows.map { it.title } shouldBe listOf("Shared")
        rows.single().genres shouldBe listOf("Action")
        rows.single().coverUrl shouldBe "known.jpg"
        calls shouldBe 2
    }

    @Test
    fun `LIKE rejected prefixes do not consume the admitted per seed quota for native or external payloads`() =
        runTest {
            for (external in listOf(false, true)) {
                for (veto in listOf(false, true)) {
                    val rejectedGenres = if (veto) {
                        listOf("Romance", "Action", "Comedy", "Fantasy", "Mystery")
                    } else {
                        listOf("Drama", "Comedy", "Fantasy", "Mystery", "Adventure")
                    }
                    val payload = (1..15).map {
                        g5Item("Rejected $it", rejectedGenres)
                    } + g5Item("Lawful", listOf("Action", "Comedy", "Fantasy", "Mystery", "Adventure"))
                    val spec = if (veto) {
                        GenreFilterSpec(
                            ignoredGenres = setOf("romance"),
                        )
                    } else {
                        GenreFilterSpec(requiredGenres = setOf("action"))
                    }
                    val budget = DiscoveryVerificationBudget(maxDetails = 0, clock = { testScheduler.currentTime })
                    val context = if (external) {
                        externalContext(
                            false,
                        ).copy(genreFilterSpec = spec)
                    } else {
                        nativeAnyContext(budget).copy(genreFilterSpec = spec)
                    }
                    val suggestions = mockk<SuggestionCoordinator>()
                    coEvery {
                        suggestions.fetchSuggestions(any(), any(), any())
                    } returns SuggestionFetchResult(
                        payload.map {
                            g5Suggestion(it.title, it.genres)
                        },
                        1,
                        0,
                    )
                    val builder = DiscoveryLikeRowBuilder(
                        suggestions,
                        FakeCatalog(items = payload),
                        includeExternal = external,
                    )
                    val rows = DiscoveryCoordinator(listOf(builder)).streamFeed(context) { _, _ ->
                    }.rows[DiscoveryRowType.LIKE].orEmpty()
                    rows.map { it.title } shouldBe listOf("Lawful")
                }
            }
        }

    @Test
    fun `native LIKE legacy excluded exact prefix leaves the lawful best effort fallback`() = runTest {
        val exact = (1..15).map { g5Item("Retired $it", listOf("Fantasy", "Action", "Comedy", "Mystery", "Drama")) }
        val fallback = g5Item("Fallback", listOf("Drama", "Action", "Comedy", "Mystery", "Adventure"))
        val budget = DiscoveryVerificationBudget(maxDetails = 0, clock = { testScheduler.currentTime })
        val context = nativeAnyContext(budget).copy(
            genreFilterSpec = GenreFilterSpec(
                requiredGenres = setOf("fantasy"),
                requiredEnforcement = RequiredEnforcement.BEST_EFFORT,
            ),
            eligibilitySnapshot = DiscoveryEligibilitySnapshot(
                DiscoveryMediaType.MANGA,
                0L,
                emptySet(),
                exact.mapTo(HashSet()) {
                    tachiyomi.domain.discovery.model.legacyDiscoveryKey(DiscoveryMediaType.MANGA, it.cleanTitle)
                },
            ),
        )
        val builder = DiscoveryLikeRowBuilder(
            SuggestionCoordinator(),
            FakeCatalog(items = exact + fallback),
            includeExternal = false,
        )
        val rows = DiscoveryCoordinator(listOf(builder)).streamFeed(context) { _, _ ->
        }.rows[DiscoveryRowType.LIKE].orEmpty()
        rows.map { it.title } shouldBe listOf("Fallback")
    }

    @Test
    fun `a duplicate after raw position fifteen still upgrades the selected native identity`() = runTest {
        val sharedGenres = listOf("Action", "Comedy", "Fantasy", "Mystery", "Drama")
        val payload = listOf(g5Item("Shared", emptyList())) + (1..14).map {
            g5Item("Rejected $it", listOf("Drama", "Comedy", "Fantasy", "Mystery", "Adventure"))
        } + g5Item("Shared", sharedGenres)
        val budget = DiscoveryVerificationBudget(maxDetails = 0, clock = { testScheduler.currentTime })
        val context = nativeAnyContext(budget).copy(genreFilterSpec = GenreFilterSpec(requiredGenres = setOf("action")))
        val builder = DiscoveryLikeRowBuilder(
            SuggestionCoordinator(),
            FakeCatalog(items = payload),
            includeExternal = false,
        )
        val rows = DiscoveryCoordinator(listOf(builder)).streamFeed(context) { _, _ ->
        }.rows[DiscoveryRowType.LIKE].orEmpty()
        rows.map { it.title } shouldBe listOf("Shared")
        rows.single().genres shouldBe sharedGenres
        budget.detailsUsed shouldBe 0
    }

    @Test
    fun `observed conflicting copies retain the first known veto instead of choosing a permissive copy`() = runTest {
        val suggestions = mockk<SuggestionCoordinator>()
        var calls = 0
        coEvery { suggestions.fetchSuggestions(any(), any(), any()) } answers {
            calls++
            SuggestionFetchResult(
                listOf(g5Suggestion("Shared", if (calls == 1) listOf("Romance") else listOf("Action"))),
                1,
                0,
            )
        }
        val context = externalContext(false).copy(
            seeds = listOf(DiscoverySeedInput(1L, "Seed One"), DiscoverySeedInput(2L, "Seed Two")),
            genreFilterSpec = GenreFilterSpec(requiredGenres = setOf("action"), ignoredGenres = setOf("romance")),
        )
        DiscoveryCoordinator(listOf(DiscoveryLikeRowBuilder(suggestions))).streamFeed(context) { _, _ ->
        }.rows[DiscoveryRowType.LIKE].orEmpty() shouldBe emptyList()
    }

    private fun g5Item(title: String, genres: List<String>) = DiscoveryRowItem(
        title, title.lowercase(), null, null, null, "Native", 1.0, sourceUrl = "/" + title.lowercase(), genres = genres,
    )
    private fun g5Suggestion(title: String, genres: List<String>, cover: String? = null) = SuggestionItem(
        title = title,
        thumbnailUrl = cover,
        providerName = "AniList",
        providerUrl = "https://fixture.invalid/" + title,
        providerId = title,
        mediaType = SuggestionMediaType.NOVEL,
        genres = genres,
    )

    private fun nativeAnyContext(
        budget: DiscoveryVerificationBudget,
        sourceIds: List<Long> = listOf(7L),
    ) = DiscoveryBuildContext(
        mediaType = DiscoveryMediaType.MANGA,
        seeds = listOf(
            DiscoverySeedInput(
                entryId = 1L,
                title = "Budget seed",
                genres = listOf("Action", "Comedy", "Fantasy", "Mystery", "Drama", "Adventure"),
            ),
        ),
        libraryCleanTitles = setOf("budget seed"),
        historyCleanTitles = emptySet(),
        hiddenCleanTitles = emptySet(),
        genreFilterSpec = GenreFilterSpec(
            requiredGenres = linkedSetOf("Action", "Comedy", "Fantasy", "Romance", "Mystery", "Drama"),
            requiredMatchMode = RequiredMatchMode.ANY,
            requiredEnforcement = RequiredEnforcement.STRICT,
        ),
        sourceId = sourceIds.first(),
        sourceIds = sourceIds,
        sourcePolicy = discoveryQueryPolicyFixture(DiscoveryMediaType.MANGA, sourceIds),
        verificationBudget = budget,
    )

    @Test
    fun `multiple seeds are queried concurrently`() = runTest {
        val catalog = FakeCatalog(
            items = listOf(
                DiscoveryRowItem(
                    "Rec Title",
                    "rec title",
                    null,
                    null,
                    null,
                    "Catalog",
                    0.9,
                    genres = listOf("Action", "Comedy", "Drama", "Fantasy", "Adventure"),
                ),
            ),
            delayMs = 50L,
        )
        val builder = DiscoveryLikeRowBuilder(
            suggestionCoordinator = SuggestionCoordinator(),
            catalog = catalog,
            includeExternal = false,
        )
        val context = DiscoveryBuildContext(
            mediaType = DiscoveryMediaType.MANGA,
            seeds = emptyList(),
            feedbackSeeds = listOf(
                DiscoveryFeedbackSeed("Seed 1", "seed 1", listOf("Action", "Comedy", "Drama", "Fantasy")),
                DiscoveryFeedbackSeed("Seed 2", "seed 2", listOf("Comedy", "Drama", "Fantasy", "Adventure")),
                DiscoveryFeedbackSeed("Seed 3", "seed 3", listOf("Drama", "Action", "Comedy", "Adventure")),
            ),
            libraryCleanTitles = emptySet(),
            historyCleanTitles = emptySet(),
            hiddenCleanTitles = emptySet(),
            sourceId = 1L,
            sourceIds = listOf(1L, 2L),
            sourcePolicy = discoveryQueryPolicyFixture(DiscoveryMediaType.MANGA, listOf(1L, 2L)),
        )
        val results = builder.build(context)
        catalog.maxConcurrent.get() shouldBe io.kotest.matchers.ints.beGreaterThan(1)
        results.isNotEmpty() shouldBe true
    }

    @Test
    fun `blacklisted tags filter external suggestions in like row builder`() = runTest {
        val suggestions = mockk<SuggestionCoordinator>()
        val item1 =
            SuggestionItem(
                "Clean Like",
                listOf("Clean Like"),
                null,
                "AniList",
                "",
                null,
                eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType.NOVEL,
                genres = listOf("Adventure"),
            )
        val item2 =
            SuggestionItem(
                "Dirty Like",
                listOf("Dirty Like"),
                null,
                "AniList",
                "",
                null,
                eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType.NOVEL,
                genres = listOf("Ecchi"),
            )
        coEvery { suggestions.fetchSuggestions(any(), any(), any()) } returns
            SuggestionFetchResult(listOf(item1, item2), attemptedSources = 1, failedSources = 0)
        val builder = DiscoveryLikeRowBuilder(suggestions)

        val context = externalContext(feedback = false).copy(
            blacklistedTags = setOf("Ecchi"),
        )
        val result = builder.build(context)
        result.map { it.title } shouldBe listOf("Clean Like")
    }

    @Test
    fun `library filtered primary with partial failure does not throw and resets isDegraded`() =
        runTest {
            val suggestions = mockk<SuggestionCoordinator>()
            val existingItem = SuggestionItem(
                "Already Saved",
                listOf("Already Saved"),
                null,
                "AniList",
                "",
                null,
                eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType.NOVEL,
                genres = listOf("Action"),
            )
            val freshItem = SuggestionItem(
                "Fresh Pick",
                listOf("Fresh Pick"),
                null,
                "AniList",
                "",
                null,
                eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType.NOVEL,
                genres = listOf("Action"),
            )
            coEvery { suggestions.fetchSuggestions(any(), any(), any()) } returnsMany listOf(
                SuggestionFetchResult(listOf(existingItem), attemptedSources = 2, failedSources = 1),
                SuggestionFetchResult(listOf(freshItem), attemptedSources = 2, failedSources = 0),
            )
            val builder = DiscoveryLikeRowBuilder(suggestions)
            val ctx = externalContext(feedback = false).copy(libraryCleanTitles = setOf("already saved"))

            val first = builder.build(ctx)
            first shouldBe emptyList()
            builder.isDegraded shouldBe true

            val second = builder.build(ctx)
            second.map { it.title } shouldBe listOf("Fresh Pick")
            builder.isDegraded shouldBe false
        }

    @Test
    fun `best effort required preserves matched items past index fifteen before perSeedCap slices`() = runTest {
        val suggestions = mockk<SuggestionCoordinator>()
        val items = (1..15).map { idx ->
            SuggestionItem(
                "Drama $idx",
                listOf("Drama $idx"),
                null,
                "AniList",
                "",
                null,
                eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType.NOVEL,
                genres = listOf("Drama"),
            )
        } + SuggestionItem(
            "Late Fantasy Match",
            listOf("Late Fantasy Match"),
            null,
            "AniList",
            "",
            null,
            eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType.NOVEL,
            genres = listOf("Fantasy"),
        )
        coEvery { suggestions.fetchSuggestions(any(), any(), any()) } returns
            SuggestionFetchResult(items, attemptedSources = 1, failedSources = 0)
        val builder = DiscoveryLikeRowBuilder(suggestions)
        val ctx = externalContext(feedback = false).copy(
            genreFilterSpec = GenreFilterSpec(
                requiredGenres = setOf("Fantasy"),
                requiredEnforcement = RequiredEnforcement.BEST_EFFORT,
            ),
        )

        val result = builder.build(ctx)
        result.first().title shouldBe "Late Fantasy Match"
    }

    @Test
    fun `native LIKE requires 5 shared genres when both sets are rich and rejects 3-4 shallow overlaps`() =
        runTest {
            val seedGenres = listOf("Action", "Adventure", "Fantasy", "Magic", "Comedy", "Drama")
            val catalog = FakeCatalog(
                items = listOf(
                    g5Item("Three Only", listOf("Action", "Adventure", "Fantasy", "Horror", "Sci-Fi", "Thriller")),
                    g5Item("Four Only", listOf("Action", "Adventure", "Fantasy", "Magic", "Horror", "Sci-Fi")),
                    g5Item("Five Match", listOf("Action", "Adventure", "Fantasy", "Magic", "Comedy", "Sci-Fi")),
                ),
            )
            val builder = DiscoveryLikeRowBuilder(SuggestionCoordinator(), catalog, includeExternal = false)
            val context = DiscoveryBuildContext(
                mediaType = DiscoveryMediaType.MANGA,
                seeds = listOf(DiscoverySeedInput(entryId = 1L, title = "Rich Seed", genres = seedGenres)),
                libraryCleanTitles = setOf("rich seed"),
                historyCleanTitles = emptySet(),
                hiddenCleanTitles = emptySet(),
                sourceId = 1L,
                sourceIds = listOf(1L),
                sourcePolicy = discoveryQueryPolicyFixture(DiscoveryMediaType.MANGA, listOf(1L)),
            )

            val result = builder.build(context)

            result.map { it.title } shouldBe listOf("Five Match")
            result.single().seedTitle shouldBe "Rich Seed"
        }

    @Test
    fun `native LIKE recognizes bilingual RU-EN genres through canonical GenreMatcher equivalence`() =
        runTest {
            val seedRu = listOf("Экшен", "Фэнтези", "Романтика", "Приключения")
            val candidateEn = listOf("Action", "Fantasy", "Romance", "Adventure")
            val catalog = FakeCatalog(
                items = listOf(g5Item("Bilingual Hit", candidateEn)),
            )
            val builder = DiscoveryLikeRowBuilder(SuggestionCoordinator(), catalog, includeExternal = false)
            val context = DiscoveryBuildContext(
                mediaType = DiscoveryMediaType.MANGA,
                seeds = listOf(DiscoverySeedInput(entryId = 1L, title = "Ру Тайтл", genres = seedRu)),
                libraryCleanTitles = setOf("ру тайтл"),
                historyCleanTitles = emptySet(),
                hiddenCleanTitles = emptySet(),
                sourceId = 1L,
                sourceIds = listOf(1L),
                sourcePolicy = discoveryQueryPolicyFixture(DiscoveryMediaType.MANGA, listOf(1L)),
            )

            val result = builder.build(context)

            result.map { it.title } shouldBe listOf("Bilingual Hit")
            result.single().seedTitle shouldBe "Ру Тайтл"
        }

    @Test
    fun `native LIKE with requiredGenres never labels an unrelated title with seedTitle`() = runTest {
        val seedGenres = listOf("Drama", "Psychological", "Mystery", "Thriller", "Tragedy")
        val catalog = FakeCatalog(
            items = listOf(
                g5Item("Wrong Comedy", listOf("Comedy", "Slice of Life", "School", "Romance")),
                g5Item("True Match", listOf("Drama", "Psychological", "Mystery", "Thriller", "Comedy")),
            ),
        )
        val builder = DiscoveryLikeRowBuilder(SuggestionCoordinator(), catalog, includeExternal = false)
        val context = DiscoveryBuildContext(
            mediaType = DiscoveryMediaType.MANGA,
            seeds = listOf(DiscoverySeedInput(entryId = 1L, title = "Dark Seed", genres = seedGenres)),
            libraryCleanTitles = setOf("dark seed"),
            historyCleanTitles = emptySet(),
            hiddenCleanTitles = emptySet(),
            genreFilterSpec = GenreFilterSpec(requiredGenres = setOf("Comedy")),
            sourceId = 1L,
            sourceIds = listOf(1L),
            sourcePolicy = discoveryQueryPolicyFixture(DiscoveryMediaType.MANGA, listOf(1L)),
            verificationBudget = DiscoveryVerificationBudget(maxDetails = 0, clock = { 0L }),
        )

        val result = builder.build(context)

        result.map { it.title } shouldBe listOf("True Match")
        result.single().seedTitle shouldBe "Dark Seed"
    }

    @Test
    fun `external LIKE rejects zero-overlap rich-genre outliers while keeping genreless MAL-NU items`() =
        runTest {
            val suggestions = mockk<SuggestionCoordinator>()
            coEvery { suggestions.fetchSuggestions(any(), any(), any()) } returns SuggestionFetchResult(
                listOf(
                    g5Suggestion("Absurd RomCom", listOf("Comedy", "Romance", "Slice of Life")),
                    g5Suggestion("Honest Thriller", listOf("Mystery", "Thriller", "Drama")),
                    g5Suggestion("MAL Without Genres", emptyList()),
                ),
                attemptedSources = 2,
                failedSources = 0,
            )
            val builder = DiscoveryLikeRowBuilder(suggestions)
            val context = DiscoveryBuildContext(
                mediaType = DiscoveryMediaType.NOVEL,
                seeds = listOf(
                    DiscoverySeedInput(
                        entryId = 1L,
                        title = "Dark Thriller",
                        genres = listOf("Mystery", "Thriller", "Psychological", "Drama"),
                    ),
                ),
                libraryCleanTitles = setOf("dark thriller"),
                historyCleanTitles = emptySet(),
                hiddenCleanTitles = emptySet(),
                sourcePolicy = discoveryQueryPolicyFixture(
                    DiscoveryMediaType.NOVEL,
                    emptyList(),
                    mapOf(DiscoveryRowType.LIKE to setOf(DiscoveryExternalProvider.ANILIST)),
                ),
            )

            val result = builder.build(context)

            result.map { it.title } shouldBe listOf("Honest Thriller", "MAL Without Genres")
        }
}
