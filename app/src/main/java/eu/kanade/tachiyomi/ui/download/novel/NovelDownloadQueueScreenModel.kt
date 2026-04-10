package eu.kanade.tachiyomi.ui.download.novel

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadQueueManager
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadQueueState
import eu.kanade.tachiyomi.data.download.novel.NovelQueuedDownload
import eu.kanade.tachiyomi.data.download.novel.NovelQueuedDownloadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NovelDownloadQueueScreenModel(
    private val downloadManager: NovelDownloadManager = NovelDownloadManager(),
    private val queueState: Flow<NovelDownloadQueueState> = NovelDownloadQueueManager.state,
    private val getDownloadCount: () -> Int = { downloadManager.getDownloadCount() },
    private val getDownloadSize: () -> Long = { downloadManager.getDownloadSize() },
) : StateScreenModel<NovelDownloadQueueScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            queueState.collect { queueState ->
                mutableState.update { current ->
                    current.copy(
                        queueTasks = queueState.tasks,
                        isQueueRunning = queueState.isRunning,
                    )
                }
            }
        }
        refreshStorage()
    }

    fun refreshStorage() {
        mutableState.update {
            it.copy(
                downloadCount = getDownloadCount(),
                downloadSize = getDownloadSize(),
            )
        }
    }

    fun startDownloads() {
        NovelDownloadQueueManager.startDownloads()
    }

    fun pauseDownloads() {
        NovelDownloadQueueManager.pauseDownloads()
    }

    fun retryFailed() {
        NovelDownloadQueueManager.retryFailed()
    }

    @Immutable
    data class State(
        val downloadCount: Int = 0,
        val downloadSize: Long = 0L,
        val isQueueRunning: Boolean = true,
        val queueTasks: List<NovelQueuedDownload> = emptyList(),
    ) {
        val pendingCount: Int
            get() = queueTasks.count { it.status == NovelQueuedDownloadStatus.QUEUED }
        val activeCount: Int
            get() = queueTasks.count { it.status == NovelQueuedDownloadStatus.DOWNLOADING }
        val failedCount: Int
            get() = queueTasks.count { it.status == NovelQueuedDownloadStatus.FAILED }
        val queueCount: Int
            get() = pendingCount + activeCount + failedCount
    }
}
