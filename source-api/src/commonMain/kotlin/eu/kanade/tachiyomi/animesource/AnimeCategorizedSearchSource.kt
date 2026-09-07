package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.SearchSuggestions

/**
 * Capability interface (contract v20): a feed source whose search answers with categorized
 * hits (categories/creators/tags, each with a preview), so the host can render site-like
 * search tabs instead of a single flat result stream.
 *
 * The host detects support with `source is AnimeCategorizedSearchSource` (instanceof) and
 * calls this in parallel with [AnimeFeedSource.getSearchFeed]; the flat feed still powers the
 * main reel stream. Failures may throw: the host hides the tabs and keeps the flat feed.
 */
interface AnimeCategorizedSearchSource {

    /** Categorized hits for [query]; empty sections are hidden by the host. */
    suspend fun getCategorizedSearch(query: String): SearchSuggestions
}
