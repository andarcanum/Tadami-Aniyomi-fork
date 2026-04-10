package eu.kanade.tachiyomi.data.download.novel

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.system.measureTimeMillis

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

class NovelDownloadQueueRuntimeState {
    private var nextTaskId = 0L
    private var workerRunning = false
    private val canceledTaskIds = mutableSetOf<Long>()
    private var activeDownloadTaskId: Long? = null
    private var activeDownloadJob: Job? = null

    @Synchronized
    fun nextTaskId(): Long {
        nextTaskId += 1
        return nextTaskId
    }

    @Synchronized
    fun tryStartWorker(): Boolean {
        if (workerRunning) return false
        workerRunning = true
        return true
    }

    @Synchronized
    fun markWorkerStopped() {
        workerRunning = false
    }

    @Synchronized
    fun markCanceled(taskId: Long) {
        canceledTaskIds += taskId
    }

    @Synchronized
    fun markCanceled(taskIds: Collection<Long>) {
        canceledTaskIds += taskIds
    }

    @Synchronized
    fun consumeCanceled(taskId: Long): Boolean {
        return canceledTaskIds.remove(taskId)
    }

    @Synchronized
    fun registerActiveDownload(taskId: Long, job: Job) {
        activeDownloadTaskId = taskId
        activeDownloadJob = job
    }

    @Synchronized
    fun clearActiveDownload(taskId: Long, job: Job) {
        if (activeDownloadTaskId == taskId && activeDownloadJob === job) {
            activeDownloadTaskId = null
            activeDownloadJob = null
        }
    }

    @Synchronized
    fun cancelActiveDownload(taskId: Long): Boolean {
        if (activeDownloadTaskId != taskId) return false
        activeDownloadJob?.cancel()
        return activeDownloadJob != null
    }
}

private data class NovelQueueTaskKey(
    val novelId: Long,
    val chapterId: Long,
    val type: NovelQueuedDownloadType,
    val format: NovelQueuedDownloadFormat,
)

data class MergeNovelQueuedTasksResult(
    val tasks: List<NovelQueuedDownload>,
    val addedCount: Int,
)

