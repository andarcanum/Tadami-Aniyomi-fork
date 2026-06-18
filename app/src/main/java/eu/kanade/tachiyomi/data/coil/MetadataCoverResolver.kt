package eu.kanade.tachiyomi.data.coil

import eu.kanade.domain.ui.UiPreferences
import tachiyomi.data.metadata.AnimeExternalMetadataCache
import tachiyomi.data.metadata.MangaExternalMetadataCache
import tachiyomi.domain.metadata.model.ExternalMetadata
import tachiyomi.domain.metadata.model.MetadataContentType
import tachiyomi.domain.metadata.model.MetadataSource

internal class MetadataCoverResolver(
    private val uiPreferences: UiPreferences,
    private val mangaMetadataCache: MangaExternalMetadataCache,
    private val animeMetadataCache: AnimeExternalMetadataCache,
) {
    suspend fun resolveMangaCoverUrl(mangaId: Long): String? {
        return resolveCoverUrl(MetadataContentType.MANGA, mangaId, mangaMetadataCache::get)
    }

    suspend fun resolveAnimeCoverUrl(animeId: Long): String? {
        return resolveCoverUrl(MetadataContentType.ANIME, animeId, animeMetadataCache::get)
    }

    private suspend fun resolveCoverUrl(
        contentType: MetadataContentType,
        mediaId: Long,
        getMetadata: suspend (MetadataContentType, Long, MetadataSource) -> ExternalMetadata?,
    ): String? {
        val source = uiPreferences.metadataSource().get()
        if (source == MetadataSource.NONE) return null

        val metadata = getMetadata(contentType, mediaId, source) ?: return null
        return metadata.coverUrl.takeIf { it.isUsableCoverUrl() }
            ?: metadata.coverUrlFallback.takeIf { it.isUsableCoverUrl() }
    }
}

private fun String?.isUsableCoverUrl(): Boolean {
    return !isNullOrBlank()
}
