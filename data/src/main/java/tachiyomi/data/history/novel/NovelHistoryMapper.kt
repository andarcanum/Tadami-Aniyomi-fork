package tachiyomi.data.history.novel

import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.history.novel.model.NovelHistory
import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations
import java.util.Date

object NovelHistoryMapper {
    fun mapNovelHistory(
        id: Long,
        chapterId: Long,
        readAt: Date?,
        readDuration: Long,
    ): NovelHistory = NovelHistory(
        id = id,
        chapterId = chapterId,
        readAt = readAt,
        readDuration = readDuration,
    )

    fun mapNovelHistoryWithRelations(
        historyId: Long,
        novelId: Long,
        chapterId: Long,
        title: String,
        thumbnailUrl: String?,
        sourceId: Long,
        isFavorite: Boolean,
        coverLastModified: Long,
        chapterNumber: Double,
        readAt: Date?,
        readDuration: Long,
    ): NovelHistoryWithRelations = NovelHistoryWithRelations(
        id = historyId,
        chapterId = chapterId,
        novelId = novelId,
        title = title,
        chapterNumber = chapterNumber,
        readAt = readAt,
        readDuration = readDuration,
        coverData = NovelCover(
            novelId = novelId,
            sourceId = sourceId,
            isNovelFavorite = isFavorite,
            url = thumbnailUrl,
            lastModified = coverLastModified,
        ),
    )
}