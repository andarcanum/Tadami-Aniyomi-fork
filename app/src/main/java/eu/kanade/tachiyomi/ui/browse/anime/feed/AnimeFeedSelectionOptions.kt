package eu.kanade.tachiyomi.ui.browse.anime.feed

import tachiyomi.domain.source.model.FeedListingType
import tachiyomi.domain.source.model.SavedSearch

data class AnimeFeedSelectionOption(
    val label: String,
    val listingType: FeedListingType,
    val savedSearch: SavedSearch? = null,
)

fun buildAnimeFeedSelectionOptions(
    sourceSupportsLatest: Boolean,
    savedSearches: List<SavedSearch>,
    latestLabel: String,
    popularLabel: String,
): List<AnimeFeedSelectionOption> {
    return buildList {
        if (sourceSupportsLatest) {
            add(AnimeFeedSelectionOption(label = latestLabel, listingType = FeedListingType.LATEST))
        }
        add(AnimeFeedSelectionOption(label = popularLabel, listingType = FeedListingType.POPULAR))
        savedSearches.forEach { search ->
            add(
                AnimeFeedSelectionOption(
                    label = search.name,
                    listingType = FeedListingType.SAVED_SEARCH,
                    savedSearch = search,
                ),
            )
        }
    }
}

fun buildAnimeFeedSubtitle(
    language: String,
    listingType: FeedListingType,
    savedSearchName: String?,
    latestLabel: String,
    popularLabel: String,
): String {
    val typeLabel = when (listingType) {
        FeedListingType.LATEST -> latestLabel
        FeedListingType.POPULAR -> popularLabel
        FeedListingType.SAVED_SEARCH -> savedSearchName ?: "Search"
    }
    return "$language · $typeLabel"
}
