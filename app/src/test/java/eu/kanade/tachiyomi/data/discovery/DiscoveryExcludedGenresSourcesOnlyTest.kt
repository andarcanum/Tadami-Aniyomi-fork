package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import tachiyomi.domain.discovery.model.DiscoveryRowType

class DiscoveryExcludedGenresSourcesOnlyTest {

    private class MangaTri(name: String) : Filter.TriState(name)
    private class MangaGenres(options: List<MangaTri>) : Filter.Group<MangaTri>("Жанры", options)
    private class AnimeTri(name: String) : AnimeFilter.TriState(name)
    private class AnimeGenres(options: List<AnimeTri>) : AnimeFilter.Group<AnimeTri>("Genres", options)
    private class NovelTri(name: String) : NovelFilter.TriState(name)
    private class NovelGenres(options: List<NovelTri>) : NovelFilter.Group<NovelTri>("Genres", options)

    private fun item(
        title: String,
        sourceId: Long = 1L,
        genres: List<String> = emptyList(),
    ) = DiscoveryRowItem(
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = null,
        reason = null,
        seedTitle = null,
        provider = "TestSource",
        score = 1.0,
        sourceId = sourceId,
        sourceUrl = "/item/${title.lowercase()}",
        genres = genres,
    )

    @Test
    fun `sources-only DiscoverySourceRowBuilder hydrates beyond 8 items and drops banned and unverified cards`() = runTest {
        val excluded6 = setOf("Яой", "Юри", "Гарем", "Меха", "Этти", "Ужасы")
        // 15 cards with empty list genres: items 1..12 get hydrated (mix of clean & banned across positions > 8),
        // items 13..15 fail metadata hydration (stay genres = emptyList()) and MUST be dropped.
        val pageItems = (1..15).map { idx -> item("Card$idx") }
        val detailsByUrl = mapOf(
            "/item/card1" to listOf("Фэнтези", "Экшен"),
            "/item/card2" to listOf("Фэнтези", "Яой"),
            "/item/card3" to listOf("Приключения", "Юри"),
            "/item/card4" to listOf("Комедия", "Гарем"),
            "/item/card5" to listOf("Фантастика", "Меха"),
            "/item/card6" to listOf("Романтика", "Этти"),
            "/item/card7" to listOf("Триллер", "Horror"),
            "/item/card8" to listOf("Приключения", "Драма"),
            // Positions 9..12 previously escaped the hardcoded maxDetails=8 cap!
            "/item/card9" to listOf("Фэнтези", "Harem"),
            "/item/card10" to listOf("Экшен", "Yaoi"),
            "/item/card11" to listOf("Боевик", "Фэнтези"),
            "/item/card12" to listOf("Комедия", "Ecchi"),
            // Card13..Card15 remain emptyList() (metadata unavailable) -> must NOT leak!
        )

        var receivedExcludedGenres: Set<String> = emptySet()
        val catalog = object : DiscoverySourceCatalog {
            override suspend fun popular(
                mediaType: DiscoveryMediaType,
                sourceId: Long,
                page: Int,
                releaseStatuses: Set<DiscoveryReleaseStatus>,
            ) = pageItems

            override suspend fun popularPage(
                mediaType: DiscoveryMediaType,
                sourceId: Long,
                page: Int,
                releaseStatuses: Set<DiscoveryReleaseStatus>,
                budget: DiscoveryVerificationBudget?,
                excludedKeys: Set<String>,
                excludedGenres: Set<String>,
            ): DiscoveryBrowsePage {
                receivedExcludedGenres = excludedGenres
                return DiscoveryBrowsePage(
                    items = if (page == 1) pageItems else emptyList(),
                    hasNextPage = false,
                    excludedGenresApplied = false,
                )
            }

            override suspend fun latest(
                mediaType: DiscoveryMediaType,
                sourceId: Long,
                page: Int,
                releaseStatuses: Set<DiscoveryReleaseStatus>,
            ) = emptyList<DiscoveryRowItem>()

            override suspend fun latestPage(
                mediaType: DiscoveryMediaType,
                sourceId: Long,
                page: Int,
                releaseStatuses: Set<DiscoveryReleaseStatus>,
                budget: DiscoveryVerificationBudget?,
            ) = DiscoveryBrowsePage(emptyList(), hasNextPage = false)

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
                val hydrated = items.mapIndexed { idx, it ->
                    if (idx < maxDetails) {
                        val fetched = detailsByUrl[it.sourceUrl].orEmpty()
                        it.copy(genres = fetched)
                    } else {
                        it
                    }
                }
                return DiscoveryGenrePage(
                    items = hydrated,
                    verified = hydrated.count { it.genres.isNotEmpty() },
                    unattempted = (items.size - maxDetails).coerceAtLeast(0),
                    metadataUnavailable = hydrated.count { it.genres.isEmpty() },
                    transientFailures = 0,
                    detailsUsed = minOf(items.size, maxDetails),
                )
            }
        }

