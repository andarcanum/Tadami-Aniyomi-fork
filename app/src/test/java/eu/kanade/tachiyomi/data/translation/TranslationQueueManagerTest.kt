package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationDiskCacheStore
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.translation.model.TranslationEnqueueResult
import tachiyomi.domain.translation.model.TranslationRun
import tachiyomi.domain.translation.model.TranslationRunStatus
import tachiyomi.domain.translation.repository.TranslationRunRepository

class TranslationQueueManagerTest {
    @Test
    fun `new progress subscribers observe current durable state without event replay`() = runTest {
        val run = TranslationRun(1, 1, 1, "", 0, "{}", TranslationRunStatus.PENDING, 0, null, 0, 0, 0, generation = 7)
        val rows = MutableStateFlow(listOf(run))
        val repo = mockk<TranslationRunRepository>()
        every { repo.observeRuns(any()) } returns rows
        val manager = TranslationQueueManager(mockk<NovelDatabaseHandler>(), Json, repo, backgroundScope, {})
        val updates = mutableListOf<TranslationProgressUpdate>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.progressUpdates.take(2).toList(updates)
        }
        updates.single().generation shouldBe 7
        updates.single().status shouldBe TranslationStatus.PENDING
        rows.value = listOf(run.copy(status = TranslationRunStatus.WAITING, progress = 66, nextAttemptAt = 10_000))
        runCurrent()
        updates.size shouldBe 2
        updates.last().progress shouldBe 66
        updates.last().nextAttemptAt shouldBe 10_000L
    }

    @Test
    fun `clear reserves mutation order before a later undispatched enqueue`() = runTest {
        mockkObject(NovelReaderTranslationDiskCacheStore)
        try {
            val events = mutableListOf<String>()
            val release = CompletableDeferred<Unit>()
            val repo = mockk<TranslationRunRepository>()
            every { repo.observeRuns(any()) } returns MutableStateFlow(emptyList())
            coEvery { repo.invalidateCheckpoint(1, any()) } coAnswers {
                events += "invalidate-start"
                release.await()
                events += "invalidate-done"
                true
            }
            every { NovelReaderTranslationDiskCacheStore.remove(1) } answers { events += "file-clear" }
            coEvery { repo.enqueue(any(), any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
                events += "enqueue"
                TranslationEnqueueResult("", listOf(1), emptyList(), emptyList())
            }
            val manager =
                TranslationQueueManager(mockk<NovelDatabaseHandler>(), Json, repo, backgroundScope, {
                    events +=
                        "schedule"
                })
            val settings = NovelReaderPreferences(InMemoryPreferenceStore(), Json).resolveSettings(1)
            manager.clearArtifacts(1)
            val after =
                async(start = CoroutineStart.UNDISPATCHED) {
                    manager.enqueueChapters(listOf(1), 1, settings, emptyList())
                }
            events shouldBe listOf("invalidate-start")
            release.complete(Unit)
            runCurrent()
            after.await() shouldBe 1
            events shouldBe listOf("invalidate-start", "invalidate-done", "file-clear", "enqueue", "schedule")
        } finally {
            unmockkObject(NovelReaderTranslationDiskCacheStore)
        }
    }

    @Test
    fun `explicit action resume schedules only an acknowledged current generation`() = runTest {
        val repo = mockk<TranslationRunRepository>()
        every { repo.observeRuns(any()) } returns MutableStateFlow(emptyList())
        coEvery { repo.resumeActionPaused(11, 7, any()) } returns true
        coEvery { repo.resumeActionPaused(11, 6, any()) } returns false
        var scheduled = 0
        val manager =
            TranslationQueueManager(mockk<NovelDatabaseHandler>(), Json, repo, backgroundScope, { scheduled++ })
        manager.resumeActionPaused(11, 6) shouldBe false
        scheduled shouldBe 0
        manager.resumeActionPaused(11, 7) shouldBe true
        scheduled shouldBe 1
    }
}
