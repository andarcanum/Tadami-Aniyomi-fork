package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * One saved highlight traveling inside [BackupNovel]. The chapter travels by URL because
 * chapter ids differ between devices (same convention as bookState.lastChapterUrl).
 */
@Serializable
data class BackupNovelHighlight(
    @ProtoNumber(1) val chapterUrl: String,
    @ProtoNumber(2) val blockIndex: Int,
    @ProtoNumber(3) val charStart: Int,
    @ProtoNumber(4) val charEndExclusive: Int,
    @ProtoNumber(5) val normalizedText: String,
    @ProtoNumber(6) val colorArgb: Long,
    @ProtoNumber(7) val note: String,
    @ProtoNumber(8) val createdAt: Long,
    @ProtoNumber(9) val updatedAt: Long,
    /** Page position captured at creation time; 0/0 = unknown (older backups). */
    @ProtoNumber(10) val pageIndex: Int = 0,
    @ProtoNumber(11) val pageCount: Int = 0,
)
