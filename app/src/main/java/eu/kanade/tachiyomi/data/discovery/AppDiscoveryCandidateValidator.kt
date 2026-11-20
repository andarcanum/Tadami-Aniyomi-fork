package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.repository.DiscoveryCandidateValidator
import tachiyomi.domain.discovery.service.DiscoverySourcePolicyProvider
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Final admission check used by the repository inside the publication
 * transaction. Reads the current genre policy from preferences so a snapshot
 * prepared with stale settings cannot publish incompatible candidates.
 */
class AppDiscoveryCandidateValidator(
    private val preferences: DiscoveryPreferences,
    private val sourcePolicyProvider: DiscoverySourcePolicyProvider,
    private val sourcePreferencesProvider: () -> SourcePreferences = { Injekt.get() },
) : DiscoveryCandidateValidator {

    override fun isConfigurationCurrent(media: DiscoveryMediaType, capturedKey: String): Boolean =
        runCatchingUnlessCancelled {
            computeDiscoveryConfigurationKey(
                preferences,
                media,
                sourcePreferencesProvider(),
                sourcePolicyProvider.current(media).revision,
            ) == capturedKey
        }.getOrDefault(false)

    override fun isAllowed(candidate: DiscoverySuggestion, liveBlacklistedTags: Set<String>): Boolean =
        isAllowedForRead(candidate, liveBlacklistedTags, allowProvisionalNative = false)

    override fun isAllowedForRead(
        candidate: DiscoverySuggestion,
        liveBlacklistedTags: Set<String>,
        allowProvisionalNative: Boolean,
    ): Boolean {
        val sourcePolicy = sourcePolicyProvider.current(candidate.mediaType)
        val sourceAllowed = if (
            !sourcePolicy.inventoryComplete &&
            allowProvisionalNative &&
            candidate.sourceId != null
        ) {
            candidate.mediaType == sourcePolicy.mediaType
        } else {
            sourcePolicy.allows(candidate)
        }
        if (!sourceAllowed) return false
        val policy = DiscoveryCandidatePolicy(
            spec = preferences.genreFilterSpec(candidate.mediaType),
            blacklistedTags = liveBlacklistedTags,
        )
        return policy.isAllowed(candidate)
    }

    override fun observeChanges(media: DiscoveryMediaType): Flow<Unit> = merge(
        sourcePolicyProvider.observe(media).map { Unit },
        preferences.observeGenreFilterSpec(media).map { Unit },
    ).onStart { emit(Unit) }

    override fun sourcePolicyRevision(media: DiscoveryMediaType): String =
        sourcePolicyProvider.current(media).revision
}
