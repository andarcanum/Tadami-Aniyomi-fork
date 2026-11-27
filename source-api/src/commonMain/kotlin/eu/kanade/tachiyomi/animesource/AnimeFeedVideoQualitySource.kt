package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.FeedVideoQualitiesResult
import eu.kanade.tachiyomi.animesource.model.FeedVideoQualityResult

/**
 * Capability interface (feed contract v29): per-item video quality selection.
 *
 * Extends [AnimeFeedReplaySource], so the v26 account scope and the v27 replay readiness stay
 * available: the host can wait for a proven native identity before asking for qualities. The
 * capability is detected with `rawSource is AnimeFeedVideoQualitySource`; sources that do not
 * implement it keep the legacy HD/SD URL path.
 *
 * All members are abstract; no default members are added to older interfaces.
 * Implementing this capability requires `extensionLib` 29.0; older plugins inside
 * `12.0..28.0` are untouched and keep loading.
 */
interface AnimeFeedVideoQualitySource : AnimeFeedReplaySource {

    /**
     * Whether [itemId] belongs to a domain this source can resolve quality variants for.
     *
     * Cheap, synchronous and free of IO/network/account lookups: the host calls it before it
     * requires a proven account identity. Telegram returns false for web/non-TDLib item ids and
     * true for native `tdlib:` ids; the adapter validates the native id format later. A false
     * result keeps the legacy single-source playback path and never blocks on authorization.
     */
    fun supportsVideoQuality(itemId: String): Boolean

    /**
     * Returns the quality variants currently available for [itemId] under [expectedAccountId].
     *
     * The result must echo the requested item/account. A catalog is not a resolved playback
     * binding: [FeedVideoQualitiesResult.Available] only lists what can be offered. Missing
     * metadata (for example an unknown size) does not remove a variant; an unavailable file or
     * an unsupported item is reported through the typed results instead of a fake Source-only
     * catalog.
     */
    suspend fun getVideoQualities(
        itemId: String,
        expectedAccountId: String,
    ): FeedVideoQualitiesResult

    /**
     * Resolves one variant to a playable URL and a byte identity.
     *
     * The caller passes the exact variant id previously returned by
     * [getVideoQualities]. [FeedVideoQualityResult.Retry] means a temporary condition; it must
     * not be translated into a Source-only menu. [FeedVideoQualityResult.Unavailable] means this
     * file cannot be served, not that the item has no other qualities.
     */
    suspend fun resolveVideoQuality(
        itemId: String,
        variantId: String,
        expectedAccountId: String,
    ): FeedVideoQualityResult
}
