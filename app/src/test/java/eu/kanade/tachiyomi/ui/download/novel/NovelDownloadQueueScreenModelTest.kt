package eu.kanade.tachiyomi.ui.download.novel

import eu.kanade.tachiyomi.data.download.novel.NovelDownloadQueueState
import eu.kanade.tachiyomi.data.download.novel.NovelQueuedDownload
import eu.kanade.tachiyomi.data.download.novel.NovelQueuedDownloadFormat
import eu.kanade.tachiyomi.data.download.novel.NovelQueuedDownloadStatus
import eu.kanade.tachiyomi.data.download.novel.NovelQueuedDownloadType
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelDownloadQueueScreenModelTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupMainDispatcher() {
            Dispatchers.setMain(Dispatchers.Unconfined)
        }

        @JvmStatic
        @org.junit.jupiter.api.AfterAll
        fun resetMainDispatcher() {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `queue updates do not trigger storage summary recalculation`() {
        runBlocking {
            val queueState = MutableStateFlow(NovelDownloadQueueState())
            var downloadCountCalls = 0
            var downloadSizeCalls = 0
            val screenModel = NovelDownloadQueueScreenModel(
                queueState = queueState,
                getDownloadCount = {
                    downloadCountCalls++
                    5
                },
                getDownloadSize = {
                    downloadSizeCalls++
                    10L
                },
            )

            try {
                withTimeout(1_000) {
                    while (screenModel.state.value.downloadCount != 5 || screenModel.state.value.downloadSize != 10L) {
                        yield()
                    }
                }

                downloadCountCalls shouldBe 1
                downloadSizeCalls shouldBe 1

                queueState.value = NovelDownloadQueueState(
                    tasks = listOf(queuedTask(1L)),
                    isRunning = false,
                )

                withTimeout(1_000) {
                    while (screenModel.state.value.queueCount != 1 || screenModel.state.value.isQueueRunning) {
                        yield()
                    }
                }

                downloadCountCalls shouldBe 1
                downloadSizeCalls shouldBe 1
            } finally {
                screenModel.onDispose()
            }
        }
    }

    @Test
    fun `refresh storage recalculates storage summary explicitly`() {
        var downloadCountCalls = 0
        var downloadSizeCalls = 0
        val screenModel = NovelDownloadQueueScreenModel(
            queueState = MutableStateFlow(NovelDownloadQueueState()),
            getDownloadCount = {
                downloadCountCalls++
                7
            },
            getDownloadSize = {
                downloadSizeCalls++
                14L
            },
        )

        try {
            runBlocking {
                withTimeout(1_000) {
                    while (downloadCountCalls != 1 || downloadSizeCalls != 1) {
                        yield()
                    }
                }
            }

            screenModel.refreshStorage()

            runBlocking {
                withTimeout(1_000) {
                    while (screenModel.state.value.downloadCount != 7 || screenModel.state.value.downloadSize != 14L) {
                        yield()
                    }
                }
            }

            downloadCountCalls shouldBe 2
            downloadSizeCalls shouldBe 2
            screenModel.state.value.downloadCount shouldBe 7
            screenModel.state.value.downloadSize shouldBe 14L
        } finally {
            screenModel.onDispose()
        }
    }

    private fun queuedTask(taskId: Long): NovelQueuedDownload {
        return NovelQueuedDownload(
            taskId = taskId,
            novel = Novel.create().copy(id = 100L + taskId),
            chapter = NovelChapter.create().copy(id = 200L + taskId, novelId = 100L + taskId),
            type = NovelQueuedDownloadType.ORIGINAL,
            format = NovelQueuedDownloadFormat.HTML,
            status = NovelQueuedDownloadStatus.QUEUED,
        )
    }
}
