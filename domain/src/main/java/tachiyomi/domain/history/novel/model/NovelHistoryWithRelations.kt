package tachiyomi.domain.history.novel.model

import tachiyomi.domain.entries.novel.model.NovelCover
import java.util.Date

data class NovelHistoryWithRelations(
    val id: Long,
    val chapterId: Long,
    val novelId: Long,
    val title: String,
    val chapterNumber: Double,
    val readAt: Date?,
    val readDuration: Long,
    val coverData: NovelCover,
)