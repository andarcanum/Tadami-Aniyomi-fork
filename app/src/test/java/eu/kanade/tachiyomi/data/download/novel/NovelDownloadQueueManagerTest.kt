package eu.kanade.tachiyomi.data.download.novel

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

    @Test
    fun `runtime state starts worker only once until released`() {
        val runtime = NovelDownloadQueueRuntimeState()

        runtime.tryStartWorker() shouldBe true
        runtime.tryStartWorker() shouldBe false

        runtime.markWorkerStopped()

        runtime.tryStartWorker() shouldBe true
    }

    @Test
    fun `runtime state generates task ids sequentially`() {
        val runtime = NovelDownloadQueueRuntimeState()

        runtime.nextTaskId() shouldBe 1L
        runtime.nextTaskId() shouldBe 2L
        runtime.nextTaskId() shouldBe 3L
    }

    @Test
    fun `runtime state tracks canceled tasks deterministically`() {
        val runtime = NovelDownloadQueueRuntimeState()

        runtime.markCanceled(7L)
        runtime.markCanceled(9L)

        runtime.consumeCanceled(7L) shouldBe true
        runtime.consumeCanceled(7L) shouldBe false
        runtime.consumeCanceled(9L) shouldBe true
    }

    @Test
    fun `merge queued tasks revives failed entries without duplicating tasks`() {
        val novel = Novel.create().copy(id = 10L)
        val chapter1 = NovelChapter.create().copy(id = 1L, novelId = novel.id)
        val chapter2 = NovelChapter.create().copy(id = 2L, novelId = novel.id)
        val existingTasks = listOf(
            NovelQueuedDownload(
                taskId = 11L,
                novel = novel,
                chapter = chapter1,
                type = NovelQueuedDownloadType.ORIGINAL,
                format = NovelQueuedDownloadFormat.HTML,
                status = NovelQueuedDownloadStatus.FAILED,
                errorMessage = "boom",
            ),
            NovelQueuedDownload(
                taskId = 12L,
                novel = novel,
                chapter = chapter2,
                type = NovelQueuedDownloadType.ORIGINAL,
                format = NovelQueuedDownloadFormat.HTML,
                status = NovelQueuedDownloadStatus.QUEUED,
            ),
        )
        val runtime = NovelDownloadQueueRuntimeState()

        val merged = mergeNovelQueuedTasks(
            currentTasks = existingTasks,
            novel = novel,
            chapters = listOf(chapter1, chapter2),
            type = NovelQueuedDownloadType.ORIGINAL,
            format = NovelQueuedDownloadFormat.HTML,
            runtimeState = runtime,
        )

        merged.addedCount shouldBe 1
        merged.tasks.size shouldBe 2
        merged.tasks.first { it.chapter.id == 1L }.status shouldBe NovelQueuedDownloadStatus.QUEUED
        merged.tasks.first { it.chapter.id == 1L }.errorMessage shouldBe null
        merged.tasks.count { it.chapter.id == 2L } shouldBe 1
    }

    @Test
    fun `merge queued tasks creates ids only for genuinely new tasks`() {
        val novel = Novel.create().copy(id = 20L)
        val existingChapter = NovelChapter.create().copy(id = 1L, novelId = novel.id)
        val newChapter = NovelChapter.create().copy(id = 2L, novelId = novel.id)
        val existingTasks = listOf(
            NovelQueuedDownload(
                taskId = 30L,
                novel = novel,
                chapter = existingChapter,
                type = NovelQueuedDownloadType.ORIGINAL,
                format = NovelQueuedDownloadFormat.HTML,
                status = NovelQueuedDownloadStatus.QUEUED,
            ),
        )
        val runtime = NovelDownloadQueueRuntimeState()

        val merged = mergeNovelQueuedTasks(
            currentTasks = existingTasks,
            novel = novel,
            chapters = listOf(existingChapter, newChapter),
            type = NovelQueuedDownloadType.ORIGINAL,
            format = NovelQueuedDownloadFormat.HTML,
            runtimeState = runtime,
        )

        merged.addedCount shouldBe 1
        merged.tasks.size shouldBe 2
        merged.tasks.first { it.chapter.id == 2L }.taskId shouldBe 1L
    }

    @Test
    fun `runtime state cancels only the matching active task job`() {
        runBlocking {
            val runtime = NovelDownloadQueueRuntimeState()
            val started = CompletableDeferred<Unit>()
            val job = launch {
                started.complete(Unit)
                CompletableDeferred<Unit>().await()
            }

            started.await()
            runtime.registerActiveDownload(taskId = 5L, job = job)

            runtime.cancelActiveDownload(taskId = 4L) shouldBe false
            job.isCancelled shouldBe false

            runtime.cancelActiveDownload(taskId = 5L) shouldBe true
            job.cancelAndJoin()
            job.isCancelled shouldBe true
        }
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
