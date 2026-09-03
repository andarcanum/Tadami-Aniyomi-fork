package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationDiskCacheStore
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.UUID

class TranslationQueueManager(
    private val handler: NovelDatabaseHandler = Injekt.get(),
    private val json: Json = Injekt.get(),
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

    suspend fun addToQueue(
        chapterIds: List<Long>,
        novelId: Long,
        targetLang: String? = null,
    ): Int {
        if (chapterIds.isEmpty()) return 0

        val currentTime = System.currentTimeMillis()
        val batchToken = ""
        var addedCount = 0
        chapterIds.distinct().forEach { chapterId ->
            if (targetLang != null && NovelReaderTranslationDiskCacheStore.has(chapterId, targetLang)) {
                return@forEach
            }

            val existing = getQueueItemByChapterId(chapterId)
            if (
                shouldSkipTranslationQueueInsert(
                    existingStatus = existing?.status,
                    activeChapterId = activeTranslation.value?.chapterId,
                    chapterId = chapterId,
                )
            ) {
                return@forEach
            }

            insertQueueItem(
                chapterId = chapterId,
                novelId = novelId,
                batchToken = batchToken,
                batchOrder = 0,
                profileSnapshotJson = null,
                createdAt = currentTime,
            )
            addedCount++
        }

        refreshQueue()
        logcat(LogPriority.DEBUG) {
            "Added $addedCount/${chapterIds.distinct().size} chapters to translation queue"
        }
        return addedCount
    }

    suspend fun enqueueTranslationBatch(request: TranslationBatchRequest): TranslationBatchEnqueueResult {
        val distinctChapterIds = request.chapterIds.distinct()
        if (distinctChapterIds.isEmpty()) {
            return TranslationBatchEnqueueResult(
                batchToken = request.batchToken,
                requestedCount = 0,
                enqueuedCount = 0,
                skippedAlreadyTranslatedCount = 0,
            )
        }

        val batchToken = request.batchToken.ifBlank { UUID.randomUUID().toString() }
        val currentTime = System.currentTimeMillis()
        val profileSnapshotJson = json.encodeToString(request.profileSnapshot)
        // Full-requirements probe: identical to the reader's restore check, so a batch never skips
        // a chapter whose cache was produced under different settings (targetLang-only probe let
        // the batch report "already translated" while the reader kept showing the original text).
        val cacheRequirements = request.profileSnapshot.toTranslationCacheRequirements()
        var enqueuedCount = 0
        var skippedAlreadyTranslatedCount = 0

        distinctChapterIds.forEachIndexed { index, chapterId ->
            if (
                !request.forceRetranslate &&
                NovelReaderTranslationDiskCacheStore.has(chapterId, cacheRequirements)
            ) {
                skippedAlreadyTranslatedCount++
                return@forEachIndexed
            }

            val existing = getQueueItemByChapterId(chapterId)
            val canReplacePendingItem = request.forceRetranslate && existing?.status == TranslationStatus.PENDING
            if (
                !canReplacePendingItem &&
                shouldSkipTranslationQueueInsert(
                    existingStatus = existing?.status,
                    activeChapterId = activeTranslation.value?.chapterId,
                    chapterId = chapterId,
                )
            ) {
                return@forEachIndexed
            }

            insertQueueItem(
                chapterId = chapterId,
                novelId = request.novelId,
                batchToken = batchToken,
                batchOrder = index,
                profileSnapshotJson = profileSnapshotJson,
                createdAt = currentTime,
            )
            enqueuedCount++
        }

        upsertBatchState(
            TranslationBatchState(
                batchToken = batchToken,
                novelId = request.novelId,
                status = if (enqueuedCount > 0) TranslationBatchStatus.RUNNING else TranslationBatchStatus.COMPLETED,
                total = distinctChapterIds.size,
                enqueued = enqueuedCount,
                skipped = skippedAlreadyTranslatedCount,
                completed = 0,
                failed = 0,
                lastSuccessfulChapterId = null,
                createdAt = currentTime,
                updatedAt = currentTime,
            ),
        )
        refreshQueue()
        logcat(LogPriority.DEBUG) {
            "Added $enqueuedCount/${distinctChapterIds.size} chapters to translation batch $batchToken"
        }
        return TranslationBatchEnqueueResult(
            batchToken = batchToken,
            requestedCount = distinctChapterIds.size,
            enqueuedCount = enqueuedCount,
            skippedAlreadyTranslatedCount = skippedAlreadyTranslatedCount,
        )
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
                        batchToken,
                        batchOrder,
                        profileSnapshotJson,
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
                        batchToken = batchToken,
                        batchOrder = batchOrder.toInt(),
                        profileSnapshotJson = profileSnapshotJson,
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

    fun emitLog(chapterId: Long, message: String) {
        scope.launch {
            try {
                val item = getQueueItemByChapterId(chapterId) ?: return@launch
                _progressUpdates.emit(
                    TranslationProgressUpdate(
                        chapterId = item.chapterId,
                        novelId = item.novelId,
                        status = item.status,
                        progress = item.progress,
                        currentChunk = 0,
                        totalChunks = 0,
                        chapterName = "",
                        errorMessage = null,
                        logMessage = message,
                    ),
                )
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to emit translation log: ${e.message}" }
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
                db.translation_queueQueries.getNextPending { _, _, _, _, _, _, _, _, _, _, _, _ -> }
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

    suspend fun getBatchState(batchToken: String): TranslationBatchState? {
        if (batchToken.isBlank()) return null
        return try {
            handler.awaitOneOrNull { db ->
                db.translation_queueQueries.getBatchState(batchToken) {
                        batchToken,
                        novelId,
                        status,
                        total,
                        enqueued,
                        skipped,
                        completed,
                        failed,
                        lastSuccessfulChapterId,
                        createdAt,
                        updatedAt,
                    ->
                    TranslationBatchState(
                        batchToken = batchToken,
                        novelId = novelId,
                        status = TranslationBatchStatus.entries[status.toInt()],
                        total = total.toInt(),
                        enqueued = enqueued.toInt(),
                        skipped = skipped.toInt(),
                        completed = completed.toInt(),
                        failed = failed.toInt(),
                        lastSuccessfulChapterId = lastSuccessfulChapterId,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                    )
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to get batch state for $batchToken: ${e.message}" }
            null
        }
    }

    suspend fun isBatchPaused(batchToken: String): Boolean {
        return getBatchState(batchToken)?.status == TranslationBatchStatus.PAUSED
    }

    suspend fun pauseBatch(batchToken: String): Boolean {
        val state = getBatchState(batchToken) ?: return false
        if (state.status == TranslationBatchStatus.CANCELLED || state.status == TranslationBatchStatus.COMPLETED) {
            return false
        }
        upsertBatchState(state.copy(status = TranslationBatchStatus.PAUSED, updatedAt = System.currentTimeMillis()))
        return true
    }

    suspend fun resumeBatch(batchToken: String): Boolean {
        val state = getBatchState(batchToken) ?: return false
        if (state.status == TranslationBatchStatus.CANCELLED || state.status == TranslationBatchStatus.COMPLETED) {
            return false
        }
        upsertBatchState(state.copy(status = TranslationBatchStatus.RUNNING, updatedAt = System.currentTimeMillis()))
        return true
    }

    suspend fun cancelBatch(batchToken: String): Boolean {
        val state = getBatchState(batchToken) ?: return false
        val wasActive = activeTranslation.value?.batchToken == batchToken
        handler.await { db ->
            db.translation_queueQueries.upsertBatchState(
                batchToken = state.batchToken,
                novelId = state.novelId,
                status = TranslationBatchStatus.CANCELLED.ordinal.toLong(),
                total = state.total.toLong(),
                enqueued = state.enqueued.toLong(),
                skipped = state.skipped.toLong(),
                completed = state.completed.toLong(),
                failed = state.failed.toLong(),
                lastSuccessfulChapterId = state.lastSuccessfulChapterId,
                createdAt = state.createdAt,
                updatedAt = System.currentTimeMillis(),
            )
            db.translation_queueQueries.deleteByBatchToken(batchToken)
        }
        if (wasActive) {
            setActiveTranslation(null)
        }
        refreshQueue()
        return wasActive
    }

    suspend fun recoverStaleInProgressRows(): Int {
        return try {
            val staleItems = handler.awaitList { db ->
                db.translation_queueQueries.getInProgress(::mapQueueItem)
            }
            if (staleItems.isEmpty()) {
                return 0
            }
            handler.await { db ->
                db.translation_queueQueries.recoverInProgress(System.currentTimeMillis())
            }
            refreshQueue()
            logcat(LogPriority.DEBUG) {
                "Recovered ${staleItems.size} stale in-progress translation row(s)"
            }
            staleItems.size
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to recover in-progress rows: ${e.message}" }
            0
        }
    }

    suspend fun recordBatchItemResult(
        batchToken: String,
        chapterId: Long,
        succeeded: Boolean,
    ): TranslationBatchState? {
        val state = getBatchState(batchToken) ?: return null
        if (state.status == TranslationBatchStatus.CANCELLED) {
            return state
        }

        val updatedState = state.copy(
            status = when {
                state.completed + state.failed + 1 >= state.enqueued && state.enqueued > 0 ->
                    TranslationBatchStatus.COMPLETED
                state.status == TranslationBatchStatus.PAUSED ->
                    TranslationBatchStatus.PAUSED
                else ->
                    TranslationBatchStatus.RUNNING
            },
            completed = state.completed + if (succeeded) 1 else 0,
            failed = state.failed + if (succeeded) 0 else 1,
            lastSuccessfulChapterId = if (succeeded) chapterId else state.lastSuccessfulChapterId,
            updatedAt = System.currentTimeMillis(),
        )
        upsertBatchState(updatedState)
        return updatedState
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
        handler.await { db ->
            db.translation_queueQueries.deleteAllBatchStates()
        }
        setActiveTranslation(null)
        refreshQueue()
        return true
    }

    suspend fun incrementRetryAwait(chapterId: Long) {
        handler.await { db ->
            db.translation_queueQueries.incrementRetry(
                chapterId = chapterId,
                updatedAt = System.currentTimeMillis(),
            )
        }
        refreshQueue()
        logcat(LogPriority.DEBUG) { "Incremented retry count for chapter $chapterId" }
    }

    fun incrementRetry(chapterId: Long) {
        scope.launch {
            try {
                incrementRetryAwait(chapterId)
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

    private suspend fun insertQueueItem(
        chapterId: Long,
        novelId: Long,
        batchToken: String,
        batchOrder: Int,
        profileSnapshotJson: String?,
        createdAt: Long,
    ) {
        handler.await { db ->
            db.translation_queueQueries.insert(
                chapterId = chapterId,
                novelId = novelId,
                batchToken = batchToken,
                batchOrder = batchOrder.toLong(),
                profileSnapshotJson = profileSnapshotJson,
                createdAt = createdAt,
            )
        }
    }

    private suspend fun upsertBatchState(state: TranslationBatchState) {
        handler.await { db ->
            db.translation_queueQueries.upsertBatchState(
                batchToken = state.batchToken,
                novelId = state.novelId,
                status = state.status.ordinal.toLong(),
                total = state.total.toLong(),
                enqueued = state.enqueued.toLong(),
                skipped = state.skipped.toLong(),
                completed = state.completed.toLong(),
                failed = state.failed.toLong(),
                lastSuccessfulChapterId = state.lastSuccessfulChapterId,
                createdAt = state.createdAt,
                updatedAt = state.updatedAt,
            )
        }
    }

    private suspend fun getQueueItemByChapterId(chapterId: Long): TranslationQueueItem? {
        return handler.awaitOneOrNull { db ->
            db.translation_queueQueries.getByChapterId(chapterId) {
                    id,
                    chapterId,
                    novelId,
                    batchToken,
                    batchOrder,
                    profileSnapshotJson,
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
                    batchToken = batchToken,
                    batchOrder = batchOrder,
                    profileSnapshotJson = profileSnapshotJson,
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
        batchToken: String,
        batchOrder: Long,
        profileSnapshotJson: String?,
        status: Long,
        progress: Long,
        errorMessage: String?,
        createdAt: Long,
        updatedAt: Long,
        retryCount: Long,
    ): TranslationQueueItem {
        val safeStatus = TranslationStatus.entries.getOrElse(status.toInt()) { TranslationStatus.PENDING }
        return TranslationQueueItem(
            id = id,
            chapterId = chapterId,
            novelId = novelId,
            status = safeStatus,
            progress = progress.toInt().coerceIn(0, 100),
            errorMessage = errorMessage,
            retryCount = retryCount.toInt().coerceAtLeast(0),
            createdAt = createdAt,
            updatedAt = updatedAt,
            batchToken = batchToken,
            batchOrder = batchOrder.toInt(),
            profileSnapshotJson = profileSnapshotJson,
        )
    }
}

internal fun shouldSkipTranslationQueueInsert(
    existingStatus: TranslationStatus?,
    activeChapterId: Long?,
    chapterId: Long,
): Boolean {
    return existingStatus == TranslationStatus.PENDING ||
        (existingStatus == TranslationStatus.IN_PROGRESS && activeChapterId == chapterId)
}
