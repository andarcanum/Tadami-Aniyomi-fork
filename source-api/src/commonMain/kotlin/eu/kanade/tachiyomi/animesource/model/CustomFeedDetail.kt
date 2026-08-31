package eu.kanade.tachiyomi.animesource.model

/**
 * Full content of a custom feed for the host's editor: its name and the selected tags.
 * Returned by [eu.kanade.tachiyomi.animesource.AnimeCustomFeedSource.getCustomFeedDetail].
 */
data class CustomFeedDetail(
    val name: String,
    val tags: List<String>,
)
