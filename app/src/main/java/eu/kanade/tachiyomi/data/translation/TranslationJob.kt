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
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationCacheEntry
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationDiskCacheStore
import eu.kanade.tachiyomi.ui.reader.novel.translation.translationCacheModelId
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsChapterRepository
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TranslationJob(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    private val queueManager: TranslationQueueManager = Injekt.get()
    private val notificationManager: TranslationNotificationManager = Injekt.get()
    private val chapterRepository: NovelTtsChapterRepository = NovelTtsChapterRepository()
    private val readerPreferences: NovelReaderPreferences = Injekt.get()
    private val translationProcessor: NovelChapterTranslationProcessor = NovelChapterTranslationProcessor()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_TRANSLATION_PROGRESS) {
            setContentTitle(applicationContext.stringResource(MR.strings.notification_translation_in_progress))
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
        logcat(LogPriority.DEBUG) { "TranslationJob.doWork() started" }

        // Set foreground first - required for foreground service workers
        setForegroundSafely()

        try {
            while (!isStopped) {
                val item = queueManager.getNextPending() ?: break

                logcat(LogPriority.DEBUG) { "Processing translation for chapter ${item.chapterId}" }
                try {
                    processItem(item)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val chapterName = "Chapter ${item.chapterId}"
                    val message = e.message ?: e::class.java.simpleName
                    queueManager.setErrorAwait(
                        chapterId = item.chapterId,
                        error = message,
                        chapterName = chapterName,
                    )
                    queueManager.setActiveTranslation(null)
                    notificationManager.showError(
                        chapterName = chapterName,
                        error = message,
                        chapterId = item.chapterId,
                    )
                    logcat(LogPriority.ERROR, e) {
                        "Translation failed for chapter ${item.chapterId}"
                    }
                }
            }

            notificationManager.showQueueComplete()
            return Result.success()
        } catch (_: CancellationException) {
            val activeItem = queueManager.activeTranslation.value
            if (activeItem != null) {
                if (queueManager.hasPendingOrActive(activeItem.chapterId)) {
                    queueManager.updateStatusAwait(activeItem.chapterId, TranslationStatus.PENDING)
                }
                queueManager.setActiveTranslation(null)
            }
            logcat(LogPriority.DEBUG) { "Translation job cancelled" }
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

    private suspend fun processItem(item: TranslationQueueItem) {
        queueManager.setActiveTranslation(item)
        queueManager.updateStatusAwait(item.chapterId, TranslationStatus.IN_PROGRESS)

        val snapshot = chapterRepository.loadChapterSnapshot(item.chapterId)
        val settings = readerPreferences.resolveSettings(snapshot.novel.source)
        val textSegments = snapshot.contentBlocks
            .asSequence()
            .filterIsInstance<NovelReaderScreenModel.ContentBlock.Text>()
            .map { it.text }
            .toList()

        if (textSegments.isEmpty()) {
            throw IllegalStateException("Chapter has no translatable text")
        }

        val chapterName = snapshot.chapter.name.ifBlank { "Chapter ${item.chapterId}" }
        queueManager.updateStatusAwait(item.chapterId, TranslationStatus.IN_PROGRESS, chapterName)
        notificationManager.showProgress(
            chapterName = chapterName,
            chapterId = item.chapterId,
            progress = 0,
            pendingCount = queueManager.queue.value.size,
        )
        val translatedByIndex = translationProcessor.translateSegments(
            segments = textSegments,
            settings = settings,
            onLog = { message ->
                logcat(LogPriority.DEBUG) { "TranslationJob[${item.chapterId}]: $message" }
                queueManager.emitLog(item.chapterId, message)
            },
            onProgress = { progress ->
                queueManager.updateProgress(
                    chapterId = item.chapterId,
                    progress = progress,
                    status = TranslationStatus.IN_PROGRESS,
                )
                notificationManager.showProgress(
                    chapterName = chapterName,
                    chapterId = item.chapterId,
                    progress = progress,
                    pendingCount = queueManager.queue.value.size,
                )
            },
        )

        if (!settings.geminiDisableCache) {
            NovelReaderTranslationDiskCacheStore.put(
                GeminiTranslationCacheEntry(
                    chapterId = item.chapterId,
                    translatedByIndex = translatedByIndex,
                    provider = settings.translationProvider,
                    model = settings.translationCacheModelId(),
                    sourceLang = settings.geminiSourceLang,
                    targetLang = settings.geminiTargetLang,
                    promptMode = settings.geminiPromptMode,
                    stylePreset = settings.geminiStylePreset,
                ),
            )
        }

        queueManager.updateProgressAwait(
            chapterId = item.chapterId,
            progress = 100,
            status = TranslationStatus.COMPLETED,
            chapterName = chapterName,
        )
        queueManager.setActiveTranslation(null)

        notificationManager.showChapterComplete(
            chapterName = chapterName,
            chapterId = item.chapterId,
        )

        logcat(LogPriority.DEBUG) { "Completed translation for chapter ${item.chapterId}" }
    }

    companion object {
        private const val TAG = "TranslationJob"

        fun runImmediately(context: Context) {
            logcat(LogPriority.DEBUG) { "TranslationJob.runImmediately() called" }
            val request = OneTimeWorkRequestBuilder<TranslationJob>()
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, request)
            logcat(LogPriority.DEBUG) { "TranslationJob work request enqueued" }
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
