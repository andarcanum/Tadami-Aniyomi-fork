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
            sources
                .filterIsInstance<NovelCatalogueSource>()
                .map(::mapSourceToDomainSource)
        }
    }

    override fun getNovelSourcesWithFavoriteCount(): Flow<List<Pair<DomainSource, Long>>> {
        return combine(
            handler.subscribeToList { novelsQueries.getSourceIdWithFavoriteCount() },
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
            handler.subscribeToList { novelsQueries.getSourceIdsWithNonLibraryNovel() }
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
        val source = sourceManager.get(sourceId) as NovelCatalogueSource
        return NovelSourceSearchPagingSource(source, query, filterList)
    }

    override fun getPopularNovels(sourceId: Long, filterList: NovelFilterList): SourcePagingSourceType {
        val source = sourceManager.get(sourceId) as NovelCatalogueSource
        return NovelSourcePopularPagingSource(source, filterList)
    }

    override fun getLatestNovels(sourceId: Long, filterList: NovelFilterList): SourcePagingSourceType {
        val source = sourceManager.get(sourceId) as NovelCatalogueSource
        return NovelSourceLatestPagingSource(source, filterList)
    }

    private fun mapSourceToDomainSource(source: NovelSource): DomainSource = DomainSource(
        id = source.id,
        lang = source.lang,
        name = source.name,
        supportsLatest = false,
        isStub = false,
    )
}
