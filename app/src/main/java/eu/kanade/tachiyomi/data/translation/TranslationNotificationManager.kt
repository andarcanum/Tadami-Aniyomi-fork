package eu.kanade.tachiyomi.data.translation

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.cancelNotification
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
        progress: Int,
        pendingCount: Int,
    ) {
        val builder = context.notificationBuilder(Notifications.CHANNEL_TRANSLATION_PROGRESS) {
            setContentTitle(context.stringResource(MR.strings.notification_translation_in_progress))
            setContentText(buildProgressText(chapterName, progress, pendingCount))
            setSmallIcon(android.R.drawable.ic_menu_edit)
            setProgress(100, progress, false)
            setOngoing(true)
            setOnlyAlertOnce(true)
            setStyle(NotificationCompat.BigTextStyle().bigText(buildProgressText(chapterName, progress, pendingCount)))
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

            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("chapterId", chapterId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                chapterId.toInt(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            addAction(
                android.R.drawable.ic_menu_view,
                context.stringResource(MR.strings.notification_action_open),
                openPendingIntent,
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

    private fun cancelChapterIntent(chapterId: Long): PendingIntent {
        val intent = Intent(context, TranslationCancelReceiver::class.java).apply {
            action = TranslationCancelReceiver.ACTION_CANCEL_CHAPTER
            putExtra(TranslationCancelReceiver.EXTRA_CHAPTER_ID, chapterId)
        }
        return PendingIntent.getBroadcast(
            context,
            chapterId.toInt(),
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
        return Notifications.ID_TRANSLATION_PROGRESS + chapterId.toInt()
    }
}
