package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import tachiyomi.domain.source.manga.service.MangaSourceManager
import java.io.IOException

class DiscoverySourceCatalogPageTest {
    private val media = DiscoveryMediaType.MANGA
    private val sourceId = 7L

    private class GenreOption(name: String) : Filter.CheckBox(name)
    private class GenreGroup(options: List<Filter.CheckBox>) :
        Filter.Group<Filter.CheckBox>("Genres", options)

    private fun source(supportsLatest: Boolean = true): CatalogueSource = mockk<CatalogueSource>().also { source ->
        every { source.id } returns sourceId
        every { source.name } returns "Fixture"
        every { source.lang } returns "en"
        every { source.supportsLatest } returns supportsLatest
        every { source.getFilterList() } returns FilterList()
    }

    private fun catalog(source: MangaSource): AppDiscoverySourceCatalog {
        val manager = mockk<MangaSourceManager>()
        every { manager.getOrStub(sourceId) } returns source
        return AppDiscoverySourceCatalog(mangaSourceManagerProvider = { manager })
    }

    private fun manga(
        title: String,
        url: String,
        status: Int = SManga.UNKNOWN,
        genre: String? = null,
    ): SManga = SManga.create().apply {
        this.title = title
        this.url = url
        this.status = status
        this.genre = genre
    }

    private fun budget(maxDetails: Int) = DiscoveryVerificationBudget(
        maxDetails = maxDetails,
        maxConcurrentDetails = 1,
        clock = { 0L },
    )

    @Test
    fun `popular preserves raw continuation after status filtering removes the page`(): Unit = runTest {
        val source = source()
        coEvery { source.getPopularManga(2) } returns MangasPage(
            listOf(manga("Completed", "/completed", status = SManga.COMPLETED)),
            hasNextPage = true,
        )

        val result = catalog(source).popularPage(
            media,
            sourceId,
            page = 2,
            releaseStatuses = setOf(DiscoveryReleaseStatus.ONGOING),
        )

        result.items shouldBe emptyList()
        result.hasNextPage shouldBe true
        coVerify(exactly = 1) { source.getPopularManga(2) }
    }

    @Test
    fun `latest preserves raw continuation after status filtering removes the page`(): Unit = runTest {
        val source = source()
        coEvery { source.getLatestUpdates(2) } returns MangasPage(
            listOf(manga("Completed", "/completed", status = SManga.COMPLETED)),
            hasNextPage = true,
        )

        val result = catalog(source).latestPage(
            media,
            sourceId,
            page = 2,
            releaseStatuses = setOf(DiscoveryReleaseStatus.ONGOING),
        )

        result.items shouldBe emptyList()
        result.hasNextPage shouldBe true
        coVerify(exactly = 1) { source.getLatestUpdates(2) }
    }

    @Test
    fun `popular page two returns native empty end without reopening page one`(): Unit = runTest {
        val source = source()
        coEvery { source.getPopularManga(2) } returns MangasPage(emptyList(), hasNextPage = false)
        coEvery { source.getPopularManga(1) } returns MangasPage(
            listOf(manga("First Page", "/first")),
            hasNextPage = true,
        )

        val result = catalog(source).popularPage(media, sourceId, page = 2)

        result.items shouldBe emptyList()
        result.hasNextPage shouldBe false
        coVerify(exactly = 1) { source.getPopularManga(2) }
        coVerify(exactly = 0) { source.getPopularManga(1) }
    }

    @Test
    fun `latest page two returns native empty end without reopening or substituting pages`(): Unit = runTest {
        val source = source()
        coEvery { source.getLatestUpdates(2) } returns MangasPage(emptyList(), hasNextPage = false)
        coEvery { source.getLatestUpdates(1) } returns MangasPage(
            listOf(manga("First Page", "/first")),
            hasNextPage = true,
        )
        coEvery { source.getPopularManga(2) } returns MangasPage(
            listOf(manga("Popular", "/popular")),
            hasNextPage = true,
        )

        val result = catalog(source).latestPage(media, sourceId, page = 2)

        result.items shouldBe emptyList()
        result.hasNextPage shouldBe false
        coVerify(exactly = 1) { source.getLatestUpdates(2) }
        coVerify(exactly = 0) { source.getLatestUpdates(1) }
        coVerify(exactly = 0) { source.getPopularManga(any()) }
    }

