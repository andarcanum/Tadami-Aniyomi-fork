package tachiyomi.domain.translation.model

/** Ordinals 0..4 are the legacy queue contract. New states must only be appended. */
enum class TranslationRunStatus {
    PENDING,
    RUNNING,
    COMPLETE,
    FAILED,
    CANCELLED,
    WAITING,
    PAUSED,
    ;

    val isTerminal: Boolean get() = this == COMPLETE || this == FAILED || this == CANCELLED
}

/** A current chapter run. Every mutating operation is fenced by both [id] and [generation]. */
data class TranslationRun(
    val id: Long,
    val chapterId: Long,
    val novelId: Long,
    val batchToken: String,
    val batchOrder: Int,
    val profileSnapshotJson: String?,
    val status: TranslationRunStatus,
    val progress: Int,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val retryCount: Int,
    val generation: Long = 0,
    val nextAttemptAt: Long = 0,
    val sourceFingerprint: String? = null,
    val profileFingerprint: String? = null,
    val sourceSegmentCount: Int = 0,
    val forceRetranslate: Boolean = false,
    val priority: Int = 1,
    val implicitOwner: Boolean = true,
    val sessionOnly: Boolean = false,
    val implicitPaused: Boolean = false,
    val actionPaused: Boolean = false,
    val actionPauseReason: TranslationActionPauseReason? = null,
) {
    // Frozen snapshots and provider errors must not accidentally enter diagnostic logs.
    override fun toString(): String =
        "TranslationRun(id=$id, chapterId=$chapterId, generation=$generation, status=$status, progress=$progress)"
}

/** Explicit user action is required; these reasons are data, never retry message classifiers. */
enum class TranslationActionPauseReason { NEEDS_ACTION, BUDGET_LIMIT, CONFIGURATION }

/** Matches the existing persisted batch ordinals. */
enum class TranslationBatchStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class TranslationBatch(
    val batchToken: String,
    val novelId: Long,
    val status: TranslationBatchStatus,
    val total: Int,
    val enqueued: Int,
    val skipped: Int,
    val completed: Int,
    val failed: Int,
    val lastSuccessfulChapterId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val cancelled: Int = 0,
)

/** Facts from an externally verified full artifact; no source/output payload or credential. */
data class TranslationArtifactReceipt(
    val sourceFingerprint: String,
    val profileFingerprint: String,
    val sourceSegmentCount: Int,
)

data class TranslationEnqueueResult(
    val batchToken: String,
    val insertedChapterIds: List<Long>,
    val joinedChapterIds: List<Long>,
    val conflictingChapterIds: List<Long>,
)
