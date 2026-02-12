package tachiyomi.domain.items.novelchapter.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate

interface NovelChapterRepository {

    suspend fun addAllChapters(chapters: List<NovelChapter>): List<NovelChapter>

    suspend fun updateChapter(chapterUpdate: NovelChapterUpdate)

    suspend fun updateAllChapters(chapterUpdates: List<NovelChapterUpdate>)

    suspend fun removeChaptersWithIds(chapterIds: List<Long>)

    suspend fun getChapterByNovelId(novelId: Long, applyScanlatorFilter: Boolean = false): List<NovelChapter>

    suspend fun getBookmarkedChaptersByNovelId(novelId: Long): List<NovelChapter>

    suspend fun getChapterById(id: Long): NovelChapter?

    suspend fun getChapterByNovelIdAsFlow(
        novelId: Long,
        applyScanlatorFilter: Boolean = false,
    ): Flow<List<NovelChapter>>

    suspend fun getChapterByUrlAndNovelId(url: String, novelId: Long): NovelChapter?
}
