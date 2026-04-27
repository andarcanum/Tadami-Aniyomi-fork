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

    private val _progressUpdates = MutableSharedFlow<TranslationProgressUpdate>(extraBufferCapacity = 64)
    val progressUpdates: SharedFlow<TranslationProgressUpdate> = _progressUpdates.asSharedFlow()

    init {
        loadQueue()
    }

    private fun loadQueue() {
        scope.launch {
            try {
                val items = handler.awaitList { db ->
                    db.translation_queueQueries.getPending(::mapQueueItem)
                }
                _queue.value = items
                logcat(LogPriority.DEBUG) { "Loaded ${items.size} items from translation queue" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to load translation queue: ${e.message}" }
            }
        }
    }

    suspend fun addToQueue(chapterIds: List<Long>, novelId: Long): Int {
        val currentTime = System.currentTimeMillis()
        var addedCount = 0
        chapterIds.forEach { chapterId ->
            val existing = getQueueItemByChapterId(chapterId)
            if (existing?.status == TranslationStatus.PENDING || existing?.status == TranslationStatus.IN_PROGRESS) {
                return@forEach
            }
            handler.await { db ->
                db.translation_queueQueries.insert(
                    chapterId = chapterId,
                    novelId = novelId,
                    createdAt = currentTime,
                )
            }
            addedCount++
        }
        refreshQueue()
        logcat(LogPriority.DEBUG) {
            "Added $addedCount/${chapterIds.size} chapters to translation queue"
        }
        return addedCount
    }

    fun removeFromQueue(chapterId: Long) {
        scope.launch {
            try {
                cancelChapter(chapterId)
                logcat(LogPriority.DEBUG) { "Removed chapter $chapterId from translation queue" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to remove chapter from queue: ${e.message}" }
            }
        }
    }

    suspend fun getNextPending(): TranslationQueueItem? {
        return try {
            handler.awaitOneOrNull { db ->
                db.translation_queueQueries.getNextPending {
                        id,
                        chapterId,
                        novelId,
                        status,
                        progress,
                        errorMessage,
                        createdAt,
                        updatedAt,
                        retryCount,
                    ->
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

    suspend fun updateStatusAwait(
        chapterId: Long,
        status: TranslationStatus,
        chapterName: String = "",
    ) {
        handler.await { db ->
            db.translation_queueQueries.updateStatus(
                chapterId = chapterId,
                status = status.ordinal.toLong(),
                updatedAt = System.currentTimeMillis(),
            )
        }
        refreshQueue()
        getQueueItemByChapterId(chapterId)?.let { item ->
            _progressUpdates.emit(
                TranslationProgressUpdate(
                    chapterId = item.chapterId,
                    novelId = item.novelId,
                    status = status,
                    progress = item.progress,
                    currentChunk = 0,
                    totalChunks = 0,
                    chapterName = chapterName,
                    errorMessage = item.errorMessage,
                ),
            )
        }
        logcat(LogPriority.DEBUG) { "Updated chapter $chapterId status to $status" }
    }

    fun updateStatus(chapterId: Long, status: TranslationStatus) {
        scope.launch {
            try {
                updateStatusAwait(chapterId, status)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to update chapter status: ${e.message}" }
            }
        }
    }

    suspend fun updateProgressAwait(
        chapterId: Long,
        progress: Int,
        status: TranslationStatus,
        chapterName: String = "",
    ) {
        handler.await { db ->
            db.translation_queueQueries.updateStatusAndProgress(
                chapterId = chapterId,
                status = status.ordinal.toLong(),
                progress = progress.toLong(),
                updatedAt = System.currentTimeMillis(),
            )
        }
        refreshQueue()
        getQueueItemByChapterId(chapterId)?.let { item ->
            _progressUpdates.emit(
                TranslationProgressUpdate(
                    chapterId = chapterId,
                    novelId = item.novelId,
                    status = status,
                    progress = progress,
                    currentChunk = 0,
                    totalChunks = 0,
                    chapterName = chapterName,
                    errorMessage = null,
                ),
            )
        }
    }

    fun updateProgress(chapterId: Long, progress: Int, status: TranslationStatus) {
        scope.launch {
            try {
                updateProgressAwait(chapterId, progress, status)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to update chapter progress: ${e.message}" }
            }
        }
    }

    suspend fun setErrorAwait(
        chapterId: Long,
        error: String,
        chapterName: String = "",
    ) {
        handler.await { db ->
            db.translation_queueQueries.setError(
                chapterId = chapterId,
                error = error,
                updatedAt = System.currentTimeMillis(),
            )
        }
        refreshQueue()
        getQueueItemByChapterId(chapterId)?.let { item ->
            _progressUpdates.emit(
                TranslationProgressUpdate(
                    chapterId = chapterId,
                    novelId = item.novelId,
                    status = TranslationStatus.FAILED,
                    progress = item.progress,
                    currentChunk = 0,
                    totalChunks = 0,
                    chapterName = chapterName,
                    errorMessage = error,
                ),
            )
        }
        logcat(LogPriority.DEBUG) { "Set error for chapter $chapterId: $error" }
    }

    fun setError(chapterId: Long, error: String) {
        scope.launch {
            try {
                setErrorAwait(chapterId, error)
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

    suspend fun hasPendingOrActive(chapterId: Long): Boolean {
        return try {
            getQueueItemByChapterId(chapterId)?.status.let { status ->
                status == TranslationStatus.PENDING || status == TranslationStatus.IN_PROGRESS
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to check chapter $chapterId queue state: ${e.message}" }
            false
        }
    }

    fun setActiveTranslation(item: TranslationQueueItem?) {
        _activeTranslation.value = item
    }

    suspend fun cancelChapter(chapterId: Long): Boolean {
        val item = getQueueItemByChapterId(chapterId) ?: return false
        _progressUpdates.emit(
            TranslationProgressUpdate(
                chapterId = item.chapterId,
                novelId = item.novelId,
                status = TranslationStatus.CANCELLED,
                progress = item.progress,
                currentChunk = 0,
                totalChunks = 0,
                chapterName = "",
                errorMessage = null,
            ),
        )
        handler.await { db ->
            db.translation_queueQueries.delete(chapterId)
        }
        if (_activeTranslation.value?.chapterId == chapterId) {
            setActiveTranslation(null)
        }
        refreshQueue()
        return item.status == TranslationStatus.IN_PROGRESS
    }

    suspend fun cancelAll(): Boolean {
        val items = queue.value + activeTranslation.value.let(::listOfNotNull)
        if (items.isEmpty()) return false
        items.distinctBy { it.chapterId }.forEach { item ->
            _progressUpdates.emit(
                TranslationProgressUpdate(
                    chapterId = item.chapterId,
                    novelId = item.novelId,
                    status = TranslationStatus.CANCELLED,
                    progress = item.progress,
                    currentChunk = 0,
                    totalChunks = 0,
                    chapterName = "",
                    errorMessage = null,
                ),
            )
            handler.await { db ->
                db.translation_queueQueries.delete(item.chapterId)
            }
        }
        setActiveTranslation(null)
        refreshQueue()
        return true
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
                db.translation_queueQueries.getPending(::mapQueueItem)
            }
            _queue.value = items
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to refresh queue: ${e.message}" }
        }
    }

    private suspend fun getQueueItemByChapterId(chapterId: Long): TranslationQueueItem? {
        return handler.awaitOneOrNull { db ->
            db.translation_queueQueries.getByChapterId(chapterId) {
                    id,
                    chapterId,
                    novelId,
                    status,
                    progress,
                    errorMessage,
                    createdAt,
                    updatedAt,
                    retryCount,
                ->
                mapQueueItem(
                    id = id,
                    chapterId = chapterId,
                    novelId = novelId,
                    status = status,
                    progress = progress,
                    errorMessage = errorMessage,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    retryCount = retryCount,
                )
            }
        }
    }

    private fun mapQueueItem(
        id: Long,
        chapterId: Long,
        novelId: Long,
        status: Long,
        progress: Long,
        errorMessage: String?,
        createdAt: Long,
        updatedAt: Long,
        retryCount: Long,
    ): TranslationQueueItem {
        return TranslationQueueItem(
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
