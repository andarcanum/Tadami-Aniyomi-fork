package eu.kanade.tachiyomi.data.translation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TranslationQueueManager(
    private val handler: NovelDatabaseHandler = Injekt.get(),
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _queue = MutableStateFlow<List<TranslationQueueItem>>(emptyList())
    val queue: StateFlow<List<TranslationQueueItem>> = _queue.asStateFlow()

    private val _activeTranslation = MutableStateFlow<TranslationQueueItem?>(null)
    val activeTranslation: StateFlow<TranslationQueueItem?> = _activeTranslation.asStateFlow()

    private val _progressUpdates = MutableSharedFlow<TranslationProgressUpdate>()
    val progressUpdates: SharedFlow<TranslationProgressUpdate> = _progressUpdates.asSharedFlow()

    init {
        loadQueue()
    }

    private fun loadQueue() {
        scope.launch {
            try {
                val items = handler.awaitList { db ->
                    db.translation_queueQueries.getPending { id, chapterId, novelId, status, progress, errorMessage, createdAt, updatedAt, retryCount ->
                        TranslationQueueItem(
                            id = id,
                            chapterId = chapterId,
                            novelId = novelId,
                            status = TranslationStatus.entries[status.toInt()],
                            progress = progress.toInt(),
                            errorMessage = errorMessage,
                            retryCount = retryCount.toInt(),
                            createdAt = createdAt,
                            updatedAt = updatedAt,
                        )
                    }
                }
                _queue.value = items
                logcat(LogPriority.DEBUG) { "Loaded ${items.size} items from translation queue" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to load translation queue: ${e.message}" }
            }
        }
    }

    fun addToQueue(chapterIds: List<Long>, novelId: Long) {
        scope.launch {
            try {
                val currentTime = System.currentTimeMillis()
                chapterIds.forEach { chapterId ->
                    handler.await { db ->
                        db.translation_queueQueries.insert(
                            chapterId = chapterId,
                            novelId = novelId,
                            createdAt = currentTime,
                        )
                    }
                }
                refreshQueue()
                logcat(LogPriority.DEBUG) { "Added ${chapterIds.size} chapters to translation queue" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to add chapters to queue: ${e.message}" }
            }
        }
    }

    fun removeFromQueue(chapterId: Long) {
        scope.launch {
            try {
                handler.await { db ->
                    db.translation_queueQueries.delete(chapterId)
                }
                refreshQueue()
                logcat(LogPriority.DEBUG) { "Removed chapter $chapterId from translation queue" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to remove chapter from queue: ${e.message}" }
            }
        }
    }

    suspend fun getNextPending(): TranslationQueueItem? {
        return try {
            handler.awaitOneOrNull { db ->
                db.translation_queueQueries.getNextPending { id, chapterId, novelId, status, progress, errorMessage, createdAt, updatedAt, retryCount ->
                    TranslationQueueItem(
                        id = id,
                        chapterId = chapterId,
                        novelId = novelId,
                        status = TranslationStatus.entries[status.toInt()],
                        progress = progress.toInt(),
                        errorMessage = errorMessage,
                        retryCount = retryCount.toInt(),
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                    )
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to get next pending item: ${e.message}" }
            null
        }
    }

    fun updateStatus(chapterId: Long, status: TranslationStatus) {
        scope.launch {
            try {
                handler.await { db ->
                    db.translation_queueQueries.updateStatus(
                        chapterId = chapterId,
                        status = status.ordinal.toLong(),
                        updatedAt = System.currentTimeMillis(),
                    )
                }
                refreshQueue()
                logcat(LogPriority.DEBUG) { "Updated chapter $chapterId status to $status" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to update chapter status: ${e.message}" }
            }
        }
    }

    fun updateProgress(chapterId: Long, progress: Int, status: TranslationStatus) {
        scope.launch {
            try {
                handler.await { db ->
                    db.translation_queueQueries.updateStatusAndProgress(
                        chapterId = chapterId,
                        status = status.ordinal.toLong(),
                        progress = progress.toLong(),
                        updatedAt = System.currentTimeMillis(),
                    )
                }
                refreshQueue()

                val item = _queue.value.find { it.chapterId == chapterId }
                if (item != null) {
                    _progressUpdates.emit(
                        TranslationProgressUpdate(
                            chapterId = chapterId,
                            novelId = item.novelId,
                            status = status,
                            progress = progress,
                            currentChunk = 0,
                            totalChunks = 0,
                            chapterName = "",
                            errorMessage = null,
                        ),
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to update chapter progress: ${e.message}" }
            }
        }
    }

    fun setError(chapterId: Long, error: String) {
        scope.launch {
            try {
                handler.await { db ->
                    db.translation_queueQueries.setError(
                        chapterId = chapterId,
                        error = error,
                        updatedAt = System.currentTimeMillis(),
                    )
                }
                refreshQueue()
                logcat(LogPriority.DEBUG) { "Set error for chapter $chapterId: $error" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to set chapter error: ${e.message}" }
            }
        }
    }

    suspend fun hasNext(): Boolean {
        return try {
            handler.awaitOneOrNull { db ->
                db.translation_queueQueries.getNextPending { _, _, _, _, _, _, _, _, _ -> }
            } != null
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to check for next item: ${e.message}" }
            false
        }
    }

    fun setActiveTranslation(item: TranslationQueueItem?) {
        _activeTranslation.value = item
    }

    fun incrementRetry(chapterId: Long) {
        scope.launch {
            try {
                handler.await { db ->
                    db.translation_queueQueries.incrementRetry(
                        chapterId = chapterId,
                        updatedAt = System.currentTimeMillis(),
                    )
                }
                refreshQueue()
                logcat(LogPriority.DEBUG) { "Incremented retry count for chapter $chapterId" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to increment retry count: ${e.message}" }
            }
        }
    }

    fun clearCompleted() {
        scope.launch {
            try {
                handler.await { db ->
                    db.translation_queueQueries.deleteCompleted()
                }
                refreshQueue()
                logcat(LogPriority.DEBUG) { "Cleared completed translations from queue" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to clear completed translations: ${e.message}" }
            }
        }
    }

    private suspend fun refreshQueue() {
        try {
            val items = handler.awaitList { db ->
                db.translation_queueQueries.getPending { id, chapterId, novelId, status, progress, errorMessage, createdAt, updatedAt, retryCount ->
                    TranslationQueueItem(
                        id = id,
                        chapterId = chapterId,
                        novelId = novelId,
                        status = TranslationStatus.entries[status.toInt()],
                        progress = progress.toInt(),
                        errorMessage = errorMessage,
                        retryCount = retryCount.toInt(),
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                    )
                }
            }
            _queue.value = items
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to refresh queue: ${e.message}" }
        }
    }
}
