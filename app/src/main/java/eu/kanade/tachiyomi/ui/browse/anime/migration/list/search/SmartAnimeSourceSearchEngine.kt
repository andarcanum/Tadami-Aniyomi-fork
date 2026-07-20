package eu.kanade.tachiyomi.ui.browse.anime.migration.list.search

import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.ui.browse.manga.migration.list.search.BaseSmartSearchEngine
import eu.kanade.tachiyomi.ui.browse.manga.migration.list.search.SearchAction
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SmartAnimeSourceSearchEngine(
    extraSearchParams: String? = null,
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
) : BaseSmartSearchEngine<SAnime>(extraSearchParams) {

    override fun getTitle(result: SAnime) = result.title

    suspend fun regularSearch(source: AnimeCatalogueSource, title: String): Anime? {
        return regularSearch(makeSearchAction(source), title)
            ?.toDomainAnime(source.id)
            ?.let { networkToLocalAnime.await(it) }
    }

    suspend fun deepSearch(source: AnimeCatalogueSource, title: String): Anime? {
        return deepSearch(makeSearchAction(source), title)
            ?.toDomainAnime(source.id)
            ?.let { networkToLocalAnime.await(it) }
    }

    private fun makeSearchAction(source: AnimeCatalogueSource): SearchAction<SAnime> = { query ->
        source.getSearchAnime(1, query, source.getFilterList()).animes
    }
}
