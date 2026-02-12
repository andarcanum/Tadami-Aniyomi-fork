package tachiyomi.domain.entries.novel.model

import tachiyomi.domain.entries.EntryCover

/**
 * Contains the required data for NovelCoverFetcher
 */
data class NovelCover(
    val novelId: Long,
    val sourceId: Long,
    val isNovelFavorite: Boolean,
    val url: String?,
    val lastModified: Long,
) : EntryCover

fun Novel.asNovelCover(): NovelCover {
    return NovelCover(
        novelId = id,
        sourceId = source,
        isNovelFavorite = favorite,
        url = thumbnailUrl,
        lastModified = coverLastModified,
    )
}
