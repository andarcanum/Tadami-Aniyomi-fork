package tachiyomi.domain.translation.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.translation.model.TranslationFailure
import tachiyomi.domain.translation.model.TranslationFailureKind

class TranslationRetryPolicyTest {
    private val policy = TranslationRetryPolicy(jitter = { 1.0 })

    @Test
    fun `retry is bounded by attempts and recovery deadline`() {
        val error = TranslationFailure(TranslationFailureKind.TRANSIENT, "503")
        policy.nextAttemptAt(error, 1, 0, 0) shouldBe 2_000L
        policy.nextAttemptAt(error, 7, 0, 10_000) shouldBe 70_000L
        policy.nextAttemptAt(error, 8, 0, 10_000) shouldBe null
        policy.nextAttemptAt(error, 1, 0, 600_000) shouldBe null
    }

    @Test
    fun `server retry minimum is never clamped down by local backoff`() {
        val error = TranslationFailure(TranslationFailureKind.RATE_LIMIT, "429", retryAfterAt = 300_000)
        policy.nextAttemptAt(error, 1, 0, 0) shouldBe 300_000L
        policy.nextAttemptAt(error.copy(retryAfterAt = 900_000), 1, 0, 0) shouldBe null
    }

    @Test
    fun `waiting before a first HTTP attempt does not consume a recovery deadline`() {
        val waiting =
            TranslationFailure(TranslationFailureKind.WAITING, "cooldown", retryAfterAt = 900_000, attempted = false)
        policy.nextAttemptAt(waiting, 0, 0, 0) shouldBe 900_000L
    }

    @Test
    fun `only transient or bounded repair failures qualify`() {
        listOf(
            TranslationFailureKind.AUTHORIZATION,
            TranslationFailureKind.QUOTA,
            TranslationFailureKind.REFUSED,
            TranslationFailureKind.CONFIGURATION,
            TranslationFailureKind.STORAGE,
        ).forEach {
            policy.nextAttemptAt(TranslationFailure(it, "message"), 1, 0, 0) shouldBe null
        }
        policy.nextAttemptAt(TranslationFailure(TranslationFailureKind.INVALID_RESPONSE, "missing"), 3, 0, 0) shouldBe
            null
    }
}
