package eu.kanade.tachiyomi.animesource.model

/**
 * One browsable category of a feed source (contract v20): a niche/genre/channel row the host
 * can render with a preview image (e.g. the RedGIFs niche directory).
 */
data class FeedCategory(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val itemCount: Long? = null,
)
