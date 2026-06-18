package eu.kanade.tachiyomi.data.suggestions.novel

import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import rx.Observable

open class FakeNovelCatalogueSource(
    override val id: Long = 1L,
    override val name: String = "Fake Source",
    override val lang: String = "en",
    override val supportsLatest: Boolean = false,
    override val supportsRelatedNovels: Boolean = false,
) : NovelCatalogueSource {

    var relatedNovelsToReturn: List<SNovel> = emptyList()
    var searchNovelsToReturn: List<SNovel> = emptyList()
    var searchNovelsByQuery: Map<String, List<SNovel>> = emptyMap()
    var popularNovelsToReturn: List<SNovel> = emptyList()
    var popularNovelsWithFiltersToReturn: List<SNovel>? = null
    var detailsByUrl: Map<String, SNovel> = emptyMap()
    var getRelatedNovelsCalled = false
    var getSearchNovelsCalledWithQuery: String? = null
    var searchQueriesCalled: MutableList<String> = mutableListOf()
    var getPopularNovelsCalled = false
    var getPopularNovelsCallCount = 0
    var getNovelDetailsCallCount = 0

    override suspend fun getRelatedNovels(novel: SNovel): List<SNovel> {
        getRelatedNovelsCalled = true
        return relatedNovelsToReturn
    }

    override suspend fun getSearchNovels(page: Int, query: String, filters: NovelFilterList): NovelsPage {
        getSearchNovelsCalledWithQuery = query
        searchQueriesCalled.add(query)
        val byQuery = searchNovelsByQuery[query]
        return NovelsPage(byQuery ?: searchNovelsToReturn, false)
    }

    override suspend fun getPopularNovels(page: Int): NovelsPage {
        getPopularNovelsCalled = true
        getPopularNovelsCallCount++
        return NovelsPage(popularNovelsToReturn, false)
    }

    override suspend fun getPopularNovels(page: Int, filters: NovelFilterList): NovelsPage {
        val filtered = popularNovelsWithFiltersToReturn
        if (filtered != null) {
            return NovelsPage(filtered, false)
        }
        return getPopularNovels(page)
    }

    override fun getFilterList(): NovelFilterList = NovelFilterList()

    override suspend fun getNovelDetails(novel: SNovel): SNovel {
        getNovelDetailsCallCount++
        return detailsByUrl[novel.url] ?: novel
    }

    override fun fetchPopularNovels(page: Int): Observable<NovelsPage> = throw UnsupportedOperationException()
    override fun fetchSearchNovels(
        page: Int,
        query: String,
        filters: NovelFilterList,
    ): Observable<NovelsPage> = throw UnsupportedOperationException()
    override fun fetchLatestUpdates(page: Int): Observable<NovelsPage> = throw UnsupportedOperationException()
}
