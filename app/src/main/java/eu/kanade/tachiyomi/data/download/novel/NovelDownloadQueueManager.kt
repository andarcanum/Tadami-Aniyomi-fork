package eu.kanade.tachiyomi.data.download.novel

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

enum class NovelQueuedDownloadType {
    ORIGINAL,
    TRANSLATED,
}

enum class NovelQueuedDownloadFormat {
    HTML,
    TXT,
    DOCX,
}

enum class NovelQueuedDownloadStatus {
    QUEUED,
    DOWNLOADING,
    FAILED,
}

data class NovelQueuedDownload(
    val taskId: Long,
    val novel: Novel,
    val chapter: NovelChapter,
    val type: NovelQueuedDownloadType,
    val format: NovelQueuedDownloadFormat,
    val status: NovelQueuedDownloadStatus,
    val errorMessage: String? = null,
)

data class NovelDownloadQueueState(
    val isRunning: Boolean = true,
    val tasks: List<NovelQueuedDownload> = emptyList(),
) {
    val pendingCount: Int
        get() = tasks.count { it.status == NovelQueuedDownloadStatus.QUEUED }
    val activeCount: Int
        get() = tasks.count { it.status == NovelQueuedDownloadStatus.DOWNLOADING }
    val failedCount: Int
        get() = tasks.count { it.status == NovelQueuedDownloadStatus.FAILED }
    val queueCount: Int
        get() = pendingCount + activeCount + failedCount
}

object NovelDownloadQueueManager {

    private val queueScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadManager = NovelDownloadManager()
    private val translatedDownloadManager = NovelTranslatedDownloadManager()
    private val _state = MutableStateFlow(NovelDownloadQueueState())
    val state = _state.asStateFlow()
    private val notifier = runCatching {
        NovelDownloadNotifier(Injekt.get<Application>())
    }.getOrNull()

    private var taskIdSeed = 0L
    private var workerRunning = false
    private val canceledTaskIds = mutableSetOf<Long>()
    private var previousNotifiedSummary = QueueNotifySummary()

    fun startDownloads() {
        updateState { it.copy(isRunning = true) }
        startWorkerIfNeeded()
    }

    fun pauseDownloads() {
        updateState { it.copy(isRunning = false) }
    }

    fun clearQueue() {
        val runningTaskIds = state.value.tasks
            .filter { it.status == NovelQueuedDownloadStatus.DOWNLOADING }
            .mapTo(mutableSetOf()) { it.taskId }
        canceledTaskIds += runningTaskIds
        updateState {
            it.copy(
                tasks = it.tasks.filter { task -> task.status == NovelQueuedDownloadStatus.DOWNLOADING },
            )
        }
    }

    fun retryFailed() {
        updateState { state ->
            state.copy(
                tasks = state.tasks.map { task ->
                    if (task.status == NovelQueuedDownloadStatus.FAILED) {
                        task.copy(status = NovelQueuedDownloadStatus.QUEUED, errorMessage = null)
                    } else {
                        task
                    }
                },
                isRunning = true,
            )
        }
        startWorkerIfNeeded()
    }

    fun cancelTask(
        novelId: Long,
        chapterId: Long,
        type: NovelQueuedDownloadType = NovelQueuedDownloadType.ORIGINAL,
    ) {
        val task = state.value.tasks.firstOrNull {
            it.novel.id == novelId && it.chapter.id == chapterId && it.type == type
        } ?: return

        canceledTaskIds += task.taskId
        updateState { queueState ->
            queueState.copy(tasks = queueState.tasks.filterNot { it.taskId == task.taskId })
        }
    }

    fun enqueueOriginal(
        novel: Novel,
        chapters: List<NovelChapter>,
    ): Int {
        return enqueueTasks(
            novel = novel,
            chapters = chapters,
            type = NovelQueuedDownloadType.ORIGINAL,
            format = NovelQueuedDownloadFormat.HTML,
        )
    }

    fun enqueueTranslated(
        novel: Novel,
        chapters: List<NovelChapter>,
        format: NovelTranslatedDownloadFormat,
    ): Int {
        val queueFormat = when (format) {
            NovelTranslatedDownloadFormat.TXT -> NovelQueuedDownloadFormat.TXT
            NovelTranslatedDownloadFormat.DOCX -> NovelQueuedDownloadFormat.DOCX
        }
        return enqueueTasks(
            novel = novel,
            chapters = chapters,
            type = NovelQueuedDownloadType.TRANSLATED,
            format = queueFormat,
        )
    }

    fun getQueuedChapterIds(novelId: Long): Set<Long> {
        return state.value.tasks
            .asSequence()
            .filter { it.novel.id == novelId }
            .filter { it.type == NovelQueuedDownloadType.ORIGINAL }
            .filter { it.status == NovelQueuedDownloadStatus.QUEUED || it.status == NovelQueuedDownloadStatus.DOWNLOADING }
            .map { it.chapter.id }
            .toSet()
    }

