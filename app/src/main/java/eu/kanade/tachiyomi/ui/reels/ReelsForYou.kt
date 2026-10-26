package eu.kanade.tachiyomi.ui.reels

/**
 * Product policy for the Reels "For You" consumption history.
 *
 * One place for the thresholds and for the identity rules, so the screen, the tracker and the
 * tests cannot drift apart. Values are a documented heuristic, not a platform standard:
 * [WATCH_THRESHOLD_MS] of real foreground playback OR [COVERAGE_THRESHOLD] of the clip's
 * naturally played intervals qualifies as watched.
 */
object ReelsForYou {

    /** Version stamp persisted with every row, so a policy change stays explainable. */
    const val POLICY_VERSION = 1

    /** Real foreground playback needed to count as watched. */
    const val WATCH_THRESHOLD_MS = 5_000L

    /** Unique media coverage needed for clips shorter than the time threshold. */
    const val COVERAGE_THRESHOLD = 0.8f

    /** Watch-accumulator sampling period. 4 Hz is enough for a 5 s threshold, no per-frame work. */
    const val SAMPLE_INTERVAL_MS = 250L

    /**
     * How long after a real drag a settled-page change still counts as manual. Covers the
     * fling/snap animation without letting a stale gesture turn a later auto-advance into a
     * skip.
     */
    const val USER_NAV_WINDOW_MS = 1_500L

    /** Candidate metadata schema stamp for [tachiyomi.domain.reels.anime.model.ReelsCandidate]. */
    const val METADATA_SCHEMA_VERSION = 2

    /** How many durable unseen candidates seed a freshly opened feed. */
    const val BACKLOG_SEED_LIMIT = 40

    /**
     * How many extra pages one feed generation may fetch while every incoming page is already
     * consumed. Bounded on purpose: a limit is NOT end-of-feed — the UI keeps saying "still
     * scanning" instead of pretending the user has seen everything.
     */
    const val UNSEEN_REFILL_MAX_PAGES = 6

    /**
     * Stable account scope for the history keys.
     *
     * A source that exposes [eu.kanade.tachiyomi.animesource.AnimeFeedAccountIdentitySource]
     * gives the real account id (survives relogin and process death). Sources without the
     * capability fall back to a source-scoped key — never to a display name or @handle, which
     * change and would silently split or merge someone's history.
     */
    fun accountScope(sourceId: Long, identity: String?): String {
        val stable = identity?.trim()?.takeIf { it.isNotEmpty() }
        return if (stable != null) "acc:$sourceId:$stable" else "src:$sourceId"
    }
}

/**
 * Why the pager moved from one settled card to another.
 *
 * `settledPage` alone cannot answer this: an auto-advance, a restore after refresh and a
 * deliberate swipe all end with the same state change. Only [USER_FORWARD] creates a skip.
 */
enum class ReelsTransitionCause {
    USER_FORWARD,
    USER_BACKWARD,
    AUTO_END,
    RESTORE,
    DATASET_CHANGE,
    ERROR_FALLBACK,
    PROGRAMMATIC,
}

/** Diagnostic surface of an interaction; stored with the summary, never used for filtering. */
enum class ReelsSurface(val key: String) {
    FOR_YOU("foryou"),
    CREATOR("creator"),
    FOLLOWING("following"),
    CUSTOM("custom"),
    NICHE("niche"),
    SEARCH("search"),
    PLAYLIST("playlist"),
}
