package eu.kanade.tachiyomi.animesource.model

/*
 * Round-robin capability models (feed contract v28).
 *
 * These are transport values between the host's For You coordinator and a feed source. New
 * request/result models in this file are not serialized as a whole into the database.
 */

/**
 * Whether the source can currently serve a round-robin scan.
 *
 * [SignedOut] and [Pending] never start a scan; only [Ready] carries a proven account context.
 * They are distinct: [SignedOut] is confirmed unauthorized and is the only state that keeps the
 * legacy unauthenticated web fallback, while [Pending] is a transient identity/TDLib resolution
 * and must never fall back to the legacy/web feed.
 */
sealed interface FeedRoundRobinReadiness {

    /**
     * Confirmed unauthorized: the source has no authorized session. Not the same as an empty
     * feed and not the same as [Pending]; only this state keeps the legacy unauthenticated web
     * feed as the host fallback.
     */
    data object SignedOut : FeedRoundRobinReadiness

    /**
     * Identity/TDLib is still resolving, not signed out; [retryAtEpochMillis] is an absolute
     * hint, null = host default. The host must not fall back to the legacy/web feed while pending.
     */
    data class Pending(val retryAtEpochMillis: Long?) : FeedRoundRobinReadiness

    /**
     * Round-robin can run.
     *
     * [context] is the ordinary v27 [FeedReplayContext] (account + mutable continuity key).
     * [storageKey] is a separate stable, non-secret identifier of the round-robin backend/format
     * (Telegram: `tdlib-rr-v1`). It must not contain the subscription list, ports, epochs or
     * timestamps, and it stays stable while [context] changes with the source configuration.
     */
    data class Ready(
        val context: FeedReplayContext,
        val storageKey: String,
    ) : FeedRoundRobinReadiness
}

/**
 * Stable identity of one round-robin candidate.
 *
 * [sourceKey] is the source's durable key for the chat (Telegram: `chatId.toString()`, never an
 * `@username`). [orderKey] is the source's ordering value inside that chat (Telegram: TDLib
 * `message.id` as a Long), compared descending. The item's own id stays delivery-independent.
 */
data class FeedRoundRobinIdentity(
    val sourceKey: String,
    val orderKey: Long,
)

/** One chat/source in a round-robin catalog. */
data class FeedRoundRobinSourceRef(
    val key: String,
    val title: String,
    val author: String?,
    val contentRevision: String?,
    val excluded: Boolean,
    val previousKeys: List<String>,
)

/** Catalog of chats the source will rotate over. */
data class FeedRoundRobinCatalog(
    val context: FeedReplayContext,
    val revision: String,
    val complete: Boolean,
    val sources: List<FeedRoundRobinSourceRef>,
    val retryAtEpochMillis: Long?,
)

/** Result of refreshing the round-robin catalog. */
sealed interface FeedRoundRobinCatalogResult {

    /** The catalog is valid for the requested context; [FeedRoundRobinCatalog.complete] tells
     * whether it is a full listing or a partial view with [retryAtEpochMillis]. */
    data class Snapshot(val catalog: FeedRoundRobinCatalog) : FeedRoundRobinCatalogResult

    /** Catalog refresh is deferred; [retryAtEpochMillis] is null when the source has no window. */
    data class Deferred(val retryAtEpochMillis: Long?) : FeedRoundRobinCatalogResult

    /** The account/context changed (401, re-login, different account); readiness must be redone. */
    data object ContextChanged : FeedRoundRobinCatalogResult
}

/** One scanned candidate, with the round-robin identity and an optional durable content key. */
data class FeedRoundRobinCandidate(
    val item: ShortVideoItem,
    val identity: FeedRoundRobinIdentity,
    /**
     * A durable content identity when the backend provides one (Telegram: TDLib remote unique
     * file id), otherwise null. A process-local file id is not a durable global identity.
     */
    val contentKey: String?,
)

/**
 * One bounded round-robin scan request for a single chat.
 *
 * [maxCandidates] is the free-slot budget, always in `1..2`; with zero free slots the host does
 * not call the scanner. [checkpoint] is null on the first scan and opaque afterwards. Every
 * returned identity must match [sourceKey], item ids must be non-empty, order keys positive and
 * the result must not exceed [maxCandidates].
 */
data class FeedRoundRobinScanRequest(
    val sourceKey: String,
    val expectedContext: FeedReplayContext,
    val filters: AnimeFilterList,
    val checkpoint: String?,
    val refreshHead: Boolean,
    val maxCandidates: Int,
)

/** Reason a round-robin scan produced no page. */
enum class FeedRoundRobinDeferral { NETWORK, RATE_LIMIT, TIME_BUDGET, UPSTREAM_PENDING }

/** Result of one round-robin scan request. */
sealed interface FeedRoundRobinScanResult {

    /**
     * A bounded batch of candidates for the requested chat.
     *
     * [checkpoint] is non-empty even when [historyExhausted] is true: later scans still need it
     * for head checks. The same empty checkpoint without exhaustion or a retry is a protocol
     * stall, not a tight retry loop. [nextHeadCheckAtEpochMillis] is an optional absolute hint.
     */
    data class Page(
        val candidates: List<FeedRoundRobinCandidate>,
        val checkpoint: String,
        val historyExhausted: Boolean,
        val nextHeadCheckAtEpochMillis: Long?,
    ) : FeedRoundRobinScanResult

    /**
     * The scan could not run this time.
     *
     * [providerWide] is true when the limit applies to the whole provider (Telegram 420/429),
     * not just this chat; even a manual refresh must not cancel [retryAtEpochMillis].
     */
    data class Deferred(
        val reason: FeedRoundRobinDeferral,
        val retryAtEpochMillis: Long?,
        val providerWide: Boolean,
    ) : FeedRoundRobinScanResult

    /** This chat cannot be scanned (deleted/left); it is not a provider-wide outage. */
    data object Unavailable : FeedRoundRobinScanResult

    /** The account/context changed; readiness and the catalog must be redone. */
    data object ContextChanged : FeedRoundRobinScanResult

    /** The supplied checkpoint can no longer be resolved; the host restarts that chat's scan. */
    data object InvalidCheckpoint : FeedRoundRobinScanResult
}
