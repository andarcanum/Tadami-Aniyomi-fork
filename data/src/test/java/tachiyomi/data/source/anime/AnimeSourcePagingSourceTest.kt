package tachiyomi.data.source.anime

import androidx.paging.PagingSource
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import rx.Observable
import tachiyomi.domain.items.episode.model.NoEpisodesException

class AnimeSourcePagingSourceTest {

    @Test
    fun `search paging source returns data and nextKey`() = runTest {
        val source = FakeAnimeCatalogueSource(hasNext = true, animes = listOf(makeAnime("A")))
        val pagingSource = AnimeSourceSearchPagingSource(source, "q", AnimeFilterList())

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
        val source = FakeAnimeCatalogueSource(hasNext = false, animes = emptyList())
        val pagingSource = AnimeSourceSearchPagingSource(source, "q", AnimeFilterList())

        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 20,
                placeholdersEnabled = false,
            ),
        )

        val error = result as PagingSource.LoadResult.Error
        error.throwable::class shouldBe NoEpisodesException::class
    }

    @Test
    fun `paging source returns error when source request times out`() = runTest {
        val source = FakeAnimeCatalogueSource(hasNext = true, animes = listOf(makeAnime("A")))
        val pagingSource = object : AnimeSourcePagingSource(source, requestTimeoutMillis = 1) {
            override suspend fun requestNextPage(currentPage: Int): AnimesPage {
                delay(50)
                return AnimesPage(listOf(makeAnime("A")), hasNextPage = false)
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
        val source = FakePagedAnimeCatalogueSource(
            responses = mapOf(
                1 to AnimesPage(listOf(makeAnime("A")), hasNextPage = true),
                2 to AnimesPage(emptyList(), hasNextPage = false),
            ),
        )
        val pagingSource = AnimeSourceSearchPagingSource(source, "q", AnimeFilterList())

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

    private fun makeAnime(title: String): SAnime = SAnime.create().apply {
        url = "/anime"
        this.title = title
    }

    private class FakeAnimeCatalogueSource(
        private val hasNext: Boolean,
        private val animes: List<SAnime>,
    ) : AnimeCatalogueSource {
        override val id: Long = 1
        override val name: String = "Fake"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override fun fetchPopularAnime(page: Int): Observable<AnimesPage> =
            Observable.just(AnimesPage(animes, hasNext))

        override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> =
            Observable.just(AnimesPage(animes, hasNext))

        override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> =
            Observable.just(AnimesPage(animes, hasNext))

        override fun getFilterList(): AnimeFilterList = AnimeFilterList()

        override suspend fun getSeasonList(anime: SAnime): List<SAnime> = emptyList()
    }

    private class FakePagedAnimeCatalogueSource(
        private val responses: Map<Int, AnimesPage>,
    ) : AnimeCatalogueSource {
        override val id: Long = 1
        override val name: String = "Fake"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override fun fetchPopularAnime(page: Int): Observable<AnimesPage> =
            Observable.just(responses[page] ?: AnimesPage(emptyList(), hasNextPage = false))

        override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> =
            Observable.just(responses[page] ?: AnimesPage(emptyList(), hasNextPage = false))

        override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> =
            Observable.just(responses[page] ?: AnimesPage(emptyList(), hasNextPage = false))

        override fun getFilterList(): AnimeFilterList = AnimeFilterList()

        override suspend fun getSeasonList(anime: SAnime): List<SAnime> = emptyList()
    }
}
