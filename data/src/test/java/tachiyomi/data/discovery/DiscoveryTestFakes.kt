package tachiyomi.data.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.model.DiscoveryTitleIdentity
import tachiyomi.domain.discovery.repository.DiscoveryCandidateValidator
import tachiyomi.domain.discovery.repository.DiscoveryKnownTitlesProvider

internal class MutableKnownTitlesProvider : DiscoveryKnownTitlesProvider {
    val titles = mutableMapOf<DiscoveryMediaType, Set<DiscoveryTitleIdentity>>()

    override suspend fun snapshot(media: DiscoveryMediaType): Set<DiscoveryTitleIdentity> =
        titles[media].orEmpty()

    override fun observe(media: DiscoveryMediaType): Flow<Set<DiscoveryTitleIdentity>> =
        flowOf(titles[media].orEmpty())
}

internal object AllowAllCandidateValidator : DiscoveryCandidateValidator {
    override fun isAllowed(candidate: DiscoverySuggestion, liveBlacklistedTags: Set<String>): Boolean = true
}
