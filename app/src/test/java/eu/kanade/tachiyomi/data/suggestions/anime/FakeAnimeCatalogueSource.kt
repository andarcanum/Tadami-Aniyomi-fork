package eu.kanade.tachiyomi.data.suggestions.anime

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import rx.Observable

open class FakeAnimeCatalogueSource(
    override val id: Long = 1L,
    override val name: String = "Fake Source",
    override val lang: String = "en",
    override val supportsLatest: Boolean = false,
) : AnimeCatalogueSource {

    var searchAnimesToReturn: List<SAnime> = emptyList()
    var detailsByUrl: Map<String, SAnime> = emptyMap()
    var getSearchAnimeCalledWithQuery: String? = null
    var getAnimeDetailsCallCount = 0

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        getSearchAnimeCalledWithQuery = query
        return AnimesPage(searchAnimesToReturn, false)
    }

    override fun getFilterList(): AnimeFilterList = AnimeFilterList()

    override fun fetchPopularAnime(page: Int): Observable<AnimesPage> = throw UnsupportedOperationException()
    override fun fetchSearchAnime(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): Observable<AnimesPage> = throw UnsupportedOperationException()
    override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> = throw UnsupportedOperationException()
    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        getAnimeDetailsCallCount++
        return detailsByUrl[anime.url] ?: anime
    }
    override suspend fun getSeasonList(anime: SAnime): List<SAnime> = emptyList()
}
