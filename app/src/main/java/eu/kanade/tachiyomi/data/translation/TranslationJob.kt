package eu.kanade.tachiyomi.data.translation

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.lifecycle.asFlow
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TranslationJob(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    private val queueManager: TranslationQueueManager = Injekt.get()
    private val notificationManager: TranslationNotificationManager = Injekt.get()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_TRANSLATION_PROGRESS) {
            setContentTitle("Translation in progress")
            setSmallIcon(android.R.drawable.ic_menu_edit)
        }.build()
        return ForegroundInfo(
            Notifications.ID_TRANSLATION_PROGRESS,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    override suspend fun doWork(): Result {
        setForegroundSafely()

        try {
            while (!isStopped) {
                val item = queueManager.getNextPending() ?: break

                logcat(LogPriority.DEBUG) { "Processing translation for chapter ${item.chapterId}" }

                queueManager.setActiveTranslation(item)
                queueManager.updateStatus(item.chapterId, TranslationStatus.IN_PROGRESS)

                simulateTranslation(item)

                if (isStopped) {
                    logcat(LogPriority.DEBUG) { "Translation job stopped during processing" }
                    queueManager.updateStatus(item.chapterId, TranslationStatus.PENDING)
                    break
                }

                queueManager.updateStatus(item.chapterId, TranslationStatus.COMPLETED)
                queueManager.setActiveTranslation(null)

                notificationManager.showComplete(
                    chapterName = "Chapter ${item.chapterId}",
                    chapterId = item.chapterId,
                )

                logcat(LogPriority.DEBUG) { "Completed translation for chapter ${item.chapterId}" }
            }

            return Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Translation job failed: ${e.message}" }

            val activeItem = queueManager.activeTranslation.value
            if (activeItem != null) {
                queueManager.setError(activeItem.chapterId, e.message ?: "Unknown error")
                queueManager.setActiveTranslation(null)

                notificationManager.showError(
                    chapterName = "Chapter ${activeItem.chapterId}",
                    error = e.message ?: "Unknown error",
                    chapterId = activeItem.chapterId,
                )
            }

            return Result.failure()
        }
    }

    // TODO: Replace with actual GeminiTranslator integration in later phase
    private suspend fun simulateTranslation(item: TranslationQueueItem) {
        for (progress in 0..100 step 10) {
            if (isStopped) break

            queueManager.updateProgress(
                chapterId = item.chapterId,
                progress = progress,
                status = TranslationStatus.IN_PROGRESS,
            )

            notificationManager.showProgress(
                TranslationProgressUpdate(
                    chapterId = item.chapterId,
                    novelId = item.novelId,
                    status = TranslationStatus.IN_PROGRESS,
                    progress = progress,
                    currentChunk = progress / 10,
                    totalChunks = 10,
                    chapterName = "Chapter ${item.chapterId}",
                    errorMessage = null,
                ),
            )

            delay(200)
        }
    }

    companion object {
        private const val TAG = "TranslationJob"

        fun runImmediately(context: Context) {
            val request = OneTimeWorkRequestBuilder<TranslationJob>()
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }

        fun start(context: Context) {
            runImmediately(context)
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(TAG)
        }

        fun isRunning(context: Context): Boolean {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(TAG)
                .get()
                .let { list -> list.count { it.state == WorkInfo.State.RUNNING } == 1 }
        }

        fun isRunningFlow(context: Context): Flow<Boolean> {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(TAG)
                .asFlow()
                .map { list -> list.count { it.state == WorkInfo.State.RUNNING } == 1 }
        }
    }
}
