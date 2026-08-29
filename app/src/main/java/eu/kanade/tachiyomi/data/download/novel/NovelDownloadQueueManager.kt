package eu.kanade.tachiyomi.data.download.novel

import android.app.Application
import eu.kanade.tachiyomi.data.download.DownloadNetworkStatus
import eu.kanade.tachiyomi.data.download.engine.DownloadCompletionTracker
import eu.kanade.tachiyomi.data.download.engine.DownloadSection
import eu.kanade.tachiyomi.data.download.engine.DownloadTelemetryEmitter
import eu.kanade.tachiyomi.data.download.shouldRequeueNovelTaskAfterFailure
import eu.kanade.tachiyomi.data.download.toDownloadNetworkStatus
import eu.kanade.tachiyomi.util.system.activeNetworkState
import eu.kanade.tachiyomi.util.system.networkStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.achievement.handler.AchievementHandler
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.random.Random
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
    val waitingForNetwork: Boolean = false,
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
    companion object {
        const val WORKER_GENERATION_NONE = 0L
    }

    private var nextTaskId = 0L
    private var workerRunning = false
    private var workerGeneration = WORKER_GENERATION_NONE
    private val canceledTaskIds = mutableSetOf<Long>()
    private var activeDownloadTaskId: Long? = null
    private var activeDownloadJob: Job? = null

    @Synchronized
    fun nextTaskId(): Long {
        nextTaskId += 1
        return nextTaskId
    }

    /**
     * Acquires the single-worker latch and returns the generation token of the newly started
     * worker, or [WORKER_GENERATION_NONE] when a worker is already running. The returned token
     * must be passed back to [releaseWorker]; releases from any other (stale) generation are
     * ignored so a late worker exit can never clear a latch owned by a newer worker.
     */
    @Synchronized
    fun tryStartWorker(): Long {
        if (workerRunning) return WORKER_GENERATION_NONE
        workerRunning = true
        workerGeneration += 1
        return workerGeneration
    }

    @Synchronized
    fun releaseWorker(generation: Long) {
        if (generation != WORKER_GENERATION_NONE && workerGeneration == generation) {
            workerRunning = false
        }
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

internal fun novelQueueTaskIdsToCancelOnClear(tasks: List<NovelQueuedDownload>): Set<Long> {
    return tasks.mapTo(mutableSetOf()) { it.taskId }
}

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

    /**
     * Telemetry emitter shared across the novel queue pipeline.
     * Replace with a real emitter that feeds into [DownloadSpeedTracker]
     * to enable live speed display for novels.
     */
    @Volatile
    var telemetryEmitter: DownloadTelemetryEmitter = DownloadTelemetryEmitter.NOOP

    @Volatile
    var completionTracker: DownloadCompletionTracker = DownloadCompletionTracker()

    private val queueScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadManager = NovelDownloadManager()
    private val translatedDownloadManager = NovelTranslatedDownloadManager()
    private val downloadPreferences: DownloadPreferences by lazy { Injekt.get() }
    private val achievementHandler: AchievementHandler by lazy { Injekt.get() }
    private val _state = MutableStateFlow(NovelDownloadQueueState())
    val state = _state.asStateFlow()
    private val notifier = runCatching {
        NovelDownloadNotifier(Injekt.get<Application>())
    }.getOrNull()

    private val application: Application by lazy { Injekt.get() }
    private val runtimeState = NovelDownloadQueueRuntimeState()
    private var previousNotifiedSummary = QueueNotifySummary()
    private var notifyJob: kotlinx.coroutines.Job? = null
    private var networkMonitorJob: Job? = null
    private var lastNovelRequestStartedAtMs = 0L

    init {
        ensureNetworkMonitor()
    }

    fun getDownloadSize(): Long = downloadManager.getDownloadSize() + translatedDownloadManager.getDownloadSize()

    fun startDownloads() {
        updateState { it.copy(isRunning = true, waitingForNetwork = false) }
        startWorkerIfNeeded()
    }

    fun pauseDownloads() {
        updateState { it.copy(isRunning = false, waitingForNetwork = false) }
    }

    fun clearQueue() {
        val snapshot = state.value.tasks
        runtimeState.markCanceled(novelQueueTaskIdsToCancelOnClear(snapshot))
        snapshot
            .filter { it.status == NovelQueuedDownloadStatus.DOWNLOADING }
            .forEach { runtimeState.cancelActiveDownload(it.taskId) }
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
        val generation = runtimeState.tryStartWorker()
        if (generation == NovelDownloadQueueRuntimeState.WORKER_GENERATION_NONE) return
        updateState { queueState ->
            queueState.copy(tasks = recoverStaleDownloadingTasks(queueState.tasks))
        }
        logcat(LogPriority.DEBUG) { "Novel queue worker starting" }
        queueScope.launch {
            processLoop(generation)
        }
    }

    private suspend fun processLoop(startGeneration: Long) {
        var generation = startGeneration
        try {
            while (true) {
                val snapshot = state.value
                if (!snapshot.isRunning) {
                    if (!shouldWaitForNovelQueueWhilePaused(snapshot)) {
                        break
                    }
                    delay(150)
                    continue
                }

                val networkStatus = currentNetworkStatus()
                if (networkStatus != DownloadNetworkStatus.Available) {
                    pauseForNetwork(networkUnavailableReason(networkStatus))
                    delay(150)
                    continue
                }

                val nextTask = snapshot.tasks.firstOrNull { it.status == NovelQueuedDownloadStatus.QUEUED }
                if (nextTask == null) {
                    runtimeState.releaseWorker(generation)
                    val hasFreshQueuedTasks = state.value.tasks.any { it.status == NovelQueuedDownloadStatus.QUEUED }
                    if (!hasFreshQueuedTasks) break
                    val retaken = runtimeState.tryStartWorker()
                    if (retaken == NovelDownloadQueueRuntimeState.WORKER_GENERATION_NONE) break
                    generation = retaken
                    continue
                }
                try {
                    val throttleConfig = NovelDownloadThrottleConfig.from(downloadPreferences)
                    if (!waitForNovelThrottleWindow(throttleConfig, nextTask.taskId)) {
                        removeTask(nextTask.taskId)
                        continue
                    }
                    // A pause that arrived while we were sleeping out the throttle window
                    // must not dispatch a new download. The task stays QUEUED; the
                    // top-of-loop break handles the paused state, and startDownloads()
                    // re-arms the worker on resume.
                    if (!state.value.isRunning) continue
                    markTaskStatus(nextTask.taskId, NovelQueuedDownloadStatus.DOWNLOADING)
                    logcat(LogPriority.DEBUG) {
                        "Novel queue task starting: taskId=${nextTask.taskId}, novel=${nextTask.novel.id}, chapter=${nextTask.chapter.id}, type=${nextTask.type}, throttle=$throttleConfig"
                    }

                    val result = supervisorScope {
                        val downloadJob = async {
                            withTimeout(throttleConfig.timeoutMs) {
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
                        completionTracker.recordCompletion(DownloadSection.NOVEL)
                        achievementHandler.trackFeatureUsed(AchievementEvent.Feature.DOWNLOAD)
                        logcat(LogPriority.DEBUG) {
                            "Novel queue task completed: taskId=${nextTask.taskId}, novel=${nextTask.novel.id}, chapter=${nextTask.chapter.id}"
                        }
                    } else {
                        val exception = result.exceptionOrNull()
                        val queueSnapshot = state.value
                        val networkAvailable = currentNetworkStatus() == DownloadNetworkStatus.Available
                        if (
                            shouldRequeueNovelTaskAfterFailure(
                                waitingForNetwork = queueSnapshot.waitingForNetwork,
                                networkAvailable = networkAvailable,
                                exception = exception,
                            )
                        ) {
                            markTaskStatus(nextTask.taskId, NovelQueuedDownloadStatus.QUEUED)
                            pauseForNetwork(networkUnavailableReason(currentNetworkStatus()))
                            logcat(LogPriority.DEBUG) {
                                "Novel queue task requeued after network issue: taskId=${nextTask.taskId}"
                            }
                            continue
                        }
                        val message = exception?.message
                        markTaskFailed(nextTask.taskId, message ?: "Download failed")
                        logcat(LogPriority.WARN) {
                            "Novel queue task failed: taskId=${nextTask.taskId}, novel=${nextTask.novel.id}, chapter=${nextTask.chapter.id}, error=${message ?: "Download failed"}"
                        }
                        applyFailureCooldown(throttleConfig, nextTask.taskId)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        if (state.value.waitingForNetwork) {
                            markTaskStatus(nextTask.taskId, NovelQueuedDownloadStatus.QUEUED)
                            continue
                        }
                        throw e
                    }
                    logcat(LogPriority.ERROR, e) {
                        "Critical failure inside novel queue processLoop iteration for taskId=${nextTask.taskId}: ${e.message}"
                    }
                    markTaskFailed(nextTask.taskId, e.message ?: "Critical download failure")
                }
            }
        } finally {
            runtimeState.releaseWorker(generation)
            logcat(LogPriority.DEBUG) { "Novel queue worker stopped" }
        }
    }

    private suspend fun waitForNovelThrottleWindow(
        config: NovelDownloadThrottleConfig,
        taskId: Long,
    ): Boolean {
        val now = System.currentTimeMillis()
        val jitter = if (config.jitterMs > 0L) Random.nextLong(config.jitterMs + 1L) else 0L
        val requiredGap = config.delayBetweenRequestsMs + jitter
        val waitMs = (lastNovelRequestStartedAtMs + requiredGap - now).coerceAtLeast(0L)
        if (waitMs > 0L) {
            logcat(LogPriority.DEBUG) { "Novel queue throttling taskId=$taskId for ${waitMs}ms" }
            if (!delayWhileQueueAllows(taskId, waitMs)) return false
        }
        lastNovelRequestStartedAtMs = System.currentTimeMillis()
        return true
    }

    private suspend fun applyFailureCooldown(
        config: NovelDownloadThrottleConfig,
        taskId: Long,
    ) {
        if (config.failureCooldownMs <= 0L) return
        logcat(LogPriority.DEBUG) { "Novel queue failure cooldown taskId=$taskId for ${config.failureCooldownMs}ms" }
        delayWhileQueueAllows(taskId, config.failureCooldownMs)
    }

    private suspend fun delayWhileQueueAllows(taskId: Long, delayMs: Long): Boolean {
        var remainingMs = delayMs
        while (remainingMs > 0L) {
            if (runtimeState.consumeCanceled(taskId)) return false
            if (!state.value.isRunning) return true
            val stepMs = remainingMs.coerceAtMost(250L)
            delay(stepMs)
            remainingMs -= stepMs
        }
        return true
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
        _state.update { state ->
            val transformed = transform(state)
            transformed.copy(tasks = pruneFailedTasks(transformed.tasks))
        }
        scheduleNotifyQueueState()
    }

    private fun ensureNetworkMonitor() {
        if (networkMonitorJob?.isActive == true) return
        networkMonitorJob = queueScope.launch {
            try {
                combine(
                    application.networkStateFlow()
                        .onStart { emit(application.activeNetworkState()) },
                    downloadPreferences.downloadOnlyOverWifi().changes()
                        .onStart { emit(downloadPreferences.downloadOnlyOverWifi().get()) },
                ) { networkState, requireWifi ->
                    networkState.toDownloadNetworkStatus(requireWifi)
                }
                    .distinctUntilChanged()
                    .collect { status -> handleNetworkStatusChange(status) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Novel queue network monitor failed" }
            }
        }
    }

    private fun handleNetworkStatusChange(status: DownloadNetworkStatus) {
        when (status) {
            DownloadNetworkStatus.Available -> {
                val snapshot = state.value
                if (snapshot.waitingForNetwork && snapshot.queueCount > 0) {
                    startDownloads()
                }
            }
            DownloadNetworkStatus.NoNetwork,
            DownloadNetworkStatus.NoWifi,
            -> pauseForNetwork(networkUnavailableReason(status))
        }
    }

    private fun pauseForNetwork(reason: String) {
        val snapshot = state.value
        if (snapshot.queueCount == 0) return
        if (snapshot.waitingForNetwork && !snapshot.isRunning && snapshot.activeCount == 0) {
            return
        }

        snapshot.tasks
            .filter { it.status == NovelQueuedDownloadStatus.DOWNLOADING }
            .forEach { runtimeState.cancelActiveDownload(it.taskId) }

        updateState { queueState ->
            queueState.copy(
                isRunning = false,
                waitingForNetwork = true,
                tasks = queueState.tasks.map { task ->
                    if (task.status == NovelQueuedDownloadStatus.DOWNLOADING) {
                        task.copy(
                            status = NovelQueuedDownloadStatus.QUEUED,
                            errorMessage = null,
                        )
                    } else {
                        task
                    }
                },
            )
        }
        notifier?.onWarning(reason)
    }

    private fun currentNetworkStatus(): DownloadNetworkStatus {
        return application.activeNetworkState()
            .toDownloadNetworkStatus(downloadPreferences.downloadOnlyOverWifi().get())
    }

    private fun networkUnavailableReason(status: DownloadNetworkStatus): String {
        return when (status) {
            DownloadNetworkStatus.NoNetwork -> application.stringResource(MR.strings.download_notifier_no_network)
            DownloadNetworkStatus.NoWifi -> application.stringResource(MR.strings.download_notifier_text_only_wifi)
            DownloadNetworkStatus.Available -> ""
        }
    }

    private fun scheduleNotifyQueueState() {
        notifyJob?.cancel()
        notifyJob = queueScope.launch {
            delay(200L)
            notifyQueueState(_state.value)
        }
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

internal fun recoverStaleDownloadingTasks(
    tasks: List<NovelQueuedDownload>,
): List<NovelQueuedDownload> {
    if (tasks.none { it.status == NovelQueuedDownloadStatus.DOWNLOADING }) return tasks

    return tasks.map { task ->
        if (task.status == NovelQueuedDownloadStatus.DOWNLOADING) {
            task.copy(
                status = NovelQueuedDownloadStatus.QUEUED,
                errorMessage = null,
            )
        } else {
            task
        }
    }
}

/**
 * Removes the oldest FAILED tasks when the count exceeds [maxFailed].
 *
 * This prevents unbounded memory growth in the download queue when downloads
 * fail repeatedly. Without pruning, every failed task stays in
 * [NovelDownloadQueueState.tasks] forever, consuming memory until OOM.
 *
 * Only FAILED tasks are removed; QUEUED and DOWNLOADING tasks are preserved.
 * Among FAILED tasks, the oldest (first in list) are removed first.
 *
 * @param tasks the current task list
 * @param maxFailed maximum number of FAILED tasks to keep (default: 100)
 * @return a new list with excess FAILED tasks removed, preserving original order
 */
internal fun pruneFailedTasks(
    tasks: List<NovelQueuedDownload>,
    maxFailed: Int = 100,
): List<NovelQueuedDownload> {
    val failedIndices = tasks.indices.filter { tasks[it].status == NovelQueuedDownloadStatus.FAILED }
    val failedToRemove = (failedIndices.size - maxFailed).coerceAtLeast(0)
    if (failedToRemove == 0) return tasks

    val removeIndices = failedIndices.take(failedToRemove).toSet()
    return tasks.filterIndexed { index, _ -> index !in removeIndices }
}

data class NovelDownloadThrottleConfig(
    val delayBetweenRequestsMs: Long,
    val jitterMs: Long,
    val timeoutMs: Long,
    val failureCooldownMs: Long,
) {
    companion object {
        private const val MIN_DELAY_MS = 0
        private const val MAX_DELAY_MS = 30_000
        private const val MIN_JITTER_MS = 0
        private const val MAX_JITTER_MS = 10_000
        private const val MIN_TIMEOUT_MS = 5_000
        private const val MAX_TIMEOUT_MS = 180_000
        private const val MIN_FAILURE_COOLDOWN_MS = 0
        private const val MAX_FAILURE_COOLDOWN_MS = 300_000

        fun from(preferences: DownloadPreferences): NovelDownloadThrottleConfig {
            return NovelDownloadThrottleConfig(
                delayBetweenRequestsMs = preferences.novelDownloadDelayMs().get()
                    .coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
                    .toLong(),
                jitterMs = preferences.novelDownloadJitterMs().get()
                    .coerceIn(MIN_JITTER_MS, MAX_JITTER_MS)
                    .toLong(),
                timeoutMs = preferences.novelDownloadTimeoutMs().get()
                    .coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
                    .toLong(),
                failureCooldownMs = preferences.novelDownloadFailureCooldownMs().get()
                    .coerceIn(MIN_FAILURE_COOLDOWN_MS, MAX_FAILURE_COOLDOWN_MS)
                    .toLong(),
            )
        }
    }
}
