package tachiyomi.domain.translation.service

import tachiyomi.domain.translation.model.TranslationFailure
import tachiyomi.domain.translation.model.TranslationFailureKind
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** One retry owner. Its due time can be persisted; this policy never sleeps or performs I/O. */
class TranslationRetryPolicy(
    private val maxAttempts: Int = 8,
    private val repairAttempts: Int = 3,
    private val maxRecoveryMs: Long = 10 * 60_000L,
    private val jitter: () -> Double = { Random.nextDouble() },
) {
    fun nextAttemptAt(
        failure: TranslationFailure,
        attempts: Int,
        firstFailureAt: Long,
        now: Long,
    ): Long? {
        // Admission waiting before the first HTTP call is not a failed attempt or recovery period.
        if (failure.kind == TranslationFailureKind.WAITING && attempts == 0 && !failure.attempted) {
            return max(safeAdd(now, 1_000L), failure.retryAfterAt ?: now)
        }
        val attemptLimit = when (failure.kind) {
            TranslationFailureKind.TRANSIENT,
            TranslationFailureKind.RATE_LIMIT,
            TranslationFailureKind.WAITING,
            -> maxAttempts
            TranslationFailureKind.TRUNCATED,
            TranslationFailureKind.INVALID_RESPONSE,
            -> repairAttempts
            else -> return null
        }
        if (attempts >= attemptLimit) return null
        val deadline = safeAdd(firstFailureAt, maxRecoveryMs)
        if (now >= deadline) return null
        val exponent = (attempts - 1).coerceIn(0, 5)
        val base = min(60_000L, 2_000L * (1L shl exponent))
        // 50..100% jitter avoids synchronized clients without an immediate spin retry.
        val waitMs = (base * (0.5 + jitter().coerceIn(0.0, 1.0) * 0.5)).toLong()
        val next = max(safeAdd(now, waitMs), failure.retryAfterAt ?: 0L)
        return next.takeIf { it < deadline }
    }

    private fun safeAdd(value: Long, delta: Long): Long =
        if (value > Long.MAX_VALUE - delta) Long.MAX_VALUE else value + delta
}
