package tachiyomi.data.source.manga

import androidx.paging.PagingSource
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import rx.Observable
import tachiyomi.domain.items.chapter.model.NoChaptersException

class MangaSourcePagingSourceTest {

    @Test
    fun `search paging source returns data and nextKey`() = runTest {
        val source = FakeMangaCatalogueSource(hasNext = true, mangas = listOf(makeManga("A")))
        val pagingSource = SourceSearchPagingSource(source, "q", FilterList())

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
    fun `search paging source returns error on empty refresh data`() = runTest {
        val source = FakeMangaCatalogueSource(hasNext = false, mangas = emptyList())
        val pagingSource = SourceSearchPagingSource(source, "q", FilterList())

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
    fun `paging source returns error when source request times out`() = runTest {
        val source = FakeMangaCatalogueSource(hasNext = true, mangas = listOf(makeManga("A")))
        val pagingSource = object : SourcePagingSource(source, requestTimeoutMillis = 1) {
            override suspend fun requestNextPage(currentPage: Int): MangasPage {
                delay(50)
                return MangasPage(listOf(makeManga("A")), hasNextPage = false)
            }
        }

        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 20,
                placeholdersEnabled = false,
            ),
        )

        val error = result as PagingSource.LoadResult.Error
        error.throwable::class shouldBe TimeoutCancellationException::class
    }

    @Test
    fun `search paging source treats empty append page as end of list`() = runTest {
        val source = FakePagedMangaCatalogueSource(
            responses = mapOf(
                1 to MangasPage(listOf(makeManga("A")), hasNextPage = true),
                2 to MangasPage(emptyList(), hasNextPage = false),
            ),
        )
        val pagingSource = SourceSearchPagingSource(source, "q", FilterList())

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

    private fun makeManga(title: String): SManga = SManga.create().apply {
        url = "/manga"
        this.title = title
    }

    private class FakeMangaCatalogueSource(
        private val hasNext: Boolean,
        private val mangas: List<SManga>,
    ) : CatalogueSource {
        override val id: Long = 1
        override val name: String = "Fake"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override fun fetchPopularManga(page: Int): Observable<MangasPage> =
            Observable.just(MangasPage(mangas, hasNext))

        override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
            Observable.just(MangasPage(mangas, hasNext))

        override fun fetchLatestUpdates(page: Int): Observable<MangasPage> =
            Observable.just(MangasPage(mangas, hasNext))

        override fun getFilterList(): FilterList = FilterList()
    }

    private class FakePagedMangaCatalogueSource(
        private val responses: Map<Int, MangasPage>,
    ) : CatalogueSource {
        override val id: Long = 1
        override val name: String = "Fake"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override fun fetchPopularManga(page: Int): Observable<MangasPage> =
            Observable.just(responses[page] ?: MangasPage(emptyList(), hasNextPage = false))

        override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
            Observable.just(responses[page] ?: MangasPage(emptyList(), hasNextPage = false))

        override fun fetchLatestUpdates(page: Int): Observable<MangasPage> =
            Observable.just(responses[page] ?: MangasPage(emptyList(), hasNextPage = false))

        override fun getFilterList(): FilterList = FilterList()
    }
}
