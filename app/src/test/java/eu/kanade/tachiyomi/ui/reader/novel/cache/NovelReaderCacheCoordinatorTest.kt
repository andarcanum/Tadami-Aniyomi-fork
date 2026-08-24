package eu.kanade.tachiyomi.ui.reader.novel.cache

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelReaderCacheCoordinatorTest {

    private val globalBudgetBytes = 32L * 1024 * 1024 // 32 MB

    /**
     * Enforcement is intentionally asynchronous in production (disk walks must not run on the
     * caller's thread - ANR fix). Tests inject a direct executor and a fixed clock so every
     * assertion is deterministic: no sleeps, no races, stable registration order.
     */
    private fun coordinator(maxTotalBytes: Long, now: Long = 1_000L) =
        NovelReaderCacheCoordinator(
            maxTotalBytes = maxTotalBytes,
            nowMs = { now },
            budgetExecutor = { it.run() },
        )

    @Test
    fun `tracks total bytes across registered caches`() {
        val coordinator = coordinator(maxTotalBytes = globalBudgetBytes)
        val cacheA = FakeSizeReportingCache("a", 1_000_000L)
        val cacheB = FakeSizeReportingCache("b", 2_000_000L)

        coordinator.register(cacheA)
        coordinator.register(cacheB)

        coordinator.totalBytes() shouldBe 3_000_000L
    }

    @Test
    fun `trims oldest caches when global budget exceeded`() {
        val coordinator = coordinator(maxTotalBytes = 5_000_000L)
        val cacheA = FakeSizeReportingCache("a", 3_000_000L) // older
        val cacheB = FakeSizeReportingCache("b", 3_000_000L) // newer

        coordinator.register(cacheA)
        coordinator.register(cacheB)

        // total 6MB > 5MB budget => trimmed oldest (cacheA) down to exactly the budget.
        coordinator.totalBytes() shouldBe 5_000_000L
        cacheA.wasTrimmed shouldBe true
        cacheB.wasTrimmed shouldBe false
    }

    @Test
    fun `registration order breaks equal timestamp ties`() {
        // Both caches register at the same fixed millisecond; the sequence counter must keep
        // "a" as the oldest regardless of wall-clock granularity.
        val coordinator = coordinator(maxTotalBytes = 5_000_000L, now = 777L)
        val cacheA = FakeSizeReportingCache("a", 3_000_000L)
        val cacheB = FakeSizeReportingCache("b", 3_000_000L)

        coordinator.register(cacheA)
        coordinator.register(cacheB)

        cacheA.wasTrimmed shouldBe true
        cacheB.wasTrimmed shouldBe false
    }

    @Test
    fun `does not trim when under budget`() {
        val coordinator = coordinator(maxTotalBytes = 10_000_000L)
        val cacheA = FakeSizeReportingCache("a", 3_000_000L)
        val cacheB = FakeSizeReportingCache("b", 2_000_000L)

        coordinator.register(cacheA)
        coordinator.register(cacheB)

        cacheA.wasTrimmed shouldBe false
        cacheB.wasTrimmed shouldBe false
    }

    @Test
    fun `unregister removes cache from tracking`() {
        val coordinator = coordinator(maxTotalBytes = globalBudgetBytes)
        val cacheA = FakeSizeReportingCache("a", 5_000_000L)
        val cacheB = FakeSizeReportingCache("b", 2_000_000L)

        coordinator.register(cacheA)
        coordinator.register(cacheB)
        coordinator.unregister("a")

        coordinator.totalBytes() shouldBe 2_000_000L
    }

    @Test
    fun `dispose trims all registered caches`() {
        val coordinator = coordinator(maxTotalBytes = globalBudgetBytes)
        val cacheA = FakeSizeReportingCache("a", 3_000_000L)
        val cacheB = FakeSizeReportingCache("b", 2_000_000L)
        coordinator.register(cacheA)
        coordinator.register(cacheB)

        coordinator.dispose()

        cacheA.wasDisposed shouldBe true
        cacheB.wasDisposed shouldBe true
    }
}

private class FakeSizeReportingCache(
    val name: String,
    initialBytes: Long,
) : NovelReaderCacheReporter {
    var wasTrimmed = false
        private set
    var wasDisposed = false
        private set
    private var currentBytesValue = initialBytes

    override fun cacheId(): String = name
    override fun currentBytes(): Long = currentBytesValue
    override fun trimToTargetBytes(targetBytes: Long) {
        wasTrimmed = true
        if (targetBytes < currentBytesValue) {
            currentBytesValue = targetBytes
        }
    }
    override fun dispose() {
        wasDisposed = true
    }
}
