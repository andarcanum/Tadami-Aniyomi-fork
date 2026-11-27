package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DiscoveryPoolStatusTest {
    @Test
    fun `empty and full pools without failures stay silent`() {
        discoveryPoolStatus(poolSize = 0, failedSources = 0) shouldBe DiscoveryPoolStatus.Silent
        discoveryPoolStatus(poolSize = 60, failedSources = 0) shouldBe DiscoveryPoolStatus.Silent
        discoveryPoolStatus(poolSize = 75, failedSources = 0) shouldBe DiscoveryPoolStatus.Silent
    }

    @Test
    fun `underfilled pool reports its size`() {
        discoveryPoolStatus(poolSize = 24, failedSources = 0) shouldBe
            DiscoveryPoolStatus.Underfilled(pool = 24, target = 60)
    }

    @Test
    fun `unavailable sources are reported even when the pool is full`() {
        discoveryPoolStatus(poolSize = 0, failedSources = 2) shouldBe
            DiscoveryPoolStatus.Unavailable(count = 2)
        discoveryPoolStatus(poolSize = 60, failedSources = 2) shouldBe
            DiscoveryPoolStatus.Unavailable(count = 2)
    }

    @Test
    fun `underfilled pool combines size and unavailable sources`() {
        discoveryPoolStatus(poolSize = 24, failedSources = 2) shouldBe
            DiscoveryPoolStatus.UnderfilledAndUnavailable(pool = 24, target = 60, count = 2)
    }
}
