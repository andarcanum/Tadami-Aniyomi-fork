package eu.kanade.domain.ui.model

/**
 * Source for anime metadata (posters, ratings, type, status).
 */
enum class AnimeMetadataSource {
    /** Use Anilist.co for metadata (default) */
    ANILIST,
    /** Use Shikimori.one for metadata */
    SHIKIMORI,
    /** Don't fetch external metadata */
    NONE,
}
