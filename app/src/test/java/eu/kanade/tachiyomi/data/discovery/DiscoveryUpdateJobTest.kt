package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType

class DiscoveryUpdateJobTest {

    @Test
    fun `manual work name is per media type to avoid REPLACE races`() {
        DiscoveryUpdateJob.manualWorkName(DiscoveryMediaType.ANIME) shouldBe "DiscoveryUpdateManual:anime"
        DiscoveryUpdateJob.manualWorkName(DiscoveryMediaType.MANGA) shouldBe "DiscoveryUpdateManual:manga"
        DiscoveryUpdateJob.manualWorkName(DiscoveryMediaType.NOVEL) shouldBe "DiscoveryUpdateManual:novel"
    }

    @Test
    fun `manual work name without media type falls back to shared tag name`() {
        DiscoveryUpdateJob.manualWorkName(null) shouldBe DiscoveryUpdateJob.TAG_MANUAL
    }

    @Test
    fun `refill work name formats work name per media type`() {
        DiscoveryUpdateJob.refillWorkName(DiscoveryMediaType.ANIME) shouldBe "DiscoveryRefillAfterConsumed:anime"
        DiscoveryUpdateJob.refillWorkName(DiscoveryMediaType.MANGA) shouldBe "DiscoveryRefillAfterConsumed:manga"
        DiscoveryUpdateJob.refillWorkName(DiscoveryMediaType.NOVEL) shouldBe "DiscoveryRefillAfterConsumed:novel"
    }
}
