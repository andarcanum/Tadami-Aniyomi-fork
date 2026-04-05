package tachiyomi.domain.metadata.cache

import tachiyomi.domain.metadata.model.ExternalMetadata
import tachiyomi.domain.metadata.model.MetadataContentType
import tachiyomi.domain.metadata.model.MetadataSource

interface ExternalMetadataCache {
    suspend fun get(
        contentType: MetadataContentType,
        mediaId: Long,
        source: MetadataSource,
    ): ExternalMetadata?

    suspend fun upsert(metadata: ExternalMetadata)

    suspend fun delete(
        contentType: MetadataContentType,
        mediaId: Long,
        source: MetadataSource,
    )

    suspend fun clearAll()

    suspend fun deleteStaleEntries()
}
