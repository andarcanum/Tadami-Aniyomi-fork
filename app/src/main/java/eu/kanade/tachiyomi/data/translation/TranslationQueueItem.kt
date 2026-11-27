package eu.kanade.tachiyomi.data.translation

data class TranslationQueueItem(
    val id: Long,
    val chapterId: Long,
    val novelId: Long,
    val status: TranslationStatus,
    val progress: Int,
    val errorMessage: String?,
    val retryCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val batchToken: String = "",
    val batchOrder: Int = 0,
    val profileSnapshotJson: String? = null,
    val generation: Long = 0,
    val nextAttemptAt: Long = 0,
    val sourceFingerprint: String? = null,
)
