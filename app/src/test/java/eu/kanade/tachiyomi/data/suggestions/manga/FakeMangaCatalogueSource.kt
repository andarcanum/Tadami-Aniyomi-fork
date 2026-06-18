package eu.kanade.tachiyomi.data.suggestions.manga

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import rx.Observable

open class FakeMangaCatalogueSource(
    override val id: Long = 1L,
    override val name: String = "Fake Source",
    override val lang: String = "en",
    override val supportsLatest: Boolean = false,
) : CatalogueSource {

    var searchMangasToReturn: List<SManga> = emptyList()
    var detailsByUrl: Map<String, SManga> = emptyMap()
    var getSearchMangaCalledWithQuery: String? = null
    var getMangaDetailsCallCount = 0

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        getSearchMangaCalledWithQuery = query
        return MangasPage(searchMangasToReturn, false)
    }

    override fun getFilterList(): FilterList = FilterList()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = throw UnsupportedOperationException()
    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> = throw UnsupportedOperationException()
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> = throw UnsupportedOperationException()
    override suspend fun getMangaDetails(manga: SManga): SManga {
        getMangaDetailsCallCount++
        return detailsByUrl[manga.url] ?: manga
    }
}
