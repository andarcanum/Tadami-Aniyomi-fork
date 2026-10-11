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

    @Test
    fun `manual refresh failure always returns failure result and never retries`() {
        resolveWorkResult(isManual = true, runAttemptCount = 0, isSuccess = false) shouldBe
            androidx.work.ListenableWorker.Result.failure()
        resolveWorkResult(isManual = true, runAttemptCount = 5, isSuccess = false) shouldBe
            androidx.work.ListenableWorker.Result.failure()
    }

    @Test
    fun `successful work returns success result`() {
        resolveWorkResult(isManual = true, runAttemptCount = 0, isSuccess = true) shouldBe
            androidx.work.ListenableWorker.Result.success()
        resolveWorkResult(isManual = false, runAttemptCount = 1, isSuccess = true) shouldBe
            androidx.work.ListenableWorker.Result.success()
    }

    @Test
    fun `auto background refresh retries on low attempt count but caps at max attempts`() {
        resolveWorkResult(isManual = false, runAttemptCount = 1, isSuccess = false, maxAutoAttempts = 2) shouldBe
            androidx.work.ListenableWorker.Result.retry()
        resolveWorkResult(isManual = false, runAttemptCount = 2, isSuccess = false, maxAutoAttempts = 2) shouldBe
            androidx.work.ListenableWorker.Result.retry()
        resolveWorkResult(isManual = false, runAttemptCount = 3, isSuccess = false, maxAutoAttempts = 2) shouldBe
            androidx.work.ListenableWorker.Result.failure()
    }
}
