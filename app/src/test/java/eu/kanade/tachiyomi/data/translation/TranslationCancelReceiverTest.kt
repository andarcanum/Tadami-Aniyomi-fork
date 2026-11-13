package eu.kanade.tachiyomi.data.translation

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import eu.kanade.tachiyomi.data.notification.Notifications
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TranslationCancelReceiverTest {

    private fun sampleBatchState(
        token: String,
        status: TranslationBatchStatus,
    ) = TranslationBatchState(
        batchToken = token,
        novelId = 10L,
        status = status,
        total = 3,
        enqueued = 3,
        skipped = 0,
        completed = 1,
        failed = 0,
        lastSuccessfulChapterId = 1L,
        createdAt = 100L,
        updatedAt = 200L,
    )

    @Test
    fun `chapter cancel uses stable notification id for large and negative int chapter ids`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val receiver = TranslationCancelReceiver()
            val queueManager = mockk<TranslationQueueManager>(relaxed = true)
            val notificationManager = mockk<TranslationNotificationManager>(relaxed = true)
            val cancelledIds = mutableListOf<Int>()

            val largeChapterId = (1L shl 40) or 0x8000_0005L
            val intent = Intent(context, TranslationCancelReceiver::class.java).apply {
                action = TranslationCancelReceiver.ACTION_CANCEL_CHAPTER
                putExtra(TranslationCancelReceiver.EXTRA_CHAPTER_ID, largeChapterId)
            }

            receiver.handleAction(
                context = context,
                intent = intent,
                queueManager = queueManager,
                notificationManager = notificationManager,
                cancelChapterNotification = { cancelledIds += it },
            )

            coVerify(exactly = 1) { queueManager.cancelChapter(largeChapterId) }
            val expectedId = Notifications.ID_TRANSLATION_PROGRESS +
                TranslationNotificationManager.stableNotificationId(largeChapterId)
            cancelledIds shouldBe listOf(expectedId)
        }
    }

    @Test
    fun `batch cancel shows remaining paused batch instead of clearing queue notification`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val receiver = TranslationCancelReceiver()
            val pausedBatch = sampleBatchState("batch-paused", TranslationBatchStatus.PAUSED)
            val queueManager = mockk<TranslationQueueManager>(relaxed = true)
            every { queueManager.activeTranslation } returns MutableStateFlow(null)
            coEvery { queueManager.hasNext() } returns false
            coEvery { queueManager.getActiveBatches() } returns listOf(pausedBatch)
            val notificationManager = mockk<TranslationNotificationManager>(relaxed = true)

            val intent = Intent(context, TranslationCancelReceiver::class.java).apply {
                action = TranslationCancelReceiver.ACTION_CANCEL_BATCH
                putExtra(TranslationCancelReceiver.EXTRA_BATCH_TOKEN, "batch-cancelled")
            }

            receiver.handleAction(
                context = context,
                intent = intent,
                queueManager = queueManager,
                notificationManager = notificationManager,
                triggerQueueWorker = {},
            )

            coVerify(exactly = 1) { queueManager.cancelBatch("batch-cancelled") }
            verify(exactly = 1) { notificationManager.showBatchPaused(pausedBatch) }
            verify(exactly = 0) { notificationManager.cancelQueueProgress() }
        }
    }

    @Test
    fun `batch cancel preserves progress notification while other queued work remains`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val receiver = TranslationCancelReceiver()
            val queuedBatch = sampleBatchState("batch-queued", TranslationBatchStatus.QUEUED)
            val pausedBatch = sampleBatchState("batch-paused", TranslationBatchStatus.PAUSED)
            val queueManager = mockk<TranslationQueueManager>(relaxed = true)
            every { queueManager.activeTranslation } returns MutableStateFlow(null)
            coEvery { queueManager.hasNext() } returns true
            coEvery { queueManager.getActiveBatches() } returns listOf(queuedBatch, pausedBatch)
            val notificationManager = mockk<TranslationNotificationManager>(relaxed = true)
            var workerTriggered = 0

            val intent = Intent(context, TranslationCancelReceiver::class.java).apply {
                action = TranslationCancelReceiver.ACTION_CANCEL_BATCH
                putExtra(TranslationCancelReceiver.EXTRA_BATCH_TOKEN, "batch-cancelled")
            }

            receiver.handleAction(
                context = context,
                intent = intent,
                queueManager = queueManager,
                notificationManager = notificationManager,
                triggerQueueWorker = { workerTriggered++ },
            )

            workerTriggered shouldBe 1
            verify(exactly = 0) { notificationManager.cancelQueueProgress() }
            verify(exactly = 0) { notificationManager.showBatchPaused(any()) }
        }
    }

    @Test
    fun `batch cancel clears queue notification when no batches or active work remain`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val receiver = TranslationCancelReceiver()
            val queueManager = mockk<TranslationQueueManager>(relaxed = true)
            every { queueManager.activeTranslation } returns MutableStateFlow(null)
            coEvery { queueManager.hasNext() } returns false
            coEvery { queueManager.getActiveBatches() } returns emptyList()
            val notificationManager = mockk<TranslationNotificationManager>(relaxed = true)

            val intent = Intent(context, TranslationCancelReceiver::class.java).apply {
                action = TranslationCancelReceiver.ACTION_CANCEL_BATCH
                putExtra(TranslationCancelReceiver.EXTRA_BATCH_TOKEN, "batch-last")
            }

            receiver.handleAction(
                context = context,
                intent = intent,
                queueManager = queueManager,
                notificationManager = notificationManager,
                triggerQueueWorker = {},
            )

            verify(exactly = 1) { notificationManager.cancelQueueProgress() }
        }
    }
}
