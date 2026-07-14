package eu.kanade.tachiyomi.data.anixart

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import com.tadami.aurora.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.aniyomi.AYMR

class AnixartImportNotifier(private val context: Context) {

    private val progressNotificationBuilder = context.notificationBuilder(
        Notifications.CHANNEL_ANIXART_IMPORT,
    ) {
        setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
        setSmallIcon(R.drawable.ic_ani)
        setAutoCancel(false)
        setOngoing(true)
        setOnlyAlertOnce(true)
    }

    private val completeNotificationBuilder = context.notificationBuilder(
        Notifications.CHANNEL_ANIXART_IMPORT,
    ) {
        setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
        setSmallIcon(R.drawable.ic_ani)
        setAutoCancel(true)
    }

    fun showProgress(current: Int, total: Int): NotificationCompat.Builder {
        return progressNotificationBuilder.apply {
            setContentTitle(context.stringResource(AYMR.strings.anixart_import_importing))
            setContentText("$current / $total")
            setProgress(total.coerceAtLeast(1), current, total == 0)
        }
    }

    private var lastProgressNotifyAt = 0L

    /**
     * Builds and actually posts the progress notification. Throttled so bulk
     * imports do not spam the notification manager on every row.
     */
    fun notifyProgress(current: Int, total: Int) {
        val now = System.currentTimeMillis()
        if (current < total && now - lastProgressNotifyAt < PROGRESS_THROTTLE_MS) return
        lastProgressNotifyAt = now
        context.notify(Notifications.ID_ANIXART_IMPORT_PROGRESS, showProgress(current, total).build())
    }

    fun showComplete(report: ImportAnixartEntries.Report, trackerReport: AnixartTrackerSync.Report?) {
        val trackerSuffix = trackerReport?.let {
            " · Shikimori ${it.synced}"
        }.orEmpty()
        completeNotificationBuilder.apply {
            setContentTitle(context.stringResource(AYMR.strings.anixart_import_done))
            setContentText(
                context.stringResource(
                    AYMR.strings.anixart_import_report,
                    report.added,
                    report.alreadyInLibrary,
                    report.failed,
                ) + trackerSuffix,
            )
        }.build().let {
            context.notify(Notifications.ID_ANIXART_IMPORT_COMPLETE, it)
        }
    }

    fun showError(message: String) {
        completeNotificationBuilder.apply {
            setContentTitle(context.stringResource(AYMR.strings.anixart_import_done))
            setContentText(message)
        }.build().let {
            context.notify(Notifications.ID_ANIXART_IMPORT_COMPLETE, it)
        }
    }

    companion object {
        private const val PROGRESS_THROTTLE_MS = 500L
    }
}
