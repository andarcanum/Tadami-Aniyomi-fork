package eu.kanade.tachiyomi.animesource

/**
 * Capability interface (contract v19 addendum): a feed source that can supply its real tag
 * list, so the host shows the source's own tags instead of a static fallback.
 *
 * The host detects support with `rawSource is AnimeSearchHintsSource` (instanceof) — the same
 * marker-interface pattern as [AnimeCreatorFeedSource] and [AnimeReelsFeedbackSource]; no
 * default members are added to existing interfaces, so plugins compiled before this interface
 * exists remain untouched.
 */
interface AnimeSearchHintsSource {

    /**
     * Tag names rendered as quick-pick chips in the reels search bar. Optional by contract:
     * sources without the capability (or answering empty) keep the host's built-in popular
     * list. The call happens off the main thread and failures are swallowed by the host, so
     * implementations may (and should) return a cached list instead of throwing.
     */
    suspend fun getSearchHints(): List<String>
}
