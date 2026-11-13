package tachiyomi.domain.translation.service

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.translation.model.TranslationAttemptException
import tachiyomi.domain.translation.model.TranslationFailure
import tachiyomi.domain.translation.model.TranslationFailureKind
import tachiyomi.domain.translation.profile.ExecutionPolicy
import tachiyomi.domain.translation.profile.ProfileRetryDecision
import tachiyomi.domain.translation.profile.ProfileRetryMode
import tachiyomi.domain.translation.profile.ProfileRetryPolicy

class TranslationProfileExecutorTest {
    @Test
    fun `fragment callback receives exact identities even for duplicate source text`(): Unit = runTest {
        val source = TranslationFragmentPlanner.plan(listOf("Same", "Same"))
        val ids = mutableListOf<Int>()
        val result = TranslationChunkExecutor(clock = { 0 }).execute(
            source,
            1,
            100,
            2,
            request = { error("Legacy callback must not dispatch") },
            requestFragments = { batch ->
                ids += batch.single().id
                listOf("Translated ${batch.single().sourceIndex}")
            },
        )
        ids.toSet() shouldBe source.map { it.id }.toSet()
        result.state shouldBe TranslationPassState.COMPLETE
    }

    @Test
    fun `profile service wait can outlive the bounded legacy window without counting admission wait`(): Unit = runTest {
        val policy = ExecutionPolicy(retryMode = ProfileRetryMode.WAIT_FOR_SERVICE)
        val source = TranslationFragmentPlanner.plan(listOf("One")).map {
            it.copy(attempts = 8, firstFailureAt = 0)
        }
        val result = TranslationChunkExecutor(clock = { 900_000 }, nextAttemptAt = { failure, attempts, started, now ->
            (ProfileRetryPolicy.next(policy, failure, attempts, started, now) as? ProfileRetryDecision.RetryAt)
                ?.notBeforeAt
        }).execute(source, 1, 100, 1, request = {
            throw TranslationAttemptException(
                TranslationFailure(
                    TranslationFailureKind.WAITING,
                    "Shared group is cooling down",
                    retryAfterAt = 1_000_000,
                    attempted = false,
                ),
            )
        })
        result.state shouldBe TranslationPassState.WAITING
        result.fragments.single().attempts shouldBe 8
        result.nextAttemptAt shouldBe 1_500_000
    }
}
