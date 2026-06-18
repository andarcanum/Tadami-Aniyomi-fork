package tachiyomi.data.source.novel

import androidx.paging.PagingState
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import kotlinx.coroutines.withTimeout
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.items.chapter.model.NoChaptersException
import tachiyomi.domain.source.novel.repository.SourcePagingSourceType

class NovelSourceSearchPagingSource(
    source: NovelCatalogueSource,
    val query: String,
    val filters: NovelFilterList,
    requestTimeoutMillis: Long = NOVEL_SOURCE_PAGE_REQUEST_TIMEOUT_MS,
) :
    NovelSourcePagingSource(
        source,
        requestTimeoutMillis,
    ) {
    override suspend fun requestNextPage(currentPage: Int): NovelsPage {
        return source.getSearchNovels(currentPage, query, filters)
    }
}

class NovelSourcePopularPagingSource(
    source: NovelCatalogueSource,
    private val filters: NovelFilterList,
    requestTimeoutMillis: Long = NOVEL_SOURCE_PAGE_REQUEST_TIMEOUT_MS,
) : NovelSourcePagingSource(source, requestTimeoutMillis) {
    override suspend fun requestNextPage(currentPage: Int): NovelsPage {
        return source.getPopularNovels(currentPage, filters)
    }
}

class NovelSourceLatestPagingSource(
    source: NovelCatalogueSource,
    private val filters: NovelFilterList,
    requestTimeoutMillis: Long = NOVEL_SOURCE_PAGE_REQUEST_TIMEOUT_MS,
) : NovelSourcePagingSource(source, requestTimeoutMillis) {
    override suspend fun requestNextPage(currentPage: Int): NovelsPage {
        return source.getLatestUpdates(currentPage, filters)
    }
}

abstract class NovelSourcePagingSource(
    protected val source: NovelCatalogueSource,
    private val requestTimeoutMillis: Long = NOVEL_SOURCE_PAGE_REQUEST_TIMEOUT_MS,
) : SourcePagingSourceType() {

    abstract suspend fun requestNextPage(currentPage: Int): NovelsPage

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, SNovel> {
        val page = params.key ?: 1

        return try {
            withIOContext {
                val novelsPage = withTimeout(requestTimeoutMillis) { requestNextPage(page.toInt()) }
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
                        // Some sources incorrectly report that another page exists,
                        // then return an empty trailing page. Treat that as the end
                        // of pagination instead of surfacing a false "no results" error.
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

internal const val NOVEL_SOURCE_PAGE_REQUEST_TIMEOUT_MS = 30_000L
