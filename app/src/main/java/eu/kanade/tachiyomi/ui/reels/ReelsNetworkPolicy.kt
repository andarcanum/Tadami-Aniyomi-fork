package eu.kanade.tachiyomi.ui.reels

/**
 * Transport and cost are separate decisions: "Wi-Fi only" is about the transport the user
 * chose for preloading, while the data saver is about the network actually charging for
 * traffic (a metered Wi-Fi hotspot is expensive, an unmetered Ethernet is not).
 */
internal data class ReelsNetworkStatus(
    val onWifi: Boolean,
    // No default network is treated as metered: the data saver must not upgrade quality
    // while connectivity is unknown.
    val metered: Boolean,
)

internal fun reelsNetworkStatus(onWifi: Boolean, unmetered: Boolean): ReelsNetworkStatus =
    ReelsNetworkStatus(onWifi = onWifi, metered = !unmetered)

internal fun reelsPreloadAllowed(
    preloadEnabled: Boolean,
    preloadWifiOnly: Boolean,
    onWifi: Boolean,
): Boolean = preloadEnabled && (!preloadWifiOnly || onWifi)

internal fun reelsForcesSd(dataSaverMetered: Boolean, metered: Boolean): Boolean =
    dataSaverMetered && metered
