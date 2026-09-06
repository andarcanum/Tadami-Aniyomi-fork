package tachiyomi.data.source.manga

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.source.manga.model.MangaSourceWithCount
import tachiyomi.domain.source.manga.model.StubMangaSource
import tachiyomi.domain.source.manga.repository.MangaSourceRepository
import tachiyomi.domain.source.manga.repository.SourcePagingSourceType
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.manga.model.Source as DomainSource

class MangaSourceRepositoryImpl(
    private val sourceManager: MangaSourceManager,
    private val handler: MangaDatabaseHandler,
) : MangaSourceRepository {

    override fun getMangaSources(): Flow<List<DomainSource>> {
        return sourceManager.catalogueSources.map { sources ->
            sources.map {
                mapSourceToDomainSource(it).copy(
                    supportsLatest = it.supportsLatest,
                )
            }
        }
    }

    override fun getOnlineMangaSources(): Flow<List<DomainSource>> {
        return sourceManager.catalogueSources.map { sources ->
            sources
                .filterIsInstance<HttpSource>()
                .map(::mapSourceToDomainSource)
        }
    }

    override fun getMangaSourcesWithFavoriteCount(): Flow<List<Pair<DomainSource, Long>>> {
        return combine(
            handler.subscribeToList { db -> db.mangasQueries.getSourceIdWithFavoriteCount() },
            sourceManager.catalogueSources,
        ) { sourceIdWithFavoriteCount, _ -> sourceIdWithFavoriteCount }
            .map {
                it.map { (sourceId, count) ->
                    val source = sourceManager.getOrStub(sourceId)
                    val domainSource = mapSourceToDomainSource(source).copy(
                        isStub = source is StubMangaSource,
                    )
                    domainSource to count
                }
            }
    }

    override fun getMangaSourcesWithNonLibraryManga(): Flow<List<MangaSourceWithCount>> {
        val sourceIdWithNonLibraryManga =
            handler.subscribeToList { db -> db.mangasQueries.getSourceIdsWithNonLibraryManga() }
        return sourceIdWithNonLibraryManga.map { sourceId ->
            sourceId.map { (sourceId, count) ->
                val source = sourceManager.getOrStub(sourceId)
                val domainSource = mapSourceToDomainSource(source).copy(
                    isStub = source is StubMangaSource,
                )
                MangaSourceWithCount(domainSource, count)
            }
        }
    }

    override fun searchManga(
        sourceId: Long,
        query: String,
        filterList: FilterList,
    ): SourcePagingSourceType {
        // BRM-12: sourceManager.get() is NULLABLE - the extension can be uninstalled/replaced
        // while the browse screen is open. The eager `as CatalogueSource` cast threw inside the
        // Pager factory; Paging does NOT catch factory exceptions (only load()), so it crashed
        // the collector instead of surfacing an error state.
        val source = sourceManager.get(sourceId) as? CatalogueSource
            ?: return SourceUnavailablePagingSource()
        return SourceSearchPagingSource(source, query, filterList)
    }

    override fun getPopularManga(sourceId: Long): SourcePagingSourceType {
        // BRM-12: sourceManager.get() is NULLABLE - the extension can be uninstalled/replaced
        // while the browse screen is open. The eager `as CatalogueSource` cast threw inside the
        // Pager factory; Paging does NOT catch factory exceptions (only load()), so it crashed
        // the collector instead of surfacing an error state.
        val source = sourceManager.get(sourceId) as? CatalogueSource
            ?: return SourceUnavailablePagingSource()
        return SourcePopularPagingSource(source)
    }

    override fun getLatestManga(sourceId: Long): SourcePagingSourceType {
        // BRM-12: sourceManager.get() is NULLABLE - the extension can be uninstalled/replaced
        // while the browse screen is open. The eager `as CatalogueSource` cast threw inside the
        // Pager factory; Paging does NOT catch factory exceptions (only load()), so it crashed
        // the collector instead of surfacing an error state.
        val source = sourceManager.get(sourceId) as? CatalogueSource
            ?: return SourceUnavailablePagingSource()
        return SourceLatestPagingSource(source)
    }

    private fun mapSourceToDomainSource(source: MangaSource): DomainSource = DomainSource(
        id = source.id,
        lang = source.lang,
        name = source.name,
        supportsLatest = false,
        isStub = false,
    )
}

/** BRM-12: fails as LoadState.Error (graceful browse error state) instead of a factory crash. */
private class SourceUnavailablePagingSource : SourcePagingSourceType() {
    override fun getRefreshKey(
        state: androidx.paging.PagingState<Long, tachiyomi.domain.entries.manga.model.Manga>,
    ): Long? = null

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, tachiyomi.domain.entries.manga.model.Manga> =
        LoadResult.Error(IllegalStateException("Source is no longer installed"))
}
