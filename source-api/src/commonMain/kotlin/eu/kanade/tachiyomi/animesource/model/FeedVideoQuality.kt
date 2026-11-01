package eu.kanade.tachiyomi.animesource.model

import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable

/*
 * Video-quality capability models (feed contract v29).
 *
 * These are transport/catalog values between the host and a feed source. They are never
 * persisted as a whole into the database; only a compact variant description is copied into
 * the app-local offline sidecar.
 */

/**
 * One playable rendition of an item.
 *
 * [id] is stable for the item (Telegram: `source` or `alt:<AlternativeVideo.id>`).
 * [width]/[height] are the reported dimensions, if known. For a vertical video the UI derives
 * the resolution from the shorter edge (1080x1920 -> 1080p).
 *
 * [sizeBytes] is the exact file size when known. A zero/unknown size must be represented as
 * null, not as a fake small file. [isSource] marks the message's main file; exactly one variant
 * in a catalog is the Source. Source is not guaranteed to be the camera original, the highest
 * resolution or a particular codec.
 */
@Serializable
data class FeedVideoQualityVariant(
    val id: String,
    val width: Int?,
    val height: Int?,
    val codec: String?,
    val sizeBytes: Long?,
    val isSource: Boolean,
) : JavaSerializable

/** Result of loading the quality catalog for one item. */
sealed interface FeedVideoQualitiesResult {

    /**
     * The catalog is available. [variants] must contain exactly one Source and only variants
     * this source can actually offer. [itemId]/[accountId] echo the request.
     */
    data class Available(
        val itemId: String,
        val accountId: String,
        val variants: List<FeedVideoQualityVariant>,
    ) : FeedVideoQualitiesResult

    /**
     * A temporary failure (network, rate limit, unresolved metadata). The host keeps the
     * previous valid binding and does not rewrite the quality preference.
     */
    data class Retry(val retryAtEpochMillis: Long?) : FeedVideoQualitiesResult

    /** The proven account is no longer authorized; the host uses its existing auth path. */
    data object NeedsAuth : FeedVideoQualitiesResult

    /** The account/replay context changed; the result belongs to the old context. */
    data object ContextChanged : FeedVideoQualitiesResult

    /** The item/file exists but cannot be served right now; this is not a Source-only catalog. */
    data object Unavailable : FeedVideoQualitiesResult

    /** The item id/domain cannot carry quality variants; the legacy single-file path applies. */
    data object Unsupported : FeedVideoQualitiesResult
}

/** Result of resolving one variant to a playable URL. */
sealed interface FeedVideoQualityResult {

    /**
     * A playable rendition. [videoUrl] is the source-provided playback URL and
     * [cacheIdentity] identifies the actual bytes, not a requested quality label.
     */
    data class Available(
        val itemId: String,
        val accountId: String,
        val variant: FeedVideoQualityVariant,
        val videoUrl: String,
        val cacheIdentity: String,
    ) : FeedVideoQualityResult

    /** Temporary condition; the caller may retry after [retryAtEpochMillis] and keeps its binding. */
    data class Retry(val retryAtEpochMillis: Long?) : FeedVideoQualityResult

    /** The proven account is no longer authorized. */
    data object NeedsAuth : FeedVideoQualityResult

    /** The account/replay context changed while resolving. */
    data object ContextChanged : FeedVideoQualityResult

    /** This specific file cannot be served; the catalog verdict is not rewritten. */
    data object Unavailable : FeedVideoQualityResult

    /** The item id/domain cannot carry quality variants. */
    data object Unsupported : FeedVideoQualityResult
}
