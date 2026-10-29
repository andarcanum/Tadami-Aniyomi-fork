package eu.kanade.tachiyomi.data.translation

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TranslationNotificationManager(
    private val context: Application,
    private val queueManager: TranslationQueueManager = Injekt.get(),
) {

    private val notificationManager = NotificationManagerCompat.from(context)

    fun showProgress(
        chapterName: String,
        chapterId: Long,
        batchToken: String = "",
        progress: Int,
        pendingCount: Int,
    ) {
        val safeProgress = progress.coerceIn(0, 100)
        val builder = context.notificationBuilder(Notifications.CHANNEL_TRANSLATION_PROGRESS) {
            setContentTitle(context.stringResource(MR.strings.notification_translation_in_progress))
            setContentText(buildProgressText(chapterName, safeProgress, pendingCount))
            setSmallIcon(android.R.drawable.ic_menu_edit)
            setProgress(100, safeProgress, false)
            setOngoing(true)
            setOnlyAlertOnce(true)
            setStyle(
                NotificationCompat.BigTextStyle().bigText(buildProgressText(chapterName, safeProgress, pendingCount)),
            )
            setContentIntent(openChapterIntent(chapterId))
            if (batchToken.isBlank()) {
                addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    context.stringResource(MR.strings.notification_action_cancel_current),
                    cancelChapterIntent(chapterId),
                )
                addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    context.stringResource(MR.strings.notification_action_cancel_all),
                    cancelAllIntent(),
                )
            } else {
                addAction(
                    android.R.drawable.ic_media_pause,
                    context.stringResource(MR.strings.action_pause),
                    pauseBatchIntent(batchToken),
                )
                addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    context.stringResource(MR.strings.notification_translation_batch_cancel),
                    cancelBatchIntent(batchToken),
                )
            }
        }

        context.notify(Notifications.ID_TRANSLATION_PROGRESS, builder.build())
    }

    fun showBatchPaused(state: TranslationBatchState) {
        val builder = context.notificationBuilder(Notifications.CHANNEL_TRANSLATION_PROGRESS) {
            setContentTitle(context.stringResource(MR.strings.notification_translation_batch_paused_title))
            setContentText(buildBatchPausedText(state))
            setSmallIcon(android.R.drawable.ic_media_pause)
            setProgress(0, 0, false)
            setOngoing(true)
            setOnlyAlertOnce(true)
            setStyle(NotificationCompat.BigTextStyle().bigText(buildBatchPausedText(state)))
            state.lastSuccessfulChapterId?.let { chapterId ->
                setContentIntent(openChapterIntent(chapterId))
            }
            addAction(
                android.R.drawable.ic_media_play,
                context.stringResource(MR.strings.action_resume),
                resumeBatchIntent(state.batchToken),
            )
            addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.stringResource(MR.strings.notification_translation_batch_cancel),
                cancelBatchIntent(state.batchToken),
            )
        }

        context.notify(Notifications.ID_TRANSLATION_PROGRESS, builder.build())
    }

    fun showBatchComplete(state: TranslationBatchState) {
        val builder = context.notificationBuilder(Notifications.CHANNEL_TRANSLATION_PROGRESS) {
            setContentTitle(context.stringResource(MR.strings.notification_translation_batch_complete_title))
            setContentText(buildBatchCompleteText(state))
            setSmallIcon(android.R.drawable.ic_menu_edit)
            setProgress(0, 0, false)
            setAutoCancel(true)
            setOnlyAlertOnce(true)
            setStyle(NotificationCompat.BigTextStyle().bigText(buildBatchCompleteText(state)))
            state.lastSuccessfulChapterId?.let { chapterId ->
                setContentIntent(openChapterIntent(chapterId))
                addAction(
                    android.R.drawable.ic_menu_view,
                    context.stringResource(MR.strings.notification_action_open),
                    openChapterIntent(chapterId),
                )
            }
        }

        context.notify(Notifications.ID_TRANSLATION_PROGRESS, builder.build())
    }

    fun showChapterComplete(chapterName: String, chapterId: Long) {
        val notificationId = getNotificationId(chapterId)

        val builder = context.notificationBuilder(Notifications.CHANNEL_TRANSLATION_PROGRESS) {
            setContentTitle(context.stringResource(MR.strings.notification_translation_complete))
            setContentText(chapterName)
            setSmallIcon(android.R.drawable.ic_menu_edit)
            setAutoCancel(true)
            setProgress(0, 0, false)
            setContentIntent(openChapterIntent(chapterId))
            addAction(
                android.R.drawable.ic_menu_view,
                context.stringResource(MR.strings.notification_action_open),
                openChapterIntent(chapterId),
            )
        }

        context.notify(notificationId, builder.build())
    }

    fun showQueueComplete() {
        context.cancelNotification(Notifications.ID_TRANSLATION_PROGRESS)
    }

    fun showError(chapterName: String, error: String, chapterId: Long) {
        val notificationId = getNotificationId(chapterId)

        val builder = context.notificationBuilder(Notifications.CHANNEL_TRANSLATION_PROGRESS) {
            setContentTitle(context.stringResource(MR.strings.notification_translation_error))
            setContentText("$chapterName: $error")
            setSmallIcon(android.R.drawable.ic_dialog_alert)
            setAutoCancel(true)
            setProgress(0, 0, false)
            setStyle(NotificationCompat.BigTextStyle().bigText("$chapterName\n$error"))
            createErrorLogFile(chapterName, error, chapterId)?.let { file ->
                val openLogIntent = NotificationReceiver.openErrorLogPendingActivity(
                    context,
                    file.getUriCompat(context),
                )
                setContentIntent(openLogIntent)
                addAction(
                    android.R.drawable.ic_menu_upload,
                    context.stringResource(MR.strings.action_open_log),
                    openLogIntent,
                )
            } ?: setContentIntent(openChapterIntent(chapterId))
            addAction(
                android.R.drawable.ic_menu_view,
                context.stringResource(MR.strings.notification_action_open),
                openChapterIntent(chapterId),
            )
        }

        context.notify(notificationId, builder.build())
    }

    fun cancel(chapterId: Long) {
        val notificationId = getNotificationId(chapterId)
        context.cancelNotification(notificationId)
    }

    fun cancelQueueProgress() {
        context.cancelNotification(Notifications.ID_TRANSLATION_PROGRESS)
    }

    private fun buildProgressText(
        chapterName: String,
        progress: Int,
        pendingCount: Int,
    ): String {
        val remaining = pendingCount.coerceAtLeast(0)
        return if (remaining > 0) {
            "$chapterName • $progress% • ${context.stringResource(
                MR.strings.notification_translation_queue_remaining,
                remaining,
            )}"
        } else {
            "$chapterName • $progress%"
        }
    }

    private fun openChapterIntent(chapterId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.INTENT_OPEN_NOVEL_CHAPTER
            putExtra(MainActivity.INTENT_NOVEL_CHAPTER_ID, chapterId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            stableNotificationId(chapterId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createErrorLogFile(
        chapterName: String,
        error: String,
        chapterId: Long,
    ) = runCatching {
        context.createFileInCacheDir("tadami_translation_error_$chapterId.txt").apply {
            bufferedWriter().use { out ->
                out.write("Novel translation error\n\n")
                out.write("Chapter ID: $chapterId\n")
                out.write("Chapter: $chapterName\n\n")
                out.write(error)
                out.write("\n")
            }
        }
    }.getOrNull()

    private fun cancelChapterIntent(chapterId: Long): PendingIntent {
        val intent = Intent(context, TranslationCancelReceiver::class.java).apply {
            action = TranslationCancelReceiver.ACTION_CANCEL_CHAPTER
            putExtra(TranslationCancelReceiver.EXTRA_CHAPTER_ID, chapterId)
        }
        return PendingIntent.getBroadcast(
            context,
            stableNotificationId(chapterId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun pauseBatchIntent(batchToken: String): PendingIntent {
        val intent = Intent(context, TranslationCancelReceiver::class.java).apply {
            action = TranslationCancelReceiver.ACTION_PAUSE_BATCH
            putExtra(TranslationCancelReceiver.EXTRA_BATCH_TOKEN, batchToken)
        }
        return PendingIntent.getBroadcast(
            context,
            stableBatchRequestCode(batchToken, 1),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun resumeBatchIntent(batchToken: String): PendingIntent {
        val intent = Intent(context, TranslationCancelReceiver::class.java).apply {
            action = TranslationCancelReceiver.ACTION_RESUME_BATCH
            putExtra(TranslationCancelReceiver.EXTRA_BATCH_TOKEN, batchToken)
        }
        return PendingIntent.getBroadcast(
            context,
            stableBatchRequestCode(batchToken, 2),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelBatchIntent(batchToken: String): PendingIntent {
        val intent = Intent(context, TranslationCancelReceiver::class.java).apply {
            action = TranslationCancelReceiver.ACTION_CANCEL_BATCH
            putExtra(TranslationCancelReceiver.EXTRA_BATCH_TOKEN, batchToken)
        }
        return PendingIntent.getBroadcast(
            context,
            stableBatchRequestCode(batchToken, 3),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelAllIntent(): PendingIntent {
        val intent = Intent(context, TranslationCancelReceiver::class.java).apply {
            action = TranslationCancelReceiver.ACTION_CANCEL_ALL
        }
        return PendingIntent.getBroadcast(
            context,
            Notifications.ID_TRANSLATION_PROGRESS,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun getNotificationId(chapterId: Long): Int {
        return Notifications.ID_TRANSLATION_PROGRESS + stableNotificationId(chapterId)
    }

    private fun stableNotificationId(chapterId: Long): Int {
        val mixed = chapterId xor (chapterId ushr 32)
        return (mixed and 0x3FFFFFFF).toInt().coerceAtLeast(1)
    }

    private fun stableBatchRequestCode(batchToken: String, salt: Int): Int {
        val hash = batchToken.hashCode() * 31 + salt
        return (hash and 0x3FFFFFFF).coerceAtLeast(1)
    }

    private fun buildBatchSummaryText(state: TranslationBatchState): String {
        return context.stringResource(
            MR.strings.notification_translation_batch_summary,
            state.completed,
            state.skipped,
            state.failed,
        )
    }

    private fun buildBatchPausedText(state: TranslationBatchState): String {
        return context.stringResource(
            MR.strings.notification_translation_batch_paused_summary,
            buildBatchSummaryText(state),
        )
    }

    private fun buildBatchCompleteText(state: TranslationBatchState): String {
        return if (state.failed > 0) {
            context.stringResource(
                MR.strings.notification_translation_batch_complete_errors,
                state.completed,
                state.skipped,
                state.failed,
            )
        } else {
            context.stringResource(
                MR.strings.notification_translation_batch_complete_success,
                state.completed,
                state.skipped,
            )
        }
    }
}
