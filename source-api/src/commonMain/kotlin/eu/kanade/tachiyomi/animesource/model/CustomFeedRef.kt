package eu.kanade.tachiyomi.animesource.model

/**
 * Lightweight reference to a user's custom feed as returned by [eu.kanade.tachiyomi.animesource.AnimeCustomFeedSource].
 * Just enough identity for the host's picker; the feed's videos come from
 * [eu.kanade.tachiyomi.animesource.AnimeCustomFeedSource.getCustomFeed].
 */
data class CustomFeedRef(
    val id: String,
    val name: String,
)
