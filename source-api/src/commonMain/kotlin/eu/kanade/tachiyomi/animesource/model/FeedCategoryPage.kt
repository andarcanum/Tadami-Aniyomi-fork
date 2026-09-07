package eu.kanade.tachiyomi.animesource.model

/**
 * One page of [FeedCategory] rows. Pagination follows the v17 sticky protocol: [nextCursor]
 * null keeps the host in page-int mode.
 */
data class FeedCategoryPage(
    val categories: List<FeedCategory>,
    val hasNextPage: Boolean,
    val nextCursor: String? = null,
)
