package eu.kanade.tachiyomi.network.interceptor

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CloudflareBridgeRegistryTest {

    @Test
    fun `novelupdates hosts are not statically bridged without marking`() {
        CloudflareBridgeRegistry.isBridged("www.novelupdates.com") shouldBe false
        CloudflareBridgeRegistry.isBridged("novelupdates.com") shouldBe false
    }

    @Test
    fun `unknown host is not bridged until marked`() {
        val host = "example-bridge-test.com"
        // Ensure isolation from other tests (registry is process-wide).
        CloudflareBridgeRegistry.isBridged(host) shouldBe false
        CloudflareBridgeRegistry.mark(host)
        CloudflareBridgeRegistry.isBridged(host) shouldBe true
    }

    @Test
    fun `fingerprint mismatch never registers a manual solve`() {
        // Regression guard for the reported loop: a clean hidden-WebView load must not show
        // the dead-end "pass captcha in WebView" button. The interceptor marks the bridge
        // registry instead - assert the two registries stay independent here.
        val host = "mismatch-guard-test.com"
        CloudflareBridgeRegistry.mark(host)
        CloudflareBridgeRegistry.isBridged(host) shouldBe true
        CloudflareManualSolveRegistry.isPending(host) shouldBe false
    }
}
