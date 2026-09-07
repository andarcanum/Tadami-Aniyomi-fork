package eu.kanade.tachiyomi.animesource.model

/** Which stream a [SearchSuggestion] opens when tapped. */
enum class SearchSuggestionKind { NICHE, CREATOR, TAG }

/**
 * One categorized-search hit (contract v20): the host renders [label] + [subtitle] with the
 * [imageUrl] preview and routes the tap by [kind] (NICHE → category feed, CREATOR → creator
 * feed, TAG → plain search with the tag as query).
 */
data class SearchSuggestion(
    val id: String,
    val label: String,
    val kind: SearchSuggestionKind,
    val imageUrl: String? = null,
    val subtitle: String? = null,
)
