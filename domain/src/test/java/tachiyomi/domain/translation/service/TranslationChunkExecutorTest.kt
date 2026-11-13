package tachiyomi.domain.translation.service

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.translation.model.TranslationAttemptException
import tachiyomi.domain.translation.model.TranslationFailure
import tachiyomi.domain.translation.model.TranslationFailureKind

class TranslationChunkExecutorTest {
    @Test
    fun `only failed chunks retry after durable checkpoint reload`() = runTest {
        var now = 0L
        val executor = TranslationChunkExecutor(TranslationRetryPolicy(jitter = { 1.0 }), { now })
        val calls = mutableListOf<List<String>>()
        val original = TranslationFragmentPlanner.plan(listOf("One", "Two", "Three"))
        val first = executor.execute(original, 1, 100, 1, request = { texts ->
            calls += texts
            if (texts == listOf("One")) {
                listOf("Первый")
            } else {
                throw TranslationAttemptException(
                    TranslationFailure(TranslationFailureKind.TRANSIENT, "HTTP 503", httpStatus = 503),
                )
            }
        })
        first.state shouldBe TranslationPassState.WAITING
        TranslationFragmentPlanner.assemble(first.fragments) shouldBe mapOf(0 to "Первый")
        calls.size shouldBe 3
        now = requireNotNull(first.nextAttemptAt)
        val second = executor.execute(first.fragments, 1, 100, 1, request = { texts ->
            calls += texts
            listOf(if (texts == listOf("Two")) "Второй" else "Третий")
        })
        second.state shouldBe TranslationPassState.COMPLETE
        calls.count { it == listOf("One") } shouldBe 1
        TranslationFragmentPlanner.assemble(second.fragments) shouldBe
            mapOf(0 to "Первый", 1 to "Второй", 2 to "Третий")
        TranslationFragmentPlanner.progress(second.fragments) shouldBe 99
    }

    @Test
    fun `partial response preserves valid block and repairs only missing block`() = runTest {
        var now = 0L
        val executor = TranslationChunkExecutor(TranslationRetryPolicy(jitter = { 1.0 }), { now })
        val first = executor.execute(
            TranslationFragmentPlanner.plan(listOf("One", "Two")),
            2,
            100,
            1,
            request = { listOf("Первый", null) },
        )
        first.state shouldBe TranslationPassState.WAITING
        now = requireNotNull(first.nextAttemptAt)
        val calls = mutableListOf<List<String>>()
        val second = executor.execute(first.fragments, 2, 100, 1, request = {
            calls += it
            listOf("Второй")
        })
        calls shouldBe listOf(listOf("Two"))
        second.state shouldBe TranslationPassState.COMPLETE
    }

    @Test
    fun `permanent error stops new chunks and cannot be retried automatically`() = runTest {
        var calls = 0
        val result = TranslationChunkExecutor().execute(
            TranslationFragmentPlanner.plan(listOf("One", "Two")),
            1,
            100,
            1,
            request = {
                calls++
                throw TranslationAttemptException(TranslationFailure(TranslationFailureKind.AUTHORIZATION, "HTTP 401"))
            },
        )
        result.state shouldBe TranslationPassState.FAILED
        calls shouldBe 1
    }

    @Test
    fun `pause prevents new requests and cancellation is not converted to failure`() = runTest {
        val source = TranslationFragmentPlanner.plan(listOf("One"))
        var calls = 0
        TranslationChunkExecutor().execute(
            source,
            1,
            100,
            1,
            beforeRequest = { false },
            request = {
                calls++
                listOf("Result")
            },
        ).state shouldBe TranslationPassState.PAUSED
        calls shouldBe 0
        val cancellation = runCatching {
            TranslationChunkExecutor().execute(
                source,
                1,
                100,
                1,
                request = { throw CancellationException("Cancelled") },
            )
        }.exceptionOrNull()
        (cancellation is CancellationException) shouldBe true
    }

    @Test
    fun `checkpoint failure prevents completion and does not disappear`() = runTest {
        val error = runCatching {
            TranslationChunkExecutor().execute(
                TranslationFragmentPlanner.plan(listOf("One")),
                1,
                100,
                1,
                request = { listOf("Result") },
                checkpoint = { error("Storage failed") },
            )
        }.exceptionOrNull()
        error?.message shouldBe "Storage failed"
    }
}
