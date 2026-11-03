package tachiyomi.domain.discovery.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DiscoveryRetirementPolicyTest {

    @Test
    fun `blockedUntil returns 48 hours for RETIRE_ALL regardless of seen`() {
        val committedAt = 1_000_000L
        DiscoveryRetirementPolicy.RETIRE_ALL.blockedUntil(committedAt, seen = true) shouldBe
            committedAt + DISCOVERY_RETIRE_MS
        DiscoveryRetirementPolicy.RETIRE_ALL.blockedUntil(committedAt, seen = false) shouldBe
            committedAt + DISCOVERY_RETIRE_MS
    }

    @Test
    fun `blockedUntil returns 48 hours for SEEN_ONLY when seen and null when unseen`() {
        val committedAt = 1_000_000L
        DiscoveryRetirementPolicy.SEEN_ONLY.blockedUntil(committedAt, seen = true) shouldBe
            committedAt + DISCOVERY_RETIRE_MS
        DiscoveryRetirementPolicy.SEEN_ONLY.blockedUntil(committedAt, seen = false) shouldBe null
    }
}
