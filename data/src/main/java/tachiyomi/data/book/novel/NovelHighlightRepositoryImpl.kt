package tachiyomi.data.book.novel

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.book.novel.model.NovelHighlight
import tachiyomi.domain.book.novel.repository.NovelHighlightRepository

class NovelHighlightRepositoryImpl(
    private val handler: NovelDatabaseHandler,
) : NovelHighlightRepository {

    override fun subscribeForNovel(novelId: Long): Flow<List<NovelHighlight>> {
        return handler.subscribeToList { db -> db.novel_highlightsQueries.getForNovel(novelId, ::novelHighlightMapper) }
    }

    override fun subscribeForChapter(chapterId: Long): Flow<List<NovelHighlight>> {
        return handler.subscribeToList { db ->
            db.novel_highlightsQueries.getForChapter(chapterId, ::novelHighlightMapper)
        }
    }

    override suspend fun getForNovel(novelId: Long): List<NovelHighlight> {
        return handler.awaitList { db -> db.novel_highlightsQueries.getForNovel(novelId, ::novelHighlightMapper) }
    }

    override suspend fun add(highlight: NovelHighlight): Long {
        return handler.await { db ->
            db.novel_highlightsQueries.insert(
                novelId = highlight.novelId,
                chapterId = highlight.chapterId,
                blockIndex = highlight.blockIndex.toLong(),
                charStart = highlight.charStart.toLong(),
                charEndExclusive = highlight.charEndExclusive.toLong(),
                normalizedText = highlight.normalizedText,
                colorArgb = highlight.colorArgb,
                note = highlight.note,
                createdAt = highlight.createdAt,
                updatedAt = highlight.updatedAt,
                pageIndex = highlight.pageIndex.toLong(),
                pageCount = highlight.pageCount.toLong(),
            )
            db.novel_highlightsQueries.selectLastInsertedRowId().executeAsOne()
        }
    }

    override suspend fun updateNoteAndColor(highlightId: Long, note: String, colorArgb: Long, updatedAt: Long) {
        handler.await { db ->
            db.novel_highlightsQueries.updateNoteAndColor(
                note = note,
                colorArgb = colorArgb,
                updatedAt = updatedAt,
                highlightId = highlightId,
            )
        }
    }

    override suspend fun reanchor(
        highlightId: Long,
        blockIndex: Int,
        charStart: Int,
        charEndExclusive: Int,
        updatedAt: Long,
    ) {
        handler.await { db ->
            db.novel_highlightsQueries.reanchor(
                blockIndex = blockIndex.toLong(),
                charStart = charStart.toLong(),
                charEndExclusive = charEndExclusive.toLong(),
                updatedAt = updatedAt,
                highlightId = highlightId,
            )
        }
    }

    override suspend fun delete(highlightId: Long) {
        handler.await { db -> db.novel_highlightsQueries.delete(highlightId) }
    }
}

private fun novelHighlightMapper(
    id: Long,
    novelId: Long,
    chapterId: Long,
    blockIndex: Long,
    charStart: Long,
    charEndExclusive: Long,
    normalizedText: String,
    colorArgb: Long,
    note: String,
    createdAt: Long,
    updatedAt: Long,
    pageIndex: Long,
    pageCount: Long,
): NovelHighlight = NovelHighlight(
    id = id,
    novelId = novelId,
    chapterId = chapterId,
    blockIndex = blockIndex.toInt(),
    charStart = charStart.toInt(),
    charEndExclusive = charEndExclusive.toInt(),
    normalizedText = normalizedText,
    colorArgb = colorArgb,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    pageIndex = pageIndex.toInt(),
    pageCount = pageCount.toInt(),
)