    @Test
    fun `latest unsupported source uses popular on the requested page only`(): Unit = runTest {
        val source = source(supportsLatest = false)
        coEvery { source.getPopularManga(2) } returns MangasPage(
            listOf(manga("Second Page", "/second")),
            hasNextPage = false,
        )
        coEvery { source.getPopularManga(1) } returns MangasPage(
            listOf(manga("First Page", "/first")),
            hasNextPage = true,
        )

        val result = catalog(source).latestPage(media, sourceId, page = 2)

        result.items.map { it.sourceUrl } shouldBe listOf("/second")
        result.hasNextPage shouldBe false
        coVerify(exactly = 1) { source.getPopularManga(2) }
        coVerify(exactly = 0) { source.getPopularManga(1) }
        coVerify(exactly = 0) { source.getLatestUpdates(any()) }
    }

    @Test
    fun `popular transport failure is not a successful exhausted page`(): Unit = runTest {
        val source = source()
        coEvery { source.getPopularManga(2) } throws IOException("fixture transport failure")
        coEvery { source.getPopularManga(1) } returns MangasPage(
            listOf(manga("First Page", "/first")),
            hasNextPage = true,
        )
        val catalog = catalog(source)

        shouldThrow<IOException> { catalog.popularPage(media, sourceId, page = 2) }
        coVerify(exactly = 0) { source.getPopularManga(1) }
    }

    @Test
    fun `latest transport failure is not a successful exhausted page`(): Unit = runTest {
        val source = source()
        coEvery { source.getLatestUpdates(2) } throws IOException("fixture transport failure")
        coEvery { source.getLatestUpdates(1) } returns MangasPage(
            listOf(manga("First Page", "/first")),
            hasNextPage = true,
        )
        coEvery { source.getPopularManga(2) } returns MangasPage(
            listOf(manga("Popular", "/popular")),
            hasNextPage = true,
        )
        val catalog = catalog(source)

        shouldThrow<IOException> { catalog.latestPage(media, sourceId, page = 2) }
        coVerify(exactly = 0) { source.getLatestUpdates(1) }
        coVerify(exactly = 0) { source.getPopularManga(any()) }
    }

    @Test
    fun `non catalogue native source cannot report a successful exhausted page`(): Unit = runTest {
        val catalog = catalog(mockk<MangaSource>())

        shouldThrow<Exception> { catalog.popularPage(media, sourceId) }
        shouldThrow<Exception> { catalog.latestPage(media, sourceId) }
    }

    @Test
    fun `canonical excluded cold card leaves the only detail for an eligible card`(): Unit = runTest {
        val source = source()
        coEvery { source.getPopularManga(1) } returns MangasPage(
            listOf(manga("Excluded", "/excluded"), manga("Eligible", "/eligible")),
            hasNextPage = true,
        )
        coEvery { source.getMangaUpdate(match { it.url == "/excluded" }, emptyList(), true, false) } returns
            SMangaUpdate(manga("Excluded", "/excluded", genre = "Fantasy"), emptyList())
        coEvery { source.getMangaUpdate(match { it.url == "/eligible" }, emptyList(), true, false) } returns
            SMangaUpdate(manga("Eligible", "/eligible", genre = "Fantasy"), emptyList())
        val budget = budget(maxDetails = 1)

        val result = catalog(source).popularWithGenresAllocated(
            media,
            sourceId,
            genres = listOf("Fantasy"),
            budget = budget,
            maxDetails = 1,
            excludedKeys = setOf("src:manga:7:/excluded"),
        )

        result.items.map { it.sourceUrl } shouldBe listOf("/eligible")
        result.items.single().genres shouldBe listOf("Fantasy")
        result.pending shouldBe emptyList()
        result.hasNextPage shouldBe true
        result.detailsUsed shouldBe 1
        budget.detailsUsed shouldBe 1
        coVerify(exactly = 1) { source.getPopularManga(1) }
        coVerify(exactly = 0) { source.getMangaUpdate(match { it.url == "/excluded" }, any(), any(), any()) }
        coVerify(exactly = 1) { source.getMangaUpdate(match { it.url == "/eligible" }, emptyList(), true, false) }
    }

