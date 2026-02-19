package eu.kanade.tachiyomi.ui.browse.novel.migration.search

import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.NovelSearchScreenModel
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.NovelSourceFilter
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.novel.interactor.GetNovel
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateNovelSearchScreenModel(
    val novelId: Long,
    getNovel: GetNovel = Injekt.get(),
) : NovelSearchScreenModel() {

    private var fromSourceId: Long? = null

    init {
        screenModelScope.launch {
            val novel = getNovel.await(novelId)!!
            fromSourceId = novel.source
            updateSearchQuery(novel.title)

            search()
        }
    }

    override fun getEnabledSources(): List<NovelCatalogueSource> {
        return super.getEnabledSources()
            .filter { state.value.sourceFilter != NovelSourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
            .sortedWith(
                compareBy(
                    { it.id != fromSourceId },
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }
}
