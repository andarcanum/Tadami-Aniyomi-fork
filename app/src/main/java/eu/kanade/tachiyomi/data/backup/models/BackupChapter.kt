package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.core.common.extensions.EMPTY
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.novelchapter.model.NovelChapter

@Serializable
data class BackupChapter(
    // in 1.x some of these values have different names
    // url is called key in 1.x
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var name: String,
    @ProtoNumber(3) var scanlator: String? = null,
    @ProtoNumber(4) var read: Boolean = false,
    @ProtoNumber(5) var bookmark: Boolean = false,
    // lastPageRead is called progress in 1.x
    @ProtoNumber(6) var lastPageRead: Long = 0,
    @ProtoNumber(7) var dateFetch: Long = 0,
    @ProtoNumber(8) var dateUpload: Long = 0,
    // chapterNumber is called number is 1.x
    @ProtoNumber(9) var chapterNumber: Float = 0F,
    @ProtoNumber(10) var sourceOrder: Long = 0,
    @ProtoNumber(11) var lastModifiedAt: Long = 0,
    @ProtoNumber(12) var version: Long = 0,
    @ProtoNumber(13) var dateUploadRaw: String? = null,
    // E-L-16: chapter memo (JsonObject) carried as JSON text; null when empty. Tag 14 is free in
    // every sibling format (Mihon chapters end at 13).
    @ProtoNumber(14) var memoJson: String? = null,
) {
    fun toChapterImpl(): Chapter {
        return Chapter.create().copy(
            url = this@BackupChapter.url,
            name = this@BackupChapter.name,
            chapterNumber = this@BackupChapter.chapterNumber.toDouble(),
            scanlator = this@BackupChapter.scanlator,
            read = this@BackupChapter.read,
            bookmark = this@BackupChapter.bookmark,
            lastPageRead = this@BackupChapter.lastPageRead,
            dateFetch = this@BackupChapter.dateFetch,
            dateUpload = this@BackupChapter.dateUpload,
            sourceOrder = this@BackupChapter.sourceOrder,
            lastModifiedAt = this@BackupChapter.lastModifiedAt,
            version = this@BackupChapter.version,
            memo = parseBackupMemo(this@BackupChapter.memoJson),
        )
    }

    fun toNovelChapterImpl(): NovelChapter {
        return NovelChapter.create().copy(
            url = this@BackupChapter.url,
            name = this@BackupChapter.name,
            chapterNumber = this@BackupChapter.chapterNumber.toDouble(),
            scanlator = this@BackupChapter.scanlator,
            read = this@BackupChapter.read,
            bookmark = this@BackupChapter.bookmark,
            lastPageRead = this@BackupChapter.lastPageRead,
            dateFetch = this@BackupChapter.dateFetch,
            dateUpload = this@BackupChapter.dateUpload,
            dateUploadRaw = this@BackupChapter.dateUploadRaw,
            sourceOrder = this@BackupChapter.sourceOrder,
            lastModifiedAt = this@BackupChapter.lastModifiedAt,
            version = this@BackupChapter.version,
        )
    }
}

val backupNovelChapterMapper = {
        _: Long,
        _: Long,
        url: String,
        name: String,
        scanlator: String?,
        read: Boolean,
        bookmark: Boolean,
        lastPageRead: Long,
        chapterNumber: Double,
        source_order: Long,
        dateFetch: Long,
        dateUpload: Long,
        dateUploadRaw: String?,
        lastModifiedAt: Long,
        version: Long,
        _: Long,
        _: JsonObject,
    ->
    BackupChapter(
        url = url,
        name = name,
        chapterNumber = chapterNumber.toFloat(),
        scanlator = scanlator,
        read = read,
        bookmark = bookmark,
        lastPageRead = lastPageRead,
        dateFetch = dateFetch,
        dateUpload = dateUpload,
        dateUploadRaw = dateUploadRaw,
        sourceOrder = source_order,
        lastModifiedAt = lastModifiedAt,
        version = version,
    )
}

val backupChapterMapper = {
        _: Long,
        _: Long,
        url: String,
        name: String,
        scanlator: String?,
        read: Boolean,
        bookmark: Boolean,
        lastPageRead: Long,
        chapterNumber: Double,
        source_order: Long,
        dateFetch: Long,
        dateUpload: Long,
        lastModifiedAt: Long,
        version: Long,
        _: Long,
        memo: JsonObject,
    ->
    BackupChapter(
        url = url,
        name = name,
        chapterNumber = chapterNumber.toFloat(),
        scanlator = scanlator,
        read = read,
        bookmark = bookmark,
        lastPageRead = lastPageRead,
        dateFetch = dateFetch,
        dateUpload = dateUpload,
        sourceOrder = source_order,
        lastModifiedAt = lastModifiedAt,
        version = version,
        memoJson = toBackupMemoJson(memo),
    )
}

// E-L-16 helpers: the domain memo is a JsonObject; the proto carries its JSON text (null when
// empty) so native backups survive restore round-trips without touching the sister-app formats.
internal fun toBackupMemoJson(memo: JsonObject?): String? =
    memo?.takeIf { it != JsonObject.EMPTY }?.toString()

internal fun parseBackupMemo(json: String?): JsonObject =
    json?.let {
        runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull()
    } ?: JsonObject.EMPTY
