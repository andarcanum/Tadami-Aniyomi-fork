package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.SManga

/**
 * lib 1.6 sources may only implement getMangaUpdate. Its default implementation
 * bridges old sources to getMangaDetails, so calling the update API supports both.
 */
internal suspend fun fetchDiscoveryMangaDetails(source: MangaSource, entry: SManga): SManga {
    val originalUrl = entry.url
    val originalTitle = runCatching { entry.title }.getOrNull()
    val details = source.getMangaUpdate(
        manga = entry,
        chapters = emptyList(),
        fetchDetails = true,
        fetchChapters = false,
    ).manga
    // Details DTOs (including LibGroup.Manga) do not have to populate the list URL.
    details.url = originalUrl
    if (runCatching { details.title }.getOrDefault("").isBlank() && !originalTitle.isNullOrBlank()) {
        details.title = originalTitle
    }
    return details
}
