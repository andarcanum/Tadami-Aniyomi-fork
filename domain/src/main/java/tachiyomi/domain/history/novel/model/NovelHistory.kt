package tachiyomi.domain.history.novel.model

import java.util.Date

data class NovelHistory(
    val id: Long,
    val chapterId: Long,
    val readAt: Date?,
    val readDuration: Long,
) {
    companion object {
        fun create() = NovelHistory(
            id = -1L,
            chapterId = -1L,
            readAt = null,
            readDuration = -1L,
        )
    }
}