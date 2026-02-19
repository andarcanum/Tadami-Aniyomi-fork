package eu.kanade.tachiyomi.novelsource

import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import rx.Observable

@Execution(ExecutionMode.CONCURRENT)
class NovelSourceTest {

    @Test
    fun `getNovelDetails delegates to fetchNovelDetails`() = runTest {
        val source = object : NovelSource {
            override val id = 1L
            override val name = "Test"

            override fun fetchNovelDetails(novel: SNovel): Observable<SNovel> {
                return Observable.just(novel.apply { title = "Updated" })
            }

            override fun fetchChapterList(novel: SNovel): Observable<List<SNovelChapter>> {
                return Observable.just(emptyList())
            }

            override fun fetchChapterText(chapter: SNovelChapter): Observable<String> {
                return Observable.just("Body")
            }
        }

        val result = source.getNovelDetails(SNovel.create())

        result.title shouldBe "Updated"
    }

    @Test
    fun `getChapterList delegates to fetchChapterList`() = runTest {
        val source = object : NovelSource {
            override val id = 1L
            override val name = "Test"

            override fun fetchNovelDetails(novel: SNovel): Observable<SNovel> {
                return Observable.just(novel)
            }

            override fun fetchChapterList(novel: SNovel): Observable<List<SNovelChapter>> {
                return Observable.just(
                    listOf(SNovelChapter.create().apply { name = "Chapter" }),
                )
            }

            override fun fetchChapterText(chapter: SNovelChapter): Observable<String> {
                return Observable.just("Body")
            }
        }

        val result = source.getChapterList(SNovel.create())

        result.size shouldBe 1
        result.first().name shouldBe "Chapter"
    }

    @Test
    fun `getChapterText delegates to fetchChapterText`() = runTest {
        val source = object : NovelSource {
            override val id = 1L
            override val name = "Test"

            override fun fetchNovelDetails(novel: SNovel): Observable<SNovel> {
                return Observable.just(novel)
            }

            override fun fetchChapterList(novel: SNovel): Observable<List<SNovelChapter>> {
                return Observable.just(emptyList())
            }

            override fun fetchChapterText(chapter: SNovelChapter): Observable<String> {
                return Observable.just("Body")
            }
        }

        val result = source.getChapterText(SNovelChapter.create())

        result shouldBe "Body"
    }
}

@Execution(ExecutionMode.CONCURRENT)
class NovelCatalogueSourceTest {

    @Test
    fun `getPopularNovels delegates to fetchPopularNovels`() = runTest {
        val source = object : NovelCatalogueSource {
            override val id = 2L
            override val name = "Catalogue"
            override val lang = "en"
            override val supportsLatest = true

            override fun fetchPopularNovels(page: Int): Observable<NovelsPage> {
                val novel = SNovel.create().apply { title = "Popular" }
                return Observable.just(NovelsPage(listOf(novel), hasNextPage = false))
            }

            override fun fetchSearchNovels(
                page: Int,
                query: String,
                filters: NovelFilterList,
            ): Observable<NovelsPage> {
                val novel = SNovel.create().apply { title = "Search" }
                return Observable.just(NovelsPage(listOf(novel), hasNextPage = true))
            }

            override fun fetchLatestUpdates(page: Int): Observable<NovelsPage> {
                val novel = SNovel.create().apply { title = "Latest" }
                return Observable.just(NovelsPage(listOf(novel), hasNextPage = false))
            }

            override fun getFilterList(): NovelFilterList = NovelFilterList()

            override fun fetchNovelDetails(novel: SNovel): Observable<SNovel> = Observable.just(novel)

            override fun fetchChapterList(novel: SNovel): Observable<List<SNovelChapter>> =
                Observable.just(emptyList())

            override fun fetchChapterText(chapter: SNovelChapter): Observable<String> =
                Observable.just("")
        }

        val result = source.getPopularNovels(1)

        result.novels.first().title shouldBe "Popular"
    }

    @Test
    fun `getSearchNovels delegates to fetchSearchNovels`() = runTest {
        val source = object : NovelCatalogueSource {
            override val id = 2L
            override val name = "Catalogue"
            override val lang = "en"
            override val supportsLatest = true

            override fun fetchPopularNovels(page: Int): Observable<NovelsPage> =
                Observable.just(NovelsPage(emptyList(), hasNextPage = false))

            override fun fetchSearchNovels(
                page: Int,
                query: String,
                filters: NovelFilterList,
            ): Observable<NovelsPage> {
                val novel = SNovel.create().apply { title = "Search" }
                return Observable.just(NovelsPage(listOf(novel), hasNextPage = true))
            }

            override fun fetchLatestUpdates(page: Int): Observable<NovelsPage> =
                Observable.just(NovelsPage(emptyList(), hasNextPage = false))

            override fun getFilterList(): NovelFilterList = NovelFilterList()

            override fun fetchNovelDetails(novel: SNovel): Observable<SNovel> = Observable.just(novel)

            override fun fetchChapterList(novel: SNovel): Observable<List<SNovelChapter>> =
                Observable.just(emptyList())

            override fun fetchChapterText(chapter: SNovelChapter): Observable<String> =
                Observable.just("")
        }

        val result = source.getSearchNovels(1, "query", NovelFilterList())

        result.novels.first().title shouldBe "Search"
    }

    @Test
    fun `getLatestUpdates delegates to fetchLatestUpdates`() = runTest {
        val source = object : NovelCatalogueSource {
            override val id = 2L
            override val name = "Catalogue"
            override val lang = "en"
            override val supportsLatest = true

            override fun fetchPopularNovels(page: Int): Observable<NovelsPage> =
                Observable.just(NovelsPage(emptyList(), hasNextPage = false))

            override fun fetchSearchNovels(
                page: Int,
                query: String,
                filters: NovelFilterList,
            ): Observable<NovelsPage> = Observable.just(NovelsPage(emptyList(), hasNextPage = false))

            override fun fetchLatestUpdates(page: Int): Observable<NovelsPage> {
                val novel = SNovel.create().apply { title = "Latest" }
                return Observable.just(NovelsPage(listOf(novel), hasNextPage = false))
            }

            override fun getFilterList(): NovelFilterList = NovelFilterList()

            override fun fetchNovelDetails(novel: SNovel): Observable<SNovel> = Observable.just(novel)

            override fun fetchChapterList(novel: SNovel): Observable<List<SNovelChapter>> =
                Observable.just(emptyList())

            override fun fetchChapterText(chapter: SNovelChapter): Observable<String> =
                Observable.just("")
        }

        val result = source.getLatestUpdates(1)

        result.novels.first().title shouldBe "Latest"
    }
}
