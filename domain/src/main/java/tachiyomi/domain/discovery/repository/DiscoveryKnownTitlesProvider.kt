package tachiyomi.domain.discovery.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryTitleIdentity

/** Live snapshot of titles known through the native library and history databases. */
interface DiscoveryKnownTitlesProvider {
    suspend fun snapshot(media: DiscoveryMediaType): Set<DiscoveryTitleIdentity>
    fun observe(media: DiscoveryMediaType): Flow<Set<DiscoveryTitleIdentity>>
}
