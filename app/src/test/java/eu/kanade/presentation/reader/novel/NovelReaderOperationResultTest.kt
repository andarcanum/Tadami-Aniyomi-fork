package eu.kanade.presentation.reader.novel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NovelReaderOperationResultTest {
    @Test
    fun `successful operation returns its value`() = runTest {
        assertEquals(42, runNovelReaderOperation { 42 }.getOrThrow())
    }

    @Test
    fun `ordinary failure does not execute success acknowledgement`() = runTest {
        var acknowledged = false
        val failure = IllegalStateException("renderer failed")
        val result = runNovelReaderOperation<Int> { throw failure }.onSuccess { acknowledged = true }
        assertSame(failure, result.exceptionOrNull())
        assertTrue(!acknowledged)
    }

    @Test
    fun `cancellation is rethrown and never acknowledged`() = runTest {
        val cancelled = CancellationException("superseded")
        var acknowledged = false
        try {
            runNovelReaderOperation<Int> { throw cancelled }.onSuccess { acknowledged = true }
            error("Cancellation was swallowed")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
        }
        assertTrue(!acknowledged)
    }
}
