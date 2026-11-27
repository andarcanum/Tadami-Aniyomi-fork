package tachiyomi.domain.translation.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.domain.translation.model.TranslationAttemptException
import tachiyomi.domain.translation.model.TranslationFailure
import tachiyomi.domain.translation.model.TranslationFailureKind
import tachiyomi.domain.translation.model.TranslationFragment
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** A pass performs only due attempts. Waiting is returned to the durable runner, never slept here. */
class TranslationChunkExecutor(
    private val retryPolicy: TranslationRetryPolicy = TranslationRetryPolicy(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val nextAttemptAt: (TranslationFailure, Int, Long, Long) -> Long? = retryPolicy::nextAttemptAt,
) {
    suspend fun execute(
        fragments: List<TranslationFragment>,
        maxBatchSize: Int,
        maxRequestChars: Int,
        concurrency: Int,
        beforeRequest: suspend () -> Boolean = { true },
        request: suspend (List<String>) -> List<String?>?,
        checkpoint: suspend (List<TranslationFragment>) -> Unit = {},
        requestFragments: (suspend (List<TranslationFragment>) -> List<String?>?)? = null,
    ): TranslationPassResult {
        require(fragments.map { it.id }.distinct().size == fragments.size)
        val updated = fragments.associateBy { it.id }.toMutableMap()
        val batches = TranslationFragmentPlanner.batches(fragments, clock(), maxBatchSize, maxRequestChars)
        val next = AtomicInteger(0)
        val fatal = AtomicReference<TranslationFailure?>(null)
        val paused = AtomicBoolean(false)
        val writer = Mutex()
        coroutineScope {
            List(minOf(concurrency.coerceIn(1, 32), batches.size)) {
                async {
                    while (fatal.get() == null && !paused.get()) {
                        val index = next.getAndIncrement()
                        val batch = batches.getOrNull(index) ?: break
                        if (!beforeRequest()) {
                            paused.set(true)
                            break
                        }
                        var failure: TranslationFailure? = null
                        val response = try {
                            if (requestFragments !=
                                null
                            ) {
                                requestFragments(batch)
                            } else {
                                request(batch.map { it.sourceText })
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: TranslationAttemptException) {
                            failure = error.failure
                            null
                        }
                        val validResponse = response?.takeIf { it.size == batch.size }
                        if (failure == null && (validResponse == null || validResponse.any { it.isNullOrBlank() })) {
                            failure = TranslationFailure(
                                TranslationFailureKind.INVALID_RESPONSE,
                                "Provider response has missing or invalid text blocks",
                            )
                        }
                        val now = clock()
                        val changes = batch.mapIndexed { localIndex, fragment ->
                            val text = validResponse?.get(localIndex)?.takeIf { !it.isNullOrBlank() }
                            val attempts = fragment.attempts + if (failure?.attempted != false) 1 else 0
                            if (text != null) {
                                fragment.copy(
                                    translatedText = text,
                                    attempts = attempts,
                                    failure = null,
                                    nextAttemptAt = 0,
                                )
                            } else {
                                val reason = requireNotNull(failure)
                                val started = fragment.firstFailureAt ?: now
                                val due = nextAttemptAt(reason, attempts, started, now)
                                if (due == null) fatal.compareAndSet(null, reason)
                                fragment.copy(
                                    attempts = attempts,
                                    firstFailureAt = if (reason.attempted) started else fragment.firstFailureAt,
                                    nextAttemptAt = due ?: 0,
                                    failure = reason,
                                )
                            }
                        }
                        // Checkpoints are acknowledged in order, so no progress write can outlive finalization.
                        writer.withLock {
                            checkpoint(changes)
                            changes.forEach { updated[it.id] = it }
                        }
                    }
                }
            }.awaitAll()
        }
        val result = updated.values.sortedBy { it.id }
        val state = when {
            result.all { it.isComplete } -> TranslationPassState.COMPLETE
            fatal.get() != null -> TranslationPassState.FAILED
            paused.get() -> TranslationPassState.PAUSED
            else -> TranslationPassState.WAITING
        }
        return TranslationPassResult(
            fragments = result,
            state = state,
            nextAttemptAt = result.filter { !it.isComplete && it.nextAttemptAt > 0 }.minOfOrNull { it.nextAttemptAt },
            failure = fatal.get() ?: result.firstNotNullOfOrNull { it.failure },
        )
    }
}

enum class TranslationPassState { COMPLETE, WAITING, PAUSED, FAILED }

data class TranslationPassResult(
    val fragments: List<TranslationFragment>,
    val state: TranslationPassState,
    val nextAttemptAt: Long?,
    val failure: TranslationFailure?,
)
