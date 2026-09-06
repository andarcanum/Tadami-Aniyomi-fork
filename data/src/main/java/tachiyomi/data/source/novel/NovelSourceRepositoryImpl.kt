package tachiyomi.data.source.novel

import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.source.novel.model.NovelSourceWithCount
import tachiyomi.domain.source.novel.model.StubNovelSource
import tachiyomi.domain.source.novel.repository.NovelSourceRepository
import tachiyomi.domain.source.novel.repository.SourcePagingSourceType
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.source.novel.model.Source as DomainSource

class NovelSourceRepositoryImpl(
    private val sourceManager: NovelSourceManager,
    private val handler: NovelDatabaseHandler,
) : NovelSourceRepository {

    override fun getNovelSources(): Flow<List<DomainSource>> {
        return sourceManager.catalogueSources.map { sources ->
            sources.map {
                mapSourceToDomainSource(it).copy(
                    supportsLatest = it.supportsLatest,
                )
            }
        }
    }

    override fun getOnlineNovelSources(): Flow<List<DomainSource>> {
        return sourceManager.catalogueSources.map { sources ->
            // catalogueSources is already List<NovelCatalogueSource>, so the old filterIsInstance was
            // a no-op that let the built-in sources into the Sources filter - disabling the local one
            // there writes its id into disabledNovelSources and hides every imported EPUB from
            // Browse. Filtering on the transport type is not an option: JS plugins are catalogue
            // sources without being NovelHttpSource, so only the two built-in ids are excluded.
            sources
                .filterNot { it.id == LOCAL_NOVEL_SOURCE_ID || it.id == OMNI_NOVEL_SOURCE_ID }
                .map(::mapSourceToDomainSource)
        }
    }

    override fun getNovelSourcesWithFavoriteCount(): Flow<List<Pair<DomainSource, Long>>> {
        return combine(
            handler.subscribeToList { db -> db.novelsQueries.getSourceIdWithFavoriteCount() },
            sourceManager.catalogueSources,
        ) { sourceIdWithFavoriteCount, _ -> sourceIdWithFavoriteCount }
            .map {
                it.map { (sourceId, count) ->
                    val source = sourceManager.getOrStub(sourceId)
                    val domainSource = mapSourceToDomainSource(source).copy(
                        isStub = source is StubNovelSource,
                    )
                    domainSource to count
                }
            }
    }

    override fun getNovelSourcesWithNonLibraryNovels(): Flow<List<NovelSourceWithCount>> {
        val sourceIdWithNonLibraryNovel =
            handler.subscribeToList { db -> db.novelsQueries.getSourceIdsWithNonLibraryNovel() }
        return sourceIdWithNonLibraryNovel.map { sourceId ->
            sourceId.map { (sourceId, count) ->
                val source = sourceManager.getOrStub(sourceId)
                val domainSource = mapSourceToDomainSource(source).copy(
                    isStub = source is StubNovelSource,
                )
                NovelSourceWithCount(domainSource, count)
            }
        }
    }

    override fun searchNovels(
        sourceId: Long,
        query: String,
        filterList: NovelFilterList,
    ): SourcePagingSourceType {
        // BRN-21/BRM-12 port: get() is nullable; the eager cast crashed the Pager factory
        // instead of surfacing an error state.
        val source = sourceManager.get(sourceId) as? NovelCatalogueSource
            ?: return NovelSourceUnavailablePagingSource()
        return NovelSourceSearchPagingSource(source, query, filterList)
    }

    override fun getPopularNovels(sourceId: Long, filterList: NovelFilterList): SourcePagingSourceType {
        // BRN-21/BRM-12 port: get() is nullable; the eager cast crashed the Pager factory
        // instead of surfacing an error state.
        val source = sourceManager.get(sourceId) as? NovelCatalogueSource
            ?: return NovelSourceUnavailablePagingSource()
        return NovelSourcePopularPagingSource(source, filterList)
    }

    override fun getLatestNovels(sourceId: Long, filterList: NovelFilterList): SourcePagingSourceType {
        // BRN-21/BRM-12 port: get() is nullable; the eager cast crashed the Pager factory
        // instead of surfacing an error state.
        val source = sourceManager.get(sourceId) as? NovelCatalogueSource
            ?: return NovelSourceUnavailablePagingSource()
        return NovelSourceLatestPagingSource(source, filterList)
    }

    private fun mapSourceToDomainSource(source: NovelSource): DomainSource = DomainSource(
        id = source.id,
        lang = source.lang,
        name = source.name,
        supportsLatest = false,
        isStub = false,
        isKotlinExtension = source.isKotlinExtension,
    )

    private companion object {
        /** [tachiyomi.source.local.entries.novel.LocalNovelSource.ID]; source-local is not on this module's path. */
        const val LOCAL_NOVEL_SOURCE_ID = 0L

        /** [eu.kanade.tachiyomi.source.novel.OmniSource.OMNI_SOURCE_ID]; lives in the app module. */
        const val OMNI_NOVEL_SOURCE_ID = -42L
    }
}

/** BRN-21/BRM-12 port: fails as LoadState.Error instead of crashing the Pager factory. */
private class NovelSourceUnavailablePagingSource : SourcePagingSourceType() {
    override fun getRefreshKey(
        state: androidx.paging.PagingState<Long, tachiyomi.domain.entries.novel.model.Novel>,
    ): Long? = null

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, tachiyomi.domain.entries.novel.model.Novel> =
        LoadResult.Error(IllegalStateException("Source is no longer installed"))
}
