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
)
