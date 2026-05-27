package tachiyomi.domain.source.model

data class FeedSavedSearch(
    val id: Long,
    val source: Long,
    val sourceType: SourceType = SourceType.MANGA,
    val listingType: FeedListingType = FeedListingType.LATEST,
    val savedSearch: Long?,
    val global: Boolean,
    val feedOrder: Long,
)
