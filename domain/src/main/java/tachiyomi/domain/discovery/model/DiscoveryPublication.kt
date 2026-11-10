package tachiyomi.domain.discovery.model

import kotlinx.serialization.Serializable

/** Live exclusions captured before a generation is prepared. */
data class DiscoveryEligibilitySnapshot(
    val mediaType: DiscoveryMediaType,
    val capturedAt: Long,
    val excludedIdentityKeys: Set<String> = emptySet(),
    val excludedLookupKeys: Set<String> = emptySet(),
)

enum class DiscoveryTraversalState {
    VALID,
    CORRUPT,
}

@Serializable
data class DiscoverySourceCursor(
    val sourceId: Long,
    val queryKey: String,
    val nextPage: Int,
    val exhausted: Boolean,
    val lastPageDigest: String? = null,
)

enum class DiscoveryPublicationOutcome {
    READY,
    PARTIAL,
    EXHAUSTED,
}

data class DiscoveryGenerationToken(
    val mediaType: DiscoveryMediaType,
    val requestId: String,
    val ownerRevision: Long,
    val expectedGeneration: Long,
    val configurationKey: String,
)

data class DiscoveryPublicationSnapshot(
    val mediaType: DiscoveryMediaType,
    val generation: Long,
    val projectionVersion: Long,
    val configurationKey: String,
    val desiredConfigurationKey: String,
    val publishedByRequestId: String?,
    val committedAt: Long,
    val outcome: DiscoveryPublicationOutcome?,
    /** Every stored raw published row, including rows filtered by the live projection. */
    val suggestions: List<DiscoverySuggestion>,
    /** Live known/policy projection of [suggestions]. */
    val eligibleSuggestions: List<DiscoverySuggestion>,
    val homeBatchKeys: List<String>,
    val homeBatchVersion: Long,
    val issuedKeys: Set<String>,
    val traversal: DiscoveryTraversalState,
    val cursors: List<DiscoverySourceCursor>,
)

data class DiscoveryQueryEvidence(
    val queryKey: String,
    val transportOk: Boolean,
    val rawEnd: Boolean,
    val pending: Int,
    val unattempted: Int,
    val metadataFailures: Int,
    val budgetStopped: Boolean,
    val duplicatePageStopped: Boolean,
)

sealed interface DiscoveryPreparedPool {
    data class Fresh(
        val suggestions: List<DiscoverySuggestion>,
        val partial: Boolean,
        val cursors: List<DiscoverySourceCursor> = emptyList(),
    ) : DiscoveryPreparedPool

    data class VerifiedEmpty(
        val expectedQueryKeys: Set<String>,
        val evidence: List<DiscoveryQueryEvidence>,
        val cursors: List<DiscoverySourceCursor> = emptyList(),
    ) : DiscoveryPreparedPool
}

sealed interface DiscoveryPublishReceipt {
    data class Published(val snapshot: DiscoveryPublicationSnapshot) : DiscoveryPublishReceipt

    data class KeptExisting(val accepted: Int, val oldEligible: Int) : DiscoveryPublishReceipt

    data class Superseded(val generation: Long) : DiscoveryPublishReceipt

    data object NoAdmissibleItems : DiscoveryPublishReceipt
}

sealed interface DiscoveryBatchReceipt {
    data class Available(val snapshot: DiscoveryPublicationSnapshot) : DiscoveryBatchReceipt

    data class NeedsSourceRefresh(val generation: Long) : DiscoveryBatchReceipt

    data class Superseded(val generation: Long) : DiscoveryBatchReceipt
}

enum class DiscoveryRetirementPolicy { RETIRE_ALL, SEEN_ONLY }

const val DISCOVERY_RETIRE_MS = 48 * 3_600_000L

fun DiscoveryRetirementPolicy.blockedUntil(committedAt: Long, seen: Boolean): Long? = when (this) {
    DiscoveryRetirementPolicy.RETIRE_ALL -> committedAt + DISCOVERY_RETIRE_MS
    DiscoveryRetirementPolicy.SEEN_ONLY -> if (seen) committedAt + DISCOVERY_RETIRE_MS else null
}

sealed interface DiscoveryTopUpReceipt {
    data class ToppedUp(
        val added: Int,
        val eligibleAfter: Int,
        val snapshot: DiscoveryPublicationSnapshot,
    ) : DiscoveryTopUpReceipt
    data class Superseded(val generation: Long) : DiscoveryTopUpReceipt
    data object Busy : DiscoveryTopUpReceipt
    data object NothingToAdd : DiscoveryTopUpReceipt
}
