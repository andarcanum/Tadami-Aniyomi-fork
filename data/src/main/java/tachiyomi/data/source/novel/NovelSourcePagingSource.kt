package tachiyomi.data.source.novel

import androidx.paging.PagingState
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import kotlinx.coroutines.withTimeout
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.chapter.model.NoChaptersException
import tachiyomi.domain.source.novel.repository.SourcePagingSourceType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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

    private val networkToLocalNovel: NetworkToLocalNovel = Injekt.get()

    abstract suspend fun requestNextPage(currentPage: Int): NovelsPage

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, Novel> {
        val page = params.key ?: 1

        return try {
            withIOContext {
                val novelsPage = withTimeout(requestTimeoutMillis) { requestNextPage(page.toInt()) }
                when {
                    novelsPage.novels.isNotEmpty() -> {
                        val domainNovels = novelsPage.novels.map { it.toDomainNovel(source.id) }
                        val savedNovels = networkToLocalNovel.await(domainNovels)
                        LoadResult.Page(
                            data = savedNovels,
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

    override fun getRefreshKey(state: PagingState<Long, Novel>): Long? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey ?: anchorPage?.nextKey
        }
    }
}

private fun SNovel.toDomainNovel(sourceId: Long): Novel {
    return Novel.create().copy(
        url = url,
        title = title,
        author = author,
        description = description?.trim(),
        genre = genre?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
        status = status.toLong(),
        thumbnailUrl = thumbnail_url,
        updateStrategy = update_strategy,
        initialized = initialized,
        source = sourceId,
    )
}

internal const val NOVEL_SOURCE_PAGE_REQUEST_TIMEOUT_MS = 30_000L
