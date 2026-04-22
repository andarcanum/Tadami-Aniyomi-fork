package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupMangaSeries(
    @ProtoNumber(1) val title: String,
    @ProtoNumber(2) val description: String? = null,
    @ProtoNumber(3) val categoryName: String? = null,
    @ProtoNumber(4) val sortOrder: Long = 0,
    @ProtoNumber(5) val dateAdded: Long = 0,
    @ProtoNumber(6) val coverLastModified: Long = 0,
    @ProtoNumber(7) val pinned: Boolean = false,
    @ProtoNumber(8) val coverMode: Long = 0,
    @ProtoNumber(9) val coverEntrySource: Long? = null,
    @ProtoNumber(10) val coverEntryUrl: String? = null,
    @ProtoNumber(11) val entries: List<BackupSeriesEntryRef> = emptyList(),
    @ProtoNumber(12) val customCover: ByteArray? = null,
)
