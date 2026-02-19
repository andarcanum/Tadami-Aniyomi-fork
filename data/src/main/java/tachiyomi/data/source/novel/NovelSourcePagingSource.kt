package tachiyomi.data.source.novel

import androidx.paging.PagingState
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.items.chapter.model.NoChaptersException
import tachiyomi.domain.source.novel.repository.SourcePagingSourceType

class NovelSourceSearchPagingSource(
    source: NovelCatalogueSource,
    val query: String,
    val filters: NovelFilterList,
) :
    NovelSourcePagingSource(
        source,
    ) {
    override suspend fun requestNextPage(currentPage: Int): NovelsPage {
        return source.getSearchNovels(currentPage, query, filters)
    }
}

class NovelSourcePopularPagingSource(
    source: NovelCatalogueSource,
    private val filters: NovelFilterList,
) : NovelSourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): NovelsPage {
        return source.getPopularNovels(currentPage, filters)
    }
}

class NovelSourceLatestPagingSource(
    source: NovelCatalogueSource,
    private val filters: NovelFilterList,
) : NovelSourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): NovelsPage {
        return source.getLatestUpdates(currentPage, filters)
    }
}

abstract class NovelSourcePagingSource(
    protected val source: NovelCatalogueSource,
) : SourcePagingSourceType() {

    abstract suspend fun requestNextPage(currentPage: Int): NovelsPage

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, SNovel> {
        val page = params.key ?: 1

        return try {
            withIOContext {
                val novelsPage = requestNextPage(page.toInt())
                when {
                    novelsPage.novels.isNotEmpty() -> {
                        LoadResult.Page(
                            data = novelsPage.novels,
                            prevKey = null,
                            nextKey = if (novelsPage.hasNextPage) page + 1 else null,
                        )
                    }
                    page == 1L -> throw NoChaptersException()
                    else -> {
                        // Some sources may return an empty trailing page while data already exists.
                        // Treat this as end-of-pagination instead of an append error.
                        LoadResult.Page(
                            data = emptyList(),
                            prevKey = null,
                            nextKey = null,
                        )
                    }
                }
            }
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Long, SNovel>): Long? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey ?: anchorPage?.nextKey
        }
    }
}
