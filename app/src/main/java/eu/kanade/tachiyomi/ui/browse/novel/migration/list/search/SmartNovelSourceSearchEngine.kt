package eu.kanade.tachiyomi.ui.browse.novel.migration.list.search

import eu.kanade.domain.entries.novel.model.toDomainNovel
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.ui.browse.manga.migration.list.search.BaseSmartSearchEngine
import eu.kanade.tachiyomi.ui.browse.manga.migration.list.search.SearchAction
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.model.Novel
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SmartNovelSourceSearchEngine(
    extraSearchParams: String? = null,
    private val networkToLocalNovel: NetworkToLocalNovel = Injekt.get(),
) : BaseSmartSearchEngine<SNovel>(extraSearchParams) {

    override fun getTitle(result: SNovel) = result.title

    suspend fun regularSearch(source: NovelCatalogueSource, title: String): Novel? {
        return regularSearch(makeSearchAction(source), title)
            ?.toDomainNovel(source.id)
            ?.let { networkToLocalNovel.await(it) }
    }

    suspend fun deepSearch(source: NovelCatalogueSource, title: String): Novel? {
        return deepSearch(makeSearchAction(source), title)
            ?.toDomainNovel(source.id)
            ?.let { networkToLocalNovel.await(it) }
    }

    private fun makeSearchAction(source: NovelCatalogueSource): SearchAction<SNovel> = { query ->
        source.getSearchNovels(1, query, source.getFilterList()).novels
    }
}
