package eu.kanade.tachiyomi.data.translation

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
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

    fun showProgress(update: TranslationProgressUpdate) {
        val notificationId = getNotificationId(update.chapterId)

        val builder = context.notificationBuilder(Notifications.CHANNEL_TRANSLATION_PROGRESS) {
            setContentTitle("${context.stringResource(MR.strings.notification_translation_in_progress)}: ${update.chapterName}")
            setContentText("${update.progress}%")
            setSmallIcon(android.R.drawable.ic_menu_edit)
            setProgress(100, update.progress, false)
            setOngoing(true)
            setOnlyAlertOnce(true)
        }

        context.notify(notificationId, builder.build())
    }

    fun showComplete(chapterName: String, chapterId: Long) {
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
            addAction(android.R.drawable.ic_menu_view, context.stringResource(MR.strings.notification_action_open), openPendingIntent)
        }

        context.notify(notificationId, builder.build())
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

    private fun getNotificationId(chapterId: Long): Int {
        return Notifications.ID_TRANSLATION_PROGRESS + chapterId.toInt()
    }
}
