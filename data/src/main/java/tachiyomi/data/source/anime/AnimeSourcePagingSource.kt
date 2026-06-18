package tachiyomi.data.source.anime

import androidx.paging.PagingState
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.withTimeout
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.items.episode.model.NoEpisodesException
import tachiyomi.domain.source.anime.repository.AnimeSourcePagingSourceType

class AnimeSourceSearchPagingSource(
    source: AnimeCatalogueSource,
    val query: String,
    val filters: AnimeFilterList,
    requestTimeoutMillis: Long = ANIME_SOURCE_PAGE_REQUEST_TIMEOUT_MS,
) : AnimeSourcePagingSource(source, requestTimeoutMillis) {
    override suspend fun requestNextPage(currentPage: Int): AnimesPage {
        return source.getSearchAnime(currentPage, query, filters)
    }
}

class AnimeSourcePopularPagingSource(
    source: AnimeCatalogueSource,
    requestTimeoutMillis: Long = ANIME_SOURCE_PAGE_REQUEST_TIMEOUT_MS,
) : AnimeSourcePagingSource(source, requestTimeoutMillis) {
    override suspend fun requestNextPage(currentPage: Int): AnimesPage {
        return source.getPopularAnime(currentPage)
    }
}

class AnimeSourceLatestPagingSource(
    source: AnimeCatalogueSource,
    requestTimeoutMillis: Long = ANIME_SOURCE_PAGE_REQUEST_TIMEOUT_MS,
) : AnimeSourcePagingSource(source, requestTimeoutMillis) {
    override suspend fun requestNextPage(currentPage: Int): AnimesPage {
        return source.getLatestUpdates(currentPage)
    }
}

abstract class AnimeSourcePagingSource(
    protected val source: AnimeCatalogueSource,
    private val requestTimeoutMillis: Long = ANIME_SOURCE_PAGE_REQUEST_TIMEOUT_MS,
) : AnimeSourcePagingSourceType() {

    abstract suspend fun requestNextPage(currentPage: Int): AnimesPage

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, SAnime> {
        val page = params.key ?: 1

        return try {
            withIOContext {
                val animesPage = withTimeout(requestTimeoutMillis) { requestNextPage(page.toInt()) }
                when {
                    animesPage.animes.isNotEmpty() -> {
                        LoadResult.Page(
                            data = animesPage.animes,
                            prevKey = null,
                            nextKey = if (animesPage.hasNextPage) page + 1 else null,
                        )
                    }
                    page == 1L -> throw NoEpisodesException()
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

    override fun getRefreshKey(state: PagingState<Long, SAnime>): Long? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey ?: anchorPage?.nextKey
        }
    }
}

internal const val ANIME_SOURCE_PAGE_REQUEST_TIMEOUT_MS = 30_000L
