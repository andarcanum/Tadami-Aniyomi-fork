package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupFeed(
    @ProtoNumber(1) val source: Long = 0,
    @ProtoNumber(2) val global: Boolean = true,
    @ProtoNumber(3) val feedOrder: Long = 0,
)

val backupFeedMapper = {
        source: Long,
        global: Boolean,
        savedSearch: Long?,
        name: String?,
        query: String?,
        filtersJson: String?,
    ->
    BackupFeed(source = source, global = global)
}
