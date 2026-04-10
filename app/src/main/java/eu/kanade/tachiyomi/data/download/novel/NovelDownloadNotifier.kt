package eu.kanade.tachiyomi.data.download.novel

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import com.tadami.aurora.R
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.injectLazy

internal class NovelDownloadNotifier(private val context: Context) {

    private val preferences: SecurityPreferences by injectLazy()

    private val progressNotificationBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_DOWNLOADER_PROGRESS) {
            setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            setAutoCancel(false)
            setOnlyAlertOnce(true)
        }
    }

    private val errorNotificationBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_DOWNLOADER_ERROR) {
            setAutoCancel(true)
            setOnlyAlertOnce(true)
        }
    }

    private fun NotificationCompat.Builder.show(id: Int) {
        context.notify(id, build())
    }

    fun onProgressChange(
        pendingCount: Int,
        activeCount: Int,
        failedCount: Int,
        currentTask: NovelQueuedDownload?,
    ) {
        val progressText = context.stringResource(
            AYMR.strings.novel_download_queue_progress,
            pendingCount,
            activeCount,
        )

        with(progressNotificationBuilder) {
            setSmallIcon(android.R.drawable.stat_sys_download)
            setOngoing(true)
            setProgress(0, 0, true)
            clearActions()
            setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
            context.cancelNotification(Notifications.ID_DOWNLOAD_NOVEL_ERROR)

            if (preferences.hideNotificationContent().get()) {
                setContentTitle(progressText)
                setContentText(null)
            } else {
                val taskText = currentTask
                    ?.let { task ->
                        "${task.novel.title.chop(18)} - ${task.chapter.name.chop(24)}".chop(44)
                    }
                    ?: context.stringResource(AYMR.strings.novel_translated_download_title)
                val withFailures = if (failedCount > 0) {
                    "$progressText, ${context.stringResource(
                        AYMR.strings.novel_download_queue_failed_count,
                        failedCount,
                    )}"
                } else {
                    progressText
                }
                setContentTitle(taskText)
                setContentText(withFailures)
            }

            show(Notifications.ID_DOWNLOAD_NOVEL_PROGRESS)
        }
    }

    fun onQueued(queuedCount: Int) {
        with(progressNotificationBuilder) {
            setSmallIcon(android.R.drawable.stat_sys_download)
            setOngoing(true)
            setProgress(0, 0, false)
            clearActions()
            setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
            context.cancelNotification(Notifications.ID_DOWNLOAD_NOVEL_ERROR)

            val queuedText = context.stringResource(
                AYMR.strings.novel_download_queue_started_count,
                queuedCount,
            )
            if (preferences.hideNotificationContent().get()) {
                setContentTitle(queuedText)
                setContentText(null)
            } else {
                setContentTitle(queuedText)
                setContentText(context.stringResource(AYMR.strings.novel_download_queue_waiting))
            }

            show(Notifications.ID_DOWNLOAD_NOVEL_PROGRESS)
        }
    }

    fun onComplete(failedCount: Int) {
        dismissProgress()

        if (failedCount <= 0) {
            context.cancelNotification(Notifications.ID_DOWNLOAD_NOVEL_ERROR)
            with(progressNotificationBuilder) {
                setSmallIcon(R.drawable.ic_done_24dp)
                setOngoing(false)
                setProgress(0, 0, false)
                clearActions()
                setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
                setContentTitle(context.stringResource(AYMR.strings.novel_download_queue_completed))
                setContentText(null)
                setTimeoutAfter(4_000L)
                show(Notifications.ID_DOWNLOAD_NOVEL_PROGRESS)
            }
            return
        }

        with(errorNotificationBuilder) {
            setSmallIcon(R.drawable.ic_warning_white_24dp)
            setOngoing(false)
            setProgress(0, 0, false)
            clearActions()
            setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
            setContentTitle(context.stringResource(AYMR.strings.novel_download_queue_completed))
            setContentText(context.stringResource(AYMR.strings.novel_download_queue_failed_count, failedCount))
            show(Notifications.ID_DOWNLOAD_NOVEL_ERROR)
        }
    }

    fun dismissProgress() {
        context.cancelNotification(Notifications.ID_DOWNLOAD_NOVEL_PROGRESS)
    }
}
