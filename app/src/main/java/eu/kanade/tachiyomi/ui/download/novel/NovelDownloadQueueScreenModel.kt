package eu.kanade.tachiyomi.ui.download.novel

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import kotlinx.coroutines.flow.update

class NovelDownloadQueueScreenModel(
    private val downloadManager: NovelDownloadManager = NovelDownloadManager(),
) : StateScreenModel<NovelDownloadQueueScreenModel.State>(State()) {

    init {
        refresh()
    }

    fun refresh() {
        mutableState.update {
            State(
                downloadCount = downloadManager.getDownloadCount(),
                downloadSize = downloadManager.getDownloadSize(),
            )
        }
    }

    @Immutable
    data class State(
        val downloadCount: Int = 0,
        val downloadSize: Long = 0L,
    )
}
