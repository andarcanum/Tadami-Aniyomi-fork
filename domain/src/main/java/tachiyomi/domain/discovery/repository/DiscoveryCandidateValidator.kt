package tachiyomi.domain.discovery.repository

import tachiyomi.domain.discovery.model.DiscoverySuggestion

/** Final policy check applied by the repository inside the publication transaction. */
interface DiscoveryCandidateValidator {
    fun isAllowed(candidate: DiscoverySuggestion, liveBlacklistedTags: Set<String>): Boolean
}
