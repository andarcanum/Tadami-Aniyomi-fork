package eu.kanade.tachiyomi.data.download.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelDownloadQueueManagerTest {

    @Test
    fun `paused queue with pending tasks should not keep polling`() {
        val state = NovelDownloadQueueState(
            isRunning = false,
            tasks = listOf(
                queuedTask(status = NovelQueuedDownloadStatus.QUEUED),
            ),
        )

        shouldWaitForNovelQueueWhilePaused(state) shouldBe false
    }

    @Test
    fun `paused queue with active download keeps short polling`() {
        val state = NovelDownloadQueueState(
            isRunning = false,
            tasks = listOf(
                queuedTask(status = NovelQueuedDownloadStatus.DOWNLOADING),
            ),
        )

        shouldWaitForNovelQueueWhilePaused(state) shouldBe true
    }

    private fun queuedTask(status: NovelQueuedDownloadStatus): NovelQueuedDownload {
        return NovelQueuedDownload(
            taskId = 1L,
            novel = Novel.create(),
            chapter = NovelChapter.create(),
            type = NovelQueuedDownloadType.ORIGINAL,
            format = NovelQueuedDownloadFormat.HTML,
            status = status,
        )
    }
}
