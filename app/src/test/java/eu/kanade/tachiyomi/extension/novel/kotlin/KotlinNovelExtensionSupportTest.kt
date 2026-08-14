package eu.kanade.tachiyomi.extension.novel.kotlin

import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.novel.NovelImageRequestSource
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.Response
import org.junit.jupiter.api.Test

class KotlinNovelExtensionSupportTest {

    @Test
    fun `filtered popular novel listing delegates to manga search with converted filters`() = runTest {
        val source = RecordingCatalogueSource()
        val adapter = source.asKotlinNovelCatalogueSource(pluginId = "test-plugin")
        val filters = NovelFilterList(object : NovelFilter.CheckBox("Completed", true) {})

        val page = adapter.getPopularNovels(page = 1, filters = filters)

        page.novels.single().title shouldBe "search"
        source.popularCalls shouldBe 0
        source.searchCalls shouldBe 1
        source.lastSearchQuery shouldBe ""
        (source.lastSearchFilters?.first() as Filter.CheckBox).state shouldBe true
    }

    @Test
    fun `filtered latest novel listing delegates to manga search with converted filters`() = runTest {
        val source = RecordingCatalogueSource()
        val adapter = source.asKotlinNovelCatalogueSource(pluginId = "test-plugin")
        val filters = NovelFilterList(object : NovelFilter.CheckBox("Completed", true) {})

        val page = adapter.getLatestUpdates(page = 1, filters = filters)

        page.novels.single().title shouldBe "search"
        source.latestCalls shouldBe 0
        source.searchCalls shouldBe 1
        source.lastSearchQuery shouldBe ""
        (source.lastSearchFilters?.first() as Filter.CheckBox).state shouldBe true
    }

    @Test
    fun `converted filters preserve extension custom manga filter class identity`() = runTest {
        val source = RecordingCatalogueSource()
        val adapter = source.asKotlinNovelCatalogueSource(pluginId = "test-plugin")
        val filters = NovelFilterList(
            object : NovelFilter.Group<NovelFilter<*>>(
                "Filters",
                listOf(object : NovelFilter.CheckBox("Custom genre", true) {}),
            ) {},
        )

        adapter.getSearchNovels(page = 1, query = "", filters = filters)

        val group = source.lastSearchFilters?.filterIsInstance<Filter.Group<*>>()?.single()!!
        val customFilter = group.state.single() as RecordingCatalogueSource.CustomGenreFilter
        customFilter.state shouldBe true
    }

    @Test
    fun `unfiltered popular and latest novel listings keep base manga listing endpoints`() = runTest {
        val source = RecordingCatalogueSource()
        val adapter = source.asKotlinNovelCatalogueSource(pluginId = "test-plugin")

        adapter.getPopularNovels(page = 1, filters = NovelFilterList()).novels.single().title shouldBe "popular"
        adapter.getLatestUpdates(page = 1, filters = NovelFilterList()).novels.single().title shouldBe "latest"

        source.popularCalls shouldBe 1
        source.latestCalls shouldBe 1
        source.searchCalls shouldBe 0
    }

    @Test
    fun `toNovel resolves relative cover URLs using source baseUrl`() = runTest {
        val source = object : eu.kanade.tachiyomi.source.online.HttpSource() {
            override val id: Long = 123L
            override val name: String = "Test Http Source"
            override val lang: String = "en"
            override val baseUrl: String = "https://example.com"
            override val supportsLatest: Boolean = false

            override suspend fun getPopularManga(page: Int): MangasPage {
                return MangasPage(
                    mangas = listOf(
                        SManga.create().also {
                            it.url = "/manga"
                            it.title = "Manga Title"
                            it.thumbnail_url = "/covers/test.jpg"
                        },
                    ),
                    hasNextPage = false,
                )
            }

            override suspend fun getLatestUpdates(page: Int) = throw NotImplementedError()
            override suspend fun getSearchManga(
                page: Int,
                query: String,
                filters: FilterList,
            ) = throw NotImplementedError()
            override fun getFilterList() = FilterList()

            override fun popularMangaRequest(page: Int) = throw NotImplementedError()
            override fun popularMangaParse(response: okhttp3.Response) = throw NotImplementedError()
            override fun latestUpdatesRequest(page: Int) = throw NotImplementedError()
            override fun latestUpdatesParse(response: okhttp3.Response) = throw NotImplementedError()
            override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw NotImplementedError()
            override fun searchMangaParse(response: okhttp3.Response) = throw NotImplementedError()
            override fun mangaDetailsParse(response: okhttp3.Response) = throw NotImplementedError()
            override fun chapterListParse(response: okhttp3.Response) = throw NotImplementedError()
            override fun pageListParse(response: okhttp3.Response) = throw NotImplementedError()
            override fun chapterPageParse(
                response: okhttp3.Response,
            ): eu.kanade.tachiyomi.source.model.SChapter = throw NotImplementedError()
            override fun imageUrlParse(response: okhttp3.Response): String = throw NotImplementedError()
        }

        val adapter = source.asKotlinNovelCatalogueSource(pluginId = "test-plugin")
        val page = adapter.getPopularNovels(page = 1)

        page.novels.single().thumbnail_url shouldBe "https://example.com/covers/test.jpg"
    }

