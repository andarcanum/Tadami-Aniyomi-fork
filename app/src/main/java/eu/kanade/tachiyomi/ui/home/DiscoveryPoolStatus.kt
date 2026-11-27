package eu.kanade.tachiyomi.ui.home

internal sealed interface DiscoveryPoolStatus {
    data object Silent : DiscoveryPoolStatus

    data class Underfilled(val pool: Int, val target: Int) : DiscoveryPoolStatus

    data class Unavailable(val count: Int) : DiscoveryPoolStatus

    data class UnderfilledAndUnavailable(
        val pool: Int,
        val target: Int,
        val count: Int,
    ) : DiscoveryPoolStatus
}

internal fun discoveryPoolStatus(
    poolSize: Int,
    failedSources: Int,
    target: Int = 60,
): DiscoveryPoolStatus = when {
    poolSize >= target && failedSources <= 0 -> DiscoveryPoolStatus.Silent
    poolSize >= target -> DiscoveryPoolStatus.Unavailable(failedSources)
    poolSize <= 0 && failedSources <= 0 -> DiscoveryPoolStatus.Silent
    poolSize <= 0 -> DiscoveryPoolStatus.Unavailable(failedSources)
    failedSources <= 0 -> DiscoveryPoolStatus.Underfilled(poolSize, target)
    else -> DiscoveryPoolStatus.UnderfilledAndUnavailable(poolSize, target, failedSources)
}