        val budget = DiscoveryVerificationBudget(
            budgetMs = 30_000L,
            maxDetails = DiscoveryVerificationBudget.DEFAULT_REQUIRED_MAX_DETAILS,
        )
        val context = DiscoveryBuildContext(
            mediaType = DiscoveryMediaType.MANGA,
            seeds = emptyList(),
            libraryCleanTitles = emptySet(),
            historyCleanTitles = emptySet(),
            hiddenCleanTitles = emptySet(),
            genreFilterSpec = GenreFilterSpec(ignoredGenres = excluded6),
            sourceIds = listOf(1L),
            sourcePolicy = discoveryQueryPolicyFixture(DiscoveryMediaType.MANGA, listOf(1L)),
            verificationBudget = budget,
        )

        val result = DiscoverySourceRowBuilder(catalog, rowCap = 30).build(context)

        receivedExcludedGenres shouldBe excluded6
        result.map { it.title } shouldContainExactlyInAnyOrder listOf("Card1", "Card8", "Card11")
    }

    @Test
    fun `sources-only DiscoveryTasteRowBuilder hydrates native cards under ignoredGenres and drops banned or unverified cards`() = runTest {
        val excluded = setOf("Яой", "Юри", "Гарем", "Этти", "Ужасы")
        val rawItems = listOf(
            item("Clean1"),
            item("BannedYaoi"),
            item("BannedHarem"),
            item("UnverifiedNoGenres"),
            item("Clean2"),
        )
        val details = mapOf(
            "/item/clean1" to listOf("Fantasy"),
            "/item/bannedyaoi" to listOf("Fantasy", "Yaoi"),
            "/item/bannedharem" to listOf("Action", "Harem"),
            "/item/clean2" to listOf("Adventure"),
        )

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
            ) = rawItems

            override suspend fun popularWithGenresAllocated(
                mediaType: DiscoveryMediaType,
                sourceId: Long,
                genres: List<String>,
                page: Int,
                releaseStatuses: Set<DiscoveryReleaseStatus>,
                budget: DiscoveryVerificationBudget?,
                maxDetails: Int,
                excludedKeys: Set<String>,
                excludedGenres: Set<String>,
            ): DiscoveryGenrePage {
                val hydrated = if (budget != null && maxDetails > 0) {
                    rawItems.map { it.copy(genres = details[it.sourceUrl].orEmpty()) }
                } else {
                    rawItems
                }
                return DiscoveryGenrePage(
                    items = hydrated,
                    verified = hydrated.count { it.genres.isNotEmpty() },
                    unattempted = 0,
                    metadataUnavailable = hydrated.count { it.genres.isEmpty() },
                    transientFailures = 0,
                    hasNextPage = false,
                    excludedGenresApplied = false,
                )
            }
        }

        val emptyTrending = object : DiscoveryTrendingSource {
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
            ) = emptyList<DiscoveryTrendingItem>()

            override suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType): DiscoveryMeta? = null
        }

        val context = DiscoveryBuildContext(
            mediaType = DiscoveryMediaType.MANGA,
            seeds = emptyList(),
            libraryCleanTitles = emptySet(),
            historyCleanTitles = emptySet(),
            hiddenCleanTitles = emptySet(),
            tasteProfile = listOf("Fantasy" to 3.0),
            genreFilterSpec = GenreFilterSpec(ignoredGenres = excluded),
            sourceIds = listOf(1L),
            sourcePolicy = discoveryQueryPolicyFixture(DiscoveryMediaType.MANGA, listOf(1L)),
            verificationBudget = DiscoveryVerificationBudget(
                budgetMs = 30_000L,
                maxDetails = DiscoveryVerificationBudget.DEFAULT_REQUIRED_MAX_DETAILS,
            ),
        )

        val result = DiscoveryTasteRowBuilder(
            trending = emptyTrending,
            catalog = catalog,
            sortProvider = { TrendSort.POPULARITY },
            includeExternal = false,
        ).build(context)

        result.map { it.title } shouldContainExactlyInAnyOrder listOf("Clean1", "Clean2")
    }

    @Test
    fun `applyGenreFilters sets TriState STATE_EXCLUDE for manga anime and novel and tracks excludedAll`() = runTest {
        val catalog = AppDiscoverySourceCatalog()

        // Manga: all excluded genres covered
        val mFantasy = MangaTri("Фэнтези")
        val mYaoi = MangaTri("Яой")
        val mHarem = MangaTri("Гарем")
        val mHorror = MangaTri("Ужасы")
        val mangaFilters = FilterList(MangaGenres(listOf(mFantasy, mYaoi, mHarem, mHorror)))
        val mangaApp = catalog.applyMangaGenreFilters(
            filters = mangaFilters,
            genres = listOf("Fantasy"),
            excludedGenres = setOf("Yaoi", "Harem", "Horror"),
        )
        mangaApp.includeApplied shouldBe true
        mangaApp.excludeApplied shouldBe true
        mangaApp.excludedAll shouldBe true
        mFantasy.state shouldBe Filter.TriState.STATE_INCLUDE
        mYaoi.state shouldBe Filter.TriState.STATE_EXCLUDE
        mHarem.state shouldBe Filter.TriState.STATE_EXCLUDE
        mHorror.state shouldBe Filter.TriState.STATE_EXCLUDE

        // Manga: exclude-only with missing genre in source -> excludedAll MUST be false
        val mOnlyAction = MangaTri("Экшен")
        val mOnlyEcchi = MangaTri("Этти")
        val partialFilters = FilterList(MangaGenres(listOf(mOnlyAction, mOnlyEcchi)))
        val partialApp = catalog.applyMangaGenreFilters(
            filters = partialFilters,
            genres = null,
            excludedGenres = setOf("Этти", "Меха"),
        )
        partialApp.includeApplied shouldBe false
        partialApp.excludeApplied shouldBe true
        partialApp.excludedAll shouldBe false
        mOnlyEcchi.state shouldBe Filter.TriState.STATE_EXCLUDE

        // Anime
        val aAction = AnimeTri("Action")
        val aMecha = AnimeTri("Mecha")
        val animeApp = catalog.applyAnimeGenreFilters(
            filters = AnimeFilterList(AnimeGenres(listOf(aAction, aMecha))),
            genres = null,
            excludedGenres = setOf("Меха"),
        )
        animeApp.excludeApplied shouldBe true
        animeApp.excludedAll shouldBe true
        aMecha.state shouldBe AnimeFilter.TriState.STATE_EXCLUDE

        // Novel
        val nFantasy = NovelTri("Fantasy")
        val nYuri = NovelTri("Yuri")
        val novelApp = catalog.applyNovelGenreFilters(
            filters = NovelFilterList(NovelGenres(listOf(nFantasy, nYuri))),
            genres = listOf("Фэнтези"),
            excludedGenres = setOf("Юри"),
        )
        novelApp.includeApplied shouldBe true
        novelApp.excludeApplied shouldBe true
        novelApp.excludedAll shouldBe true
        nFantasy.state shouldBe NovelFilter.TriState.STATE_INCLUDE
        nYuri.state shouldBe NovelFilter.TriState.STATE_EXCLUDE
    }
}
