package eu.kanade.tachiyomi.ui.browse.anime.feed

import tachiyomi.domain.source.model.FeedListingType

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