fun mergeNovelQueuedTasks(
    currentTasks: List<NovelQueuedDownload>,
    novel: Novel,
    chapters: List<NovelChapter>,
    type: NovelQueuedDownloadType,
    format: NovelQueuedDownloadFormat,
    runtimeState: NovelDownloadQueueRuntimeState,
): MergeNovelQueuedTasksResult {
    if (chapters.isEmpty()) {
        return MergeNovelQueuedTasksResult(
            tasks = currentTasks,
            addedCount = 0,
        )
    }

    val tasks = currentTasks.toMutableList()
    val indexByKey = tasks
        .withIndex()
        .associate { (index, task) ->
            NovelQueueTaskKey(
                novelId = task.novel.id,
                chapterId = task.chapter.id,
                type = task.type,
                format = task.format,
            ) to index
        }.toMutableMap()
    var added = 0

    chapters.forEach { chapter ->
        val key = NovelQueueTaskKey(
            novelId = novel.id,
            chapterId = chapter.id,
            type = type,
            format = format,
        )
        val existingIndex = indexByKey[key]
        if (existingIndex == null) {
            tasks += NovelQueuedDownload(
                taskId = runtimeState.nextTaskId(),
                novel = novel,
                chapter = chapter,
                type = type,
                format = format,
                status = NovelQueuedDownloadStatus.QUEUED,
            )
            indexByKey[key] = tasks.lastIndex
            added++
        } else {
            val existing = tasks[existingIndex]
            if (existing.status == NovelQueuedDownloadStatus.FAILED) {
                tasks[existingIndex] = existing.copy(
                    status = NovelQueuedDownloadStatus.QUEUED,
                    errorMessage = null,
                )
                added++
            }
        }
    }

    return MergeNovelQueuedTasksResult(
        tasks = tasks,
        addedCount = added,
    )
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

    private val runtimeState = NovelDownloadQueueRuntimeState()
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
        runtimeState.markCanceled(runningTaskIds)
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

        runtimeState.markCanceled(task.taskId)
        runtimeState.cancelActiveDownload(task.taskId)
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
            .filter {
                it.status == NovelQueuedDownloadStatus.QUEUED ||
                    it.status == NovelQueuedDownloadStatus.DOWNLOADING
            }
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
        var addedCount = 0
        val elapsed = measureTimeMillis {
            updateState { queueState ->
                val merged = mergeNovelQueuedTasks(
                    currentTasks = queueState.tasks,
                    novel = novel,
                    chapters = chapters,
                    type = type,
                    format = format,
                    runtimeState = runtimeState,
                )
                addedCount = merged.addedCount
                queueState.copy(tasks = merged.tasks)
            }
        }
        logcat(LogPriority.DEBUG) {
            "Novel queue enqueue: novel=${novel.id}, requested=${chapters.size}, added=$addedCount, type=$type, format=$format, elapsedMs=$elapsed"
        }
        startWorkerIfNeeded()
        return addedCount
    }

    private fun startWorkerIfNeeded() {
        if (!runtimeState.tryStartWorker()) return
        logcat(LogPriority.DEBUG) { "Novel queue worker starting" }
        queueScope.launch {
            try {
                processLoop()
            } finally {
                runtimeState.markWorkerStopped()
                logcat(LogPriority.DEBUG) { "Novel queue worker stopped" }
            }
        }
    }

    private suspend fun processLoop() {
        while (true) {
            val snapshot = state.value
            if (!snapshot.isRunning) {
                if (!shouldWaitForNovelQueueWhilePaused(snapshot)) {
                    break
                }
                delay(150)
                continue
            }

            val nextTask = snapshot.tasks.firstOrNull { it.status == NovelQueuedDownloadStatus.QUEUED } ?: break
            markTaskStatus(nextTask.taskId, NovelQueuedDownloadStatus.DOWNLOADING)
            logcat(LogPriority.DEBUG) {
                "Novel queue task starting: taskId=${nextTask.taskId}, novel=${nextTask.novel.id}, chapter=${nextTask.chapter.id}, type=${nextTask.type}"
            }

            val result = coroutineScope {
                val downloadJob = async {
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
                runtimeState.registerActiveDownload(nextTask.taskId, downloadJob)
                try {
                    runCatching { downloadJob.await() }
                } finally {
                    runtimeState.clearActiveDownload(nextTask.taskId, downloadJob)
                }
            }

            val canceled = runtimeState.consumeCanceled(nextTask.taskId)
            if (canceled) {
                if (nextTask.type == NovelQueuedDownloadType.ORIGINAL) {
                    downloadManager.deleteChapter(nextTask.novel, nextTask.chapter.id)
                } else {
                    val format = when (nextTask.format) {
                        NovelQueuedDownloadFormat.TXT -> NovelTranslatedDownloadFormat.TXT
                        NovelQueuedDownloadFormat.DOCX -> NovelTranslatedDownloadFormat.DOCX
                        NovelQueuedDownloadFormat.HTML -> NovelTranslatedDownloadFormat.TXT
                    }
                    translatedDownloadManager.deleteTranslatedChapter(
                        novel = nextTask.novel,
                        chapter = nextTask.chapter,
                        format = format,
                    )
                }
                removeTask(nextTask.taskId)
                continue
            }

            val success = result.getOrElse { false }
            if (success) {
                removeTask(nextTask.taskId)
                logcat(LogPriority.DEBUG) {
                    "Novel queue task completed: taskId=${nextTask.taskId}, novel=${nextTask.novel.id}, chapter=${nextTask.chapter.id}"
                }
            } else {
                val message = result.exceptionOrNull()?.message
                markTaskFailed(nextTask.taskId, message ?: "Download failed")
                logcat(LogPriority.WARN) {
                    "Novel queue task failed: taskId=${nextTask.taskId}, novel=${nextTask.novel.id}, chapter=${nextTask.chapter.id}, error=${message ?: "Download failed"}"
                }
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
            if (summary.active > 0) {
                val currentTask = state.tasks.firstOrNull { it.status == NovelQueuedDownloadStatus.DOWNLOADING }
                    ?: state.tasks.firstOrNull { it.status == NovelQueuedDownloadStatus.QUEUED }
                notifier.onProgressChange(
                    pendingCount = summary.pending,
                    activeCount = summary.active,
                    failedCount = summary.failed,
                    currentTask = currentTask,
                )
            } else {
                notifier.onQueued(summary.pending)
            }
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

internal fun shouldWaitForNovelQueueWhilePaused(
    state: NovelDownloadQueueState,
): Boolean {
    return !state.isRunning && state.tasks.any { it.status == NovelQueuedDownloadStatus.DOWNLOADING }
}
