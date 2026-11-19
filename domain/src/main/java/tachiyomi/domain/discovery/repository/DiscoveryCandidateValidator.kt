package tachiyomi.domain.discovery.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoverySuggestion

/** Final policy check applied by the repository inside the publication transaction. */
interface DiscoveryCandidateValidator {
    fun isAllowed(candidate: DiscoverySuggestion, liveBlacklistedTags: Set<String>): Boolean

    /** Pure live-config comparison after repository awaits, before any publication/top-up mutation. */
    fun isConfigurationCurrent(media: DiscoveryMediaType, capturedKey: String): Boolean = true

    /**
     * Reactive trigger emitted whenever the live admission policy (installed source inventory,
     * source exclusions, external providers, or genre filters) changes for [media].
     * Default emits once so static test validators remain compatible.
     */
    fun observeChanges(media: DiscoveryMediaType): Flow<Unit> = flowOf(Unit)

    /**
     * Source policy revision captured when validating a snapshot. Empty when the validator
     * does not track source inventory revisions (e.g. test fakes).
     */
    fun sourcePolicyRevision(media: DiscoveryMediaType): String = ""
}
