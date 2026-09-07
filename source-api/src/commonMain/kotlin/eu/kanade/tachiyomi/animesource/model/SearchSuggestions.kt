package eu.kanade.tachiyomi.animesource.model

/**
 * The three categorized-search sections (contract v20). A source fills only the sections its
 * service supports; the host hides empty tabs.
 */
data class SearchSuggestions(
    val niches: List<SearchSuggestion> = emptyList(),
    val creators: List<SearchSuggestion> = emptyList(),
    val tags: List<SearchSuggestion> = emptyList(),
)
