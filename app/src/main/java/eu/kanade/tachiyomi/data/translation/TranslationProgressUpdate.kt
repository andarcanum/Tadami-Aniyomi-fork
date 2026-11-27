package eu.kanade.tachiyomi.data.translation

data class TranslationProgressUpdate(
    val chapterId: Long,
    val novelId: Long,
    val status: TranslationStatus,
    val progress: Int,
    val currentChunk: Int,
    val totalChunks: Int,
    val chapterName: String,
    val errorMessage: String?,
    val logMessage: String? = null,
    val generation: Long = 0,
    val nextAttemptAt: Long = 0,
    val sourceFingerprint: String? = null,
)
