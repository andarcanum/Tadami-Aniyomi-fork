package eu.kanade.tachiyomi.data.library

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The library update jobs hold one of their few semaphore slots per source group. The pacing
 * delay must run OUTSIDE the acquired permit, otherwise a paced source sleeps while holding
 * one of the job's limited slots and starves every other source.
 */
class ProcessEntriesWithPacingTest {

    @Test
    fun `permit is released while pacing runs`() = runTest {
        val semaphore = Semaphore(1)
        var permitFreeDuringPace = false

        processEntriesWithPacing(
            entries = listOf("a", "b"),
            semaphore = semaphore,
            process = { true },
            paceAfter = {
                permitFreeDuringPace = semaphore.tryAcquire()
                semaphore.release()
            },
        )

        permitFreeDuringPace shouldBe true
    }

    @Test
    fun `skipped entry does not trigger pacing`() = runTest {
        val pacedAfter = mutableListOf<String>()

        processEntriesWithPacing(
            entries = listOf("removed-from-library", "kept-1", "kept-2"),
            semaphore = Semaphore(2),
            process = { entry -> entry != "removed-from-library" },
            paceAfter = { pacedAfter += "pace" },
        )

        // Only after kept-1: skipped entry and the last entry are never paced.
        pacedAfter.size shouldBe 1
    }

    @Test
    fun `last entry is never paced`() = runTest {
        val processed = mutableListOf<String>()
        var paceCalls = 0

        processEntriesWithPacing(
            entries = listOf("a", "b", "c"),
            semaphore = Semaphore(3),
            process = { entry ->
                processed += entry
                true
            },
            paceAfter = { paceCalls++ },
        )

        processed shouldContainExactly listOf("a", "b", "c")
        paceCalls shouldBe 2
    }
}
