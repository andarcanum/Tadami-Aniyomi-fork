package tachiyomi.domain.source.novel.interactor

import androidx.paging.PagingSource
import androidx.paging.PagingState
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.SNovel
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.source.novel.repository.NovelSourceRepository

class GetRemoteNovelTest {

    @Test
    fun `uses popular when query matches popular constant`() {
        val repo = FakeNovelSourceRepository()
        val interactor = GetRemoteNovel(repo)

        interactor.subscribe(1L, GetRemoteNovel.QUERY_POPULAR, NovelFilterList())

        repo.popularCalls shouldBe 1
        repo.latestCalls shouldBe 0
        repo.searchCalls shouldBe 0
    }

    @Test
    fun `uses latest when query matches latest constant`() {
        val repo = FakeNovelSourceRepository()
        val interactor = GetRemoteNovel(repo)

        interactor.subscribe(1L, GetRemoteNovel.QUERY_LATEST, NovelFilterList())

        repo.latestCalls shouldBe 1
        repo.popularCalls shouldBe 0
        repo.searchCalls shouldBe 0
    }

    @Test
    fun `uses search otherwise`() {
        val repo = FakeNovelSourceRepository()
        val interactor = GetRemoteNovel(repo)

        interactor.subscribe(1L, "query", NovelFilterList())

        repo.searchCalls shouldBe 1
        repo.latestCalls shouldBe 0
        repo.popularCalls shouldBe 0
    }

    private class FakeNovelSourceRepository : NovelSourceRepository {
        var popularCalls = 0
        var latestCalls = 0
        var searchCalls = 0

        override fun getNovelSources() = throw UnsupportedOperationException()
        override fun getOnlineNovelSources() = throw UnsupportedOperationException()
        override fun getNovelSourcesWithFavoriteCount() = throw UnsupportedOperationException()
        override fun getNovelSourcesWithNonLibraryNovels() = throw UnsupportedOperationException()

        override fun searchNovels(sourceId: Long, query: String, filterList: NovelFilterList) =
            FakePagingSource().also { searchCalls++ }

        override fun getPopularNovels(sourceId: Long) = FakePagingSource().also { popularCalls++ }

        override fun getLatestNovels(sourceId: Long) = FakePagingSource().also { latestCalls++ }
    }

    private class FakePagingSource : PagingSource<Long, SNovel>() {
        override fun getRefreshKey(state: PagingState<Long, SNovel>): Long? = null

        override suspend fun load(params: LoadParams<Long>): LoadResult<Long, SNovel> {
            return LoadResult.Page(emptyList(), null, null)
        }
    }
}
