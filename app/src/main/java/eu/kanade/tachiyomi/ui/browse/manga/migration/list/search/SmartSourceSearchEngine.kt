package eu.kanade.tachiyomi.ui.browse.manga.migration.list.search

import eu.kanade.domain.entries.manga.model.toDomainManga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.entries.manga.model.Manga

class SmartSourceSearchEngine(
    extraSearchParams: String? = null,
) : BaseSmartSearchEngine<SManga>(extraSearchParams) {

    override fun getTitle(result: SManga) = result.title

    suspend fun regularSearch(source: CatalogueSource, title: String): Manga? {
        return regularSearch(makeSearchAction(source), title)?.toDomainManga(source.id)
    }

    suspend fun deepSearch(source: CatalogueSource, title: String): Manga? {
        return deepSearch(makeSearchAction(source), title)?.toDomainManga(source.id)
    }

    private fun makeSearchAction(source: CatalogueSource): SearchAction<SManga> = { query ->
        source.getSearchManga(1, query, source.getFilterList()).mangas
    }
}
