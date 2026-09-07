package eu.kanade.tachiyomi.animesource

/**
 * Capability interface (contract v20): a feed source whose service supports per-account
 * category subscriptions (follow/unfollow a niche/genre/channel), so the host can render the
 * follow toggle on category feeds — mirroring the creator-follow affordance of
 * [AnimeCreatorFeedSource].
 *
 * The host detects support with `source is AnimeCategorySubscriptionSource` (instanceof) —
 * the same marker-interface pattern as [AnimeCreatorFeedSource]/[AnimeReelsFeedbackSource];
 * no default members are added to existing interfaces. Requires login: logged-out sources
 * answer with an empty set / false and the host hides the toggle.
 */
interface AnimeCategorySubscriptionSource {

    /** Ids of the categories the account currently follows; empty when logged out or none. */
    suspend fun getSubscribedCategoryIds(): List<String>

    /** Follows ([followed] true) or unfollows one category. True on success. */
    suspend fun setCategorySubscription(categoryId: String, followed: Boolean): Boolean
}
