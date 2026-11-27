package eu.kanade.tachiyomi.data.library

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LibraryUpdateConcurrencyTest {

    @Test
    fun `standard devices allow 5 concurrent sources`() {
        LibraryUpdateConcurrency.resolveMaxConcurrentSources(
            isLowRamDevice = false,
            maxHeapBytes = 512L * 1024L * 1024L,
        ) shouldBe LibraryUpdateConcurrency.STANDARD_MAX_CONCURRENT_SOURCES
    }

    @Test
    fun `low ram devices cap concurrency at 3`() {
        LibraryUpdateConcurrency.resolveMaxConcurrentSources(
            isLowRamDevice = true,
            maxHeapBytes = 512L * 1024L * 1024L,
        ) shouldBe LibraryUpdateConcurrency.LOW_RAM_MAX_CONCURRENT_SOURCES
    }

    @Test
    fun `small heap devices cap concurrency at 3 even when isLowRamDevice is false`() {
        LibraryUpdateConcurrency.resolveMaxConcurrentSources(
            isLowRamDevice = false,
            maxHeapBytes = 192L * 1024L * 1024L,
        ) shouldBe LibraryUpdateConcurrency.LOW_RAM_MAX_CONCURRENT_SOURCES
    }
}
