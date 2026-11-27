package tachiyomi.domain.translation.service

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.translation.model.TranslationAttemptException

class TranslationRequestGateTest {
    @Test
    fun `all consumers respect shared endpoint capacity and release on cancellation`() = runTest {
        val gate = TranslationRequestGate { 0 }
        gate.configure("provider", 2)
        val release = CompletableDeferred<Unit>()
        var active = 0
        var peak = 0
        val requests = List(5) {
            async {
                gate.execute("provider") {
                    active++
                    peak = maxOf(peak, active)
                    try {
                        release.await()
                    } finally {
                        active--
                    }
                }
            }
        }
        runCurrent()
        active shouldBe 2
        requests[0].cancel()
        runCurrent()
        active shouldBe 2
        release.complete(Unit)
        requests.drop(1).awaitAll()
        peak shouldBe 2
        active shouldBe 0
        gate.execute("provider") { "free" } shouldBe "free"
    }

    @Test
    fun `cooldown stops waiting consumers without spending an HTTP attempt`() = runTest {
        var now = 0L
        val gate = TranslationRequestGate { now }
        gate.defer("provider", 300_000)
        var calls = 0
        val error = runCatching {
            gate.execute("provider") { calls++ }
        }.exceptionOrNull() as TranslationAttemptException
        error.failure.attempted shouldBe false
        error.failure.retryAfterAt shouldBe 300_000L
        calls shouldBe 0
        gate.defer("provider", 1_000)
        now = 300_000
        gate.execute("provider") { calls++ }
        calls shouldBe 1
    }
}
