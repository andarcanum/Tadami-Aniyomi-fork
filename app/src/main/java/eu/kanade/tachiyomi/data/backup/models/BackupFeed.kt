package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupFeed(
    @ProtoNumber(1) val source: Long = 0,
    @ProtoNumber(2) val global: Boolean = true,
    @ProtoNumber(3) val feedOrder: Long = 0,
    @ProtoNumber(4) val sourceType: Long = 0,
    @ProtoNumber(5) val savedSearchName: String? = null,
    @ProtoNumber(6) val savedSearchQuery: String? = null,
    @ProtoNumber(7) val savedSearchFiltersJson: String? = null,
)

val backupFeedMapper = {
        source: Long,
        sourceType: Long,
        global: Boolean,
        feedOrder: Long,
        savedSearch: Long?,
        name: String?,
        query: String?,
        filtersJson: String?,
    ->
    BackupFeed(
        source = source,
        sourceType = sourceType,
        global = global,
        feedOrder = feedOrder,
        savedSearchName = if (savedSearch != null) name else null,
        savedSearchQuery = if (savedSearch != null) query else null,
        savedSearchFiltersJson = if (savedSearch != null) filtersJson else null,
    )
}
