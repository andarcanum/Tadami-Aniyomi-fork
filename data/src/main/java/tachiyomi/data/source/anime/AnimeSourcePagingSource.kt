package tachiyomi.data.source.anime

import androidx.paging.PagingState
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.withTimeout
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.NoEpisodesException
import tachiyomi.domain.source.anime.repository.AnimeSourcePagingSourceType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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

    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get()

    abstract suspend fun requestNextPage(currentPage: Int): AnimesPage

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, Anime> {
        val page = params.key ?: 1

        return try {
            withIOContext {
                val animesPage = withTimeout(requestTimeoutMillis) { requestNextPage(page.toInt()) }
                when {
                    animesPage.animes.isNotEmpty() -> {
                        val domainAnimes = animesPage.animes.map { it.toDomainAnime(source.id) }
                        val savedAnimes = networkToLocalAnime.await(domainAnimes)
                        LoadResult.Page(
                            data = savedAnimes,
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

    override fun getRefreshKey(state: PagingState<Long, Anime>): Long? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey ?: anchorPage?.nextKey
        }
    }
}

private fun SAnime.toDomainAnime(sourceId: Long): Anime {
    return Anime.create().copy(
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = getGenres(),
        status = status.toLong(),
        thumbnailUrl = thumbnail_url,
        backgroundUrl = background_url,
        updateStrategy = update_strategy,
        fetchType = fetch_type,
        seasonNumber = season_number,
        initialized = initialized,
        source = sourceId,
    )
}

internal const val ANIME_SOURCE_PAGE_REQUEST_TIMEOUT_MS = 30_000L
