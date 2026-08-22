package tachiyomi.domain.book.novel.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.book.novel.model.NovelHighlight
import tachiyomi.domain.book.novel.model.NovelHighlightWithChapter

interface NovelHighlightRepository {

    fun subscribeForNovel(novelId: Long): Flow<List<NovelHighlight>>

    fun subscribeForChapter(chapterId: Long): Flow<List<NovelHighlight>>

    fun subscribeAll(): Flow<List<NovelHighlightWithChapter>>

    suspend fun getForNovel(novelId: Long): List<NovelHighlight>

    suspend fun add(highlight: NovelHighlight): Long

    suspend fun updateNoteAndColor(highlightId: Long, note: String, colorArgb: Long, updatedAt: Long)

    suspend fun reanchor(
        highlightId: Long,
        blockIndex: Int,
        charStart: Int,
        charEndExclusive: Int,
        updatedAt: Long,
    )

    suspend fun delete(highlightId: Long)
}
