package tachiyomi.domain.translation.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.translation.model.TranslationActionPauseReason
import tachiyomi.domain.translation.model.TranslationArtifactReceipt
import tachiyomi.domain.translation.model.TranslationBatch
import tachiyomi.domain.translation.model.TranslationEnqueueResult
import tachiyomi.domain.translation.model.TranslationFragment
import tachiyomi.domain.translation.model.TranslationRun
import tachiyomi.domain.translation.model.TranslationRunStatus

/** Durable chapter work, frozen inputs and shared batch ownership behind one transactional interface. */
interface TranslationRunRepository {

    /** Active identical work joins an owner; a different snapshot or force request conflicts. */
    suspend fun enqueue(
        chapterIds: List<Long>,
        novelId: Long,
        profileSnapshotJson: String?,
        profileFingerprint: String,
        batchToken: String,
        forceRetranslate: Boolean,
        priority: Int = 1,
        now: Long,
        sessionOnly: Boolean = false,
    ): TranslationEnqueueResult

    /** Promotes only an exact active prefetch membership; never enqueues or resurrects a finished run. */
    suspend fun promotePrefetchRun(
        chapterId: Long,
        batchToken: String,
        profileSnapshotJson: String,
        profileFingerprint: String,
        now: Long,
    ): Boolean

    /** Claims only due work with at least one runnable owner, skipping paused heads. */
    suspend fun claimNext(now: Long): TranslationRun?

    suspend fun getByChapterId(chapterId: Long): TranslationRun?

    fun observeRuns(novelId: Long? = null): Flow<List<TranslationRun>>

    /** Historical generations remain readable, including after a terminal rerun. */
    suspend fun getFragments(run: TranslationRun): List<TranslationFragment>

    /** Freezes canonical source identity once; session-only work persists metadata, never fragment text. */
    suspend fun prepare(
        run: TranslationRun,
        sourceFingerprint: String,
        profileFingerprint: String,
        sourceSegmentCount: Int,
        fragments: List<TranslationFragment>,
        now: Long,
    ): Boolean

    /** A legacy null snapshot can be frozen once. A non-null snapshot is never replaced. */
    suspend fun freezeProfile(run: TranslationRun, json: String, fingerprint: String, now: Long): TranslationRun?

    /** In-flight replies may checkpoint RUNNING work even while its batch owners are paused. */
    suspend fun checkpoint(run: TranslationRun, changes: List<TranslationFragment>, progress: Int, now: Long): Boolean

    /** Releases RUNNING/PAUSED work to PENDING, WAITING, PAUSED or terminal FAILED. */
    suspend fun release(
        run: TranslationRun,
        status: TranslationRunStatus,
        progress: Int,
        error: String?,
        nextAttemptAt: Long = 0,
        now: Long,
    ): Boolean

    /**
     * Publishes only a current runnable run under the ownership transaction. Durable work requires
     * exact complete fragment coverage; session-only work trusts [publish] to acknowledge a complete
     * in-memory result, because source/output text is deliberately absent from the database.
     * Stale/partial/terminal calls return false without invoking [publish]. Callback failures roll
     * back status and counters and propagate to the caller; duplicate successful calls are no-ops.
     */
    suspend fun publish(run: TranslationRun, now: Long, publish: () -> Unit): Boolean

    /** Caller verifies full artifact coverage/configuration; matching receipt replaces no fragment payload. */
    suspend fun publishCached(
        run: TranslationRun,
        now: Long,
        receipt: TranslationArtifactReceipt,
        publish: () -> Unit,
    ): Boolean

    suspend fun getBatch(batchToken: String): TranslationBatch?

    suspend fun getActiveBatches(): List<TranslationBatch>

    suspend fun pauseBatch(batchToken: String, now: Long): Boolean

    suspend fun resumeBatch(batchToken: String, now: Long): Boolean

    /** Cancels only this membership; shared and implicit reader owners survive. */
    suspend fun cancelBatch(batchToken: String, now: Long): Boolean

    /** Changes only this reader's implicit ownership, not named batch memberships. */
    suspend fun pauseImplicitOwner(chapterId: Long, expectedGeneration: Long, now: Long): Boolean
    suspend fun resumeImplicitOwner(chapterId: Long, expectedGeneration: Long, now: Long): Boolean
    suspend fun detachImplicitOwner(chapterId: Long, expectedGeneration: Long, now: Long): Boolean

    /** Parks this generation without changing owners, policy, checkpoints or charged budget. */
    suspend fun parkForAction(
        chapterId: Long,
        expectedGeneration: Long,
        reason: TranslationActionPauseReason,
        now: Long,
    ): Boolean

    /** Explicit UI resume only; spent budgets remain spent and can park again on the next attempt. */
    suspend fun resumeActionPaused(chapterId: Long, expectedGeneration: Long, now: Long): Boolean

    /** Explicit UI retry preserves the stored intent; stale/detached background retries are forbidden. */
    suspend fun retryOwnedRun(chapterId: Long, expectedGeneration: Long, force: Boolean, now: Long): Boolean

    suspend fun cancelChapter(chapterId: Long, now: Long): Boolean

    suspend fun cancelAll(now: Long): Int

    /** Explicit Clear cache forgets reuse and fences old work without deleting historical payloads. */
    suspend fun invalidateCheckpoint(chapterId: Long, now: Long): Boolean

    suspend fun invalidateAllCheckpoints(now: Long): Int

    suspend fun isRunnable(run: TranslationRun): Boolean

    suspend fun nextWakeAt(): Long?

    /** Process-restart recovery keeps generation, source, fragment checkpoints and progress. */
    suspend fun recoverInterrupted(now: Long): Int
}
