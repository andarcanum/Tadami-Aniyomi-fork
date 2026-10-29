package eu.kanade.tachiyomi.animesource.model

/**
 * Per-item media contract returned by [eu.kanade.tachiyomi.animesource.AnimeFeedMediaDescriptorSource]
 * (contract v25). Supplies the semantics [ShortVideoItem] cannot express, so the host stops
 * guessing them from the URL text.
 *
 * Sources without the capability keep the legacy host behavior: the media kind is sniffed from
 * the URL extension and a blank `videoUrl` is resolved through
 * [eu.kanade.tachiyomi.animesource.AnimeFeedVideoResolverSource] when the source implements it.
 * A descriptor is a verdict per item, not per feed: mixed albums can answer differently for
 * each reel.
 */
data class FeedMediaDescriptor(
    /**
     * Authoritative payload kind. [FeedMediaKind.STILL] makes the host render the URL as an
     * image even when it carries no recognizable extension (signed/opaque photo URLs);
     * [FeedMediaKind.VIDEO] suppresses the extension sniffing for video URLs that happen to
     * end in an image extension. Null keeps the legacy URL-based inference.
     */
    val kind: FeedMediaKind? = null,
    /**
     * True when a blank `videoUrl` may be signed just-in-time through the resolver capability.
     * False turns a blank URL into a terminal playback error instead of an endless
     * poster/spinner, because the host has no way to obtain a stream for the item.
     */
    val lazyResolution: Boolean = true,
    /**
     * True when a failed HD resolution may fall back to resolving the base variant
     * (`hd = false`). Only consulted for lazily resolved items; a present base URL is always
     * preferred over the HD variant already by the host.
     */
    val hdFallback: Boolean = true,
    /**
     * False marks the item as non-downloadable: the host refuses to create an offline copy
     * (removing an already stored copy still works).
     */
    val offlineSupported: Boolean = true,
)
