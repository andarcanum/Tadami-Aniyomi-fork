package eu.kanade.tachiyomi.data.suggestions

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SuggestionSearchGateTest {

    @Test
    fun `fourth concurrent search waits for a free permit`() = runTest {
        val entered = Channel<Int>(Channel.UNLIMITED)
        val release = CompletableDeferred<Unit>()
        val jobs = (1..4).map { index ->
            launch {
                SuggestionSearchGate.withPermit {
                    entered.send(index)
                    release.await()
                }
            }
        }

        assertEquals(1, entered.receive())
        assertEquals(2, entered.receive())
        assertEquals(3, entered.receive())
        // Three permits are taken, so the fourth caller must stay outside the gate.
        assertNull(withTimeoutOrNull(100) { entered.receive() })

        release.complete(Unit)
        assertEquals(4, entered.receive())
        jobs.forEach { it.join() }
    }
}