    @Test
    fun `toNovel translates SManga status to SNovel status correctly`() = runTest {
        val source = object : eu.kanade.tachiyomi.source.online.HttpSource() {
            override val id: Long = 123L
            override val name: String = "Test Http Source"
            override val lang: String = "en"
            override val baseUrl: String = "https://example.com"
            override val supportsLatest: Boolean = false

            override suspend fun getPopularManga(page: Int): MangasPage {
                return MangasPage(
                    mangas = listOf(
                        SManga.create().also {
                            it.url = "/manga"
                            it.title = "Manga Title"
                            it.status = 1 // 1 is COMPLETED in standard Tachiyomi (which maps to SNovel.COMPLETED = 2)
                        },
                    ),
                    hasNextPage = false,
                )
            }

            override suspend fun getLatestUpdates(page: Int) = throw NotImplementedError()
            override suspend fun getSearchManga(
                page: Int,
                query: String,
                filters: FilterList,
            ) = throw NotImplementedError()
            override fun getFilterList() = FilterList()

            override fun popularMangaRequest(page: Int) = throw NotImplementedError()
            override fun popularMangaParse(response: okhttp3.Response) = throw NotImplementedError()
            override fun latestUpdatesRequest(page: Int) = throw NotImplementedError()
            override fun latestUpdatesParse(response: okhttp3.Response) = throw NotImplementedError()
            override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw NotImplementedError()
            override fun searchMangaParse(response: okhttp3.Response) = throw NotImplementedError()
            override fun mangaDetailsParse(response: okhttp3.Response) = throw NotImplementedError()
            override fun chapterListParse(response: okhttp3.Response) = throw NotImplementedError()
            override fun pageListParse(response: okhttp3.Response) = throw NotImplementedError()
            override fun chapterPageParse(
                response: okhttp3.Response,
            ): eu.kanade.tachiyomi.source.model.SChapter = throw NotImplementedError()
            override fun imageUrlParse(response: okhttp3.Response): String = throw NotImplementedError()
        }

        val adapter = source.asKotlinNovelCatalogueSource(pluginId = "test-plugin")
        val page = adapter.getPopularNovels(page = 1)

        page.novels.single().status shouldBe SNovel.COMPLETED
    }

    @Test
    fun `toManga translates SNovel status to SManga status correctly`() = runTest {
        var receivedStatus: Int? = null
        val source = object : eu.kanade.tachiyomi.source.online.HttpSource() {
            override val id: Long = 123L
            override val name: String = "Test Http Source"
            override val lang: String = "en"
            override val baseUrl: String = "https://example.com"
            override val supportsLatest: Boolean = false

            override suspend fun getMangaDetails(manga: SManga): SManga {
                receivedStatus = manga.status
                return manga
            }

            override suspend fun getPopularManga(page: Int) = throw NotImplementedError()
            override suspend fun getLatestUpdates(page: Int) = throw NotImplementedError()
            override suspend fun getSearchManga(
                page: Int,
                query: String,
                filters: FilterList,
            ) = throw NotImplementedError()
            override fun getFilterList() = FilterList()

            override fun popularMangaRequest(page: Int) = throw NotImplementedError()
            override fun popularMangaParse(response: okhttp3.Response) = throw NotImplementedError()
            override fun latestUpdatesRequest(page: Int) = throw NotImplementedError()
            override fun latestUpdatesParse(response: okhttp3.Response) = throw NotImplementedError()
            override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw NotImplementedError()
            override fun searchMangaParse(response: okhttp3.Response) = throw NotImplementedError()
            override fun mangaDetailsParse(response: okhttp3.Response) = throw NotImplementedError()
            override fun chapterListParse(response: okhttp3.Response) = throw NotImplementedError()
            override fun pageListParse(response: okhttp3.Response) = throw NotImplementedError()
            override fun chapterPageParse(
                response: okhttp3.Response,
            ): eu.kanade.tachiyomi.source.model.SChapter = throw NotImplementedError()
            override fun imageUrlParse(response: okhttp3.Response): String = throw NotImplementedError()
        }

        val adapter = source.asKotlinNovelCatalogueSource(pluginId = "test-plugin")
        val novel = SNovel.create().apply {
            status = SNovel.COMPLETED // SNovel.COMPLETED is 2
            title = "Test Novel"
            url = "/test"
        }
        adapter.getNovelDetails(novel)

        receivedStatus shouldBe 1 // 1 is COMPLETED in standard Tachiyomi
    }

