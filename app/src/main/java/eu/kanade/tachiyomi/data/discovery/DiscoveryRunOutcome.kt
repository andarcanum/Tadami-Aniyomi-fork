package eu.kanade.tachiyomi.data.discovery

import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType

enum class DiscoveryRunStatus {
    SUCCESS,
    PARTIAL,
    FAILED,
    SKIPPED,
}

data class DiscoveryRunOutcome(
    val mediaType: DiscoveryMediaType,
    val status: DiscoveryRunStatus,
    val attemptedRows: Set<DiscoveryRowType> = emptySet(),
    val failedRows: Set<DiscoveryRowType> = emptySet(),
    val degradedRows: Set<DiscoveryRowType> = emptySet(),
    val persistedRows: Set<DiscoveryRowType> = emptySet(),
    val cause: Throwable? = null,
) {
    val isSuccess: Boolean get() = status == DiscoveryRunStatus.SUCCESS || status == DiscoveryRunStatus.PARTIAL
    val isFailure: Boolean get() = status == DiscoveryRunStatus.FAILED
    val isSkipped: Boolean get() = status == DiscoveryRunStatus.SKIPPED
}
