package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.discovery.service.DiscoveryPreferences
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.repository.DiscoveryCandidateValidator

/**
 * Final admission check used by the repository inside the publication
 * transaction. Reads the current genre policy from preferences so a snapshot
 * prepared with stale settings cannot publish incompatible candidates.
 */
class AppDiscoveryCandidateValidator(
    private val preferences: DiscoveryPreferences,
) : DiscoveryCandidateValidator {

    override fun isAllowed(candidate: DiscoverySuggestion, liveBlacklistedTags: Set<String>): Boolean {
        val policy = DiscoveryCandidatePolicy(
            spec = preferences.genreFilterSpec(),
            blacklistedTags = liveBlacklistedTags,
        )
        return policy.isAllowed(candidate)
    }
}
