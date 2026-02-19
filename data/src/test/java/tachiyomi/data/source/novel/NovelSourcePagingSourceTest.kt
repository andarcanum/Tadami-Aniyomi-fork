package tachiyomi.data.source.novel

import androidx.paging.PagingSource
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import rx.Observable
import tachiyomi.domain.items.chapter.model.NoChaptersException

class NovelSourcePagingSourceTest {

    @Test
    fun `search paging source returns data and nextKey`() = runTest {
        val source = FakeNovelCatalogueSource(hasNext = true, novels = listOf(makeNovel("A")))
        val pagingSource = NovelSourceSearchPagingSource(source, "q", NovelFilterList())

        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 20,
                placeholdersEnabled = false,
            ),
        )

        val page = result as PagingSource.LoadResult.Page
        page.data.first().title shouldBe "A"
        page.nextKey shouldBe 2L
    }

    @Test
    fun `search paging source returns error on empty data`() = runTest {
        val source = FakeNovelCatalogueSource(hasNext = false, novels = emptyList())
        val pagingSource = NovelSourceSearchPagingSource(source, "q", NovelFilterList())

        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 20,
                placeholdersEnabled = false,
            ),
        )

        val error = result as PagingSource.LoadResult.Error
        error.throwable::class shouldBe NoChaptersException::class
    }

    @Test
    fun `search paging source treats empty append page as end of list`() = runTest {
        val first = makeNovel("A")
        val source = FakePagedNovelCatalogueSource(
            responses = mapOf(
                1 to NovelsPage(listOf(first), hasNextPage = true),
                2 to NovelsPage(emptyList(), hasNextPage = false),
            ),
        )
        val pagingSource = NovelSourceSearchPagingSource(source, "q", NovelFilterList())

        val result = pagingSource.load(
            PagingSource.LoadParams.Append(
                key = 2,
                loadSize = 20,
                placeholdersEnabled = false,
            ),
        )

        val page = result as PagingSource.LoadResult.Page
        page.data.size shouldBe 0
        page.nextKey shouldBe null
    }

    private fun makeNovel(title: String): SNovel = SNovel.create().apply {
        url = "/novel"
        this.title = title
    }

    private class FakeNovelCatalogueSource(
        val hasNext: Boolean,
        val novels: List<SNovel>,
    ) : NovelCatalogueSource {
        override val id: Long = 1
        override val name: String = "Fake"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override fun fetchPopularNovels(page: Int): Observable<NovelsPage> =
            Observable.just(NovelsPage(novels, hasNext))

        override fun fetchSearchNovels(
            page: Int,
            query: String,
            filters: NovelFilterList,
        ): Observable<NovelsPage> =
            Observable.just(NovelsPage(novels, hasNext))

        override fun fetchLatestUpdates(page: Int): Observable<NovelsPage> =
            Observable.just(NovelsPage(novels, hasNext))

        override fun getFilterList(): NovelFilterList = NovelFilterList()

        override fun fetchNovelDetails(novel: SNovel): Observable<SNovel> = Observable.just(novel)

        override fun fetchChapterList(novel: SNovel): Observable<List<SNovelChapter>> =
            Observable.just(emptyList())

        override fun fetchChapterText(chapter: SNovelChapter): Observable<String> =
            Observable.just("")
    }

    private class FakePagedNovelCatalogueSource(
        private val responses: Map<Int, NovelsPage>,
    ) : NovelCatalogueSource {
        override val id: Long = 1
        override val name: String = "Fake"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override fun fetchPopularNovels(page: Int): Observable<NovelsPage> =
            Observable.just(responses[page] ?: NovelsPage(emptyList(), hasNextPage = false))

        override fun fetchSearchNovels(
            page: Int,
            query: String,
            filters: NovelFilterList,
        ): Observable<NovelsPage> =
            Observable.just(responses[page] ?: NovelsPage(emptyList(), hasNextPage = false))

        override fun fetchLatestUpdates(page: Int): Observable<NovelsPage> =
            Observable.just(responses[page] ?: NovelsPage(emptyList(), hasNextPage = false))

        override fun getFilterList(): NovelFilterList = NovelFilterList()

        override fun fetchNovelDetails(novel: SNovel): Observable<SNovel> = Observable.just(novel)

        override fun fetchChapterList(novel: SNovel): Observable<List<SNovelChapter>> =
            Observable.just(emptyList())

        override fun fetchChapterText(chapter: SNovelChapter): Observable<String> =
            Observable.just("")
    }
}
