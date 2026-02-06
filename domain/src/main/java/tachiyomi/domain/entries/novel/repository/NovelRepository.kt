package tachiyomi.domain.entries.novel.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.library.novel.LibraryNovel

interface NovelRepository {

    suspend fun getNovelById(id: Long): Novel

    suspend fun getNovelByIdAsFlow(id: Long): Flow<Novel>

    suspend fun getNovelByUrlAndSourceId(url: String, sourceId: Long): Novel?

    fun getNovelByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Novel?>

    suspend fun getNovelFavorites(): List<Novel>

    suspend fun getReadNovelNotInLibrary(): List<Novel>

    suspend fun getLibraryNovel(): List<LibraryNovel>

    fun getLibraryNovelAsFlow(): Flow<List<LibraryNovel>>

    fun getNovelFavoritesBySourceId(sourceId: Long): Flow<List<Novel>>

    suspend fun insertNovel(novel: Novel): Long?

    suspend fun updateNovel(update: NovelUpdate): Boolean

    suspend fun updateAllNovel(novelUpdates: List<NovelUpdate>): Boolean

    suspend fun resetNovelViewerFlags(): Boolean
}
