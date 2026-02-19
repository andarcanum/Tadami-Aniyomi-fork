package tachiyomi.domain.source.novel.repository

import androidx.paging.PagingSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.SNovel
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.novel.model.NovelSourceWithCount
import tachiyomi.domain.source.novel.model.Source

typealias SourcePagingSourceType = PagingSource<Long, SNovel>

interface NovelSourceRepository {

    fun getNovelSources(): Flow<List<Source>>

    fun getOnlineNovelSources(): Flow<List<Source>>

    fun getNovelSourcesWithFavoriteCount(): Flow<List<Pair<Source, Long>>>

    fun getNovelSourcesWithNonLibraryNovels(): Flow<List<NovelSourceWithCount>>

    fun searchNovels(sourceId: Long, query: String, filterList: NovelFilterList): SourcePagingSourceType

    fun getPopularNovels(sourceId: Long, filterList: NovelFilterList): SourcePagingSourceType

    fun getLatestNovels(sourceId: Long, filterList: NovelFilterList): SourcePagingSourceType
}
