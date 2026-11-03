package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import org.junit.Test

class DiscoveryTopUpPolicyTest {

    @Test
    fun `topUpNeed returns expected shortage or zero`() {
        topUpNeed(47) shouldBe 13
        topUpNeed(48) shouldBe 0
        topUpNeed(0) shouldBe 60
    }

    @Test
    fun `shouldAttemptTopUp throttles repeated zeroes and permits progress`() {
        val now = 10_000_000L
        val backoff = DISCOVERY_TOP_UP_BACKOFF_MS

        // Added 0, elapsed < 6h -> false
        shouldAttemptTopUp(now = now, lastAttemptAt = now - 1000L, lastAdded = 0) shouldBe false

        // Added 0, elapsed >= 6h -> true
        shouldAttemptTopUp(now = now, lastAttemptAt = now - backoff, lastAdded = 0) shouldBe true

        // Added > 0 -> true immediately
        shouldAttemptTopUp(now = now, lastAttemptAt = now - 1000L, lastAdded = 5) shouldBe true

        // First attempt (lastAttemptAt == 0L) -> true
        shouldAttemptTopUp(now = now, lastAttemptAt = 0L, lastAdded = 0) shouldBe true
    }
}
