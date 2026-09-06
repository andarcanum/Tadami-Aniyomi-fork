package tachiyomi.data.source.anime

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.repository.AnimeSourcePagingSourceType
import tachiyomi.domain.source.anime.repository.AnimeSourceRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.anime.model.AnimeSource as DomainSource

class AnimeSourceRepositoryImpl(
    private val sourceManager: AnimeSourceManager,
    private val handler: AnimeDatabaseHandler,
) : AnimeSourceRepository {

    override fun getAnimeSources(): Flow<List<DomainSource>> {
        return sourceManager.sources.map { sources ->
            sources.map { mapSourceToDomainSource(it) }
        }
    }

    override fun getOnlineAnimeSources(): Flow<List<DomainSource>> {
        return sourceManager.catalogueSources.map { sources ->
            sources
                .filterIsInstance<AnimeHttpSource>()
                .map(::mapSourceToDomainSource)
        }
    }

    override fun getAnimeSourcesWithFavoriteCount(): Flow<List<Pair<DomainSource, Long>>> {
        return combine(
            handler.subscribeToList { db -> db.animesQueries.getAnimeSourceIdWithFavoriteCount() },
            sourceManager.catalogueSources,
        ) { sourceIdWithFavoriteCount, _ -> sourceIdWithFavoriteCount }
            .map {
                it.map { (sourceId, count) ->
                    val source = sourceManager.getOrStub(sourceId)
                    val domainSource = mapSourceToDomainSource(source).copy(
                        isStub = source is StubAnimeSource,
                    )
                    domainSource to count
                }
            }
    }

    override fun searchAnime(
        sourceId: Long,
        query: String,
        filterList: AnimeFilterList,
    ): AnimeSourcePagingSourceType {
        // BRM-12 port: get() is nullable (an anime replace literally passes through an
        // UNINSTALLED window); the eager cast crashed the Pager factory instead of surfacing
        // an error state.
        val source = sourceManager.get(sourceId) as? AnimeCatalogueSource
            ?: return AnimeSourceUnavailablePagingSource()
        return AnimeSourceSearchPagingSource(source, query, filterList)
    }

    override fun getPopularAnime(sourceId: Long): AnimeSourcePagingSourceType {
        // BRM-12 port: get() is nullable (an anime replace literally passes through an
        // UNINSTALLED window); the eager cast crashed the Pager factory instead of surfacing
        // an error state.
        val source = sourceManager.get(sourceId) as? AnimeCatalogueSource
            ?: return AnimeSourceUnavailablePagingSource()
        return AnimeSourcePopularPagingSource(source)
    }

    override fun getLatestAnime(sourceId: Long): AnimeSourcePagingSourceType {
        // BRM-12 port: get() is nullable (an anime replace literally passes through an
        // UNINSTALLED window); the eager cast crashed the Pager factory instead of surfacing
        // an error state.
        val source = sourceManager.get(sourceId) as? AnimeCatalogueSource
            ?: return AnimeSourceUnavailablePagingSource()
        return AnimeSourceLatestPagingSource(source)
    }
}

fun mapSourceToDomainSource(source: AnimeSource): DomainSource = DomainSource(
    id = source.id,
    lang = source.lang,
    name = source.name,
    supportsLatest = (source as? AnimeCatalogueSource)?.supportsLatest ?: false,
    isStub = false,
    isFeedSource = source is AnimeFeedSource,
)

/** BRM-12 port: fails as LoadState.Error instead of crashing the Pager factory. */
private class AnimeSourceUnavailablePagingSource : AnimeSourcePagingSourceType() {
    override fun getRefreshKey(
        state: androidx.paging.PagingState<Long, tachiyomi.domain.entries.anime.model.Anime>,
    ): Long? = null

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, tachiyomi.domain.entries.anime.model.Anime> =
        LoadResult.Error(IllegalStateException("Source is no longer installed"))
}
