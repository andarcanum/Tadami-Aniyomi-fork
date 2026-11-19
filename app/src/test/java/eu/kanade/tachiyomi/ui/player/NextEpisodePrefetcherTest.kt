package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.animesource.model.Hoster
import io.kotest.matchers.shouldBe
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
}
