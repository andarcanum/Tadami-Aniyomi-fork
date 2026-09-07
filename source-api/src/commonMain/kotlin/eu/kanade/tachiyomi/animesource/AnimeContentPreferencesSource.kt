package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.ContentPreferenceOption

/**
 * Capability interface (contract v20): a feed source whose service stores per-account
 * content preferences (content-type toggles that shape the personalized feed).
 *
 * The host detects support with `source is AnimeContentPreferencesSource` (instanceof) — the
 * same marker-interface pattern as [AnimeCreatorFeedSource]/[AnimeReelsFeedbackSource]; no
 * default members are added to existing interfaces. Requires a logged-in account: with no
 * session [getContentPreferences] returns an empty list and [setContentPreferences] false.
 */
interface AnimeContentPreferencesSource {

    /** Current preference toggles of the logged-in account; empty when logged out. */
    suspend fun getContentPreferences(): List<ContentPreferenceOption>

    /**
     * Persists the enabled-toggle set ([enabledIds] ⊂ option ids). True on success; false
     * when logged out or rejected. Transport failures may throw (host surfaces them).
     */
    suspend fun setContentPreferences(enabledIds: List<String>): Boolean
}