    @Test
    fun `fully excluded cold page spends no details and preserves native continuation`(): Unit = runTest {
        val source = source()
        coEvery { source.getPopularManga(1) } returns MangasPage(
            listOf(manga("Excluded", "/excluded")),
            hasNextPage = true,
        )
        coEvery { source.getMangaUpdate(any(), emptyList(), true, false) } returns
            SMangaUpdate(manga("Excluded", "/excluded", genre = "Fantasy"), emptyList())
        val budget = budget(maxDetails = 1)

        val result = catalog(source).popularWithGenresAllocated(
            media,
            sourceId,
            genres = listOf("Fantasy"),
            budget = budget,
            maxDetails = 1,
            excludedKeys = setOf("excluded"),
        )

        result.items shouldBe emptyList()
        result.pending shouldBe emptyList()
        result.hasNextPage shouldBe true
        result.detailsUsed shouldBe 0
        budget.remainingDetails shouldBe 1
        coVerify(exactly = 1) { source.getPopularManga(1) }
        coVerify(exactly = 0) { source.getMangaUpdate(any(), any(), any(), any()) }
    }

    @Test
    fun `popular status enrichment shares the budget and keeps unattempted UNKNOWN cards`(): Unit = runTest {
        val source = source()
        coEvery { source.getPopularManga(1) } returns MangasPage(
            listOf(manga("Completed", "/completed"), manga("Unattempted", "/unattempted")),
            hasNextPage = true,
        )
        coEvery { source.getMangaUpdate(match { it.url == "/completed" }, emptyList(), true, false) } returns
            SMangaUpdate(manga("Completed", "/completed", SManga.COMPLETED, "Fantasy"), emptyList())
        coEvery { source.getMangaUpdate(match { it.url == "/unattempted" }, emptyList(), true, false) } returns
            SMangaUpdate(manga("Unattempted", "/unattempted", SManga.ONGOING, "Fantasy"), emptyList())
        val budget = budget(maxDetails = 1)

        val result = catalog(source).popularPage(
            media,
            sourceId,
            releaseStatuses = setOf(DiscoveryReleaseStatus.ONGOING),
            budget = budget,
        )

        result.items.map { it.sourceUrl } shouldBe listOf("/unattempted")
        result.items.single().genres shouldBe emptyList()
        result.hasNextPage shouldBe true
        budget.detailsUsed shouldBe 1
        coVerify(exactly = 1) { source.getMangaUpdate(match { it.url == "/completed" }, emptyList(), true, false) }
        coVerify(exactly = 0) { source.getMangaUpdate(match { it.url == "/unattempted" }, any(), any(), any()) }
    }

    @Test
    fun `filterless source falls back to browsing popular manga`(): Unit = runTest {
        val source = source()
        coEvery { source.getPopularManga(1) } returns MangasPage(
            listOf(manga("Popular One", "/popular", genre = "MILF")),
            hasNextPage = true,
        )

        val result = catalog(source).popularWithGenresAllocated(
            media,
            sourceId,
            genres = listOf("milf"),
            budget = budget(maxDetails = 0),
            maxDetails = 0,
        )

        result.genreFilterApplied shouldBe false
        result.items.map { it.sourceUrl } shouldBe listOf("/popular")
        result.hasNextPage shouldBe true
        coVerify(exactly = 1) { source.getPopularManga(1) }
    }

    @Test
    fun `genre query reports whether the source filter was applied`(): Unit = runTest {
        val source = source()
        every { source.getFilterList() } returns FilterList(
            GenreGroup(listOf(GenreOption("MILF"), GenreOption("Drama"))),
        )
        coEvery { source.getSearchManga(1, "", any()) } returns MangasPage(
            listOf(manga("Milf One", "/milf")),
            hasNextPage = true,
        )

        val result = catalog(source).popularWithGenresAllocated(
            media,
            sourceId,
            genres = listOf("milf"),
            budget = budget(maxDetails = 0),
            maxDetails = 0,
        )

        result.genreFilterApplied shouldBe true
        result.items.map { it.sourceUrl } shouldBe listOf("/milf")
        coVerify(exactly = 1) { source.getSearchManga(1, "", any()) }
        coVerify(exactly = 0) { source.getPopularManga(any()) }
    }