    private fun enqueueTasks(
        novel: Novel,
        chapters: List<NovelChapter>,
        type: NovelQueuedDownloadType,
        format: NovelQueuedDownloadFormat,
    ): Int {
        if (chapters.isEmpty()) return 0
        var added = 0
        updateState { queueState ->
            val currentTasks = queueState.tasks.toMutableList()
            chapters.forEach { chapter ->
                val existing = currentTasks.firstOrNull { task ->
                    task.novel.id == novel.id &&
                        task.chapter.id == chapter.id &&
                        task.type == type &&
                        task.format == format
                }
                if (existing == null) {
                    currentTasks += NovelQueuedDownload(
                        taskId = ++taskIdSeed,
                        novel = novel,
                        chapter = chapter,
                        type = type,
                        format = format,
                        status = NovelQueuedDownloadStatus.QUEUED,
                    )
                    added++
                } else if (existing.status == NovelQueuedDownloadStatus.FAILED) {
                    val index = currentTasks.indexOf(existing)
                    currentTasks[index] = existing.copy(
                        status = NovelQueuedDownloadStatus.QUEUED,
                        errorMessage = null,
                    )
                    added++
                }
            }
            queueState.copy(tasks = currentTasks)
        }
        startWorkerIfNeeded()
        return added
    }

    private fun startWorkerIfNeeded() {
        if (workerRunning) return
        workerRunning = true
        queueScope.launch {
            try {
                processLoop()
            } finally {
                workerRunning = false
            }
        }
    }

    private suspend fun processLoop() {
        while (true) {
            val snapshot = state.value
            if (!snapshot.isRunning) {
                val hasActiveOrPending = snapshot.tasks.any { task ->
                    task.status == NovelQueuedDownloadStatus.QUEUED ||
                        task.status == NovelQueuedDownloadStatus.DOWNLOADING
                }
                if (!hasActiveOrPending) {
                    break
                }
                delay(150)
                continue
            }

            val nextTask = snapshot.tasks.firstOrNull { it.status == NovelQueuedDownloadStatus.QUEUED } ?: break
            markTaskStatus(nextTask.taskId, NovelQueuedDownloadStatus.DOWNLOADING)

            val result = runCatching {
                when (nextTask.type) {
                    NovelQueuedDownloadType.ORIGINAL -> {
                        downloadManager.downloadChapter(nextTask.novel, nextTask.chapter)
                    }
                    NovelQueuedDownloadType.TRANSLATED -> {
                        val format = when (nextTask.format) {
                            NovelQueuedDownloadFormat.TXT -> NovelTranslatedDownloadFormat.TXT
                            NovelQueuedDownloadFormat.DOCX -> NovelTranslatedDownloadFormat.DOCX
                            NovelQueuedDownloadFormat.HTML -> NovelTranslatedDownloadFormat.TXT
                        }
                        translatedDownloadManager
                            .exportTranslatedChapter(nextTask.novel, nextTask.chapter, format)
                            .isSuccess
                    }
                }
            }

            val canceled = canceledTaskIds.remove(nextTask.taskId)
            if (canceled) {
                if (nextTask.type == NovelQueuedDownloadType.ORIGINAL) {
                    downloadManager.deleteChapter(nextTask.novel, nextTask.chapter.id)
                }
                removeTask(nextTask.taskId)
                continue
            }

            val success = result.getOrElse { false }
            if (success) {
                removeTask(nextTask.taskId)
            } else {
                val message = result.exceptionOrNull()?.message
                markTaskFailed(nextTask.taskId, message ?: "Download failed")
            }
        }
    }

    private fun markTaskStatus(
        taskId: Long,
        status: NovelQueuedDownloadStatus,
    ) {
        updateState { queueState ->
            queueState.copy(
                tasks = queueState.tasks.map { task ->
                    if (task.taskId == taskId) {
                        task.copy(status = status)
                    } else {
                        task
                    }
                },
            )
        }
    }

    private fun markTaskFailed(
        taskId: Long,
        errorMessage: String,
    ) {
        updateState { queueState ->
            queueState.copy(
                tasks = queueState.tasks.map { task ->
                    if (task.taskId == taskId) {
                        task.copy(
                            status = NovelQueuedDownloadStatus.FAILED,
                            errorMessage = errorMessage,
                        )
                    } else {
                        task
                    }
                },
            )
        }
    }

    private fun removeTask(taskId: Long) {
        updateState { queueState ->
            queueState.copy(
                tasks = queueState.tasks.filterNot { task -> task.taskId == taskId },
            )
        }
    }

    private inline fun updateState(
        transform: (NovelDownloadQueueState) -> NovelDownloadQueueState,
    ) {
        _state.update(transform)
        notifyQueueState(_state.value)
    }

    private fun notifyQueueState(state: NovelDownloadQueueState) {
        val notifier = notifier ?: return
        val summary = QueueNotifySummary(
            pending = state.pendingCount,
            active = state.activeCount,
            failed = state.failedCount,
        )
        val wasActive = previousNotifiedSummary.activeTotal > 0
        val isActive = summary.activeTotal > 0

        if (isActive) {
            val currentTask = state.tasks.firstOrNull { it.status == NovelQueuedDownloadStatus.DOWNLOADING }
                ?: state.tasks.firstOrNull { it.status == NovelQueuedDownloadStatus.QUEUED }
            notifier.onProgressChange(
                pendingCount = summary.pending,
                activeCount = summary.active,
                failedCount = summary.failed,
                currentTask = currentTask,
            )
        } else if (wasActive) {
            notifier.onComplete(summary.failed)
        } else if (summary.failed == 0) {
            notifier.dismissProgress()
        }

        previousNotifiedSummary = summary
    }

    private data class QueueNotifySummary(
        val pending: Int = 0,
        val active: Int = 0,
        val failed: Int = 0,
    ) {
        val activeTotal: Int
            get() = pending + active
    }
}
