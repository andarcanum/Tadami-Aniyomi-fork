package tachiyomi.domain.discovery.repository

import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoverySuggestion

/** Final policy check applied by the repository inside the publication transaction. */
interface DiscoveryCandidateValidator {
    fun isAllowed(candidate: DiscoverySuggestion, liveBlacklistedTags: Set<String>): Boolean

    /** Pure live-config comparison after repository awaits, before any publication/top-up mutation. */
    fun isConfigurationCurrent(media: DiscoveryMediaType, capturedKey: String): Boolean = true
}
