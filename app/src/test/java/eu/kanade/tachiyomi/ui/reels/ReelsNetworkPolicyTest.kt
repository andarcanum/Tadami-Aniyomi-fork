package eu.kanade.tachiyomi.ui.reels

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReelsNetworkPolicyTest {

    @Test
    fun `data saver follows cost, not the Wi-Fi transport`() {
        // Wi-Fi + metered (hotspot): data saver must still force SD.
        reelsForcesSd(dataSaverMetered = true, metered = true) shouldBe true
        // Wi-Fi + unmetered: HD is allowed.
        reelsForcesSd(dataSaverMetered = true, metered = false) shouldBe false
        // Ethernet + unmetered: HD is allowed.
        reelsForcesSd(dataSaverMetered = true, metered = false) shouldBe false
        // Cellular + metered: SD.
        reelsForcesSd(dataSaverMetered = true, metered = true) shouldBe true
        // Data saver off: the quality preference wins on every network.
        reelsForcesSd(dataSaverMetered = false, metered = true) shouldBe false
    }

    @Test
    fun `preload wifi-only still means the Wi-Fi transport`() {
        reelsPreloadAllowed(preloadEnabled = true, preloadWifiOnly = true, onWifi = true) shouldBe true
        reelsPreloadAllowed(preloadEnabled = true, preloadWifiOnly = true, onWifi = false) shouldBe false
        reelsPreloadAllowed(preloadEnabled = true, preloadWifiOnly = false, onWifi = false) shouldBe true
        reelsPreloadAllowed(preloadEnabled = false, preloadWifiOnly = false, onWifi = true) shouldBe false
    }

    @Test
    fun `unknown or lost network is metered`() {
        reelsNetworkStatus(onWifi = false, unmetered = false) shouldBe
            ReelsNetworkStatus(onWifi = false, metered = true)
        reelsNetworkStatus(onWifi = true, unmetered = false) shouldBe
            ReelsNetworkStatus(onWifi = true, metered = true)
        reelsNetworkStatus(onWifi = false, unmetered = true) shouldBe
            ReelsNetworkStatus(onWifi = false, metered = false)
    }
}
