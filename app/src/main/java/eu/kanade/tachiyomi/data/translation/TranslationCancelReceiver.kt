package eu.kanade.tachiyomi.data.translation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TranslationCancelReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val queueManager = Injekt.get<TranslationQueueManager>()
        val notificationManager = Injekt.get<TranslationNotificationManager>()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleAction(
                    context = context,
                    intent = intent,
                    queueManager = queueManager,
                    notificationManager = notificationManager,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    internal suspend fun handleAction(
        context: Context,
        intent: Intent,
        queueManager: TranslationQueueManager,
        notificationManager: TranslationNotificationManager,
        stopQueueWorker: () -> Unit = { TranslationJob.stop(context) },
        triggerQueueWorker: () -> Unit = { TranslationJob.runImmediately(context.applicationContext) },
        cancelChapterNotification: (Int) -> Unit = { id -> context.cancelNotification(id) },
    ) {
        when (intent.action) {
            ACTION_CANCEL_ALL -> {
                val hadWork = queueManager.cancelAll()
                if (hadWork) {
                    stopQueueWorker()
                }
                notificationManager.cancelQueueProgress()
            }
            ACTION_PAUSE_BATCH -> {
                val batchToken = intent.getStringExtra(EXTRA_BATCH_TOKEN).orEmpty()
                if (batchToken.isBlank()) return
                if (queueManager.pauseBatch(batchToken)) {
                    queueManager.getBatchState(batchToken)?.let(notificationManager::showBatchPaused)
                }
            }
            ACTION_RESUME_BATCH -> {
                val batchToken = intent.getStringExtra(EXTRA_BATCH_TOKEN).orEmpty()
                if (batchToken.isBlank()) return
                if (queueManager.resumeBatch(batchToken)) {
                    triggerQueueWorker()
                }
            }
            ACTION_CANCEL_BATCH -> {
                val batchToken = intent.getStringExtra(EXTRA_BATCH_TOKEN).orEmpty()
                if (batchToken.isBlank()) return
                queueManager.cancelBatch(batchToken)
                val hasNext = queueManager.hasNext()
                if (hasNext) {
                    triggerQueueWorker()
                }
                val remainingBatches = queueManager.getActiveBatches()
                if (!hasNext && queueManager.activeTranslation.value == null) {
                    val pausedBatch = remainingBatches.firstOrNull {
                        it.status == TranslationBatchStatus.PAUSED
                    }
                    when {
                        pausedBatch != null -> notificationManager.showBatchPaused(pausedBatch)
                        remainingBatches.isEmpty() -> notificationManager.cancelQueueProgress()
                    }
                }
            }
            else -> {
                val chapterId = intent.getLongExtra(EXTRA_CHAPTER_ID, -1)
                if (chapterId == -1L) return
                queueManager.cancelChapter(chapterId)
                cancelChapterNotification(
                    Notifications.ID_TRANSLATION_PROGRESS +
                        TranslationNotificationManager.stableNotificationId(chapterId),
                )
            }
        }
    }

    companion object {
        const val ACTION_CANCEL_CHAPTER = "eu.kanade.tachiyomi.action.CANCEL_TRANSLATION_CHAPTER"
        const val ACTION_CANCEL_ALL = "eu.kanade.tachiyomi.action.CANCEL_TRANSLATION_ALL"
        const val ACTION_PAUSE_BATCH = "eu.kanade.tachiyomi.action.PAUSE_TRANSLATION_BATCH"
        const val ACTION_RESUME_BATCH = "eu.kanade.tachiyomi.action.RESUME_TRANSLATION_BATCH"
        const val ACTION_CANCEL_BATCH = "eu.kanade.tachiyomi.action.CANCEL_TRANSLATION_BATCH"
        const val EXTRA_CHAPTER_ID = "chapterId"
        const val EXTRA_BATCH_TOKEN = "batchToken"
    }
}
