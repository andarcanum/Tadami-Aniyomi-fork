package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.FeedMediaDescriptor
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem

/**
 * Capability interface (contract v25): a feed source that describes the media semantics of its
 * items instead of leaving the host to infer them from URL text.
 *
 * Instanceof-detected (the marker-interface pattern; no default members on existing
 * interfaces). Sources without it keep the legacy behavior — extension-based still detection,
 * lazy resolution through [AnimeFeedVideoResolverSource] when available, HD-to-base fallback,
 * and offline copies for every item.
 *
 * [mediaDescriptor] must be cheap and side-effect free: the host may call it for preloaded
 * pages as well. It receives the exact item the feed returned; returning the same descriptor
 * for the same item is expected.
 */
interface AnimeFeedMediaDescriptorSource {

    fun mediaDescriptor(item: ShortVideoItem): FeedMediaDescriptor
}
