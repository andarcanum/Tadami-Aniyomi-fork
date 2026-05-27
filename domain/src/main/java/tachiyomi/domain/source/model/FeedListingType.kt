package tachiyomi.domain.source.model

enum class FeedListingType(val id: Long) {
    LATEST(0),
    POPULAR(1),
    SAVED_SEARCH(2),
}
