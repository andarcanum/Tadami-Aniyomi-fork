package tachiyomi.domain.entries.novel.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.library.novel.LibraryNovel

interface NovelRepository {

    suspend fun getNovelById(id: Long): Novel

    fun getNovelByIdAsFlow(id: Long): Flow<Novel>

    suspend fun getNovelByUrlAndSourceId(url: String, sourceId: Long): Novel?

    fun getNovelByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Novel?>

    suspend fun getNovelFavorites(): List<Novel>

    /**
     * BRN-11: targeted duplicate lookup (manga/anime etalon) instead of scanning favorites.
     * Default keeps the numerous test fakes compiling; the single production implementation
     * (NovelRepositoryImpl) overrides it with the novels.sq query.
     */
    suspend fun getDuplicateLibraryNovel(id: Long, title: String): List<Novel> = emptyList()

    suspend fun getReadNovelNotInLibrary(): List<Novel>

    suspend fun getLibraryNovel(): List<LibraryNovel>

    fun getLibraryNovelAsFlow(): Flow<List<LibraryNovel>>

    fun getNovelFavoritesBySourceId(sourceId: Long): Flow<List<Novel>>

    suspend fun insertNovel(novel: Novel): Long?

    suspend fun insertNetworkNovels(novels: List<Novel>, autoFavorite: Boolean = false): List<Novel> {
        return novels.map { novel ->
            val insertedId = insertNovel(novel)
            if (insertedId != null) {
                novel.copy(id = insertedId)
            } else {
                novel
            }
        }
    }

    suspend fun updateNovel(update: NovelUpdate): Boolean

    suspend fun updateAllNovel(novelUpdates: List<NovelUpdate>): Boolean

    fun getUpcomingNovels(statuses: Set<Long>): Flow<List<Novel>>

    suspend fun resetNovelViewerFlags(): Boolean

    suspend fun updateNovelMetadata(
        novelId: Long,
        customTitle: String?,
        customAuthor: String?,
        customDescription: String?,
        customGenre: List<String>?,
        customStatus: Long?,
    ): Boolean
}
