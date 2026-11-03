package eu.kanade.tachiyomi.data.discovery

internal const val DISCOVERY_POOL_TARGET = 60
internal const val DISCOVERY_POOL_LOW_WATER = 48
internal const val DISCOVERY_TOP_UP_BACKOFF_MS = 6 * 3_600_000L

internal fun topUpNeed(
    eligible: Int,
    target: Int = DISCOVERY_POOL_TARGET,
    lowWater: Int = DISCOVERY_POOL_LOW_WATER,
): Int = if (eligible >= lowWater) 0 else (target - eligible).coerceAtLeast(0)

internal fun shouldAttemptTopUp(now: Long, lastAttemptAt: Long, lastAdded: Int): Boolean =
    lastAdded > 0 || lastAttemptAt == 0L || now - lastAttemptAt >= DISCOVERY_TOP_UP_BACKOFF_MS
