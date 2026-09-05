package eu.kanade.domain.track

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DelayedTrackingStoreKeysTest {

    @Test
    fun `stable keys round-trip`() {
        DelayedTrackingStoreKeys.parse(DelayedTrackingStoreKeys.build(42L, 3L)) shouldBe (42L to 3L)
        DelayedTrackingStoreKeys.parse(DelayedTrackingStoreKeys.build(1L, 1L)) shouldBe (1L to 1L)
    }

    @Test
    fun `legacy bare id keys are rejected`() {
        // The legacy shared "tracking_queue" file keyed entries by the bare track row _id -
        // ambiguous across media by construction (manga_sync._id and anime_sync._id both start
        // at 1). The new stores must never resolve such keys as (entryId, trackerId).
        DelayedTrackingStoreKeys.parse("7") shouldBe null
        DelayedTrackingStoreKeys.parse("123456") shouldBe null
    }

    @Test
    fun `malformed keys are rejected`() {
        DelayedTrackingStoreKeys.parse("") shouldBe null
        DelayedTrackingStoreKeys.parse(":3") shouldBe null
        DelayedTrackingStoreKeys.parse("42:") shouldBe null
        DelayedTrackingStoreKeys.parse("a:b") shouldBe null
        DelayedTrackingStoreKeys.parse("42:3:7") shouldBe null
        DelayedTrackingStoreKeys.parse("-") shouldBe null
    }
}
