package tachiyomi.domain.history.novel.model

import java.util.Date

data class NovelHistoryUpdate(
    val chapterId: Long,
    val readAt: Date,
    val sessionReadDuration: Long,
)