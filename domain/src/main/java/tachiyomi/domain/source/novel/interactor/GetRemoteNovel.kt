package tachiyomi.domain.source.novel.interactor

import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import tachiyomi.domain.source.novel.repository.NovelSourceRepository
import tachiyomi.domain.source.novel.repository.SourcePagingSourceType

class GetRemoteNovel(
    private val repository: NovelSourceRepository,
) {

    fun subscribe(sourceId: Long, query: String, filterList: NovelFilterList): SourcePagingSourceType {
        return when (query) {
            QUERY_POPULAR -> repository.getPopularNovels(sourceId, filterList)
            QUERY_LATEST -> repository.getLatestNovels(sourceId, filterList)
            else -> repository.searchNovels(sourceId, query, filterList)
        }
    }

    companion object {
        const val QUERY_POPULAR = "tachiyomi.domain.source.novel.interactor.POPULAR"
        const val QUERY_LATEST = "tachiyomi.domain.source.novel.interactor.LATEST"
    }
}