    @Test
    fun `adapter implements NovelHttpSource and NovelImageRequestSource`() = runTest {
        val source = object : HttpSource() {
            override val id: Long = 123L
            override val name: String = "Test Http Source"
            override val lang: String = "en"
            override val baseUrl: String = "https://example.com"
            override val supportsLatest: Boolean = false

            override fun headersBuilder(): Headers.Builder =
                Headers.Builder().add("Referer", "https://example.com/")

            override suspend fun getPopularManga(page: Int): MangasPage = throw NotImplementedError()
            override suspend fun getLatestUpdates(page: Int) = throw NotImplementedError()
            override suspend fun getSearchManga(page: Int, query: String, filters: FilterList) =
                throw NotImplementedError()
            override fun getFilterList() = FilterList()
            override fun popularMangaRequest(page: Int) = throw NotImplementedError()
            override fun popularMangaParse(response: Response) = throw NotImplementedError()
            override fun latestUpdatesRequest(page: Int) = throw NotImplementedError()
            override fun latestUpdatesParse(response: Response) = throw NotImplementedError()
            override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
                throw NotImplementedError()
            override fun searchMangaParse(response: Response) = throw NotImplementedError()
            override fun mangaDetailsParse(response: Response) = throw NotImplementedError()
            override fun chapterListParse(response: Response) = throw NotImplementedError()
            override fun pageListParse(response: Response) = throw NotImplementedError()
            override fun chapterPageParse(response: Response): SChapter = throw NotImplementedError()
            override fun imageUrlParse(response: Response): String = throw NotImplementedError()
        }

        val adapter = source.asKotlinNovelCatalogueSource(pluginId = "test-plugin")
        (adapter is NovelHttpSource) shouldBe true
        (adapter as NovelHttpSource).baseUrl shouldBe "https://example.com"
        (adapter is NovelImageRequestSource) shouldBe true
        val headers = (adapter as NovelImageRequestSource).getImageRequestHeaders()
        headers["Referer"] shouldBe "https://example.com/"
    }

    @Test
    fun `getChapterText concatenates multiple pages with line breaks`() = runTest {
        val source = object : HttpSource() {
            override val id: Long = 123L
            override val name: String = "Test Http Source"
            override val lang: String = "en"
            override val baseUrl: String = "https://example.com"
            override val supportsLatest: Boolean = false

            override suspend fun getPageList(chapter: SChapter): List<Page> {
                return listOf(
                    Page(0, "/page1"),
                    Page(1, "/page2"),
                )
            }

            override suspend fun fetchPageText(page: Page): String {
                return if (page.index == 0) "Paragraph 1" else "Paragraph 2"
            }

            override suspend fun getPopularManga(page: Int): MangasPage = throw NotImplementedError()
            override suspend fun getLatestUpdates(page: Int) = throw NotImplementedError()
            override suspend fun getSearchManga(page: Int, query: String, filters: FilterList) =
                throw NotImplementedError()
            override fun getFilterList() = FilterList()
            override fun popularMangaRequest(page: Int) = throw NotImplementedError()
            override fun popularMangaParse(response: Response) = throw NotImplementedError()
            override fun latestUpdatesRequest(page: Int) = throw NotImplementedError()
            override fun latestUpdatesParse(response: Response) = throw NotImplementedError()
            override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
                throw NotImplementedError()
            override fun searchMangaParse(response: Response) = throw NotImplementedError()
            override fun mangaDetailsParse(response: Response) = throw NotImplementedError()
            override fun chapterListParse(response: Response) = throw NotImplementedError()
            override fun pageListParse(response: Response) = throw NotImplementedError()
            override fun chapterPageParse(response: Response): SChapter = throw NotImplementedError()
            override fun imageUrlParse(response: Response): String = throw NotImplementedError()
        }

        val adapter = source.asKotlinNovelCatalogueSource(pluginId = "test-plugin")
        val chapter = SNovelChapter.create().apply { url = "/ch1" }
        val text = adapter.getChapterText(chapter)

        text shouldBe "Paragraph 1\n\nParagraph 2"
    }

    @Test
    fun `supported lib versions include 1_6 and 1_7`() {
        KotlinNovelExtensionLoader.SUPPORTED_LIB_VERSIONS.contains(1.6) shouldBe true
        KotlinNovelExtensionLoader.SUPPORTED_LIB_VERSIONS.contains(1.7) shouldBe true
    }

    private class RecordingCatalogueSource : CatalogueSource {
        override val id: Long = 123L
        override val name: String = "Recording source"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        var popularCalls = 0
        var latestCalls = 0
        var searchCalls = 0
        var lastSearchQuery: String? = null
        var lastSearchFilters: FilterList? = null

        override suspend fun getPopularManga(page: Int): MangasPage {
            popularCalls += 1
            return mangaPage("popular")
        }

        override suspend fun getLatestUpdates(page: Int): MangasPage {
            latestCalls += 1
            return mangaPage("latest")
        }

        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
            searchCalls += 1
            lastSearchQuery = query
            lastSearchFilters = filters
            return mangaPage("search")
        }

        override fun getFilterList(): FilterList {
            return FilterList(
                object : Filter.CheckBox("Completed", false) {},
                object : Filter.Group<Filter<*>>("Filters", listOf(CustomGenreFilter())) {},
            )
        }

        class CustomGenreFilter : Filter.CheckBox("Custom genre", false)

        private fun mangaPage(title: String): MangasPage {
            return MangasPage(
                mangas = listOf(
                    SManga.create().also {
                        it.url = "/$title"
                        it.title = title
                    },
                ),
                hasNextPage = false,
            )
        }
    }
}
