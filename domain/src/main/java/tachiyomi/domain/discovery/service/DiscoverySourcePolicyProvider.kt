package tachiyomi.domain.discovery.service

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoverySourcePolicy

interface DiscoverySourcePolicyProvider {
    /** Reads live preference choices synchronously without initializing managers or reading a database. */
    fun current(mediaType: DiscoveryMediaType): DiscoverySourcePolicy

    fun observe(mediaType: DiscoveryMediaType): Flow<DiscoverySourcePolicy>

    /** Caller owns the deadline. Completion permits an empty inventory; cancellation propagates. */
    suspend fun awaitComplete(mediaType: DiscoveryMediaType): DiscoverySourcePolicy
}
