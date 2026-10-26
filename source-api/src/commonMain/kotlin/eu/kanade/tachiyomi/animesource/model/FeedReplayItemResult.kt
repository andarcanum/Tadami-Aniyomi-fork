package eu.kanade.tachiyomi.animesource.model

import java.io.Serializable

/**
 * Result of resolving a single saved replay candidate (feed contract v27).
 */
sealed interface FeedReplayItemResult : Serializable {

    /**
     * Fresh playable video item echoing the requested item id with current stream/poster URLs.
     */
    data class Available(val item: ShortVideoItem) : FeedReplayItemResult

    /**
     * Item is excluded by the source's content filter configuration.
     */
    data object FilteredOut : FeedReplayItemResult

    /**
     * Authoritative removal or permanently unsupported item (e.g. deleted message/post).
     */
    data object Unavailable : FeedReplayItemResult

    /**
     * Transient failure (e.g. rate limit, temporary timeout, network backoff).
     * The candidate remains pending in the session queue.
     */
    data class Deferred(val retryAtEpochMillis: Long?) : FeedReplayItemResult

    /**
     * The source's active context changed or mismatched the expected replay context.
     */
    data object ContextChanged : FeedReplayItemResult
}
