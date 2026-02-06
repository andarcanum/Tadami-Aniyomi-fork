package tachiyomi.domain.updates.novel.model

import tachiyomi.domain.entries.novel.model.NovelCover

data class NovelUpdatesWithRelations(
    val novelId: Long,
    val novelTitle: String,
    val chapterId: Long,
    val chapterName: String,
    val scanlator: String?,
    val read: Boolean,
    val bookmark: Boolean,
    val lastPageRead: Long,
    val sourceId: Long,
    val dateFetch: Long,
    val coverData: NovelCover,
)
