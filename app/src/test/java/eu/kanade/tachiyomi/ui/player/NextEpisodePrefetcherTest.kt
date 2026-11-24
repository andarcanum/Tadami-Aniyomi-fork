package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.animesource.model.Hoster
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class NextEpisodePrefetcherTest {

    @Test
    fun `prefetch caches hosters once and consumeOrNull returns them once`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var now = 1_000L
        var loaderCalls = 0
        val prefetcher = NextEpisodePrefetcher(
            clockMs = { now },
            ioDispatcher = dispatcher,
            ttlMs = 60_000L,
        )
        val expected = listOf(Hoster(hosterName = "Kodik", videoList = emptyList()))

        prefetcher.prefetchIfNeeded(this, episodeId = 42L) {
            loaderCalls++
            expected
        }
        // Duplicate prefetch while in-flight is ignored
        prefetcher.prefetchIfNeeded(this, episodeId = 42L) {
            loaderCalls++
            expected
        }

        advanceUntilIdle()
        loaderCalls shouldBe 1

        prefetcher.consumeOrNull(42L) shouldBe expected
        // Already consumed
        prefetcher.consumeOrNull(42L) shouldBe null
    }

    @Test
    fun `expired prefetched hosters are discarded`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var now = 1_000L
        val prefetcher = NextEpisodePrefetcher(
            clockMs = { now },
            ioDispatcher = dispatcher,
            ttlMs = 5_000L,
        )
        val hosters = listOf(Hoster(hosterName = "CDN", videoList = emptyList()))

        prefetcher.prefetchIfNeeded(this, episodeId = 7L) { hosters }
        advanceUntilIdle()

        now = 10_000L
        prefetcher.consumeOrNull(7L) shouldBe null
    }

    @Test
    fun `timed out prefetch degrades safely to null`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val prefetcher = NextEpisodePrefetcher(
            ioDispatcher = dispatcher,
            timeoutMs = 50L,
        )

        prefetcher.prefetchIfNeeded(this, episodeId = 99L) {
            delay(5_000L)
            listOf(Hoster(hosterName = "Slow", videoList = emptyList()))
        }

        prefetcher.consumeOrNull(99L) shouldBe null
    }

    @Test
    fun `empty result does not re-invoke loader within failureCooldownMs`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var now = 10_000L
        var loaderCalls = 0
        val prefetcher = NextEpisodePrefetcher(
            clockMs = { now },
            ioDispatcher = dispatcher,
            failureCooldownMs = 120_000L,
        )

        prefetcher.prefetchIfNeeded(this, episodeId = 15L) {
            loaderCalls++
            emptyList()
        }
        advanceUntilIdle()
        loaderCalls shouldBe 1

        now += 30_000L
        prefetcher.prefetchIfNeeded(this, episodeId = 15L) {
            loaderCalls++
            emptyList()
        }
        advanceUntilIdle()
        loaderCalls shouldBe 1
    }

    @Test
    fun `exception and timeout do not re-invoke loader within failureCooldownMs`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var now = 10_000L
        var errorCalls = 0
        var timeoutCalls = 0
        val prefetcher = NextEpisodePrefetcher(
            clockMs = { now },
            ioDispatcher = dispatcher,
            timeoutMs = 50L,
            failureCooldownMs = 120_000L,
        )

        prefetcher.prefetchIfNeeded(this, episodeId = 20L) {
            errorCalls++
            throw IllegalStateException("HTTP 503")
        }
        advanceUntilIdle()
        errorCalls shouldBe 1

        now += 60_000L
        prefetcher.prefetchIfNeeded(this, episodeId = 20L) {
            errorCalls++
            emptyList()
        }
        advanceUntilIdle()
        errorCalls shouldBe 1

        prefetcher.prefetchIfNeeded(this, episodeId = 21L) {
            timeoutCalls++
            delay(5_000L)
            listOf(Hoster(hosterName = "Late", videoList = emptyList()))
        }
        advanceUntilIdle()
        timeoutCalls shouldBe 1

        now += 60_000L
        prefetcher.prefetchIfNeeded(this, episodeId = 21L) {
            timeoutCalls++
            listOf(Hoster(hosterName = "Retry", videoList = emptyList()))
        }
        advanceUntilIdle()
        timeoutCalls shouldBe 1
    }

    @Test
    fun `retries cleanly after failureCooldownMs elapses`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var now = 1_000L
        var loaderCalls = 0
        val expected = listOf(Hoster(hosterName = "Recovered", videoList = emptyList()))
        val prefetcher = NextEpisodePrefetcher(
            clockMs = { now },
            ioDispatcher = dispatcher,
            failureCooldownMs = 120_000L,
        )

        prefetcher.prefetchIfNeeded(this, episodeId = 33L) {
            loaderCalls++
            emptyList()
        }
        advanceUntilIdle()
        loaderCalls shouldBe 1

        now += 119_999L
        prefetcher.prefetchIfNeeded(this, episodeId = 33L) {
            loaderCalls++
            expected
        }
        advanceUntilIdle()
        loaderCalls shouldBe 1

        now += 1L
        prefetcher.prefetchIfNeeded(this, episodeId = 33L) {
            loaderCalls++
            expected
        }
        advanceUntilIdle()
        loaderCalls shouldBe 2
        prefetcher.consumeOrNull(33L) shouldBe expected
    }

    @Test
    fun `cancelInFlight cancels active job while preserving already-cached hosters`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val prefetcher = NextEpisodePrefetcher(
            ioDispatcher = dispatcher,
            failureCooldownMs = 120_000L,
        )
        val cachedHosters = listOf(Hoster(hosterName = "Cached", videoList = emptyList()))

        prefetcher.prefetchIfNeeded(this, episodeId = 50L) { cachedHosters }
        advanceUntilIdle()

        // Cancel in-flight before any new job -> cached result remains intact
        prefetcher.cancelInFlight()

        val started = CompletableDeferred<Unit>()
        var cancelledExternally = false
        prefetcher.prefetchIfNeeded(this, episodeId = 51L) {
            started.complete(Unit)
            try {
                delay(10_000L)
                emptyList()
            } catch (e: CancellationException) {
                cancelledExternally = true
                throw e
            }
        }
        started.await()
        prefetcher.cancelInFlight()
        advanceUntilIdle()

        cancelledExternally shouldBe true
        // Cancelling in-flight episode 51 must preserve previously cached episode 50
        prefetcher.consumeOrNull(50L) shouldBe cachedHosters
        // External cancellation must NOT trigger negative caching cooldown
        var retryCalls = 0
        val ep51Hosters = listOf(Hoster(hosterName = "AfterCancel", videoList = emptyList()))
        prefetcher.prefetchIfNeeded(this, episodeId = 51L) {
            retryCalls++
            ep51Hosters
        }
        advanceUntilIdle()
        retryCalls shouldBe 1
        prefetcher.consumeOrNull(51L) shouldBe ep51Hosters
    }
}