    @Test
    fun `isGenreGroupName recognizes themes tags and demographics`() {
        isGenreGroupName("Themes") shouldBe true
        isGenreGroupName("Темы") shouldBe true
        isGenreGroupName("Метки") shouldBe true
        isGenreGroupName("Демография") shouldBe true
        isGenreGroupName("Demographics") shouldBe true
        isGenreGroupName("Формат") shouldBe true
    }

    @Test
    fun `popular status enrichment excludes retired cards before spending details`(): Unit = runTest {
        val source = source()
        coEvery { source.getPopularManga(1) } returns MangasPage(
            listOf(manga("Retired", "/retired"), manga("Fresh", "/fresh")),
            hasNextPage = true,
        )
        coEvery { source.getMangaUpdate(match { it.url == "/retired" }, emptyList(), true, false) } returns
            SMangaUpdate(manga("Retired", "/retired", SManga.COMPLETED, "Fantasy"), emptyList())
        coEvery { source.getMangaUpdate(match { it.url == "/fresh" }, emptyList(), true, false) } returns
            SMangaUpdate(manga("Fresh", "/fresh", SManga.ONGOING, "Fantasy"), emptyList())
        val budget = budget(maxDetails = 1)

        val result = catalog(source).popularPage(
            media,
            sourceId,
            releaseStatuses = setOf(DiscoveryReleaseStatus.ONGOING),
            budget = budget,
            excludedKeys = setOf("src:manga:7:/retired"),
        )

        result.items.map { it.sourceUrl } shouldBe listOf("/fresh")
        result.items.single().genres shouldBe listOf("Fantasy")
        result.hasNextPage shouldBe true
        budget.detailsUsed shouldBe 1
        coVerify(exactly = 0) { source.getMangaUpdate(match { it.url == "/retired" }, any(), any(), any()) }
        coVerify(exactly = 1) { source.getMangaUpdate(match { it.url == "/fresh" }, emptyList(), true, false) }
    }

    @Test
    fun `genre status enrichment respects generation exclusions and source allowance`(): Unit = runTest {
        val source = source()
        coEvery { source.getPopularManga(1) } returns MangasPage(
            listOf(manga("Excluded", "/excluded"), manga("Eligible", "/eligible")),
            hasNextPage = false,
        )
        coEvery { source.getMangaUpdate(match { it.url == "/excluded" }, emptyList(), true, false) } returns
            SMangaUpdate(manga("Excluded", "/excluded", SManga.ONGOING, "Fantasy"), emptyList())
        coEvery { source.getMangaUpdate(match { it.url == "/eligible" }, emptyList(), true, false) } returns
            SMangaUpdate(manga("Eligible", "/eligible", SManga.ONGOING, "Fantasy"), emptyList())
        val budget = budget(maxDetails = 10)

        val result = catalog(source).popularWithGenresAllocated(
            media,
            sourceId,
            genres = listOf("Fantasy"),
            releaseStatuses = setOf(DiscoveryReleaseStatus.ONGOING),
            budget = budget,
            maxDetails = 1,
            excludedKeys = setOf("src:manga:7:/excluded"),
        )

        result.items.map { it.sourceUrl } shouldBe listOf("/eligible")
        result.items.single().genres shouldBe listOf("Fantasy")
        result.pending shouldBe emptyList()
        result.hasNextPage shouldBe false
        result.detailsUsed shouldBe 1
        budget.remainingDetails shouldBe 9
        coVerify(exactly = 1) { source.getPopularManga(1) }
        coVerify(exactly = 0) { source.getMangaUpdate(match { it.url == "/excluded" }, any(), any(), any()) }
        coVerify(exactly = 1) { source.getMangaUpdate(match { it.url == "/eligible" }, emptyList(), true, false) }
    }
}
