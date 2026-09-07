package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.FeedCategoryPage
import eu.kanade.tachiyomi.animesource.model.FeedPage

/**
 * Capability interface (contract v20): a feed source with a browsable public category
 * directory (niches/genres/channels) and one feed per category.
 *
 * The host detects support with `source is AnimeFeedBrowseSource` (instanceof); sources
 * without it simply never show the category browser. Both calls follow the v17 sticky
 * pagination protocol per stream and are replay-safe.
 */
interface AnimeFeedBrowseSource {

    /** One page of the category directory. Empty list + hasNextPage=false when unsupported/empty. */
    suspend fun getBrowseCategories(page: Int, cursor: String?): FeedCategoryPage

    /**
     * One page of a category's video feed. [categoryId] is exactly a
     * [eu.kanade.tachiyomi.animesource.model.FeedCategory.id] previously returned by this
     * source; a deleted category returns an empty [FeedPage], not a throw.
     */
    suspend fun getCategoryFeed(categoryId: String, page: Int, cursor: String?): FeedPage
}
