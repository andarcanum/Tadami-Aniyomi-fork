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
)
