package tachiyomi.data.metadata

import tachiyomi.data.anilist.AnilistMetadataCache
import tachiyomi.data.shikimori.ShikimoriMetadataCache
import tachiyomi.domain.anilist.model.AnilistMetadata
import tachiyomi.domain.metadata.cache.ExternalMetadataCache
import tachiyomi.domain.metadata.model.ExternalMetadata
import tachiyomi.domain.metadata.model.MetadataContentType
import tachiyomi.domain.metadata.model.MetadataSource
import tachiyomi.domain.shikimori.model.ShikimoriMetadata

class AnimeExternalMetadataCache(
    private val anilistCache: AnilistMetadataCache,
    private val shikimoriCache: ShikimoriMetadataCache,
) : ExternalMetadataCache {

    override suspend fun get(
        contentType: MetadataContentType,
        mediaId: Long,
        source: MetadataSource,
    ): ExternalMetadata? {
        if (contentType != MetadataContentType.ANIME) return null

        return when (source) {
            MetadataSource.ANILIST -> anilistCache.get(mediaId)?.toExternalMetadata()
            MetadataSource.SHIKIMORI -> shikimoriCache.get(mediaId)?.toExternalMetadata()
            MetadataSource.NONE -> null
        }
    }

    override suspend fun upsert(metadata: ExternalMetadata) {
        if (metadata.contentType != MetadataContentType.ANIME) return

        when (metadata.source) {
            MetadataSource.ANILIST -> anilistCache.upsert(metadata.toAnilistMetadata())
            MetadataSource.SHIKIMORI -> shikimoriCache.upsert(metadata.toShikimoriMetadata())
            MetadataSource.NONE -> Unit
        }
    }

    override suspend fun delete(
        contentType: MetadataContentType,
        mediaId: Long,
        source: MetadataSource,
    ) {
        if (contentType != MetadataContentType.ANIME) return

        when (source) {
            MetadataSource.ANILIST -> anilistCache.delete(mediaId)
            MetadataSource.SHIKIMORI -> shikimoriCache.delete(mediaId)
            MetadataSource.NONE -> Unit
        }
    }

    override suspend fun clearAll() {
        anilistCache.clearAll()
        shikimoriCache.clearAll()
    }

    override suspend fun deleteStaleEntries() {
        anilistCache.deleteStaleEntries()
        shikimoriCache.deleteStaleEntries()
    }
}

private fun AnilistMetadata.toExternalMetadata(): ExternalMetadata {
    return ExternalMetadata(
        contentType = MetadataContentType.ANIME,
        source = MetadataSource.ANILIST,
        mediaId = animeId,
        remoteId = anilistId,
        score = score,
        format = format,
        status = status,
        coverUrl = coverUrl,
        coverUrlFallback = coverUrlFallback,
        searchQuery = searchQuery,
        updatedAt = updatedAt,
        isManualMatch = isManualMatch,
    )
}

private fun ShikimoriMetadata.toExternalMetadata(): ExternalMetadata {
    return ExternalMetadata(
        contentType = MetadataContentType.ANIME,
        source = MetadataSource.SHIKIMORI,
        mediaId = animeId,
        remoteId = shikimoriId,
        score = score,
        format = kind,
        status = status,
        coverUrl = coverUrl,
        coverUrlFallback = coverUrl,
        searchQuery = searchQuery,
        updatedAt = updatedAt,
        isManualMatch = isManualMatch,
    )
}

private fun ExternalMetadata.toAnilistMetadata(): AnilistMetadata {
    return AnilistMetadata(
        animeId = mediaId,
        anilistId = remoteId,
        score = score,
        format = format,
        status = status,
        coverUrl = coverUrl,
        coverUrlFallback = coverUrlFallback,
        searchQuery = searchQuery,
        updatedAt = updatedAt,
        isManualMatch = isManualMatch,
    )
}

private fun ExternalMetadata.toShikimoriMetadata(): ShikimoriMetadata {
    return ShikimoriMetadata(
        animeId = mediaId,
        shikimoriId = remoteId,
        score = score,
        kind = format,
        status = status,
        coverUrl = coverUrl,
        searchQuery = searchQuery,
        updatedAt = updatedAt,
        isManualMatch = isManualMatch,
    )
}
