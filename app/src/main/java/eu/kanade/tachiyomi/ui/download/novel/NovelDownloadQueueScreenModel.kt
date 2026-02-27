package eu.kanade.tachiyomi.ui.download.novel

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadQueueManager
import eu.kanade.tachiyomi.data.download.novel.NovelQueuedDownload
import eu.kanade.tachiyomi.data.download.novel.NovelQueuedDownloadStatus
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NovelDownloadQueueScreenModel(
    private val downloadManager: NovelDownloadManager = NovelDownloadManager(),
) : StateScreenModel<NovelDownloadQueueScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            NovelDownloadQueueManager.state.collect { queueState ->
                mutableState.update { current ->
                    current.copy(
                        downloadCount = downloadManager.getDownloadCount(),
                        downloadSize = downloadManager.getDownloadSize(),
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
                downloadCount = downloadManager.getDownloadCount(),
                downloadSize = downloadManager.getDownloadSize(),
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
